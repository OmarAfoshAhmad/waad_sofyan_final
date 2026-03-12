@echo off
setlocal enabledelayedexpansion

REM ====================================================
REM TBA WAAD System - Dev Runner
REM Automatically kills port 8080 and starts the server
REM ====================================================

set PORT=8080

echo [INFO] Checking for process on port %PORT%...

REM Check if any process is listening on the port
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":%PORT%" ^| findstr "LISTENING"') do (
    set PID=%%a
    if "!PID!" neq "" (
        echo [WARN] Port %PORT% is in use by PID !PID!.
        echo [INFO] Killing process !PID!...
        taskkill /F /PID !PID! >nul 2>&1
        if !errorlevel! equ 0 (
             echo [SUCCESS] Process !PID! terminated.
        ) else (
             echo [ERROR] Failed to terminate process !PID!.
        )
    )
)

echo.
echo [INFO] Starting Spring Boot Application (dev profile)...
echo ====================================================
set SPRING_PROFILES_ACTIVE=dev
set DB_PASSWORD=12345
call mvn spring-boot:run

endlocal
