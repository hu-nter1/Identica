# Identica Attendance

The cross-platform React Native app is in [`mobile/`](./mobile/). It is an offline-first prototype for tracking staff attendance at a school, company, or other premise. The original Kotlin Android app remains in the root Android project during this migration; the React Native app is the new attendance-focused implementation.

## What the prototype does

- Register staff with a unique staff number, full name, department, role, optional phone number, and a required photo taken directly with the camera. Use “Save & capture next staff member” to register staff one after another.
- Record daily check-in and check-out times, prevent duplicate attendance actions, and keep each day’s register on the device.
- Configure the check-in window (defaults to **06:00–08:30**) and earliest official check-out (defaults to **15:00**). Late arrivals are marked absent unless an administrator records a late-check-in exception. Check-out has no evening cutoff; a missing check-out is resolved when the next attendance day starts.
- Reconcile staff who did not check in as absent after the check-in window, and mark an unchecked-out staff member `absent_no_checkout` when that day closes. The daily register distinguishes this from a person who never checked in.
- Keep a reasoned special-issues register for approved absences, late check-ins, early check-outs, and missed check-outs. An applicable exception is considered during attendance reconciliation.
- Review attendance by date and share a CSV report using the device’s share sheet.

## Run the React Native app

Requirements: Node.js and npm. From this directory, install dependencies and start the Expo development server:

```bash
cd mobile
npm install
npm run dev
```

- **Android/iOS:** scan the Expo QR code with Expo Go. The phone and computer should be on the same network.
- **Web:** open a second terminal in `mobile/` and run `npm run web`, or press **w** in the running Expo terminal.
- `npm run android` and `npm run ios` open an installed simulator; iOS simulator requires macOS.

The equivalent Expo command is `npx expo start`. Use `npx` (not `npm expo`) to run Expo directly. `npm run dev` is also available as a shortcut.

## Important biometric limitation

Fingerprint scanning is currently **not connected**. Built-in Android/iOS biometrics cannot identify which employee touched a shared kiosk. This repository does not capture or match fingerprint data; the UI only exposes clearly disabled/pending scanner integration points.

The Android-first wired reader candidate is the [SecuGen Hamster Pro 20](https://secugen.com/products/hamster-pro-20/). SecuGen lists Android, Windows, and Linux support and provides its [FDx SDK Pro for Android](https://secugen.com/products/sdk/) by request. The SDK is not included, the scanner has not been purchased or tested, and its product support information does not list macOS. No USB reader works on every computer automatically: each host OS needs a compatible vendor driver and SDK.

### Desktop and macOS scanner bridge (prepared, not implemented)

The React Native screens can run in a computer browser on Windows, Linux, and macOS. Browsers cannot directly read a proprietary fingerprint sensor's templates, so desktop capture requires a separately installed local native scanner service built with that reader's OS-specific SDK. The web adapter in [`mobile/src/fingerprintScannerImpl.web.ts`](./mobile/src/fingerprintScannerImpl.web.ts) defines the client boundary for that future service at `http://127.0.0.1:38721/v1`; it does **not** make a scanner or bridge work by itself. The service must explicitly pair with the app, report its supported reader/capabilities, and provide enrollment and identification operations.

Before deploying this bridge, implement and security-review its pairing/authentication, strict origin allowlist, loopback-only binding, and OS-specific driver/SDK. Keep fingerprint templates in the vendor-backed local service; return only an opaque enrollment reference and identified staff number to the app. macOS remains unsupported for the selected SecuGen reader unless SecuGen confirms and supplies a compatible macOS SDK or a different reader with official macOS support is selected. Android integration similarly remains pending the requested SecuGen SDK. Until those components exist, fingerprint enrolment and attendance remain disabled; staff profiles/photos and the other app features continue to work.

The staff photo is captured manually with the in-app camera; there is no gallery or photo-upload flow and no face recognition. Local staff profiles, captured photos, schedules, attendance records, and exceptions remain on the device or browser. Android/iOS use SQLite through [`mobile/src/repository.ts`](./mobile/src/repository.ts); web uses IndexedDB through [`mobile/src/repository.web.ts`](./mobile/src/repository.web.ts). This avoids requiring browser-only SQLite/WebAssembly isolation headers and lets the same interface serve a future API-backed Neon integration. Browser data is local to that browser and does not sync to a phone or another computer. Do not put Neon credentials in the mobile client. Admin authentication, multi-device synchronization, secure backups, and tamper-resistant audit history are not implemented yet, so use test data only.

## Project layout

- [`mobile/App.tsx`](./mobile/App.tsx) — React Native screens and attendance workflow.
- [`mobile/src/repository.ts`](./mobile/src/repository.ts) — local SQLite data and schedule rules, behind the `AttendanceRepository` interface.
- [`mobile/src/types.ts`](./mobile/src/types.ts) — staff, attendance, schedule, and exception types.
- `app/` — retained Kotlin Android implementation during migration; its face-search features are not part of the new React Native attendance prototype.

## Legacy Android build

The retained Kotlin project can still be built from the root:

```bash
bash gradlew :app:testDebugUnitTest :app:assembleDebug
```
