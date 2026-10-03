# Check how tool results look on the robot: show each saved result the way the app does
# (director.play_tool(tool, result)), save a frame every -Every seconds and put them in a contact sheet
# (<Out>\<tool>_sheet.png, scene time under each frame).
#   .\tools\tool_frames.ps1 -Data C:\tmp\samples -Out C:\tmp\tools [-Tools trading_price,trading_news] [-Every 1.0]
# -Resolution: the window (default the canvas itself, 720x1280: a smaller window draws Thai a few % wider
#   than it is laid out, so text looks cut at the edges in the shots but not on a phone)
# -Data: a folder of <tool>.json files, e.g. from the app's live dump
#   (composeApp AvatarSceneDataLiveDump: RUN_LIVE_TESTS=1 SCENE_SAMPLES_DIR=<dir>). Without -Tools: every file there.
param(
    [Parameter(Mandatory)] [string] $Data,
    [Parameter(Mandatory)] [string] $Out,
    [string[]] $Tools = @(),
    [double] $Every = 1.0,
    [string] $Resolution = "720x1280",
    [int] $TimeoutSec = 45,
    [string] $Godot = "C:\Godot\Godot_v4.7.2-stable_win64_console.exe"
)
$ErrorActionPreference = "Stop"
$project = Split-Path -Parent $PSScriptRoot
New-Item -ItemType Directory -Force $Out | Out-Null
if (-not $Tools.Count) {
    $Tools = Get-ChildItem $Data -Filter *.json | ForEach-Object { $_.BaseName }
}
Add-Type -AssemblyName System.Drawing
foreach ($tool in $Tools) {
    $file = Join-Path $Data "$tool.json"
    if (-not (Test-Path $file)) { Write-Output "no data: $tool"; continue }
    $dir = Join-Path $Out $tool
    if (Test-Path $dir) { Get-ChildItem $dir -Filter *.png | Remove-Item }
    $args = @("--path", $project, "--resolution", $Resolution, "--", "--tool=$tool", "--data=$file", "--frames=$dir", "--every=$Every", "--hide-ui", "--quit-at-end")
    $p = Start-Process -FilePath $Godot -ArgumentList $args -PassThru -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $Out "$tool.log") -RedirectStandardError (Join-Path $Out "$tool.err")
    if (-not $p.WaitForExit($TimeoutSec * 1000)) { $p.Kill(); Write-Output "timeout (killed): $tool" }
    $frames = @(Get-ChildItem $dir -Filter *.png -ErrorAction SilentlyContinue | Sort-Object Name)
    $errs = @(Get-Content (Join-Path $Out "$tool.err") -ErrorAction SilentlyContinue | Where-Object { $_ -match "ERROR|SCRIPT" })
    Write-Output ("{0}: {1} frames, {2} error lines" -f $tool, $frames.Count, $errs.Count)
    if (-not $frames.Count) { continue }
    $W = 180; $H = 320; $cols = [Math]::Min(5, $frames.Count); $rows = [Math]::Ceiling($frames.Count / $cols)
    $bmp = New-Object Drawing.Bitmap ($W * $cols), ($H * $rows)
    $g = [Drawing.Graphics]::FromImage($bmp); $g.InterpolationMode = "HighQualityBicubic"
    $font = New-Object Drawing.Font "Segoe UI", 11, ([Drawing.FontStyle]::Bold)
    for ($i = 0; $i -lt $frames.Count; $i++) {
        $img = [Drawing.Image]::FromFile($frames[$i].FullName)
        $x = ($i % $cols) * $W; $y = [Math]::Floor($i / $cols) * $H
        $g.DrawImage($img, (New-Object Drawing.Rectangle $x, $y, $W, $H))
        $img.Dispose()
        $g.FillRectangle([Drawing.Brushes]::Black, $x, ($y + $H - 22), 52, 22)
        $g.DrawString(("{0:0.0}s" -f (($i + 0.5) * $Every)), $font, [Drawing.Brushes]::White, ($x + 4), ($y + $H - 21))
    }
    $sheet = Join-Path $Out "${tool}_sheet.png"
    $bmp.Save($sheet); $g.Dispose(); $bmp.Dispose()
    Write-Output $sheet
}
