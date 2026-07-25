@echo off
setlocal enabledelayedexpansion

REM ====================================================
REM TBA WAAD - Backend Restart Helper
REM - Kills any process on port 8080
REM - Loads configuration from the root .env file
REM - Starts backend with dev profile
REM ====================================================

set "PORT=8080"
if exist "%~dp0pom.xml" (
    set "BACKEND_DIR=%~dp0"
    set "ROOT_DIR=%~dp0..\"
) else (
    set "ROOT_DIR=%~dp0"
    set "BACKEND_DIR=%~dp0backend"
)

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
if exist "%ROOT_DIR%.env" (
    for /f "usebackq eol=# tokens=1,* delims==" %%A in ("%ROOT_DIR%.env") do (
        if not "%%A"=="" set "%%A=%%B"
    )
)
REM This launcher is explicitly for a backend running directly on Windows.
REM Load secrets from .env, but never inherit the Docker/prod host name "db".
set "SPRING_PROFILES_ACTIVE=dev"
set "SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/tba_waad_system"
set "SPRING_DATASOURCE_USERNAME=postgres"
if "%DB_PASSWORD%"=="" (
    echo ERROR: DB_PASSWORD is not configured in .env or the process environment.
    exit /b 1
)
if "%JWT_SECRET%"=="" (
    echo ERROR: JWT_SECRET is not configured in .env or the process environment.
    exit /b 1
)

echo [4/4] Starting backend...
cd /d "%BACKEND_DIR%"
call mvn compile spring-boot:run -Dmaven.test.skip=true 


endlocal
