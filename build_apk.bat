@echo off
rem Kadian Android · one-click APK build
rem Usage: build_apk.bat [debug|release]
rem Env vars take priority: existing JAVA_HOME / ANDROID_HOME are respected;
rem otherwise falls back to the local defaults below.
chcp 65001 >nul
cd /d %~dp0
if "%JAVA_HOME%"=="" set JAVA_HOME=D:\dev\java\zulu17.54.21-ca-jdk17.0.13-win_x64
if "%ANDROID_HOME%"=="" set ANDROID_HOME=D:\android-sdk
set PATH=%JAVA_HOME%\bin;%ANDROID_HOME%\cmdline-tools\latest\bin;%ANDROID_HOME%\platform-tools;%PATH%

set VARIANT=%1
if "%VARIANT%"=="" set VARIANT=debug

rem Resolve a usable Python (the WindowsApps "python" stub exits 9009 with args)
set PYTHON=%KADIAN_PYTHON%
if "%PYTHON%"=="" (
    python -c "import sys" >nul 2>&1
    if not errorlevel 1 set PYTHON=python
)
if "%PYTHON%"=="" (
    py -3 -c "import sys" >nul 2>&1
    if not errorlevel 1 set PYTHON=py -3
)
if "%PYTHON%"=="" (
    echo [ERROR] No usable Python found. Set KADIAN_PYTHON to a python.exe path.
    exit /b 1
)

echo [1/3] Sync PWA assets into assets\public ...
%PYTHON% tools\sync_assets.py || goto :err

echo [2/3] Gradle build (%VARIANT%) ...
call gradlew.bat assemble%VARIANT% --no-daemon || goto :err

echo [3/3] Collect artifact ...
%PYTHON% tools\collect_apk.py %VARIANT% || goto :err
goto :eof

:err
echo.
echo [BUILD FAILED] see the error messages above.
exit /b 1
