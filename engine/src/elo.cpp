#include "elo.hpp"
#include <cmath>

namespace oddsengine {

std::map<std::string, double> calculate_elo_updates(
    const Event& event,
    const std::map<std::string, double>& current_ratings,
    double k_factor
) {
    std::map<std::string, double> updates;
    size_t n = event.participants.size();
    if (n <= 1) {
        return updates;
    }

    // Retrieve/default ratings for all participants
    std::vector<double> ratings(n);
    for (size_t i = 0; i < n; ++i) {
        const auto& p = event.participants[i];
        auto it = current_ratings.find(p.entity_id);
        ratings[i] = (it != current_ratings.end()) ? it->second : 1500.0;
    }

    // Calculate pairwise contributions
    for (size_t i = 0; i < n; ++i) {
        double sum_diff = 0.0;
        const auto& p_a = event.participants[i];
        double r_a = ratings[i];

        for (size_t j = 0; j < n; ++j) {
            if (i == j) continue;

            const auto& p_b = event.participants[j];
            double r_b = ratings[j];

            double actual = 0.5;
            if (p_a.finish_rank < p_b.finish_rank) {
                actual = 1.0;
            } else if (p_a.finish_rank > p_b.finish_rank) {
                actual = 0.0;
            }

            double expected = 1.0 / (1.0 + std::pow(10.0, (r_b - r_a) / 400.0));
            sum_diff += (actual - expected);
        }

        // Apply scaled rating change
        double new_rating = r_a + (k_factor * sum_diff) / static_cast<double>(n - 1);
        updates[p_a.entity_id] = new_rating;
    }

    return updates;
}

} // namespace oddsengine
