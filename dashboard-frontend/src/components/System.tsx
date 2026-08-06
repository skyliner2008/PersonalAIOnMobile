import { useState, useEffect, useRef } from 'react';
import { mt5Api } from '../services/api';

const System = () => {
  const [clients, setClients] = useState<any[]>([]);
  const [newClientPath, setNewClientPath] = useState('');
  const [logs, setLogs] = useState<string[]>([]);
  const logEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    fetchClients();
    
    // Simulate ws log receiving (in a real app, hook up WebSocket here)
    const handleLog = (e: any) => {
      setLogs(prev => [...prev, e.detail].slice(-100)); // Keep last 100
    };
    window.addEventListener('system_log', handleLog);
    return () => window.removeEventListener('system_log', handleLog);
  }, []);

  useEffect(() => {
    logEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [logs]);

  const fetchClients = async () => {
    try {
      const data: any = await mt5Api.getClients();
      setClients(Array.isArray(data) ? data : data?.data || []);
    } catch (e) {
      console.error(e);
    }
  };

  const startClient = async () => {
    if (!newClientPath) return;
    try {
      await mt5Api.startClient(newClientPath);
      setNewClientPath('');
      fetchClients();
    } catch (e) {
      console.error(e);
    }
  };

  const stopClient = async (pid: number) => {
    try {
      await mt5Api.stopClient(pid);
      fetchClients();
    } catch (e) {
      console.error(e);
    }
  };

  return (
    <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 h-full">
      <div className="col-span-1 flex flex-col gap-6 h-full">
        <div className="bg-[#1a1d2d] rounded-xl border border-slate-800 flex flex-col flex-1">
          <div className="p-4 border-b border-slate-800 flex justify-between items-center shrink-0">
            <h3 className="font-semibold text-lg">MT5 Terminal Clients</h3>
            <button onClick={fetchClients} className="text-sm bg-slate-800 hover:bg-slate-700 px-3 py-1 rounded">Refresh</button>
          </div>
          <div className="flex-1 overflow-auto p-4 space-y-3">
            {clients.length === 0 && <div className="text-slate-500 text-sm text-center">No active clients</div>}
            {clients.map(client => (
              <div key={client.pid} className="bg-slate-800/50 p-3 rounded-lg border border-slate-700/50 flex justify-between items-center">
                <div>
                  <div className="font-medium text-sm">PID: {client.pid}</div>
                  <div className="text-xs text-slate-400 truncate max-w-[150px]" title={client.path}>{client.path}</div>
                </div>
                <button onClick={() => stopClient(client.pid)} className="text-red-400 hover:bg-red-400/10 px-2 py-1 rounded text-xs">Kill</button>
              </div>
            ))}
          </div>
          <div className="p-4 border-t border-slate-800 shrink-0 flex gap-2">
            <input 
              value={newClientPath}
              onChange={e => setNewClientPath(e.target.value)}
              placeholder="C:\Path\To\terminal64.exe"
              className="flex-1 bg-slate-900 border border-slate-700 rounded-lg px-3 py-1.5 text-sm focus:border-blue-500 focus:outline-none"
            />
            <button onClick={startClient} className="bg-emerald-600 hover:bg-emerald-500 text-white px-3 py-1.5 rounded-lg text-sm font-medium">Start</button>
          </div>
        </div>
      </div>

      <div className="col-span-1 lg:col-span-2 flex flex-col gap-6 h-full">
        <div className="bg-[#1a1d2d] rounded-xl border border-slate-800 flex flex-col">
          <div className="p-4 border-b border-slate-800 shrink-0">
            <h3 className="font-semibold text-lg">System Log</h3>
          </div>
          <div className="flex-1 p-4 overflow-auto font-mono text-xs text-green-400 bg-[#0a0a0a] min-h-[300px]">
            {logs.length === 0 ? <span className="text-slate-600">Waiting for logs...</span> : logs.map((log, i) => (
              <div key={i} className="mb-1">{log}</div>
            ))}
            <div ref={logEndRef} />
          </div>
        </div>

        <div className="bg-[#1a1d2d] rounded-xl border border-slate-800 p-6">
          <div className="flex justify-between items-center mb-4">
            <h3 className="font-semibold text-lg">AI Provider Models Configuration</h3>
            <a href="obsidian://open?vault=PersonalAIBot&file=System%20Configuration" className="text-xs text-blue-400 hover:underline">Reference Obsidian Wiki</a>
          </div>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div className="bg-slate-900/50 p-4 rounded-lg border border-slate-700/50">
              <label className="block text-sm font-medium text-slate-300 mb-2">LocalOnnxProvider</label>
              <input type="text" placeholder="Path to ONNX Model" className="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-sm focus:border-blue-500 outline-none mb-2" defaultValue="models/onnx/model_v2.onnx" />
              <div className="flex items-center gap-2">
                <input type="checkbox" defaultChecked className="accent-blue-500" />
                <span className="text-xs text-slate-400">Enable Hardware Acceleration (CUDA/TensorRT)</span>
              </div>
            </div>
            <div className="bg-slate-900/50 p-4 rounded-lg border border-slate-700/50">
              <label className="block text-sm font-medium text-slate-300 mb-2">EmbeddingProvider</label>
              <input type="text" placeholder="Embedding API URL or Local Model" className="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-sm focus:border-blue-500 outline-none mb-2" defaultValue="text-embedding-ada-002" />
              <div className="flex items-center gap-2">
                <input type="checkbox" defaultChecked className="accent-blue-500" />
                <span className="text-xs text-slate-400">Cache Embeddings Locally</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default System;
