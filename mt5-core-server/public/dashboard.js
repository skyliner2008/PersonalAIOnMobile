/* JARVIS MT5 - Professional Dashboard Logic */

const state = {
  token: localStorage.getItem('adminToken') || '',
  view: 'overview',
  snapshot: null,
  symbols: [],
  ticks: {},
  clients: [],
  analyticsRange: '1d',
  ws: null,
  timers: []
};

const $ = (id) => document.getElementById(id);

// --- API Helper ---
async function api(path, options = {}) {
  const headers = { 'Content-Type': 'application/json' };
  if (state.token) headers['Authorization'] = `Bearer ${state.token}`;
  
  const fetchOptions = { ...options, headers };
  if (fetchOptions.body && typeof fetchOptions.body === 'object') {
    fetchOptions.body = JSON.stringify(fetchOptions.body);
  }
  
  const res = await fetch(`/api/mt5${path}`, fetchOptions);
  const json = await res.json();
  
  if (!res.ok) {
    if (res.status === 401 || res.status === 403) {
      showOverlay(true);
    }
    throw new Error(json.error || json.error_code || 'API Error');
  }
  return json.data || json;
}

// --- Auth & Overlay ---
function showOverlay(show) {
  const overlay = $('authOverlay');
  if (show) overlay.classList.add('show');
  else overlay.classList.remove('show');
}

async function unlock() {
  const id = $('adminIdInput').value.trim();
  const password = $('adminPasswordInput').value.trim();
  const errEl = $('loginError');
  
  if (errEl) errEl.style.display = 'none'; // ซ่อน error เก่า
  
  if (!id || !password) {
    if (errEl) {
      errEl.textContent = 'ID and Password required';
      errEl.style.display = 'block';
    }
    return;
  }
  
  try {
    console.log('[auth] Attempting login with ID:', id);
    const res = await fetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ id, password })
    });
    
    console.log('[auth] Response status:', res.status);
    const json = await res.json();
    console.log('[auth] Response JSON:', json);
    
    if (!res.ok) throw new Error(json.error || 'Login failed');
    
    state.token = json.token;
    localStorage.setItem('adminToken', json.token);
    showOverlay(false);
    toast('Unlocked successfully', 'success');
    boot();
  } catch (err) {
    console.error('[auth] Login error:', err);
    if (errEl) {
      errEl.textContent = `Error: ${err.message}`;
      errEl.style.display = 'block';
    } else {
      toast(err.message, 'danger');
    }
  }
}

function logout() {
  localStorage.removeItem('adminToken');
  state.token = '';
  location.reload();
}

// --- View Management ---
function switchView(view) {
  state.view = view;
  document.querySelectorAll('.nav-item').forEach(btn => {
    btn.classList.toggle('active', btn.dataset.view === view);
  });
  document.querySelectorAll('.view').forEach(el => {
    el.classList.toggle('active', el.id === `view-${view}`);
  });
  $('viewTitle').textContent = document.querySelector(`[data-view="${view}"]`)?.textContent.trim() || 'Dashboard';
}

// --- Toast ---
function toast(msg, type = 'success') {
  const el = $('toast');
  el.textContent = msg;
  el.className = `toast show ${type}`;
  setTimeout(() => el.classList.remove('show'), 3000);
}

// --- Data Fetching & Rendering ---

async function refreshSnapshot() {
  try {
    // ดึงข้อมูล Snapshot ทั่วไป (ยอดเงิน, ออเดอร์)
    const data = await api('/snapshot');
    const baseSnap = data.snapshot || data;
    
    // ดึงข้อมูล Auto Trading Config (Watchlist, AI Models)
    let autoSnap = {};
    try {
      autoSnap = await api('/auto/snapshot');
    } catch (e) {
      console.warn('Failed to load auto snapshot', e);
    }
    
    // รวมข้อมูลเข้าด้วยกัน
    state.snapshot = { ...baseSnap, ...autoSnap };
    
    renderOverview();
    renderSettings();
    renderSymbols(); // อัปเดตรายการคู่เงินด้วยเสมอ
  } catch (err) {
    console.error('Snapshot failed', err);
  }
}

