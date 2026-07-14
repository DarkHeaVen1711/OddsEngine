#include "elo.hpp"
#include <iostream>
#include <string>
#include <vector>
#include <map>
#include <cassert>
#include <cmath>

void run_tests() {
    using namespace oddsengine;

    // Test 1: Pairwise (N=2) traditional Elo update
    // Team A (1600) vs Team B (1400), Team A wins
    Event match1;
    match1.id = "evt1";
    match1.sport_id = "football";
    match1.participants = {
        {"A", 1},
        {"B", 2}
    };

    std::map<std::string, double> ratings = {
        {"A", 1600.0},
        {"B", 1400.0}
    };

    auto updates = calculate_elo_updates(match1, ratings, 32.0);

    // Expected outcome:
    // E_AB = 1 / (1 + 10^((1400-1600)/400)) = 0.7597469
    // R'_A = 1600 + 32 * (1.0 - 0.7597469) = 1607.688
    // R'_B = 1400 + 32 * (0.0 - 0.240253) = 1392.312
    assert(std::abs(updates["A"] - 1607.688) < 0.001);
    assert(std::abs(updates["B"] - 1392.312) < 0.001);

    // Test 2: N-way (F1 style, N=3)
    // Driver A (1500) 1st, Driver B (1500) 2nd, Driver C (1500) 3rd
    Event race;
    race.id = "evt2";
    race.sport_id = "f1";
    race.participants = {
        {"A", 1},
        {"B", 2},
        {"C", 3}
    };

    std::map<std::string, double> race_ratings = {
        {"A", 1500.0},
        {"B", 1500.0},
        {"C", 1500.0}
    };

    auto race_updates = calculate_elo_updates(race, race_ratings, 32.0);
    assert(std::abs(race_updates["A"] - 1516.0) < 0.001);
    assert(std::abs(race_updates["B"] - 1500.0) < 0.001);
    assert(std::abs(race_updates["C"] - 1484.0) < 0.001);

    std::cout << "All statistical core tests passed successfully!" << std::endl;
}

// Simple manual parser to avoid JSON dependencies.
// Expects: {"participants": [{"entity_id":"id","finish_rank":1,"current_rating":1500.0}, ...]}
void run_cli() {
    std::string line;
    if (!std::getline(std::cin, line)) return;

    using namespace oddsengine;
    Event event;
    event.id = "cli_event";
    event.sport_id = "general";
    std::map<std::string, double> current_ratings;

    size_t pos = 0;
    while (true) {
        pos = line.find("\"entity_id\"", pos);
        if (pos == std::string::npos) break;

        pos = line.find("\"", pos + 11);
        size_t id_end = line.find("\"", pos + 1);
        std::string entity_id = line.substr(pos + 1, id_end - pos - 1);
        pos = id_end;

        pos = line.find("\"finish_rank\"", pos);
        pos = line.find(":", pos);
        size_t rank_end = line.find_first_of(",}", pos);
        int finish_rank = std::stoi(line.substr(pos + 1, rank_end - pos - 1));
        pos = rank_end;

        pos = line.find("\"current_rating\"", pos);
        pos = line.find(":", pos);
        size_t rating_end = line.find_first_of(",}", pos);
        double rating = std::stod(line.substr(pos + 1, rating_end - pos - 1));
        pos = rating_end;

        event.participants.push_back({entity_id, finish_rank});
        current_ratings[entity_id] = rating;
    }

    auto updates = calculate_elo_updates(event, current_ratings);

    std::cout << "{\"ratings\": {";
    bool first = true;
    for (const auto& pair : updates) {
        if (!first) std::cout << ", ";
        std::cout << "\"" << pair.first << "\": " << pair.second;
        first = false;
    }
    std::cout << "}}" << std::endl;
}

int main(int argc, char* argv[]) {
    if (argc > 1 && std::string(argv[1]) == "--cli") {
        run_cli();
    } else {
        run_tests();
    }
    return 0;
}

