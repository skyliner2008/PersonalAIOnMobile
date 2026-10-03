# Check the scripted scenes: play each one, save a frame every -Every seconds, and put the frames
# of each scene side by side in a contact sheet (<Out>\<id>_sheet.png, scene time under each frame).
#   .\tools\scene_frames.ps1 -Out C:\tmp\scenes [-Scenes idle_play,bored] [-Every 1.0] [-StopAt 8]
# -StopAt: stop each scene after that many seconds (looping scenes such as listening never end).
param(
    [Parameter(Mandatory)] [string] $Out,
    [string[]] $Scenes = @(),
    [double] $Every = 1.0,
    [double] $StopAt = 0,
    [int] $TimeoutSec = 45,
    [string] $Godot = "C:\Godot\Godot_v4.7.2-stable_win64_console.exe"
)
$ErrorActionPreference = "Stop"
$project = Split-Path -Parent $PSScriptRoot
New-Item -ItemType Directory -Force $Out | Out-Null
if (-not $Scenes.Count) {
    $Scenes = Get-ChildItem (Join-Path $project "scenes") -Filter *.json | ForEach-Object { $_.BaseName }
}
Add-Type -AssemblyName System.Drawing
foreach ($id in $Scenes) {
    $dir = Join-Path $Out $id
    if (Test-Path $dir) { Get-ChildItem $dir -Filter *.png | Remove-Item }
    $args = @("--path", $project, "--", "--scene=$id", "--frames=$dir", "--every=$Every", "--hide-ui", "--quit-at-end")
    if ($StopAt -gt 0) { $args += "--stop-at=$StopAt" }
    $p = Start-Process -FilePath $Godot -ArgumentList $args -PassThru -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $Out "$id.log") -RedirectStandardError (Join-Path $Out "$id.err")
    if (-not $p.WaitForExit($TimeoutSec * 1000)) { $p.Kill(); Write-Output "timeout (killed): $id" }
    $frames = @(Get-ChildItem $dir -Filter *.png -ErrorAction SilentlyContinue | Sort-Object Name)
    $errs = @(Get-Content (Join-Path $Out "$id.err") -ErrorAction SilentlyContinue | Where-Object { $_ -match "ERROR" })
    Write-Output ("{0}: {1} frames, {2} error lines" -f $id, $frames.Count, $errs.Count)
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
    $sheet = Join-Path $Out "${id}_sheet.png"
    $bmp.Save($sheet); $g.Dispose(); $bmp.Dispose()
    Write-Output $sheet
}
