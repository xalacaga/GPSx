@echo off
setlocal
set "options=%*"
if not "%~1"=="" goto launch

echo GPSx emulator
echo   1. Build: compile and install the latest GPSx
echo   2. No build: install the APK already compiled
choice /c 12 /n /m "Choose 1 or 2: "
if errorlevel 3 exit /b 1
if not errorlevel 1 exit /b 1
if errorlevel 2 set "options=-NoBuild"

:launch
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-gpsx-emulator.ps1" %options%
if errorlevel 1 (
    echo.
    echo GPSx emulator launch failed.
    pause
    exit /b 1
)
