package com.oddsengine.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "participants")
@IdClass(ParticipantId.class)
public class Participant {
    @Id
    @Column(name = "event_id")
    private String eventId;

    @Id
    @Column(name = "entity_id")
    private String entityId;

    @Column(name = "result_data_json", columnDefinition = "TEXT")
    private String resultDataJson;

    @Column(name = "finish_rank")
    private Integer finishRank;

    // Getters and Setters
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }

    public String getResultDataJson() { return resultDataJson; }
    public void setResultDataJson(String resultDataJson) { this.resultDataJson = resultDataJson; }

    public Integer getFinishRank() { return finishRank; }
    public void setFinishRank(Integer finishRank) { this.finishRank = finishRank; }
}
