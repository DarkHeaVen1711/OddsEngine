package com.oddsengine.service.chat;

import com.oddsengine.model.Participant;
import com.oddsengine.model.SportEvent;
import com.oddsengine.repository.ParticipantRepository;
import com.oddsengine.repository.SportEventRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class FixtureResolver {
    private final SportEventRepository eventRepository;
    private final ParticipantRepository participantRepository;

    public static class ResolutionResult {
        private final SportEvent event;
        private final boolean resolved;
        private final List<SportEvent> candidates;
        private final String failureReason;

        public ResolutionResult(SportEvent event) {
            this.event = event;
            this.resolved = true;
            this.candidates = Collections.emptyList();
            this.failureReason = null;
        }

        public ResolutionResult(List<SportEvent> candidates) {
            this.event = null;
            this.resolved = false;
            this.candidates = candidates;
            this.failureReason = "Ambiguous query. Multiple fixtures matched.";
        }

        public ResolutionResult(String failureReason) {
            this.event = null;
            this.resolved = false;
            this.candidates = Collections.emptyList();
            this.failureReason = failureReason;
        }

        public SportEvent getEvent() { return event; }
        public boolean isResolved() { return resolved; }
        public List<SportEvent> getCandidates() { return candidates; }
        public String getFailureReason() { return failureReason; }
    }

    public FixtureResolver(SportEventRepository eventRepository, ParticipantRepository participantRepository) {
        this.eventRepository = eventRepository;
        this.participantRepository = participantRepository;
    }

    public ResolutionResult resolve(FixtureSlot slots) {
        List<String> entities = slots.getEntities();
        if (entities == null || entities.isEmpty()) {
            return new ResolutionResult("No entities provided for resolution.");
        }

        // 1. Find all events that feature the target entities
        List<SportEvent> matchingEvents = findEventsWithParticipants(entities);

        if (slots.getSport() != null) {
            matchingEvents = matchingEvents.stream()
                .filter(e -> slots.getSport().equalsIgnoreCase(e.getSportId()))
                .collect(Collectors.toList());
        }

        if (matchingEvents.isEmpty()) {
            return new ResolutionResult("No fixtures found containing the resolved participants: " + entities);
        }

        // 2. Filter by status (default priority: scheduled -> completed)
        List<SportEvent> scheduled = matchingEvents.stream()
            .filter(e -> "scheduled".equalsIgnoreCase(e.getStatus()) || "live".equalsIgnoreCase(e.getStatus()))
            .collect(Collectors.toList());

        List<SportEvent> activeSet = scheduled.isEmpty() ? matchingEvents : scheduled;

        // 3. Narrow using competition / format metadata matches
        if (slots.getCompetition() != null) {
            List<SportEvent> compFiltered = activeSet.stream()
                .filter(e -> e.getMetadataJson() != null && e.getMetadataJson().toLowerCase().contains(slots.getCompetition()))
                .collect(Collectors.toList());
            if (!compFiltered.isEmpty()) {
                activeSet = compFiltered;
            }
        }

        // 4. Narrow using round metadata
        if (slots.getRound() != null) {
            List<SportEvent> roundFiltered = activeSet.stream()
                .filter(e -> e.getMetadataJson() != null && e.getMetadataJson().toLowerCase().contains(slots.getRound()))
                .collect(Collectors.toList());
            if (!roundFiltered.isEmpty()) {
                activeSet = roundFiltered;
            }
        }

        // 5. Evaluate final cardinality
        if (activeSet.size() == 1) {
            return new ResolutionResult(activeSet.get(0));
        }

        if (activeSet.size() > 1) {
            // Ambiguity handling: sort by date-hint if possible or temporal proximity, else return all candidates
            // Sort activeSet descending by timestamp (or ID to preserve stable ordering)
            activeSet.sort((a, b) -> {
                long tA = a.getTimestamp() != null ? a.getTimestamp() : 0L;
                long tB = b.getTimestamp() != null ? b.getTimestamp() : 0L;
                return Long.compare(tB, tA); // Most recent first
            });
            return new ResolutionResult(activeSet);
        }

        return new ResolutionResult("Fixture resolution narrowed down to zero matches after filters applied.");
    }

    private List<SportEvent> findEventsWithParticipants(List<String> entityIds) {
        if (entityIds.size() == 1) {
            List<Participant> pList = participantRepository.findByEntityId(entityIds.get(0));
            return pList.stream()
                .map(p -> eventRepository.findById(p.getEventId()).orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        }

        // If N-way / 2-way match, find events that contain ALL target entities
        Map<String, Set<String>> eventEntityMap = new HashMap<>();
        for (String eid : entityIds) {
            List<Participant> pList = participantRepository.findByEntityId(eid);
            for (Participant p : pList) {
                eventEntityMap.computeIfAbsent(p.getEventId(), k -> new HashSet<>()).add(eid);
            }
        }

        List<String> eventIdsContainingAll = eventEntityMap.entrySet().stream()
            .filter(entry -> entry.getValue().size() == entityIds.size())
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());

        return eventIdsContainingAll.stream()
            .map(id -> eventRepository.findById(id).orElse(null))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }
}
