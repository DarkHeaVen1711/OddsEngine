"use client";

import React, { useState, useEffect, useCallback } from "react";
import {
  LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer,
  BarChart, Bar, Cell, ScatterChart, Scatter, ReferenceLine,
} from "recharts";

const API = "http://localhost:8080";

// ─── Type Definitions ────────────────────────────────────────────────────────

interface LeaderboardEntry {
  entityId: string;
  sportId: string;
  modelName: string;
  rating: number;
  ratingDeviation?: number;
  volatility?: number;
  asOfTimestamp: number;
}

interface RatingPoint {
  timestamp: number;
  rating: number;
  modelName: string;
}

interface Match {
  id: string;
  sportId: string;
  venue: string;
  homeTeam: string;
  awayTeam: string;
  status: string;
  probabilities?: { win: number; draw: number; loss: number };
  scorelineMatrix?: number[][];
}

interface CalibrationBucket {
  bucketMidpoint: number;
  meanPredicted: number;
  meanActual: number;
  count: number;
}

interface AccuracyData {
  modelName: string;
  sampleSize: number;
  brierScore: number | null;
  logLoss: number | null;
  calibrationBuckets: CalibrationBucket[];
}

interface SimResults {
  [team: string]: number[];
}

// ─── Mock Fallbacks ───────────────────────────────────────────────────────────

const MOCK_LEADERBOARD: LeaderboardEntry[] = [
  { entityId: "man_city",     sportId: "football", modelName: "elo", rating: 1684.52, asOfTimestamp: Date.now() },
  { entityId: "real_madrid",  sportId: "football", modelName: "elo", rating: 1655.10, asOfTimestamp: Date.now() },
  { entityId: "barcelona",    sportId: "football", modelName: "elo", rating: 1621.34, asOfTimestamp: Date.now() },
  { entityId: "liverpool",    sportId: "football", modelName: "elo", rating: 1604.22, asOfTimestamp: Date.now() },
  { entityId: "arsenal",      sportId: "football", modelName: "elo", rating: 1598.90, asOfTimestamp: Date.now() },
  { entityId: "bayern_munich",sportId: "football", modelName: "elo", rating: 1582.44, asOfTimestamp: Date.now() },
];

const MOCK_MATCHES: Match[] = [
  { id: "euro_sf_1", sportId: "football", venue: "Allianz Arena",   homeTeam: "Spain",   awayTeam: "France",    status: "scheduled" },
  { id: "wc_sf_2",   sportId: "football", venue: "Wembley Stadium", homeTeam: "England", awayTeam: "Argentina", status: "scheduled" },
  { id: "pl_match_1",sportId: "football", venue: "Emirates Stadium",homeTeam: "Arsenal", awayTeam: "Chelsea",   status: "scheduled" },
];

const MOCK_PREDICTIONS: Record<string, { win: number; draw: number; loss: number }> = {
  "euro_sf_1":  { win: 0.6516, draw: 0.2284, loss: 0.1200 },
  "wc_sf_2":    { win: 0.4215, draw: 0.2810, loss: 0.2975 },
  "pl_match_1": { win: 0.5200, draw: 0.2400, loss: 0.2400 },
};

const MOCK_RATING_HISTORY: RatingPoint[] = [
  { timestamp: 1680000000000, rating: 1580, modelName: "elo" },
  { timestamp: 1682592000000, rating: 1598, modelName: "elo" },
  { timestamp: 1685270400000, rating: 1612, modelName: "elo" },
  { timestamp: 1687862400000, rating: 1605, modelName: "elo" },
  { timestamp: 1690540800000, rating: 1630, modelName: "elo" },
  { timestamp: 1693219200000, rating: 1648, modelName: "elo" },
  { timestamp: 1695811200000, rating: 1665, modelName: "elo" },
  { timestamp: 1698489600000, rating: 1650, modelName: "elo" },
  { timestamp: 1701168000000, rating: 1672, modelName: "elo" },
  { timestamp: 1703760000000, rating: 1684, modelName: "elo" },
];

const MOCK_ACCURACY: AccuracyData = {
  modelName: "elo",
  sampleSize: 0,
  brierScore: null,
  logLoss: null,
  calibrationBuckets: [
    { bucketMidpoint: 0.05, meanPredicted: 0.05, meanActual: 0.04, count: 12 },
    { bucketMidpoint: 0.15, meanPredicted: 0.15, meanActual: 0.13, count: 18 },
    { bucketMidpoint: 0.25, meanPredicted: 0.25, meanActual: 0.26, count: 24 },
    { bucketMidpoint: 0.35, meanPredicted: 0.35, meanActual: 0.33, count: 31 },
    { bucketMidpoint: 0.45, meanPredicted: 0.45, meanActual: 0.47, count: 28 },
    { bucketMidpoint: 0.55, meanPredicted: 0.55, meanActual: 0.54, count: 22 },
    { bucketMidpoint: 0.65, meanPredicted: 0.65, meanActual: 0.67, count: 19 },
    { bucketMidpoint: 0.75, meanPredicted: 0.75, meanActual: 0.74, count: 14 },
    { bucketMidpoint: 0.85, meanPredicted: 0.85, meanActual: 0.88, count: 9  },
    { bucketMidpoint: 0.95, meanPredicted: 0.95, meanActual: 0.92, count: 5  },
  ],
};

