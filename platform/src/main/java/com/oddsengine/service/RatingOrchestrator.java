package com.oddsengine.service;

import com.oddsengine.model.Participant;
import com.oddsengine.model.RatingSnapshot;
import com.oddsengine.model.SportEvent;
import com.oddsengine.repository.ParticipantRepository;
import com.oddsengine.repository.RatingSnapshotRepository;
import com.oddsengine.repository.SportEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class RatingOrchestrator {
    private final EngineClient engineClient;
    private final SportEventRepository eventRepository;
    private final ParticipantRepository participantRepository;
    private final RatingSnapshotRepository ratingRepository;

    public RatingOrchestrator(EngineClient engineClient,
                              SportEventRepository eventRepository,
                              ParticipantRepository participantRepository,
                              RatingSnapshotRepository ratingRepository) {
        this.engineClient = engineClient;
        this.eventRepository = eventRepository;
        this.participantRepository = participantRepository;
        this.ratingRepository = ratingRepository;
    }

    @Transactional
    public List<RatingSnapshot> processEventRatings(String eventId, String modelName) {
        SportEvent event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));
        
        List<Participant> participants = participantRepository.findByEventId(eventId);
        if (participants.isEmpty()) {
            throw new IllegalStateException("No participants found for event: " + eventId);
        }

        List<EngineClient.ParticipantInput> inputs = new ArrayList<>();
        for (Participant p : participants) {
            Optional<RatingSnapshot> latest = ratingRepository.findLatestRating(p.getEntityId(), event.getSportId(), modelName);
            double currentRating = latest.map(RatingSnapshot::getRating).orElse(1500.0);
            inputs.add(new EngineClient.ParticipantInput(p.getEntityId(), p.getFinishRank(), currentRating));
        }

        Map<String, Double> newRatings = engineClient.calculateRatings(inputs);

        List<RatingSnapshot> snapshots = new ArrayList<>();
        for (Participant p : participants) {
            double ratingValue = newRatings.getOrDefault(p.getEntityId(), 1500.0);
            RatingSnapshot snapshot = new RatingSnapshot();
            snapshot.setEntityId(p.getEntityId());
            snapshot.setSportId(event.getSportId());
            snapshot.setModelName(modelName);
            snapshot.setAsOfTimestamp(event.getTimestamp());
            snapshot.setRating(ratingValue);
            snapshot.setRatingDeviation(350.0);
            snapshot.setVolatility(0.06);
            snapshots.add(ratingRepository.save(snapshot));
        }

        return snapshots;
    }
}
