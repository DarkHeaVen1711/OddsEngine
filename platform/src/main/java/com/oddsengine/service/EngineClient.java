package com.oddsengine.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class EngineClient {
    private final ObjectMapper objectMapper = new ObjectMapper();

    public static class ParticipantInput {
        public String entity_id;
        public int finish_rank;
        public double current_rating;
        public boolean is_home;
        public int matches_played;
        public double rating_deviation;
        public double volatility;

        public ParticipantInput(String entity_id, int finish_rank, double current_rating, boolean is_home, int matches_played, double rating_deviation, double volatility) {
            this.entity_id = entity_id;
            this.finish_rank = finish_rank;
            this.current_rating = current_rating;
            this.is_home = is_home;
            this.matches_played = matches_played;
            this.rating_deviation = rating_deviation;
            this.volatility = volatility;
        }
    }

    public static class RatingOutput {
        public double rating;
        public double rating_deviation;
        public double volatility;

        public RatingOutput(double rating, double rating_deviation, double volatility) {
            this.rating = rating;
            this.rating_deviation = rating_deviation;
            this.volatility = volatility;
        }
    }

    public Map<String, RatingOutput> calculateRatings(List<ParticipantInput> participants, String modelName) {
        Map<String, RatingOutput> results = new HashMap<>();
        try {
            String enginePath = findEngineExecutable();
            if (enginePath == null) {
                throw new RuntimeException("Could not locate engine.exe in the project directory.");
            }

            Map<String, Object> inputMap = new HashMap<>();
            inputMap.put("model_name", modelName);
            inputMap.put("participants", participants);
            String inputJson = objectMapper.writeValueAsString(inputMap);

            ProcessBuilder pb = new ProcessBuilder(enginePath, "--cli");
            Process process = pb.start();

            try (OutputStream os = process.getOutputStream()) {
                os.write(inputJson.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            String outputJson;
            try (InputStream is = process.getInputStream()) {
                outputJson = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("Engine subprocess exited with code " + exitCode);
            }

            JsonNode root = objectMapper.readTree(outputJson);
            JsonNode ratingsNode = root.path("ratings");
            ratingsNode.fields().forEachRemaining(entry -> {
                JsonNode val = entry.getValue();
                if (val.isNumber()) {
                    results.put(entry.getKey(), new RatingOutput(val.asDouble(), 350.0, 0.06));
                } else {
                    results.put(entry.getKey(), new RatingOutput(
                        val.path("rating").asDouble(),
                        val.path("rating_deviation").asDouble(),
                        val.path("volatility").asDouble()
                    ));
                }
            });

        } catch (Exception e) {
            throw new RuntimeException("Error calculating ratings through engine: " + e.getMessage(), e);
        }
        return results;
    }

    private String findEngineExecutable() {
        String[] paths = {
            "../engine/engine.exe",
            "engine/engine.exe",
            "./engine.exe",
            "E:/Coding/OddsEngine/engine/engine.exe"
        };
        for (String path : paths) {
            File f = new File(path);
            if (f.exists() && f.isFile()) {
                return f.getAbsolutePath();
            }
        }
        return null;
    }
}
