import urllib.request
import csv
import json
import time
from datetime import datetime

# Fetch Premier League 23/24 data
CSV_URL = "https://www.football-data.co.uk/mmz4281/2324/E0.csv"
API_URL = "http://localhost:8080/api/ingest/batch?modelName=poisson"

def parse_date(date_str, time_str):
    try:
        # e.g., 11/08/2023, 20:00
        dt = datetime.strptime(f"{date_str} {time_str}", "%d/%m/%Y %H:%M")
        return int(dt.timestamp() * 1000)
    except:
        return int(time.time() * 1000)

def fetch_and_ingest():
    print(f"Fetching football data from {CSV_URL}...")
    req = urllib.request.Request(CSV_URL, headers={'User-Agent': 'Mozilla/5.0'})
    response = urllib.request.urlopen(req)
    lines = [line.decode('utf-8') for line in response.readlines()]
    
    reader = csv.DictReader(lines)
    payload = []
    
    count = 0
    for row in reader:
        if not row.get('HomeTeam') or not row.get('AwayTeam'):
            continue
            
        home_team = row['HomeTeam']
        away_team = row['AwayTeam']
        
        try:
            home_goals = int(row['FTHG'])
            away_goals = int(row['FTAG'])
        except (ValueError, KeyError):
            continue
            
        timestamp = parse_date(row['Date'], row.get('Time', '15:00'))
        
        home_entity_id = home_team.lower().replace(" ", "_")
        away_entity_id = away_team.lower().replace(" ", "_")
        
        event_id = f"fb_{timestamp}_{home_entity_id}_{away_entity_id}"
        
        event = {
            "id": event_id,
            "sportId": "football",
            "timestamp": timestamp,
            "venue": f"{home_team} Stadium",
            "status": "completed",
            "format": "match",
            "metadataJson": json.dumps({"fthg": home_goals, "ftag": away_goals})
        }
        
        home_participant = {
            "eventId": event_id,
            "entityId": home_entity_id,
            "resultDataJson": json.dumps({"goals": home_goals}),
            "finishRank": 1 if home_goals > away_goals else (2 if home_goals < away_goals else 1)
        }
        
        away_participant = {
            "eventId": event_id,
            "entityId": away_entity_id,
            "resultDataJson": json.dumps({"goals": away_goals}),
            "finishRank": 1 if away_goals > home_goals else (2 if away_goals < home_goals else 1)
        }
        
        entities = [
            {
                "id": home_entity_id,
                "name": home_team,
                "sportId": "football",
                "entityType": "team",
                "metadataJson": "{}"
            },
            {
                "id": away_entity_id,
                "name": away_team,
                "sportId": "football",
                "entityType": "team",
                "metadataJson": "{}"
            }
        ]
        
        payload.append({
            "event": event,
            "participants": [home_participant, away_participant],
            "entities": entities
        })
        
        count += 1
        
    print(f"Parsed {count} matches. Pushing to backend...")
    
    # POST to API
    req_api = urllib.request.Request(API_URL, data=json.dumps(payload).encode('utf-8'), headers={'Content-Type': 'application/json'})
    api_resp = urllib.request.urlopen(req_api)
    print("Backend Response:", api_resp.read().decode('utf-8'))

if __name__ == "__main__":
    fetch_and_ingest()
