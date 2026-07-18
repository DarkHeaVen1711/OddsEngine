package com.oddsengine.service;

import com.oddsengine.model.ContextFeature;
import com.oddsengine.repository.ContextFeatureRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.logging.Logger;

@Component
public class FeatureDerivationJob {

    private static final Logger logger = Logger.getLogger(FeatureDerivationJob.class.getName());
    private final ContextFeatureRepository contextFeatureRepository;
    private final FeatureApplicabilityRegistry featureApplicabilityRegistry;

    public FeatureDerivationJob(ContextFeatureRepository contextFeatureRepository, FeatureApplicabilityRegistry featureApplicabilityRegistry) {
        this.contextFeatureRepository = contextFeatureRepository;
        this.featureApplicabilityRegistry = featureApplicabilityRegistry;
    }

    @Scheduled(cron = "0 0 2 * * ?") // Run at 2 AM every day
    public void deriveFeatures() {
        logger.info("Starting offline feature derivation job...");
        // TODO: Implement logic to derive 'rest_days' and 'current_form'
        // 1. Fetch recent events
        // 2. Group by entity
        // 3. Compute rest days = current date - last event date
        // 4. Compute current form = points/wins in last 5 matches
        // 5. Store in context_features table checking sport applicability
        logger.info("Feature derivation job completed.");
    }
}
