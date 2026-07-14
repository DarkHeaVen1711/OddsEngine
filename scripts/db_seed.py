import sqlite3
import os
import json
from fetch_football import fetch_football_data
from fetch_cricket import fetch_cricket_data
from fetch_f1 import fetch_f1_data

DB_PATH = os.path.join(os.path.dirname(__file__), "..", "db.sqlite3")

DDL = [
    """
    CREATE TABLE IF NOT EXISTS entities (
        id TEXT PRIMARY KEY,
        name TEXT NOT NULL,
        sport_id TEXT NOT NULL,
        entity_type TEXT NOT NULL,
        metadata_json TEXT
    );
    """,
    """
    CREATE TABLE IF NOT EXISTS events (
        id TEXT PRIMARY KEY,
        sport_id TEXT NOT NULL,
        timestamp TEXT NOT NULL,
        venue TEXT,
        status TEXT NOT NULL,
        format TEXT,
        metadata_json TEXT
    );
    """,
    """
    CREATE TABLE IF NOT EXISTS participants (
        event_id TEXT NOT NULL,
        entity_id TEXT NOT NULL,
        result_data_json TEXT,
        finish_rank INTEGER,
        PRIMARY KEY (event_id, entity_id),
        FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE CASCADE,
        FOREIGN KEY (entity_id) REFERENCES entities(id) ON DELETE CASCADE
    );
    """
]

def seed_db():
    print(f"Connecting to SQLite database at: {DB_PATH}")
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    
    for statement in DDL:
        cursor.execute(statement)
        
    print("Fetching feasibility data...")
    football_events = fetch_football_data()
    cricket_events = fetch_cricket_data()
    f1_events = fetch_f1_data()
    
    all_events = football_events + cricket_events + f1_events
    
    entity_count = 0
    event_count = 0
    participant_count = 0
    
    for event in all_events:
        cursor.execute(
            "INSERT OR IGNORE INTO events (id, sport_id, timestamp, venue, status, format, metadata_json) VALUES (?, ?, ?, ?, ?, ?, ?)",
            (event["id"], event["sport_id"], str(event["timestamp"]), event["venue"], event["status"], event["format"], event["metadata_json"])
        )
        if cursor.rowcount > 0:
            event_count += 1
            
        for participant in event["participants"]:
            cursor.execute(
                "INSERT OR IGNORE INTO entities (id, name, sport_id, entity_type, metadata_json) VALUES (?, ?, ?, ?, ?)",
                (participant["entity_id"], participant["name"], event["sport_id"], participant["entity_type"], "{}")
            )
            if cursor.rowcount > 0:
                entity_count += 1
                
            cursor.execute(
                "INSERT OR IGNORE INTO participants (event_id, entity_id, result_data_json, finish_rank) VALUES (?, ?, ?, ?)",
                (event["id"], participant["entity_id"], participant["result_data_json"], participant["finish_rank"])
            )
            if cursor.rowcount > 0:
                participant_count += 1
                
    conn.commit()
    conn.close()
    
    print(f"Successfully seeded: {entity_count} entities, {event_count} events, {participant_count} participants.")

if __name__ == "__main__":
    seed_db()
