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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class RatingOrchestratorTest {

    @Autowired
    private RatingOrchestrator orchestrator;

    @Autowired
    private IngestionService ingestionService;

    @Autowired
    private PredictionService predictionService;

    @Autowired
    private SimulationService simulationService;

    @Autowired
    private CsvSportDataAdapter csvAdapter;

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
        event.setVenue("Test Team A");
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

        // 1500 (home) vs 1500 (away) with A winning -> A gets ~1511.518, B gets ~1488.482
        assertEquals(1511.518, snapshotA.getRating(), 0.001);
        assertEquals(1488.482, snapshotB.getRating(), 0.001);
    }

    @Test
    public void testProcessGlicko2Ratings() {
        SportEntity teamA = new SportEntity();
        teamA.setId("glicko_team_a");
        teamA.setName("Glicko Team A");
        teamA.setEntityType("team");
        entityRepository.save(teamA);

        SportEntity teamB = new SportEntity();
        teamB.setId("glicko_team_b");
        teamB.setName("Glicko Team B");
        teamB.setEntityType("team");
        entityRepository.save(teamB);

        SportEvent event = new SportEvent();
        event.setId("glicko_event_1");
        event.setSportId("football");
        event.setTimestamp(1710000000L);
        event.setStatus("completed");
        eventRepository.save(event);

        Participant pA = new Participant();
        pA.setEventId("glicko_event_1");
        pA.setEntityId("glicko_team_a");
        pA.setFinishRank(1);
        participantRepository.save(pA);

        Participant pB = new Participant();
        pB.setEventId("glicko_event_1");
        pB.setEntityId("glicko_team_b");
        pB.setFinishRank(2);
        participantRepository.save(pB);

        List<RatingSnapshot> snapshots = orchestrator.processEventRatings("glicko_event_1", "glicko2");

        assertEquals(2, snapshots.size());
        
        RatingSnapshot snapshotA = snapshots.stream()
                .filter(s -> s.getEntityId().equals("glicko_team_a"))
                .findFirst()
                .orElseThrow();
        RatingSnapshot snapshotB = snapshots.stream()
                .filter(s -> s.getEntityId().equals("glicko_team_b"))
                .findFirst()
                .orElseThrow();

        // Glicko-2 outputs should have updated volatility and deviations
        assertTrue(snapshotA.getRating() > 1500.0);
        assertTrue(snapshotB.getRating() < 1500.0);
        assertTrue(snapshotA.getRatingDeviation() < 350.0);
        assertTrue(snapshotB.getRatingDeviation() < 350.0);
        assertEquals("glicko2", snapshotA.getModelName());
    }

    @Test
    public void testMatchIngestionPipeline() throws IOException {
        // Create temporary CSV match file
        File tempCsv = File.createTempFile("test_matches", ".csv");
        tempCsv.deleteOnExit();

        try (FileWriter fw = new FileWriter(tempCsv)) {
            fw.write("id,sport_id,timestamp,venue,home_team,away_team,home_goals,away_goals,status\n");
            fw.write("csv_event_1,football,1710000000,Arsenal Stadium,Arsenal,Chelsea,2,1,completed\n");
        }

        csvAdapter.setCsvFilePath(tempCsv.getAbsolutePath());

        // Perform ingestion
        int count = ingestionService.ingestMatches("football", 1700000000L, "elo");
        assertEquals(1, count);

        // Verify database state
        assertTrue(eventRepository.existsById("csv_event_1"));

        // Verify ratings populated dynamically downstream
        List<RatingSnapshot> snapshots = ratingRepository.findAll();
        boolean hasArsenal = snapshots.stream().anyMatch(s -> s.getEntityId().equals("arsenal") && "elo".equals(s.getModelName()));
        boolean hasChelsea = snapshots.stream().anyMatch(s -> s.getEntityId().equals("chelsea") && "elo".equals(s.getModelName()));
        assertTrue(hasArsenal);
        assertTrue(hasChelsea);
    }

    @Test
    public void testPredictionAndSimulationPipelines() throws Exception {
        // Seeding database events if not present
        SportEntity teamA = entityRepository.findById("test_team_a").orElseGet(() -> {
            SportEntity s = new SportEntity();
            s.setId("test_team_a");
            s.setName("Test Team A");
            s.setSportId("football");
            s.setEntityType("team");
            return entityRepository.save(s);
        });

        SportEntity teamB = entityRepository.findById("test_team_b").orElseGet(() -> {
            SportEntity s = new SportEntity();
            s.setId("test_team_b");
            s.setName("Test Team B");
            s.setSportId("football");
            s.setEntityType("team");
            return entityRepository.save(s);
        });

        SportEvent completedEvent = new SportEvent();
        completedEvent.setId("pred_comp_1");
        completedEvent.setSportId("football");
        completedEvent.setTimestamp(1710000000L);
        completedEvent.setStatus("completed");
        completedEvent.setVenue("Test Team A");
        eventRepository.save(completedEvent);

        Participant cpA = new Participant();
        cpA.setEventId("pred_comp_1");
        cpA.setEntityId("test_team_a");
        cpA.setFinishRank(1);
        cpA.setResultDataJson("{\"goals\": 3}");
        participantRepository.save(cpA);

        Participant cpB = new Participant();
        cpB.setEventId("pred_comp_1");
        cpB.setEntityId("test_team_b");
        cpB.setFinishRank(2);
        cpB.setResultDataJson("{\"goals\": 1}");
        participantRepository.save(cpB);

        // Scheduled event to predict
        SportEvent scheduledEvent = new SportEvent();
        scheduledEvent.setId("pred_sched_1");
        scheduledEvent.setSportId("football");
        scheduledEvent.setTimestamp(1720000000L);
        scheduledEvent.setStatus("scheduled");
        scheduledEvent.setVenue("Test Team A");
        eventRepository.save(scheduledEvent);

        Participant spA = new Participant();
        spA.setEventId("pred_sched_1");
        spA.setEntityId("test_team_a");
        participantRepository.save(spA);

        Participant spB = new Participant();
        spB.setEventId("pred_sched_1");
        spB.setEntityId("test_team_b");
        participantRepository.save(spB);

        // Run prediction
        com.oddsengine.model.PredictionRecord record = predictionService.predictMatch("pred_sched_1", "poisson");
        assertNotNull(record);
        assertEquals("pred_sched_1", record.getEventId());
        assertTrue(record.getPredictedOutcomeProbsJson().contains("win"));

        // Run simulation
        String jobId = simulationService.startSimulation("football", 100);
        assertNotNull(jobId);
        
        // Wait for task completion
        Thread.sleep(1500);

        SimulationService.SimulationJob job = simulationService.getJob(jobId);
        assertNotNull(job);
        assertEquals("COMPLETED", job.status);
        assertNotNull(job.resultJson);
    }
}
