<div align="center">

<img src="https://img.shields.io/badge/C%2B%2B-17-00599C?style=for-the-badge&logo=cplusplus&logoColor=white"/>
<img src="https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white"/>
<img src="https://img.shields.io/badge/Spring_Boot-3.x-6DB33F?style=for-the-badge&logo=springboot&logoColor=white"/>
<img src="https://img.shields.io/badge/Next.js-15-000000?style=for-the-badge&logo=nextdotjs&logoColor=white"/>
<img src="https://img.shields.io/badge/gRPC-Protobuf-4285F4?style=for-the-badge&logo=google&logoColor=white"/>
<img src="https://img.shields.io/badge/SQLite-embedded-003B57?style=for-the-badge&logo=sqlite&logoColor=white"/>

# ⚡ OddsEngine

### A Multi-Sport Statistical Rating & Match Prediction Engine

*Probabilistic match outcome prediction powered by Elo, Glicko-2, Bivariate Poisson, and Plackett-Luce models — built for football, cricket, F1, and beyond.*

</div>

---

## Overview

**OddsEngine** is a production-grade, multi-sport match prediction platform. At its core is a **C++ statistical engine** implementing four different mathematical rating and prediction models, exposed via a **Java/Spring Boot REST API** with a **Next.js frontend**.

The engine was designed from the ground up with a strict separation between:
- **Statistical computation** — high-performance C++ numerical solvers
- **Orchestration & persistence** — Java platform with JPA and SQLite
- **API surface** — RESTful endpoints and gRPC contracts
- **Presentation** — Next.js web dashboard

A live demonstration of the system predicted the **2026 FIFA World Cup semi-final** (England vs Argentina) using real tournament match data, producing calibrated win/draw/loss probabilities via the Bivariate Poisson model.

---

## Statistical Models

### 1. Generalised Elo-for-N (`engine/src/elo.*`)
The classical Elo rating system, extended for N-way competitions:
- **Home-field advantage** offset ($H = 100$ rating points)
- **K-factor experience decay**: $K = 32$ for provisional entities (< 30 matches), $K = 16$ for established
- Works for any sport with a pairwise win/loss/draw outcome

### 2. Glicko-2 (`engine/src/glicko2.*`)
Marks Glickman's full Glicko-2 implementation with volatility tracking:
- Converts to/from the internal Glicko-2 scale ($\mu$, $\phi$, $\sigma$)
- **Illinois algorithm** (regula falsi) root-finding for volatility updates — O(1) convergence, iteration-guarded against infinite loops
- Tracks rating *deviation* (confidence intervals) and *volatility* (consistency)
- Verified against Glickman's worked example: $r \approx 1464.06$, $RD \approx 151.52$, $\sigma \approx 0.05999$

### 3. Bivariate Poisson Scoreline Model (`engine/src/poisson.*`)
Implements the **Karlis & Ntzoufras (2003)** bivariate Poisson formulation for football/soccer:

$$P(X=x, Y=y) = e^{-(\lambda_1+\lambda_2+\lambda_3)} \sum_{k=0}^{\min(x,y)} \frac{\lambda_1^{x-k}\,\lambda_2^{y-k}\,\lambda_3^k}{(x-k)!\,(y-k)!\,k!}$$

Where:
- $\lambda_A = \alpha_A \cdot \beta_B \cdot \gamma$ — expected home goals (attack × opponent defence × home advantage)
- $\lambda_B = \alpha_B \cdot \beta_A$ — expected away goals
- $\lambda_3$ — score correlation coefficient (captures low-scoring game dependency)

Features:
- **Time-decay weighting** on historical matches (exponential decay, recent = higher weight)
- **MLE parameter fitting** via numerical gradient descent
- **Identifiability normalisation**: attack strengths scaled to average 1.0
- Full $10 \times 10$ **scoreline probability matrix** → aggregated Win / Draw / Loss

### 4. Plackett-Luce N-Way Model (`engine/src/plackett_luce.*`)
Purpose-built for **F1 and N-way ranking sports**:

$$P(\pi_1, \pi_2, \dots, \pi_M) = \prod_{i=1}^{M} \frac{\theta_i}{\sum_{j=i}^{M}\theta_j}$$

Where participant strength is factored as:

$$\theta_i = \theta_{D(i)} \cdot \theta_{C(i)} \cdot e^{-\alpha \cdot G(i)}$$

