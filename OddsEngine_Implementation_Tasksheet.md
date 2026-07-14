# OddsEngine — Implementation Tasksheet
### Statistical Sports Outcome Prediction Platform (C++ Stats Core + Java Full-Stack Platform)

Sport-agnostic engine. All models operate on a generic `Match { entityA, entityB, scoreA, scoreB, timestamp, venue, metadata }` abstraction so any sport (football, cricket, basketball, esports) can be plugged in via an adapter.

---

## PHASE 0 — Architecture & Contracts

**0.1 Define core domain model (N-way from day one)**
- `Entity` (team/driver/player): id, name, sport_id, entity_type (team | driver | constructor — needed because F1 has two rated things per participant), metadata (JSON blob for sport-specific attrs)
- `Event` (replaces the old pairwise `Match`): id, sport_id, timestamp, venue, status (scheduled/completed/live), format (nullable — e.g. "T20"/"ODI"/"Test" for cricket, "race"/"sprint" for F1), metadata (JSON)
- `Participant` (child of Event, 2 for football/cricket, 20+ for F1): event_id, entity_id, result_data (JSON — sport-specific: {goals} for football, {runs, wickets, overs} for cricket, {finish_position, grid_position, dnf, fastest_lap} for F1), finish_rank (nullable, integer — the normalized "where did this entity place" field every sport can populate, used by the baseline model in 1.0 below)
- `RatingSnapshot`: entity_id, sport_id, model_name, rating, rating_deviation (nullable), volatility (nullable), as_of_timestamp
- `PredictionRecord`: event_id, model_name, predicted_outcome_probs (JSON — shape varies: {win,draw,loss} for pairwise, {P(rank=1)...P(rank=N)} for N-way), predicted_result (nullable, JSON), generated_at, actual_result (nullable until event completes)

This schema change is the single most important fix versus the original draft — `Event`/`Participant` can represent 2 participants or 20 without any structural change, and `finish_rank` gives every sport a common denominator for the baseline model in Phase 1.0, even before sport-specific models exist.

**0.2 Define the C++↔Java service contract (N-way aware)**
- Choose gRPC (recommended — strong typing, streaming support for live updates) over REST/JSON for the inter-service boundary
- Draft `.proto` files around the Event/Participant shape, not a hardcoded pairwise one:
  - `RatingService`: `UpdateRating(EventResult) → List<RatingSnapshot>` — takes the full participant list, not just A/B, so it works unchanged for F1's 20-entrant races
  - `PredictionService`: `PredictEvent(sport_id, participant_entity_ids, as_of) → PredictionRecord`
  - `SimulationService`: `SimulateSeason(league_id, n_simulations) → SeasonSimulationResult`
- Decide serialization for large batch jobs (e.g. season simulation) — stream results back rather than one giant response

**0.3 Data feasibility spike (do this before writing any model code)**
- For each sport, confirm a concrete, licensable data source and pull a real sample before committing further:
  - **Football**: football-data.org (or similar) free-tier API — confirm rate limits and historical depth
  - **Cricket**: Cricsheet.org structured data dumps (ball-by-ball, YAML/JSON) — safer and more stable than scraping Cricinfo directly, which has fragile page structure and ToS restrictions
  - **F1**: Jolpica-F1 (the community successor to the now-frozen Ergast API) for historical results; confirm season coverage, since the original Ergast API stopped live updates in 2024
- Deliverable: one small script per sport that pulls ~1 season of real data into local Postgres, so Phase 2's ingestion adapters are built against real payload shapes, not assumptions
- **This phase blocks Phase 1.1 onward.** Do not write model code against a schema you haven't validated against real data first — this is the step that was missing before and is where an "all three sports" MVP most commonly stalls.

**0.4 Repo & build setup**
- Monorepo layout: `/engine` (C++), `/platform` (Java/Spring Boot), `/frontend` (Next.js), `/proto` (shared .proto files)
- C++: CMake + vcpkg for dependencies (Eigen, gRPC, Google Test)
- Java: Gradle multi-module, Spring Boot 3.x, gRPC Java codegen plugin
- CI: GitHub Actions — build both engine and platform, run unit tests, lint

---

## PHASE 1 — C++ Statistical Engine

