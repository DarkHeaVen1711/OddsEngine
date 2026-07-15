package com.oddsengine.service.chat;

import java.util.ArrayList;
import java.util.List;

public class FixtureSlot {
    private String sport;
    private List<String> entities = new ArrayList<>();
    private String competition;
    private String round;
    private String dateHint;

    // Getters and Setters
    public String getSport() { return sport; }
    public void setSport(String sport) { this.sport = sport; }

    public List<String> getEntities() { return entities; }
    public void setEntities(List<String> entities) { this.entities = entities; }

    public String getCompetition() { return competition; }
    public void setCompetition(String competition) { this.competition = competition; }

    public String getRound() { return round; }
    public void setRound(String round) { this.round = round; }

    public String getDateHint() { return dateHint; }
    public void setDateHint(String dateHint) { this.dateHint = dateHint; }
}