- $\theta_D$ — driver skill parameter
- $\theta_C$ — constructor/car performance parameter
- $\alpha$ — grid position penalty (fitted from data, bounded $[0.01, 0.20]$)
- **Constructor-specific DNF reliability rates** modelled separately from pace
- Parameters normalised post-fit for identifiability

---

## Architecture

```
OddsEngine/
├── proto/                          # gRPC & Protobuf contracts
│   ├── domain.proto                # Entity, Event, Participant, RatingSnapshot
│   └── services.proto              # RatingService, PredictionService, SimulationService
│
├── engine/                         # C++ Statistical Core
│   ├── src/
│   │   ├── elo.{hpp,cpp}           # Generalised Elo-for-N
│   │   ├── glicko2.{hpp,cpp}       # Glicko-2 with Illinois volatility solver
│   │   ├── poisson.{hpp,cpp}       # Bivariate Poisson scoreline model
│   │   ├── plackett_luce.{hpp,cpp} # Plackett-Luce F1/N-way ranking model
│   │   └── main.cpp                # CLI entrypoint + unit test suite
│   └── Makefile                    # MinGW g++ build (C++17)
│
├── platform/                       # Java/Spring Boot Platform
│   └── src/main/java/com/oddsengine/
│       ├── model/                  # JPA entities (SportEntity, Event, Participant, RatingSnapshot)
│       ├── repository/             # Spring Data JPA repositories
│       ├── service/
│       │   ├── EngineClient.java   # C++ subprocess bridge (ProcessBuilder → JSON)
│       │   └── RatingOrchestrator.java  # Orchestrates ratings + snapshots
│       └── controller/
│           └── PredictionController.java  # REST API endpoints
│
├── frontend/                       # Next.js 15 Web Dashboard
├── scripts/                        # Data scraping + DB seeding (Python)
│   ├── fetch_football.py
│   ├── fetch_cricket.py
│   ├── fetch_f1.py
│   └── db_seed.py
└── OddsEngine_Implementation_Tasksheet.md  # Full implementation roadmap
```

### Data Flow

```
REST Request
     │
     ▼
PredictionController (Java)
     │
     ▼
RatingOrchestrator
  ├── Fetches current ratings from SQLite (JPA)
  ├── Calculates matches played (K-factor / RD scaling)
  └── Invokes EngineClient
           │
           ▼  JSON over stdin/stdout
      engine.exe (C++)
        ├── Routes by "model_name"
        ├── Runs statistical computation
        └── Returns JSON result
           │
           ▼
  RatingOrchestrator
  └── Persists RatingSnapshot to SQLite
```

---

## Building

### Prerequisites
- **C++**: MinGW-w64 / g++ (C++17) on Windows, or GCC/Clang on Linux/macOS
- **Java**: JDK 21+, Gradle
- **Frontend**: Node.js 20+

### C++ Engine

```bash
cd engine
mingw32-make        # Build engine.exe
mingw32-make test   # Build + run all unit tests
mingw32-make clean  # Remove build artifacts
```

**Expected test output:**
```
All statistical core tests passed successfully!
```

