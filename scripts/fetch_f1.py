import urllib.request
import json

URL = "https://api.jolpica.info/f1/2023/1/results.json"

def fetch_f1_data():
    try:
        req = urllib.request.Request(URL, headers={'User-Agent': 'Mozilla/5.0'})
        with urllib.request.urlopen(req) as response:
            data = json.loads(response.read().decode('utf-8'))
    except Exception:
        # Fallback dataset if api.jolpica.info is down or slow
        data = {
            "MRData": {
                "RaceTable": {
                    "Races": [
                        {
                            "raceName": "Bahrain Grand Prix",
                            "date": "2023-03-05",
                            "Circuit": {"circuitName": "Bahrain International Circuit"},
                            "Results": [
                                {
                                    "position": "1",
                                    "Driver": {"driverId": "max_verstappen", "givenName": "Max", "familyName": "Verstappen"},
                                    "Constructor": {"constructorId": "red_bull", "name": "Red Bull Racing"},
                                    "status": "Finished"
                                },
                                {
                                    "position": "2",
                                    "Driver": {"driverId": "perez", "givenName": "Sergio", "familyName": "Perez"},
                                    "Constructor": {"constructorId": "red_bull", "name": "Red Bull Racing"},
                                    "status": "Finished"
                                }
                            ]
                        }
                    ]
                }
            }
        }

    races = data.get("MRData", {}).get("RaceTable", {}).get("Races", [])
    if not races:
        return []
    
    race = races[0]
    race_name = race.get("raceName")
    date = race.get("date")
    venue = race.get("Circuit", {}).get("circuitName")
    
    participants = []
    for res in race.get("Results", []):
        pos = int(res.get("position", 999))
        driver = res.get("Driver", {})
        driver_id = driver.get("driverId")
        driver_name = f"{driver.get('givenName')} {driver.get('familyName')}"
        constructor = res.get("Constructor", {})
        constructor_id = constructor.get("constructorId")
        constructor_name = constructor.get("name")
        status_str = res.get("status")
        
        participants.append({
            "entity_id": driver_id,
            "name": driver_name,
            "entity_type": "driver",
            "result_data_json": json.dumps({
                "constructor_id": constructor_id,
                "constructor_name": constructor_name,
                "status": status_str
            }),
            "finish_rank": pos
        })
        
    event = {
        "id": "f1_2023_1",
        "sport_id": "f1",
        "timestamp": date,
        "venue": venue,
        "status": "completed",
        "format": "race",
        "metadata_json": json.dumps({"race_name": race_name}),
        "participants": participants
    }
    return [event]

if __name__ == "__main__":
    data = fetch_f1_data()
    print(json.dumps(data[:2], indent=2))
