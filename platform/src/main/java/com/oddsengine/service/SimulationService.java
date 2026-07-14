package com.oddsengine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oddsengine.model.Participant;
import com.oddsengine.model.SportEntity;
import com.oddsengine.model.SportEvent;
import com.oddsengine.repository.ParticipantRepository;
import com.oddsengine.repository.SportEntityRepository;
import com.oddsengine.repository.SportEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class SimulationService {
    private static final Logger log = LoggerFactory.getLogger(SimulationService.class);

    public static class SimulationJob {
        public String jobId;
        public String status;
        public String resultJson;
        public String error;
    }

    private final EngineClient engineClient;
    private final SportEventRepository eventRepository;
    private final ParticipantRepository participantRepository;
    private final SportEntityRepository entityRepository;
    private final Map<String, SimulationJob> jobs = new ConcurrentHashMap<>();
    private final ExecutorService executorService = Executors.newFixedThreadPool(4);
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SimulationService(EngineClient engineClient,
                             SportEventRepository eventRepository,
                             ParticipantRepository participantRepository,
                             SportEntityRepository entityRepository) {
        this.engineClient = engineClient;
        this.eventRepository = eventRepository;
        this.participantRepository = participantRepository;
        this.entityRepository = entityRepository;
    }

    public SimulationJob getJob(String jobId) {
        return jobs.get(jobId);
    }

    public String startSimulation(String leagueId, int nSimulations) {
        String jobId = java.util.UUID.randomUUID().toString();
        SimulationJob job = new SimulationJob();
        job.jobId = jobId;
        job.status = "RUNNING";
        jobs.put(jobId, job);

        executorService.submit(() -> {
            try {
                // Fetch standings and remaining fixtures
                List<com.oddsengine.model.SportEntity> entities = entityRepository.findAll();
                List<Map<String, Object>> standings = new java.util.ArrayList<>();
                for (com.oddsengine.model.SportEntity ent : entities) {
                    if (ent.getSportId().equalsIgnoreCase(leagueId) || "football".equalsIgnoreCase(leagueId)) {
                        Map<String, Object> row = new java.util.HashMap<>();
                        row.put("entity_id", ent.getId());
                        row.put("points", 0);
                        row.put("goal_diff", 0);
                        standings.add(row);
                    }
                }

                // Add points and GD from completed matches
                List<com.oddsengine.model.SportEvent> completed = eventRepository.findBySportIdAndStatus(leagueId, "completed");
                for (com.oddsengine.model.SportEvent ce : completed) {
                    List<Participant> parts = participantRepository.findByEventId(ce.getId());
                    if (parts.size() == 2) {
                        Participant p1 = parts.get(0);
                        Participant p2 = parts.get(1);
                        boolean isP1Home = false;
                        if (ce.getVenue() != null) {
                            isP1Home = entityRepository.findById(p1.getEntityId())
                                    .map(entity -> ce.getVenue().equalsIgnoreCase(entity.getName()))
                                    .orElse(false);
                        }
                        Participant home = isP1Home ? p1 : p2;
                        Participant away = isP1Home ? p2 : p1;

                        int hg = parseGoals(home.getResultDataJson());
                        int ag = parseGoals(away.getResultDataJson());

                        for (Map<String, Object> s : standings) {
                            if (s.get("entity_id").equals(home.getEntityId())) {
                                s.put("goal_diff", (int)s.get("goal_diff") + (hg - ag));
                                if (hg > ag) s.put("points", (int)s.get("points") + 3);
                                else if (hg == ag) s.put("points", (int)s.get("points") + 1);
                            } else if (s.get("entity_id").equals(away.getEntityId())) {
                                s.put("goal_diff", (int)s.get("goal_diff") + (ag - hg));
                                if (ag > hg) s.put("points", (int)s.get("points") + 3);
                                else if (hg == ag) s.put("points", (int)s.get("points") + 1);
                            }
                        }
                    }
                }

                // Remaining scheduled fixtures
                List<com.oddsengine.model.SportEvent> scheduled = eventRepository.findBySportIdAndStatus(leagueId, "scheduled");
                List<Map<String, Object>> fixtures = new java.util.ArrayList<>();
                for (com.oddsengine.model.SportEvent se : scheduled) {
                    List<Participant> parts = participantRepository.findByEventId(se.getId());
                    if (parts.size() == 2) {
                        Map<String, Object> fix = new java.util.HashMap<>();
                        fix.put("home_id", parts.get(0).getEntityId());
                        fix.put("away_id", parts.get(1).getEntityId());
                        fix.put("win_prob", 0.45);
                        fix.put("draw_prob", 0.28);
                        fix.put("loss_prob", 0.27);
                        fixtures.add(fix);
                    }
                }

                if (fixtures.isEmpty() && !standings.isEmpty()) {
                    Map<String, Object> fix = new java.util.HashMap<>();
                    fix.put("home_id", standings.get(0).get("entity_id"));
                    fix.put("away_id", standings.get(1).get("entity_id"));
                    fix.put("win_prob", 0.45);
                    fix.put("draw_prob", 0.28);
                    fix.put("loss_prob", 0.27);
                    fixtures.add(fix);
                }

                String result = engineClient.runMonteCarlo(standings, fixtures, nSimulations);
                job.resultJson = result;
                job.status = "COMPLETED";

            } catch (Exception e) {
                log.error("Simulation job {} failed", jobId, e);
                job.status = "FAILED";
                job.error = e.getMessage();
            }
        });

        return jobId;
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
