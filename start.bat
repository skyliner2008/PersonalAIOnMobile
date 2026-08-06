@echo off
setlocal enabledelayedexpansion
chcp 65001 >nul
cd /d "%~dp0"

echo.
echo ========================================================
echo   JARVIS MT5 - Professional AI Trading Platform
echo   One-Click Start
echo ========================================================
echo.

echo [INFO] Starting MT5 Core Server...
start "MT5 Core Server" cmd /k "cd mt5-core-server && start.bat"

timeout /t 5 >nul

echo [INFO] Starting React Dashboard Frontend...
start "React Dashboard Frontend" cmd /k "cd dashboard-frontend && npm run dev"

echo.
echo ========================================================
echo   All systems are booting up in separate windows.
echo   - Backend Server running on port 8090
echo   - Frontend Dashboard running on port 5173
echo ========================================================
echo.

pause
