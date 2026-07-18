package com.oddsengine.service.chat;

import com.oddsengine.service.GazetteerService;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

@Service
public class SlotParser {
    private final GazetteerService gazetteerService;

    // Trigger words for rejection of compound/conditional queries
    private static final List<String> UNPARSEABLE_INDICATORS = Arrays.asList(
        "if", "but", "unless", "whenever", "injured", "weather", "rain"
    );

    // Controlled vocabulary lists for metadata slots
    private static final Map<String, String> COMPETITION_VOCAB = new LinkedHashMap<>();
    private static final Map<String, String> ROUND_VOCAB = new LinkedHashMap<>();
    private static final Map<String, String> SPORT_INDICATORS = new HashMap<>();

    static {
        COMPETITION_VOCAB.put("world cup", "world cup");
        COMPETITION_VOCAB.put("premier league", "premier league");
        COMPETITION_VOCAB.put("champions league", "champions league");
        COMPETITION_VOCAB.put("grand prix", "grand prix");
        COMPETITION_VOCAB.put("test series", "test series");
        COMPETITION_VOCAB.put("ipl", "ipl");

        ROUND_VOCAB.put("semi final", "semi final");
        ROUND_VOCAB.put("semifinal", "semi final");
        ROUND_VOCAB.put("quarter final", "quarter final");
        ROUND_VOCAB.put("quarterfinal", "quarter final");
        ROUND_VOCAB.put("final", "final");
        ROUND_VOCAB.put("group stage", "group stage");

        SPORT_INDICATORS.put("football", "football");
        SPORT_INDICATORS.put("soccer", "football");
        SPORT_INDICATORS.put("cricket", "cricket");
        SPORT_INDICATORS.put("f1", "f1");
        SPORT_INDICATORS.put("formula 1", "f1");
    }

    public SlotParser(GazetteerService gazetteerService) {
        this.gazetteerService = gazetteerService;
    }

    public static class ParseResult {
        private final FixtureSlot slot;
        private final boolean parseable;
        private final String rejectReason;

        public ParseResult(FixtureSlot slot) {
            this.slot = slot;
            this.parseable = true;
            this.rejectReason = null;
        }

        public ParseResult(String rejectReason) {
            this.slot = null;
            this.parseable = false;
            this.rejectReason = rejectReason;
        }

        public FixtureSlot getSlot() { return slot; }
        public boolean isParseable() { return parseable; }
        public String getRejectReason() { return rejectReason; }
    }

    public ParseResult parse(String query) {
        if (query == null || query.trim().isEmpty()) {
            return new ParseResult("Empty query phrase.");
        }

        String normalized = query.toLowerCase().trim();

        // 1. Filter out compound queries / conditions
        for (String indicator : UNPARSEABLE_INDICATORS) {
            if (Pattern.compile("\\b" + indicator + "\\b").matcher(normalized).find()) {
                return new ParseResult("Compound/conditional constraints are out of scope (found trigger: '" + indicator + "').");
            }
        }

        FixtureSlot slot = new FixtureSlot();

        // 2. Extract competition if match found
        for (Map.Entry<String, String> entry : COMPETITION_VOCAB.entrySet()) {
            if (normalized.contains(entry.getKey())) {
                slot.setCompetition(entry.getValue());
                normalized = normalized.replace(entry.getKey(), " ");
                break;
            }
        }

        // 3. Extract round if match found
        for (Map.Entry<String, String> entry : ROUND_VOCAB.entrySet()) {
            if (normalized.contains(entry.getKey())) {
                slot.setRound(entry.getValue());
                normalized = normalized.replace(entry.getKey(), " ");
                break;
            }
        }

        // 4. Extract explicit sport indicator if present
        for (Map.Entry<String, String> entry : SPORT_INDICATORS.entrySet()) {
            if (Pattern.compile("\\b" + entry.getKey() + "\\b").matcher(normalized).find()) {
                slot.setSport(entry.getValue());
                normalized = normalized.replaceAll("\\b" + entry.getKey() + "\\b", " ");
                break;
            }
        }

        // 5. Parse out participants by splitting tokens on connection words
        String cleaned = normalized
            .replaceAll("\\bpredict\\b", "")
            .replaceAll("\\bwhat is the probability of\\b", "")
            .replaceAll("\\bwho wins\\b", "");

        String[] parts = cleaned.split("\\b(vs|v|against|versus|-)\\b");
        
        Set<String> resolvedEntities = new LinkedHashSet<>();
        String resolvedSportFromEntities = null;

        for (String part : parts) {
            String candidate = cleanCandidatePhrase(part);
            if (candidate.isEmpty()) continue;

            Optional<GazetteerService.EntityMatch> match = gazetteerService.resolve(candidate);
            if (match.isPresent()) {
                resolvedEntities.add(match.get().getEntityId());
                if (resolvedSportFromEntities == null) {
                    resolvedSportFromEntities = match.get().getSportId();
                } else if (!resolvedSportFromEntities.equals(match.get().getSportId())) {
                    return new ParseResult("Query spans entities across multiple sports: " + resolvedSportFromEntities + " and " + match.get().getSportId());
                }
            }
        }

        if (resolvedEntities.isEmpty()) {
            return new ParseResult("Could not resolve any sports participants/teams in the query.");
        }

        slot.setEntities(new ArrayList<>(resolvedEntities));

        // 6. Handle implicit sport setting
        if (slot.getSport() == null) {
            slot.setSport(resolvedSportFromEntities);
        }

        // 7. Extract date hints (basic heuristics)
        if (normalized.contains("today")) {
            slot.setDateHint("today");
        } else if (normalized.contains("tomorrow")) {
            slot.setDateHint("tomorrow");
        } else if (normalized.contains("weekend") || normalized.contains("this weekend")) {
            slot.setDateHint("weekend");
        }

        return new ParseResult(slot);
    }

    private String cleanCandidatePhrase(String phrase) {
        return phrase.replaceAll("[.,!?()\"']", "")
            .replaceAll("\\b(the|a|an|in|on|at|for|to|between|and|will|win|play|match|game)\\b", "")
            .trim();
    }
}