The test suite covers:
- Pairwise Elo updates (established + provisional K-factors)
- N-way Elo with home advantage
- Glicko-2 parameter recovery (vs Glickman's paper)
- Poisson scoreline matrix validation
- Plackett-Luce parameter recovery for F1-style results

### Java Platform

```bash
cd platform
./gradlew bootRun       # Start Spring Boot server (port 8080)
./gradlew test          # Run integration tests
```

---

## CLI Usage

The engine accepts a single JSON line on `stdin` and writes a JSON result to `stdout`.

### Elo Model
```bash
echo '{"model_name":"elo","participants":[{"entity_id":"TeamA","finish_rank":1,"current_rating":1500.0},{"entity_id":"TeamB","finish_rank":2,"current_rating":1500.0}]}' | ./engine.exe --cli
```

### Glicko-2 Model
```bash
echo '{"model_name":"glicko2","participants":[{"entity_id":"P1","finish_rank":1,"current_rating":1500.0,"rating_deviation":200.0,"volatility":0.06},...]}' | ./engine.exe --cli
```

### Bivariate Poisson (Football Prediction)
```bash
echo '{
  "model_name": "poisson",
  "history": [
    {"home_id":"ENG","away_id":"ARG","home_goals":4,"away_goals":2,"weight":0.95},
    ...
  ],
  "predict_match": {"home_id":"ENG","away_id":"ARG"}
}' | ./engine.exe --cli
# → {"probabilities": {"win": 0.547431, "draw": 0.274523, "loss": 0.178047}}
```

### Plackett-Luce (F1 Prediction)
```bash
echo '{
  "model_name": "plackett_luce",
  "history": [...],
  "predict_entrants": [
    {"entity_id":"VER","driver_id":"VER","constructor_id":"RBR","grid_position":1},
    ...
  ]
}' | ./engine.exe --cli
# → {"probabilities": {"VER": 0.38, "HAM": 0.22, ...}}
```

---

## REST API

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/events/process` | Process an event and update ratings |
| `GET` | `/api/ratings/{entityId}` | Fetch rating history for an entity |
| `POST` | `/api/ingest/batch` | Ingest bulk JSON payloads of EventWrappers |

### Example — Batch Ingestion (Phase 2.2 Live Adapters)
You can ingest bulk match data from external providers (football-data.co.uk, Cricsheet, Jolpica F1) using the included Python adapters:
```bash
python scripts/fetch_football_api.py   # Premier League CSV -> JSON Ingestion
python scripts/fetch_cricket_api.py    # Cricsheet match data
python scripts/fetch_f1_api.py         # Jolpica F1 race results
```
This pushes JSON `EventWrapper` payloads to the Spring Boot backend, automatically deduplicating fixtures, mapping entities, and triggering the underlying statistical model updates.

### Example — Process a Football Match
```json
POST /api/events/process
{
  "eventId": "wc2026-sf-1",
  "sportId": "football",
  "modelName": "poisson",
  "venue": "Atlanta Stadium",
  "participants": [
    { "entityId": "ENG", "finishRank": 1 },
    { "entityId": "ARG", "finishRank": 2 }
  ]
}
```

---

## Live Prediction Demo

Using the Bivariate Poisson model with **27 real match records** (2026 World Cup tournament data + 2025 qualifiers + 2024 Copa América), the engine produced the following prediction for the **2026 FIFA World Cup Semi-Final**:

| 🏴󠁧󠁢󠁥󠁮󠁧󠁿 England | ➖ Draw | 🇦🇷 Argentina |
|:---:|:---:|:---:|
| **54.7%** | **27.5%** | **17.8%** |

Data was weighted by recency (exponential decay: WC knockouts `0.97–0.99`, qualifiers `0.55–0.68`), and attack/defense parameters were fitted via MLE gradient descent.

---

## Implementation Roadmap

| Phase | Description | Status |
|-------|-------------|--------|
| **0** | Architecture, Protobuf contracts, DB schema | ✅ Complete |
| **0.3** | Data feasibility spike (scraping scripts) | ✅ Complete |
| **1–2** | Java platform, JPA models, REST API | ✅ Complete |
| **1.1** | Elo refinements (home advantage, K-decay) | ✅ Complete |
| **1.2** | Glicko-2 module (volatility, Illinois solver) | ✅ Complete |
| **1.3** | Bivariate Poisson scoreline model | ✅ Complete |
| **1.3b** | Plackett-Luce F1/N-way ranking model | ✅ Complete |
| **1.3c** | Cricket format-aware model | 🚧 In Progress |
| **1.4** | Bayesian updating layer | 🔜 Upcoming |
| **1.5** | Monte Carlo season simulator | 🔜 Upcoming |
| **1.6** | Model evaluation & backtesting utilities | 🚧 In Progress |
| **1.7** | gRPC service layer | 🔜 Upcoming |
| **5a** | Chat: Fixture Resolution & Prediction | ✅ Complete |
| **5b** | Chat: Conversational Analytics | 🔜 Upcoming |
| **6** | Context-Aware Feature Layer | 🔜 Upcoming |
| **7** | UI Redesign (hybrid chat/dashboard) | 🔜 Upcoming |

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Statistical Core | C++17, MinGW-w64 |
| Platform | Java 21, Spring Boot 3, Gradle |
| Database | SQLite (via JPA/Hibernate) |
| Migrations | Flyway |
| Contracts | gRPC, Protocol Buffers |
| Frontend | Next.js 15 |
| Data Pipeline | Python 3 (requests, sqlite3) |

---

## Contributing

This project follows strict micro-commit discipline:
- All commits are **< 100 lines** of changes
- Features are developed on **sub-branches**, merged with `--no-ff`
- Branch topology is preserved for clean history inspection

---

<div align="center">

Built with rigorous statistical foundations and an eye toward production-grade architecture.

*Elo · Glicko-2 · Bivariate Poisson · Plackett-Luce*

</div>
