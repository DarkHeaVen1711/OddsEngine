import urllib.request
import json
import sys

def predict():
    url = "http://localhost:8080/api/chat/predict"
    payload = {
        "query": "predict spain vs argentina",
        "sessionId": "test-session-1"
    }
    req = urllib.request.Request(url, data=json.dumps(payload).encode('utf-8'), headers={'Content-Type': 'application/json'})
    
    try:
        response = urllib.request.urlopen(req)
        data = json.loads(response.read().decode('utf-8'))
        print(json.dumps(data, indent=2))
    except Exception as e:
        print(f"Error: {e}")

if __name__ == "__main__":
    predict()
