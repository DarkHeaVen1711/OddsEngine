package com.oddsengine.service;

import java.util.List;

public interface SportDataAdapter {
    List<EventWrapper> fetchRecentMatches(String sportId, Long since);
}
