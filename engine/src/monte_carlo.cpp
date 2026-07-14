#include "monte_carlo.hpp"
#include <algorithm>
#include <numeric>
#include <thread>
#include <mutex>
#include <vector>

namespace oddsengine {

// ---------------------------------------------------------------------------
// Fast per-thread xorshift64 RNG — no mutex, no shared state.
// ---------------------------------------------------------------------------
struct XorShift64 {
    uint64_t state;
    explicit XorShift64(uint64_t seed) : state(seed ? seed : 1) {}

    uint64_t next() {
        state ^= state << 13;
        state ^= state >> 7;
        state ^= state << 17;
        return state;
    }

    // Returns uniform double in [0, 1)
    double uniform() {
        return static_cast<double>(next()) / static_cast<double>(UINT64_MAX);
    }
};

// ---------------------------------------------------------------------------
// Simulate one season given current standings + remaining fixtures.
// Returns final rank order (index = entity position in standings vector, value = rank 1..N).
// ---------------------------------------------------------------------------
static std::vector<int> simulate_season(
    const std::vector<StandingRow>& start,
    const std::vector<SimFixture>& fixtures,
    XorShift64& rng
) {
    // Copy standings so we can mutate them
    std::vector<StandingRow> table = start;

    // Build entity → index map for O(1) lookup
    std::map<std::string, size_t> idx;
    for (size_t i = 0; i < table.size(); ++i) idx[table[i].entity_id] = i;

    // Simulate each fixture
    for (const auto& fix : fixtures) {
        double r = rng.uniform();
        int home_pts = 0, away_pts = 0, home_gd = 0, away_gd = 0;

        if (r < fix.win_prob) {
            // Home win: score like 2-0 for GD tracking
            home_pts = 3; home_gd = 1; away_gd = -1;
        } else if (r < fix.win_prob + fix.draw_prob) {
            // Draw: 1 point each
            home_pts = 1; away_pts = 1;
        } else {
            // Away win
            away_pts = 3; away_gd = 1; home_gd = -1;
        }

        auto it_h = idx.find(fix.home_id);
        auto it_a = idx.find(fix.away_id);
        if (it_h != idx.end()) {
            table[it_h->second].points   += home_pts;
            table[it_h->second].goal_diff += home_gd;
        }
        if (it_a != idx.end()) {
            table[it_a->second].points   += away_pts;
            table[it_a->second].goal_diff += away_gd;
        }
    }

    // Sort by points desc, then GD desc → assign ranks
    std::vector<size_t> order(table.size());
    std::iota(order.begin(), order.end(), 0);
    std::stable_sort(order.begin(), order.end(), [&](size_t a, size_t b) {
        if (table[a].points != table[b].points) return table[a].points > table[b].points;
        return table[a].goal_diff > table[b].goal_diff;
    });

    std::vector<int> ranks(table.size());
    for (size_t r2 = 0; r2 < order.size(); ++r2) {
        ranks[order[r2]] = static_cast<int>(r2) + 1;
    }
    return ranks;
}

// ---------------------------------------------------------------------------
// Parallel Monte Carlo runner — splits simulations across hardware threads.
// Each thread gets its own RNG seed = (base_seed + thread_id) to avoid
// contention and guarantee reproducibility for a given seed.
// ---------------------------------------------------------------------------
MonteCarloResult run_monte_carlo(
    const std::vector<StandingRow>& current_standings,
    const std::vector<SimFixture>& fixtures,
    int n_simulations,
    uint64_t seed
) {
    int n_entities = static_cast<int>(current_standings.size());
    if (n_entities == 0 || n_simulations <= 0) return {};

    unsigned int n_threads = std::max(1u, std::thread::hardware_concurrency());
    // rank_counts[entity_idx][rank-1] = count of simulations where entity finished that rank
    std::vector<std::vector<int>> rank_counts(n_entities, std::vector<int>(n_entities, 0));
    std::mutex mtx;

    auto worker = [&](int thread_id, int sim_start, int sim_end) {
        XorShift64 rng(seed + static_cast<uint64_t>(thread_id) * 6364136223846793005ULL);
        // Local accumulator — no mutex inside hot loop
        std::vector<std::vector<int>> local_counts(n_entities, std::vector<int>(n_entities, 0));

        for (int s = sim_start; s < sim_end; ++s) {
            auto ranks = simulate_season(current_standings, fixtures, rng);
            for (int i = 0; i < n_entities; ++i) {
                local_counts[i][ranks[i] - 1]++;
            }
        }

        // Merge into global counts under lock (done once per thread, not per sim)
        std::lock_guard<std::mutex> lock(mtx);
        for (int i = 0; i < n_entities; ++i) {
            for (int r = 0; r < n_entities; ++r) {
                rank_counts[i][r] += local_counts[i][r];
            }
        }
    };

    // Distribute simulations across threads
    std::vector<std::thread> threads;
    int batch = n_simulations / static_cast<int>(n_threads);
    for (unsigned int t = 0; t < n_threads; ++t) {
        int s_start = static_cast<int>(t) * batch;
        int s_end   = (t + 1 == n_threads) ? n_simulations : s_start + batch;
        threads.emplace_back(worker, static_cast<int>(t), s_start, s_end);
    }
    for (auto& th : threads) th.join();

    // Build result — convert counts to probabilities
    MonteCarloResult result;
    for (int i = 0; i < n_entities; ++i) {
        EntityRankDist erd;
        erd.entity_id = current_standings[i].entity_id;
        erd.rank_probs.resize(n_entities);
        for (int r = 0; r < n_entities; ++r) {
            erd.rank_probs[r] = static_cast<double>(rank_counts[i][r]) / n_simulations;
        }
        result.distributions.push_back(erd);
    }
    return result;
}

} // namespace oddsengine
