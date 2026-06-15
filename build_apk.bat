@echo off
setlocal enabledelayedexpansion

echo === TG WS Proxy APK Build Script (v1.1.0) ===
echo === Output: 3 APKs (universal, arm64-v8a, armeabi-v7a) ===
echo.

:: 0. Read app version from Gradle config
set "VERSION_NAME="
for /f "tokens=2 delims==" %%V in ('findstr /R /C:"versionName *= *\".*\"" "app\build.gradle.kts"') do (
    set "VERSION_NAME=%%V"
)
set "VERSION_NAME=%VERSION_NAME:"=%"
set "VERSION_NAME=%VERSION_NAME: =%"
if not defined VERSION_NAME (
    echo ERROR: Could not read versionName from app\build.gradle.kts
    pause
    exit /b 1
)
set "RELEASE_PREFIX=v%VERSION_NAME%-release"
echo Version: %VERSION_NAME%
echo Release prefix: %RELEASE_PREFIX%
echo.

:: 1. Verify .so files exist for all architectures
set "MISSING=0"
if not exist "app\src\main\jniLibs\arm64-v8a\libtgwsproxy.so" (
    echo ERROR: arm64-v8a .so not found!
    set "MISSING=1"
)
if not exist "app\src\main\jniLibs\armeabi-v7a\libtgwsproxy.so" (
    echo ERROR: armeabi-v7a .so not found!
    set "MISSING=1"
)

if "%MISSING%"=="1" (
    echo.
    echo Run build_so.bat first to build all native libraries!
    pause
    exit /b 1
)

:: 2. Skipping clean for faster incremental builds
echo Incremental build...

:: 3. Build release APKs (ABI splits produce 4 APKs)
echo Building release APKs...
call gradlew assembleRelease --no-daemon

if %errorlevel% neq 0 (
    echo.
    echo BUILD FAILED! Please check the errors above.
    pause
    exit /b 1
)

:: 4. Create release directory
if not exist "app\release" mkdir "app\release"

:: 5. Copy and rename all APK variants
echo.
echo Copying APKs to release folder...

set "APK_DIR=app\build\outputs\apk\release"

:: Universal APK (all architectures)
if exist "%APK_DIR%\app-universal-release.apk" (
    copy /Y "%APK_DIR%\app-universal-release.apk" "app\release\%RELEASE_PREFIX%-universal.apk" >nul
    for %%F in ("app\release\%RELEASE_PREFIX%-universal.apk") do echo   [OK] %%~nxF  [%%~zF bytes]
) else (
    echo   [!!] Universal APK not found
)

:: arm64-v8a
if exist "%APK_DIR%\app-arm64-v8a-release.apk" (
    copy /Y "%APK_DIR%\app-arm64-v8a-release.apk" "app\release\%RELEASE_PREFIX%-arm64-v8a.apk" >nul
    for %%F in ("app\release\%RELEASE_PREFIX%-arm64-v8a.apk") do echo   [OK] %%~nxF  [%%~zF bytes]
) else (
    echo   [!!] arm64-v8a APK not found
)

:: armeabi-v7a
if exist "%APK_DIR%\app-armeabi-v7a-release.apk" (
    copy /Y "%APK_DIR%\app-armeabi-v7a-release.apk" "app\release\%RELEASE_PREFIX%-armeabi-v7a.apk" >nul
    for %%F in ("app\release\%RELEASE_PREFIX%-armeabi-v7a.apk") do echo   [OK] %%~nxF  [%%~zF bytes]
) else (
    echo   [!!] armeabi-v7a APK not found
)



echo.
echo === DONE ===
echo Output directory: app\release\
echo.
echo   %RELEASE_PREFIX%-universal.apk    - все архитектуры в одном APK
echo   %RELEASE_PREFIX%-arm64-v8a.apk    - только 64-bit ARM
echo   %RELEASE_PREFIX%-armeabi-v7a.apk  - только 32-bit ARM

echo.
pause
