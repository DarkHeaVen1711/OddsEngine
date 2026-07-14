#include "elo.hpp"
#include "glicko2.hpp"
#include "poisson.hpp"
#include "plackett_luce.hpp"
#include "bayesian.hpp"
#include "monte_carlo.hpp"
#include "eval.hpp"
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

    // Test 5: Poisson Model Parameter Recovery
    std::vector<MatchRecord> history = {
        {"H", "A", 3, 0, 1.0},
        {"H", "A", 2, 1, 1.0},
        {"A", "H", 0, 2, 1.0},
        {"H", "A", 4, 1, 1.0}
    };
    PoissonModel poisson;
    poisson.fit(history, 100, 0.01);
    double win = 0.0, draw = 0.0, loss = 0.0;
    poisson.get_score_matrix("H", "A", win, draw, loss);
    assert(std::abs(win + draw + loss - 1.0) < 0.01);

    // Test 6: Plackett-Luce F1 Model Parameter Recovery
    RaceParticipant rp1 = {"ent1", "D1", "C1", 1, 1, false};
    RaceParticipant rp2 = {"ent2", "D2", "C2", 2, 2, false};
    RaceRecord rr1 = {"race1", {rp1, rp2}};

    RaceParticipant rp3 = {"ent1", "D1", "C1", 2, 1, false};
    RaceParticipant rp4 = {"ent2", "D2", "C2", 1, 2, false};
    RaceRecord rr2 = {"race2", {rp3, rp4}};

    PlackettLuceModel pl;
    pl.fit({rr1, rr2}, 100, 0.01);
    
    std::vector<RaceParticipant> entrants = {
        {"ent1", "D1", "C1", 1, 0, false},
        {"ent2", "D2", "C2", 1, 0, false}
    };
    auto pl_probs = pl.predict_win_probabilities(entrants);
    assert(pl_probs["ent1"] > pl_probs["ent2"]);
    assert(std::abs(pl_probs["ent1"] + pl_probs["ent2"] - 1.0) < 1e-6);

    // Test 7: Bayesian Updating Layer
    // 7a: Poisson prior seeded, no updates — mean matches Poisson output
    BayesianModel bayes;
    bayes.init_from_poisson(0.5474, 0.2745, 0.1780);
    auto res_prior = bayes.get_result(0.90);
    assert(std::abs(res_prior.mean - 0.5474) < 0.01);
    assert(res_prior.ci_lower < res_prior.mean);
    assert(res_prior.ci_upper > res_prior.mean);

    // 7b: weak prior (kappa=2) + 10 wins → posterior mean > 0.80, CI entirely above 0.50
    // With kappa=2: alpha_w=1, alpha_d=0.5, alpha_l=0.5 → after 10 wins: 11/12 ≈ 0.917
    BayesianModel bayes_wins;
    bayes_wins.init_from_poisson(0.5, 0.25, 0.25, 2.0);
    for (int i = 0; i < 10; ++i) bayes_wins.update(1.0);
    auto res_wins = bayes_wins.get_result(0.90);
    assert(res_wins.mean > 0.80);
    assert(res_wins.ci_lower > 0.50);

    // 7c: 5 wins + 5 losses → posterior mean ≈ 0.50
    BayesianModel bayes_even;
    bayes_even.init_from_elo(0.5);
    for (int i = 0; i < 5; ++i) bayes_even.update(1.0);
    for (int i = 0; i < 5; ++i) bayes_even.update(0.0);
    auto res_even = bayes_even.get_result(0.90);
    assert(std::abs(res_even.mean - 0.5) < 0.05);

    // 7d: CI shrinks as evidence increases
    assert(res_wins.ci_upper - res_wins.ci_lower < res_prior.ci_upper - res_prior.ci_lower);

    // Test 8: Monte Carlo Season Simulator (3-team round-robin sanity check)
    // Team A always beats B and C (win_prob=0.99). A should finish 1st in ~99% of sims.
    std::vector<StandingRow> standings = {{"A", 0, 0}, {"B", 0, 0}, {"C", 0, 0}};
    std::vector<SimFixture> fixtures = {
        {"A", "B", 0.99, 0.01, 0.00},
        {"A", "C", 0.99, 0.01, 0.00},
        {"B", "C", 0.50, 0.20, 0.30}
    };
    auto mc = run_monte_carlo(standings, fixtures, 1000, 42);
    // Find A's rank distribution
    double a_rank1 = 0.0;
    for (const auto& erd : mc.distributions) {
        if (erd.entity_id == "A") a_rank1 = erd.rank_probs[0];
    }
    assert(a_rank1 > 0.90); // A wins > 90% of the time with 0.99 win prob against both

    // Test 9: Model Evaluation & Backtesting
    std::vector<PredictionRecord> eval_preds = {
        {0.7, 0.2, 0.1, 1},
        {0.2, 0.6, 0.2, 0},
        {0.1, 0.3, 0.6, -1}
    };
    auto eval_res = evaluate(eval_preds, 5);
    assert(eval_res.brier_score < 0.3);
    assert(eval_res.log_loss < 0.65);
    assert(eval_res.calibration_buckets.size() == 5);

    std::vector<MatchRecord> backtest_hist = {
        {"H", "A", 3, 0, 1.0},
        {"H", "A", 2, 1, 1.0},
        {"A", "H", 0, 2, 1.0},
        {"H", "A", 4, 1, 1.0},
        {"A", "H", 1, 3, 1.0},
        {"H", "A", 3, 1, 1.0}
    };
    auto backtest_res = rolling_backtest(backtest_hist, 3);
    assert(backtest_res.n_predictions == 3);
    assert(backtest_res.brier_score > 0.0);

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

    if (model_name == "eval") {
        std::string mode = (line.find("\"backtest\"") != std::string::npos) ? "backtest" : "evaluate";
        if (mode == "evaluate") {
            std::vector<PredictionRecord> preds;
            size_t pos = 0;
            while ((pos = line.find("\"predicted_win\"", pos)) != std::string::npos) {
                auto get_d = [&](const std::string& k) {
                    size_t idx = line.find(k, pos - 20);
                    return std::stod(line.substr(line.find(":", idx) + 1, line.find_first_of(",}", idx) - line.find(":", idx) - 1));
                };
                preds.push_back({get_d("\"predicted_win\""), get_d("\"predicted_draw\""), get_d("\"predicted_loss\""), 
                                 static_cast<int>(get_d("\"actual_outcome\""))});
                pos += 15;
            }
            int n_bins = 10;
            size_t nb = line.find("\"n_bins\"");
            if (nb != std::string::npos) n_bins = std::stoi(line.substr(line.find(":", nb) + 1, line.find_first_of(",}", nb) - line.find(":", nb) - 1));
            
            auto eval = evaluate(preds, n_bins);
            std::cout << "{\"brier_score\":" << eval.brier_score << ",\"log_loss\":" << eval.log_loss << ",\"calibration_buckets\":[";
            for (size_t i = 0; i < eval.calibration_buckets.size(); ++i) {
                const auto& b = eval.calibration_buckets[i];
                if (i > 0) std::cout << ",";
                std::cout << "{\"bin_lower\":" << b.bin_lower << ",\"bin_upper\":" << b.bin_upper 
                          << ",\"predicted_freq\":" << b.predicted_freq << ",\"actual_freq\":" << b.actual_freq 
                          << ",\"count\":" << b.count << "}";
            }
            std::cout << "]}" << std::endl;
        } else {
            std::vector<MatchRecord> history;
            size_t pos = 0;
            while ((pos = line.find("\"home_id\"", pos)) != std::string::npos) {
                auto get_str = [&](const std::string& k) {
                    size_t idx = line.find(k, pos - 10);
                    size_t s = line.find("\"", line.find(":", idx));
                    return line.substr(s + 1, line.find("\"", s + 1) - s - 1);
                };
                auto get_val = [&](const std::string& k) {
                    size_t idx = line.find(k, pos - 10);
                    return std::stod(line.substr(line.find(":", idx) + 1, line.find_first_of(",}", idx) - line.find(":", idx) - 1));
                };
                history.push_back({get_str("\"home_id\""), get_str("\"away_id\""), 
                                  static_cast<int>(get_val("\"home_goals\"")), static_cast<int>(get_val("\"away_goals\"")), get_val("\"weight\"")});
                pos += 9;
            }
            int min_train = 5;
            size_t mt = line.find("\"min_training_matches\"");
            if (mt != std::string::npos) min_train = std::stoi(line.substr(line.find(":", mt) + 1, line.find_first_of(",}", mt) - line.find(":", mt) - 1));
            auto res = rolling_backtest(history, min_train);
            std::cout << "{\"brier_score\":" << res.brier_score << ",\"log_loss\":" << res.log_loss << ",\"n_predictions\":" << res.n_predictions << "}" << std::endl;
        }
    } else if (model_name == "bayesian") {
        // Expects: {"model_name":"bayesian","prior":{"win":0.5474,"draw":0.2745,"loss":0.1780},"kappa":10.0,"outcomes":[1.0,0.0,1.0,...]}
        double prior_win = 1.0/3.0, prior_draw = 1.0/3.0, prior_loss = 1.0/3.0, kappa = 10.0;

        auto parse_double = [&](const std::string& key) -> double {
            size_t p = line.find(key);
            if (p == std::string::npos) return -1.0;
            p = line.find(":", p);
            size_t e = line.find_first_of(",}", p);
            return std::stod(line.substr(p + 1, e - p - 1));
        };

        double v;
        if ((v = parse_double("\"win\""))   >= 0.0) prior_win  = v;
        if ((v = parse_double("\"draw\""))  >= 0.0) prior_draw = v;
        if ((v = parse_double("\"loss\""))  >= 0.0) prior_loss = v;
        if ((v = parse_double("\"kappa\"")) >= 0.0) kappa      = v;

        BayesianModel bayes;
        bayes.init_from_poisson(prior_win, prior_draw, prior_loss, kappa);

        size_t o_pos = line.find("\"outcomes\"");
        if (o_pos != std::string::npos) {
            size_t o_end = line.find("]", o_pos);
            o_pos = line.find("[", o_pos);
            while (o_pos < o_end) {
                size_t num_start = line.find_first_of("0123456789.-", o_pos + 1);
                if (num_start == std::string::npos || num_start >= o_end) break;
                size_t num_end = line.find_first_of(",]", num_start);
                bayes.update(std::stod(line.substr(num_start, num_end - num_start)));
                o_pos = num_end;
            }
        }

        double ci_level = 0.90;
        if ((v = parse_double("\"ci_level\"")) >= 0.0) ci_level = v;

        auto result = bayes.get_result(ci_level);
        std::cout << "{\"win_probability\": " << result.mean
                  << ", \"ci_lower\": " << result.ci_lower
                  << ", \"ci_upper\": " << result.ci_upper
                  << ", \"ci_level\": " << result.ci_level << "}" << std::endl;

    } else if (model_name == "monte_carlo") {
        // Expects: {"model_name":"monte_carlo","n_simulations":10000,"seed":42,
        //   "standings":[{"entity_id":"ENG","points":10,"goal_diff":5},...],
        //   "fixtures":[{"home_id":"ENG","away_id":"ARG","win_prob":0.55,"draw_prob":0.27,"loss_prob":0.18},...]}
        int n_sims = 10000;
        uint64_t mc_seed = 42;

        auto pint = [&](const std::string& key) -> int {
            size_t p = line.find(key); if (p == std::string::npos) return -1;
            p = line.find(":", p); size_t e = line.find_first_of(",}", p);
            return std::stoi(line.substr(p + 1, e - p - 1));
        };
        auto pdbl = [&](const std::string& key, size_t from = 0) -> double {
            size_t p = line.find(key, from); if (p == std::string::npos) return -1.0;
            p = line.find(":", p); size_t e = line.find_first_of(",}", p);
            return std::stod(line.substr(p + 1, e - p - 1));
        };

        int tmp_n = pint("\"n_simulations\""); if (tmp_n > 0) n_sims = tmp_n;
        double tmp_seed = pdbl("\"seed\""); if (tmp_seed >= 0.0) mc_seed = static_cast<uint64_t>(tmp_seed);

        std::vector<StandingRow> mc_standings;
        size_t st_pos = line.find("\"standings\"");
        if (st_pos != std::string::npos) {
            size_t st_end = line.find("]", st_pos);
            while ((st_pos = line.find("\"entity_id\"", st_pos)) != std::string::npos && st_pos < st_end) {
                st_pos = line.find("\"", st_pos + 11); size_t id_end = line.find("\"", st_pos + 1);
                std::string eid = line.substr(st_pos + 1, id_end - st_pos - 1);
                int pts = pint("\"points\""); int gd = pint("\"goal_diff\"");
                mc_standings.push_back({eid, pts < 0 ? 0 : pts, gd < 0 ? 0 : gd});
                st_pos = line.find("}", st_pos) + 1;
            }
        }

        std::vector<SimFixture> mc_fixtures;
        size_t fx_pos = line.find("\"fixtures\"");
        if (fx_pos != std::string::npos) {
            size_t fx_end = line.find("]", fx_pos);
            while ((fx_pos = line.find("\"home_id\"", fx_pos)) != std::string::npos && fx_pos < fx_end) {
                fx_pos = line.find("\"", fx_pos + 9); size_t h_end = line.find("\"", fx_pos + 1);
                std::string hid = line.substr(fx_pos + 1, h_end - fx_pos - 1); fx_pos = h_end;
                size_t a_pos = line.find("\"away_id\"", fx_pos);
                a_pos = line.find("\"", a_pos + 9); size_t a_end = line.find("\"", a_pos + 1);
                std::string aid = line.substr(a_pos + 1, a_end - a_pos - 1);
                double wp = pdbl("\"win_prob\"", fx_pos), dp = pdbl("\"draw_prob\"", fx_pos), lp = pdbl("\"loss_prob\"", fx_pos);
                mc_fixtures.push_back({hid, aid, wp < 0 ? 0.4 : wp, dp < 0 ? 0.3 : dp, lp < 0 ? 0.3 : lp});
                fx_pos = line.find("}", fx_pos) + 1;
            }
        }

        auto mc_result = run_monte_carlo(mc_standings, mc_fixtures, n_sims, mc_seed);
        std::cout << "{\"rank_distributions\": {";
        bool first_e = true;
        for (const auto& erd : mc_result.distributions) {
            if (!first_e) std::cout << ", ";
            std::cout << "\"" << erd.entity_id << "\": [";
            for (size_t ri = 0; ri < erd.rank_probs.size(); ++ri) {
                if (ri > 0) std::cout << ", ";
                std::cout << erd.rank_probs[ri];
            }
            std::cout << "]";
            first_e = false;
        }
        std::cout << "}}" << std::endl;

    } else if (model_name == "glicko2") {
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

    } else if (model_name == "plackett_luce") {
        std::vector<RaceRecord> history;
        size_t pos = line.find("\"history\"");
        if (pos != std::string::npos) {
            size_t hist_end = line.find("]", pos);
            while ((pos = line.find("\"race_id\"", pos)) != std::string::npos && pos < hist_end) {
                pos = line.find("\"", pos + 9);
                size_t r_end = line.find("\"", pos + 1);
                RaceRecord rec = {line.substr(pos + 1, r_end - pos - 1), {}};
                pos = r_end;
                size_t p_list_end = line.find("]", pos);
                while ((pos = line.find("\"entity_id\"", pos)) != std::string::npos && pos < p_list_end) {
                    auto extract = [&](const std::string& key) {
                        size_t k_pos = line.find(key, pos);
                        k_pos = line.find_first_of("\":", k_pos + key.size());
                        k_pos = line.find_first_of("\"0123456789t", k_pos);
                        size_t k_end = line.find_first_of(",}\"", k_pos);
                        return line.substr(k_pos, k_end - k_pos);
                    };
                    std::string id = extract("\"entity_id\"");
                    std::string d = extract("\"driver_id\"");
                    std::string c = extract("\"constructor_id\"");
                    int gp = std::stoi(extract("\"grid_position\""));
                    int fr = std::stoi(extract("\"finish_rank\""));
                    bool dnf = (extract("\"is_dnf\"").find("true") != std::string::npos);
                    rec.participants.push_back({id, d, c, gp, fr, dnf});
                    pos = line.find("}", pos) + 1;
                }
                history.push_back(rec);
            }
        }
        std::vector<RaceParticipant> entrants;
        size_t pred_pos = line.find("\"predict_entrants\"");
        if (pred_pos != std::string::npos) {
            size_t pred_end = line.find("]", pred_pos);
            while ((pos = line.find("\"entity_id\"", pred_pos)) != std::string::npos && pos < pred_end) {
                auto extract = [&](const std::string& key) {
                    size_t k_pos = line.find(key, pos);
                    k_pos = line.find_first_of("\":", k_pos + key.size());
                    k_pos = line.find_first_of("\"0123456789", k_pos);
                    size_t k_end = line.find_first_of(",}\"", k_pos);
                    return line.substr(k_pos, k_end - k_pos);
                };
                entrants.push_back({extract("\"entity_id\""), extract("\"driver_id\""), extract("\"constructor_id\""), std::stoi(extract("\"grid_position\"")), 0, false});
                pred_pos = line.find("}", pos) + 1;
            }
        }
        PlackettLuceModel pl;
        pl.fit(history, 100, 0.005);
        auto win_probs = pl.predict_win_probabilities(entrants);
        std::cout << "{\"probabilities\": {";
        bool first = true;
        for (const auto& pair : win_probs) {
            if (!first) std::cout << ", ";
            std::cout << "\"" << pair.first << "\": " << pair.second;
            first = false;
        }
        std::cout << "}}" << std::endl;

    } else if (model_name == "poisson") {
        std::vector<MatchRecord> history;
        size_t pos = line.find("\"history\"");
        if (pos != std::string::npos) {
            size_t hist_end = line.find("]", pos);
            while (true) {
                pos = line.find("\"home_id\"", pos);
                if (pos == std::string::npos || pos > hist_end) break;

                pos = line.find("\"", pos + 9);
                size_t h_end = line.find("\"", pos + 1);
                std::string home_id = line.substr(pos + 1, h_end - pos - 1);
                pos = h_end;

                pos = line.find("\"away_id\"", pos);
                pos = line.find("\"", pos + 9);
                size_t a_end = line.find("\"", pos + 1);
                std::string away_id = line.substr(pos + 1, a_end - pos - 1);
                pos = a_end;

                pos = line.find("\"home_goals\"", pos);
                pos = line.find(":", pos);
                size_t hg_end = line.find_first_of(",}", pos);
                int home_goals = std::stoi(line.substr(pos + 1, hg_end - pos - 1));
                pos = hg_end;

                pos = line.find("\"away_goals\"", pos);
                pos = line.find(":", pos);
                size_t ag_end = line.find_first_of(",}", pos);
                int away_goals = std::stoi(line.substr(pos + 1, ag_end - pos - 1));
                pos = ag_end;

                pos = line.find("\"weight\"", pos);
                pos = line.find(":", pos);
                size_t w_end = line.find_first_of(",}", pos);
                double weight = std::stod(line.substr(pos + 1, w_end - pos - 1));
                pos = w_end;

                history.push_back({home_id, away_id, home_goals, away_goals, weight});
            }
        }

        std::string pred_home = "H";
        std::string pred_away = "A";
        size_t pred_pos = line.find("\"predict_match\"");
        if (pred_pos != std::string::npos) {
            pred_pos = line.find("\"home_id\"", pred_pos);
            pred_pos = line.find("\"", pred_pos + 9);
            size_t h_end = line.find("\"", pred_pos + 1);
            pred_home = line.substr(pred_pos + 1, h_end - pred_pos - 1);
            pred_pos = h_end;

            pred_pos = line.find("\"away_id\"", pred_pos);
            pred_pos = line.find("\"", pred_pos + 9);
            size_t a_end = line.find("\"", pred_pos + 1);
            pred_away = line.substr(pred_pos + 1, a_end - pred_pos - 1);
        }

        PoissonModel poisson;
        poisson.fit(history, 100, 0.005);

        double win = 0.0, draw = 0.0, loss = 0.0;
        poisson.get_score_matrix(pred_home, pred_away, win, draw, loss);

        std::cout << "{\"probabilities\": {\"win\": " << win 
                  << ", \"draw\": " << draw 
                  << ", \"loss\": " << loss << "}}" << std::endl;

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


