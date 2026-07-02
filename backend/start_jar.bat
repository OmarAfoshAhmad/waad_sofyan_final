@echo off
setlocal EnableExtensions EnableDelayedExpansion

:: ═══════════════════════════════════════════════════════
:: TBA Backend - Persistent JAR Runner
:: يُنهي العملية السابقة، يبني الـ JAR، ثم يُشغّله بشكل مستقل
:: ═══════════════════════════════════════════════════════

set PORT=8081
set DB_PASSWORD=12345
set SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/tba_waad_system
set SPRING_DATASOURCE_USERNAME=postgres
set JWT_SECRET=waad_dev_secret_not_for_production_only_local_dev_9dda11e5
set ADMIN_DEFAULT_PASSWORD=Admin@123

echo.
echo ════════════════════════════════════════════
echo  TBA Backend Launcher (Port: %PORT%)
echo ════════════════════════════════════════════

:: 1. Stop any existing process on port
echo [1/4] Stopping existing backend on port %PORT%...
for /f "tokens=5" %%P in ('netstat -ano 2^>nul ^| findstr ":%PORT% "') do (
    taskkill /F /PID %%P >nul 2>&1
)
ping 127.0.0.1 -n 2 >nul

:: 2. Build JAR (skip tests for speed)
echo [2/4] Building JAR (skipping tests)...
call mvn package -DskipTests -q
if %errorlevel% neq 0 (
    echo [ERROR] Build failed! Check Maven output.
    exit /b 1
)

:: 3. Find the JAR
for %%F in (target\*.jar) do set JAR_FILE=%%F
if not defined JAR_FILE (
    echo [ERROR] No JAR found in target\
    exit /b 1
)
echo [3/4] Found JAR: %JAR_FILE%

:: 4. Launch as independent process (survives terminal close)
echo [4/4] Launching backend (independent process)...
start "TBA-Backend-8081" /MIN java ^
  -Xmx768m -Xms256m ^
  -Dspring.profiles.active=dev ^
  -Dserver.port=%PORT% ^
  -DDB_PASSWORD=%DB_PASSWORD% ^
  -DSPRING_DATASOURCE_URL=%SPRING_DATASOURCE_URL% ^
  -DSPRING_DATASOURCE_USERNAME=%SPRING_DATASOURCE_USERNAME% ^
  -DJWT_SECRET=%JWT_SECRET% ^
  -DADMIN_DEFAULT_PASSWORD=%ADMIN_DEFAULT_PASSWORD% ^
  -jar %JAR_FILE%

echo.
echo [SUCCESS] Backend started as independent process!
echo [INFO]    Port: %PORT%
echo [INFO]    JAR: %JAR_FILE%
echo [INFO]    It will stay running even after this window closes.
echo [INFO]    To stop: taskkill /F /FI "WINDOWTITLE eq TBA-Backend-8081"
echo.

:: Wait a bit then check if it's up
echo [INFO] Waiting 25 seconds for startup...
ping 127.0.0.1 -n 25 >nul

netstat -ano 2>nul | findstr ":%PORT% " | findstr "LISTENING" >nul
if %errorlevel% == 0 (
    echo [SUCCESS] Backend is RUNNING on port %PORT%!
) else (
    echo [WARN] Backend may still be starting up. Check the TBA-Backend-8081 window.
)

endlocal
