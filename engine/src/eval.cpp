#include "eval.hpp"
#include <cmath>
#include <algorithm>

namespace oddsengine {

EvalResult evaluate(const std::vector<PredictionRecord>& predictions, int n_bins) {
    EvalResult res;
    if (predictions.empty()) {
        res.brier_score = 0.0;
        res.log_loss = 0.0;
        return res;
    }

    double total_brier = 0.0;
    double total_log_loss = 0.0;

    for (const auto& pred : predictions) {
        double y_w = (pred.actual_outcome == 1) ? 1.0 : 0.0;
        double y_d = (pred.actual_outcome == 0) ? 1.0 : 0.0;
        double y_l = (pred.actual_outcome == -1) ? 1.0 : 0.0;

        // Brier score calculation (multiclass)
        double se = (pred.predicted_win - y_w) * (pred.predicted_win - y_w) +
                    (pred.predicted_draw - y_d) * (pred.predicted_draw - y_d) +
                    (pred.predicted_loss - y_l) * (pred.predicted_loss - y_l);
        total_brier += se;

        // Log loss calculation (clamped to prevent log(0))
        double p_w = std::max(1e-15, std::min(1.0 - 1e-15, pred.predicted_win));
        double p_d = std::max(1e-15, std::min(1.0 - 1e-15, pred.predicted_draw));
        double p_l = std::max(1e-15, std::min(1.0 - 1e-15, pred.predicted_loss));

        double ll = y_w * std::log(p_w) + y_d * std::log(p_d) + y_l * std::log(p_l);
        total_log_loss -= ll;
    }

    res.brier_score = total_brier / predictions.size();
    res.log_loss = total_log_loss / predictions.size();

    // Calibration buckets for win probability
    res.calibration_buckets.resize(n_bins);
    for (int b = 0; b < n_bins; ++b) {
        res.calibration_buckets[b].bin_lower = static_cast<double>(b) / n_bins;
        res.calibration_buckets[b].bin_upper = static_cast<double>(b + 1) / n_bins;
        res.calibration_buckets[b].predicted_freq = 0.0;
        res.calibration_buckets[b].actual_freq = 0.0;
        res.calibration_buckets[b].count = 0;
    }

    for (const auto& pred : predictions) {
        double p = pred.predicted_win;
        int bin_idx = static_cast<int>(p * n_bins);
        if (bin_idx >= n_bins) bin_idx = n_bins - 1;
        if (bin_idx < 0) bin_idx = 0;

        res.calibration_buckets[bin_idx].predicted_freq += p;
        res.calibration_buckets[bin_idx].actual_freq += (pred.actual_outcome == 1 ? 1.0 : 0.0);
        res.calibration_buckets[bin_idx].count++;
    }

    for (int b = 0; b < n_bins; ++b) {
        if (res.calibration_buckets[b].count > 0) {
            res.calibration_buckets[b].predicted_freq /= res.calibration_buckets[b].count;
            res.calibration_buckets[b].actual_freq /= res.calibration_buckets[b].count;
        }
    }

    return res;
}


BacktestResult rolling_backtest(
    const std::vector<MatchRecord>& ordered_history,
    int min_training_matches
) {
    BacktestResult res = {0.0, 0.0, 0};
    int n = ordered_history.size();
    if (n <= min_training_matches) return res;

    std::vector<PredictionRecord> preds;

    for (int i = min_training_matches; i < n; ++i) {
        std::vector<MatchRecord> training_history(ordered_history.begin(), ordered_history.begin() + i);

        PoissonModel model;
        model.fit(training_history, 100, 0.01);

        const auto& target = ordered_history[i];
        double win = 0.0, draw = 0.0, loss = 0.0;
        model.get_score_matrix(target.home_id, target.away_id, win, draw, loss);

        int actual = 0;
        if (target.home_goals > target.away_goals) actual = 1;
        else if (target.home_goals < target.away_goals) actual = -1;

        preds.push_back({win, draw, loss, actual});
    }

    auto eval = evaluate(preds);
    res.brier_score = eval.brier_score;
    res.log_loss = eval.log_loss;
    res.n_predictions = preds.size();

    return res;
}

} // namespace oddsengine
