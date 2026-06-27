@echo off
color 0B
echo ========================================================
echo        Stopping Frontend and Clearing Port 3000
echo ========================================================

REM البحث عن أي عملية تستخدم المنفذ 3000 وإيقافها
FOR /F "tokens=5" %%T IN ('netstat -ano ^| findstr :3000') DO (
    IF NOT "%%T"=="0" (
        echo [INFO] Found process %%T using port 3000. Killing it...
        taskkill /PID %%T /F >nul 2>&1
    )
)

echo [INFO] Port 3000 is now clear.

echo.
echo ========================================================
echo               Starting Frontend Server
echo ========================================================
echo [INFO] Running 'npm start'...

call npm start

pause
