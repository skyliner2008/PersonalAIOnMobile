import { useState, useEffect } from 'react';
import { LayoutDashboard, LogOut, Terminal, Lock, Activity, Cpu, Database, LineChart } from 'lucide-react';
import Overview from './components/Overview';
import TradingViewDashboard from './components/TradingViewDashboard';
import TradeMenu from './components/TradeMenu';
import AIAgent from './components/AIAgent';
import System from './components/System';
import Analytics from './components/Analytics';
import { authApi } from './services/api';

function App() {
  const [activeTab, setActiveTab] = useState('overview');
  const [isAuthenticated, setIsAuthenticated] = useState(!!localStorage.getItem('adminToken'));
  const [id, setId] = useState('admin');
  const [password, setPassword] = useState('');
  const [loginError, setLoginError] = useState('');

  useEffect(() => {
    const handleAuthError = () => {
      localStorage.removeItem('adminToken');
      setIsAuthenticated(false);
    };
    window.addEventListener('auth_error', handleAuthError);
    return () => window.removeEventListener('auth_error', handleAuthError);
  }, []);

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const res: any = await authApi.login(id, password);
      localStorage.setItem('adminToken', res.token);
      setIsAuthenticated(true);
      setLoginError('');
    } catch (err: any) {
      setLoginError(err || 'Login failed');
    }
  };

  const handleLogout = () => {
    localStorage.removeItem('adminToken');
    setIsAuthenticated(false);
  };

  if (!isAuthenticated) {
    return (
      <div className="flex h-screen w-full items-center justify-center bg-[#0f111a] text-slate-200">
        <form onSubmit={handleLogin} className="bg-[#1a1d2d] p-8 rounded-2xl border border-slate-800 shadow-2xl w-full max-w-md">
          <div className="flex justify-center mb-6">
            <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-blue-500 to-purple-600 flex items-center justify-center">
              <Lock className="text-white" />
            </div>
          </div>
          <h2 className="text-2xl font-bold text-center mb-2">JARVIS Access</h2>
          <p className="text-slate-400 text-center mb-6 text-sm">Professional AI Trading Platform</p>
          
          <div className="space-y-4">
            <div>
              <label className="block text-sm text-slate-400 mb-1">Admin ID</label>
              <input value={id} onChange={e => setId(e.target.value)} className="w-full bg-slate-900 border border-slate-700 rounded-lg px-4 py-2 focus:border-blue-500 focus:outline-none" />
            </div>
            <div>
              <label className="block text-sm text-slate-400 mb-1">Password</label>
              <input type="password" value={password} onChange={e => setPassword(e.target.value)} className="w-full bg-slate-900 border border-slate-700 rounded-lg px-4 py-2 focus:border-blue-500 focus:outline-none" />
            </div>
            {loginError && <div className="text-red-400 text-sm bg-red-500/10 p-2 rounded">{loginError}</div>}
            <button type="submit" className="w-full bg-blue-600 hover:bg-blue-500 text-white font-bold py-2 px-4 rounded-lg transition-colors mt-4">
              Unlock
            </button>
          </div>
        </form>
      </div>
    );
  }

  const renderContent = () => {
    switch (activeTab) {
      case 'overview': return <Overview />;
      case 'tradingview': return <TradingViewDashboard />;
      case 'trade': return <TradeMenu />;
      case 'aiagent': return <AIAgent />;
      case 'system': return <System />;
      case 'analytics': return <Analytics />;
      default: return <Overview />;
    }
  };

  const tabs = [
    { id: 'overview', label: 'Overview', icon: LayoutDashboard },
    { id: 'tradingview', label: 'TradingView', icon: Activity },
    { id: 'trade', label: 'Trade', icon: Terminal },
    { id: 'aiagent', label: 'AI Agent', icon: Cpu },
    { id: 'system', label: 'System', icon: Database },
    { id: 'analytics', label: 'Analytics', icon: LineChart },
  ];

  return (
    <div className="flex h-screen bg-[#0f111a] text-slate-200 font-sans">
      <aside className="w-64 bg-[#1a1d2d] border-r border-slate-800 flex flex-col">
        <div className="p-6 flex items-center gap-3">
          <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-blue-500 to-purple-600 flex items-center justify-center font-bold text-white shadow-lg">J</div>
          <span className="text-xl font-bold bg-clip-text text-transparent bg-gradient-to-r from-blue-400 to-purple-500">JARVIS MT5</span>
        </div>
        
        <nav className="flex-1 px-4 py-4 space-y-2">
          {tabs.map(tab => {
            const Icon = tab.icon;
            const isActive = activeTab === tab.id;
            return (
              <button key={tab.id} onClick={() => setActiveTab(tab.id)} className={`w-full flex items-center gap-3 px-4 py-3 rounded-xl font-medium transition-colors ${isActive ? 'bg-blue-500/10 text-blue-400' : 'hover:bg-slate-800/50 text-slate-400 hover:text-slate-200'}`}>
                <Icon size={20} /> {tab.label}
              </button>
            );
          })}
        </nav>

        <div className="p-4 border-t border-slate-800">
          <button onClick={handleLogout} className="w-full flex items-center gap-3 px-4 py-3 rounded-xl hover:bg-red-500/10 text-slate-400 hover:text-red-400 transition-colors">
            <LogOut size={20} /> Logout
          </button>
        </div>
      </aside>

      <main className="flex-1 flex flex-col min-w-0 overflow-hidden">
        <header className="h-16 border-b border-slate-800 bg-[#1a1d2d]/50 backdrop-blur-md flex items-center justify-between px-8 shrink-0">
          <h1 className="text-lg font-semibold capitalize">{activeTab}</h1>
          <div className="flex items-center gap-4">
            <div className="flex items-center gap-2 px-3 py-1.5 rounded-full bg-emerald-500/10 border border-emerald-500/20">
              <div className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse" />
              <span className="text-sm font-medium text-emerald-400">Live Backend</span>
            </div>
          </div>
        </header>

        <div className="flex-1 overflow-auto p-8">
          {renderContent()}
        </div>
      </main>
    </div>
  );
}

export default App;
