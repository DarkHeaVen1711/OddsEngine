#ifndef ODDSENGINE_CRICKET_HPP
#define ODDSENGINE_CRICKET_HPP

#include <string>
#include <map>
#include <vector>

namespace oddsengine {

/**
 * Cricket Format-Aware Win Probability Model (§1.3c)
 *
 * Key design decisions — see methodology.md §4:
 *
 * 1. SEPARATE RATING TRACKS PER FORMAT
 *    T20, ODI, and Test ratings are stored under distinct model names
 *    ("cricket_t20", "cricket_odi", "cricket_test"). The Elo engine handles
 *    storage — this module only computes match-level win probability, it does
 *    not duplicate the rating update logic.
 *
 * 2. LOGISTIC WIN PROBABILITY (NOT SCORELINE POISSON)
 *    Cricket does not reuse the football Poisson model. Runs, wickets, and overs
 *    form a different kind of outcome, and scoreline Poisson produces nonsense for
 *    two-innings matches with a wicket constraint. Instead we use a logistic function:
 *
 *      P(A wins) = σ( β₀·(rating_A - rating_B) + β₁·toss_advantage + β₂·home_advantage )
 *
 *    where σ is the sigmoid and β-weights are format-specific.
 *    This is analogous to a Duckworth-Lewis-aware approach without needing full DL tables.
 *
 * 3. TOSS AND HOME ADVANTAGE AS EXPLICIT COVARIATES
 *    Both are stronger predictors in cricket than in football, especially for
 *    day-night matches and home pitches suited to particular bowling attacks.
 *
 * 4. NOT A BLACK BOX
 *    The three input components (rating diff, toss, home) are all returned in the
 *    prediction output so the frontend's Model Inputs panel can display them.
 */

// Format constants — used as model_name suffixes
constexpr const char* FORMAT_T20  = "cricket_t20";
constexpr const char* FORMAT_ODI  = "cricket_odi";
constexpr const char* FORMAT_TEST = "cricket_test";

// Per-format tuning parameters (fit from historical data — these are reasonable priors)
struct CricketFormatParams {
    double rating_scale;     // divisor for rating difference (analogous to 400 in Elo)
    double toss_advantage;   // logit contribution of winning the toss
    double home_advantage;   // logit contribution of playing at home
};

// Format-specific parameter table
const std::map<std::string, CricketFormatParams> FORMAT_PARAMS = {
    { FORMAT_T20,  { 350.0, 0.12, 0.18 } },  // T20: toss matters most, short format reduces home edge
    { FORMAT_ODI,  { 380.0, 0.10, 0.22 } },  // ODI: moderate toss effect, stronger home advantage
    { FORMAT_TEST, { 420.0, 0.06, 0.35 } },  // Test: toss less decisive, strong home/pitch advantage
};

struct CricketPrediction {
    double p_home_win;   // P(home team wins)
    double p_away_win;   // P(away team wins)
    // Note: cricket can draw/tie but probability is format-dependent and small —
    // for T20/ODI we treat ties as ~0 and allocate to each team proportionally.
    // For Test, draws are meaningful; estimated as (1 - p_home_win - p_away_win).
    double p_draw;

    // Model input components (for frontend transparency panel)
    double rating_diff;
    double toss_logit;
    double home_logit;
    std::string format;
};

/**
 * Compute pre-match win probability for a cricket match.
 *
 * @param format           "cricket_t20" | "cricket_odi" | "cricket_test"
 * @param home_entity_id   entity ID of the home team
 * @param away_entity_id   entity ID of the away team
 * @param ratings          map of entity_id → current rating (format-specific)
 * @param toss_winner_id   entity ID of the toss winner ("" if unknown)
 * @return CricketPrediction with win probabilities and model inputs
 */
CricketPrediction predict_cricket_match(
    const std::string& format,
    const std::string& home_entity_id,
    const std::string& away_entity_id,
    const std::map<std::string, double>& ratings,
    const std::string& toss_winner_id = ""
);

/**
 * Update Elo ratings for a cricket match (wrapper around the generalised Elo module).
 * Returns updated ratings for home and away entities.
 *
 * @param format           format model name (e.g. "cricket_t20")
 * @param home_entity_id   entity ID of the home team
 * @param away_entity_id   entity ID of the away team
 * @param home_finish_rank 1 if home team won, 2 if lost, 1 for draw/tie (both)
 * @param away_finish_rank 1 if away team won, 2 if lost, 1 for draw/tie (both)
 * @param ratings          current ratings for both entities
 * @param home_matches_played  matches played by home team (for K-factor selection)
 * @param away_matches_played  matches played by away team (for K-factor selection)
 */
std::map<std::string, double> update_cricket_ratings(
    const std::string& home_entity_id,
    const std::string& away_entity_id,
    int home_finish_rank,
    int away_finish_rank,
    const std::map<std::string, double>& ratings,
    int home_matches_played = 30,
    int away_matches_played = 30
);

} // namespace oddsengine

#endif // ODDSENGINE_CRICKET_HPP
