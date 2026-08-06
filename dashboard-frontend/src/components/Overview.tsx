import { useEffect, useState } from 'react';
import { mt5Api } from '../services/api';

export default function Overview() {
  const [snapshot, setSnapshot] = useState<any>(null);
  const [positions, setPositions] = useState<any[]>([]);
  const [timeFilter, setTimeFilter] = useState('Day');

  const refresh = async () => {
    try {
      const snapRes: any = await mt5Api.getSnapshot();
      const autoSnap: any = await mt5Api.getAutoSnapshot().catch(() => ({}));
      setSnapshot({ ...(snapRes?.snapshot || snapRes), ...autoSnap });

      const posRes: any = await mt5Api.getPositions();
      setPositions(Array.isArray(posRes) ? posRes : posRes.data || []);
    } catch (e) {
      console.error(e);
    }
  };

  useEffect(() => {
    refresh();
    const interval = setInterval(refresh, 5000);
    return () => clearInterval(interval);
  }, []);

  const acc = snapshot?.account || {};
  const run = snapshot?.runtime || {};

  return (
    <div className="flex flex-col gap-6 h-full">
      {/* Top Stats Row */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <div className="bg-[#1a1d2d] p-6 rounded-2xl border border-slate-800 shadow-lg">
          <div className="text-slate-400 text-sm mb-1">Balance</div>
          <div className="text-3xl font-bold text-slate-100">${parseFloat(acc.balance || 0).toFixed(2)}</div>
        </div>
        <div className="bg-[#1a1d2d] p-6 rounded-2xl border border-slate-800 shadow-lg">
          <div className="text-slate-400 text-sm mb-1">Equity</div>
          <div className="text-3xl font-bold text-slate-100">${parseFloat(acc.equity || 0).toFixed(2)}</div>
        </div>
        <div className="bg-[#1a1d2d] p-6 rounded-2xl border border-slate-800 shadow-lg">
          <div className="text-slate-400 text-sm mb-1">Free Margin</div>
          <div className="text-3xl font-bold text-slate-100">${parseFloat(acc.margin_free || 0).toFixed(2)}</div>
        </div>
        <div className={`p-6 rounded-2xl border shadow-lg ${acc.profit > 0 ? 'bg-emerald-500/10 border-emerald-500/30' : acc.profit < 0 ? 'bg-red-500/10 border-red-500/30' : 'bg-[#1a1d2d] border-slate-800'}`}>
          <div className={`text-sm mb-1 ${acc.profit > 0 ? 'text-emerald-400/80' : acc.profit < 0 ? 'text-red-400/80' : 'text-slate-400'}`}>Total PnL</div>
          <div className={`text-3xl font-bold ${acc.profit > 0 ? 'text-emerald-400' : acc.profit < 0 ? 'text-red-400' : 'text-slate-100'}`}>
            {acc.profit > 0 ? '+' : ''}${parseFloat(acc.profit || 0).toFixed(2)}
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 xl:grid-cols-3 gap-6 flex-1 min-h-0">
        {/* Chart Area */}
        <div className="xl:col-span-2 bg-[#1a1d2d] rounded-2xl border border-slate-800 flex flex-col min-h-[400px]">
          <div className="p-6 border-b border-slate-800 flex justify-between items-center shrink-0">
            <div>
              <h2 className="text-lg font-semibold">Portfolio Growth</h2>
              <p className="text-sm text-slate-400">Equity history and AI performance</p>
            </div>
            <div className="flex bg-slate-900 rounded-lg p-1 border border-slate-700">
              {['Day', 'Week', 'Month'].map(t => (
                <button 
                  key={t}
                  onClick={() => setTimeFilter(t)}
                  className={`px-4 py-1.5 text-sm rounded-md transition-colors ${timeFilter === t ? 'bg-blue-600 text-white' : 'text-slate-400 hover:text-slate-200'}`}
                >
                  {t}
                </button>
              ))}
            </div>
          </div>
          <div className="flex-1 p-4 flex flex-col items-center justify-center text-slate-500">
            <div className="w-full flex items-end justify-between h-48 border-b border-slate-800/50 px-4">
              {Array.from({ length: 20 }).map((_, i) => (
                <div key={i} className="w-3 bg-blue-500/20 rounded-t-sm" style={{ height: `${Math.random() * 80 + 10}%` }}>
                  <div className="w-full bg-blue-500 rounded-t-sm" style={{ height: '4px' }}></div>
                </div>
              ))}
            </div>
            <p className="mt-4 text-xs font-mono">Portfolio Chart Placeholder ({timeFilter})</p>
          </div>
        </div>

        {/* Trade History / Active Positions */}
        <div className="bg-[#1a1d2d] rounded-2xl border border-slate-800 flex flex-col min-h-[400px]">
          <div className="p-6 border-b border-slate-800 shrink-0 flex justify-between items-center">
            <h2 className="text-lg font-semibold">Active Positions</h2>
            <span className={`px-2 py-1 rounded text-xs font-bold ${run.state === 'RUNNING' ? 'bg-emerald-500/20 text-emerald-400' : 'bg-red-500/20 text-red-400'}`}>
              {run.state || 'IDLE'}
            </span>
          </div>
          <div className="flex-1 overflow-y-auto p-4 space-y-3">
            {positions.length === 0 ? (
              <div className="text-center text-slate-500 mt-10">No open positions</div>
            ) : (
              positions.map((p: any, i: number) => (
                <div key={i} className="p-3 bg-slate-800/40 border border-slate-700/50 rounded-xl flex justify-between items-center hover:bg-slate-800/60 transition-colors">
                  <div>
                    <div className="font-bold flex items-center gap-2">
                      {p.symbol}
                      <span className={`text-[10px] px-2 py-0.5 rounded-full ${p.side === 'BUY' ? 'bg-emerald-500/20 text-emerald-400' : 'bg-red-500/20 text-red-400'}`}>{p.side}</span>
                    </div>
                    <div className="text-xs text-slate-400 mt-1">Vol: {p.volume} | Open: {p.open_price}</div>
                  </div>
                  <div className="text-right">
                    <div className={`font-bold ${p.profit > 0 ? 'text-emerald-400' : p.profit < 0 ? 'text-red-400' : 'text-slate-400'}`}>
                      {p.profit > 0 ? '+' : ''}{p.profit}
                    </div>
                    <button onClick={() => mt5Api.closePosition(p.ticket, p.symbol)} className="text-[10px] uppercase font-bold tracking-wider bg-slate-900 border border-red-500/30 text-red-400 hover:bg-red-500/20 px-2 py-1 rounded mt-1 transition-colors">
                      Close
                    </button>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
