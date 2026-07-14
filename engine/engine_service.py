import sys
import os
import json
import time
import sqlite3
import subprocess
from concurrent import futures
import grpc

# Import generated stubs
import domain_pb2
import services_pb2
import services_pb2_grpc

DB_PATH = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "db.sqlite3"))
ENGINE_PATH = os.path.abspath(os.path.join(os.path.dirname(__file__), "engine.exe"))

# Fallbacks for platforms without compiled engine.exe (like Linux Docker container)
if not os.path.exists(ENGINE_PATH):
    linux_path = os.path.abspath(os.path.join(os.path.dirname(__file__), "engine"))
    if os.path.exists(linux_path):
        ENGINE_PATH = linux_path

def log_structured(service, method, latency_ms, status="SUCCESS", metadata=None):
    log_record = {
        "timestamp": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "service": service,
        "method": method,
        "latency_ms": round(latency_ms, 2),
        "status": status,
    }
    if metadata:
        log_record["metadata"] = metadata
    print(json.dumps(log_record), flush=True)

class RatingServiceImpl(services_pb2_grpc.RatingServiceServicer):
    def UpdateRating(self, request, context):
        start_time = time.time()
        try:
            model_name = "elo"
            if request.event.metadata_json:
                try:
                    meta = json.loads(request.event.metadata_json)
                    model_name = meta.get("model_name", "elo")
                except Exception:
                    pass
            
            conn = sqlite3.connect(DB_PATH)
            cursor = conn.cursor()
            
            participants_input = []
            for p in request.participants:
                cursor.execute(
                    "SELECT COUNT(*) FROM participants WHERE entity_id = ?",
                    (p.entity_id,)
                )
                matches_played = cursor.fetchone()[0]
                
                is_home = (request.event.venue == p.entity_id)
                
                participants_input.append({
                    "entity_id": p.entity_id,
                    "finish_rank": p.finish_rank,
                    "current_rating": p.current_rating or 1500.0,
                    "is_home": is_home,
                    "matches_played": matches_played,
                    "rating_deviation": p.rating_deviation or 350.0,
                    "volatility": p.volatility or 0.06
                })
            conn.close()

            engine_input = {
                "model_name": model_name,
                "participants": participants_input
            }
            
            proc = subprocess.Popen(
                [ENGINE_PATH, "--cli"],
                stdin=subprocess.PIPE,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True
            )
            stdout, stderr = proc.communicate(input=json.dumps(engine_input))
            
            if proc.returncode != 0:
                raise RuntimeError(f"Engine process failed with code {proc.returncode}: {stderr}")
                
            res_json = json.loads(stdout)
            
            snapshots = []
            ratings_node = res_json.get("ratings", {})
            for entity_id, val in ratings_node.items():
                if isinstance(val, (int, float)):
                    rating = float(val)
                    rd = 350.0
                    vol = 0.06
                else:
                    rating = val.get("rating", 1500.0)
                    rd = val.get("rating_deviation", 350.0)
                    vol = val.get("volatility", 0.06)
                    
                snapshots.append(domain_pb2.RatingSnapshot(
                    entity_id=entity_id,
                    sport_id=request.event.sport_id,
                    model_name=model_name,
                    rating=rating,
                    rating_deviation=rd,
                    volatility=vol,
                    as_of_timestamp=request.event.timestamp
                ))
                
            latency = (time.time() - start_time) * 1000.0
            log_structured("RatingService", "UpdateRating", latency, "SUCCESS", {"event_id": request.event.id})
            return services_pb2.RatingResponse(snapshots=snapshots)
            
        except Exception as e:
            latency = (time.time() - start_time) * 1000.0
            log_structured("RatingService", "UpdateRating", latency, "ERROR", {"error": str(e)})
            context.set_code(grpc.StatusCode.INTERNAL)
            context.set_details(str(e))
            raise

class PredictionServiceImpl(services_pb2_grpc.PredictionServiceServicer):
    def PredictEvent(self, request, context):
        start_time = time.time()
        try:
            conn = sqlite3.connect(DB_PATH)
            cursor = conn.cursor()
            
            cursor.execute("""
                SELECT e.id, e.venue, e.timestamp, p.entity_id, p.result_data_json, p.finish_rank
                FROM events e
                JOIN participants p ON e.id = p.event_id
                WHERE e.sport_id = ? AND e.status = 'completed'
            """, (request.sport_id,))
            
            rows = cursor.fetchall()
            conn.close()
            
            events_map = {}
            for row in rows:
                ev_id, venue, ts, ent_id, res_json, rank = row
                if ev_id not in events_map:
                    events_map[ev_id] = {"venue": venue, "timestamp": ts, "parts": []}
                
                goals = 0
                try:
                    if res_json:
                        goals = json.loads(res_json).get("goals", 0)
                except Exception:
                    pass
                    
                events_map[ev_id]["parts"].append({
                    "entity_id": ent_id,
                    "goals": goals,
                    "rank": rank
                })
                
            history = []
            for ev_id, data in events_map.items():
                parts = data["parts"]
                if len(parts) == 2:
                    p1, p2 = parts[0], parts[1]
                    is_p1_home = (p1["entity_id"] in data["venue"].lower() or p1["entity_id"] == data["venue"])
                    home = p1 if is_p1_home else p2
                    away = p2 if is_p1_home else p1
                    
                    history.append({
                        "home_id": home["entity_id"],
                        "away_id": away["entity_id"],
                        "home_goals": home["goals"],
                        "away_goals": away["goals"],
                        "weight": 1.0
                    })
                    
            if len(request.participant_entity_ids) < 2:
                raise ValueError("Prediction requires at least 2 participants")
                
            pred_home = request.participant_entity_ids[0]
            pred_away = request.participant_entity_ids[1]
            
            engine_input = {
                "model_name": request.model_name or "poisson",
                "history": history,
                "predict_match": {
                    "home_id": pred_home,
                    "away_id": pred_away
                }
            }
            
            proc = subprocess.Popen(
                [ENGINE_PATH, "--cli"],
                stdin=subprocess.PIPE,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True
            )
            stdout, stderr = proc.communicate(input=json.dumps(engine_input))
            
            if proc.returncode != 0:
                raise RuntimeError(f"Engine process failed with code {proc.returncode}: {stderr}")
                
            res_json = json.loads(stdout)
            probs = res_json.get("probabilities", {"win": 1.0/3.0, "draw": 1.0/3.0, "loss": 1.0/3.0})
            
            latency = (time.time() - start_time) * 1000.0
            log_structured("PredictionService", "PredictEvent", latency, "SUCCESS", {"sport_id": request.sport_id})
            
            return domain_pb2.PredictionRecord(
                event_id=f"pred_{int(time.time())}",
                model_name=request.model_name or "poisson",
                predicted_outcome_probs_json=json.dumps(probs),
                predicted_result_json=json.dumps(probs),
                generated_at=int(time.time())
            )
            
        except Exception as e:
            latency = (time.time() - start_time) * 1000.0
            log_structured("PredictionService", "PredictEvent", latency, "ERROR", {"error": str(e)})
            context.set_code(grpc.StatusCode.INTERNAL)
            context.set_details(str(e))
            raise
