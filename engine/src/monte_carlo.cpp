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

} // namespace oddsengine
