# Android Studio

Open the `Android` folder directly in Android Studio.

This folder is now a Gradle entry point for the app module located in `androidApp/app`, so Android Studio can detect and run the app without needing to open `Android/androidApp` manually.

## Run the app

1. Open the `Android` folder in Android Studio.
2. Wait for Gradle sync to finish.
3. Choose the `deviceDebug` variant for a physical phone over USB, or `emulatorDebug` for the Android emulator.
4. Start the backend from `Android/backend` with `docker compose up -d`.
5. For a physical phone, run `Android/androidApp/reverse-8080.bat` before logging in.

## Backend URL

- `deviceDebug` uses `http://127.0.0.1:8080/`
- `emulatorDebug` uses `http://10.0.2.2:8080/`

If you want to use the phone over Wi-Fi instead of `adb reverse`, set `dev.api.base.url` in `Android/gradle.properties`.
