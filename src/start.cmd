@echo off
cls

:menu
echo ================================================
echo     DISTRIBUTED SYSTEM - MAIN MENU
echo ================================================
echo.
echo 1. Start System (compile + run servers)
echo 2. Run CLI (client interface)
echo 3. Stop System
echo 4. Clean Project
echo 5. Exit
echo.
set /p choice="Choose option (1-5): "

if "%choice%"=="1" goto start_system
if "%choice%"=="2" goto run_cli
if "%choice%"=="3" goto stop_system
if "%choice%"=="4" goto clean_project
if "%choice%"=="5" goto exit_program

echo Invalid choice!
ping 127.0.0.1 -n 2 >nul
cls
goto menu

:start_system
cls
echo ================================================
echo          STARTING SYSTEM
echo ================================================
echo.

echo Creating directories...
if not exist bin mkdir bin
if not exist data mkdir data
if not exist logs mkdir logs
if not exist keys mkdir keys
if not exist downloads mkdir downloads
echo [OK] Directories created
echo.

echo Compiling source code...
javac -d bin -sourcepath . common\*.java 2>error.log
if errorlevel 1 (
    echo [ERROR] Compilation failed! Check error.log
    pause
    goto menu
)

javac -d bin -sourcepath . -cp bin client\*.java 2>>error.log
if errorlevel 1 (
    echo [ERROR] Compilation failed! Check error.log
    pause
    goto menu
)

javac -d bin -sourcepath . -cp bin server\*.java 2>>error.log
if errorlevel 1 (
    echo [ERROR] Compilation failed! Check error.log
    pause
    goto menu
)

del error.log 2>nul
echo [OK] Compilation complete
echo.

echo Starting servers...
echo.

echo - API Gateway (Port 7000)
start "APIGateway-7000" cmd /k java -cp bin server.APIGateway
ping 127.0.0.1 -n 3 >nul

echo - Registration Service (Port 6000)
start "Registration-6000" cmd /k java -cp bin server.RegistrationService
ping 127.0.0.1 -n 2 >nul

echo - Login Service (Port 6001)
start "Login-6001" cmd /k java -cp bin server.LoginService
ping 127.0.0.1 -n 2 >nul

echo - Posts Service (Port 6002/6003)
start "Posts-6002-6003" cmd /k java -cp bin server.PostsService
ping 127.0.0.1 -n 2 >nul

echo - File Service (Port 6004/6005/6007)
start "Files-6004-6005-6007" cmd /k java -cp bin server.FileService
ping 127.0.0.1 -n 2 >nul

echo.
echo [OK] All servers started!
echo.
echo 5 server windows have been opened.
echo Check each window - they should show startup messages.
echo If any window closed immediately, there was an error.
echo.
echo WAIT 5 seconds before running CLI!
echo.
echo Next: Choose option 2 to run CLI
echo.
pause
cls
goto menu

:run_cli
cls
echo ================================================
echo          RUNNING CLI CLIENT
echo ================================================
echo.

if not exist bin\client\CLI.class (
    echo [ERROR] System not compiled!
    echo Please run option 1 first to compile and start servers.
    echo.
    pause
    cls
    goto menu
)

echo Connecting to server...
echo.
echo ================================================
echo.

java -cp bin client.CLI

echo.
echo ================================================
echo CLI session ended.
echo.
pause
cls
goto menu

:stop_system
cls
echo ================================================
echo          STOPPING SYSTEM
echo ================================================
echo.

echo Stopping all server processes...

taskkill /FI "WINDOWTITLE eq APIGateway*" /F >nul 2>&1
taskkill /FI "WINDOWTITLE eq Registration*" /F >nul 2>&1
taskkill /FI "WINDOWTITLE eq Login*" /F >nul 2>&1
taskkill /FI "WINDOWTITLE eq Posts*" /F >nul 2>&1
taskkill /FI "WINDOWTITLE eq Files*" /F >nul 2>&1

ping 127.0.0.1 -n 2 >nul

echo.
echo [OK] All servers stopped
echo.
pause
cls
goto menu

:clean_project
cls
echo ================================================
echo          CLEAN PROJECT
echo ================================================
echo.
echo What do you want to clean?
echo.
echo 1. Only compiled files (bin)
echo 2. Compiled files + logs
echo 3. EVERYTHING (data, keys, files) - WARNING!
echo 4. Back to menu
echo.
set /p clean_choice="Choose option (1-4): "

if "%clean_choice%"=="1" goto clean_bin
if "%clean_choice%"=="2" goto clean_logs
if "%clean_choice%"=="3" goto clean_all
if "%clean_choice%"=="4" (
    cls
    goto menu
)

echo Invalid choice!
ping 127.0.0.1 -n 2 >nul
goto clean_project

:clean_bin
echo.
echo Cleaning compiled files (bin)...
if exist bin rmdir /s /q bin
mkdir bin
echo [OK] Cleaned bin directory
echo.
pause
cls
goto menu

:clean_logs
echo.
echo Cleaning compiled files and logs...
if exist bin rmdir /s /q bin
if exist logs rmdir /s /q logs
mkdir bin
mkdir logs
echo [OK] Cleaned bin and logs
echo.
pause
cls
goto menu

:clean_all
echo.
echo ================================================
echo WARNING: This will delete ALL data!
echo - User accounts and keys
echo - All posts
echo - All uploaded files
echo ================================================
echo.
set /p confirm="Are you absolutely sure? Type YES to confirm: "

if not "%confirm%"=="YES" (
    echo Cancelled.
    ping 127.0.0.1 -n 2 >nul
    cls
    goto menu
)

echo.
echo Cleaning everything...
if exist bin rmdir /s /q bin
if exist logs rmdir /s /q logs
if exist data rmdir /s /q data
if exist keys rmdir /s /q keys
if exist downloads rmdir /s /q downloads

mkdir bin
mkdir data
mkdir keys
mkdir downloads

echo [OK] Everything cleaned
echo.
pause
cls
goto menu

:exit_program
cls
echo.
echo Exiting...
echo.
echo Don't forget to stop servers (option 3) if they're still running!
echo.
ping 127.0.0.1 -n 2 >nul
exit