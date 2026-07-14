package com.oddsengine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oddsengine.repository.ParticipantRepository;
import com.oddsengine.repository.SportEntityRepository;
import com.oddsengine.repository.SportEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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
}
