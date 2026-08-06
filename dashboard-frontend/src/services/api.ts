import axios from 'axios';

const api = axios.create({
  baseURL: '/api',
  headers: {
    'Content-Type': 'application/json'
  }
});

// Interceptor to attach token
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('adminToken');
  if (token) {
    config.headers['Authorization'] = `Bearer ${token}`;
  }
  return config;
});

// Interceptor to handle auth errors
api.interceptors.response.use(
  (response) => response.data?.data || response.data, // Unbox data if needed
  (error) => {
    if (error.response?.status === 401 || error.response?.status === 403) {
      window.dispatchEvent(new Event('auth_error'));
    }
    return Promise.reject(error.response?.data?.error || error.message);
  }
);

export const authApi = {
  login: (id: string, password: string): Promise<any> => api.post('/auth/login', { id, password })
};

export const mt5Api = {
  getSnapshot: () => api.get('/mt5/snapshot'),
  getAutoSnapshot: () => api.get('/mt5/auto/snapshot'),
  getPositions: () => api.get('/mt5/positions'),
  getDecisionFeed: () => api.get('/mt5/auto/decision-feed?limit=10'),
  getSymbols: () => api.get('/mt5/symbols-meta?limit=100'),
  updateConfig: (config: any) => api.post('/mt5/auto/config', config),
  command: (path: string) => api.post(`/mt5${path}`),
  closePosition: (ticket: string, symbol: string) => api.post('/mt5/close', { ticket, symbol }),
  placeOrder: (order: any) => api.post('/mt5/order', order),
  getProviders: () => api.get('/mt5/auto/providers'),
  getClients: () => api.get('/mt5/clients'),
  startClient: (exePath: string) => api.post('/mt5/clients/start', { exePath }),
  stopClient: (pid: number) => api.post('/mt5/clients/stop', { pid }),
  runScript: (script: string, arg: string) => api.post('/mt5/auto/run-script', { script, arg })
};

export default api;
