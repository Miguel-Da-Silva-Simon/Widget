@echo off
chcp 65001 >nul
set "ADB="
if defined ANDROID_HOME if exist "%ANDROID_HOME%\platform-tools\adb.exe" set "ADB=%ANDROID_HOME%\platform-tools\adb.exe"
if not defined ADB if defined ANDROID_SDK_ROOT if exist "%ANDROID_SDK_ROOT%\platform-tools\adb.exe" set "ADB=%ANDROID_SDK_ROOT%\platform-tools\adb.exe"
if not defined ADB if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
if not defined ADB set "ADB=adb"

set "ROOT=%~dp0..\backend"
echo.
echo === 1) Backend Docker (PostgreSQL + API en puerto 8080) ===
cd /d "%ROOT%"
docker compose up -d
if errorlevel 1 (
  echo ERROR: No se pudo arrancar Docker. Abre Docker Desktop e intentalo de nuevo.
  pause
  exit /b 1
)
echo.
echo === 2) Enlace USB adb reverse (obligatorio para 127.0.0.1 en el movil) ===
echo    adb: %ADB%
cd /d "%~dp0"
"%ADB%" reverse tcp:8080 tcp:8080
if errorlevel 1 (
  echo ERROR: adb fallo. Ejecuta solo reverse-8080.bat cuando tengas el movil conectado.
  pause
  exit /b 1
)
"%ADB%" reverse --list
echo.
echo Listo. Compila con variante deviceDebug y prueba login en la app.
pause
