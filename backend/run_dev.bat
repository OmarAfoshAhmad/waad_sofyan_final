@echo off
setlocal EnableExtensions EnableDelayedExpansion

REM ====================================================
REM TBA WAAD System - Dev Runner
REM Stops existing backend instance(s) then starts a fresh one
REM ====================================================

set DEFAULT_PORT=8080
set PORT=%~1
if "%PORT%"=="" (
    set /p PORT=Enter port [default %DEFAULT_PORT%]: 
)
if "%PORT%"=="" set PORT=%DEFAULT_PORT%

echo [INFO] Using port %PORT%.

echo [INFO] Stopping existing backend processes...
echo.

echo [INFO] Checking for process(es) using port %PORT%...

set FOUND_ANY=0

REM Find and stop any TCP process bound to local target port
for /f "tokens=5" %%P in ('netstat -ano -p TCP ^| findstr /R /C:"^ *TCP *[^ ]*:%PORT% *"') do (
    set FOUND_ANY=1
    echo [WARN] Port %PORT% is in use by PID %%P.
    echo [INFO] Killing PID %%P...
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
for /L %%I in (1,1,10) do (
    set PORT_FREE=1
    for /f "tokens=5" %%P in ('netstat -ano -p TCP ^| findstr /R /C:"^ *TCP *[^ ]*:%PORT% *"') do set PORT_FREE=0
    if !PORT_FREE! equ 1 (
        set PORT_FREE=1
        goto :PORT_READY
    )
    echo [INFO] Port %PORT% still busy. Retry %%I/10...
    ping 127.0.0.1 -n 2 >nul
)

:PORT_READY
if "!PORT_FREE!"=="0" (
    echo [ERROR] Port %PORT% is still in use after retries. Startup aborted.
    endlocal
    exit /b 1
)

echo.
echo [INFO] Starting Spring Boot Application (dev profile)...
echo ====================================================
set DB_PASSWORD=12345
call mvn spring-boot:run "-Dspring-boot.run.profiles=dev" "-Dspring-boot.run.arguments=--server.port=%PORT%"

endlocal
