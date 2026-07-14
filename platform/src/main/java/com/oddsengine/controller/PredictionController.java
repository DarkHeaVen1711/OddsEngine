package com.oddsengine.controller;

import com.oddsengine.model.PredictionRecord;
import com.oddsengine.model.RatingSnapshot;
import com.oddsengine.model.SportEvent;
import com.oddsengine.service.PredictionService;
import com.oddsengine.service.RatingOrchestrator;
import com.oddsengine.repository.RatingSnapshotRepository;
import com.oddsengine.repository.SportEventRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class PredictionController {
    private final RatingOrchestrator ratingOrchestrator;
    private final RatingSnapshotRepository ratingRepository;
    private final PredictionService predictionService;
    private final SportEventRepository eventRepository;

    public PredictionController(RatingOrchestrator ratingOrchestrator,
                                RatingSnapshotRepository ratingRepository,
                                PredictionService predictionService,
                                SportEventRepository eventRepository) {
        this.ratingOrchestrator = ratingOrchestrator;
        this.ratingRepository = ratingRepository;
        this.predictionService = predictionService;
        this.eventRepository = eventRepository;
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
}
