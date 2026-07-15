package com.oddsengine.service.chat;

import com.oddsengine.service.GazetteerService;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

@Service
public class SlotParser {
    private final GazetteerService gazetteerService;

    private static final List<String> UNPARSEABLE_INDICATORS = Arrays.asList(
        "if", "but", "unless", "whenever", "injured", "weather", "rain"
    );

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
}
