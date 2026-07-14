package com.oddsengine.model;

import java.io.Serializable;
import java.util.Objects;

public class ParticipantId implements Serializable {
    private String eventId;
    private String entityId;

    public ParticipantId() {}
    public ParticipantId(String eventId, String entityId) {
        this.eventId = eventId;
        this.entityId = entityId;
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    
    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ParticipantId)) return false;
        ParticipantId that = (ParticipantId) o;
        return Objects.equals(eventId, that.eventId) && Objects.equals(entityId, that.entityId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId, entityId);
    }
}
