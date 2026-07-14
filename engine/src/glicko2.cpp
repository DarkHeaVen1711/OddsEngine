#include "glicko2.hpp"
#include <cmath>

namespace oddsengine {

const double SCALE = 173.7178;
const double PI = 3.141592653589793;

double g_val(double phi) {
    return 1.0 / std::sqrt(1.0 + 3.0 * phi * phi / (PI * PI));
}

double E_val(double theta, double theta_j, double phi_j) {
    return 1.0 / (1.0 + std::exp(-g_val(phi_j) * (theta - theta_j)));
}

double f_val(double x, double delta, double phi, double v, double a, double tau) {
    double ex = std::exp(x);
    double numer = ex * (delta * delta - phi * phi - v - ex);
    double denom = 2.0 * (phi * phi + v + ex) * (phi * phi + v + ex);
    return (numer / denom) - (x - a) / (tau * tau);
}

GlickoParticipant calculate_glicko2_update(
    const GlickoParticipant& player,
    const std::vector<GlickoMatch>& matches,
    double tau
) {
    // 1. Convert to Glicko-2 scale
    double theta = (player.rating - 1500.0) / SCALE;
    double phi = player.rd / SCALE;
    double sigma = player.volatility;

    if (matches.empty()) {
        // If player does not play, only rating deviation increases due to uncertainty
        double new_phi = std::sqrt(phi * phi + sigma * sigma);
        return {player.entity_id, player.rating, new_phi * SCALE, sigma};
    }

    // 2. Compute v and delta
    double v_inv = 0.0;
    double sum_diff = 0.0;

    for (const auto& match : matches) {
        double theta_j = (match.opponent_rating - 1500.0) / SCALE;
        double phi_j = match.opponent_rd / SCALE;
        double g = g_val(phi_j);
        double E = E_val(theta, theta_j, phi_j);

        v_inv += g * g * E * (1.0 - E);
        sum_diff += g * (match.score - E);
    }

    double v = 1.0 / v_inv;
    double delta = v * sum_diff;

    // 3. Volatility root-finding (Illinois algorithm)
    double a = std::log(sigma * sigma);
    double A = a;
    double B;
    if (delta * delta > phi * phi + v) {
        B = std::log(delta * delta - phi * phi - v);
    } else {
        double k = 1.0;
        while (f_val(a - k * tau, delta, phi, v, a, tau) >= 0.0) {
            k += 1.0;
        }
        B = a - k * tau;
    }

    double f_A = f_val(A, delta, phi, v, a, tau);
    double f_B = f_val(B, delta, phi, v, a, tau);

    while (std::abs(B - A) > 1e-6) {
        double C = A + f_A * (A - B) / (f_B - f_A);
        double f_C = f_val(C, delta, phi, v, a, tau);
        if (f_C * f_B < 0.0) {
            A = B;
            f_A = f_B;
        } else {
            f_A /= 2.0;
        }
        B = C;
        f_B = f_C;
    }

    double new_sigma = std::exp(B / 2.0);

    // 4. Update rating and deviation
    double phi_star = std::sqrt(phi * phi + new_sigma * new_sigma);
    double new_phi = 1.0 / std::sqrt(1.0 / (phi_star * phi_star) + 1.0 / v);
    double new_theta = theta + new_phi * new_phi * sum_diff;

    // 5. Convert back to standard scale
    double new_rating = 1500.0 + SCALE * new_theta;
    double new_rd = SCALE * new_phi;

    return {player.entity_id, new_rating, new_rd, new_sigma};
}

} // namespace oddsengine
