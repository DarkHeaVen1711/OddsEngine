#ifndef ODDSENGINE_GLICKO2_HPP
#define ODDSENGINE_GLICKO2_HPP

#include <string>
#include <vector>

namespace oddsengine {

struct GlickoMatch {
    double opponent_rating;
    double opponent_rd;
    double score; // 1.0 = win, 0.5 = draw, 0.0 = loss
};

struct GlickoParticipant {
    std::string entity_id;
    double rating;
    double rd;
    double volatility;
};

// Calculates Glicko-2 rating, deviation, and volatility.
// ponytail: follows Glickman's standard paper formulas exactly.
GlickoParticipant calculate_glicko2_update(
    const GlickoParticipant& player,
    const std::vector<GlickoMatch>& matches,
    double tau = 0.5
);

} // namespace oddsengine

#endif // ODDSENGINE_GLICKO2_HPP
