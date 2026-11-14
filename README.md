# Identica Android app

A beginner-friendly standalone Android app recreated from the provided screen images, with a consistent dark teal interface and clear active-page navigation. It uses Java for app behavior and XML for layouts.

On launch, the custom splash screen displays the `falling.png` artwork for about three seconds before opening the app.

## Open and run

Open this folder in Android Studio, let Gradle sync, and run the `app` configuration. Or build an APK with:

```sh
./gradlew assembleDebug
```

## Code map

- `app/src/main/java/com/example/identica/MainActivity.java` contains screen navigation, form validation, camera/photo picker, and fingerprint verification.
- `app/src/main/java/com/example/identica/DatabaseHelper.java` saves admin accounts, identity profiles, and audit events in the app's private SQLite database.
- `app/src/main/res/layout/` contains the XML for login, home, registration, scanning, registry, and audit screens.
- `app/src/main/res/values/styles.xml` and `colors.xml` contain the shared design styles and colors.

## First use

1. Install and open the app.
2. Select **Create admin account** and set a username, password (at least four characters), and four-digit recovery code.
3. Sign in, then use the navigation bar to register profiles, search by name or masked ID, browse profiles, and view or clear the audit log.
4. On profile registration, portrait and device fingerprint verification are optional. Portraits can be taken with the camera app or selected from the device. Grant access only to photos you want saved.
5. Fingerprint verification requires a device fingerprint sensor with a fingerprint already enrolled in Android settings.

Everything runs offline. No API keys, cloud service, or external database are required. Passwords and recovery codes are stored as salted PBKDF2 hashes. Use **Forgot password?** on the login screen, then enter the admin username and recovery code to set a new password. Keep the four-digit code private; anyone who has it and knows the username can reset the password. Password visibility can be toggled while entering a password. Profiles, photos, and logs are stored locally in the app's private storage; uninstalling the app deletes them. The full national ID is not saved; only its last four characters are shown/stored in masked form.

If upgrading an older install that had an admin account but no recovery code, sign in with the existing password once and the app will ask you to set a recovery code.

Android deliberately does not give apps the fingerprint image or template, nor tell the app which enrolled finger was used. The optional fingerprint action verifies the phone's enrolled owner and records that check; it does not attach a fingerprint to a person or search the registry. Camera capture saves a full-resolution portrait in app-private storage, and the registry uses smaller previews to avoid loading full-size photos into memory. Choosing a photo on the search screen shows it as a reference beside registry profiles for manual comparison; it does not perform automatic face recognition. Text search matches names and masked IDs.

The provided files are screenshots, not separate portrait-image assets, so the app does not use the people shown in those screenshots as profile photos.
