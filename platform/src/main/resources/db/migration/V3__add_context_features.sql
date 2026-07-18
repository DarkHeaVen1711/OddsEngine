CREATE TABLE context_features (
    id VARCHAR(255) PRIMARY KEY,
    entity_id VARCHAR(255) NOT NULL,
    event_id VARCHAR(255),
    feature_name VARCHAR(100) NOT NULL,
    value DOUBLE NOT NULL,
    as_of_timestamp BIGINT NOT NULL,
    sport_applicability_json TEXT,
    FOREIGN KEY (entity_id) REFERENCES entities(id) ON DELETE CASCADE,
    FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE CASCADE
);

CREATE INDEX idx_context_features_entity ON context_features(entity_id);
CREATE INDEX idx_context_features_event ON context_features(event_id);
