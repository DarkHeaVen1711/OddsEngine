package com.oddsengine.service;

import com.oddsengine.model.Participant;
import com.oddsengine.model.RatingSnapshot;
import com.oddsengine.model.SportEvent;
import com.oddsengine.repository.ParticipantRepository;
import com.oddsengine.repository.RatingSnapshotRepository;
import com.oddsengine.repository.SportEntityRepository;
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
    private final SportEntityRepository entityRepository;

    public RatingOrchestrator(EngineClient engineClient,
                              SportEventRepository eventRepository,
                              ParticipantRepository participantRepository,
                              RatingSnapshotRepository ratingRepository,
                              SportEntityRepository entityRepository) {
        this.engineClient = engineClient;
        this.eventRepository = eventRepository;
        this.participantRepository = participantRepository;
        this.ratingRepository = ratingRepository;
        this.entityRepository = entityRepository;
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
            double currentRd = latest.map(RatingSnapshot::getRatingDeviation).orElse(350.0);
            double currentVol = latest.map(RatingSnapshot::getVolatility).orElse(0.06);
            
            long matchCount = participantRepository.countByEntityId(p.getEntityId());
            int matchesPlayed = (int) Math.max(0, matchCount - 1);

            boolean isHome = false;
            if (event.getVenue() != null) {
                isHome = entityRepository.findById(p.getEntityId())
                        .map(entity -> event.getVenue().equalsIgnoreCase(entity.getName()))
                        .orElse(false);
            }

            inputs.add(new EngineClient.ParticipantInput(p.getEntityId(), p.getFinishRank(), currentRating, isHome, matchesPlayed, currentRd, currentVol));
        }

        Map<String, EngineClient.RatingOutput> newRatings = engineClient.calculateRatings(inputs, modelName);

        List<RatingSnapshot> snapshots = new ArrayList<>();
        for (Participant p : participants) {
            EngineClient.RatingOutput output = newRatings.get(p.getEntityId());
            double ratingValue = (output != null) ? output.rating : 1500.0;
            double deviationValue = (output != null) ? output.rating_deviation : 350.0;
            double volatilityValue = (output != null) ? output.volatility : 0.06;

            RatingSnapshot snapshot = new RatingSnapshot();
            snapshot.setEntityId(p.getEntityId());
            snapshot.setSportId(event.getSportId());
            snapshot.setModelName(modelName);
            snapshot.setAsOfTimestamp(event.getTimestamp());
            snapshot.setRating(ratingValue);
            snapshot.setRatingDeviation(deviationValue);
            snapshot.setVolatility(volatilityValue);
            snapshots.add(ratingRepository.save(snapshot));
        }

        return snapshots;
    }
}
