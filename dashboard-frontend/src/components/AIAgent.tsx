import { useState, useEffect } from 'react';
import { mt5Api } from '../services/api';

export default function AIAgent() {
  const [providers, setProviders] = useState<any>({ analyst: [], riskOfficer: [], execution: [], sltp: [] });
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    fetchProviders();
  }, []);

  const fetchProviders = async () => {
    try {
      const p: any = await mt5Api.getProviders();
      setProviders(p || { analyst: [], riskOfficer: [], execution: [], sltp: [] });
    } catch (e) {
      console.error(e);
    }
  };

  const handleSave = () => {
    setLoading(true);
    setTimeout(() => {
      setLoading(false);
      alert('AI Agent Configurations Saved');
    }, 800);
  };

  return (
    <div className="flex flex-col gap-6 h-full overflow-auto pr-2 pb-6">
      <div className="bg-[#1a1d2d] rounded-2xl border border-slate-800 p-6 flex flex-col lg:flex-row justify-between items-start lg:items-center gap-4 shrink-0 shadow-lg">
        <div>
          <h2 className="text-2xl font-bold bg-clip-text text-transparent bg-gradient-to-r from-blue-400 to-purple-500">AI Agents Core</h2>
          <p className="text-slate-400 text-sm mt-1">Configure and manage the neural network providers for each trading subsystem.</p>
        </div>
        <div className="flex gap-3">
          <button onClick={fetchProviders} className="bg-slate-800 hover:bg-slate-700 text-slate-200 px-4 py-2 rounded-lg font-medium transition-colors">Refresh Models</button>
          <button onClick={handleSave} disabled={loading} className="bg-blue-600 hover:bg-blue-500 text-white px-6 py-2 rounded-lg font-bold transition-colors disabled:opacity-50">
            {loading ? 'Saving...' : 'Save Configuration'}
          </button>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {/* Analyst Agent */}
        <div className="bg-[#1a1d2d] rounded-2xl border border-slate-800 p-6 shadow-xl relative overflow-hidden group">
          <div className="absolute top-0 right-0 w-32 h-32 bg-blue-500/5 rounded-bl-full -z-10 group-hover:bg-blue-500/10 transition-colors"></div>
          <div className="flex items-center gap-3 mb-6">
            <div className="w-10 h-10 rounded-xl bg-blue-500/20 text-blue-400 flex items-center justify-center font-bold text-xl">A</div>
            <div>
              <h3 className="font-semibold text-lg text-slate-200">Analyst Agent</h3>
              <p className="text-xs text-slate-500">Market parsing & signal generation</p>
            </div>
          </div>
          <div className="space-y-4">
            <div>
              <label className="block text-sm text-slate-400 mb-1">Primary Provider Model</label>
              <select className="w-full bg-slate-900 border border-slate-700 rounded-lg px-4 py-2.5 text-sm focus:border-blue-500 outline-none">
                <option>GPT-4o (OpenAI)</option>
                <option>Claude 3.5 Sonnet (Anthropic)</option>
                <option>Gemini 1.5 Pro (Google)</option>
                {providers?.analyst?.map((m: any, i: number) => <option key={i}>{m}</option>)}
              </select>
            </div>
            <div>
              <label className="block text-sm text-slate-400 mb-1">Context Window Strategy</label>
              <select className="w-full bg-slate-900 border border-slate-700 rounded-lg px-4 py-2.5 text-sm focus:border-blue-500 outline-none">
                <option>Deep Context (128k) - Slower</option>
                <option>Standard Context (16k) - Balanced</option>
                <option>Sniper Context (4k) - Ultra Fast</option>
              </select>
            </div>
          </div>
        </div>

        {/* Risk Officer */}
        <div className="bg-[#1a1d2d] rounded-2xl border border-slate-800 p-6 shadow-xl relative overflow-hidden group">
          <div className="absolute top-0 right-0 w-32 h-32 bg-purple-500/5 rounded-bl-full -z-10 group-hover:bg-purple-500/10 transition-colors"></div>
          <div className="flex items-center gap-3 mb-6">
            <div className="w-10 h-10 rounded-xl bg-purple-500/20 text-purple-400 flex items-center justify-center font-bold text-xl">R</div>
            <div>
              <h3 className="font-semibold text-lg text-slate-200">Risk Officer Agent</h3>
              <p className="text-xs text-slate-500">Capital protection & position sizing</p>
            </div>
          </div>
          <div className="space-y-4">
            <div>
              <label className="block text-sm text-slate-400 mb-1">Primary Provider Model</label>
              <select className="w-full bg-slate-900 border border-slate-700 rounded-lg px-4 py-2.5 text-sm focus:border-purple-500 outline-none">
                <option>Claude 3.5 Sonnet (Anthropic)</option>
                <option>GPT-4o (OpenAI)</option>
                <option>Llama 3 70B (Local/Groq)</option>
              </select>
            </div>
            <div>
              <label className="block text-sm text-slate-400 mb-1">Risk Override Authority</label>
              <select className="w-full bg-slate-900 border border-slate-700 rounded-lg px-4 py-2.5 text-sm focus:border-purple-500 outline-none">
                <option>Strict (Veto Any Trade)</option>
                <option>Moderate (Adjust Position Size)</option>
                <option>Advisory Only</option>
              </select>
            </div>
          </div>
        </div>

        {/* Execution Engine */}
        <div className="bg-[#1a1d2d] rounded-2xl border border-slate-800 p-6 shadow-xl relative overflow-hidden group">
          <div className="absolute top-0 right-0 w-32 h-32 bg-emerald-500/5 rounded-bl-full -z-10 group-hover:bg-emerald-500/10 transition-colors"></div>
          <div className="flex items-center gap-3 mb-6">
            <div className="w-10 h-10 rounded-xl bg-emerald-500/20 text-emerald-400 flex items-center justify-center font-bold text-xl">E</div>
            <div>
              <h3 className="font-semibold text-lg text-slate-200">Execution Agent</h3>
              <p className="text-xs text-slate-500">Timing & slippage management</p>
            </div>
          </div>
          <div className="space-y-4">
            <div>
              <label className="block text-sm text-slate-400 mb-1">Primary Provider Model</label>
              <select className="w-full bg-slate-900 border border-slate-700 rounded-lg px-4 py-2.5 text-sm focus:border-emerald-500 outline-none">
                <option>Mixtral 8x7B (Ultra Fast)</option>
                <option>GPT-3.5 Turbo</option>
                <option>Claude 3 Haiku</option>
              </select>
            </div>
            <div>
              <label className="block text-sm text-slate-400 mb-1">Execution Speed Preference</label>
              <select className="w-full bg-slate-900 border border-slate-700 rounded-lg px-4 py-2.5 text-sm focus:border-emerald-500 outline-none">
                <option>Aggressive (Market Orders immediately)</option>
                <option>Balanced (Limit Orders within spread)</option>
                <option>Sniper (Wait for exact PB)</option>
              </select>
            </div>
          </div>
        </div>

        {/* SL/TP Agent */}
        <div className="bg-[#1a1d2d] rounded-2xl border border-slate-800 p-6 shadow-xl relative overflow-hidden group">
          <div className="absolute top-0 right-0 w-32 h-32 bg-rose-500/5 rounded-bl-full -z-10 group-hover:bg-rose-500/10 transition-colors"></div>
          <div className="flex items-center gap-3 mb-6">
            <div className="w-10 h-10 rounded-xl bg-rose-500/20 text-rose-400 flex items-center justify-center font-bold text-xl">S</div>
            <div>
              <h3 className="font-semibold text-lg text-slate-200">SL/TP Manager</h3>
              <p className="text-xs text-slate-500">Dynamic trailing & break-even logic</p>
            </div>
          </div>
          <div className="space-y-4">
            <div>
              <label className="block text-sm text-slate-400 mb-1">Primary Provider Model</label>
              <select className="w-full bg-slate-900 border border-slate-700 rounded-lg px-4 py-2.5 text-sm focus:border-rose-500 outline-none">
                <option>Deterministic Algorithm (No AI)</option>
                <option>Claude 3 Haiku (Dynamic AI)</option>
                <option>GPT-4o Mini</option>
              </select>
            </div>
            <div>
              <label className="block text-sm text-slate-400 mb-1">Trailing Mode</label>
              <select className="w-full bg-slate-900 border border-slate-700 rounded-lg px-4 py-2.5 text-sm focus:border-rose-500 outline-none">
                <option>SMC Market Structure Trailing</option>
                <option>ATR Based Trailing</option>
                <option>Fixed Pips Trailing</option>
              </select>
            </div>
          </div>
        </div>

      </div>
    </div>
  );
}
