import fs from 'node:fs';
import path from 'node:path';
import { spawn } from 'node:child_process';
import { config } from '../config.js';
import { atLog, atError, tryParseJson } from './auto/utils.js';

type BridgeHealthState = {
  reachable: boolean;
  mt5Ready: boolean;
  message: string;
};

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function getBridgeHealth(timeoutMs = 2000): Promise<BridgeHealthState> {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const res = await fetch(`${config.mt5BridgeUrl}/health`, {
      method: 'GET',
      signal: controller.signal,
    });
    const raw = await res.text();
    const payload = tryParseJson(raw);
    const data = payload?.data && typeof payload.data === 'object' ? (payload.data as Record<string, unknown>) : null;
    const mt5Ready = Boolean(data?.mt5Ready);
    const message = String(data?.message || payload?.error || `HTTP ${res.status}`);
    if (!res.ok) return { reachable: false, mt5Ready: false, message };
    return { reachable: true, mt5Ready, message };
  } catch (err: any) {
    return { reachable: false, mt5Ready: false, message: String(err?.message || err) };
  } finally {
    clearTimeout(timeout);
  }
}



function resolveBridgeCommands(): string[] {
  if (config.bridgeStartCmd) return [config.bridgeStartCmd];

  const bridgeScript = path.resolve(process.cwd(), 'bridge', 'mt5_bridge.py');
  if (!fs.existsSync(bridgeScript)) return [];

  return [`python "${bridgeScript}"`, `py -3 "${bridgeScript}"`];
}

function startBridgeProcess(command: string): boolean {
  try {
    const child = spawn(command, {
      cwd: process.cwd(),
      shell: true,
      detached: true,
      stdio: 'ignore',
      windowsHide: true,
    });
    child.unref();
    // eslint-disable-next-line no-console
    atLog(`[bridge-supervisor] start command launched: ${command}`);
    return true;
  } catch (err: any) {
    // eslint-disable-next-line no-console
    console.warn(`[bridge-supervisor] start command failed: ${command} -> ${String(err?.message || err)}`);
    return false;
  }
}

export async function ensureBridgeReady(): Promise<void> {
  // PRE-POLL: start.bat may have just launched the Python bridge and it is
  // still booting (MetaTrader5 init typically takes 5–10s). Retry a few
  // times before deciding to spawn — otherwise we race start.bat and end up
  // with two Python processes listening on the same port / racing for MT5.
  const PRE_POLL_ATTEMPTS = 6;       // 6 × 2s = 12s window
  const PRE_POLL_GAP_MS = 2000;
  let health = await getBridgeHealth();
  for (let i = 1; i <= PRE_POLL_ATTEMPTS && !health.reachable; i += 1) {
    // eslint-disable-next-line no-console
    atLog(
      `[bridge-supervisor] bridge not reachable yet, waiting startup... (${i}/${PRE_POLL_ATTEMPTS}, ${health.message})`
    );
    await sleep(PRE_POLL_GAP_MS);
    health = await getBridgeHealth();
  }

  if (health.reachable && health.mt5Ready) {
    // eslint-disable-next-line no-console
    atLog('[bridge-supervisor] bridge ready (reachable + mt5Ready=true)');
    return;
  }
  if (health.reachable && !health.mt5Ready) {
    // eslint-disable-next-line no-console
    console.warn(`[bridge-supervisor] bridge reachable but mt5 not ready: ${health.message}`);
    return;
  }

  if (!config.bridgeAutoStart) {
    // eslint-disable-next-line no-console
    console.warn(`[bridge-supervisor] bridge unreachable and auto-start disabled: ${health.message}`);
    return;
  }

  const commands = resolveBridgeCommands();
  if (commands.length === 0) {
    // eslint-disable-next-line no-console
    console.warn('[bridge-supervisor] no bridge command configured and bridge/mt5_bridge.py not found');
    return;
  }

  let launched = false;
  for (const cmd of commands) {
    if (startBridgeProcess(cmd)) {
      launched = true;
      break;
    }
  }
  if (!launched) {
    // eslint-disable-next-line no-console
    console.warn('[bridge-supervisor] failed to launch bridge process');
    return;
  }

  for (let i = 1; i <= 8; i += 1) {
    await sleep(1000);
    const state = await getBridgeHealth(2500);
    if (state.reachable) {
      if (state.mt5Ready) {
        // eslint-disable-next-line no-console
        atLog('[bridge-supervisor] bridge is online and mt5Ready=true');
      } else {
        // eslint-disable-next-line no-console
        console.warn(`[bridge-supervisor] bridge online but mt5Ready=false: ${state.message}`);
      }
      return;
    }
    // eslint-disable-next-line no-console
    console.warn(`[bridge-supervisor] waiting bridge startup... attempt ${i}/8 (${state.message})`);
  }

  // eslint-disable-next-line no-console
  console.warn('[bridge-supervisor] bridge still unreachable after startup attempts');
}

