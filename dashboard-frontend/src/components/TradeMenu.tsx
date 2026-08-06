import { useState, useEffect } from 'react';
import { mt5Api } from '../services/api';

const GATE_TOGGLES = [
  { id: 'marketTradabilityGate', label: 'Market Tradability' },
  { id: 'preAiPositionCapGate', label: 'Pre-AI Position Cap' },
  { id: 'unifiedZoneGate', label: 'Unified Zone' },
  { id: 'eaFallbackConfidenceGate', label: 'EA Confidence' },
  { id: 'eaFallbackRrrGate', label: 'EA RRR' },
  { id: 'decisionSideGuard', label: 'Decision Side' },
  { id: 'slDistanceGuard', label: 'SL Distance' },
  { id: 'counterTrendGuard', label: 'Counter Trend' },
  { id: 'ltfConsensusGuard', label: 'LTF Consensus' },
  { id: 'proximityGate', label: 'Proximity' },
  { id: 'm15SmcPressureGate', label: 'M15 SMC Pressure' }
];

const STRATEGY_TOGGLES = [
  'SCALPING', 'TREND_FOLLOW', 'MEAN_REVERSION', 'BREAKOUT', 'SMC_FVG_SCALP', 'SMC_FVG_REVERSAL'
];

export default function TradeMenu() {
  const [activeTab, setActiveTab] = useState<'manual' | 'auto'>('manual');
  const [symbols, setSymbols] = useState<any[]>([]);
  const [watchList, setWatchList] = useState<string[]>([]);
  const [search, setSearch] = useState('');
  
  // Manual Order
  const [orderSymbol, setOrderSymbol] = useState('');
  const [orderVolume, setOrderVolume] = useState('0.01');
  const [orderType, setOrderType] = useState('buy');
  
  // Auto Config
  const [config, setConfig] = useState<any>({});

  useEffect(() => {
    fetchData();
  }, []);

  const fetchData = async () => {
    try {
      const symRes: any = await mt5Api.getSymbols();
      if (Array.isArray(symRes)) setSymbols(symRes);
      
      const snap: any = await mt5Api.getAutoSnapshot();
      if (snap?.config) setConfig(snap.config);
    } catch (err) {
      console.error(err);
    }
  };

  const saveConfig = async () => {
    try {
      await mt5Api.updateConfig(config);
      alert('Configuration saved successfully');
    } catch (e) {
      alert('Failed to save configuration');
    }
  };

  const handlePlaceOrder = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await mt5Api.placeOrder({ symbol: orderSymbol, volume: parseFloat(orderVolume), type: orderType });
      alert('Order placed successfully');
    } catch (err) {
      alert('Order failed');
    }
  };

  const filteredSymbols = symbols.filter(s => 
    s?.name?.toLowerCase().includes(search.toLowerCase()) || 
    (typeof s === 'string' && s.toLowerCase().includes(search.toLowerCase()))
  );

  return (
    <div className="flex flex-col h-full gap-6">
      <div className="flex gap-4 border-b border-slate-800 pb-2 shrink-0">
        <button 
          onClick={() => setActiveTab('manual')}
          className={`px-6 py-2 rounded-lg font-medium transition-colors ${activeTab === 'manual' ? 'bg-blue-600 text-white' : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800'}`}
        >
          Manual Trade
        </button>
        <button 
          onClick={() => setActiveTab('auto')}
          className={`px-6 py-2 rounded-lg font-medium transition-colors ${activeTab === 'auto' ? 'bg-blue-600 text-white' : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800'}`}
        >
          Auto Trade Config
        </button>
      </div>

      {activeTab === 'manual' && (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 flex-1 min-h-0">
          <div className="col-span-1 lg:col-span-2 bg-[#1a1d2d] rounded-xl border border-slate-800 flex flex-col h-full overflow-hidden">
            <div className="p-4 border-b border-slate-800 flex justify-between items-center bg-slate-900/50">
              <h3 className="font-semibold">Symbol Manager</h3>
              <input type="text" placeholder="Search symbol..." value={search} onChange={e => setSearch(e.target.value)} className="bg-slate-900 border border-slate-700 rounded-lg px-3 py-1 text-sm focus:border-blue-500 outline-none" />
            </div>
            <div className="flex flex-1 overflow-hidden">
              <div className="flex-1 border-r border-slate-800 p-4 flex flex-col">
                <h4 className="text-slate-400 text-sm mb-3">Broker Symbols</h4>
                <div className="flex-1 overflow-auto space-y-2 pr-2">
                  {filteredSymbols.map((sym, i) => (
                    <div key={i} className="p-2 bg-slate-800/50 rounded flex justify-between items-center hover:bg-slate-800 transition-colors">
                      <span className="text-sm">{sym?.name || sym}</span>
                      <button onClick={() => { if(!watchList.includes(sym?.name || sym)) setWatchList([...watchList, sym?.name || sym])}} className="text-emerald-400 hover:text-emerald-300 text-lg font-bold px-2">+</button>
                    </div>
                  ))}
                </div>
              </div>
              <div className="flex-1 p-4 flex flex-col">
                <h4 className="text-slate-400 text-sm mb-3">Trade Watchlist</h4>
                <div className="flex-1 overflow-auto space-y-2 pr-2">
                  {watchList.length === 0 && <div className="text-slate-500 text-sm text-center mt-10">Empty Watchlist</div>}
                  {watchList.map((sym, i) => (
                    <div key={i} className="p-2 bg-slate-800/50 border border-slate-700/50 rounded flex justify-between items-center">
                      <span className="text-sm text-blue-400 font-medium">{sym}</span>
                      <button onClick={() => setWatchList(watchList.filter(s => s !== sym))} className="text-red-400 hover:text-red-300 text-lg font-bold px-2">-</button>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          </div>

          <div className="bg-[#1a1d2d] rounded-xl border border-slate-800 h-fit p-6 shadow-xl">
            <h3 className="font-semibold text-lg border-b border-slate-800 pb-4 mb-4">Manual Order</h3>
            <form onSubmit={handlePlaceOrder} className="space-y-4">
              <div>
                <label className="block text-sm text-slate-400 mb-1">Symbol</label>
                <input required value={orderSymbol} onChange={e => setOrderSymbol(e.target.value)} className="w-full bg-slate-900 border border-slate-700 rounded-lg px-4 py-2 focus:border-blue-500 outline-none" placeholder="XAUUSD" />
              </div>
              <div>
                <label className="block text-sm text-slate-400 mb-1">Volume</label>
                <input required type="number" step="0.01" min="0.01" value={orderVolume} onChange={e => setOrderVolume(e.target.value)} className="w-full bg-slate-900 border border-slate-700 rounded-lg px-4 py-2 focus:border-blue-500 outline-none" />
              </div>
              <div>
                <label className="block text-sm text-slate-400 mb-1">Type</label>
                <div className="grid grid-cols-2 gap-2 mt-1">
                  <button type="button" onClick={() => setOrderType('buy')} className={`py-3 rounded-lg font-bold transition-colors ${orderType === 'buy' ? 'bg-emerald-600 text-white' : 'bg-slate-800 text-slate-400 hover:bg-emerald-900/50 hover:text-emerald-500'}`}>BUY</button>
                  <button type="button" onClick={() => setOrderType('sell')} className={`py-3 rounded-lg font-bold transition-colors ${orderType === 'sell' ? 'bg-red-600 text-white' : 'bg-slate-800 text-slate-400 hover:bg-red-900/50 hover:text-red-500'}`}>SELL</button>
                </div>
              </div>
              <button type="submit" className={`w-full font-bold py-3 px-4 rounded-lg transition-colors mt-6 ${orderType === 'buy' ? 'bg-emerald-600 hover:bg-emerald-500 text-white' : 'bg-red-600 hover:bg-red-500 text-white'}`}>
                Execute {orderType.toUpperCase()}
              </button>
            </form>
          </div>
        </div>
      )}

      {activeTab === 'auto' && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 overflow-y-auto pr-2 pb-6">
          <div className="bg-[#1a1d2d] rounded-xl border border-slate-800 p-6">
            <div className="flex justify-between items-center border-b border-slate-800 pb-4 mb-4">
              <h3 className="font-semibold text-lg">System Modes</h3>
              <button onClick={saveConfig} className="bg-blue-600 hover:bg-blue-500 text-white px-4 py-1.5 rounded-lg text-sm font-medium">Save All</button>
            </div>
            <div className="flex gap-8 mb-6 bg-slate-900/50 p-4 rounded-xl border border-slate-800">
              <label className="flex items-center gap-3 cursor-pointer">
                <input type="checkbox" checked={config?.isLive || false} onChange={e => setConfig({...config, isLive: e.target.checked})} className="w-5 h-5 accent-red-500" />
                <span className="text-red-400 font-bold tracking-wide">LIVE TRADING</span>
              </label>
              <label className="flex items-center gap-3 cursor-pointer">
                <input type="checkbox" checked={config?.aiMode || false} onChange={e => setConfig({...config, aiMode: e.target.checked})} className="w-5 h-5 accent-blue-500" />
                <span className="text-blue-400 font-bold tracking-wide">AI AUTO MODE</span>
              </label>
            </div>

            <h3 className="font-semibold text-lg border-b border-slate-800 pb-2 mb-4 mt-8">Risk Parameters</h3>
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm text-slate-400 mb-1">Max Risk (%)</label>
                <input type="number" step="0.1" value={config?.riskParam?.maxRiskPct || 1.0} onChange={e => setConfig({...config, riskParam: {...config.riskParam, maxRiskPct: parseFloat(e.target.value)}})} className="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-sm outline-none focus:border-blue-500" />
              </div>
              <div>
                <label className="block text-sm text-slate-400 mb-1">Default SL (Pips/ATR)</label>
                <input type="number" value={config?.riskParam?.defaultSL || 50} onChange={e => setConfig({...config, riskParam: {...config.riskParam, defaultSL: parseFloat(e.target.value)}})} className="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-sm outline-none focus:border-blue-500" />
              </div>
            </div>
          </div>

          <div className="bg-[#1a1d2d] rounded-xl border border-slate-800 p-6">
            <h3 className="font-semibold text-lg border-b border-slate-800 pb-2 mb-4">Execution Gates</h3>
            <div className="grid grid-cols-2 gap-y-3">
              {GATE_TOGGLES.map(gate => (
                <label key={gate.id} className="flex items-center gap-2 cursor-pointer">
                  <input type="checkbox" checked={config?.gates?.[gate.id] ?? true} onChange={() => setConfig((p: any) => ({...p, gates: {...p.gates, [gate.id]: !p.gates?.[gate.id]}}))} className="w-4 h-4 accent-emerald-500" />
                  <span className="text-sm text-slate-300">{gate.label}</span>
                </label>
              ))}
            </div>
          </div>

          <div className="bg-[#1a1d2d] rounded-xl border border-slate-800 p-6 lg:col-span-2">
            <h3 className="font-semibold text-lg border-b border-slate-800 pb-2 mb-4">Strategy Toggles</h3>
            <div className="grid grid-cols-2 md:grid-cols-4 gap-y-3">
              {STRATEGY_TOGGLES.map(strat => (
                <label key={strat} className="flex items-center gap-2 cursor-pointer">
                  <input type="checkbox" checked={config?.activeStrategies?.includes(strat) ?? false} onChange={() => {
                    let strats = [...(config?.activeStrategies || [])];
                    if (strats.includes(strat)) strats = strats.filter(s => s !== strat);
                    else strats.push(strat);
                    setConfig((p: any) => ({...p, activeStrategies: strats}));
                  }} className="w-4 h-4 accent-purple-500" />
                  <span className="text-sm text-slate-300 truncate">{strat.replace(/_/g, ' ')}</span>
                </label>
              ))}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
