#include "poisson.hpp"
#include <cmath>
#include <algorithm>
#include <set>

namespace oddsengine {

double fact(int n) {
    double f = 1.0;
    for (int i = 2; i <= n; ++i) f *= i;
    return f;
}

double biv_poisson_prob(double x, double y, double lam1, double lam2, double lam3) {
    double exp_term = std::exp(-(lam1 + lam2 + lam3));
    double sum = 0.0;
    int limit = std::min(static_cast<int>(x), static_cast<int>(y));
    for (int k = 0; k <= limit; ++k) {
        double term = (std::pow(lam1, x - k) * std::pow(lam2, y - k) * std::pow(lam3, k)) /
                      (fact(x - k) * fact(y - k) * fact(k));
        sum += term;
    }
    return exp_term * sum;
}

// Computes the Negative Log-Likelihood of the history dataset given parameter vector.
double compute_nll(
    const std::vector<MatchRecord>& history,
    const std::vector<std::string>& teams,
    const std::vector<double>& p
) {
    int m = teams.size();
    double home_adv = p[2 * m];
    double cov = p[2 * m + 1];

    double nll = 0.0;
    for (const auto& match : history) {
        int home_idx = -1, away_idx = -1;
        for (int i = 0; i < m; ++i) {
            if (teams[i] == match.home_id) home_idx = i;
            if (teams[i] == match.away_id) away_idx = i;
        }
        if (home_idx == -1 || away_idx == -1) continue;

        double att_h = p[home_idx];
        double def_a = p[m + away_idx];
        double att_a = p[away_idx];
        double def_h = p[m + home_idx];

        double lam_h = std::max(0.01, att_h * def_a * home_adv);
        double lam_a = std::max(0.01, att_a * def_h);

        double lam1 = std::max(0.001, lam_h - cov);
        double lam2 = std::max(0.001, lam_a - cov);

        double prob = biv_poisson_prob(match.home_goals, match.away_goals, lam1, lam2, cov);
        if (prob < 1e-12) prob = 1e-12;
        nll -= match.weight * std::log(prob);
    }

    // L2 regularizer to keep parameters near baseline
    for (int i = 0; i < 2 * m; ++i) {
        nll += 0.005 * (p[i] - 1.0) * (p[i] - 1.0);
    }
    nll += 0.005 * (home_adv - 1.2) * (home_adv - 1.2);
    nll += 0.005 * (cov - 0.05) * (cov - 0.05);

    return nll;
}

void PoissonModel::fit(
    const std::vector<MatchRecord>& history,
    int max_iterations,
    double learning_rate
) {
    std::set<std::string> team_set;
    for (const auto& match : history) {
        team_set.insert(match.home_id);
        team_set.insert(match.away_id);
    }
    std::vector<std::string> teams(team_set.begin(), team_set.end());
    int m = teams.size();
    if (m == 0) return;

    // Vector mapping: [att_0..att_m-1, def_0..def_m-1, home_advantage, covariance]
    std::vector<double> p(2 * m + 2);
    for (int i = 0; i < m; ++i) {
        p[i] = 1.0;       // attack
        p[m + i] = 1.0;   // defense
    }
    p[2 * m] = 1.2;       // home advantage
    p[2 * m + 1] = 0.05;  // covariance

    double h = 1e-5;
    for (int iter = 0; iter < max_iterations; ++iter) {
        std::vector<double> grad(2 * m + 2, 0.0);
        for (size_t j = 0; j < p.size(); ++j) {
            double old_val = p[j];
            p[j] = old_val + h;
            double nll_plus = compute_nll(history, teams, p);
            p[j] = old_val - h;
            double nll_minus = compute_nll(history, teams, p);
            p[j] = old_val;

            grad[j] = (nll_plus - nll_minus) / (2.0 * h);
        }

        // Gradient update and boundary constraints clamping
        for (size_t j = 0; j < p.size(); ++j) {
            p[j] -= learning_rate * grad[j];
            if (j < 2 * m + 1) {
                p[j] = std::max(0.05, p[j]); // positive coefficients
            } else {
                p[j] = std::max(0.0, std::min(0.15, p[j])); // covariance bounds
            }
        }
    }

    // Extract optimized parameters back to class
    attack.clear();
    defense.clear();
    double avg_attack = 0.0;
    for (int i = 0; i < m; ++i) {
        attack[teams[i]] = p[i];
        defense[teams[i]] = p[m + i];
        avg_attack += p[i];
    }
    home_advantage = p[2 * m];
    covariance = p[2 * m + 1];

    // Identifiability constraint: average attack = 1.0
    if (m > 0) {
        avg_attack /= m;
        for (int i = 0; i < m; ++i) {
            attack[teams[i]] /= avg_attack;
            defense[teams[i]] *= avg_attack;
        }
    }
}

} // namespace oddsengine
