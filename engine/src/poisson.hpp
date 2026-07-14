#ifndef ODDSENGINE_POISSON_HPP
#define ODDSENGINE_POISSON_HPP

#include <string>
#include <vector>
#include <map>

namespace oddsengine {

struct MatchRecord {
    std::string home_id;
    std::string away_id;
    int home_goals;
    int away_goals;
    double weight; // time-decay weight
};

class PoissonModel {
public:
    std::map<std::string, double> attack;
    std::map<std::string, double> defense;
    double home_advantage = 1.2;
    double covariance = 0.05;

    // Fits the attack/defense, home advantage, and covariance parameters using MLE gradient descent.
    // ponytail: simple numerical gradient descent avoids dependency bloat.
    void fit(
        const std::vector<MatchRecord>& history,
        int max_iterations = 100,
        double learning_rate = 0.005
    );

    // Computes a 10x10 probability matrix and outputs aggregate win/draw/loss probabilities.
    std::vector<std::vector<double>> get_score_matrix(
        const std::string& home_id,
        const std::string& away_id,
        double& out_win,
        double& out_draw,
        double& out_loss
    ) const;
};

} // namespace oddsengine

#endif // ODDSENGINE_POISSON_HPP