async function refreshAll() {
  await refreshSnapshot();
  await loadPositions();
  await loadDecisionFeed();
  if (state.view === 'terminal') {
    await loadSymbols();
  }
  if (state.view === 'system') {
    await refreshMt5Clients();
    await refreshPairRequests();
  }
}

function renderOverview() {
  const snap = state.snapshot;
  if (!snap) return;
  
  // Account
  const acc = snap.account || {};
  $('balanceText').textContent = `$${num(acc.balance)}`;
  $('equityText').textContent = `$${num(acc.equity)}`;
  $('marginText').textContent = `$${num(acc.margin_free)}`;
  
  const profit = acc.profit || 0;
  const pnlEl = $('pnlText');
  pnlEl.textContent = `$${num(profit)}`;
  pnlEl.className = profit > 0 ? 'positive' : profit < 0 ? 'negative' : 'neutral';
  
  // Engine State
  const cfg = snap.config || {};
  const run = snap.runtime || {};
  $('engineStatus').textContent = run.state || 'IDLE';
  $('engineStatus').className = `badge ${run.state === 'RUNNING' ? 'success' : 'danger'}`;
  
  // Toggles in Settings
  $('liveToggle').checked = cfg.enableLiveTrading || false;
  $('aiToggle').checked = cfg.enableAiMode || false;
  $('v25Toggle').checked = cfg.adaptive?.v25?.enabled || false;
  $('v25OnlyToggle').checked = cfg.adaptive?.v25?.enableV25Only || false;
}

async function loadPositions() {
  try {
    const data = await api('/positions');
    const tbody = $('positionsBody');
    tbody.innerHTML = '';
    
    const positions = Array.isArray(data) ? data : data.data || [];
    
    if (positions.length === 0) {
      tbody.innerHTML = '<tr><td colspan="8" class="text-center">No open positions</td></tr>';
      return;
    }
    
    for (const p of positions) {
      const tr = document.createElement('tr');
      const profit = Number(p.profit || 0);
      const pnlClass = profit > 0 ? 'positive' : profit < 0 ? 'negative' : '';
      
      tr.innerHTML = `
        <td class="num">${esc(p.symbol)}</td>
        <td><span class="badge ${p.side === 'BUY' ? 'success' : 'danger'}">${esc(p.side)}</span></td>
        <td class="num">${num(p.volume, 2)}</td>
        <td class="num">${num(p.open_price)}</td>
        <td class="num">${num(p.current_price)}</td>
        <td class="num ${pnlClass}">${num(profit)}</td>
        <td class="num">${num(p.sl)} / ${num(p.tp)}</td>
        <td>
          <button class="btn-sm btn-secondary" onclick="closePosition('${esc(p.ticket)}', '${esc(p.symbol)}')">Close</button>
        </td>
      `;
      tbody.appendChild(tr);
    }
  } catch (err) {
    console.error('Failed to load positions', err);
  }
}

async function loadDecisionFeed() {
  try {
    const data = await api('/auto/decision-feed?limit=10');
    const el = $('decisionFeed');
    el.innerHTML = '';
    
    const items = data.rows || data || [];
    if (items.length === 0) {
      el.innerHTML = '<div class="feed-item empty">No recent signals</div>';
      return;
    }
    
    for (const item of items) {
      const div = document.createElement('div');
      div.className = 'feed-item';
      const time = new Date(item.created_at * 1000).toLocaleTimeString();
      div.innerHTML = `
        <header>
          <strong>${esc(item.symbol)}</strong>
          <span class="time">${time}</span>
        </header>
        <p>${esc(item.rationale || item.strategy)}</p>
        <div style="margin-top: 4px; font-size: 11px; color: var(--text-muted);">
          Side: <span class="${item.side === 'BUY' ? 'positive' : 'negative'}">${esc(item.side)}</span> | 
          Conf: ${num(item.confluence, 1)}
        </div>
      `;
      el.appendChild(div);
    }
  } catch (err) {
    console.error('Failed to load decision feed', err);
  }
}

// --- Terminal & Symbol Manager ---

