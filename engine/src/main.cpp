#include "elo.hpp"
#include "glicko2.hpp"
#include <iostream>
#include <string>
#include <vector>
#include <map>
#include <cassert>
#include <cmath>

void run_tests() {
    using namespace oddsengine;

    // Test 1: Pairwise (N=2) traditional Elo update (established, k=16)
    // Team A (1600) vs Team B (1400), Team A wins
    Event match1;
    match1.id = "evt1";
    match1.sport_id = "football";
    match1.participants = {
        {"A", 1, false, 30},
        {"B", 2, false, 30}
    };

    std::map<std::string, double> ratings = {
        {"A", 1600.0},
        {"B", 1400.0}
    };

    auto updates = calculate_elo_updates(match1, ratings, 16.0);
    // E_AB = 1 / (1 + 10^((1400-1600)/400)) = 0.7597469
    // R'_A = 1600 + 16 * (1.0 - 0.7597469) = 1603.844
    // R'_B = 1400 + 16 * (0.0 - 0.240253) = 1396.156
    assert(std::abs(updates["A"] - 1603.844) < 0.001);
    assert(std::abs(updates["B"] - 1396.156) < 0.001);

    // Test 2: N-way (F1 style, N=3) (provisional, k=32)
    // Driver A (1500) 1st, Driver B (1500) 2nd, Driver C (1500) 3rd
    Event race;
    race.id = "evt2";
    race.sport_id = "f1";
    race.participants = {
        {"A", 1, false, 0},
        {"B", 2, false, 0},
        {"C", 3, false, 0}
    };

    std::map<std::string, double> race_ratings = {
        {"A", 1500.0},
        {"B", 1500.0},
        {"C", 1500.0}
    };

    auto race_updates = calculate_elo_updates(race, race_ratings, 16.0);
    // Provisional uses K=32.0.
    // For A (1st): change_A = 32.0 * ((1.0 - 0.5) + (1.0 - 0.5)) / 2 = 16.0 -> 1516.0
    // For B (2nd): change_B = 32.0 * ((0.0 - 0.5) + (1.0 - 0.5)) / 2 = 0.0 -> 1500.0
    // For C (3rd): change_C = 32.0 * ((0.0 - 0.5) + (0.0 - 0.5)) / 2 = -16.0 -> 1484.0
    assert(std::abs(race_updates["A"] - 1516.0) < 0.001);
    assert(std::abs(race_updates["B"] - 1500.0) < 0.001);
    assert(std::abs(race_updates["C"] - 1484.0) < 0.001);

    // Test 3: Home-Field Advantage
    // Team A (1500, home) vs Team B (1500, away), Draw. Established.
    Event match3;
    match3.id = "evt3";
    match3.sport_id = "football";
    match3.participants = {
        {"A", 1, true, 30},
        {"B", 1, false, 30}
    };
    std::map<std::string, double> match3_ratings = {
        {"A", 1500.0},
        {"B", 1500.0}
    };
    auto match3_updates = calculate_elo_updates(match3, match3_ratings, 16.0);
    // R_A_eff = 1600, R_B_eff = 1500 -> E_AB = 0.640065
    // R'_A = 1500 + 16 * (0.5 - 0.640065) = 1497.759
    assert(std::abs(match3_updates["A"] - 1497.759) < 0.001);
    assert(std::abs(match3_updates["B"] - 1502.241) < 0.001);

    // Test 4: Glicko-2 Worked Example from Glickman's paper
    GlickoParticipant player = {"player", 1500.0, 200.0, 0.06};
    std::vector<GlickoMatch> matches = {
        {1400.0, 30.0, 1.0},
        {1550.0, 100.0, 0.0},
        {1700.0, 300.0, 0.0}
    };
    auto glicko_result = calculate_glicko2_update(player, matches, 0.5);
    assert(std::abs(glicko_result.rating - 1464.06) < 0.05);
    assert(std::abs(glicko_result.rd - 151.52) < 0.05);
    assert(std::abs(glicko_result.volatility - 0.05999) < 0.001);

    std::cout << "All statistical core tests passed successfully!" << std::endl;
}

// Simple manual parser to avoid JSON dependencies.
// Expects: {"model_name":"glicko2", "participants": [{"entity_id":"id","finish_rank":1,"current_rating":1500.0,"rating_deviation":350.0,"volatility":0.06}, ...]}
void run_cli() {
    std::string line;
    if (!std::getline(std::cin, line)) return;

    using namespace oddsengine;
    
    std::string model_name = "elo";
    size_t model_pos = line.find("\"model_name\"");
    if (model_pos != std::string::npos) {
        model_pos = line.find("\"", model_pos + 12);
        size_t model_end = line.find("\"", model_pos + 1);
        model_name = line.substr(model_pos + 1, model_end - model_pos - 1);
    }

    if (model_name == "glicko2") {
        struct ParsedGlicko {
            std::string id;
            int rank;
            double rating;
            double rd;
            double vol;
        };
        std::vector<ParsedGlicko> parsed;
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

            pos = line.find("\"rating_deviation\"", pos);
            pos = line.find(":", pos);
            size_t rd_end = line.find_first_of(",}", pos);
            double rd = std::stod(line.substr(pos + 1, rd_end - pos - 1));
            pos = rd_end;

            pos = line.find("\"volatility\"", pos);
            pos = line.find(":", pos);
            size_t vol_end = line.find_first_of(",}", pos);
            double vol = std::stod(line.substr(pos + 1, vol_end - pos - 1));
            pos = vol_end;

            parsed.push_back({entity_id, finish_rank, rating, rd, vol});
        }

        std::cout << "{\"ratings\": {";
        bool first = true;
        for (size_t i = 0; i < parsed.size(); ++i) {
            const auto& p_a = parsed[i];
            GlickoParticipant player = {p_a.id, p_a.rating, p_a.rd, p_a.vol};
            
            std::vector<GlickoMatch> matches;
            for (size_t j = 0; j < parsed.size(); ++j) {
                if (i == j) continue;
                const auto& p_b = parsed[j];
                
                double score = 0.5;
                if (p_a.rank < p_b.rank) {
                    score = 1.0;
                } else if (p_a.rank > p_b.rank) {
                    score = 0.0;
                }
                matches.push_back({p_b.rating, p_b.rd, score});
            }
            
            auto updated = calculate_glicko2_update(player, matches, 0.5);
            if (!first) std::cout << ", ";
            std::cout << "\"" << p_a.id << "\": {\"rating\": " << updated.rating 
                      << ", \"rating_deviation\": " << updated.rd 
                      << ", \"volatility\": " << updated.volatility << "}";
            first = false;
        }
        std::cout << "}}" << std::endl;

    } else {
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

            pos = line.find("\"is_home\"", pos);
            pos = line.find(":", pos);
            size_t home_end = line.find_first_of(",}", pos);
            std::string home_str = line.substr(pos + 1, home_end - pos - 1);
            bool is_home = (home_str.find("true") != std::string::npos);
            pos = home_end;

            pos = line.find("\"matches_played\"", pos);
            pos = line.find(":", pos);
            size_t exp_end = line.find_first_of(",}", pos);
            int matches_played = std::stoi(line.substr(pos + 1, exp_end - pos - 1));
            pos = exp_end;

            event.participants.push_back({entity_id, finish_rank, is_home, matches_played});
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
}

int main(int argc, char* argv[]) {
    if (argc > 1 && std::string(argv[1]) == "--cli") {
        run_cli();
    } else {
        run_tests();
    }
    return 0;
}


