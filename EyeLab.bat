@echo off
rem LOOI Eye Lab - double-click to start the local page (server + browser).
rem Close the "LOOI Eye Lab server" window to stop it.
chcp 65001 >nul
setlocal
set "HERE=%~dp0"
set "SERVER=%HERE%rive_avatar\tools\eyelab\server.py"
set "URL=http://localhost:8765"

rem already running? just open the page
call :alive
if not errorlevel 1 (
  echo Eye Lab กำลังทำงานอยู่แล้ว - เปิดหน้าเว็บ
  start "" "%URL%"
  exit /b 0
)

rem find Python
set "PY="
where python >nul 2>nul && set "PY=python"
if not defined PY where py >nul 2>nul && set "PY=py -3"
if not defined PY goto nopython

echo กำลังเปิด LOOI Eye Lab ...
start "LOOI Eye Lab server" /min cmd /c "cd /d "%HERE%" && %PY% "%SERVER%" || pause"

rem wait up to ~30 s for the server, then open the browser
for /l %%i in (1,1,30) do (
  call :alive
  if not errorlevel 1 goto ready
  timeout /t 1 /nobreak >nul
)
echo เซิร์ฟเวอร์ไม่ตอบ - ดูข้อความในหน้าต่าง "LOOI Eye Lab server"
pause
exit /b 1

:nopython
echo ไม่พบ Python - ติดตั้งจาก https://www.python.org แล้วลองใหม่
pause
exit /b 1

:ready
start "" "%URL%"
exit /b 0

:alive
curl.exe -s -m 2 -o nul "http://127.0.0.1:8765/api/version" >nul 2>nul
exit /b %errorlevel%
