package com.oddsengine.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "prediction_records")
@IdClass(PredictionRecordId.class)
public class PredictionRecord {
    @Id
    @Column(name = "event_id")
    private String eventId;

    @Id
    @Column(name = "model_name")
    private String modelName;

    @Column(name = "predicted_outcome_probs_json", columnDefinition = "TEXT")
    private String predictedOutcomeProbsJson;

    @Column(name = "predicted_result_json", columnDefinition = "TEXT")
    private String predictedResultJson;

    @Column(name = "generated_at", nullable = false)
    private Long generatedAt;

    @Column(name = "actual_result_json", columnDefinition = "TEXT")
    private String actualResultJson;

    // Getters and Setters
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public String getPredictedOutcomeProbsJson() { return predictedOutcomeProbsJson; }
    public void setPredictedOutcomeProbsJson(String predictedOutcomeProbsJson) { this.predictedOutcomeProbsJson = predictedOutcomeProbsJson; }

    public String getPredictedResultJson() { return predictedResultJson; }
    public void setPredictedResultJson(String predictedResultJson) { this.predictedResultJson = predictedResultJson; }

    public Long getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(Long generatedAt) { this.generatedAt = generatedAt; }

    public String getActualResultJson() { return actualResultJson; }
    public void setActualResultJson(String actualResultJson) { this.actualResultJson = actualResultJson; }
}
