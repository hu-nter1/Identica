# Identica

Identica is a beginner-friendly Android identity registry app built with **Java** and **XML**. It runs locally on the phone and uses a dark teal interface.

## Features

- Create one local administrator account and sign in.
- Toggle password and recovery-code visibility with the eye buttons.
- Reset the administrator password using the four-digit recovery code.
- Register profiles with a name, age, sex, masked ID, optional document details, and optional portrait.
- Take a portrait with the camera or choose one from the device.
- Search saved profiles by name or masked ID.
- Choose a reference photo and compare it manually with registry portraits.
- Optionally verify the phone owner with an enrolled device fingerprint.
- Review and clear locally stored activity logs.
- Show `falling.png` on the splash screen for about three seconds; `guardian.png` is the app icon.

## Requirements

- Android Studio with Android SDK 35 installed.
- Java 11 or newer for Gradle (Java 21 was used to build this project).
- Android 6.0 (API 23) or newer to install the app.

## Open and run

1. Open this project folder in Android Studio.
2. Allow Gradle to sync and install any requested Android SDK components.
3. Connect an Android phone with USB debugging enabled, or start an emulator.
4. Select the `app` run configuration and press **Run**.

To build a debug APK from a terminal:

```sh
./gradlew assembleDebug
```

On Windows, run:

```bat
gradlew.bat assembleDebug
```

The generated APK is:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Install the debug APK on a connected device with:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## First use

1. Open Identica and wait for the splash screen to finish.
2. On the sign-in screen, choose **Create admin account**.
3. Enter a username, a password of at least four characters, and a four-digit recovery code. Confirm the password and recovery code.
4. Sign in with the new administrator account.
5. Use the bottom navigation to open Home, Register, Scan, Registry, or Audit.
6. On the Register screen, enter the required profile details. Adding a portrait and checking the phone owner's fingerprint are optional.
7. On the Scan screen, search by name or masked ID, choose a photo for manual comparison, or verify the phone owner.

Keep the recovery code private. Anyone who knows both the administrator username and recovery code can reset that administrator's password.

## Screens

| Screen | Purpose |
| --- | --- |
| Sign in | Authenticate the local administrator; create the first account or open password recovery. |
| Home | Show the operator and profile count, with a shortcut to secure logout. |
| Register identity | Save a profile, optional portrait, and optional device-owner verification. |
| Identity search | Search by text, choose a manual photo reference, or verify the device owner. |
| Registry | Browse, inspect, and delete saved profiles. |
| Audit log | Review or clear activity recorded on the device. |

## Project structure

```text
app/src/main/
├── AndroidManifest.xml
├── java/com/example/identica/
│   ├── MainActivity.java       # Screens, navigation, validation, photos, and device check
│   ├── SplashActivity.java     # Displays the custom splash before opening the app
│   └── DatabaseHelper.java     # Local SQLite database and database operations
└── res/
    ├── drawable/               # Icons, backgrounds, guardian.png, and falling.png
    ├── layout/                 # Screen layouts written in XML
    ├── values/                 # Shared colors and styles
    └── xml/                    # FileProvider configuration for camera photos
```

## Data and privacy

- Identica works offline and does not send profile data to a server.
- Administrator credentials, profiles, and audit events are kept in the app's private local storage.
- Passwords and recovery codes are stored as salted PBKDF2 hashes, not as the original values.
- The full ID entered during registration is not stored; the app saves and displays only its last four characters in masked form.
- Portraits are saved in the app's private files. Uninstalling the app removes its local data.
- The app has one local administrator account. It is intended as a standalone assignment/demo, not a multi-user production identity system.

## Important limitations

- **No automatic face recognition is implemented.** A selected photo is only a visual reference for manually comparing the profiles shown in the registry.
- Android does not allow apps to read fingerprint images or templates. The optional biometric check asks Android to verify an enrolled fingerprint for the device owner; it does not identify a person in the registry.
- The recovery code is exactly four digits for simplicity. It is less secure than a longer recovery secret, so do not reuse a sensitive PIN.
- The debug APK is for development and assignment testing. A release build intended for distribution needs a release signing configuration.

## Supplied images

The launcher uses `app/src/main/res/drawable/guardian.png`. The custom startup screen displays `app/src/main/res/drawable/falling.png` over the app's splash background for approximately three seconds.
