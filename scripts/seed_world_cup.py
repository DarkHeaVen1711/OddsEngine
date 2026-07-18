import urllib.request
import json
import time

API_URL = "http://localhost:8080/api/ingest/batch?modelName=poisson"

def create_match(id, home, away, h_goals, a_goals, status="completed", timestamp=None):
    if timestamp is None:
        timestamp = int(time.time() * 1000)
    
    event = {
        "id": id,
        "sportId": "football",
        "timestamp": timestamp,
        "venue": "World Cup Stadium",
        "status": status,
        "format": "match",
        "metadataJson": json.dumps({"fthg": h_goals, "ftag": a_goals}) if status == "completed" else "{}"
    }
    
    home_id = home.lower().replace(" ", "_")
    away_id = away.lower().replace(" ", "_")
    
    r0 = 1 if h_goals > a_goals else (2 if h_goals < a_goals else 1)
    r1 = 1 if a_goals > h_goals else (2 if a_goals < h_goals else 1)
    
    participants = [
        {
            "eventId": id,
            "entityId": home_id,
            "resultDataJson": json.dumps({"goals": h_goals}) if status == "completed" else "{}",
            "finishRank": r0 if status == "completed" else None
        },
        {
            "eventId": id,
            "entityId": away_id,
            "resultDataJson": json.dumps({"goals": a_goals}) if status == "completed" else "{}",
            "finishRank": r1 if status == "completed" else None
        }
    ]
    
    entities = [
        {
            "id": home_id,
            "name": home,
            "sportId": "football",
            "entityType": "team",
            "metadataJson": "{}"
        },
        {
            "id": away_id,
            "name": away,
            "sportId": "football",
            "entityType": "team",
            "metadataJson": "{}"
        }
    ]
    
    return {
        "event": event,
        "participants": participants,
        "entities": entities
    }

def seed_world_cup():
    matches = [
        # Give Argentina some history
        create_match("wc_arg_fra", "Argentina", "France", 3, 3, timestamp=1671379200000),
        create_match("wc_arg_cro", "Argentina", "Croatia", 3, 0, timestamp=1670947200000),
        create_match("wc_arg_ned", "Argentina", "Netherlands", 2, 2, timestamp=1670601600000),
        
        # Give Spain some history
        create_match("wc_spa_mar", "Spain", "Morocco", 0, 0, timestamp=1670342400000),
        create_match("wc_spa_jpn", "Spain", "Japan", 1, 2, timestamp=1669910400000),
        create_match("wc_spa_crc", "Spain", "Costa Rica", 7, 0, timestamp=1669219200000),
        
        # The upcoming fixture they want to predict
        create_match("wc_spa_arg_future", "Spain", "Argentina", 0, 0, status="scheduled", timestamp=int(time.time() * 1000) + 86400000)
    ]
    
    print("Seeding World Cup data...")
    req = urllib.request.Request(API_URL, data=json.dumps(matches).encode('utf-8'), headers={'Content-Type': 'application/json'})
    response = urllib.request.urlopen(req)
    print("Backend Response:", response.read().decode('utf-8'))

if __name__ == "__main__":
    seed_world_cup()
