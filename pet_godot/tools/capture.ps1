# Take lab screenshots from the command line, one Godot run per shot, with a timeout
# (a run that saved its shot but hangs on exit is killed).
#   .\tools\capture.ps1 -Out C:\tmp\shots -Shots @(
#       @{ name = "fire"; state = "normal,cannon_R,city,fire_R"; wait = 0.95; turn = 0 }, ...) [-Sheet sheet.png]
param(
    [Parameter(Mandatory)] [string] $Out,
    [Parameter(Mandatory)] [object[]] $Shots,
    [string] $Sheet = "",
    [int] $TimeoutSec = 40,
    [string] $Godot = "C:\Godot\Godot_v4.7.2-stable_win64_console.exe"
)
$ErrorActionPreference = "Stop"
$project = Split-Path -Parent $PSScriptRoot
New-Item -ItemType Directory -Force $Out | Out-Null
$files = @()
foreach ($s in $Shots) {
    $png = Join-Path $Out "$($s.name).png"
    $args = @("--path", $project, "--", "--shot=$png", "--state=$($s.state)", "--wait=$($s.wait)", "--hide-ui")
    if ($s.turn) { $args += "--turn=$($s.turn)" }
    $p = Start-Process -FilePath $Godot -ArgumentList $args -PassThru -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $Out "$($s.name).log") -RedirectStandardError (Join-Path $Out "$($s.name).err")
    if (-not $p.WaitForExit($TimeoutSec * 1000)) { $p.Kill(); Write-Output "timeout (killed): $($s.name)" }
    if (Test-Path $png) { $files += $png } else { Write-Output "no shot: $($s.name)" }
}
if ($Sheet -and $files.Count) {
    Add-Type -AssemblyName System.Drawing
    $W = 225; $H = 400; $cols = [Math]::Min(4, $files.Count); $rows = [Math]::Ceiling($files.Count / $cols)
    $bmp = New-Object Drawing.Bitmap ($W * $cols), ($H * $rows)
    $g = [Drawing.Graphics]::FromImage($bmp); $g.InterpolationMode = "HighQualityBicubic"
    for ($i = 0; $i -lt $files.Count; $i++) {
        $img = [Drawing.Image]::FromFile($files[$i])
        $g.DrawImage($img, (New-Object Drawing.Rectangle (($i % $cols) * $W), ([Math]::Floor($i / $cols) * $H), $W, $H))
        $img.Dispose()
    }
    $bmp.Save($Sheet); $g.Dispose(); $bmp.Dispose()
    Write-Output $Sheet
}
