package com.oddsengine.controller;

import com.oddsengine.model.PredictionRecord;
import com.oddsengine.model.SportEvent;
import com.oddsengine.service.PredictionService;
import com.oddsengine.service.chat.FixtureResolver;
import com.oddsengine.service.chat.SlotParser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/chat")
public class ChatController {

    private final SlotParser slotParser;
    private final FixtureResolver fixtureResolver;
    private final PredictionService predictionService;

    public static class ChatRequest {
        private String query;
        public String getQuery() { return query; }
        public void setQuery(String query) { this.query = query; }
    }

    public static class ChatResponse {
        private final boolean success;
        private final String message;
        private final Map<String, Object> resolvedFixture;
        private final Map<String, Double> probabilities;
        private final List<Map<String, Object>> candidates;

        public ChatResponse(boolean success, String message, Map<String, Object> resolvedFixture, Map<String, Double> probabilities, List<Map<String, Object>> candidates) {
            this.success = success;
            this.message = message;
            this.resolvedFixture = resolvedFixture;
            this.probabilities = probabilities;
            this.candidates = candidates;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public Map<String, Object> getResolvedFixture() { return resolvedFixture; }
        public Map<String, Double> getProbabilities() { return probabilities; }
        public List<Map<String, Object>> getCandidates() { return candidates; }
    }

    public ChatController(SlotParser slotParser, FixtureResolver fixtureResolver, PredictionService predictionService) {
        this.slotParser = slotParser;
        this.fixtureResolver = fixtureResolver;
        this.predictionService = predictionService;
    }
}
