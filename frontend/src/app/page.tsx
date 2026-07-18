"use client";

import React, { useState, useEffect, useCallback, useRef } from "react";
import {
  LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip as RechartsTooltip, Legend, ResponsiveContainer,
  BarChart, Bar
} from "recharts";
import { ProbabilityBar } from "../components/ui/ProbabilityBar";
import { MatchCard, MatchData } from "../components/ui/MatchCard";

const API = "http://localhost:8080";

// ─── Type Definitions ────────────────────────────────────────────────────────
interface LeaderboardEntry {
  entityId: string;
  sportId: string;
  modelName: string;
  rating: number;
  asOfTimestamp: number;
}

interface ChatMessage {
  id: string;
  sender: "user" | "engine";
  text: string;
  isTyping?: boolean;
  matchData?: MatchData;
}

// ─── Mock Data ───────────────────────────────────────────────────────────
const MOCK_LEADERBOARD: LeaderboardEntry[] = [
  { entityId: "man_city", sportId: "football", modelName: "elo", rating: 1684.52, asOfTimestamp: Date.now() },
  { entityId: "real_madrid", sportId: "football", modelName: "elo", rating: 1655.10, asOfTimestamp: Date.now() },
  { entityId: "arsenal", sportId: "football", modelName: "elo", rating: 1621.34, asOfTimestamp: Date.now() },
  { entityId: "liverpool", sportId: "football", modelName: "elo", rating: 1604.22, asOfTimestamp: Date.now() },
];

const MOCK_MATCHES: MatchData[] = [
  { id: "sf_1", sportId: "football", venue: "Allianz Arena", homeTeam: "Spain", awayTeam: "France", status: "scheduled" },
  { id: "sf_2", sportId: "football", venue: "Wembley", homeTeam: "England", awayTeam: "Argentina", status: "scheduled" },
];

const MOCK_PREDICTIONS: Record<string, { win: number; draw: number; loss: number }> = {
  "sf_1": { win: 0.6516, draw: 0.2284, loss: 0.1200 },
  "sf_2": { win: 0.4215, draw: 0.2810, loss: 0.2975 },
};

// ─── Main Dashboard ───────────────────────────────────────────────────────────

