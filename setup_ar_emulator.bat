@echo off
set "APK_URL=https://github.com/google-ar/arcore-android-sdk/releases/download/v1.54.0/Google_Play_Services_for_AR_1.54.0_x86_for_emulator.apk"
set "APK_FILE=Google_Play_Services_for_AR_1.54.0_x86_for_emulator.apk"

echo Downloading ARCore for Emulator...
powershell -Command "Invoke-WebRequest -Uri '%APK_URL%' -OutFile '%APK_FILE%'"

if exist "%APK_FILE%" (
    echo Installing ARCore...
    adb install -r "%APK_FILE%"
    echo Done! You can now try to launch AR in the app again.
) else (
    echo Failed to download the APK. Please check your internet connection.
)
pause
