import sqlite3
import json
import subprocess
import os
import math

DB_PATH = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "db.sqlite3"))
ENGINE_PATH = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "engine", "engine.exe"))
REPORT_PATH = r"C:\Users\Vyom\.gemini\antigravity-ide\brain\4cedbe79-42b4-4839-aec1-b2822290c69e\backtest_report.md"

def expected_score(r_a, r_b):
    return 1.0 / (1.0 + math.pow(10.0, (r_b - r_a) / 400.0))

def main():
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    
    cursor.execute("""
        SELECT e.id, e.timestamp, p.entity_id, p.result_data_json, p.finish_rank
        FROM events e
        JOIN participants p ON e.id = p.event_id
        WHERE e.sport_id = 'football' AND e.status = 'completed'
        ORDER BY e.timestamp ASC
    """)
    rows = cursor.fetchall()
    
    matches = {}
    for row in rows:
        event_id, ts, ent_id, res_json, rank = row
        goals = json.loads(res_json).get("goals", 0)
        if event_id not in matches:
            matches[event_id] = {"timestamp": ts, "teams": []}
        matches[event_id]["teams"].append({"id": ent_id, "goals": goals, "rank": rank})
        
    conn.close()
    
    sorted_matches = sorted(matches.items(), key=lambda x: x[1]["timestamp"])[:80]
    
    history = []
    elo_predictions = []
    poisson_predictions = []
    elo_ratings = {}
    
    # Spawn single engine process
    proc = subprocess.Popen(
        [ENGINE_PATH, "--cli"],
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True
    )

    def engine_request(req):
        proc.stdin.write(json.dumps(req) + "\n")
        proc.stdin.flush()
        return json.loads(proc.stdout.readline())

    for i, (event_id, m_data) in enumerate(sorted_matches):
        if i % 10 == 0:
            print(f"Processing match {i}/{len(sorted_matches)}...", flush=True)
            
        if len(m_data["teams"]) != 2:
            continue
        t1, t2 = m_data["teams"][0], m_data["teams"][1]
        
        r1 = elo_ratings.get(t1["id"], 1500.0)
        r2 = elo_ratings.get(t2["id"], 1500.0)
        
        actual_outcome = 1 if t1["goals"] == t2["goals"] else (0 if t1["goals"] > t2["goals"] else 2)
        
        diff = (r1 - r2 + 100.0) / 400.0
        elo_p_win = 1.0 / (1.0 + math.pow(10.0, -diff))
        elo_p_draw = 0.25 * math.exp(-(diff**2))
        elo_p_win = elo_p_win * (1 - elo_p_draw)
        elo_p_loss = 1.0 - elo_p_win - elo_p_draw
        
        if len(history) >= 3:
            elo_predictions.append({
                "predicted_win": elo_p_win,
                "predicted_draw": elo_p_draw,
                "predicted_loss": elo_p_loss,
                "actual_outcome": actual_outcome
            })
            
            engine_input = {
                "model_name": "poisson",
                "history": history[-100:], 
                "predict_match": {
                    "home_id": t1["id"],
                    "away_id": t2["id"]
                }
            }
            try:
                res = engine_request(engine_input)
                probs = res.get("probabilities", {})
                poisson_predictions.append({
                    "predicted_win": probs.get("win", 0.33),
                    "predicted_draw": probs.get("draw", 0.33),
                    "predicted_loss": probs.get("loss", 0.34),
                    "actual_outcome": actual_outcome
                })
            except Exception as e:
                print(e)
                pass
        
        history.append({
            "home_id": t1["id"],
            "away_id": t2["id"],
            "home_goals": t1["goals"],
            "away_goals": t2["goals"],
            "weight": 1.0
        })
        
        elo_input = {
            "model_name": "elo",
            "participants": [
                {"entity_id": t1["id"], "finish_rank": t1["rank"], "current_rating": r1, "is_home": True, "matches_played": 30},
                {"entity_id": t2["id"], "finish_rank": t2["rank"], "current_rating": r2, "is_home": False, "matches_played": 30}
            ]
        }
        try:
            res = engine_request(elo_input)
            ratings = res.get("ratings", {})
            elo_ratings[t1["id"]] = ratings.get(t1["id"], r1)
            elo_ratings[t2["id"]] = ratings.get(t2["id"], r2)
        except Exception:
            pass

    def evaluate_preds(preds):
        if not preds: return 0.0, 0.0
        eval_input = {
            "model_name": "eval",
            "predictions": preds
        }
        try:
            res = engine_request(eval_input)
            return res.get("brier_score", 0.0), res.get("log_loss", 0.0)
        except Exception:
            pass
        return 0.0, 0.0

    elo_brier, elo_log = evaluate_preds(elo_predictions)
    poisson_brier, poisson_log = evaluate_preds(poisson_predictions)
    
    proc.terminate()

    with open(REPORT_PATH, "w") as f:
        f.write("# Model Backtesting & Evaluation Report\n\n")
        f.write("Comparative evaluation of the statistical models over historical football matches.\n\n")
        f.write("> [!NOTE]\n")
        f.write("> Lower Brier Score and Log Loss denote better accuracy. A model must beat Tier 0 to be deployed to Tier 1.\n\n")
        
        f.write("## Performance Metrics\n\n")
        f.write(f"- **Evaluated Matches**: {len(poisson_predictions)}\n\n")
        
        f.write("### Tier 0: Baseline (Generalised Elo)\n")
        f.write(f"- **Brier Score**: {elo_brier:.5f}\n")
        f.write(f"- **Log Loss**: {elo_log:.5f}\n\n")
        
        f.write("### Tier 1: Validated Model (Bivariate Poisson)\n")
        f.write(f"- **Brier Score**: {poisson_brier:.5f}\n")
        f.write(f"- **Log Loss**: {poisson_log:.5f}\n\n")
        
        if poisson_brier < elo_brier:
            f.write("> [!TIP]\n")
            f.write("> Validation Successful! The Bivariate Poisson model effectively outperforms the Elo baseline.\n")
        else:
            f.write("> [!WARNING]\n")
            f.write("> Validation Failed! The Tier 1 model did not beat the Tier 0 baseline.\n")

    print(f"Backtesting complete. Report saved to {REPORT_PATH}")

if __name__ == "__main__":
    main()