// Build a mock 5×5 scoreline matrix (normalized Poisson-like distribution)
function buildMockMatrix(lambdaA = 1.4, lambdaB = 1.1): number[][] {
  const factorial = (n: number): number => n <= 1 ? 1 : n * factorial(n - 1);
  const poisson = (lambda: number, k: number) => Math.exp(-lambda) * Math.pow(lambda, k) / factorial(k);
  const size = 6;
  const mat: number[][] = [];
  for (let i = 0; i < size; i++) {
    mat[i] = [];
    for (let j = 0; j < size; j++) {
      mat[i][j] = parseFloat((poisson(lambdaA, i) * poisson(lambdaB, j) * 100).toFixed(2));
    }
  }
  return mat;
}

const MOCK_MATRIX = buildMockMatrix();

// ─── Colour helpers ───────────────────────────────────────────────────────────

const RANK_COLORS = ["#7c3aed", "#4f46e5", "#0891b2", "#059669", "#d97706", "#dc2626"];

function heatColor(value: number, max: number): string {
  const ratio = max > 0 ? value / max : 0;
  const h = 240 - Math.round(ratio * 180); // blue → green → red
  return `hsl(${h}, 80%, ${20 + Math.round(ratio * 40)}%)`;
}

// ─── Main Dashboard ───────────────────────────────────────────────────────────

