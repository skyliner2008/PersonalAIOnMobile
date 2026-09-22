# ==============================================================================
# Launch Live Interactive 60fps Rive Avatar Preview on Windows
# ==============================================================================
$repoRoot = (Get-Item $PSScriptRoot).Parent.FullName
$riveProject = Join-Path $repoRoot "rive_avatar"
$riveExe = "C:\Users\JOJO\.rive\bin\rive.exe"
if (-not (Test-Path $riveExe)) {
    $cmd = Get-Command rive -ErrorAction SilentlyContinue
    if ($cmd) { $riveExe = $cmd.Source }
}

if (-not $riveExe) {
    Write-Error "Rive CLI executable not found. Ensure Rive CLI is installed at ~/.rive/bin/rive.exe."
}

Write-Host ">>> Launching Rive Avatar Live Interactive Window..." -ForegroundColor Cyan
Write-Host "  - Move mouse to test smooth gaze tracking" -ForegroundColor Gray
Write-Host "  - Press 's' to save screenshot" -ForegroundColor Gray
Write-Host "  - Press 'p' to pause/resume" -ForegroundColor Gray
Write-Host "  - Edit tools/build_scene.py, rerun it, and the window hot-reloads scene.rml" -ForegroundColor Gray

& $riveExe $riveProject