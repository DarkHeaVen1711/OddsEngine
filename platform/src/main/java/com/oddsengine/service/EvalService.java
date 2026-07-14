package com.oddsengine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oddsengine.model.PredictionRecord;
import com.oddsengine.repository.PredictionRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes model evaluation metrics (Brier score, log loss, calibration buckets)
 * entirely from stored PredictionRecord rows — no engine round-trip required.
 *
 * Format assumptions for stored JSON:
 *   predictedOutcomeProbsJson: {"win":0.6,"draw":0.2,"loss":0.2}
 *   actualResultJson:          {"outcome":"win"} | {"outcome":"draw"} | {"outcome":"loss"}
 */
@Service
public class EvalService {

    private static final Logger log = LoggerFactory.getLogger(EvalService.class);
    private static final int CALIBRATION_BUCKETS = 10;

    private final PredictionRecordRepository predictionRepo;
    private final ObjectMapper mapper = new ObjectMapper();

    public EvalService(PredictionRecordRepository predictionRepo) {
        this.predictionRepo = predictionRepo;
    }

    /**
     * Compute accuracy metrics for a given model over records that have an actual result stored.
     *
     * @param modelName  e.g. "elo", "poisson", "glicko2"
     * @param sinceEpoch  unix millis — only consider predictions generated at or after this time
     * @return map with keys: brierScore, logLoss, sampleSize, calibrationBuckets
     */
    public Map<String, Object> computeAccuracy(String modelName, long sinceEpoch) {
        List<PredictionRecord> all = predictionRepo.findAll();

        double brierSum = 0.0;
        double logLossSum = 0.0;
        int count = 0;

        // 10 calibration buckets [0.0,0.1), [0.1,0.2), ... [0.9,1.0]
        double[] bucketPredSum = new double[CALIBRATION_BUCKETS];
        double[] bucketActualSum = new double[CALIBRATION_BUCKETS];
        int[]    bucketCount    = new int[CALIBRATION_BUCKETS];

        for (PredictionRecord rec : all) {
            if (!modelName.equalsIgnoreCase(rec.getModelName())) continue;
            if (rec.getGeneratedAt() == null || rec.getGeneratedAt() < sinceEpoch) continue;
            if (rec.getPredictedOutcomeProbsJson() == null || rec.getActualResultJson() == null) continue;

            try {
                @SuppressWarnings("unchecked")
                Map<String, Double> probs = mapper.readValue(rec.getPredictedOutcomeProbsJson(), Map.class);
                @SuppressWarnings("unchecked")
                Map<String, String> actual = mapper.readValue(rec.getActualResultJson(), Map.class);

                String outcome = actual.get("outcome"); // "win" | "draw" | "loss"
                if (outcome == null) continue;

                double pWin  = probs.getOrDefault("win",  0.0);
                double pDraw = probs.getOrDefault("draw", 0.0);
                double pLoss = probs.getOrDefault("loss", 0.0);

                // Binary indicator vectors for the three outcomes
                double aWin  = "win".equals(outcome)  ? 1.0 : 0.0;
                double aDraw = "draw".equals(outcome)  ? 1.0 : 0.0;
                double aLoss = "loss".equals(outcome)  ? 1.0 : 0.0;

                // Multi-class Brier score: sum of squared differences across all classes
                double brier = (Math.pow(pWin - aWin, 2) + Math.pow(pDraw - aDraw, 2) + Math.pow(pLoss - aLoss, 2)) / 3.0;
                brierSum += brier;

                // Log loss on the predicted probability of the actual outcome
                double pActual = "win".equals(outcome) ? pWin : ("draw".equals(outcome) ? pDraw : pLoss);
                pActual = Math.max(1e-7, Math.min(1 - 1e-7, pActual)); // clip to avoid log(0)
                logLossSum += -Math.log(pActual);

                // Calibration: bucket by p(win) as the primary probability of interest
                int bucket = (int) Math.min(CALIBRATION_BUCKETS - 1, Math.floor(pWin * CALIBRATION_BUCKETS));
                bucketPredSum[bucket]   += pWin;
                bucketActualSum[bucket] += aWin;
                bucketCount[bucket]++;

                count++;
            } catch (Exception e) {
                log.warn("Could not parse prediction record for event {}: {}", rec.getEventId(), e.getMessage());
            }
        }

        // Build calibration bucket list
        List<Map<String, Object>> calibration = new ArrayList<>();
        for (int i = 0; i < CALIBRATION_BUCKETS; i++) {
            if (bucketCount[i] == 0) continue;
            Map<String, Object> bucket = new LinkedHashMap<>();
            bucket.put("bucketMidpoint", (i + 0.5) / CALIBRATION_BUCKETS);
            bucket.put("meanPredicted",  bucketPredSum[i]   / bucketCount[i]);
            bucket.put("meanActual",     bucketActualSum[i] / bucketCount[i]);
            bucket.put("count",          bucketCount[i]);
            calibration.add(bucket);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("modelName",          modelName);
        result.put("sampleSize",         count);
        result.put("brierScore",         count > 0 ? brierSum / count : null);
        result.put("logLoss",            count > 0 ? logLossSum / count : null);
        result.put("calibrationBuckets", calibration);
        return result;
    }
}
