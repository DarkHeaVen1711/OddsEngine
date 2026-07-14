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

class SimulationServiceImpl(services_pb2_grpc.SimulationServiceServicer):
    def SimulateSeason(self, request, context):
        start_time = time.time()
        try:
            conn = sqlite3.connect(DB_PATH)
            cursor = conn.cursor()
            
            cursor.execute("SELECT id, name FROM entities")
            entities = cursor.fetchall()
            
            mc_standings = []
            for ent_id, name in entities:
                mc_standings.append({
                    "entity_id": ent_id,
                    "points": 0,
                    "goal_diff": 0
                })
                
            cursor.execute("""
                SELECT e.id, e.venue, p.entity_id, p.result_data_json, p.finish_rank
                FROM events e
                JOIN participants p ON e.id = p.event_id
                WHERE e.status = 'completed'
            """)
            rows = cursor.fetchall()
            
            events_map = {}
            for row in rows:
                ev_id, venue, ent_id, res_json, rank = row
                if ev_id not in events_map:
                    events_map[ev_id] = {"venue": venue, "parts": []}
                goals = 0
                try:
                    if res_json:
                        goals = json.loads(res_json).get("goals", 0)
                except Exception:
                    pass
                events_map[ev_id]["parts"].append({"entity_id": ent_id, "goals": goals, "rank": rank})
                
            for ev_id, data in events_map.items():
                parts = data["parts"]
                if len(parts) == 2:
                    p1, p2 = parts[0], parts[1]
                    is_p1_home = (p1["entity_id"] in data["venue"].lower() or p1["entity_id"] == data["venue"])
                    home = p1 if is_p1_home else p2
                    away = p2 if is_p1_home else p1
                    
                    for s in mc_standings:
                        if s["entity_id"] == home["entity_id"]:
                            s["goal_diff"] += (home["goals"] - away["goals"])
                            if home["goals"] > away["goals"]: s["points"] += 3
                            elif home["goals"] == away["goals"]: s["points"] += 1
                        elif s["entity_id"] == away["entity_id"]:
                            s["goal_diff"] += (away["goals"] - home["goals"])
                            if away["goals"] > home["goals"]: s["points"] += 3
                            elif home["goals"] == away["goals"]: s["points"] += 1
                            
            cursor.execute("""
                SELECT e.id, e.venue, p.entity_id
                FROM events e
                JOIN participants p ON e.id = p.event_id
                WHERE e.status = 'scheduled'
            """)
            fixtures_rows = cursor.fetchall()
            conn.close()
            
            fixtures_map = {}
            for r in fixtures_rows:
                ev_id, venue, ent_id = r
                if ev_id not in fixtures_map:
                    fixtures_map[ev_id] = {"venue": venue, "parts": []}
                fixtures_map[ev_id]["parts"].append(ent_id)
                
            mc_fixtures = []
            for ev_id, data in fixtures_map.items():
                parts = data["parts"]
                if len(parts) == 2:
                    mc_fixtures.append({
                        "home_id": parts[0],
                        "away_id": parts[1],
                        "win_prob": 0.45,
                        "draw_prob": 0.28,
                        "loss_prob": 0.27
                    })
                    
            if not mc_fixtures:
                mc_fixtures.append({
                    "home_id": mc_standings[0]["entity_id"],
                    "away_id": mc_standings[1]["entity_id"],
                    "win_prob": 0.45,
                    "draw_prob": 0.28,
                    "loss_prob": 0.27
                })
                
            engine_input = {
                "model_name": "monte_carlo",
                "n_simulations": request.n_simulations or 10000,
                "seed": 42,
                "standings": mc_standings,
                "fixtures": mc_fixtures
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
            distributions = res_json.get("rank_distributions", {})
            
            latency = (time.time() - start_time) * 1000.0
            log_structured("SimulationService", "SimulateSeason", latency, "SUCCESS", {"league_id": request.league_id})
            
            for entity_id, probs in distributions.items():
                frequencies = [int(p * (request.n_simulations or 10000)) for p in probs]
                yield services_pb2.SeasonSimulationResult(
                    entity_id=entity_id,
                    rank_frequencies=frequencies
                )
                
        except Exception as e:
            latency = (time.time() - start_time) * 1000.0
            log_structured("SimulationService", "SimulateSeason", latency, "ERROR", {"error": str(e)})
            context.set_code(grpc.StatusCode.INTERNAL)
            context.set_details(str(e))
            raise

def serve():
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))
    services_pb2_grpc.add_RatingServiceServicer_to_server(RatingServiceImpl(), server)
    services_pb2_grpc.add_PredictionServiceServicer_to_server(PredictionServiceImpl(), server)
    services_pb2_grpc.add_SimulationServiceServicer_to_server(SimulationServiceImpl(), server)
    
    server.add_insecure_port('[::]:50051')
    print("Starting gRPC Server on port 50051...", flush=True)
    server.start()
    try:
        while True:
            time.sleep(86400)
    except KeyboardInterrupt:
        server.stop(0)

if __name__ == '__main__':
    serve()
