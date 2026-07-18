package com.oddsengine.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.FetchType;

@Entity
@Table(name = "context_features")
public class ContextFeature {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id", nullable = false)
    private SportEntity entity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    private SportEvent event;

    @Column(name = "feature_name", nullable = false)
    private String featureName;

    @Column(nullable = false)
    private Double value;

    @Column(name = "as_of_timestamp", nullable = false)
    private Long asOfTimestamp;

    @Column(name = "sport_applicability_json", columnDefinition = "TEXT")
    private String sportApplicabilityJson;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public SportEntity getEntity() { return entity; }
    public void setEntity(SportEntity entity) { this.entity = entity; }

    public SportEvent getEvent() { return event; }
    public void setEvent(SportEvent event) { this.event = event; }

    public String getFeatureName() { return featureName; }
    public void setFeatureName(String featureName) { this.featureName = featureName; }

    public Double getValue() { return value; }
    public void setValue(Double value) { this.value = value; }

    public Long getAsOfTimestamp() { return asOfTimestamp; }
    public void setAsOfTimestamp(Long asOfTimestamp) { this.asOfTimestamp = asOfTimestamp; }

    public String getSportApplicabilityJson() { return sportApplicabilityJson; }
    public void setSportApplicabilityJson(String sportApplicabilityJson) { this.sportApplicabilityJson = sportApplicabilityJson; }
}
