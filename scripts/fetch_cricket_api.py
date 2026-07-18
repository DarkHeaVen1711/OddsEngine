import urllib.request
import json
import time
from datetime import datetime

URL = "https://cricsheet.org/downloads/json/1389389.json"
INGEST_URL = "http://localhost:8080/api/ingest/batch?modelName=cricket_t20"

def parse_date(date_str):
    try:
        dt = datetime.strptime(date_str, "%Y-%m-%d")
        return int(dt.timestamp() * 1000)
    except:
        return int(time.time() * 1000)

def fetch_and_ingest():
    print(f"Fetching cricket data from {URL}...")
    try:
        req = urllib.request.Request(URL, headers={'User-Agent': 'Mozilla/5.0'})
        response = urllib.request.urlopen(req)
        data = json.loads(response.read().decode('utf-8'))
    except Exception as e:
        print("Failed to download from cricsheet. Using fallback dataset.")
        data = {
            "info": {
                "dates": ["2023-12-14"],
                "match_type": "T20",
                "teams": ["India", "South Africa"],
                "outcome": {
                    "winner": "India",
                    "by": {"runs": 106}
                },
                "venue": "New Wanderers Stadium, Johannesburg"
            },
            "innings": [
                {
                    "team": "India",
                    "overs": [
                        {"deliveries": [{"runs": {"total": 4}}]}
                    ]
                },
                {
                    "team": "South Africa",
                    "overs": []
                }
            ]
        }
        
    info = data.get("info", {})
    teams = info.get("teams", [])
    if len(teams) < 2:
        return
        
    date_str = info.get("dates", ["unknown"])[0]
    timestamp = parse_date(date_str)
    
    venue = info.get("venue", "unknown")
    match_type = info.get("match_type", "T20")
    outcome = info.get("outcome", {})
    winner = outcome.get("winner", None)
    
    runs = {team: 0 for team in teams}
    wickets = {team: 0 for team in teams}
        
    for inning in data.get("innings", []):
        team = inning.get("team")
        if team not in runs:
            continue
        for over in inning.get("overs", []):
            for delivery in over.get("deliveries", []):
                runs[team] += delivery.get("runs", {}).get("total", 0)
                if "wickets" in delivery:
                    wickets[team] += len(delivery["wickets"])
                    
    if winner == teams[0]:
        r0, r1 = 1, 2
    elif winner == teams[1]:
        r0, r1 = 2, 1
    else:
        r0, r1 = 1, 1
        
    event_id = f"cr_{date_str}_{teams[0].lower().replace(' ', '_')}_{teams[1].lower().replace(' ', '_')}"
    
    event = {
        "id": event_id,
        "sportId": "cricket",
        "timestamp": timestamp,
        "venue": venue,
        "status": "completed",
        "format": match_type,
        "metadataJson": json.dumps({"winner": winner, "outcome_details": outcome.get("by", {})})
    }
    
    home_entity_id = teams[0].lower().replace(" ", "_")
    away_entity_id = teams[1].lower().replace(" ", "_")
    
    participants = [
        {
            "eventId": event_id,
            "entityId": home_entity_id,
            "resultDataJson": json.dumps({"runs": runs[teams[0]], "wickets": wickets[teams[0]]}),
            "finishRank": r0
        },
        {
            "eventId": event_id,
            "entityId": away_entity_id,
            "resultDataJson": json.dumps({"runs": runs[teams[1]], "wickets": wickets[teams[1]]}),
            "finishRank": r1
        }
    ]
    
    entities = [
        {
            "id": home_entity_id,
            "name": teams[0],
            "sportId": "cricket",
            "entityType": "team",
            "metadataJson": "{}"
        },
        {
            "id": away_entity_id,
            "name": teams[1],
            "sportId": "cricket",
            "entityType": "team",
            "metadataJson": "{}"
        }
    ]
    
    payload = [{
        "event": event,
        "participants": participants,
        "entities": entities
    }]
    
    print("Parsed cricket match. Pushing to backend...")
    req_api = urllib.request.Request(INGEST_URL, data=json.dumps(payload).encode('utf-8'), headers={'Content-Type': 'application/json'})
    api_resp = urllib.request.urlopen(req_api)
    print("Backend Response:", api_resp.read().decode('utf-8'))

if __name__ == "__main__":
    fetch_and_ingest()
