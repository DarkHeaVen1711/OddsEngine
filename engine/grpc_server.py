import sys
import os
import json
import subprocess
import time
import grpc
from concurrent import futures

import domain_pb2
import domain_pb2_grpc
import services_pb2
import services_pb2_grpc

ENGINE_EXE = os.path.join(os.path.dirname(__file__), 'engine.exe')

class EngineBridge:
    def __init__(self):
        self.process = subprocess.Popen(
            [ENGINE_EXE, '--cli'],
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True
        )

    def request(self, payload_dict):
        req_str = json.dumps(payload_dict) + "\n"
        self.process.stdin.write(req_str)
        self.process.stdin.flush()
        
        response_str = self.process.stdout.readline()
        if not response_str:
            raise RuntimeError("Engine process died or returned empty response.")
        
        return json.loads(response_str)

class RatingServiceServicer(services_pb2_grpc.RatingServiceServicer):
    def __init__(self, engine):
        self.engine = engine

    def UpdateRating(self, request, context):
        payload = {
            "model_name": "elo",
            "participants": []
        }
        for p in request.participants:
            payload["participants"].append({
                "entity_id": p.entity_id,
                "finish_rank": p.finish_rank,
                "current_rating": 1500.0,
                "is_home": False,
                "matches_played": 30
            })
        
        res = self.engine.request(payload)
        
        response = services_pb2.RatingResponse()
        for eid, rating in res.get("ratings", {}).items():
            snap = domain_pb2.RatingSnapshot(
                entity_id=eid,
                model_name="elo",
                rating=rating,
                as_of_timestamp=int(time.time())
            )
            response.snapshots.append(snap)
            
        return response

class PredictionServiceServicer(services_pb2_grpc.PredictionServiceServicer):
    def __init__(self, engine):
        self.engine = engine

    def PredictEvent(self, request, context):
        payload = {
            "model_name": request.model_name or "poisson",
            "predict_match": {
                "home_id": request.participant_entity_ids[0] if len(request.participant_entity_ids) > 0 else "A",
                "away_id": request.participant_entity_ids[1] if len(request.participant_entity_ids) > 1 else "B"
            },
            "history": []
        }
        
        if payload["model_name"] == "poisson":
             payload["history"] = [
                 {"home_id": payload["predict_match"]["home_id"], "away_id": payload["predict_match"]["away_id"], "home_goals": 2, "away_goals": 1, "weight": 1.0}
             ]
             
        elif "cricket" in payload["model_name"]:
             payload["home_id"] = payload["predict_match"]["home_id"]
             payload["away_id"] = payload["predict_match"]["away_id"]
             payload["home_rating"] = 1500.0
             payload["away_rating"] = 1500.0

        res = self.engine.request(payload)
        probs = res.get("probabilities", {})
        
        return domain_pb2.PredictionRecord(
            event_id="mock_event",
            model_name=payload["model_name"],
            predicted_outcome_probs_json=json.dumps(probs),
            generated_at=int(time.time())
        )

class SimulationServiceServicer(services_pb2_grpc.SimulationServiceServicer):
    def __init__(self, engine):
        self.engine = engine

    def SimulateSeason(self, request, context):
        payload = {
            "model_name": "monte_carlo",
            "n_simulations": request.n_simulations,
            "seed": 42,
            "standings": [
                {"entity_id": "TeamA", "points": 10, "goal_diff": 5},
                {"entity_id": "TeamB", "points": 8, "goal_diff": 2}
            ],
            "fixtures": [
                {"home_id": "TeamA", "away_id": "TeamB", "win_prob": 0.5, "draw_prob": 0.3, "loss_prob": 0.2}
            ]
        }
        
        res = self.engine.request(payload)
        rank_dists = res.get("rank_distributions", {})
        
        for eid, freqs in rank_dists.items():
            yield services_pb2.SeasonSimulationResult(
                entity_id=eid,
                rank_frequencies=[int(f * request.n_simulations) for f in freqs]
            )

def serve():
    engine = EngineBridge()
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))
    services_pb2_grpc.add_RatingServiceServicer_to_server(RatingServiceServicer(engine), server)
    services_pb2_grpc.add_PredictionServiceServicer_to_server(PredictionServiceServicer(engine), server)
    services_pb2_grpc.add_SimulationServiceServicer_to_server(SimulationServiceServicer(engine), server)
    
    server.add_insecure_port('[::]:50051')
    server.start()
    print("gRPC server running on port 50051...")
    server.wait_for_termination()

if __name__ == '__main__':
    serve()
