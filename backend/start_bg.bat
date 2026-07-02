@echo off
setlocal EnableExtensions EnableDelayedExpansion

set PORT=8081
set DB_PASSWORD=12345
set SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/tba_waad_system
set SPRING_DATASOURCE_USERNAME=postgres
set JWT_SECRET=waad_dev_secret_not_for_production_only_local_dev_9dda11e5
set ADMIN_DEFAULT_PASSWORD=Admin@123
set MAVEN_OPTS=-Xmx1024m -Xms512m

echo [INFO] Stopping any existing backend on port %PORT%...
for /f "tokens=5" %%P in ('netstat -ano -p TCP ^| findstr /R /C:"^ *TCP *[^ ]*:%PORT% *.*LISTENING"') do (
    taskkill /F /PID %%P >nul 2>&1
)
ping 127.0.0.1 -n 3 >nul

echo [INFO] Compiling...
call mvn compile -q
if %errorlevel% neq 0 (
    echo [ERROR] Compile failed!
    exit /b 1
)

echo [INFO] Starting backend in background on port %PORT%...
start "TBA-Backend" /B mvn spring-boot:run -Dspring-boot.run.profiles=dev -Dspring-boot.run.arguments="--server.port=%PORT%" > backend_bg.log 2>&1

echo [SUCCESS] Backend started in background. Check backend_bg.log for output.
echo [INFO] Waiting for startup...
ping 127.0.0.1 -n 20 >nul

for /f "tokens=5" %%P in ('netstat -ano -p TCP ^| findstr /R /C:"^ *TCP *[^ ]*:%PORT% *.*LISTENING"') do (
    echo [SUCCESS] Backend is RUNNING on port %PORT% (PID: %%P)
    goto :done
)
echo [WARN] Backend may still be starting. Check backend_bg.log

:done
endlocal
