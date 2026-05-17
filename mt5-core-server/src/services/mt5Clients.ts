import fs from 'fs/promises';
import type { Dirent } from 'fs';
import path from 'path';
import { spawn } from 'child_process';
import os from 'os';

type RunningProcess = {
  pid: number;
  name: string;
  executablePath: string;
};

export type Mt5ClientInfo = {
  id: string;
  name: string;
  exePath: string;
  running: boolean;
  pids: number[];
};

const MT5_EXECUTABLE_NAMES = new Set(['terminal64.exe', 'terminal.exe']);

export async function listMt5Clients(): Promise<Mt5ClientInfo[]> {
  let installed: any[] = [];
  try {
    installed = await discoverMt5Installations();
  } catch (err) {
    // eslint-disable-next-line no-console
    console.error(`[mt5-clients] discovery failed: ${err}`);
  }

  let running: any[] = [];
  try {
    running = await listRunningMt5Processes();
  } catch (err) {
    // eslint-disable-next-line no-console
    console.error(`[mt5-clients] process listing failed: ${err}`);
  }

  const runningByPath = new Map<string, number[]>();
  for (const proc of running) {
    const key = normalizeWinPath(proc.executablePath);
    const list = runningByPath.get(key) || [];
    list.push(proc.pid);
    runningByPath.set(key, list);
  }

  const merged: Mt5ClientInfo[] = installed.map((item) => {
    const key = normalizeWinPath(item.exePath);
    const pids = runningByPath.get(key) || [];
    return {
      ...item,
      running: pids.length > 0,
      pids,
    };
  });

  for (const proc of running) {
    const key = normalizeWinPath(proc.executablePath);
    if (merged.some((m) => normalizeWinPath(m.exePath) === key)) continue;
    merged.push({
      id: makeClientId(proc.executablePath),
      name: proc.name || path.basename(proc.executablePath, path.extname(proc.executablePath)),
      exePath: proc.executablePath,
      running: true,
      pids: [proc.pid],
    });
  }

  return merged.sort((a, b) => Number(b.running) - Number(a.running) || a.name.localeCompare(b.name));
}

export async function startMt5Client(exePath: string): Promise<{ started: boolean; message: string }> {
  if (!exePath || !exePath.toLowerCase().endsWith('.exe')) {
    throw new Error('Invalid MT5 executable path');
  }

  await fs.access(exePath);

  await new Promise<void>((resolve, reject) => {
    try {
      const child = spawn(exePath, [], {
        detached: true,
        stdio: 'ignore',
        windowsHide: false,
      });
      child.on('error', reject);
      child.unref();
      resolve();
    } catch (err) {
      reject(err);
    }
  });

  return { started: true, message: `Started: ${exePath}` };
}

export async function stopMt5ByPid(pid: number): Promise<{ stopped: boolean; message: string }> {
  if (!Number.isFinite(pid) || pid <= 0) {
    throw new Error('Invalid PID');
  }

  await execPowerShell(`Stop-Process -Id ${pid} -Force`);
  return { stopped: true, message: `Stopped PID ${pid}` };
}

async function discoverMt5Installations(): Promise<Mt5ClientInfo[]> {
  const roots = getScanRoots();
  const found = new Map<string, Mt5ClientInfo>();

  for (const root of roots) {
    const candidates = await findExecutableCandidates(root, 4);
    for (const exePath of candidates) {
      const key = normalizeWinPath(exePath);
      if (found.has(key)) continue;

      const folderName = path.basename(path.dirname(exePath));
      const clientName = prettyClientName(folderName, exePath);
      found.set(key, {
        id: makeClientId(exePath),
        name: clientName,
        exePath,
        running: false,
        pids: [],
      });
    }
  }

  return Array.from(found.values());
}

async function findExecutableCandidates(root: string, maxDepth: number): Promise<string[]> {
  const out: string[] = [];
  await walk(root, 0, maxDepth, out);
  return out;
}

async function walk(dir: string, depth: number, maxDepth: number, out: string[]): Promise<void> {
  if (depth > maxDepth) return;

  let entries: Dirent[];
  try {
    entries = await fs.readdir(dir, { withFileTypes: true });
  } catch {
    return;
  }

  for (const entry of entries) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      if (isLikelyHeavyFolder(entry.name)) continue;
      await walk(full, depth + 1, maxDepth, out);
      continue;
    }

    if (!entry.isFile()) continue;
    const lower = entry.name.toLowerCase();
    if (MT5_EXECUTABLE_NAMES.has(lower)) {
      out.push(full);
    }
  }
}

function isLikelyHeavyFolder(name: string): boolean {
  const lower = name.toLowerCase();
  return lower === 'windows' || lower === 'winsxs' || lower === '$recycle.bin' || lower === 'system volume information';
}

function getScanRoots(): string[] {
  const fromEnv = process.env.MT5_SCAN_ROOTS
    ?.split(';')
    .map((x) => x.trim())
    .filter(Boolean);
  if (fromEnv && fromEnv.length > 0) return fromEnv;

  const roots = new Set<string>();
  roots.add(path.join(process.env['ProgramFiles'] || 'C:\\Program Files'));
  roots.add(path.join(process.env['ProgramFiles(x86)'] || 'C:\\Program Files (x86)'));

  const localAppData = process.env.LOCALAPPDATA;
  if (localAppData) roots.add(path.join(localAppData, 'Programs'));

  if (os.platform() === 'win32') {
    roots.add('C:\\');
  }

  return Array.from(roots);
}

async function listRunningMt5Processes(): Promise<RunningProcess[]> {
  if (os.platform() !== 'win32') return [];

  const command = "$ErrorActionPreference='SilentlyContinue'; Get-CimInstance Win32_Process | Where-Object { $_.Name -eq 'terminal64.exe' -or $_.Name -eq 'terminal.exe' } | Select-Object ProcessId, Name, ExecutablePath | ConvertTo-Json -Compress";
  const raw = await execPowerShell(command);
  if (!raw.trim()) return [];

  let parsed: any;
  try {
    parsed = JSON.parse(raw);
  } catch {
    return [];
  }

  const rows = Array.isArray(parsed) ? parsed : [parsed];
  return rows
    .map((r) => ({
      pid: Number(r?.ProcessId || 0),
      name: String(r?.Name || ''),
      executablePath: String(r?.ExecutablePath || ''),
    }))
    .filter((r) => r.pid > 0 && r.executablePath);
}

function prettyClientName(folderName: string, exePath: string): string {
  const base = folderName || path.basename(path.dirname(exePath));
  return base.replace(/[_-]+/g, ' ').trim() || 'MetaTrader 5';
}

function makeClientId(exePath: string): string {
  return Buffer.from(normalizeWinPath(exePath)).toString('base64url').slice(0, 24);
}

function normalizeWinPath(p: string): string {
  return String(p || '').replace(/\//g, '\\\\').toLowerCase();
}

async function execPowerShell(psCommand: string): Promise<string> {
  return await new Promise<string>((resolve, reject) => {
    const child = spawn('powershell.exe', ['-NoProfile', '-Command', psCommand], {
      windowsHide: true,
      stdio: ['ignore', 'pipe', 'pipe'],
    });

    let stdout = '';
    let stderr = '';

    child.stdout.on('data', (d) => {
      stdout += d.toString();
    });
    child.stderr.on('data', (d) => {
      stderr += d.toString();
    });

    child.on('error', reject);
    child.on('close', (code) => {
      if (code === 0) return resolve(stdout.trim());
      reject(new Error(stderr.trim() || `PowerShell exited with code ${code}`));
    });
  });
}

