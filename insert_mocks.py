import sqlite3
import os

DB_PATH = os.path.join(os.path.dirname(__file__), "db.sqlite3")

def insert_mocks():
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    
    # Insert Argentina and England if they don't exist
    cursor.execute("INSERT OR IGNORE INTO entities (id, name, sport_id, entity_type, metadata_json) VALUES (?, ?, ?, ?, ?)", ("argentina", "Argentina", "football", "team", "{}"))
    cursor.execute("INSERT OR IGNORE INTO entities (id, name, sport_id, entity_type, metadata_json) VALUES (?, ?, ?, ?, ?)", ("england", "England", "football", "team", "{}"))
    
    # Insert World Cup Semi Final event
    event_id = "mock_wc_semi"
    cursor.execute("INSERT OR IGNORE INTO events (id, sport_id, timestamp, venue, status, format, metadata_json) VALUES (?, ?, ?, ?, ?, ?, ?)", 
                   (event_id, "football", "1786752000000", "Lusail Stadium", "scheduled", "match", '{"competition": "world cup", "round": "semi final"}'))
    
    cursor.execute("INSERT OR IGNORE INTO participants (event_id, entity_id, result_data_json, finish_rank) VALUES (?, ?, ?, ?)", (event_id, "argentina", "{}", None))
    cursor.execute("INSERT OR IGNORE INTO participants (event_id, entity_id, result_data_json, finish_rank) VALUES (?, ?, ?, ?)", (event_id, "england", "{}", None))
    
    # Insert Belgium GP event
    gp_event_id = "mock_belgium_gp"
    cursor.execute("INSERT OR IGNORE INTO events (id, sport_id, timestamp, venue, status, format, metadata_json) VALUES (?, ?, ?, ?, ?, ?, ?)", 
                   (gp_event_id, "f1", "1786752000000", "Spa-Francorchamps", "scheduled", "race", '{"competition": "grand prix", "round": "belgium"}'))
    
    cursor.execute("INSERT OR IGNORE INTO participants (event_id, entity_id, result_data_json, finish_rank) VALUES (?, ?, ?, ?)", (gp_event_id, "max_verstappen", "{}", None))
    cursor.execute("INSERT OR IGNORE INTO participants (event_id, entity_id, result_data_json, finish_rank) VALUES (?, ?, ?, ?)", (gp_event_id, "perez", "{}", None))

    conn.commit()
    conn.close()
    print("Mock events inserted successfully!")

if __name__ == "__main__":
    insert_mocks()
