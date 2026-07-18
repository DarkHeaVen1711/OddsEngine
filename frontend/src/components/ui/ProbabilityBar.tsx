import React from 'react';

interface ProbabilityBarProps {
  win: number;
  draw: number;
  loss: number;
  showLabels?: boolean;
}

export function ProbabilityBar({ win, draw, loss, showLabels = true }: ProbabilityBarProps) {
  const wPct = (win * 100).toFixed(1);
  const dPct = (draw * 100).toFixed(1);
  const lPct = (loss * 100).toFixed(1);

  return (
    <div className="w-full">
      <div className="flex w-full h-6 overflow-hidden rounded-full font-bold text-xs text-white shadow-inner bg-gray-800">
        <div
          style={{ width: `${wPct}%` }}
          className="bg-emerald-500 flex items-center justify-center transition-all duration-500"
        >
          {win > 0.1 && `${wPct}%`}
        </div>
        <div
          style={{ width: `${dPct}%` }}
          className="bg-amber-500 flex items-center justify-center transition-all duration-500"
        >
          {draw > 0.1 && `${dPct}%`}
        </div>
        <div
          style={{ width: `${lPct}%` }}
          className="bg-rose-500 flex items-center justify-center transition-all duration-500"
        >
          {loss > 0.1 && `${lPct}%`}
        </div>
      </div>
      {showLabels && (
        <div className="flex gap-4 mt-2 text-xs text-gray-400 font-medium">
          <span className="flex items-center gap-1"><span className="w-2 h-2 rounded-full bg-emerald-500"></span> Home Win</span>
          <span className="flex items-center gap-1"><span className="w-2 h-2 rounded-full bg-amber-500"></span> Draw</span>
          <span className="flex items-center gap-1"><span className="w-2 h-2 rounded-full bg-rose-500"></span> Away Win</span>
        </div>
      )}
    </div>
  );
}
