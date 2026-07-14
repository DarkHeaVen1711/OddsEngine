package com.oddsengine.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "entities")
public class SportEntity {
    @Id
    private String id;
    
    @Column(nullable = false)
    private String name;
    
    @Column(name = "sport_id", nullable = false)
    private String sportId;
    
    @Column(name = "entity_type", nullable = false)
    private String entityType;
    
    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getSportId() { return sportId; }
    public void setSportId(String sportId) { this.sportId = sportId; }
    
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
}
