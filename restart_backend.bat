@echo off
setlocal enabledelayedexpansion

REM ====================================================
REM TBA WAAD - Backend Restart Helper
REM - Kills any process on port 8080
REM - Loads minimal env vars for local run
REM - Starts backend with dev profile
REM ====================================================

set "PORT=8080"
set "ROOT_DIR=%~dp0"
set "BACKEND_DIR=%ROOT_DIR%backend"

echo [1/4] Checking port %PORT%...
set "FOUND=0"
for /f "tokens=5" %%P in ('netstat -aon ^| findstr ":%PORT%" ^| findstr "LISTENING"') do (
    set "FOUND=1"
    echo Stopping PID %%P on port %PORT%...
    taskkill /F /PID %%P >nul 2>&1
)
if "%FOUND%"=="0" (
    echo No LISTENING process found on port %PORT%.
)

echo [2/4] Validating backend directory...
if not exist "%BACKEND_DIR%\pom.xml" (
    echo ERROR: pom.xml not found in:
    echo %BACKEND_DIR%
    exit /b 1
)

echo [3/4] Setting local environment variables...
set "SPRING_PROFILES_ACTIVE=dev"
set "DB_PASSWORD=12345"
if "%ADMIN_DEFAULT_PASSWORD%"=="" set "ADMIN_DEFAULT_PASSWORD=Admin@123"
if "%JWT_SECRET%"=="" set "JWT_SECRET=waad_dev_secret_not_for_production_only_local_dev_9dda11e5"

echo [4/4] Starting backend...
cd /d "%BACKEND_DIR%"
call mvn clean spring-boot:run

endlocal
