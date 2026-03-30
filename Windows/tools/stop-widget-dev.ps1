# Cierra host/app y, por defecto, Widgets.exe (suelen bloquear resources.pri → DEP1000 0x800704C8).
# Uso antes de F5 en el .wapproj:  .\stop-widget-dev.ps1 -CleanupPri
# Opcional -PackageRoot: carpeta bin\...\Debug concreta (p. ej. si no usas x64).
# Desactivar cierre del panel: variable de entorno WIDGET_SKIP_WIDGETS_EXE=1
param(
    [string] $PackageRoot = '',
    [switch] $CleanupPri
)

$ErrorActionPreference = 'SilentlyContinue'

$names = @(
    'Widget.TimeTracking.WidgetHost',
    'Widget.TimeTracking.App'
)

foreach ($n in $names) {
    Get-Process -Name $n -ErrorAction SilentlyContinue | ForEach-Object {
        Write-Host "Finalizando $($_.ProcessName) (PID $($_.Id))..."
        Stop-Process -Id $_.Id -Force
    }
}

foreach ($exe in @('Widget.TimeTracking.WidgetHost.exe', 'Widget.TimeTracking.App.exe')) {
    & taskkill /F /IM $exe 2>$null | Out-Null
}

if ($env:WIDGET_SKIP_WIDGETS_EXE -ne '1') {
    & taskkill /F /IM Widgets.exe 2>$null | Out-Null
    Write-Host "Widgets.exe: cierre solicitado (evita bloqueo de resources.pri). Omitir: WIDGET_SKIP_WIDGETS_EXE=1"
}

Start-Sleep -Milliseconds 800

$priRoots = @()
if ($PackageRoot) {
    $priRoots = @($PackageRoot)
}
elseif ($CleanupPri) {
    $base = Join-Path $PSScriptRoot '..\src\Widget.TimeTracking.Package\bin'
    foreach ($plat in @('x64', 'ARM64')) {
        foreach ($cfg in @('Debug', 'Release')) {
            $r = Join-Path $base "$plat\$cfg"
            if (Test-Path -LiteralPath $r) { $priRoots += $r }
        }
    }
}

foreach ($root in $priRoots) {
    if (-not (Test-Path -LiteralPath $root)) { continue }
    $candidates = @(
        (Join-Path $root 'resources.pri'),
        (Join-Path $root 'AppX\resources.pri')
    )
    foreach ($p in $candidates) {
        if (Test-Path -LiteralPath $p) {
            Remove-Item -LiteralPath $p -Force -ErrorAction SilentlyContinue
            if (Test-Path -LiteralPath $p) {
                Write-Host "No se pudo borrar (bloqueado): $p"
            } else {
                Write-Host "Eliminado: $p"
            }
        }
    }
}

Write-Host "Listo. Si DEP1000 persiste: cierra VS, borra bin/obj del Package, o ejecuta de nuevo tras cerrar el panel de widgets."
