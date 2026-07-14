#ifndef ODDSENGINE_ELO_HPP
#define ODDSENGINE_ELO_HPP

#include <string>
#include <vector>
#include <map>

namespace oddsengine {

struct Participant {
    std::string entity_id;
    int finish_rank;
};

struct Event {
    std::string id;
    std::string sport_id;
    std::vector<Participant> participants;
};

struct Rating {
    double value;
};

// Generalized Elo-for-N update function.
// Decomposes N-way events into pairwise outcomes and updates ratings.
// ponytail: scaling factor of (N - 1) prevents rating speed scaling with participant count.
std::map<std::string, double> calculate_elo_updates(
    const Event& event,
    const std::map<std::string, double>& current_ratings,
    double k_factor = 32.0
);

} // namespace oddsengine

#endif // ODDSENGINE_ELO_HPP
