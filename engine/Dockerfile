# Stage 1: Build the C++ core
FROM gcc:latest AS builder
WORKDIR /app
COPY src/ /app/src/
# Compile C++ engine directly to a Linux binary named 'engine'
RUN g++ -std=c++17 -Wall -Wextra -O2 -o engine \
    src/main.cpp src/elo.cpp src/glicko2.cpp src/poisson.cpp src/plackett_luce.cpp src/bayesian.cpp src/monte_carlo.cpp src/eval.cpp

# Stage 2: Service Runtime
FROM python:3.11-slim
WORKDIR /app

# Install runtime dependencies
RUN pip install --no-cache-dir grpcio protobuf

# Copy C++ compiled engine and Python service files
COPY --from=builder /app/engine /app/engine
RUN chmod +x /app/engine

COPY engine_service.py /app/
COPY domain_pb2.py domain_pb2_grpc.py services_pb2.py services_pb2_grpc.py /app/

# Expose port 50051
EXPOSE 50051

CMD ["python", "engine_service.py"]
