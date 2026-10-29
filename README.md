# Identica — Biometric Identity Registry

Updated from the supplied `id.zip`.

## UI updates
- A consistent, calm dark-teal visual system with clearer typography, rounded surfaces, more deliberate spacing, and distinct primary, secondary, and destructive actions.
- Login, dashboard, search, registration, registry, recovery, and audit screens share the same visual hierarchy and readable sans-serif styles.
- Dashboard provides the main controls for the application.
- Dashboard navigation is at the bottom.
- Search navigation is at the bottom with Face Scan and Fingerprint tabs.
- Registration explicitly uses `SCAN FACE / CAPTURE PORTRAIT` and `SCAN FINGERPRINT`.
- Fingerprint registration includes a live fingerprint visual, scan animation and 0–100% progress display.
- Search scanner shows the portrait or fingerprint visual inside the scan frame.
- Match results show the complete stored identity profile, including portrait, name, age, sex, masked ID, document details, scan type and match percentage.
- Registry page shows counts for registered identities and system operators, plus identity cards with portraits and operator cards with generated avatars.
- Existing authentication, Room database, audit log, camera capture, biometric prompt and matching features are preserved.

## Code map for beginners

The app is an Android project written in Kotlin. Start with `app/src/main/java/com/example/identica/IdenticaApp.kt` to see how the database, session manager, and repository are created.

- **Frontend — `ui/`:** activities and custom views that display screens and handle user input. Screens call the repository instead of running database queries themselves.
- **Backend — `data/`:** `IdenticaRepository.kt` coordinates validation, database access, and audit records. `data/local/` contains the Room database, SQL queries (`dao/`), and database row models (`entity/`).
- **Backend — `security/`:** password hashing and the encrypted local session.
- **Backend helpers — `biometric/` and `util/`:** image matching, hash calculations, bitmap handling, and input validation.

For example, person registration starts in `ui/person/RegisterPersonActivity`, is checked and saved by `data/IdenticaRepository`, and is stored through `data/local/dao/PersonDao` as a `RegisteredPerson`. These folders are intentionally kept separate by responsibility rather than combined into broad `frontend` and `backend` folders; this is the usual Android project structure and makes each part easier to find and change.

Some data in this app is sensitive. Keep password hashing, encrypted session storage, national-ID masking, and audit logging in place when changing the code.

## Screenshot protection

Screenshot blocking is temporarily disabled in the shared secure activity and the login, sign-up, and password recovery screens. Screenshots and screen recording can include sensitive identity data while this setting is disabled. Re-enable the `FLAG_SECURE` calls in those files before using the app with real data or releasing it.

## Important Android biometric limitation
Android `BiometricPrompt` verifies a biometric enrolled on the device but does not expose the raw fingerprint template or identify which enrolled finger was used. The in-app fingerprint graphics and progress are therefore visual feedback around the real Android biometric verification; they do not claim to read raw fingerprint data from the OS sensor.

## Build

From the project root, run:

```bash
bash gradlew :app:testDebugUnitTest :app:assembleDebug
```

## Photo search matching update

Photo search no longer compares SHA-256 hashes of entire image files. It computes an offline perceptual visual signature from a center-square grayscale crop and compares signatures using Hamming similarity. This is tolerant of some file re-encoding and small brightness changes and works for old database records by recalculating the signature from their saved photo paths. It is **not a trained face-recognition model**: different photos of the same person may still score poorly, and similar-looking people may score highly. A real identity-level face matcher requires an on-device face-embedding model and validation. Fingerprint UI, admin authentication/recovery, registry, audit log, and existing Room migrations are preserved.
