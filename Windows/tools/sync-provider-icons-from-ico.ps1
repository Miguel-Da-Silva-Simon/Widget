# Genera WidgetIcon/Small/Large desde src/icons/*.ico y luego WidgetMedium (galería).
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$repoRoot = Join-Path $PSScriptRoot '..'
$iconsDir = Join-Path $repoRoot 'src\icons'
$icoPath = (Get-ChildItem -Path $iconsDir -Filter '*.ico' -ErrorAction SilentlyContinue | Select-Object -First 1).FullName
if (-not $icoPath) {
    Write-Error "No hay ningún .ico en: $iconsDir"
}

$assetsDir = Join-Path $repoRoot 'src\Widget.TimeTracking.Package\ProviderAssets'
New-Item -ItemType Directory -Force -Path $assetsDir | Out-Null

function Save-IconToPng {
    param(
        [string] $OutPath,
        [int] $CanvasW,
        [int] $CanvasH
    )
    # Image.FromFile carga .ico en Windows (GDI+).
    $src = [System.Drawing.Image]::FromFile((Resolve-Path $icoPath))
    try {
        $fmt = [System.Drawing.Imaging.PixelFormat]::Format32bppArgb
        $bmp = New-Object System.Drawing.Bitmap($CanvasW, $CanvasH, $fmt)
        try {
            $g = [System.Drawing.Graphics]::FromImage($bmp)
            try {
                $g.Clear([System.Drawing.Color]::Transparent)
                $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
                $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
                $scale = [Math]::Min($CanvasW / $src.Width, $CanvasH / $src.Height)
                $drawW = [int]($src.Width * $scale)
                $drawH = [int]($src.Height * $scale)
                $x = [int](($CanvasW - $drawW) / 2)
                $y = [int](($CanvasH - $drawH) / 2)
                $g.DrawImage($src, $x, $y, $drawW, $drawH)
            } finally {
                $g.Dispose()
            }
            $bmp.Save($OutPath, [System.Drawing.Imaging.ImageFormat]::Png)
        } finally {
            $bmp.Dispose()
        }
    } finally {
        $src.Dispose()
    }
}

# Mismas dimensiones que los assets anteriores del paquete
Save-IconToPng -OutPath (Join-Path $assetsDir 'WidgetIcon.png') -CanvasW 256 -CanvasH 204
Save-IconToPng -OutPath (Join-Path $assetsDir 'WidgetSmall.png') -CanvasW 256 -CanvasH 204
Save-IconToPng -OutPath (Join-Path $assetsDir 'WidgetLarge.png') -CanvasW 256 -CanvasH 204
Write-Host "Actualizado WidgetIcon/Small/Large desde: $icoPath"

& (Join-Path $PSScriptRoot 'generate-widget-picker-assets.ps1')
