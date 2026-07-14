import sys
import os
import time
import grpc
import threading

ENGINE_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "engine"))
sys.path.append(ENGINE_DIR)

try:
    import domain_pb2
    import services_pb2
    import services_pb2_grpc
except ImportError:
    pass

CONCURRENCY = 5
REQUESTS_PER_THREAD = 10
PORT = 50051

latencies = []
failures = 0
lock = threading.Lock()

def send_requests():
    global failures
    try:
        channel = grpc.insecure_channel(f"localhost:{PORT}")
        stub = services_pb2_grpc.PredictionServiceStub(channel)
        
        for _ in range(REQUESTS_PER_THREAD):
            request = services_pb2.PredictRequest(
                sport_id="football",
                model_name="poisson",
                participant_entity_ids=["burnley", "man_city"]
            )
            
            start = time.time()
            try:
                response = stub.PredictEvent(request, timeout=5.0)
                latency = (time.time() - start) * 1000.0
                with lock:
                    latencies.append(latency)
            except Exception as e:
                with lock:
                    failures += 1
                print(f"Request failed: {e}")
                
        channel.close()
    except Exception as e:
        with lock:
            failures += REQUESTS_PER_THREAD

def main():
    print(f"Starting gRPC concurrency load test against localhost:{PORT}...")
    print(f"Parameters: Concurrency = {CONCURRENCY}, Requests/Thread = {REQUESTS_PER_THREAD}")
    
    threads = []
    start_total = time.time()
    for _ in range(CONCURRENCY):
        t = threading.Thread(target=send_requests)
        threads.append(t)
        t.start()
        
    for t in threads:
        t.join()
        
    end_total = time.time()
    total_time = end_total - start_total
    total_requests = CONCURRENCY * REQUESTS_PER_THREAD
    
    if latencies:
        avg_latency = sum(latencies) / len(latencies)
        min_latency = min(latencies)
        max_latency = max(latencies)
    else:
        avg_latency = min_latency = max_latency = 0.0
        
    print("\n=== LOAD TEST RESULTS ===")
    print(f"Total Requests: {total_requests}")
    print(f"Successful:     {len(latencies)}")
    print(f"Failed:         {failures}")
    print(f"Total Time:     {total_time:.2f} seconds")
    if total_time > 0:
        print(f"Throughput:     {total_requests / total_time:.2f} RPS")
    print(f"Min Latency:    {min_latency:.2f} ms")
    print(f"Max Latency:    {max_latency:.2f} ms")
    print(f"Avg Latency:    {avg_latency:.2f} ms")
    print("=========================")

if __name__ == "__main__":
    main()
