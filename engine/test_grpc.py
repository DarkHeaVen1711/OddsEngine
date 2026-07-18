import grpc
import domain_pb2
import services_pb2
import services_pb2_grpc

def run():
    with grpc.insecure_channel('localhost:50051') as channel:
        stub = services_pb2_grpc.PredictionServiceStub(channel)
        req = services_pb2.PredictRequest(
            sport_id="football",
            participant_entity_ids=["H", "A"],
            model_name="poisson"
        )
        print("Calling PredictEvent...")
        try:
            response = stub.PredictEvent(req)
            print("Received:", response.predicted_outcome_probs_json)
        except Exception as e:
            print("Error:", e)
            
        print("\nCalling UpdateRating...")
        rating_stub = services_pb2_grpc.RatingServiceStub(channel)
        rating_req = services_pb2.EventResult(
            participants=[
                domain_pb2.Participant(entity_id="TeamA", finish_rank=1),
                domain_pb2.Participant(entity_id="TeamB", finish_rank=2)
            ]
        )
        try:
            response = rating_stub.UpdateRating(rating_req)
            for snap in response.snapshots:
                print(f"Entity: {snap.entity_id}, Rating: {snap.rating}")
        except Exception as e:
            print("Error:", e)
            
        print("\nCalling SimulateSeason...")
        sim_stub = services_pb2_grpc.SimulationServiceStub(channel)
        sim_req = services_pb2.SeasonRequest(league_id="test", n_simulations=10)
        try:
            for response in sim_stub.SimulateSeason(sim_req):
                print(f"Entity: {response.entity_id}, Ranks: {response.rank_frequencies}")
        except Exception as e:
            print("Error:", e)

if __name__ == '__main__':
    run()
