#include "plackett_luce.hpp"
#include <cmath>
#include <algorithm>
#include <set>

namespace oddsengine {

// Computes the Negative Log-Likelihood of the race history dataset.
// ponytail: splits driver and constructor components.
double compute_pl_nll(
    const std::vector<RaceRecord>& history,
    const std::vector<std::string>& drivers,
    const std::vector<std::string>& constructors,
    const std::vector<double>& p
) {
    int num_d = drivers.size();
    int num_c = constructors.size();
    double alpha = p[num_d + num_c];

    double nll = 0.0;
    for (const auto& race : history) {
        std::vector<RaceParticipant> finishers;
        for (const auto& part : race.participants) {
            if (!part.is_dnf) {
                finishers.push_back(part);
            }
        }
        std::sort(finishers.begin(), finishers.end(), [](const RaceParticipant& a, const RaceParticipant& b) {
            return a.finish_rank < b.finish_rank;
        });

        int num_finishers = finishers.size();
        if (num_finishers <= 1) continue;

        std::vector<double> thetas(num_finishers);
        for (int i = 0; i < num_finishers; ++i) {
            int d_idx = -1, c_idx = -1;
            for (int k = 0; k < num_d; ++k) if (drivers[k] == finishers[i].driver_id) d_idx = k;
            for (int k = 0; k < num_c; ++k) if (constructors[k] == finishers[i].constructor_id) c_idx = k;

            double theta_d = (d_idx != -1) ? p[d_idx] : 1.0;
            double theta_c = (c_idx != -1) ? p[num_d + c_idx] : 1.0;

            thetas[i] = theta_d * theta_c * std::exp(-alpha * finishers[i].grid_position);
        }

        for (int i = 0; i < num_finishers; ++i) {
            double denom = 0.0;
            for (int j = i; j < num_finishers; ++j) {
                denom += thetas[j];
            }
            if (denom < 1e-12) denom = 1e-12;
            nll -= std::log(thetas[i] / denom);
        }
    }

    for (int i = 0; i < num_d + num_c; ++i) {
        nll += 0.005 * (p[i] - 1.0) * (p[i] - 1.0);
    }
    nll += 0.005 * (alpha - 0.05) * (alpha - 0.05);

    return nll;
}

} // namespace oddsengine
