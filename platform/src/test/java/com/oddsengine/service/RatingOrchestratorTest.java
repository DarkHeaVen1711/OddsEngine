package com.oddsengine.service;

import com.oddsengine.model.Participant;
import com.oddsengine.model.RatingSnapshot;
import com.oddsengine.model.SportEntity;
import com.oddsengine.model.SportEvent;
import com.oddsengine.repository.ParticipantRepository;
import com.oddsengine.repository.RatingSnapshotRepository;
import com.oddsengine.repository.SportEntityRepository;
import com.oddsengine.repository.SportEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class RatingOrchestratorTest {

    @Autowired
    private RatingOrchestrator orchestrator;

    @Autowired
    private SportEventRepository eventRepository;

    @Autowired
    private SportEntityRepository entityRepository;

    @Autowired
    private ParticipantRepository participantRepository;

    @Autowired
    private RatingSnapshotRepository ratingRepository;

    @Test
    public void testEndToEndRatingCalculation() {
        SportEntity teamA = new SportEntity();
        teamA.setId("test_team_a");
        teamA.setName("Test Team A");
        teamA.setSportId("football");
        teamA.setEntityType("team");
        entityRepository.save(teamA);

        SportEntity teamB = new SportEntity();
        teamB.setId("test_team_b");
        teamB.setName("Test Team B");
        teamB.setSportId("football");
        teamB.setEntityType("team");
        entityRepository.save(teamB);

        SportEvent event = new SportEvent();
        event.setId("test_event_1");
        event.setSportId("football");
        event.setTimestamp(1710000000L);
        event.setStatus("completed");
        eventRepository.save(event);

        Participant pA = new Participant();
        pA.setEventId("test_event_1");
        pA.setEntityId("test_team_a");
        pA.setFinishRank(1);
        pA.setResultDataJson("{\"goals\": 2}");
        participantRepository.save(pA);

        Participant pB = new Participant();
        pB.setEventId("test_event_1");
        pB.setEntityId("test_team_b");
        pB.setFinishRank(2);
        pB.setResultDataJson("{\"goals\": 1}");
        participantRepository.save(pB);

        List<RatingSnapshot> snapshots = orchestrator.processEventRatings("test_event_1", "elo");

        assertEquals(2, snapshots.size());
        
        RatingSnapshot snapshotA = snapshots.stream()
                .filter(s -> s.getEntityId().equals("test_team_a"))
                .findFirst()
                .orElseThrow();
        RatingSnapshot snapshotB = snapshots.stream()
                .filter(s -> s.getEntityId().equals("test_team_b"))
                .findFirst()
                .orElseThrow();

        assertEquals(1516.0, snapshotA.getRating());
        assertEquals(1484.0, snapshotB.getRating());
    }
}
