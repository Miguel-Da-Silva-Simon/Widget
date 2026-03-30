# Genera WidgetMedium.png (300x304) para la galería de widgets según documentación de Microsoft.
# Requiere: ProviderAssets\WidgetIcon.png como fuente.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$packageRoot = Join-Path $PSScriptRoot '..\src\Widget.TimeTracking.Package'
$assetsDir = Join-Path $packageRoot 'ProviderAssets'
$iconPath = Join-Path $assetsDir 'WidgetIcon.png'
$outPath = Join-Path $assetsDir 'WidgetMedium.png'

if (-not (Test-Path $iconPath)) {
    Write-Error "No existe: $iconPath"
}

$w = 300
$h = 304
$src = [System.Drawing.Image]::FromFile((Resolve-Path $iconPath))
try {
    $bmp = New-Object System.Drawing.Bitmap $w, $h
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    try {
        $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
        $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $g.Clear([System.Drawing.Color]::FromArgb(255, 245, 245, 250))

        $pad = 40
        $innerW = $w - 2 * $pad
        $innerH = $h - 2 * $pad
        $scale = [Math]::Min($innerW / $src.Width, $innerH / $src.Height)
        $drawW = [int]($src.Width * $scale)
        $drawH = [int]($src.Height * $scale)
        $x = [int](($w - $drawW) / 2)
        $y = [int](($h - $drawH) / 2)
        $g.DrawImage($src, $x, $y, $drawW, $drawH)
    } finally {
        $g.Dispose()
    }
    $bmp.Save($outPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    Write-Host "Escrito $outPath (${w}x${h})"
} finally {
    $src.Dispose()
}
