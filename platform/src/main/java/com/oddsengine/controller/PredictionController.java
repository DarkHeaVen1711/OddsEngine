package com.oddsengine.controller;

import com.oddsengine.model.RatingSnapshot;
import com.oddsengine.service.RatingOrchestrator;
import com.oddsengine.repository.RatingSnapshotRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class PredictionController {
    private final RatingOrchestrator ratingOrchestrator;
    private final RatingSnapshotRepository ratingRepository;

    public PredictionController(RatingOrchestrator ratingOrchestrator,
                                RatingSnapshotRepository ratingRepository) {
        this.ratingOrchestrator = ratingOrchestrator;
        this.ratingRepository = ratingRepository;
    }

    @PostMapping("/events/{eventId}/process")
    public List<RatingSnapshot> processEvent(@PathVariable String eventId,
                                            @RequestParam(defaultValue = "elo") String modelName) {
        return ratingOrchestrator.processEventRatings(eventId, modelName);
    }

    @GetMapping("/entities/{entityId}/rating-history")
    public List<RatingSnapshot> getRatingHistory(@PathVariable String entityId,
                                                @RequestParam String sportId,
                                                @RequestParam(defaultValue = "elo") String modelName) {
        return ratingRepository.findByEntityIdAndSportIdAndModelNameOrderByAsOfTimestampAsc(entityId, sportId, modelName);
    }
}