export default function Dashboard() {
  const [activeTab, setActiveTab] = useState("dashboard");
  const [sportId, setSportId] = useState("football");
  const [modelName, setModelName] = useState("poisson");

  // Dashboard State
  const [leaderboard, setLeaderboard] = useState<LeaderboardEntry[]>(MOCK_LEADERBOARD);
  const [upcomingMatches, setUpcomingMatches] = useState<MatchData[]>(MOCK_MATCHES);

  // Chat State
  const [chatInput, setChatInput] = useState("");
  const [chatMessages, setChatMessages] = useState<ChatMessage[]>([
    { id: "1", sender: "engine", text: "Welcome to OddsEngine. Ask me to predict matches using natural language, for example: 'predict Spain vs France'." }
  ]);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [chatMessages]);

  const handlePredict = async (matchId: string) => {
    setUpcomingMatches(prev => prev.map(m => 
      m.id === matchId ? { ...m, probabilities: MOCK_PREDICTIONS[matchId] || { win: 0.5, draw: 0.2, loss: 0.3 } } : m
    ));
  };

  const handleSendChatMessage = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    if (!chatInput.trim()) return;

    const userText = chatInput;
    setChatInput("");
    setChatMessages(prev => [...prev, { id: Date.now().toString(), sender: "user", text: userText }]);
    
    // Typing indicator
    const typingId = "typing-" + Date.now();
    setChatMessages(prev => [...prev, { id: typingId, sender: "engine", text: "", isTyping: true }]);

    setTimeout(() => {
      setChatMessages(prev => prev.filter(m => m.id !== typingId));

      // Mock NLP resolution
      if (userText.toLowerCase().includes("spain") && userText.toLowerCase().includes("france")) {
        const mData: MatchData = {
          id: "chat_sf_1", sportId: "football", venue: "Allianz Arena", homeTeam: "Spain", awayTeam: "France", status: "scheduled",
          probabilities: MOCK_PREDICTIONS["sf_1"]
        };
        setChatMessages(prev => [...prev, {
          id: Date.now().toString(),
          sender: "engine",
          text: "I've resolved your query to **Spain vs France**. Here is the prediction using the Tier 1 Bivariate Poisson model:",
          matchData: mData
        }]);
      } else {
        setChatMessages(prev => [...prev, {
          id: Date.now().toString(),
          sender: "engine",
          text: "I couldn't resolve a specific fixture for that query. Try asking about a scheduled match like 'England vs Argentina'."
        }]);
      }
    }, 1000);
  };

  return (
    <div className="flex h-screen bg-[#0b0f19] text-gray-100 overflow-hidden font-sans">
      
      {/* ── Sidebar Navigation ── */}
      <aside className="w-64 flex-shrink-0 bg-[#131a26]/80 backdrop-blur-xl border-r border-[#243048] flex flex-col">
        <div className="p-6">
          <h1 className="text-2xl font-black tracking-tight bg-clip-text text-transparent bg-gradient-to-br from-purple-400 to-blue-500">
            OddsEngine
          </h1>
          <p className="text-xs text-gray-500 font-medium tracking-wide mt-1 uppercase">Predictive Intelligence</p>
        </div>

        <nav className="flex-1 px-4 space-y-2 mt-4">
          <button 
            onClick={() => setActiveTab('chat')}
            className={`w-full text-left px-4 py-3 rounded-xl transition-all duration-200 font-medium ${
              activeTab === 'chat' 
                ? 'bg-purple-600/10 text-purple-400 shadow-[inset_0_1px_0_0_rgba(139,92,246,0.1)]' 
                : 'text-gray-400 hover:bg-[#1b2536] hover:text-gray-200'
            }`}
          >
            <div className="flex items-center gap-3">
              <span className="text-lg">✨</span>
              <span>Assistant</span>
            </div>
          </button>
          
          <button 
            onClick={() => setActiveTab('dashboard')}
            className={`w-full text-left px-4 py-3 rounded-xl transition-all duration-200 font-medium ${
              activeTab === 'dashboard' 
                ? 'bg-purple-600/10 text-purple-400 shadow-[inset_0_1px_0_0_rgba(139,92,246,0.1)]' 
                : 'text-gray-400 hover:bg-[#1b2536] hover:text-gray-200'
            }`}
          >
            <div className="flex items-center gap-3">
              <span className="text-lg">📊</span>
              <span>Dashboard</span>
            </div>
          </button>
        </nav>
      </aside>

      {/* ── Main Content Area ── */}
      <main className="flex-1 flex flex-col overflow-hidden relative">
        {/* Background glow effects */}
        <div className="absolute top-[-20%] left-[-10%] w-[50%] h-[50%] bg-purple-600/10 blur-[120px] rounded-full pointer-events-none"></div>
        <div className="absolute bottom-[-20%] right-[-10%] w-[40%] h-[40%] bg-blue-600/10 blur-[100px] rounded-full pointer-events-none"></div>

        {/* Global Filters Header */}
        <header className="h-20 border-b border-[#243048]/50 flex items-center justify-end px-8 z-10 backdrop-blur-sm">
          <div className="flex gap-4">
            <select 
              value={sportId} onChange={e => setSportId(e.target.value)}
              className="bg-[#1b2536] border border-[#243048] text-sm text-gray-200 px-4 py-2 rounded-lg outline-none focus:border-purple-500 transition-colors shadow-sm"
            >
              <option value="football">Football</option>
              <option value="cricket">Cricket</option>
              <option value="f1">Formula 1</option>
            </select>
            <select 
              value={modelName} onChange={e => setModelName(e.target.value)}
              className="bg-[#1b2536] border border-[#243048] text-sm text-gray-200 px-4 py-2 rounded-lg outline-none focus:border-purple-500 transition-colors shadow-sm"
            >
              <option value="elo">Elo Rating</option>
              <option value="poisson">Tier 1: Bivariate Poisson</option>
              <option value="mc">Tier 2: Monte Carlo</option>
            </select>
          </div>
        </header>

        {/* Dynamic Views */}
        <div className="flex-1 overflow-y-auto p-8 z-10">
          
          {/* ASSISTANT VIEW */}
          {activeTab === 'chat' && (
            <div className="max-w-4xl mx-auto flex flex-col h-full bg-[#131a26]/40 backdrop-blur-md rounded-2xl border border-[#243048] shadow-2xl">
              <div className="flex-1 overflow-y-auto p-6 space-y-6">
                {chatMessages.map((msg) => (
                  <div key={msg.id} className={`flex ${msg.sender === 'user' ? 'justify-end' : 'justify-start'}`}>
                    <div className={`max-w-[85%] rounded-2xl p-5 ${
                      msg.sender === 'user' 
                        ? 'bg-gradient-to-br from-purple-600 to-purple-800 text-white shadow-lg shadow-purple-900/20 rounded-br-sm' 
                        : 'bg-[#1b2536] border border-[#243048] text-gray-200 rounded-bl-sm'
                    }`}>
                      {msg.isTyping ? (
                        <div className="flex gap-1.5 items-center h-6">
                          <div className="w-2 h-2 rounded-full bg-gray-500 animate-bounce" style={{ animationDelay: "0ms" }}></div>
                          <div className="w-2 h-2 rounded-full bg-gray-500 animate-bounce" style={{ animationDelay: "150ms" }}></div>
                          <div className="w-2 h-2 rounded-full bg-gray-500 animate-bounce" style={{ animationDelay: "300ms" }}></div>
                        </div>
                      ) : (
                        <div className="text-sm leading-relaxed">{msg.text}</div>
                      )}

                      {/* Inline Rich Component Rendering */}
                      {msg.matchData && (
                        <div className="mt-4 p-1">
                          <MatchCard match={msg.matchData} />
                        </div>
                      )}
                    </div>
                  </div>
                ))}
                <div ref={messagesEndRef} />
              </div>
              
              <div className="p-4 bg-[#1b2536]/80 backdrop-blur-lg border-t border-[#243048] rounded-b-2xl">
                <form onSubmit={handleSendChatMessage} className="flex gap-3 relative">
                  <input
                    type="text"
                    value={chatInput}
                    onChange={(e) => setChatInput(e.target.value)}
                    placeholder="Ask OddsEngine a question..."
                    className="flex-1 bg-[#0b0f19] border border-[#243048] text-gray-100 placeholder-gray-500 px-6 py-4 rounded-xl outline-none focus:border-purple-500 transition-colors shadow-inner text-sm"
                  />
                  <button 
                    type="submit"
                    disabled={!chatInput.trim()}
                    className="bg-purple-600 hover:bg-purple-500 disabled:opacity-50 disabled:hover:bg-purple-600 text-white px-8 py-4 rounded-xl font-semibold transition-all shadow-[0_0_20px_rgba(139,92,246,0.3)] disabled:shadow-none"
                  >
                    Send
                  </button>
                </form>
              </div>
            </div>
          )}

          {/* DASHBOARD VIEW */}
          {activeTab === 'dashboard' && (
            <div className="max-w-6xl mx-auto space-y-8 pb-12">
              <h2 className="text-3xl font-bold tracking-tight text-white mb-2">Live Analytics</h2>
              
              <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
                
                {/* Upcoming Fixtures */}
                <div className="space-y-4">
                  <div className="flex items-center justify-between">
                    <h3 className="text-lg font-semibold text-gray-300">Upcoming Fixtures</h3>
                    <span className="text-xs font-bold px-2 py-1 bg-blue-500/10 text-blue-400 rounded-md border border-blue-500/20">Tier 1</span>
                  </div>
                  <div className="space-y-4">
                    {upcomingMatches.map(m => (
                      <MatchCard key={m.id} match={m} onPredict={handlePredict} />
                    ))}
                  </div>
                </div>

                {/* Leaderboard */}
                <div className="bg-[#131a26]/60 backdrop-blur-md border border-[#243048] rounded-2xl p-6 shadow-xl">
                  <div className="flex items-center justify-between mb-6">
                    <h3 className="text-lg font-semibold text-gray-300">Entity Leaderboard</h3>
                    <span className="text-xs font-bold px-2 py-1 bg-purple-500/10 text-purple-400 rounded-md border border-purple-500/20">{modelName.toUpperCase()}</span>
                  </div>
                  
                  <div className="space-y-3">
                    {leaderboard.map((item, idx) => (
                      <div key={item.entityId} className="flex items-center justify-between p-3 rounded-xl hover:bg-[#1b2536] transition-colors group">
                        <div className="flex items-center gap-4">
                          <span className="text-gray-500 font-bold w-4">{idx + 1}</span>
                          <span className="font-semibold text-gray-200 group-hover:text-white transition-colors capitalize">{item.entityId.replace(/_/g, " ")}</span>
                        </div>
                        <span className="font-bold text-transparent bg-clip-text bg-gradient-to-r from-purple-400 to-blue-400">
                          {item.rating.toFixed(1)}
                        </span>
                      </div>
                    ))}
                  </div>
                </div>

              </div>
            </div>
          )}
          
        </div>
      </main>
    </div>
  );
}