async function loadSymbols() {
  try {
    const data = await api('/symbols-meta?limit=100');
    console.log('[symbols] Data received:', data);
    
    if (Array.isArray(data)) {
      state.symbols = data;
    } else if (data && Array.isArray(data.data)) {
      state.symbols = data.data;
    } else if (data && Array.isArray(data.symbols)) {
      state.symbols = data.symbols;
    } else if (data && Array.isArray(data.rows)) {
      state.symbols = data.rows;
    } else {
      state.symbols = [];
      console.warn('[symbols] Unknown data format:', data);
    }
    
    renderSymbols();
  } catch (err) {
    console.error('[symbols] Failed to load symbols:', err);
    toast('Failed to load symbols', 'danger');
  }
}

function renderSymbols() {
  const q = $('symbolSearch').value.trim().toUpperCase();
  
  const cfg = state.snapshot?.config || {};
  // ใช้ watchlist เพียงอย่างเดียวตามมาตรฐานของ App มือถือ
  const watchlist = cfg.watchlist || [];
  
  console.log('[symbols] Current watchlist used for render:', watchlist);
  console.log('[symbols] Raw config:', cfg);
  
  // Filter Broker Symbols
  const brokerEl = $('brokerSymbolsList');
  brokerEl.innerHTML = '';
  
  const visibleBroker = state.symbols.filter(s => {
    const name = String(s.symbol || s.name || '').toUpperCase();
    return (!q || name.includes(q)) && !watchlist.includes(name);
  }).slice(0, 50);
  
  for (const s of visibleBroker) {
    const name = String(s.symbol || s.name || '').toUpperCase();
    const div = document.createElement('div');
    div.className = 'symbol-item-pro';
    div.innerHTML = `
      <div>
        <div class="name">${esc(name)}</div>
        <div class="desc">${esc(s.description || s.path || '')}</div>
      </div>
      <button class="btn-sm btn-secondary" onclick="addToWatchlist('${esc(name)}')">+</button>
    `;
    brokerEl.appendChild(div);
  }
  
  // Render Watchlist
  const watchEl = $('autoWatchlist');
  watchEl.innerHTML = '';
  
  for (const name of watchlist) {
    if (q && !name.includes(q)) continue;
    const div = document.createElement('div');
    div.className = 'symbol-item-pro';
    div.innerHTML = `
      <div>
        <div class="name">${esc(name)}</div>
      </div>
      <button class="btn-sm btn-danger" onclick="removeFromWatchlist('${esc(name)}')">-</button>
    `;
    watchEl.appendChild(div);
  }
}

async function addToWatchlist(symbol) {
  const watchlist = state.snapshot?.config?.watchlist || [];
  if (watchlist.includes(symbol)) return;
  
  const newWatchlist = [...watchlist, symbol];
  await updateConfig({ watchlist: newWatchlist }, `Added ${symbol} to watchlist`);
}

async function removeFromWatchlist(symbol) {
  const watchlist = state.snapshot?.config?.watchlist || [];
  const newWatchlist = watchlist.filter(s => s !== symbol);
  await updateConfig({ watchlist: newWatchlist }, `Removed ${symbol} from watchlist`);
}

// --- Settings ---

function renderSettings() {
  const cfg = state.snapshot?.config || {};
  
  // Set risk values
  $('maxRiskPct').value = cfg.maxRiskPerTrade || 1.0;
  $('defaultSL').value = cfg.defaultSL || 50;
  $('defaultTP').value = cfg.defaultTP || 2.0;
}

async function refreshProviders() {
  try {
    const data = await api('/auto/providers');
    const roles = data.roles || [];
    const current = data.currentSelection || {};
    
    setupModelSelect('analystModelSelect', data.providers, current.analyst?.model);
    setupModelSelect('riskOfficerModelSelect', data.providers, current.riskOfficer?.model);
    setupModelSelect('executionModelSelect', data.providers, current.executionTrader?.model);
    setupModelSelect('slTpAgentSelect', data.providers, current.slTpAgent?.model);
    
    toast('Models loaded', 'success');
  } catch (err) {
    toast('Failed to load models', 'danger');
  }
}

