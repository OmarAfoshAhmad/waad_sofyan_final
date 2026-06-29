@echo off
setlocal EnableExtensions EnableDelayedExpansion

REM ====================================================
REM TBA WAAD System - Dev Runner
REM Stops existing backend instance(s) then starts a fresh one
REM Usage: run_dev.bat [port]   (default port = 8081)
REM ====================================================

REM -- Port: argument first, then default (non-interactive) --
set DEFAULT_PORT=8081
if "%~1"=="" (
    set PORT=%DEFAULT_PORT%
) else (
    set PORT=%~1
)

echo [INFO] Using port %PORT%.
echo.
echo [INFO] Checking for process(es) using port %PORT%...

set FOUND_ANY=0

REM Find and stop any TCP process bound to local target port
for /f "tokens=5" %%P in ('netstat -ano -p TCP ^| findstr /R /C:"^ *TCP *[^ ]*:%PORT% *.*LISTENING"') do (
    set FOUND_ANY=1
    echo [WARN] Port %PORT% is in use by PID %%P. Killing...
    taskkill /F /PID %%P >nul 2>&1
    if !errorlevel! equ 0 (
        echo [SUCCESS] PID %%P terminated.
    ) else (
        echo [ERROR] Failed to terminate PID %%P.
    )
)

if "!FOUND_ANY!"=="0" (
    echo [INFO] No process found using port %PORT%.
)

echo.
echo [INFO] Waiting for port %PORT% to be released...
set PORT_FREE=0
for /L %%I in (1,1,15) do (
    set PORT_FREE=1
    for /f "tokens=5" %%P in ('netstat -ano -p TCP ^| findstr /R /C:"^ *TCP *[^ ]*:%PORT% *.*LISTENING"') do set PORT_FREE=0
    if !PORT_FREE! equ 1 (
        goto :PORT_READY
    )
    echo [INFO] Port %PORT% still busy. Retry %%I/15...
    ping 127.0.0.1 -n 2 >nul
)

:PORT_READY
if "!PORT_FREE!"=="0" (
    echo [ERROR] Port %PORT% is still in use after retries. Startup aborted.
    endlocal
    exit /b 1
)

echo.
echo [INFO] Starting Spring Boot Application (dev profile) on port %PORT%...
echo ====================================================
set DB_PASSWORD=12345
set SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/tba_waad_system
set SPRING_DATASOURCE_USERNAME=postgres
set JWT_SECRET=waad_dev_secret_not_for_production_only_local_dev_9dda11e5
set MAVEN_OPTS=-Xmx1024m -Xms512m
call mvn compile spring-boot:run -Dspring-boot.run.profiles=dev -Dspring-boot.run.arguments=--server.port=%PORT%

endlocal
