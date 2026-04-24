@echo off
setlocal

set "PORT=3000"
set "FRONTEND_DIR=%~dp0frontend"

echo [1/3] Checking for processes on port %PORT%...
set "FOUND=0"
for /f "tokens=5" %%P in ('netstat -aon ^| findstr ":%PORT%" ^| findstr "LISTENING"') do (
    set "FOUND=1"
    echo Stopping PID %%P on port %PORT%...
    taskkill /F /PID %%P >nul 2>&1
)

if "%FOUND%"=="0" (
    echo No LISTENING process found on port %PORT%.
)

echo [2/3] Preparing frontend start...
if not exist "%FRONTEND_DIR%\package.json" (
    echo ERROR: frontend/package.json not found at:
    echo %FRONTEND_DIR%
    exit /b 1
)

cd /d "%FRONTEND_DIR%"
echo [3/3] Starting frontend with npm run start...
call npm run start

endlocal