function setupModelSelect(elId, providers, currentModel) {
  const select = $(elId);
  select.innerHTML = '';
  
  for (const p of providers) {
    // For simplicity, we just list the provider's generationModel if we don't have full list
    const opt = document.createElement('option');
    opt.value = p.defaults?.generationModel;
    opt.textContent = `${p.displayName} (${p.defaults?.generationModel})`;
    if (currentModel === p.defaults?.generationModel) opt.selected = true;
    select.appendChild(opt);
  }
}

// --- Commands ---

async function updateConfig(partial, msg = 'Config updated') {
  try {
    await api('/auto/config', { method: 'POST', body: partial });
    toast(msg, 'success');
    await refreshSnapshot();
  } catch (err) {
    toast(err.message, 'danger');
  }
}

async function engineCommand(path, msg) {
  try {
    await api(path, { method: 'POST' });
    toast(msg, 'success');
    await refreshSnapshot();
  } catch (err) {
    toast(err.message, 'danger');
  }
}

async function closePosition(ticket, symbol) {
  if (!confirm(`Close position ${ticket}?`)) return;
  try {
    await api('/close', { method: 'POST', body: { ticket, symbol } });
    toast(`Closed ${symbol}`, 'success');
    loadPositions();
  } catch (err) {
    toast(err.message, 'danger');
  }
}

async function placeOrder() {
  const symbol = $('orderSymbol').value.trim();
  const volume = parseFloat($('orderVolume').value);
  const side = $('orderType').value;
  const sl = parseFloat($('orderSL').value) || undefined;
  const tp = parseFloat($('orderTP').value) || undefined;
  
  if (!symbol || !volume) return toast('Symbol and Volume required', 'danger');
  
  try {
    await api('/order', { method: 'POST', body: { symbol, volume, side, sl, tp } });
    toast(`Order placed for ${symbol}`, 'success');
    loadPositions();
  } catch (err) {
    toast(err.message, 'danger');
  }
}

// --- System ---

async function refreshMt5Clients() {
  try {
    const data = await api('/clients');
    const el = $('mt5ClientsList');
    el.innerHTML = '';
    const rows = data.rows || [];
    
    if (rows.length === 0) {
      el.innerHTML = '<div class="feed-item">No active clients</div>';
      return;
    }
    
    for (const r of rows) {
      const div = document.createElement('div');
      div.className = 'symbol-item-pro';
      div.innerHTML = `
        <div>
          <div class="name">PID: ${r.pids?.join(', ') || '-'}</div>
          <div class="desc">${esc(r.exePath)}</div>
        </div>
        <button class="btn-sm btn-danger" onclick="stopClient(${r.pids?.[0]})">Stop</button>
      `;
      el.appendChild(div);
    }
  } catch (err) {
    console.error('Failed to load clients', err);
  }
}

async function startClient() {
  const exePath = $('newClientPath').value.trim();
  if (!exePath) return toast('Exe path required', 'danger');
  try {
    await api('/clients/start', { method: 'POST', body: { exePath } });
    toast('Client start requested', 'success');
    $('newClientPath').value = '';
    refreshMt5Clients();
  } catch (err) {
    toast(err.message, 'danger');
  }
}

async function stopClient(pid) {
  if (!pid) return;
  if (!confirm(`Stop MT5 client PID ${pid}?`)) return;
  try {
    await api('/clients/stop', { method: 'POST', body: { pid } });
    toast('Client stop requested', 'success');
    refreshMt5Clients();
  } catch (err) {
    toast(err.message, 'danger');
  }
}

async function refreshPairRequests() {
  try {
    const data = await api('/pairing-requests?status=PENDING'); // Adjust endpoint if needed
    const el = $('pairRequests');
    el.innerHTML = '';
    const rows = data.rows || data || [];
    
    if (rows.length === 0) {
      el.innerHTML = '<div class="feed-item">No pending requests</div>';
      return;
    }
    
    for (const r of rows) {
      const div = document.createElement('div');
      div.className = 'feed-item';
      div.innerHTML = `
        <header>
          <strong>${esc(r.device_name || r.device_id)}</strong>
          <span class="time">${esc(r.app_version)}</span>
        </header>
        <div style="margin-top: 8px; display: flex; gap: 8px;">
          <button class="btn-sm btn-success" onclick="pairAction('${r.id}', 'approve')">Approve</button>
          <button class="btn-sm btn-danger" onclick="pairAction('${r.id}', 'reject')">Reject</button>
        </div>
      `;
      el.appendChild(div);
    }
  } catch (err) {
    console.error('Failed to load pairing requests', err);
  }
}

