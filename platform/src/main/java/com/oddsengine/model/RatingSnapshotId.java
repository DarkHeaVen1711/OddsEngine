package com.oddsengine.model;

import java.io.Serializable;
import java.util.Objects;

public class RatingSnapshotId implements Serializable {
    private String entityId;
    private String sportId;
    private String modelName;
    private Long asOfTimestamp;

    public RatingSnapshotId() {}
    public RatingSnapshotId(String entityId, String sportId, String modelName, Long asOfTimestamp) {
        this.entityId = entityId;
        this.sportId = sportId;
        this.modelName = modelName;
        this.asOfTimestamp = asOfTimestamp;
    }

    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }

    public String getSportId() { return sportId; }
    public void setSportId(String sportId) { this.sportId = sportId; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public Long getAsOfTimestamp() { return asOfTimestamp; }
    public void setAsOfTimestamp(Long asOfTimestamp) { this.asOfTimestamp = asOfTimestamp; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RatingSnapshotId)) return false;
        RatingSnapshotId that = (RatingSnapshotId) o;
        return Objects.equals(entityId, that.entityId) &&
               Objects.equals(sportId, that.sportId) &&
               Objects.equals(modelName, that.modelName) &&
               Objects.equals(asOfTimestamp, that.asOfTimestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entityId, sportId, modelName, asOfTimestamp);
    }
}
