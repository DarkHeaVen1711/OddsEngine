package com.oddsengine.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "events")
public class SportEvent {
    @Id
    private String id;
    
    @Column(name = "sport_id", nullable = false)
    private String sportId;
    
    @Column(nullable = false)
    private Long timestamp;
    
    private String venue;
    
    @Column(nullable = false)
    private String status;
    
    private String format;
    
    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getSportId() { return sportId; }
    public void setSportId(String sportId) { this.sportId = sportId; }
    
    public Long getTimestamp() { return timestamp; }
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
    
    public String getVenue() { return venue; }
    public void setVenue(String venue) { this.venue = venue; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }
    
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
}
