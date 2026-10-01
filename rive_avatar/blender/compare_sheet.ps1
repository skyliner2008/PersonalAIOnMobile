# Side-by-side check of the Blender robot against the user's model sheet.
# Top row: the four views cut from reference/robot_model_sheet_v2.jpg; bottom row: renders/robot_<view>.png
# (made by robot_build.py render_views). Output: renders/compare_sheet.png
param(
    [int[]]$RenderCrop = @(212, 130, 600, 750)   # x, y, w, h inside the 1024 px renders
)
$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing
$base = $PSScriptRoot
$ref = [Drawing.Image]::FromFile("$base\reference\robot_model_sheet_v2.jpg")
$sx = $ref.Width / 992.0
$sy = $ref.Height / 1067.0
# view boxes on the 992 x 1067 sheet
$refBoxes = @(@(92, 100, 200, 250), @(300, 100, 200, 250), @(505, 100, 200, 250), @(718, 100, 200, 250))
$names = "front", "side", "three_quarter", "back"
$W = 320; $H = 400
$out = New-Object Drawing.Bitmap ($W * 4), ($H * 2)
$g = [Drawing.Graphics]::FromImage($out)
$g.InterpolationMode = "HighQualityBicubic"
$g.Clear([Drawing.Color]::White)
for ($i = 0; $i -lt 4; $i++) {
    $rb = $refBoxes[$i]
    $src = New-Object Drawing.Rectangle ([int]($rb[0] * $sx)), ([int]($rb[1] * $sy)), ([int]($rb[2] * $sx)), ([int]($rb[3] * $sy))
    $g.DrawImage($ref, (New-Object Drawing.Rectangle ($i * $W), 0, $W, $H), $src, [Drawing.GraphicsUnit]::Pixel)
    $img = [Drawing.Image]::FromFile("$base\renders\robot_$($names[$i]).png")
    $crop = New-Object Drawing.Rectangle $RenderCrop[0], $RenderCrop[1], $RenderCrop[2], $RenderCrop[3]
    $g.DrawImage($img, (New-Object Drawing.Rectangle ($i * $W), $H, $W, $H), $crop, [Drawing.GraphicsUnit]::Pixel)
    $img.Dispose()
}
$out.Save("$base\renders\compare_sheet.png")
$g.Dispose(); $out.Dispose(); $ref.Dispose()
Write-Output "$base\renders\compare_sheet.png"
