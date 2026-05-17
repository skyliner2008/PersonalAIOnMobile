@echo off
echo =======================================================
echo Starting LiteLLM Proxy Server for Gemini
echo =======================================================
echo.


:: รัน LiteLLM Proxy (พอร์ตเริ่มต้นคือ 4000)
$env:PYTHONUTF8=1
& "C:\Users\JOJO\AppData\Roaming\Python\Python313\Scripts\litellm.exe" --config "C:\Users\JOJO\config.yaml"

pause
