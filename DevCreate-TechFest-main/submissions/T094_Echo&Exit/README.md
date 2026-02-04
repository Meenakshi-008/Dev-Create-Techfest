
## Team Name & Team ID

- Team Name: Echo & Exit
- Team ID: T094

## Members and Roles

| Member | Role |
| --- | --- |
|  Rohan| Backend architect and developer |
|Meenakshi | Idea and Presentation |
| Akshaya | Frontend |
| Parthiv | Data analysis on student records |
| RamTeja | Designing and presentation |

> Please update the list above with real member names and roles.

## Problem statement selected

This submission implements the Android application for the selected BuildFest challenge. (Problem statement not found in the repository; please replace this line with the exact problem statement you selected.)

## Tech stack used

- Android (mobile)
- Kotlin
- Gradle / Gradle Wrapper
- Android Studio (recommended for development)

## How to run the project (setup instructions)

Prerequisites

- JDK 11 or newer installed and JAVA_HOME set
- Android Studio (recommended) or Android SDK tools + platform tools
- An Android device or emulator

Recommended (Android Studio)

1. Open Android Studio.
2. Choose "Open" and select the `code/` folder inside this submission (path: `submissions/T094_Echo&Exit/code`).
3. Let Android Studio sync Gradle and download dependencies.
4. Run the app using the Run button or create an emulator and install to it.

Command line (Windows PowerShell)

From the root of this submission (where this README is located), run:

```powershell
# Build debug APK
.\code\gradlew.bat assembleDebug

# Install debug build to a connected device/emulator
.\code\gradlew.bat installDebug
```

Notes:

- If you get "permission" errors running the wrapper, ensure `gradlew.bat` is executable; running from PowerShell as a normal user should work on Windows.
- The build requires a valid Android SDK installation. If local SDK path is not detected, confirm `local.properties` in `code/` points to your SDK (`sdk.dir=`).
