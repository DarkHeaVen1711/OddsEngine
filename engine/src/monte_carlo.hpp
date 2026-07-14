#ifndef ODDSENGINE_MONTE_CARLO_HPP
#define ODDSENGINE_MONTE_CARLO_HPP

#include <string>
#include <vector>
#include <map>

namespace oddsengine {

// A single remaining fixture with pre-computed outcome probabilities.
// Probabilities come from the Poisson/Bayesian layer upstream.
struct SimFixture {
    std::string home_id;
    std::string away_id;
    double win_prob;   // P(home wins)
    double draw_prob;
    double loss_prob;  // P(away wins)
};

// Current standing for one entity entering the simulation.
struct StandingRow {
    std::string entity_id;
    int points      = 0;
    int goal_diff   = 0;
};

// Per-entity rank distribution output: rank_probs[i] = P(finishing rank i+1)
struct EntityRankDist {
    std::string entity_id;
    std::vector<double> rank_probs; // index 0 = rank 1, index 1 = rank 2, ...
};

struct MonteCarloResult {
    std::vector<EntityRankDist> distributions;
};

// Runs N parallel simulations and aggregates rank distributions.
// ponytail: uses std::thread + per-thread xorshift64 RNG (no external deps).
MonteCarloResult run_monte_carlo(
    const std::vector<StandingRow>& current_standings,
    const std::vector<SimFixture>& fixtures,
    int n_simulations = 10000,
    uint64_t seed = 42
);

} // namespace oddsengine

#endif // ODDSENGINE_MONTE_CARLO_HPP
