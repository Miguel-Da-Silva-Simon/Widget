$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$src = 'C:\Users\Practicas\.cursor\projects\c-Users-Practicas-Documents-Widget\assets\c__Users_Practicas_AppData_Roaming_Cursor_User_workspaceStorage_989ba34c1cf0600df8da72c18ed7bdcf_images_image-e4b88cdc-3016-439e-9cd6-fec22a6f628e.png'
if (-not (Test-Path $src)) {
    $alt = Join-Path (Split-Path $PSScriptRoot -Parent) 'assets\c__Users_Practicas_AppData_Roaming_Cursor_User_workspaceStorage_989ba34c1cf0600df8da72c18ed7bdcf_images_image-e4b88cdc-3016-439e-9cd6-fec22a6f628e.png'
    if (Test-Path $alt) { $src = $alt }
}

$outDir = Join-Path $PSScriptRoot '..\src\Widget.TimeTracking.WidgetHost\Assets\ActionIcons'
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$img = [System.Drawing.Image]::FromFile((Resolve-Path $src))
try {
    $w = $img.Width
    $h = $img.Height
    $cw = [int]($w / 3)
    Write-Host "Source: ${w}x${h}, slice width: $cw"

    $names = @('StopOrEntry', 'Coffee', 'Food')
    for ($i = 0; $i -lt 3; $i++) {
        $rect = New-Object System.Drawing.Rectangle ($i * $cw), 0, $cw, $h
        $bmp = New-Object System.Drawing.Bitmap $cw, $h
        $g = [System.Drawing.Graphics]::FromImage($bmp)
        try {
            $g.DrawImage($img, 0, 0, $rect, [System.Drawing.GraphicsUnit]::Pixel)
        } finally {
            $g.Dispose()
        }
        $path = Join-Path $outDir ($names[$i] + '.png')
        $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
        $bmp.Dispose()
        Write-Host "Wrote $path"
    }
} finally {
    $img.Dispose()
}
