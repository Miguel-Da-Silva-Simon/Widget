@echo off
chcp 65001 >nul
echo.
echo === Enlace USB: movil 127.0.0.1:8080 -^> PC puerto 8080 ===
echo    (Ejecuta ESTE script en el PC cada vez que conectes el movil por USB)
echo.
adb reverse tcp:8080 tcp:8080
if errorlevel 1 (
  echo.
  echo ERROR: No se pudo ejecutar adb.
  echo - Instala Android SDK Platform-Tools o usa "adb" desde Android Studio.
  echo - En el movil: Ajustes ^> Opciones de desarrollador ^> Depuracion USB activada.
  echo - Conecta el cable USB y acepta la huella RSA en el telefono.
  pause
  exit /b 1
)
echo.
echo Listado de reverses activos:
adb reverse --list
echo.
echo OK. En el movil la app puede usar http://127.0.0.1:8080/ (variante deviceDebug).
echo Prueba de nuevo "Entrar" en la app.
echo.
pause
