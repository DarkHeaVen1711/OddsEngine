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
}
