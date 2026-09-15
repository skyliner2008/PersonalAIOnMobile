# ==============================================================================
# Build Rive Avatar Asset and Sync to ComposeApp Android Resources
#   1. regenerate scene.rml + presets.json from tools/build_scene.py
#   2. rive --verify, rive inspect (problems must be empty), Luau tests
#   3. compile the .riv and copy it to composeApp/src/androidMain/res/raw
# Any failing step stops the script BEFORE the Android asset is replaced.
# ==============================================================================
$ErrorActionPreference = "Stop"

$repoRoot = (Get-Item $PSScriptRoot).Parent.FullName
$riveProject = Join-Path $repoRoot "rive_avatar"

$riveExe = Join-Path $env:USERPROFILE ".rive\bin\rive.exe"
if (-not (Test-Path $riveExe)) {
    $cmd = Get-Command rive -ErrorAction SilentlyContinue
    $riveExe = if ($cmd) { $cmd.Source } else { $null }
}
if (-not $riveExe) {
    Write-Error "Rive CLI not found. Install it to ~/.rive/bin/rive.exe or put 'rive' on PATH."
}

# PowerShell 5.1 does not stop on a native exe's exit code -- check it by hand.
function Invoke-Step([string]$label, [scriptblock]$block) {
    Write-Host ">>> $label" -ForegroundColor Cyan
    & $block
    if ($LASTEXITCODE -ne 0) {
        Write-Error "$label failed (exit $LASTEXITCODE). Android asset NOT updated."
    }
}

Push-Location $riveProject
try {
    Invoke-Step "Generating scene.rml" { python tools\build_scene.py scene.rml }
    Invoke-Step "Verifying project" { & $riveExe . --verify }

    Write-Host ">>> Inspecting for problems" -ForegroundColor Cyan
    $inspect = & $riveExe inspect . --summary 2>$null | Out-String
    if ($LASTEXITCODE -ne 0) { Write-Error "rive inspect failed. Android asset NOT updated." }
    $problems = ($inspect | ConvertFrom-Json).problems
    if ($problems -and $problems.Count -gt 0) {
        Write-Error "rive inspect reported problems: $($problems | ConvertTo-Json -Compress)"
    }

    Invoke-Step "Running Luau tests" { & $riveExe . --test }
    Invoke-Step "Compiling .riv" { & $riveExe . --once }
}
finally {
    Pop-Location
}

$builtRiv = Join-Path $riveProject "build\rive_avatar.riv"
$targetRaw = Join-Path $repoRoot "composeApp\src\androidMain\res\raw"
if (-not (Test-Path $targetRaw)) {
    New-Item -ItemType Directory -Force -Path $targetRaw | Out-Null
}

$targetRiv = Join-Path $targetRaw "avatar.riv"
Copy-Item $builtRiv $targetRiv -Force

Write-Host ">>> Deployed: $targetRiv ($((Get-Item $targetRiv).Length) bytes)" -ForegroundColor Green
