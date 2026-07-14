#ifndef ODDSENGINE_EVAL_HPP
#define ODDSENGINE_EVAL_HPP

#include "poisson.hpp"
#include <string>
#include <vector>

namespace oddsengine {

// A single prediction vs actual outcome record.
struct PredictionRecord {
    double predicted_win;
    double predicted_draw;
    double predicted_loss;
    int actual_outcome; // 1=home win, 0=draw, -1=away win
};

// One calibration bucket: predictions grouped by probability bin.
struct CalibrationBucket {
    double bin_lower;
    double bin_upper;
    double predicted_freq; // mean predicted prob in this bin
    double actual_freq;    // actual outcome rate in this bin
    int count;
};

// Aggregate evaluation result.
struct EvalResult {
    double brier_score;
    double log_loss;
    std::vector<CalibrationBucket> calibration_buckets;
};

// Rolling backtest result: scored on every match after min_training_matches.
struct BacktestResult {
    double brier_score;
    double log_loss;
    int n_predictions;
};

// Compute Brier score, log loss, and calibration curve over a set of predictions.
EvalResult evaluate(const std::vector<PredictionRecord>& predictions, int n_bins = 10);

// Rolling backtest harness using Poisson model.
// Replays ordered_history chronologically: trains on matches 0..i-1, predicts match i.
// Critical: no future data leaks into past predictions.
BacktestResult rolling_backtest(
    const std::vector<MatchRecord>& ordered_history,
    int min_training_matches = 5
);

} // namespace oddsengine

#endif // ODDSENGINE_EVAL_HPP
