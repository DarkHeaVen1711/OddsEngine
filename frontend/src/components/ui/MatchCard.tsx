import React from 'react';
import { ProbabilityBar } from './ProbabilityBar';

export interface MatchData {
  id: string;
  sportId: string;
  venue: string;
  homeTeam: string;
  awayTeam: string;
  status: string;
  probabilities?: { win: number; draw: number; loss: number };
}

interface MatchCardProps {
  match: MatchData;
  onClick?: (match: MatchData) => void;
  onPredict?: (id: string) => void;
}

export function MatchCard({ match, onClick, onPredict }: MatchCardProps) {
  return (
    <div
      onClick={() => onClick && onClick(match)}
      className="bg-[#1b2536]/80 backdrop-blur-md border border-[#243048] rounded-xl p-5 hover:border-purple-500/50 hover:shadow-[0_0_15px_rgba(139,92,246,0.15)] transition-all cursor-pointer group"
    >
      <div className="grid grid-cols-3 items-center gap-4">
        <div className="text-right font-semibold text-lg">{match.homeTeam.replace(/_/g, " ")}</div>
        
        <div className="text-center font-bold text-gray-500 text-sm">
          {match.probabilities ? (
            <span className="text-emerald-400 group-hover:text-emerald-300 transition-colors flex items-center justify-center gap-1">
              PREDICTED <span className="text-[10px]">↗</span>
            </span>
          ) : (
            <button
              onClick={(e) => {
                e.stopPropagation();
                onPredict && onPredict(match.id);
              }}
              className="bg-purple-600 hover:bg-purple-500 text-white px-3 py-1.5 rounded-lg transition-colors shadow-lg shadow-purple-500/20"
            >
              Predict
            </button>
          )}
        </div>
        
        <div className="text-left font-semibold text-lg">{match.awayTeam.replace(/_/g, " ")}</div>
      </div>
      
      {match.probabilities && (
        <div className="mt-4 pt-4 border-t border-[#243048]/50">
          <ProbabilityBar
            win={match.probabilities.win}
            draw={match.probabilities.draw}
            loss={match.probabilities.loss}
          />
        </div>
      )}
    </div>
  );
}
