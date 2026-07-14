package com.oddsengine.controller;

import com.oddsengine.model.PredictionRecord;
import com.oddsengine.model.RatingSnapshot;
import com.oddsengine.model.SportEvent;
import com.oddsengine.service.EvalService;
import com.oddsengine.service.PredictionService;
import com.oddsengine.service.RatingOrchestrator;
import com.oddsengine.repository.RatingSnapshotRepository;
import com.oddsengine.repository.SportEventRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api")
public class PredictionController {
    private final RatingOrchestrator ratingOrchestrator;
    private final RatingSnapshotRepository ratingRepository;
    private final PredictionService predictionService;
    private final SportEventRepository eventRepository;
    private final EvalService evalService;

    public PredictionController(RatingOrchestrator ratingOrchestrator,
                                RatingSnapshotRepository ratingRepository,
                                PredictionService predictionService,
                                SportEventRepository eventRepository,
                                EvalService evalService) {
        this.ratingOrchestrator = ratingOrchestrator;
        this.ratingRepository = ratingRepository;
        this.predictionService = predictionService;
        this.eventRepository = eventRepository;
        this.evalService = evalService;
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

    @PostMapping("/predictions/match/{matchId}")
    public PredictionRecord predictMatch(@PathVariable String matchId,
                                         @RequestParam(defaultValue = "poisson") String modelName) {
        return predictionService.predictMatch(matchId, modelName);
    }

    @GetMapping("/predictions/upcoming")
    public List<SportEvent> getUpcomingMatches() {
        return eventRepository.findByStatus("scheduled");
    }

    @GetMapping("/entities/leaderboard")
    public List<RatingSnapshot> getLeaderboard(@RequestParam(defaultValue = "football") String sportId,
                                               @RequestParam(defaultValue = "elo") String modelName) {
        return ratingRepository.getLeaderboard(sportId, modelName);
    }

    /**
     * Aggregated model accuracy metrics: Brier score, log loss, calibration buckets.
     *
     * @param modelName  e.g. "elo", "poisson"
     * @param since      unix epoch millis — only score predictions generated at or after this time (default 0 = all)
     */
    @GetMapping("/models/accuracy")
    public Map<String, Object> getModelAccuracy(
            @RequestParam(defaultValue = "elo") String modelName,
            @RequestParam(defaultValue = "0") long since) {
        return evalService.computeAccuracy(modelName, since);
    }
}
