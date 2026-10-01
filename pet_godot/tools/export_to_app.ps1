# Packs the Godot robot into the JARVIS app (Avatar mode). Run after changing anything in pet_godot/:
#
#   powershell -ExecutionPolicy Bypass -File pet_godot\tools\export_to_app.ps1 [-SkipSprites]
#
# 1. composeApp/libs/godot-lib.template_release.aar  <- Godot's Android library from the 4.7.2 export
#    templates on this PC (only if missing; too big for git, Gradle falls back to Maven Central without it)
# 2. composeApp/src/commonMain/composeResources/files/robot/  <- the robot's faces for the overlay bubble
#    and the Drive screen, and pet_godot/assets/boot_splash.png (main.gd --sprites)
# 3. composeApp/src/androidMain/godot-assets/        <- the exported project (the Android preset's APK,
#    its assets/ folder without the _cl_ command line): the engine reads it from the app's assets
param(
    [string]$Godot = "C:\Godot\Godot_v4.7.2-stable_win64_console.exe",
    [switch]$SkipSprites
)
$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.IO.Compression.FileSystem

$lab = Split-Path -Parent $PSScriptRoot                       # pet_godot
$repo = Split-Path -Parent $lab
$app = Join-Path $repo "composeApp"

# 1. the engine library
$aar = Join-Path $app "libs\godot-lib.template_release.aar"
if (-not (Test-Path $aar)) {
    $templates = Join-Path $env:APPDATA "Godot\export_templates\4.7.2.stable\android_source.zip"
    if (-not (Test-Path $templates)) { throw "Godot 4.7.2 Android export templates not found: $templates" }
    New-Item -ItemType Directory -Force (Split-Path $aar) | Out-Null
    $zip = [System.IO.Compression.ZipFile]::OpenRead($templates)
    try {
        $entry = $zip.Entries | Where-Object { $_.FullName -eq "libs/release/godot-lib.template_release.aar" }
        [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $aar, $true)
    } finally { $zip.Dispose() }
    Write-Output "engine library -> $aar"
}

# 2. the faces (and the engine's boot splash, which the export below takes along)
if (-not $SkipSprites) {
    $faces = Join-Path $app "src\commonMain\composeResources\files\robot"
    if (Test-Path $faces) { Remove-Item -Recurse -Force $faces }
    New-Item -ItemType Directory -Force $faces | Out-Null
    $p = Start-Process -FilePath $Godot -ArgumentList @("--path", $lab, "--", "--sprites=$faces") -NoNewWindow -PassThru
    if (-not $p.WaitForExit(180000)) { Stop-Process -Id $p.Id -Force; throw "sprite render timed out" }
    Write-Output ("faces -> $faces (" + (Get-ChildItem $faces -Filter *.webp).Count + " files)")
}

# 3. the exported project
$apk = Join-Path $lab "build\PetLab.apk"
New-Item -ItemType Directory -Force (Split-Path $apk) | Out-Null
& $Godot --headless --path $lab --export-debug Android $apk 2>&1 | Out-Null
if (-not (Test-Path $apk)) { throw "export failed: $apk" }
$assets = Join-Path $app "src\androidMain\godot-assets"
if (Test-Path $assets) { Remove-Item -Recurse -Force $assets }
New-Item -ItemType Directory -Force $assets | Out-Null
$zip = [System.IO.Compression.ZipFile]::OpenRead($apk)
$count = 0
try {
    foreach ($e in $zip.Entries) {
        if (-not $e.FullName.StartsWith("assets/") -or $e.FullName.EndsWith("/")) { continue }
        $rel = $e.FullName.Substring(7)
        if ($rel -eq "_cl_") { continue }                   # the lab APK's command line, not the app's
        $out = Join-Path $assets ($rel -replace '/', '\')
        New-Item -ItemType Directory -Force (Split-Path $out) | Out-Null
        [System.IO.Compression.ZipFileExtensions]::ExtractToFile($e, $out, $true)
        $count++
    }
} finally { $zip.Dispose() }
Write-Output "project -> $assets ($count files)"
