@echo off
setlocal enabledelayedexpansion
chcp 65001 >nul
cd /d "%~dp0"

set "MODE=dev"
for %%A in (%*) do (
  if /I "%%~A"=="--prod" set "MODE=prod"
)

echo.
echo +------------------------------------------------------+
echo ^| MT5 Core Server Startup                             ^|
echo ^| Path: %cd%                                           ^|
echo +------------------------------------------------------+
echo.

where node >nul 2>&1
if errorlevel 1 (
  echo [ERROR] Node.js not found in PATH
  pause
  exit /b 1
)

where npm >nul 2>&1
if errorlevel 1 (
  echo [ERROR] npm not found in PATH
  pause
  exit /b 1
)

if not exist "package.json" (
  echo [ERROR] package.json not found. Run this file inside mt5-core-server folder.
  pause
  exit /b 1
)

if not exist ".env" (
  if exist ".env.example" (
    echo [INFO] .env not found, creating from .env.example
    copy /Y ".env.example" ".env" >nul
  ) else (
    echo [WARN] .env and .env.example are missing.
  )
)

if not exist "node_modules" (
  echo [INFO] Installing dependencies...
  call npm install
  if errorlevel 1 (
    echo [ERROR] npm install failed
    pause
    exit /b 1
  )
)

set "PYTHON_CMD="
where python >nul 2>&1
if not errorlevel 1 set "PYTHON_CMD=python"
if not defined PYTHON_CMD (
  where py >nul 2>&1
  if not errorlevel 1 set "PYTHON_CMD=py -3"
)

if exist "bridge\\mt5_bridge.py" (
  if defined PYTHON_CMD (
    for /f "usebackq delims=" %%R in (`powershell -NoProfile -Command "$ok='0'; try { & %PYTHON_CMD% -c \"import MetaTrader5\" | Out-Null; $ok='1' } catch { $ok='0' }; $ok"`) do (
      set "MT5_PY_READY=%%R"
    )
    if not "!MT5_PY_READY!"=="1" (
      echo [INFO] Installing Python bridge dependency: MetaTrader5
      call %PYTHON_CMD% -m pip install MetaTrader5
      if errorlevel 1 (
        echo [WARN] Failed to install MetaTrader5 package. Bridge may not be ready.
      )
    )
  ) else (
    echo [WARN] Python not found. bridge\\mt5_bridge.py cannot run.
  )
)

set "PORT=8090"
set "MT5_BRIDGE_URL="
set "BRIDGE_START_CMD="
for /f "usebackq delims=" %%P in (`powershell -NoProfile -Command "$v=''; if (Test-Path '.env') { $v=(Get-Content '.env' | Select-String -Pattern '^PORT=' | Select-Object -Last 1).Line }; if ($v) { $v.Split('=')[1].Trim() }"`) do (
  if not "%%P"=="" set "PORT=%%P"
)
for /f "usebackq delims=" %%P in (`powershell -NoProfile -Command "$v=''; if (Test-Path '.env') { $v=(Get-Content '.env' | Select-String -Pattern '^MT5_BRIDGE_URL=' | Select-Object -Last 1).Line }; if ($v) { $v.Substring('MT5_BRIDGE_URL='.Length).Trim() }"`) do (
  if not "%%P"=="" set "MT5_BRIDGE_URL=%%P"
)
for /f "usebackq delims=" %%P in (`powershell -NoProfile -Command "$v=''; if (Test-Path '.env') { $v=(Get-Content '.env' | Select-String -Pattern '^BRIDGE_START_CMD=' | Select-Object -Last 1).Line }; if ($v) { $v.Substring('BRIDGE_START_CMD='.Length).Trim() }"`) do (
  set "BRIDGE_START_CMD=%%P"
)
if defined BRIDGE_START_CMD (
  if "!BRIDGE_START_CMD:~0,1!"=="\"" if "!BRIDGE_START_CMD:~-1!"=="\"" set "BRIDGE_START_CMD=!BRIDGE_START_CMD:~1,-1!"
)
if not defined BRIDGE_START_CMD (
  if exist "bridge\\mt5_bridge.py" (
    set "BRIDGE_START_CMD=python bridge\\mt5_bridge.py"
  )
)

if not "%MT5_BRIDGE_URL%"=="" (
  set "BRIDGE_HEALTH_URL=%MT5_BRIDGE_URL%/health"
  for /f "usebackq delims=" %%H in (`powershell -NoProfile -Command "$u='%BRIDGE_HEALTH_URL%'; try { $r=Invoke-WebRequest -UseBasicParsing -Uri $u -TimeoutSec 2; if ($r.StatusCode -ge 200 -and $r.StatusCode -lt 500) { 'ok' } else { 'down' } } catch { 'down' }"`) do (
    set "BRIDGE_STATE=%%H"
  )
  if /I not "!BRIDGE_STATE!"=="ok" (
    echo [WARN] MT5 bridge not reachable at %MT5_BRIDGE_URL%
    if not "%BRIDGE_START_CMD%"=="" (
      echo [INFO] Starting bridge with BRIDGE_START_CMD...
      start "mt5-bridge (launcher)" cmd /c %BRIDGE_START_CMD%
      rem Tell Node bridgeSupervisor NOT to spawn another Python process —
      rem start.bat already launched one. Supervisor will only poll/wait.
      set "BRIDGE_AUTO_START=0"
      timeout /t 3 >nul
      for /f "usebackq delims=" %%H in (`powershell -NoProfile -Command "$u='%BRIDGE_HEALTH_URL%'; try { $r=Invoke-WebRequest -UseBasicParsing -Uri $u -TimeoutSec 2; if ($r.StatusCode -ge 200 -and $r.StatusCode -lt 500) { 'ok' } else { 'down' } } catch { 'down' }"`) do (
        set "BRIDGE_STATE=%%H"
      )
      if /I not "!BRIDGE_STATE!"=="ok" (
        echo [WARN] Bridge still not reachable. Core server will start, but MT5 routes may return 502.
      ) else (
        echo [INFO] Bridge is reachable.
      )
    ) else (
      echo [WARN] BRIDGE_START_CMD is not set in .env
      echo [WARN] Please start your bridge manually or set BRIDGE_START_CMD.
    )
  ) else (
    echo [INFO] Bridge reachable at %MT5_BRIDGE_URL%
  )
)

set "PORT_PID="
for /f "usebackq delims=" %%P in (`powershell -NoProfile -Command "$c=Get-NetTCPConnection -LocalPort %PORT% -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1; if ($c) { $c.OwningProcess }"`) do (
  set "PORT_PID=%%P"
)

if defined PORT_PID (
  set "PORT_PROC="
  for /f "usebackq delims=" %%N in (`powershell -NoProfile -Command "$p=Get-Process -Id !PORT_PID! -ErrorAction SilentlyContinue; if ($p) { $p.ProcessName }"`) do (
    set "PORT_PROC=%%N"
  )
  echo [INFO] Port %PORT% is already in use by PID !PORT_PID! !PORT_PROC!
  echo [INFO] If needed: taskkill /PID !PORT_PID! /F
  pause
  exit /b 0
)

if /I "%MODE%"=="prod" (
  echo [1/2] Building TypeScript...
  call npm run build
  if errorlevel 1 (
    echo [ERROR] Build failed
    pause
    exit /b 1
  )
  echo [2/2] Starting production server...
  echo [INFO] URL: http://localhost:%PORT%
  call npm run start
) else (
  echo [1/1] Starting development server...
  echo [INFO] URL: http://localhost:%PORT%
  call npm run dev
)
