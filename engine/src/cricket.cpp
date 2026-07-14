#include "cricket.hpp"
#include "elo.hpp"
#include <cmath>
#include <stdexcept>

namespace oddsengine {

// ── Helpers ───────────────────────────────────────────────────────────────────

static double sigmoid(double x) {
    return 1.0 / (1.0 + std::exp(-x));
}

static double get_rating(const std::map<std::string, double>& ratings,
                          const std::string& entity_id,
                          double default_rating = 1500.0) {
    auto it = ratings.find(entity_id);
    return (it != ratings.end()) ? it->second : default_rating;
}

// ── predict_cricket_match ─────────────────────────────────────────────────────

CricketPrediction predict_cricket_match(
    const std::string& format,
    const std::string& home_entity_id,
    const std::string& away_entity_id,
    const std::map<std::string, double>& ratings,
    const std::string& toss_winner_id
) {
    // Look up format parameters; fall back to ODI defaults for unknown formats
    CricketFormatParams params = { 380.0, 0.10, 0.22 };
    auto param_it = FORMAT_PARAMS.find(format);
    if (param_it != FORMAT_PARAMS.end()) {
        params = param_it->second;
    }

    double r_home = get_rating(ratings, home_entity_id);
    double r_away = get_rating(ratings, away_entity_id);

    // Rating difference component (positive = home team favoured)
    double rating_diff = (r_home - r_away) / params.rating_scale;

    // Toss advantage component
    // Toss winner tends to bat second in T20/ODI (chase), first in Tests
    // Sign convention: positive when home team wins toss
    double toss_logit = 0.0;
    if (!toss_winner_id.empty()) {
        if (toss_winner_id == home_entity_id) {
            toss_logit = params.toss_advantage;
        } else if (toss_winner_id == away_entity_id) {
            toss_logit = -params.toss_advantage;
        }
        // empty or unknown toss winner → no adjustment
    }

    // Home advantage component (always positive for home team)
    double home_logit = params.home_advantage;

    // Combined logit for home team win probability
    double logit_home = rating_diff + toss_logit + home_logit;
    double p_home_win = sigmoid(logit_home);

    // Draw probability — only meaningful for Test matches.
    // Using a conservative fixed prior: ~28% draw rate historically in Test cricket.
    // Scale it down so that p_home_win + p_draw + p_away_win ≈ 1.
    double p_draw = 0.0;
    if (format == FORMAT_TEST) {
        // Historical draw rate ≈ 0.27, but it varies with pitch/conditions.
        // We shrink it toward 0.25 and scale the remaining prob between win/loss.
        double base_draw = 0.25;
        double remaining = 1.0 - base_draw;
        p_home_win = p_home_win * remaining;
        p_draw = base_draw;
    }

    double p_away_win = 1.0 - p_home_win - p_draw;
    // Clamp to [0, 1] in case of floating-point drift
    if (p_away_win < 0.0) { p_away_win = 0.0; }

    return CricketPrediction{
        p_home_win,
        p_away_win,
        p_draw,
        rating_diff,
        toss_logit,
        home_logit,
        format
    };
}

// ── update_cricket_ratings ────────────────────────────────────────────────────

std::map<std::string, double> update_cricket_ratings(
    const std::string& home_entity_id,
    const std::string& away_entity_id,
    int home_finish_rank,
    int away_finish_rank,
    const std::map<std::string, double>& ratings,
    int home_matches_played,
    int away_matches_played
) {
    // Delegate to the generalised Elo-for-N module (§1.0/§1.1).
    // Cricket is a 2-participant event, so this collapses to plain Elo with home advantage.
    Event event;
    event.id = "cricket_match";
    event.sport_id = "cricket";
    event.participants = {
        { home_entity_id, home_finish_rank, /*is_home=*/true,  home_matches_played },
        { away_entity_id, away_finish_rank, /*is_home=*/false, away_matches_played },
    };

    // Use a slightly higher K for cricket due to higher variance per match (especially T20)
    return calculate_elo_updates(event, ratings, /*k_factor=*/20.0);
}

} // namespace oddsengine
