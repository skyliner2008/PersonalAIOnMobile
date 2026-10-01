# Record the scripted scenes as videos: Godot's movie writer (fixed 30 fps, so timing is exact
# whatever the PC's speed) writes <Out>\<id>.avi, then Blender turns each into a small <id>.mp4
# and, with -Reel, joins them all into <Out>\all_scenes.mp4.
#   .\tools\record_scenes.ps1 -Out C:\tmp\clips [-Scenes angry,love] [-Size 576x1024] [-Reel]
# -Size writes a temporary override.cfg (the project's window override would otherwise win over
# --resolution); it is removed again when recording ends. The window must fit on the screen
# (borderless, height <= the work area, 1040 on a 1080p screen): Windows shrinks a taller window,
# and then the 3D view and the caption / bubble layer no longer line up in the video.
param(
    [Parameter(Mandatory)] [string] $Out,
    [string[]] $Scenes = @(),
    [string] $Size = "576x1024",
    [switch] $Reel,
    [switch] $KeepAvi,
    [int] $TimeoutSec = 120,
    [string] $Godot = "C:\Godot\Godot_v4.7.2-stable_win64_console.exe",
    [string] $Blender = "C:\Program Files\Blender Foundation\Blender 5.2\blender.exe"
)
$ErrorActionPreference = "Stop"
$project = Split-Path -Parent $PSScriptRoot
New-Item -ItemType Directory -Force $Out | Out-Null
if (-not $Scenes.Count) {
    $Scenes = Get-ChildItem (Join-Path $project "scenes") -Filter *.json | ForEach-Object { $_.BaseName }
}
$override = Join-Path $project "override.cfg"
$w, $h = $Size -split "x"
[IO.File]::WriteAllText($override, "[display]`n`nwindow/size/window_width_override=$w`nwindow/size/window_height_override=$h`nwindow/size/borderless=true`n")
$clips = @()
try {
    foreach ($id in $Scenes) {
        $avi = Join-Path $Out "$id.avi"
        if (Test-Path $avi) { Remove-Item $avi }
        $args = @("--path", $project, "--write-movie", $avi, "--fixed-fps", "30", "--", "--scene=$id", "--hide-ui", "--quit-at-end")
        $p = Start-Process -FilePath $Godot -ArgumentList $args -PassThru -WindowStyle Hidden `
            -RedirectStandardOutput (Join-Path $Out "$id.log") -RedirectStandardError (Join-Path $Out "$id.err")
        if (-not $p.WaitForExit($TimeoutSec * 1000)) { $p.Kill(); Write-Output "timeout (killed): $id" }
        if (Test-Path $avi) { $clips += $avi; Write-Output ("{0}: {1:N1} MB" -f $id, ((Get-Item $avi).Length / 1MB)) }
        else { Write-Output "no video: $id" }
    }
} finally {
    Remove-Item $override -ErrorAction SilentlyContinue
}
if ($clips.Count) {
    # Blender writes warnings to stderr; run it as a separate process so they don't stop this script
    $bargs = @("-b", "--factory-startup", "--python", "`"$(Join-Path $PSScriptRoot 'avi_to_mp4.py')`"", "--") + ($clips | ForEach-Object { "`"$_`"" })
    if ($Reel) { $bargs += @("--reel", "`"$(Join-Path $Out 'all_scenes.mp4')`"") }
    $log = Join-Path $Out "blender.log"
    $b = Start-Process -FilePath $Blender -ArgumentList $bargs -PassThru -Wait -WindowStyle Hidden `
        -RedirectStandardOutput $log -RedirectStandardError (Join-Path $Out "blender.err")
    Get-Content $log | Where-Object { $_ -match "^MP4|Error" }
    $mp4 = @($clips | Where-Object { Test-Path ([IO.Path]::ChangeExtension($_, ".mp4")) })
    if (-not $KeepAvi -and $mp4.Count -eq $clips.Count) { $clips | Remove-Item }
}
