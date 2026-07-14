import csv
import urllib.request
import json

URL = "https://www.football-data.co.uk/mmz4281/2324/E0.csv"

def fetch_football_data():
    req = urllib.request.Request(URL, headers={'User-Agent': 'Mozilla/5.0'})
    with urllib.request.urlopen(req) as response:
        lines = [line.decode('utf-8') for line in response.readlines()]
    
    reader = csv.DictReader(lines)
    events = []
    
    for i, row in enumerate(reader):
        if i >= 10:
            break
        
        home = row['HomeTeam']
        away = row['AwayTeam']
        fthg = int(row['FTHG'])
        ftag = int(row['FTAG'])
        date = row['Date']
        
        event_id = f"ft_{i}"
        
        if fthg > ftag:
            home_rank = 1
            away_rank = 2
        elif fthg < ftag:
            home_rank = 2
            away_rank = 1
        else:
            home_rank = 1
            away_rank = 1
            
        event = {
            "id": event_id,
            "sport_id": "football",
            "timestamp": date,
            "venue": home,
            "status": "completed",
            "format": None,
            "metadata_json": json.dumps({"fthg": fthg, "ftag": ftag}),
            "participants": [
                {
                    "entity_id": home.lower().replace(" ", "_"),
                    "name": home,
                    "entity_type": "team",
                    "result_data_json": json.dumps({"goals": fthg}),
                    "finish_rank": home_rank
                },
                {
                    "entity_id": away.lower().replace(" ", "_"),
                    "name": away,
                    "entity_type": "team",
                    "result_data_json": json.dumps({"goals": ftag}),
                    "finish_rank": away_rank
                }
            ]
        }
        events.append(event)
        
    return events

if __name__ == "__main__":
    data = fetch_football_data()
    print(json.dumps(data[:2], indent=2))
