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
    }
}
