#include "bayesian.hpp"
#include <cmath>
#include <stdexcept>
#include <algorithm>

namespace oddsengine {

// ---------------------------------------------------------------------------
// Regularised incomplete beta function via Lentz continued fraction.
// Reference: Numerical Recipes §6.4 (betacf + betai).
// Required for computing Beta CDF and credible interval bounds.
// ---------------------------------------------------------------------------

static double betacf(double a, double b, double x) {
    const int MAX_ITER = 200;
    const double EPS = 1e-10;
    const double FPMIN = 1e-300;

    double qab = a + b;
    double qap = a + 1.0;
    double qam = a - 1.0;
    double c = 1.0;
    double d = 1.0 - qab * x / qap;
    if (std::abs(d) < FPMIN) d = FPMIN;
    d = 1.0 / d;
    double h = d;

    for (int m = 1; m <= MAX_ITER; ++m) {
        double m2 = 2 * m;
        // Even step
        double aa = m * (b - m) * x / ((qam + m2) * (a + m2));
        d = 1.0 + aa * d;
        if (std::abs(d) < FPMIN) d = FPMIN;
        c = 1.0 + aa / c;
        if (std::abs(c) < FPMIN) c = FPMIN;
        d = 1.0 / d;
        h *= d * c;
        // Odd step
        aa = -(a + m) * (qab + m) * x / ((a + m2) * (qap + m2));
        d = 1.0 + aa * d;
        if (std::abs(d) < FPMIN) d = FPMIN;
        c = 1.0 + aa / c;
        if (std::abs(c) < FPMIN) c = FPMIN;
        d = 1.0 / d;
        double del = d * c;
        h *= del;
        if (std::abs(del - 1.0) < EPS) break;
    }
    return h;
}

// Regularised incomplete beta function I_x(a,b)
static double ibeta(double a, double b, double x) {
    if (x < 0.0 || x > 1.0) return 0.0;
    if (x == 0.0) return 0.0;
    if (x == 1.0) return 1.0;

    double lbeta = std::lgamma(a) + std::lgamma(b) - std::lgamma(a + b);
    double front = std::exp(std::log(x) * a + std::log(1.0 - x) * b - lbeta) / a;

    // Use the symmetry relation for better convergence
    if (x < (a + 1.0) / (a + b + 2.0)) {
        return front * betacf(a, b, x);
    } else {
        return 1.0 - (std::exp(std::log(1.0 - x) * b + std::log(x) * a - lbeta) / b) * betacf(b, a, 1.0 - x);
    }
}

// Beta quantile via Newton-Raphson inversion of ibeta.
static double ibeta_inv(double a, double b, double p) {
    if (p <= 0.0) return 0.0;
    if (p >= 1.0) return 1.0;

    // Initial guess via normal approximation
    double x = a / (a + b);
    for (int i = 0; i < 50; ++i) {
        double fx = ibeta(a, b, x) - p;
        // Beta PDF at x for derivative
        double lbeta_val = std::lgamma(a) + std::lgamma(b) - std::lgamma(a + b);
        double dfx = std::exp((a - 1.0) * std::log(x) + (b - 1.0) * std::log(1.0 - x) - lbeta_val);
        if (std::abs(dfx) < 1e-300) break;
        double dx = fx / dfx;
        x -= dx;
        x = std::max(1e-10, std::min(1.0 - 1e-10, x));
        if (std::abs(dx) < 1e-10) break;
    }
    return x;
}

// ---------------------------------------------------------------------------
// BayesianModel implementation
// ---------------------------------------------------------------------------

void BayesianModel::init_from_poisson(double win, double draw, double loss, double kappa) {
    // Normalize to guard against floating point drift
    double total = win + draw + loss;
    if (total < 1e-12) { win = draw = loss = 1.0 / 3.0; total = 1.0; }
    alpha_w = (win  / total) * kappa;
    alpha_d = (draw / total) * kappa;
    alpha_l = (loss / total) * kappa;
}

void BayesianModel::init_from_elo(double elo_win_prob, double kappa) {
    double p = std::max(0.01, std::min(0.99, elo_win_prob));
    alpha_w = p * kappa;
    alpha_d = 0.0;  // Elo has no draw concept — zero draw mass
    alpha_l = (1.0 - p) * kappa;
}

void BayesianModel::update(double outcome) {
    if (outcome >= 0.99) {
        alpha_w += 1.0;
    } else if (outcome <= 0.01) {
        alpha_l += 1.0;
    } else {
        alpha_w += outcome;
        alpha_l += (1.0 - outcome);
        alpha_d += std::min(outcome, 1.0 - outcome); // draw evidence
    }
}

BayesianResult BayesianModel::get_result(double ci_level) const {
    // Posterior mean from Dirichlet: alpha_w / (alpha_w + alpha_d + alpha_l)
    double total = alpha_w + alpha_d + alpha_l;
    if (total < 1e-12) {
        return {1.0 / 3.0, 0.0, 1.0, ci_level};
    }
    double mean = alpha_w / total;

    // Credible interval via the Beta marginal for win probability.
    // The marginal of a Dirichlet for one component is Beta(alpha_w, total - alpha_w).
    double a = alpha_w;
    double b = total - alpha_w;

    double tail = (1.0 - ci_level) / 2.0;
    double lower = ibeta_inv(a, b, tail);
    double upper = ibeta_inv(a, b, 1.0 - tail);

    return {mean, lower, upper, ci_level};
}

} // namespace oddsengine
