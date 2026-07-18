package com.oddsengine.service;

import com.oddsengine.model.Participant;
import com.oddsengine.model.SportEntity;
import com.oddsengine.model.SportEvent;
import com.oddsengine.repository.ParticipantRepository;
import com.oddsengine.repository.SportEntityRepository;
import com.oddsengine.repository.SportEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class IngestionService {
    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final SportDataAdapter dataAdapter;
    private final SportEventRepository eventRepository;
    private final ParticipantRepository participantRepository;
    private final SportEntityRepository entityRepository;
    private final RatingOrchestrator ratingOrchestrator;

    public IngestionService(SportDataAdapter dataAdapter,
                            SportEventRepository eventRepository,
                            ParticipantRepository participantRepository,
                            SportEntityRepository entityRepository,
                            RatingOrchestrator ratingOrchestrator) {
        this.dataAdapter = dataAdapter;
        this.eventRepository = eventRepository;
        this.participantRepository = participantRepository;
        this.entityRepository = entityRepository;
        this.ratingOrchestrator = ratingOrchestrator;
    }

    @Transactional
    public int ingestMatches(String sportId, Long since, String modelName) {
        List<EventWrapper> recentEvents = dataAdapter.fetchRecentMatches(sportId, since);
        return ingestBatch(recentEvents, modelName);
    }

    @Transactional
    public int ingestBatch(List<EventWrapper> recentEvents, String modelName) {
        int ingestedCount = 0;

        for (EventWrapper wrapper : recentEvents) {
            SportEvent event = wrapper.getEvent();
            if (event.getId() == null || event.getId().trim().isEmpty()) {
                log.warn("Skipping match: event ID is missing or empty.");
                continue;
            }
            if (wrapper.getParticipants() == null || wrapper.getParticipants().size() < 2) {
                log.warn("Skipping match {}: does not have at least 2 participants.", event.getId());
                continue;
            }

            if (eventRepository.existsById(event.getId())) {
                log.debug("Match {} already exists, skipping.", event.getId());
                continue;
            }

            if (wrapper.getEntities() != null) {
                for (SportEntity entity : wrapper.getEntities()) {
                    if (!entityRepository.existsById(entity.getId())) {
                        entityRepository.save(entity);
                    }
                }
            }

            eventRepository.save(event);

            for (Participant p : wrapper.getParticipants()) {
                participantRepository.save(p);
            }

            ingestedCount++;

            if ("completed".equalsIgnoreCase(event.getStatus())) {
                try {
                    ratingOrchestrator.processEventRatings(event.getId(), modelName);
                } catch (Exception e) {
                    log.error("Failed to update ratings for event {}: {}", event.getId(), e.getMessage());
                }
            }
        }

        return ingestedCount;
    }
}
