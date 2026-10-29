# Identica — Biometric Identity Registry

Updated from the supplied `id.zip`.

## UI updates
- Dashboard provides the main controls for the application.
- Dashboard navigation is at the bottom.
- Search navigation is at the bottom with Face Scan and Fingerprint tabs.
- Registration explicitly uses `SCAN FACE / CAPTURE PORTRAIT` and `SCAN FINGERPRINT`.
- Fingerprint registration includes a live fingerprint visual, scan animation and 0–100% progress display.
- Search scanner shows the portrait or fingerprint visual inside the scan frame.
- Match results show the complete stored identity profile, including portrait, name, age, sex, masked ID, document details, scan type and match percentage.
- Registry page shows counts for registered identities and system operators, plus identity cards with portraits and operator cards with generated avatars.
- Existing authentication, Room database, audit log, camera capture, biometric prompt and matching features are preserved.

## Important Android biometric limitation
Android `BiometricPrompt` verifies a biometric enrolled on the device but does not expose the raw fingerprint template or identify which enrolled finger was used. The in-app fingerprint graphics and progress are therefore visual feedback around the real Android biometric verification; they do not claim to read raw fingerprint data from the OS sensor.

## Build



## Photo search matching update

Photo search no longer compares SHA-256 hashes of entire image files. It computes an offline perceptual visual signature from a center-square grayscale crop and compares signatures using Hamming similarity. This is tolerant of some file re-encoding and small brightness changes and works for old database records by recalculating the signature from their saved photo paths. It is **not a trained face-recognition model**: different photos of the same person may still score poorly, and similar-looking people may score highly. A real identity-level face matcher requires an on-device face-embedding model and validation. Fingerprint UI, admin authentication/recovery, registry, audit log, and existing Room migrations are preserved.
# Identica
# Identica
# Identica
