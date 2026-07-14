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

} // namespace oddsengine
