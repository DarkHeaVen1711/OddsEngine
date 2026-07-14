package com.oddsengine.service;

import com.oddsengine.model.Participant;
import com.oddsengine.model.SportEntity;
import com.oddsengine.model.SportEvent;
import java.util.List;

public class EventWrapper {
    private SportEvent event;
    private List<Participant> participants;
    private List<SportEntity> entities;

    public EventWrapper(SportEvent event, List<Participant> participants, List<SportEntity> entities) {
        this.event = event;
        this.participants = participants;
        this.entities = entities;
    }

    public SportEvent getEvent() { return event; }
    public void setEvent(SportEvent event) { this.event = event; }

    public List<Participant> getParticipants() { return participants; }
    public void setParticipants(List<Participant> participants) { this.participants = participants; }

    public List<SportEntity> getEntities() { return entities; }
    public void setEntities(List<SportEntity> entities) { this.entities = entities; }
}
