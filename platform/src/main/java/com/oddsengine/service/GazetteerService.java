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
        loadAliasesAndDatabaseEntities();
    }

    public synchronized void loadAliasesAndDatabaseEntities() {
        synonymIndex.clear();
        log.info("Initializing Gazetteer alias/entity index...");

        Map<String, Map<String, List<String>>> staticAliases = new HashMap<>();
        try {
            ClassPathResource resource = new ClassPathResource("aliases.json");
            if (resource.exists()) {
                try (InputStream is = resource.getInputStream()) {
                    staticAliases = mapper.readValue(is, new TypeReference<Map<String, Map<String, List<String>>>>() {});
                }
            }
        } catch (Exception e) {
            log.error("Failed to load aliases.json file", e);
        }

        List<SportEntity> dbEntities = entityRepository.findAll();
        Set<String> processedDbIds = new HashSet<>();

        for (SportEntity entity : dbEntities) {
            String entityId = entity.getId();
            String sportId = entity.getSportId();
            String name = entity.getName();

            addSynonym(name, entityId, sportId, name, 1.0);
            processedDbIds.add(entityId);

            Map<String, List<String>> sportAliases = staticAliases.get(sportId);
            if (sportAliases != null) {
                List<String> synonyms = sportAliases.get(entityId);
                if (synonyms != null) {
                    for (String syn : synonyms) {
                        addSynonym(syn, entityId, sportId, name, 1.0);
                    }
                }
            }
        }

        for (Map.Entry<String, Map<String, List<String>>> sportEntry : staticAliases.entrySet()) {
            String sportId = sportEntry.getKey();
            for (Map.Entry<String, List<String>> entityEntry : sportEntry.getValue().entrySet()) {
                String entityId = entityEntry.getKey();
                if (!processedDbIds.contains(entityId)) {
                    List<String> synonyms = entityEntry.getValue();
                    String canonicalName = synonyms.isEmpty() ? entityId : synonyms.get(0);
                    for (String syn : synonyms) {
                        addSynonym(syn, entityId, sportId, canonicalName, 0.95);
                    }
                }
            }
        }
    }

    private void addSynonym(String term, String entityId, String sportId, String canonicalName, double baseScore) {
        if (term == null || term.trim().isEmpty()) return;
        String normalized = term.trim().toLowerCase();
        
        List<EntityMatch> matches = synonymIndex.computeIfAbsent(normalized, k -> new ArrayList<>());
        boolean exists = matches.stream().anyMatch(m -> m.getEntityId().equals(entityId) && m.getSportId().equals(sportId));
        if (!exists) {
            matches.add(new EntityMatch(entityId, sportId, canonicalName, term, baseScore));
        }
    }

    public Optional<EntityMatch> resolve(String phrase) {
        if (phrase == null || phrase.trim().isEmpty()) {
            return Optional.empty();
        }
        String query = phrase.trim().toLowerCase();

        List<EntityMatch> exactMatches = synonymIndex.get(query);
        if (exactMatches != null && !exactMatches.isEmpty()) {
            return Optional.of(exactMatches.get(0));
        }

        EntityMatch bestMatch = null;
        double bestScore = 0.0;

        for (Map.Entry<String, List<EntityMatch>> entry : synonymIndex.entrySet()) {
            String synonym = entry.getKey();
            double score = jaroWinkler.apply(query, synonym);

            if (score >= FUZZY_THRESHOLD && score > bestScore) {
                bestScore = score;
                EntityMatch matchTemplate = entry.getValue().get(0);
                bestMatch = new EntityMatch(
                    matchTemplate.getEntityId(),
                    matchTemplate.getSportId(),
                    matchTemplate.getCanonicalName(),
                    phrase,
                    bestScore
                );
            }
        }

        return Optional.ofNullable(bestMatch);
    }
}
