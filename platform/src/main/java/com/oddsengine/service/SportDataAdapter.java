package com.oddsengine.service;

import com.oddsengine.model.SportEvent;
import java.util.List;

public interface SportDataAdapter {
    List<SportEvent> fetchRecentMatches(String sportId, Long since);
}
