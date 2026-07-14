package com.oddsengine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oddsengine.model.Participant;
import com.oddsengine.model.PredictionRecord;
import com.oddsengine.model.SportEvent;
import com.oddsengine.repository.ParticipantRepository;
import com.oddsengine.repository.PredictionRecordRepository;
import com.oddsengine.repository.SportEntityRepository;
import com.oddsengine.repository.SportEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PredictionService {
    private final EngineClient engineClient;
    private final SportEventRepository eventRepository;
    private final ParticipantRepository participantRepository;
    private final SportEntityRepository entityRepository;
    private final PredictionRecordRepository predictionRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PredictionService(EngineClient engineClient,
                             SportEventRepository eventRepository,
                             ParticipantRepository participantRepository,
                             SportEntityRepository entityRepository,
                             PredictionRecordRepository predictionRepository) {
        this.engineClient = engineClient;
        this.eventRepository = eventRepository;
        this.participantRepository = participantRepository;
        this.entityRepository = entityRepository;
        this.predictionRepository = predictionRepository;
    }

    @Transactional
    public PredictionRecord predictMatch(String matchId, String modelName) {
        SportEvent event = eventRepository.findById(matchId)
                .orElseThrow(() -> new IllegalArgumentException("Match not found: " + matchId));

        List<Participant> targetParts = participantRepository.findByEventId(matchId);
        if (targetParts.size() < 2) {
            throw new IllegalArgumentException("Target match must have at least 2 participants.");
        }

        Participant p1 = targetParts.get(0);
        Participant p2 = targetParts.get(1);

        boolean isP1Home = false;
        if (event.getVenue() != null) {
            isP1Home = entityRepository.findById(p1.getEntityId())
                    .map(entity -> event.getVenue().equalsIgnoreCase(entity.getName()))
                    .orElse(false);
        }
        Participant home = isP1Home ? p1 : p2;
        Participant away = isP1Home ? p2 : p1;

        List<Map<String, Object>> history = new ArrayList<>();
        List<SportEvent> completed = eventRepository.findBySportIdAndStatus(event.getSportId(), "completed");
        for (SportEvent ce : completed) {
            List<Participant> parts = participantRepository.findByEventId(ce.getId());
            if (parts.size() == 2) {
                Participant cp1 = parts.get(0);
                Participant cp2 = parts.get(1);
                boolean isCp1Home = false;
                if (ce.getVenue() != null) {
                    isCp1Home = entityRepository.findById(cp1.getEntityId())
                            .map(entity -> ce.getVenue().equalsIgnoreCase(entity.getName()))
                            .orElse(false);
                }
                Participant chome = isCp1Home ? cp1 : cp2;
                Participant caway = isCp1Home ? cp2 : cp1;

                Map<String, Object> matchMap = new HashMap<>();
                matchMap.put("home_id", chome.getEntityId());
                matchMap.put("away_id", caway.getEntityId());
                matchMap.put("home_goals", parseGoals(chome.getResultDataJson()));
                matchMap.put("away_goals", parseGoals(caway.getResultDataJson()));
                matchMap.put("weight", 1.0);
                history.add(matchMap);
            }
        }

        String probsJson = engineClient.predictEvent(modelName, history, home.getEntityId(), away.getEntityId());

        PredictionRecord record = new PredictionRecord();
        record.setEventId(matchId);
        record.setModelName(modelName);
        record.setPredictedOutcomeProbsJson(probsJson);
        record.setPredictedResultJson(probsJson);
        record.setGeneratedAt(System.currentTimeMillis());

        return predictionRepository.save(record);
    }

    private int parseGoals(String json) {
        if (json == null) return 0;
        try {
            return objectMapper.readTree(json).path("goals").asInt(0);
        } catch (Exception e) {
            return 0;
        }
    }
}