**Sequencing principle: baseline first, across all three sports, before any specialized model.** You said statistical rigor matters most — rigor means validating against real data, and you can't validate a model you haven't shipped. Ship 1.0 for football, cricket, and F1 together first (this alone proves the N-way schema and gRPC contract work end-to-end for all three). Only then move to the specialized upgrade per sport (1.2–1.4), one sport at a time, in whatever order your data feasibility spike (0.3) showed to be cleanest — likely football first, since its data source is the most reliable.

### 1.0 Baseline: Generalized Elo-for-N (ship this for all 3 sports first)
- Implement a **generalized pairwise Elo** that decomposes any N-way `Event` into all `C(N,2)` implied pairwise outcomes based on `finish_rank` (entity that finished higher = "won" the pairwise comparison against every entity that finished lower), then applies the standard Elo update to each pair
- This is a known, simple way to bootstrap a multi-entrant ranking system before investing in true Plackett-Luce/TrueSkill — for football/cricket (N=2) it collapses to plain Elo with no special-casing needed
- Deliberately crude for F1 (treats a 1st vs 20th finish the same weight as 1st vs 2nd) — that's fine, it's a baseline, not the final model. Note this limitation explicitly in code comments so it's not mistaken for the real model later
- Unit tests: verify against known Elo reference sequences for football; verify N-way decomposition produces sane relative orderings for a synthetic F1-shaped race
- **Exit criterion for this sub-phase**: all three sports have ratings updating correctly end-to-end through the full gRPC → Java → Postgres → frontend pipeline, on real ingested data from 0.3. This is your first genuine full-stack milestone — do not proceed to 1.2 until this works.

