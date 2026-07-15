package com.oddsengine.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oddsengine.model.SportEntity;
import com.oddsengine.repository.SportEntityRepository;
import jakarta.annotation.PostConstruct;
import org.apache.commons.text.similarity.JaroWinklerDistance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.*;

@Service
public class GazetteerService {
    private static final Logger log = LoggerFactory.getLogger(GazetteerService.class);
    private static final double FUZZY_THRESHOLD = 0.88;

    private final SportEntityRepository entityRepository;
    private final JaroWinklerDistance jaroWinkler = new JaroWinklerDistance();
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, List<EntityMatch>> synonymIndex = new HashMap<>();

    public static class EntityMatch {
        private final String entityId;
        private final String sportId;
        private final String canonicalName;
        private final String matchText;
        private final double confidence;

        public EntityMatch(String entityId, String sportId, String canonicalName, String matchText, double confidence) {
            this.entityId = entityId;
            this.sportId = sportId;
            this.canonicalName = canonicalName;
            this.matchText = matchText;
            this.confidence = confidence;
        }

        public String getEntityId() { return entityId; }
        public String getSportId() { return sportId; }
        public String getCanonicalName() { return canonicalName; }
        public String getMatchText() { return matchText; }
        public double getConfidence() { return confidence; }
    }

    public GazetteerService(SportEntityRepository entityRepository) {
        this.entityRepository = entityRepository;
    }

    @PostConstruct
    public void init() {
        // Temporary minimal init
    }
}
