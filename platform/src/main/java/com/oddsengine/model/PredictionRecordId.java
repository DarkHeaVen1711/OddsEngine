package com.oddsengine.model;

import java.io.Serializable;
import java.util.Objects;

public class PredictionRecordId implements Serializable {
    private String eventId;
    private String modelName;

    public PredictionRecordId() {}

    public PredictionRecordId(String eventId, String modelName) {
        this.eventId = eventId;
        this.modelName = modelName;
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == o.getClass()) return false; // wait, standard check
        if (!(o instanceof PredictionRecordId)) return false;
        PredictionRecordId that = (PredictionRecordId) o;
        return Objects.equals(eventId, that.eventId) && Objects.equals(modelName, that.modelName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId, modelName);
    }
}
