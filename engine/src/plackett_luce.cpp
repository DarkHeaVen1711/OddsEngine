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

void PlackettLuceModel::fit(
    const std::vector<RaceRecord>& history,
    int max_iterations,
    double learning_rate
) {
    std::set<std::string> d_set;
    std::set<std::string> c_set;
    std::map<std::string, int> constructor_entries;
    std::map<std::string, int> constructor_dnfs;

    for (const auto& race : history) {
        for (const auto& part : race.participants) {
            d_set.insert(part.driver_id);
            c_set.insert(part.constructor_id);

            constructor_entries[part.constructor_id]++;
            if (part.is_dnf) {
                constructor_dnfs[part.constructor_id]++;
            }
        }
    }

    std::vector<std::string> drivers(d_set.begin(), d_set.end());
    std::vector<std::string> constructors(c_set.begin(), c_set.end());

    int num_d = drivers.size();
    int num_c = constructors.size();
    if (num_d == 0 || num_c == 0) return;

    constructor_dnf_rates.clear();
    for (const auto& c : constructors) {
        double entries = constructor_entries[c];
        double dnfs = constructor_dnfs[c];
        constructor_dnf_rates[c] = (entries > 0) ? (dnfs / entries) : 0.05;
    }

    std::vector<double> p(num_d + num_c + 1);
    for (int i = 0; i < num_d; ++i) p[i] = 1.0;
    for (int i = 0; i < num_c; ++i) p[num_d + i] = 1.0;
    p[num_d + num_c] = 0.05; // grid penalty alpha

    double h = 1e-5;
    for (int iter = 0; iter < max_iterations; ++iter) {
        std::vector<double> grad(num_d + num_c + 1, 0.0);
        for (size_t j = 0; j < p.size(); ++j) {
            double old_val = p[j];
            p[j] = old_val + h;
            double nll_plus = compute_pl_nll(history, drivers, constructors, p);
            p[j] = old_val - h;
            double nll_minus = compute_pl_nll(history, drivers, constructors, p);
            p[j] = old_val;

            grad[j] = (nll_plus - nll_minus) / (2.0 * h);
        }

        for (size_t j = 0; j < p.size(); ++j) {
            p[j] -= learning_rate * grad[j];
            if (j < p.size() - 1) {
                p[j] = std::max(0.05, p[j]); // skills positive
            } else {
                p[j] = std::max(0.01, std::min(0.20, p[j])); // alpha penalty limits
            }
        }
    }

    driver_skills.clear();
    constructor_skills.clear();
    double avg_d = 0.0;
    for (int i = 0; i < num_d; ++i) {
        driver_skills[drivers[i]] = p[i];
        avg_d += p[i];
    }
    for (int i = 0; i < num_c; ++i) {
        constructor_skills[constructors[i]] = p[num_d + i];
    }
    grid_penalty = p[num_d + num_c];

    if (num_d > 0) {
        avg_d /= num_d;
        for (int i = 0; i < num_d; ++i) {
            driver_skills[drivers[i]] /= avg_d;
        }
        for (int i = 0; i < num_c; ++i) {
            constructor_skills[constructors[i]] *= avg_d;
        }
    }
}

std::map<std::string, double> PlackettLuceModel::predict_win_probabilities(
    const std::vector<RaceParticipant>& entrants
) const {
    std::map<std::string, double> probs;
    double sum = 0.0;

    for (const auto& part : entrants) {
        double dnf_rate = 0.05;
        auto it_dnf = constructor_dnf_rates.find(part.constructor_id);
        if (it_dnf != constructor_dnf_rates.end()) dnf_rate = it_dnf->second;

        double theta_d = 1.0;
        auto it_d = driver_skills.find(part.driver_id);
        if (it_d != driver_skills.end()) theta_d = it_d->second;

        double theta_c = 1.0;
        auto it_c = constructor_skills.find(part.constructor_id);
        if (it_c != constructor_skills.end()) theta_c = it_c->second;

        double strength = (1.0 - dnf_rate) * theta_d * theta_c * std::exp(-grid_penalty * part.grid_position);
        probs[part.entity_id] = strength;
        sum += strength;
    }

    if (sum > 0.0) {
        for (auto& pair : probs) {
            pair.second /= sum;
        }
    }

    return probs;
}

} // namespace oddsengine
