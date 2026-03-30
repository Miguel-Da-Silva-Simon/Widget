@echo off
chcp 65001 >nul
set "ADB="
if defined ANDROID_HOME if exist "%ANDROID_HOME%\platform-tools\adb.exe" set "ADB=%ANDROID_HOME%\platform-tools\adb.exe"
if not defined ADB if defined ANDROID_SDK_ROOT if exist "%ANDROID_SDK_ROOT%\platform-tools\adb.exe" set "ADB=%ANDROID_SDK_ROOT%\platform-tools\adb.exe"
if not defined ADB if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
if not defined ADB set "ADB=adb"

echo.
echo === Enlace USB: movil 127.0.0.1:8080 -^> PC puerto 8080 ===
echo    (Ejecuta ESTE script en el PC cada vez que conectes el movil por USB)
echo    adb: %ADB%
echo.
"%ADB%" reverse tcp:8080 tcp:8080
if errorlevel 1 (
  echo.
  echo ERROR: No se pudo ejecutar adb.
  echo - Instala "Android SDK Platform-Tools" desde SDK Manager en Android Studio.
  echo - O define ANDROID_HOME apuntando al SDK (ej. %%LOCALAPPDATA%%\Android\Sdk).
  echo - En el movil: Ajustes ^> Opciones de desarrollador ^> Depuracion USB activada.
  echo - Conecta el cable USB y acepta la huella RSA en el telefono.
  pause
  exit /b 1
)
echo.
echo Listado de reverses activos:
"%ADB%" reverse --list
echo.
echo OK. En el movil la app puede usar http://127.0.0.1:8080/ (variante deviceDebug).
echo Prueba de nuevo "Entrar" en la app.
echo.
pause