### 1.1 Elo Rating Module (football-specific refinement, builds on 1.0)
- Implement base Elo update: `R'_A = R_A + K(S_A - E_A)`, with configurable K-factor
- Add K-factor decay/scaling (higher K for new entities with few matches, matching FIDE-style provisional ratings)
- Support draw handling (S_A = 0.5) for sports where draws exist
- Home-field advantage adjustment (constant offset added to home entity's effective rating pre-calculation)
- Unit tests: verify against known Elo reference sequences (e.g. reconstruct a small known chess/football rating history and check convergence)

### 1.2 Glicko-2 Rating Module
- Implement full Glicko-2 algorithm: rating (μ), rating deviation (φ), volatility (σ)
- Implement the iterative volatility-update sub-algorithm (this is the fiddly part — Illinois algorithm for root-finding)
- Rating period batching (Glicko-2 is designed for periodic batch updates, not per-match — decide period length, e.g. weekly)
- Unit tests against Glickman's published worked example (his paper includes exact expected outputs — use these as ground truth)

### 1.3 Poisson / Bivariate Poisson Scoreline Model
- Implement attack/defense strength parameters per entity (Dixon-Coles style): `λ_A = attack_A * defense_B * home_advantage`
- MLE fitting via Eigen — need a numerical optimizer (implement simple gradient descent or L-BFGS via a small header-only lib) to fit attack/defense parameters from historical scores
- Add time-decay weighting on historical matches (recent matches weighted higher — exponential decay factor)
- Bivariate Poisson extension for correlated scores (captures low-scoring-game correlation that independent Poisson misses) — implement the Karlis & Ntzoufras bivariate Poisson formulation
- Convert fitted λ_A, λ_B into full scoreline probability matrix (P(scoreA=i, scoreB=j) for i,j in 0..10)
- Derive win/draw/loss probabilities and most-likely scoreline from the matrix
- Unit tests: fit against a small synthetic dataset with known generating parameters, verify recovered parameters are close

### 1.3b Plackett-Luce N-Way Model (F1-specific — build only after 1.0 is validated)
- Implement the **Plackett-Luce model**: given a set of entities with strength parameters, it directly gives the probability of an entire finishing order, not just a single pairwise outcome — this is the correct replacement for the 1.0 baseline once you trust the pipeline
- Fit strength parameters via MLE (Eigen + your L-BFGS/gradient descent utility from 1.3, reused here) against historical race results
- Separate strength parameters for **driver** and **constructor/car** — F1 outcomes are driven heavily by car performance, and conflating the two will make the model look like it's tracking driver skill when it's actually just tracking whose car is fastest this season
- Model DNFs as a distinct outcome (not simply "finished last") — a mechanical failure carries different information than being outraced
- Incorporate grid position as a prior/covariate, since starting position has a large, well-documented effect on finishing position in F1
- Unit tests: verify against a small synthetic race with known generating strengths, confirm recovered parameters and predicted finishing-order probabilities are sane
- Consider this module a stretch goal relative to football/cricket — it's the newest math to you and the one most likely to need iteration; budget accordingly rather than treating it as equivalent effort to 1.1

### 1.4 Bayesian Updating Layer
- Wrap ratings in a Bayesian framing: treat Elo/Glicko rating as prior mean, use match outcome likelihood to compute posterior
- Implement credible interval computation (not just point estimate) — output prediction as a distribution, not a single number
- This layer sits on top of 1.1–1.3, doesn't replace them — it's what turns a point-rating into "probability team A wins is 62% ± 8%"

### 1.5 Monte Carlo Season Simulator
- Given current ratings for all entities in a league + a remaining fixture list, simulate the rest of the season N times (start with N=10,000)
- Each simulation: sample match outcomes from the fitted probability distributions (not just take the argmax), propagate rating changes forward through the simulated season, tally final standings
- Parallelize across simulations (std::thread pool or OpenMP — this is the part that actually needs C++ speed)
- Output: for each entity, distribution over final rank (e.g. "68% chance of finishing top 4", "12% relegation risk")
- Unit tests: sanity-check with a tiny 3-team round-robin where outcomes can be reasoned about by hand

### 1.6 Model Evaluation & Backtesting Utilities
- Implement Brier score calculator (mean squared error between predicted probability and actual binary outcome)
- Implement log loss calculator
- Implement calibration bucket generator (group predictions into probability bins, compare predicted vs actual frequency — feeds the frontend calibration curve)
- Rolling backtest harness: replay historical matches chronologically, generate predictions "as of" each match date using only prior data, score against actual outcomes (critical: must not leak future data into past predictions)

### 1.7 gRPC Service Layer
- Implement the three services defined in 0.2, wrapping modules 1.1–1.6
- Add structured logging (spdlog) for prediction requests/latency
- Dockerize the engine service

---

## PHASE 2 — Java Backend Platform

### 2.1 Data Model & Persistence
- Postgres schema mirroring the domain model from 0.1 (entities, matches, rating_snapshots, prediction_records)
- Use Flyway for migrations
- JPA/Hibernate entities + repositories (Spring Data JPA)
- Index strategy: matches by (entity_id, timestamp) for fast historical lookups; prediction_records by match_id

### 2.2 Data Ingestion Pipeline
- Adapter interface: `SportDataAdapter { List<Match> fetchRecentMatches(sportId, since) }`
- Implement one concrete adapter first (pick based on data source availability — free football APIs like football-data.org are a good starting point, or CSV import for offline historical datasets)
- Ingestion job: dedupe incoming matches against existing records, insert new completed matches, trigger downstream rating update
- Validation layer: reject malformed matches (missing scores, invalid entity IDs) with clear error logging

### 2.3 gRPC Client Integration
- Java gRPC client stubs (generated from shared .proto files) to call the C++ engine
- Connection pooling/retry logic (engine calls should be resilient — engine may be mid-restart)
- Circuit breaker pattern (Resilience4j) around engine calls so platform degrades gracefully if engine is down

### 2.4 Rating Update Orchestration
- Scheduled job (Spring `@Scheduled` or Quartz) triggered after new match ingestion: calls `RatingService.UpdateRating` for affected entities, persists new `RatingSnapshot`
- Handle out-of-order match ingestion (e.g. a delayed match result arriving after later matches already processed) — decide on a recompute-from-checkpoint strategy vs. strict chronological enforcement

### 2.5 Prediction API
- `POST /api/predictions/match/{matchId}` — triggers `PredictionService.PredictMatch`, persists result
- `GET /api/predictions/upcoming` — list predictions for scheduled matches
- `GET /api/entities/{id}/rating-history` — time series of rating snapshots for charting
- `GET /api/models/accuracy` — aggregated Brier score / log loss / calibration data over a date range

### 2.6 Simulation API
- `POST /api/simulations/season/{leagueId}` — async job (simulations can take time), returns job ID
- `GET /api/simulations/{jobId}` — poll for status/result
- Consider WebSocket push for simulation completion instead of polling, if frontend needs real-time feel

### 2.7 Auth & Access (if multi-user)
- Spring Security + JWT for basic auth, if the platform will have user accounts (e.g. saved leagues, personal accuracy dashboards)
- Skip this phase entirely if it's a single-operator tool — decide based on intended use

---

## PHASE 3 — Frontend Dashboard (Next.js)

### 3.1 Core Layout
- Entity leaderboard view: current ratings, sortable, with rating trend sparkline per entity
- Upcoming matches view: predicted probabilities as horizontal bar (win/draw/loss split), predicted scoreline

### 3.2 Rating History Visualization
- Time-series chart per entity (rating over time) — Recharts or D3
- Overlay major events (e.g. mark points where a big upset occurred)

### 3.3 Calibration & Accuracy Dashboard
- Calibration curve chart: predicted probability bucket vs actual frequency (the classic reliability diagram)
- Brier score / log loss trend over time (is the model getting better as more data comes in?)
- Confusion-matrix-style breakdown for discrete outcome accuracy

### 3.4 Season Simulation Viewer
- Distribution chart for final standings (e.g. stacked probability bars per entity showing P(rank=1), P(rank=2), etc.)
- "What-if" trigger: let user manually force a hypothetical result for an upcoming match and re-run simulation to see standings impact

### 3.5 Match Detail Page
- Head-to-head history between two entities
- Full scoreline probability matrix visualization (heatmap)
- Model explanation panel (show the attack/defense strength inputs that drove the prediction — important for credibility, not a black box)

---

## PHASE 4 — Evaluation & Hardening

### 4.1 Backtesting Report
- Run the Phase 1.6 backtest harness over the full historical dataset
- Compare Elo-only vs. Poisson vs. Bayesian-blended predictions on Brier score / log loss
- Document findings — this is your strongest artifact for a report or IEEE-style writeup, since it's a genuine empirical comparison of statistical models

### 4.2 Load & Correctness Testing
- gRPC service load test (concurrent prediction requests)
- Verify rating update idempotency (replaying the same match update shouldn't double-count)
- Edge cases: entity with zero match history, postponed/cancelled matches, mid-season new entrants

### 4.3 Documentation
- Architecture diagram (C++ engine ↔ Java platform ↔ frontend, with gRPC/REST boundaries marked)
- Model methodology writeup (the math — Elo formula, Glicko-2 update equations, Poisson MLE derivation) suitable for an appendix or paper

---

### 1.3c Cricket Format-Aware Model (builds on 1.0, not on the football Poisson module)
- Cricket does **not** reuse the football Poisson/scoreline model — runs, wickets, and overs form a different kind of outcome, and a raw scoreline Poisson fit will produce nonsense for a format with two innings and a wicket constraint
- Maintain **separate rating tracks per format** (Test / ODI / T20) per entity — a team's T20 rating and Test rating are close to unrelated in reality, and merging them will actively hurt accuracy
- Model win probability directly (rather than trying to predict an exact scoreline first) using the pre-match ratings plus in-progress state (current run rate, wickets in hand, overs remaining) once you get to live/in-play prediction — this is closer to a logistic/Duckworth-Lewis-aware approach than a Poisson one
- Toss result and home advantage are stronger predictors here than in football — include as explicit covariates
- Treat this as its own model family, not a variant of 1.2/1.3 — budget it as comparable effort to the football module, not a quick adaptation

---

## Model risk tiering (addresses "what if the specialized model is wrong")
Given the recommendation to ship baselines first, track each sport's model at an explicit confidence tier so the rest of the system (frontend, backtesting) can display it honestly rather than presenting an unvalidated model with false confidence:
- **Tier 0 (baseline)**: Generalized Elo-for-N (1.0) — known-simple, low accuracy ceiling, but trustworthy and fast to ship
- **Tier 1 (specialized, validated)**: Sport-specific model (1.1–1.3c) that has passed the Phase 4.1 backtest and beaten Tier 0 on Brier score / log loss on held-out historical data
- **Never promote a sport to Tier 1 in the frontend/API until it has beaten its own Tier 0 baseline on backtested data.** This gives you an objective, unambiguous gate instead of a subjective "does this feel more rigorous" judgment — and it directly protects your stated priority of statistical rigor, since a specialized model that can't beat a naive Elo baseline has no business replacing it.

---

## Open decisions to lock down before starting
1. **Order of specialized-model work after 1.0 ships** — recommendation: football → cricket → F1, following the data-reliability ordering surfaced in the Phase 0.3 feasibility spike
2. **Single global engine instance vs. per-sport engine instances** — affects how rating parameters (K-factor, home advantage, format-specific tracks) are configured
3. **Multi-user or single-operator** — determines whether Phase 2.7 is needed at all
4. **Scraper legality/stability** — confirm Jolpica-F1 and Cricsheet.org coverage meets your needs during 0.3, before building ingestion adapters (2.2) against a specific source's payload shape
