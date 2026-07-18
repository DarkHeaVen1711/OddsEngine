package com.oddsengine.service;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Component
public class FeatureApplicabilityRegistry {
    
    private final Map<String, List<String>> sportFeatureApplicability;

    public FeatureApplicabilityRegistry() {
        sportFeatureApplicability = new HashMap<>();
        // Football: Current Form, H2H, Scoring Stats, Weather, Rest Days
        sportFeatureApplicability.put("football", Arrays.asList("current_form", "h2h", "scoring_stats", "weather", "rest_days", "market_sentiment"));
        // Cricket: Current Form, H2H, Scoring Stats, Weather, Rest Days
        sportFeatureApplicability.put("cricket", Arrays.asList("current_form", "h2h", "scoring_stats", "weather", "rest_days", "market_sentiment"));
        // F1: Form (finishing positions), H2H, Weather, Rest Days (back to back)
        sportFeatureApplicability.put("f1", Arrays.asList("current_form", "h2h", "weather", "rest_days", "market_sentiment"));
    }

    public boolean isFeatureApplicable(String sportId, String featureName) {
        List<String> applicableFeatures = sportFeatureApplicability.get(sportId.toLowerCase());
        return applicableFeatures != null && applicableFeatures.contains(featureName);
    }
}
