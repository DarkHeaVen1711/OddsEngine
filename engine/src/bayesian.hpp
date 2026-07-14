#ifndef ODDSENGINE_BAYESIAN_HPP
#define ODDSENGINE_BAYESIAN_HPP

#include <vector>

namespace oddsengine {

// Credible interval result from the Bayesian posterior.
struct BayesianResult {
    double mean;       // Posterior mean win probability
    double ci_lower;   // Lower bound of credible interval
    double ci_upper;   // Upper bound of credible interval
    double ci_level;   // e.g. 0.90 for 90% CI
};

// Bayesian model wrapping Elo/Poisson point estimates.
// Uses Beta-Bernoulli (binary) or Dirichlet-Multinomial (W/D/L) posteriors.
// Sits on top of Phase 1.1-1.3 models — does not replace them.
class BayesianModel {
public:
    // Dirichlet parameters for {win, draw, loss}
    double alpha_w = 1.0;
    double alpha_d = 1.0;
    double alpha_l = 1.0;

    // Seed prior from Poisson/Elo win-draw-loss point estimates.
    // kappa controls prior strength: higher = prior dominates longer.
    void init_from_poisson(double win, double draw, double loss, double kappa = 10.0);

    // Seed prior from a binary Elo win probability (no draw).
    void init_from_elo(double elo_win_prob, double kappa = 10.0);

    // Update posterior with a match outcome.
    // outcome: 1.0 = win, 0.5 = draw, 0.0 = loss
    void update(double outcome);

    // Compute posterior mean and credible interval.
    // ci_level: e.g. 0.90 for 90% CI (uses Beta marginal for win param)
    BayesianResult get_result(double ci_level = 0.90) const;
};

} // namespace oddsengine

#endif // ODDSENGINE_BAYESIAN_HPP
