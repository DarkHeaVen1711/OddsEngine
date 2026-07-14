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
