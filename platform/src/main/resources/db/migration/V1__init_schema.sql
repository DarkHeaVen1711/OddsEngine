CREATE TABLE entities (
    id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    sport_id VARCHAR(255) NOT NULL,
    entity_type VARCHAR(255) NOT NULL,
    metadata_json TEXT
);

CREATE TABLE events (
    id VARCHAR(255) PRIMARY KEY,
    sport_id VARCHAR(255) NOT NULL,
    timestamp BIGINT NOT NULL,
    venue VARCHAR(255),
    status VARCHAR(50) NOT NULL,
    format VARCHAR(50),
    metadata_json TEXT
);

CREATE TABLE participants (
    event_id VARCHAR(255) NOT NULL,
    entity_id VARCHAR(255) NOT NULL,
    result_data_json TEXT,
    finish_rank INTEGER,
    PRIMARY KEY (event_id, entity_id),
    FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE CASCADE,
    FOREIGN KEY (entity_id) REFERENCES entities(id) ON DELETE CASCADE
);

CREATE TABLE rating_snapshots (
    entity_id VARCHAR(255) NOT NULL,
    sport_id VARCHAR(255) NOT NULL,
    model_name VARCHAR(100) NOT NULL,
    rating DOUBLE NOT NULL,
    rating_deviation DOUBLE,
    volatility DOUBLE,
    as_of_timestamp BIGINT NOT NULL,
    PRIMARY KEY (entity_id, sport_id, model_name, as_of_timestamp),
    FOREIGN KEY (entity_id) REFERENCES entities(id) ON DELETE CASCADE
);

CREATE TABLE prediction_records (
    event_id VARCHAR(255) NOT NULL,
    model_name VARCHAR(100) NOT NULL,
    predicted_outcome_probs_json TEXT,
    predicted_result_json TEXT,
    generated_at BIGINT NOT NULL,
    actual_result_json TEXT,
    PRIMARY KEY (event_id, model_name),
    FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE CASCADE
);
