package com.oddsengine.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "rating_snapshots")
@IdClass(RatingSnapshotId.class)
public class RatingSnapshot {
    @Id
    @Column(name = "entity_id")
    private String entityId;

    @Id
    @Column(name = "sport_id")
    private String sportId;

    @Id
    @Column(name = "model_name")
    private String modelName;

    @Id
    @Column(name = "as_of_timestamp")
    private Long asOfTimestamp;

    @Column(nullable = false)
    private Double rating;

    @Column(name = "rating_deviation")
    private Double ratingDeviation;

    private Double volatility;

    // Getters and Setters
    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }

    public String getSportId() { return sportId; }
    public void setSportId(String sportId) { this.sportId = sportId; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public Long getAsOfTimestamp() { return asOfTimestamp; }
    public void setAsOfTimestamp(Long asOfTimestamp) { this.asOfTimestamp = asOfTimestamp; }

    public Double getRating() { return rating; }
    public void setRating(Double rating) { this.rating = rating; }

    public Double getRatingDeviation() { return ratingDeviation; }
    public void setRatingDeviation(Double ratingDeviation) { this.ratingDeviation = ratingDeviation; }

    public Double getVolatility() { return volatility; }
    public void setVolatility(Double volatility) { this.volatility = volatility; }
}