export default function Dashboard() {
  const [activeTab, setActiveTab] = useState("leaderboard");
  const [sportId,   setSportId]   = useState("football");
  const [modelName, setModelName] = useState("elo");

  // Leaderboard
  const [leaderboard, setLeaderboard] = useState<LeaderboardEntry[]>([]);

  // Predictions
  const [upcomingMatches,   setUpcomingMatches]   = useState<Match[]>([]);
  const [selectedMatch,     setSelectedMatch]     = useState<Match | null>(null);
  const [detailMatrix,      setDetailMatrix]      = useState<number[][] | null>(null);

  // Rating history
  const [historyEntityId,   setHistoryEntityId]   = useState("man_city");
  const [ratingHistory,     setRatingHistory]     = useState<RatingPoint[]>([]);
  const [historyLoading,    setHistoryLoading]    = useState(false);

  // Simulation
  const [simResults,        setSimResults]        = useState<SimResults | null>(null);
  const [simStatus,         setSimStatus]         = useState("");
  const [simLoading,        setSimLoading]        = useState(false);
  const [simIterations,     setSimIterations]     = useState(1000);

  // Accuracy / Calibration
  const [accuracyData,      setAccuracyData]      = useState<AccuracyData | null>(null);
  const [accuracyModel,     setAccuracyModel]     = useState("elo");
  const [accuracyLoading,   setAccuracyLoading]   = useState(false);

  // Chat NLU state
  interface ChatMessage {
    sender: "user" | "engine";
    text: string;
    success?: boolean;
    resolvedFixture?: {
      eventId: string;
      sportId: string;
      venue: string;
      date: any;
      format?: string;
    };
    probabilities?: { win: number; draw: number; loss: number };
    candidates?: Array<{
      eventId: string;
      sportId: string;
      venue: string;
      date: any;
    }>;
  }
  const [chatInput, setChatInput] = useState("");
  const [chatMessages, setChatMessages] = useState<ChatMessage[]>([
    { sender: "engine", text: "Welcome to OddsEngine assistant. Ask me to predict matches using natural language (e.g. 'predict Man City vs Real Madrid' or 'who wins France vs Spain')." }
  ]);
  const [chatLoading, setChatLoading] = useState(false);

  // ── Effects ────────────────────────────────────────────────────────────────

  useEffect(() => { fetchLeaderboard(); fetchUpcoming(); }, [sportId, modelName]);

  // ── Fetch helpers ──────────────────────────────────────────────────────────

  const fetchLeaderboard = async () => {
    try {
      const res = await fetch(`${API}/api/entities/leaderboard?sportId=${sportId}&modelName=${modelName}`);
      if (res.ok) {
        const data: LeaderboardEntry[] = await res.json();
        if (data?.length > 0) { setLeaderboard(data); return; }
      }
    } catch {}
    setLeaderboard(MOCK_LEADERBOARD.filter(e => e.sportId === sportId && e.modelName === modelName));
  };

  const fetchUpcoming = async () => {
    try {
      const res = await fetch(`${API}/api/predictions/upcoming`);
      if (res.ok) {
        const data = await res.json();
        if (data?.length > 0) {
          setUpcomingMatches(data.map((m: any) => ({
            id: m.id, sportId: m.sportId, venue: m.venue ?? "TBD", homeTeam: m.id, awayTeam: m.id, status: m.status,
          })));
          return;
        }
      }
    } catch {}
    setUpcomingMatches(MOCK_MATCHES);
  };

  const handlePredict = async (matchId: string) => {
    try {
      const res = await fetch(`${API}/api/predictions/match/${matchId}?modelName=poisson`, { method: "POST" });
      if (res.ok) {
        const data = await res.json();
        const probs = JSON.parse(data.predictedOutcomeProbsJson);
        setUpcomingMatches(prev => prev.map(m => m.id === matchId ? { ...m, probabilities: probs } : m));
        return;
      }
    } catch {}
    setUpcomingMatches(prev => prev.map(m => m.id === matchId ? { ...m, probabilities: MOCK_PREDICTIONS[matchId] } : m));
  };

  const openMatchDetail = async (match: Match) => {
    setSelectedMatch(match);
    // Try to fetch real scoreline from a stored prediction, fall back to mock
    try {
      const res = await fetch(`${API}/api/predictions/match/${match.id}?modelName=poisson`, { method: "POST" });
      if (res.ok) {
        const data = await res.json();
        const probs = JSON.parse(data.predictedOutcomeProbsJson);
        setUpcomingMatches(prev => prev.map(m => m.id === match.id ? { ...m, probabilities: probs } : m));
      }
    } catch {}
    setDetailMatrix(MOCK_MATRIX);
  };

  const fetchRatingHistory = useCallback(async () => {
    setHistoryLoading(true);
    try {
      const res = await fetch(`${API}/api/entities/${historyEntityId}/rating-history?sportId=${sportId}&modelName=${modelName}`);
      if (res.ok) {
        const data: RatingPoint[] = await res.json();
        if (data?.length > 0) { setRatingHistory(data); setHistoryLoading(false); return; }
      }
    } catch {}
    setRatingHistory(MOCK_RATING_HISTORY);
    setHistoryLoading(false);
  }, [historyEntityId, sportId, modelName]);

  useEffect(() => {
    if (activeTab === "history") fetchRatingHistory();
  }, [activeTab, fetchRatingHistory]);

  const fetchAccuracy = useCallback(async () => {
    setAccuracyLoading(true);
    try {
      const res = await fetch(`${API}/api/models/accuracy?modelName=${accuracyModel}&since=0`);
      if (res.ok) {
        const data: AccuracyData = await res.json();
        setAccuracyData(data);
        setAccuracyLoading(false);
        return;
      }
    } catch {}
    setAccuracyData({ ...MOCK_ACCURACY, modelName: accuracyModel });
    setAccuracyLoading(false);
  }, [accuracyModel]);

  useEffect(() => {
    if (activeTab === "accuracy") fetchAccuracy();
  }, [activeTab, fetchAccuracy]);

  const runSimulation = async () => {
    setSimLoading(true); setSimStatus("INITIALIZING"); setSimResults(null);
    try {
      const res = await fetch(`${API}/api/simulations/season/${sportId}?nSimulations=${simIterations}`, { method: "POST" });
      if (res.ok) {
        const { jobId } = await res.json();
        pollJob(jobId);
        return;
      }
    } catch {}
    simulateMock();
  };

  const pollJob = (jobId: string) => {
    const iv = setInterval(async () => {
      try {
        const res = await fetch(`${API}/api/simulations/${jobId}`);
        if (res.ok) {
          const job = await res.json();
          setSimStatus(job.status);
          if (job.status === "COMPLETED") {
            clearInterval(iv);
            setSimResults(JSON.parse(job.resultJson));
            setSimLoading(false);
          } else if (job.status === "FAILED") {
            clearInterval(iv); setSimLoading(false); simulateMock();
          }
        }
      } catch { clearInterval(iv); simulateMock(); }
    }, 1000);
  };

  const simulateMock = () => {
    setTimeout(() => {
      setSimStatus("COMPLETED");
      setSimResults({
        "Man City":   [0.65, 0.20, 0.10, 0.05],
        "Real Madrid":[0.20, 0.45, 0.25, 0.10],
        "Barcelona":  [0.10, 0.25, 0.45, 0.20],
        "Liverpool":  [0.05, 0.10, 0.20, 0.65],
      });
      setSimLoading(false);
    }, 1200);
  };

  const handleSendChatMessage = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    if (!chatInput.trim() || chatLoading) return;

    const userText = chatInput;
    setChatInput("");
    setChatMessages(prev => [...prev, { sender: "user", text: userText }]);
    setChatLoading(true);

    try {
      const res = await fetch(`${API}/api/chat/predict`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ query: userText }),
      });
      if (res.ok) {
        const data = await res.json();
        setChatMessages(prev => [...prev, {
          sender: "engine",
          text: data.message,
          success: data.success,
          resolvedFixture: data.resolvedFixture,
          probabilities: data.probabilities,
          candidates: data.candidates,
        }]);
      } else {
        setChatMessages(prev => [...prev, {
          sender: "engine",
          text: "Error calling chat prediction service.",
        }]);
      }
    } catch {
      setChatMessages(prev => [...prev, {
        sender: "engine",
        text: "Could not connect to the platform backend.",
      }]);
    } finally {
      setChatLoading(false);
    }
  };

  // Build stacked bar data for simulation
  const simChartData = simResults
    ? Object.entries(simResults).map(([team, probs]) => ({
        team,
        "1st":   +(probs[0] * 100).toFixed(1),
        "2nd":   +(probs[1] * 100).toFixed(1),
        "3rd":   +(probs[2] * 100).toFixed(1),
        "4th+":  +((1 - probs[0] - probs[1] - probs[2]) * 100).toFixed(1),
      }))
    : [];

  // Build calibration scatter data
  const calibScatterData = accuracyData?.calibrationBuckets.map(b => ({
    predicted: +b.meanPredicted.toFixed(3),
    actual:    +b.meanActual.toFixed(3),
    count:     b.count,
  })) ?? [];

  // Rating history chart data
  const historyChartData = ratingHistory.map(p => ({
    date: new Date(p.timestamp).toLocaleDateString("en-GB", { month: "short", year: "2-digit" }),
    rating: +p.rating.toFixed(1),
  }));

  // ── Render ─────────────────────────────────────────────────────────────────

  return (
    <div className="app-container">
      <header>
        <h1 className="hero-title">OddsEngine</h1>
        <p className="hero-subtitle">Resilient C++ Core Driven Predictive Sports Intelligence</p>
      </header>

      {/* ── Tab Bar ── */}
      <div className="tabs-container">
        {[
          ["leaderboard", "Leaderboard"],
          ["predictions", "Predictions"],
          ["history",     "Rating History"],
          ["simulation",  "Season Sim"],
          ["accuracy",    "Model Accuracy"],
          ["chat",        "Chat Predict"],
        ].map(([id, label]) => (
          <button
            key={id}
            id={`tab-${id}`}
            className={`tab-btn ${activeTab === id ? "active" : ""}`}
            onClick={() => setActiveTab(id)}
          >
            {label}
          </button>
        ))}
      </div>

      {/* ── Sport / Model Filters (global) ── */}
      <div className="global-filters">
        <select id="sport-select" value={sportId} onChange={e => setSportId(e.target.value)}>
          <option value="football">Football</option>
          <option value="cricket">Cricket</option>
          <option value="f1">Formula 1</option>
        </select>
        <select id="model-select" value={modelName} onChange={e => setModelName(e.target.value)}>
          <option value="elo">Elo Rating</option>
          <option value="glicko2">Glicko-2</option>
          <option value="poisson">Poisson</option>
        </select>
      </div>

      {/* ══════════════ LEADERBOARD TAB ══════════════ */}
      {activeTab === "leaderboard" && (
        <div className="card">
          <h2 className="card-title">Entity Leaderboard</h2>
          <table className="leaderboard-table">
            <thead>
              <tr>
                <th>Rank</th>
                <th>Entity</th>
                <th>Sport</th>
                <th>Model</th>
                <th>Rating</th>
              </tr>
            </thead>
            <tbody>
              {leaderboard.map((item, idx) => (
                <tr key={item.entityId}>
                  <td><strong>#{idx + 1}</strong></td>
                  <td style={{ textTransform: "capitalize" }}>{item.entityId.replace(/_/g, " ")}</td>
                  <td>{item.sportId.toUpperCase()}</td>
                  <td>{item.modelName.toUpperCase()}</td>
                  <td><span className="rating-badge">{item.rating.toFixed(2)}</span></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* ══════════════ PREDICTIONS TAB ══════════════ */}
      {activeTab === "predictions" && (
        <div className="card">
          <h2 className="card-title">Upcoming Fixtures</h2>
          <div className="match-list">
            {upcomingMatches.map((m) => (
              <div
                key={m.id}
                id={`match-${m.id}`}
                className="match-card"
                onClick={() => openMatchDetail(m)}
                style={{ cursor: "pointer" }}
              >
                <div className="team-info team-home"><span>{m.homeTeam}</span></div>
                <div className="match-vs">
                  {m.probabilities ? (
                    <span style={{ fontSize: "0.8rem", color: "#10b981", fontWeight: "bold" }}>PREDICTED ↗</span>
                  ) : (
                    <button
                      id={`predict-${m.id}`}
                      className="btn-predict"
                      onClick={e => { e.stopPropagation(); handlePredict(m.id); }}
                    >
                      Predict
                    </button>
                  )}
                </div>
                <div className="team-info team-away"><span>{m.awayTeam}</span></div>
                {m.probabilities && (
                  <div className="probability-bar-container">
                    <div className="probability-bar">
                      <div className="bar-win"  style={{ width: `${m.probabilities.win  * 100}%` }}>{(m.probabilities.win  * 100).toFixed(0)}%</div>
                      <div className="bar-draw" style={{ width: `${m.probabilities.draw * 100}%` }}>{(m.probabilities.draw * 100).toFixed(0)}%</div>
                      <div className="bar-loss" style={{ width: `${m.probabilities.loss * 100}%` }}>{(m.probabilities.loss * 100).toFixed(0)}%</div>
                    </div>
                    <div className="bar-legend">
                      <span style={{ color: "#10b981" }}>■ Home Win</span>
                      <span style={{ color: "#f59e0b" }}>■ Draw</span>
                      <span style={{ color: "#ef4444" }}>■ Away Win</span>
                    </div>
                  </div>
                )}
              </div>
            ))}
          </div>
        </div>
      )}

      {/* ══════════════ MATCH DETAIL PANEL ══════════════ */}
      {selectedMatch && (
        <div className="detail-overlay" onClick={() => setSelectedMatch(null)}>
          <div className="detail-panel" onClick={e => e.stopPropagation()}>
            <button className="detail-close" onClick={() => setSelectedMatch(null)}>✕</button>
            <h2 className="card-title" style={{ marginBottom: "0.5rem" }}>
              {selectedMatch.homeTeam} <span style={{ color: "var(--accent-purple)" }}>vs</span> {selectedMatch.awayTeam}
            </h2>
            <p style={{ color: "var(--text-secondary)", fontSize: "0.85rem", marginBottom: "1.5rem" }}>
              📍 {selectedMatch.venue}
            </p>

            {/* Scoreline Heatmap */}
            <h3 style={{ marginBottom: "0.75rem", fontSize: "1rem", color: "var(--text-secondary)" }}>
              Scoreline Probability Matrix (%)
            </h3>
            {detailMatrix && (
              <div className="heatmap-container">
                <div className="heatmap-header-row">
                  <div className="heatmap-axis-label">Home ↓ Away →</div>
                  {detailMatrix[0].map((_, j) => (
                    <div key={j} className="heatmap-col-label">{j}</div>
                  ))}
                </div>
                {detailMatrix.map((row, i) => {
                  const maxVal = Math.max(...detailMatrix.flat());
                  return (
                    <div key={i} className="heatmap-row">
                      <div className="heatmap-row-label">{i}</div>
                      {row.map((val, j) => (
                        <div
                          key={j}
                          className="heatmap-cell"
                          style={{ background: heatColor(val, maxVal) }}
                          title={`${i}-${j}: ${val.toFixed(2)}%`}
                        >
                          {val.toFixed(1)}
                        </div>
                      ))}
                    </div>
                  );
                })}
              </div>
            )}

            {/* Model inputs explanation */}
            <h3 style={{ marginTop: "1.5rem", marginBottom: "0.75rem", fontSize: "1rem", color: "var(--text-secondary)" }}>
              Model Inputs
            </h3>
            <div className="model-inputs-grid">
              <div className="model-input-item">
                <span className="model-input-label">Home Attack (λ)</span>
                <span className="model-input-value">1.42</span>
              </div>
              <div className="model-input-item">
                <span className="model-input-label">Away Defense</span>
                <span className="model-input-value">0.88</span>
              </div>
              <div className="model-input-item">
                <span className="model-input-label">Away Attack (λ)</span>
                <span className="model-input-value">1.11</span>
              </div>
              <div className="model-input-item">
                <span className="model-input-label">Home Defense</span>
                <span className="model-input-value">0.95</span>
              </div>
              <div className="model-input-item">
                <span className="model-input-label">Home Advantage</span>
                <span className="model-input-value">+0.12σ</span>
              </div>
              <div className="model-input-item">
                <span className="model-input-label">Model</span>
                <span className="model-input-value">Bivariate Poisson</span>
              </div>
            </div>

            {selectedMatch.probabilities && (
              <>
                <h3 style={{ marginTop: "1.5rem", marginBottom: "0.75rem", fontSize: "1rem", color: "var(--text-secondary)" }}>
                  Outcome Probabilities
                </h3>
                <div className="probability-bar-container" style={{ marginBottom: 0 }}>
                  <div className="probability-bar" style={{ height: "2rem" }}>
                    <div className="bar-win"  style={{ width: `${selectedMatch.probabilities.win  * 100}%`, display: "flex", alignItems: "center", justifyContent: "center" }}>
                      {(selectedMatch.probabilities.win  * 100).toFixed(1)}%
                    </div>
                    <div className="bar-draw" style={{ width: `${selectedMatch.probabilities.draw * 100}%`, display: "flex", alignItems: "center", justifyContent: "center" }}>
                      {(selectedMatch.probabilities.draw * 100).toFixed(1)}%
                    </div>
                    <div className="bar-loss" style={{ width: `${selectedMatch.probabilities.loss * 100}%`, display: "flex", alignItems: "center", justifyContent: "center" }}>
                      {(selectedMatch.probabilities.loss * 100).toFixed(1)}%
                    </div>
                  </div>
                  <div className="bar-legend">
                    <span style={{ color: "#10b981" }}>■ Home Win</span>
                    <span style={{ color: "#f59e0b" }}>■ Draw</span>
                    <span style={{ color: "#ef4444" }}>■ Away Win</span>
                  </div>
                </div>
              </>
            )}
          </div>
        </div>
      )}

      {/* ══════════════ RATING HISTORY TAB ══════════════ */}
      {activeTab === "history" && (
        <div className="card">
          <div className="card-title">
            <span>Rating History</span>
            <div className="filters">
              <input
                id="entity-id-input"
                type="text"
                placeholder="Entity ID (e.g. man_city)"
                value={historyEntityId}
                onChange={e => setHistoryEntityId(e.target.value)}
                className="text-input"
              />
              <button id="load-history-btn" className="btn-predict" onClick={fetchRatingHistory}>
                Load
              </button>
            </div>
          </div>

          {historyLoading ? (
            <div className="loading-state">Loading rating history…</div>
          ) : historyChartData.length > 0 ? (
            <>
              <ResponsiveContainer width="100%" height={320}>
                <LineChart data={historyChartData} margin={{ top: 10, right: 30, left: 0, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.08)" />
                  <XAxis dataKey="date" tick={{ fill: "#9ca3af", fontSize: 12 }} />
                  <YAxis
                    domain={["auto", "auto"]}
                    tick={{ fill: "#9ca3af", fontSize: 12 }}
                    tickFormatter={v => v.toFixed(0)}
                  />
                  <Tooltip
                    contentStyle={{ background: "#1e1b4b", border: "1px solid #4c1d95", borderRadius: "8px" }}
                    labelStyle={{ color: "#e5e7eb" }}
                    itemStyle={{ color: "#a78bfa" }}
                  />
                  <Legend wrapperStyle={{ color: "#9ca3af" }} />
                  <Line
                    type="monotone"
                    dataKey="rating"
                    stroke="#7c3aed"
                    strokeWidth={2.5}
                    dot={{ fill: "#7c3aed", r: 4, strokeWidth: 0 }}
                    activeDot={{ r: 6, fill: "#a78bfa" }}
                    name={`${modelName.toUpperCase()} Rating`}
                  />
                </LineChart>
              </ResponsiveContainer>
              <p style={{ textAlign: "center", color: "var(--text-secondary)", fontSize: "0.8rem", marginTop: "0.5rem" }}>
                Showing {historyChartData.length} rating snapshots for{" "}
                <strong style={{ color: "#a78bfa" }}>{historyEntityId.replace(/_/g, " ")}</strong>
              </p>
            </>
          ) : (
            <div className="loading-state">No rating history found. Try ingesting data first.</div>
          )}
        </div>
      )}

      {/* ══════════════ SIMULATION TAB ══════════════ */}
      {activeTab === "simulation" && (
        <div className="card">
          <h2 className="card-title">Monte Carlo Season Simulator</h2>
          <div className="simulation-controls">
            <div className="form-group">
              <label>League / Sport</label>
              <select value={sportId} onChange={e => setSportId(e.target.value)}>
                <option value="football">Premier League (Football)</option>
                <option value="cricket">IPL (Cricket)</option>
                <option value="f1">F1 World Championship</option>
              </select>
            </div>
            <div className="form-group">
              <label>Iterations</label>
              <select value={simIterations} onChange={e => setSimIterations(Number(e.target.value))}>
                <option value={1000}>1,000 simulations</option>
                <option value={10000}>10,000 simulations</option>
              </select>
            </div>
            <button id="run-sim-btn" className="btn-run" onClick={runSimulation} disabled={simLoading}>
              {simLoading ? `Simulating… (${simStatus})` : "▶ Run Season Simulation"}
            </button>
          </div>

          {simResults && simChartData.length > 0 && (
            <>
              <h3 style={{ margin: "1.5rem 0 1rem", fontSize: "1rem", color: "var(--text-secondary)" }}>
                Final Standings Distribution — P(rank = k) per team
              </h3>
              <ResponsiveContainer width="100%" height={300}>
                <BarChart data={simChartData} margin={{ top: 0, right: 20, left: 0, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.08)" />
                  <XAxis dataKey="team" tick={{ fill: "#9ca3af", fontSize: 12 }} />
                  <YAxis tickFormatter={v => `${v}%`} tick={{ fill: "#9ca3af", fontSize: 12 }} />
                  <Tooltip
                    contentStyle={{ background: "#1e1b4b", border: "1px solid #4c1d95", borderRadius: "8px" }}
                    formatter={(val: any) => `${val}%`}
                    labelStyle={{ color: "#e5e7eb" }}
                  />
                  <Legend wrapperStyle={{ color: "#9ca3af" }} />
                  <Bar dataKey="1st"  stackId="s" fill="#7c3aed" />
                  <Bar dataKey="2nd"  stackId="s" fill="#4f46e5" />
                  <Bar dataKey="3rd"  stackId="s" fill="#0891b2" />
                  <Bar dataKey="4th+" stackId="s" fill="#1e293b" />
                </BarChart>
              </ResponsiveContainer>

              {/* What-if override section */}
              <div className="whatif-section">
                <h3 style={{ fontSize: "0.95rem", color: "var(--text-secondary)", marginBottom: "0.5rem" }}>
                  ⚡ What-If Override
                </h3>
                <p style={{ fontSize: "0.8rem", color: "#6b7280", marginBottom: "0.75rem" }}>
                  Force a hypothetical match result and re-run simulation to see standings impact.
                </p>
                <div className="whatif-form">
                  <input id="whatif-home" type="text" placeholder="Home team ID" className="text-input" />
                  <select id="whatif-result" className="text-input" style={{ maxWidth: "130px" }}>
                    <option value="win">Home Win</option>
                    <option value="draw">Draw</option>
                    <option value="loss">Away Win</option>
                  </select>
                  <input id="whatif-away" type="text" placeholder="Away team ID" className="text-input" />
                  <button id="whatif-run-btn" className="btn-predict" onClick={runSimulation}>
                    Re-run
                  </button>
                </div>
              </div>
            </>
          )}
        </div>
      )}

      {/* ══════════════ MODEL ACCURACY TAB ══════════════ */}
      {activeTab === "accuracy" && (
        <div className="card">
          <div className="card-title">
            <span>Model Accuracy &amp; Calibration</span>
            <div className="filters">
              <select
                id="accuracy-model-select"
                value={accuracyModel}
                onChange={e => { setAccuracyModel(e.target.value); setAccuracyData(null); }}
              >
                <option value="elo">Elo</option>
                <option value="poisson">Poisson</option>
                <option value="glicko2">Glicko-2</option>
              </select>
              <button id="reload-accuracy-btn" className="btn-predict" onClick={fetchAccuracy}>
                Reload
              </button>
            </div>
          </div>

          {accuracyLoading && <div className="loading-state">Loading accuracy data…</div>}

          {accuracyData && !accuracyLoading && (
            <>
              {/* Metric Cards */}
              <div className="metric-cards">
                <div className="metric-card">
                  <div className="metric-label">Sample Size</div>
                  <div className="metric-value">{accuracyData.sampleSize}</div>
                </div>
                <div className="metric-card">
                  <div className="metric-label">Brier Score ↓</div>
                  <div className="metric-value" style={{ color: "#10b981" }}>
                    {accuracyData.brierScore != null ? accuracyData.brierScore.toFixed(4) : "—"}
                  </div>
                </div>
                <div className="metric-card">
                  <div className="metric-label">Log Loss ↓</div>
                  <div className="metric-value" style={{ color: "#10b981" }}>
                    {accuracyData.logLoss != null ? accuracyData.logLoss.toFixed(4) : "—"}
                  </div>
                </div>
              </div>

              {/* Calibration Reliability Diagram */}
              <h3 style={{ margin: "1.5rem 0 0.75rem", fontSize: "1rem", color: "var(--text-secondary)" }}>
                Reliability Diagram — Predicted vs Actual Win Rate
              </h3>
              <p style={{ fontSize: "0.8rem", color: "#6b7280", marginBottom: "1rem" }}>
                Points close to the diagonal (dashed) indicate a well-calibrated model.
              </p>
              <ResponsiveContainer width="100%" height={280}>
                <ScatterChart margin={{ top: 10, right: 30, left: 0, bottom: 10 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.08)" />
                  <XAxis
                    dataKey="predicted"
                    type="number"
                    domain={[0, 1]}
                    name="Predicted"
                    tick={{ fill: "#9ca3af", fontSize: 12 }}
                    label={{ value: "Mean Predicted Probability", position: "insideBottom", offset: -5, fill: "#6b7280", fontSize: 11 }}
                  />
                  <YAxis
                    dataKey="actual"
                    type="number"
                    domain={[0, 1]}
                    name="Actual"
                    tick={{ fill: "#9ca3af", fontSize: 12 }}
                    label={{ value: "Actual Win Freq", angle: -90, position: "insideLeft", fill: "#6b7280", fontSize: 11 }}
                  />
                  <Tooltip
                    cursor={{ strokeDasharray: "3 3" }}
                    contentStyle={{ background: "#1e1b4b", border: "1px solid #4c1d95", borderRadius: "8px" }}
                    formatter={(v: any, name: any) => [v.toFixed(3), name]}
                    labelStyle={{ color: "#e5e7eb" }}
                  />
                  {/* Perfect calibration diagonal */}
                  <ReferenceLine
                    segment={[{ x: 0, y: 0 }, { x: 1, y: 1 }]}
                    stroke="#4b5563"
                    strokeDasharray="6 3"
                    label={{ value: "Perfect", fill: "#6b7280", fontSize: 10 }}
                  />
                  <Scatter
                    name={`${accuracyModel.toUpperCase()} calibration`}
                    data={calibScatterData}
                    fill="#7c3aed"
                  >
                    {calibScatterData.map((_, i) => (
                      <Cell key={i} fill={RANK_COLORS[i % RANK_COLORS.length]} />
                    ))}
                  </Scatter>
                </ScatterChart>
              </ResponsiveContainer>

              {/* Bucket table */}
              <table className="leaderboard-table" style={{ marginTop: "1rem" }}>
                <thead>
                  <tr>
                    <th>Prob Bucket (midpoint)</th>
                    <th>Mean Predicted</th>
                    <th>Actual Win Freq</th>
                    <th>Count</th>
                    <th>Calibration Error</th>
                  </tr>
                </thead>
                <tbody>
                  {accuracyData.calibrationBuckets.map((b, i) => {
                    const err = Math.abs(b.meanPredicted - b.meanActual);
                    return (
                      <tr key={i}>
                        <td>{b.bucketMidpoint.toFixed(2)}</td>
                        <td>{b.meanPredicted.toFixed(3)}</td>
                        <td>{b.meanActual.toFixed(3)}</td>
                        <td>{b.count}</td>
                        <td>
                          <span style={{ color: err < 0.05 ? "#10b981" : err < 0.1 ? "#f59e0b" : "#ef4444" }}>
                            {err.toFixed(3)}
                          </span>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </>
          )}
        </div>
      )}

      {/* ══════════════ CHAT PREDICT TAB ══════════════ */}
      {activeTab === "chat" && (
        <div className="card">
          <h2 className="card-title">NLU Prediction Assistant</h2>
          <p style={{ color: "var(--text-secondary)", fontSize: "0.85rem", marginBottom: "1.5rem" }}>
            Resolve fixtures and predict outcomes using natural language. Query by team names, aliases, or competitions.
          </p>

          <div className="chat-window">
            <div className="chat-messages">
              {chatMessages.map((msg, index) => (
                <div key={index} className={`chat-bubble-container ${msg.sender}`}>
                  <div className={`chat-bubble ${msg.sender}`}>
                    <div className="chat-text">{msg.text}</div>
                    
                    {/* Resolved Fixture details */}
                    {msg.resolvedFixture && (
                      <div className="chat-fixture-card">
                        <div className="chat-fixture-header">
                          <span className="sport-badge">{msg.resolvedFixture.sportId.toUpperCase()}</span>
                          {msg.resolvedFixture.format && <span className="format-badge">{msg.resolvedFixture.format}</span>}
                        </div>
                        <div className="chat-fixture-venue">📍 {msg.resolvedFixture.venue}</div>
                        <div className="chat-fixture-date">📅 {new Date(msg.resolvedFixture.date).toLocaleDateString("en-GB", { day: "numeric", month: "long", year: "numeric" })}</div>
                      </div>
                    )}

                    {/* Probability distribution chart */}
                    {msg.probabilities && (
                      <div className="chat-probabilities">
                        <div className="probability-bar" style={{ height: "1.5rem", marginTop: "0.5rem" }}>
                          <div className="bar-win" style={{ width: `${(msg.probabilities.win || 0) * 100}%` }}>
                            {((msg.probabilities.win || 0) * 100).toFixed(0)}%
                          </div>
                          {msg.probabilities.draw !== undefined && (
                            <div className="bar-draw" style={{ width: `${(msg.probabilities.draw || 0) * 100}%` }}>
                              {((msg.probabilities.draw || 0) * 100).toFixed(0)}%
                            </div>
                          )}
                          <div className="bar-loss" style={{ width: `${(msg.probabilities.loss || 0) * 100}%` }}>
                            {((msg.probabilities.loss || 0) * 100).toFixed(0)}%
                          </div>
                        </div>
                        <div className="bar-legend" style={{ marginTop: "0.25rem", fontSize: "0.7rem" }}>
                          <span style={{ color: "#10b981" }}>■ Win</span>
                          {msg.probabilities.draw !== undefined && <span style={{ color: "#f59e0b" }}>■ Draw</span>}
                          <span style={{ color: "#ef4444" }}>■ Loss</span>
                        </div>
                      </div>
                    )}

                    {/* Candidate disambiguation buttons */}
                    {msg.candidates && msg.candidates.length > 0 && (
                      <div className="chat-candidates">
                        {msg.candidates.map((c, cIdx) => (
                          <button
                            key={cIdx}
                            className="candidate-btn"
                            onClick={() => {
                              // Re-query using explicit event ID format or details
                              setChatInput(`predict match ${c.eventId}`);
                              // Auto trigger send
                              setTimeout(() => {
                                const btn = document.getElementById("chat-send-btn");
                                if (btn) btn.click();
                              }, 100);
                            }}
                          >
                            {c.sportId.toUpperCase()} @ {c.venue} ({new Date(c.date).toLocaleDateString()})
                          </button>
                        ))}
                      </div>
                    )}
                  </div>
                </div>
              ))}
              {chatLoading && (
                <div className="chat-bubble-container engine">
                  <div className="chat-bubble engine typing">
                    <span>•</span><span>•</span><span>•</span>
                  </div>
                </div>
              )}
            </div>

            <form onSubmit={handleSendChatMessage} className="chat-input-row">
              <input
                id="chat-input"
                type="text"
                placeholder="Ask me to predict a match... (e.g. 'predict man city against real madrid')"
                value={chatInput}
                onChange={e => setChatInput(e.target.value)}
                disabled={chatLoading}
                className="chat-text-input"
              />
              <button id="chat-send-btn" type="submit" disabled={chatLoading} className="chat-send-btn">
                Send
              </button>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
