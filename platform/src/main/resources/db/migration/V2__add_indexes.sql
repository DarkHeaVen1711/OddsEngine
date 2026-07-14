CREATE INDEX idx_participants_entity ON participants(entity_id);
CREATE INDEX idx_events_timestamp ON events(timestamp);
CREATE INDEX idx_events_sport_timestamp ON events(sport_id, timestamp);
CREATE INDEX idx_rating_snapshots_entity_time ON rating_snapshots(entity_id, as_of_timestamp);
