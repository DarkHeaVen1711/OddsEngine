#ifndef ODDSENGINE_PLACKETT_LUCE_HPP
#define ODDSENGINE_PLACKETT_LUCE_HPP

#include <string>
#include <vector>
#include <map>

namespace oddsengine {

struct RaceParticipant {
    std::string entity_id;
    std::string driver_id;
    std::string constructor_id;
    int grid_position;
    int finish_rank;
    bool is_dnf;
};

struct RaceRecord {
    std::string race_id;
    std::vector<RaceParticipant> participants;
};

class PlackettLuceModel {
public:
    std::map<std::string, double> driver_skills;
    std::map<std::string, double> constructor_skills;
    std::map<std::string, double> constructor_dnf_rates;
    double grid_penalty = 0.05;

    // Fits driver/constructor strengths, grid penalty, and reliability rates using MLE gradient descent.
    // ponytail: simple numerical gradient descent avoids dependency bloat.
    void fit(
        const std::vector<RaceRecord>& history,
        int max_iterations = 100,
        double learning_rate = 0.005
    );

    // Predicts the probability of each non-DNF entrant winning the race (Plackett-Luce probability).
    std::map<std::string, double> predict_win_probabilities(
        const std::vector<RaceParticipant>& entrants
    ) const;
};

} // namespace oddsengine

#endif // ODDSENGINE_PLACKETT_LUCE_HPP
