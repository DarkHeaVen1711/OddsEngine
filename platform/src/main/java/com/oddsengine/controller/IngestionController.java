package com.oddsengine.controller;

import com.oddsengine.repository.RatingSnapshotRepository;
import com.oddsengine.repository.SportEntityRepository;
import com.oddsengine.repository.SportEventRepository;
import com.oddsengine.service.IngestionService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HTTP surface for the data ingestion pipeline.
 *
 * POST /api/ingest?sportId=football&since=0&modelName=elo
 *   — triggers IngestionService.ingestMatches() and returns count of newly ingested events.
 *
 * GET /api/ingest/status
 *   — returns aggregate counts of events, entities, and rating snapshots in the DB.
 */
@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/ingest")
public class IngestionController {

    private final IngestionService ingestionService;
    private final SportEventRepository eventRepository;
    private final SportEntityRepository entityRepository;
    private final RatingSnapshotRepository ratingRepository;

    public IngestionController(IngestionService ingestionService,
                               SportEventRepository eventRepository,
                               SportEntityRepository entityRepository,
                               RatingSnapshotRepository ratingRepository) {
        this.ingestionService = ingestionService;
        this.eventRepository = eventRepository;
        this.entityRepository = entityRepository;
        this.ratingRepository = ratingRepository;
    }

    /**
     * Trigger ingestion of recent matches for the given sport.
     *
     * @param sportId   sport identifier, e.g. "football", "cricket", "f1"
     * @param since     unix epoch millis — only ingest matches at or after this time (default 0 = all history)
     * @param modelName rating model to apply on ingested completed events (default "elo")
     */
    @PostMapping
    public Map<String, Object> ingest(
            @RequestParam(defaultValue = "football") String sportId,
            @RequestParam(defaultValue = "0") long since,
            @RequestParam(defaultValue = "elo") String modelName) {

        int count = ingestionService.ingestMatches(sportId, since, modelName);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("sportId", sportId);
        response.put("modelName", modelName);
        response.put("eventsIngested", count);
        return response;
    }

    /**
     * Ingest a batch of matches sent directly as a JSON payload.
     * Useful for external data pipelines or direct API integrations.
     *
     * @param events    List of event wrapper JSON objects containing event, participants, and entities.
     * @param modelName rating model to apply on ingested completed events (default "elo")
     */
    @PostMapping("/batch")
    public Map<String, Object> ingestBatch(
            @RequestBody java.util.List<com.oddsengine.service.EventWrapper> events,
            @RequestParam(defaultValue = "elo") String modelName) {

        int count = ingestionService.ingestBatch(events, modelName);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("modelName", modelName);
        response.put("eventsReceived", events.size());
        response.put("eventsIngested", count);
        return response;
    }

    /**
     * Return current DB row counts — useful for verifying the pipeline has data.
     */
    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("totalEvents",          eventRepository.count());
        response.put("totalEntities",        entityRepository.count());
        response.put("totalRatingSnapshots", ratingRepository.count());
        return response;
    }
}
