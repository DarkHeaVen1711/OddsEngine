package com.oddsengine.service.chat;

import com.oddsengine.model.Participant;
import com.oddsengine.model.SportEntity;
import com.oddsengine.model.SportEvent;
import com.oddsengine.repository.ParticipantRepository;
import com.oddsengine.repository.SportEntityRepository;
import com.oddsengine.repository.SportEventRepository;
import com.oddsengine.service.GazetteerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class ChatNluTest {

    @Autowired
    private SlotParser slotParser;

    @Autowired
    private FixtureResolver fixtureResolver;

    @Autowired
    private GazetteerService gazetteerService;

    @Autowired
    private SportEntityRepository entityRepository;

    @Autowired
    private SportEventRepository eventRepository;

    @Autowired
    private ParticipantRepository participantRepository;

    @BeforeEach
    public void setUp() {
        // Seed some test entities and fixtures
        SportEntity manCity = new SportEntity();
        manCity.setId("man_city");
        manCity.setName("Manchester City");
        manCity.setSportId("football");
        manCity.setEntityType("team");
        entityRepository.save(manCity);

        SportEntity realMadrid = new SportEntity();
        realMadrid.setId("real_madrid");
        realMadrid.setName("Real Madrid");
        realMadrid.setSportId("football");
        realMadrid.setEntityType("team");
        entityRepository.save(realMadrid);

        SportEntity india = new SportEntity();
        india.setId("india");
        india.setName("India");
        india.setSportId("cricket");
        india.setEntityType("team");
        entityRepository.save(india);

        SportEntity aus = new SportEntity();
        aus.setId("australia");
        aus.setName("Australia");
        aus.setSportId("cricket");
        aus.setEntityType("team");
        entityRepository.save(aus);

        // Events
        SportEvent uclMatch = new SportEvent();
        uclMatch.setId("evt_ucl_1");
        uclMatch.setSportId("football");
        uclMatch.setVenue("Etihad Stadium");
        uclMatch.setStatus("scheduled");
        uclMatch.setTimestamp(1786752000000L); // 2026-08-15 in epoch milliseconds
        uclMatch.setMetadataJson("{\"competition\": \"champions league\", \"round\": \"semi final\"}");
        eventRepository.save(uclMatch);

        Participant p1 = new Participant();
        p1.setEventId("evt_ucl_1");
        p1.setEntityId("man_city");
        p1.setFinishRank(1);
        p1.setResultDataJson("{}");
        participantRepository.save(p1);

        Participant p2 = new Participant();
        p2.setEventId("evt_ucl_1");
        p2.setEntityId("real_madrid");
        p2.setFinishRank(2);
        p2.setResultDataJson("{}");
        participantRepository.save(p2);

        // Reload gazetteer with seeded db records
        gazetteerService.loadAliasesAndDatabaseEntities();
    }

    @Test
    public void testFuzzyAliasParsing() {
        // "City" -> "man_city", "Los Blancos" -> "real_madrid"
        SlotParser.ParseResult result = slotParser.parse("predict City against Los Blancos");
        assertTrue(result.isParseable());
        
        FixtureSlot slots = result.getSlot();
        assertNotNull(slots);
        assertEquals("football", slots.getSport());
        assertTrue(slots.getEntities().contains("man_city"));
        assertTrue(slots.getEntities().contains("real_madrid"));
    }

    @Test
    public void testMetadataExtraction() {
        SlotParser.ParseResult result = slotParser.parse("probability of Real Madrid vs Man City in Champions League semi final");
        assertTrue(result.isParseable());
        
        FixtureSlot slots = result.getSlot();
        assertEquals("champions league", slots.getCompetition());
        assertEquals("semi final", slots.getRound());
    }

    @Test
    public void testCompoundQueryRejection() {
        // Contains 'if'
        SlotParser.ParseResult result = slotParser.parse("predict Man City vs Real Madrid if it rains");
        assertFalse(result.isParseable());
        assertNotNull(result.getRejectReason());
        assertTrue(result.getRejectReason().contains("Compound/conditional"));
    }

    @Test
    public void testFixtureResolutionSuccess() {
        SlotParser.ParseResult parse = slotParser.parse("Man City versus Real Madrid");
        assertTrue(parse.isParseable());

        FixtureResolver.ResolutionResult resolution = fixtureResolver.resolve(parse.getSlot());
        assertTrue(resolution.isResolved());
        assertEquals("evt_ucl_1", resolution.getEvent().getId());
    }

    @Test
    public void testCrossSportRejection() {
        // Man City (football) vs India (cricket)
        SlotParser.ParseResult parse = slotParser.parse("City vs India");
        assertFalse(parse.isParseable());
        assertTrue(parse.getRejectReason().contains("multiple sports"));
    }

    @Test
    public void testQueryWithNoTeams() {
        SlotParser.ParseResult parse = slotParser.parse("predict semi final 2 for world cup");
        assertFalse(parse.isParseable());
        assertTrue(parse.getRejectReason().contains("Could not resolve any sports participants"));
    }
}
