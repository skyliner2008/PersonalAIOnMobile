import { useState } from 'react';
import { mt5Api } from '../services/api';

const Analytics = () => {
  const [output, setOutput] = useState<string>('Ready to run analytics...');
  const [gateCategory, setGateCategory] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  const runScript = async (scriptName: string, arg: string = '') => {
    setIsLoading(true);
    setOutput(`Running ${scriptName} ${arg}...`);
    try {
      const res: any = await mt5Api.runScript(scriptName, arg);
      setOutput(res.output || 'No output received');
    } catch (e: any) {
      setOutput(`Error: ${e.message || e}`);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="h-full flex flex-col gap-6">
      <div className="bg-[#1a1d2d] rounded-xl border border-slate-800 p-6 shrink-0">
        <h3 className="font-semibold text-lg border-b border-slate-800 pb-2 mb-4">Analytics Tools</h3>
        <div className="flex flex-wrap gap-4 items-center">
          <button 
            disabled={isLoading}
            onClick={() => runScript('advanced_analytics.mjs', '--all')}
            className="bg-blue-600 hover:bg-blue-500 disabled:opacity-50 text-white px-4 py-2 rounded-lg font-medium transition-colors"
          >
            Run Analyze (All-Time)
          </button>
          
          <button 
            disabled={isLoading}
            onClick={() => runScript('gate_analytics.mjs', '--summary')}
            className="bg-purple-600 hover:bg-purple-500 disabled:opacity-50 text-white px-4 py-2 rounded-lg font-medium transition-colors"
          >
            Gate Analytics (Summary)
          </button>

          <div className="flex items-center gap-2 border border-slate-700 rounded-lg p-1 bg-slate-900">
            <input 
              type="text" 
              value={gateCategory}
              onChange={e => setGateCategory(e.target.value)}
              placeholder="ZONE_GATE, RISK_GATE..."
              className="bg-transparent border-none px-3 py-1.5 text-sm focus:outline-none"
            />
            <button 
              disabled={isLoading || !gateCategory}
              onClick={() => runScript('gate_analytics.mjs', `--category ${gateCategory}`)}
              className="bg-slate-700 hover:bg-slate-600 disabled:opacity-50 text-white px-3 py-1.5 rounded-md text-sm transition-colors"
            >
              Run Gate Detail
            </button>
          </div>
        </div>
      </div>

      <div className="bg-[#1a1d2d] rounded-xl border border-slate-800 p-6 shrink-0 mt-6">
        <h3 className="font-semibold text-lg border-b border-slate-800 pb-2 mb-4">AI Learning System Analytics</h3>
        <p className="text-sm text-slate-400 mb-4">Pull and analyze data from the AI's learning and memory systems (Obsidian knowledge base and runtime vector store).</p>
        <div className="flex flex-wrap gap-4 items-center">
          <button 
            disabled={isLoading}
            onClick={() => runScript('ai_memory_audit.mjs', '--deep')}
            className="bg-emerald-600 hover:bg-emerald-500 disabled:opacity-50 text-white px-4 py-2 rounded-lg font-medium transition-colors"
          >
            Audit AI Memory
          </button>
          
          <button 
            disabled={isLoading}
            onClick={() => runScript('model_performance_review.mjs', '--recent 7d')}
            className="bg-teal-600 hover:bg-teal-500 disabled:opacity-50 text-white px-4 py-2 rounded-lg font-medium transition-colors"
          >
            7D Model Performance
          </button>
        </div>
      </div>

      <div className="flex-1 bg-[#1a1d2d] rounded-xl border border-slate-800 flex flex-col overflow-hidden min-h-[400px]">
        <div className="p-4 border-b border-slate-800 shrink-0 flex justify-between items-center bg-[#111]">
          <h3 className="font-semibold text-sm text-slate-400">Terminal Output</h3>
          {isLoading && <div className="w-4 h-4 rounded-full border-2 border-blue-500 border-t-transparent animate-spin" />}
        </div>
        <div className="flex-1 p-4 font-mono text-xs sm:text-sm text-[#0f0] bg-[#0a0a0a] overflow-auto whitespace-pre-wrap">
          {output}
        </div>
      </div>
    </div>
  );
};

export default Analytics;