async function pairAction(id, action) {
  try {
    await api(`/pairing-requests/${id}/${action}`, { method: 'POST' }); // Adjust endpoint if needed
    toast(`Device ${action}d`, 'success');
    refreshPairRequests();
  } catch (err) {
    toast(err.message, 'danger');
  }
}

// --- Helpers ---
function esc(val) {
  return String(val ?? '').replace(/[&<>"']/g, ch => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch]));
}

function num(val, dec = 2) {
  const n = parseFloat(val);
  return isNaN(n) ? '0.00' : n.toFixed(dec).replace(/\B(?=(\d{3})+(?!\d))/g, ",");
}

// --- Boot & Events ---
function bindEvents() {
  // Navigation
  document.querySelectorAll('.nav-item').forEach(btn => {
    btn.addEventListener('click', () => {
      switchView(btn.dataset.view);
      refreshAll(); // Refresh data for the specific view if needed
    });
  });
  
  // Auth
  $('unlockBtn').addEventListener('click', unlock);
  $('adminPasswordInput').addEventListener('keydown', (e) => { if (e.key === 'Enter') unlock(); });
  $('logoutBtn').addEventListener('click', logout);
  
  // Overview
  $('refreshBtn').addEventListener('click', refreshAll);
  $('startBtn').addEventListener('click', () => engineCommand('/auto/start', 'Engine started'));
  $('stopBtn').addEventListener('click', () => engineCommand('/auto/stop', 'Engine stopped'));
  $('runOnceBtn').addEventListener('click', () => engineCommand('/auto/run-once', 'Cycle complete'));
  $('learnBtn').addEventListener('click', () => engineCommand('/auto/learn', 'Learning complete'));
  $('haltBtn').addEventListener('click', () => engineCommand('/auto/halt', 'Engine halted'));
  
  // Terminal
  $('symbolSearch').addEventListener('input', renderSymbols);
  $('placeOrderBtn').addEventListener('click', placeOrder);
  
  // Settings
  $('refreshProvidersBtn').addEventListener('click', refreshProviders);
  
  // Toggles
  $('liveToggle').addEventListener('change', (e) => updateConfig({ enableLiveTrading: e.target.checked }, 'Live trading updated'));
  $('aiToggle').addEventListener('change', (e) => updateConfig({ enableAiMode: e.target.checked }, 'AI mode updated'));
  $('v25Toggle').addEventListener('change', (e) => updateConfig({ adaptive: { v25: { enabled: e.target.checked } } }, 'V25 updated'));
  $('v25OnlyToggle').addEventListener('change', (e) => updateConfig({ adaptive: { v25: { enableV25Only: e.target.checked } } }, 'V25 only updated'));
  
  // System
  $('refreshClientsBtn').addEventListener('click', refreshMt5Clients);
  $('startClientBtn').addEventListener('click', startClient);
  $('refreshPairBtn').addEventListener('click', refreshPairRequests);
}

function boot() {
  // ต้องผูก Event ก่อนเสมอ เพื่อให้ปุ่ม Unlock ทำงานได้แม้ยังไม่มี Token
  bindEvents();
  
  if (!state.token) {
    showOverlay(true);
    return;
  }
  
  refreshAll();
  
  // Poll for updates
  state.timers.push(setInterval(refreshSnapshot, 10000));
  state.timers.push(setInterval(loadPositions, 5000));
}

// Make functions global for inline onclick handlers
window.addToWatchlist = addToWatchlist;
window.removeFromWatchlist = removeFromWatchlist;
window.closePosition = closePosition;
window.stopClient = stopClient;
window.pairAction = pairAction;

document.addEventListener('DOMContentLoaded', boot);
