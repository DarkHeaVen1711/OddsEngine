#include "elo.hpp"
#include <iostream>
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
    // All start at 1500, so E_ij = 0.5.
    // For A (1st): change_A = 32.0 * ((1.0 - 0.5) + (1.0 - 0.5)) / 2 = 16.0 -> 1516.0
    // For B (2nd): change_B = 32.0 * ((0.0 - 0.5) + (1.0 - 0.5)) / 2 = 0.0 -> 1500.0
    // For C (3rd): change_C = 32.0 * ((0.0 - 0.5) + (0.0 - 0.5)) / 2 = -16.0 -> 1484.0
    assert(std::abs(race_updates["A"] - 1516.0) < 0.001);
    assert(std::abs(race_updates["B"] - 1500.0) < 0.001);
    assert(std::abs(race_updates["C"] - 1484.0) < 0.001);

    std::cout << "All statistical core tests passed successfully!" << std::endl;
}

int main() {
    run_tests();
    return 0;
}
