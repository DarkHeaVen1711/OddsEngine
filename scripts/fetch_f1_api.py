import urllib.request
import json
import time
from datetime import datetime

API_URL = "http://api.jolpi.ca/ergast/f1/2023/results.json?limit=1000"
INGEST_URL = "http://localhost:8080/api/ingest/batch?modelName=plackett_luce"

def parse_date(date_str, time_str):
    try:
        if not time_str:
            time_str = "00:00:00Z"
        # e.g., 2023-03-05 15:00:00Z
        dt = datetime.strptime(f"{date_str} {time_str.replace('Z', '')}", "%Y-%m-%d %H:%M:%S")
        return int(dt.timestamp() * 1000)
    except:
        return int(time.time() * 1000)

def fetch_and_ingest():
    print(f"Fetching F1 data from {API_URL}...")
    req = urllib.request.Request(API_URL, headers={'User-Agent': 'Mozilla/5.0'})
    response = urllib.request.urlopen(req)
    data = json.loads(response.read().decode('utf-8'))
    
    races = data.get("MRData", {}).get("RaceTable", {}).get("Races", [])
    payload = []
    
    for race in races:
        race_name = race.get("raceName", "Grand Prix")
        timestamp = parse_date(race.get("date"), race.get("time"))
        event_id = f"f1_{race.get('season')}_{race.get('round')}"
        
        event = {
            "id": event_id,
            "sportId": "f1",
            "timestamp": timestamp,
            "venue": race.get("Circuit", {}).get("circuitName", "Unknown"),
            "status": "completed",
            "format": "race",
            "metadataJson": json.dumps({"raceName": race_name, "season": race.get("season"), "round": race.get("round")})
        }
        
        participants = []
        entities = []
        
        for result in race.get("Results", []):
            driver = result.get("Driver", {})
            constructor = result.get("Constructor", {})
            
            driver_id = driver.get("driverId")
            if not driver_id:
                continue
                
            pos = result.get("position")
            try:
                finish_rank = int(pos)
            except:
                finish_rank = 20
                
            participants.append({
                "eventId": event_id,
                "entityId": driver_id,
                "resultDataJson": json.dumps({"position": pos, "constructor": constructor.get("constructorId"), "status": result.get("status")}),
                "finishRank": finish_rank
            })
            
            entities.append({
                "id": driver_id,
                "name": f"{driver.get('givenName')} {driver.get('familyName')}",
                "sportId": "f1",
                "entityType": "driver",
                "metadataJson": "{}"
            })
            
        if len(participants) >= 2:
            payload.append({
                "event": event,
                "participants": participants,
                "entities": entities
            })
            
    print(f"Parsed {len(payload)} F1 races. Pushing to backend...")
    
    # POST to API
    req_api = urllib.request.Request(INGEST_URL, data=json.dumps(payload).encode('utf-8'), headers={'Content-Type': 'application/json'})
    api_resp = urllib.request.urlopen(req_api)
    print("Backend Response:", api_resp.read().decode('utf-8'))

if __name__ == "__main__":
    fetch_and_ingest()
