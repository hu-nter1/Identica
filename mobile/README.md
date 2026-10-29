# Identica Attendance

Identica is a cross-platform staff attendance app built with Expo, React Native, and
TypeScript. It currently stores attendance data on the device. It is an in-progress
application, not a production-ready staff or security system.

## Run the app

From this directory:

```bash
npm install
npm run dev
```

In the Expo terminal:

- Press `w` to open the web app.
- Press `a` to open Android (requires Android Studio or a connected Android device).
- Press `i` to open iOS (requires macOS and Xcode).

Convenience commands are also available: `npm run web`, `npm run android`, and
`npm run ios`. To create the production web build locally, run `npm run build:web`;
the output is written to `dist/`.

## Host the web app on Render

The repository includes a Render Blueprint in `../render.yaml`. To deploy:

1. Push the project to the `main` branch on GitHub.
2. In Render, choose **New → Blueprint** and connect the `hu-nter1/Identica`
   repository.
3. Review the `identica-attendance` static site and create it.
4. Wait for the build to finish, then open the `onrender.com` URL shown in Render.

Render builds the Expo web app with `npm ci && npm run build:web` from this
directory and publishes `dist/`. New commits pushed to `main` trigger deployments.

**This only hosts the web frontend, not a backend.** Staff, schedules, attendance,
photos, and exceptions remain in the browser's IndexedDB. They are not shared
between users or browsers and are not backed up by Render. Do not use real or
sensitive staff data until a secure backend, authentication, and backup strategy
are implemented. Fingerprint scanning also remains unavailable; publishing the
web app does not add the missing scanner bridge.

## Check your changes

```bash
npm run lint
npx tsc --noEmit
npx expo-doctor
```

To bundle all supported app targets:

```bash
npx expo export --platform web --platform android --platform ios
```

## Project layout

```text
src/
  app/
    _layout.tsx          Expo Router root layout and navigation setup
    index.tsx            Main app state and connection between features
  components/
    Screens.tsx          Today, staff, history, issues, and settings views
    Forms.tsx            Staff, camera, and exception forms
    Shared.tsx           Reusable buttons, labels, and small UI pieces
  attendanceFormatters.ts
                         Date/time display, labels, and shared form defaults
  fingerprintScanner.ts  Shared fingerprint scanner interface
  fingerprintScannerImpl.native.ts
                         Native scanner adapter (SDK integration pending)
  fingerprintScannerImpl.web.ts
                         Desktop browser client for a future local bridge
  repository.ts          SQLite storage for Android and iOS
  repository.web.ts      IndexedDB storage for web browsers
  styles.ts              Shared colors and visual styles
  types.ts               Staff, attendance, schedule, and exception data types
  uiTypes.ts             Types used by the app's screens and forms
```

`src/app/_layout.tsx` is the route-level shell. `src/app/index.tsx` is the current
attendance screen and coordinates data loading and user actions. The tab-style
buttons are currently part of that screen; the views and forms they display are
separate components so they can be understood and changed independently.

## How to extend the app

1. **Change a screen:** edit the matching component in `src/components/Screens.tsx`.
2. **Change a form or modal:** edit `src/components/Forms.tsx`.
3. **Add reusable UI:** add a small component to `src/components/Shared.tsx`.
4. **Change stored fields:** update the types in `src/types.ts`, then update both
   `src/repository.ts` and `src/repository.web.ts`. The two repositories should
   continue to expose the same operations.
5. **Add attendance actions:** connect the screen to the repository through
   `src/app/index.tsx`; keep storage and scanner details out of the display
   components where possible.
6. **Change a visual style:** edit `src/styles.ts`.

The app uses TypeScript (`.ts` and `.tsx` files). Types describe the expected shape
of staff and attendance data, and help catch mistakes while editing. React
components receive data and callback functions through props; for example, a
screen can call an `onSave` callback without knowing how the database saves it.

## Current functionality and limits

- Staff profiles require a photo taken with the app camera; selecting or uploading
  an existing photo is not supported.
- Attendance schedules, local staff records, exceptions, and history are available.
- Native apps store data in SQLite; the web app stores data in that browser's
  IndexedDB. These stores do not sync with one another or with a cloud database.
- There is no admin sign-in, cloud backup, or multi-device sync. Use test data only.
- Fingerprint support is **in progress**. The app does not simulate a successful
  scan. Android still needs the scanner vendor SDK and native integration. Desktop
  browsers need a separate local bridge service as well as a reader verified for
  that operating system. The current web adapter is only a client for that future
  service; the bridge is not included here. macOS hardware support is not confirmed.
- The SecuGen Hamster Pro 20 is only a candidate reader for Android integration;
  no scanner has been verified or tested with this app.

### Adding a real fingerprint reader

Before implementing a scanner, confirm the exact scanner model, operating system,
vendor SDK, licensing terms, and a test device. Then:

1. Implement `getStatus`, `enroll`, and `identify` from the shared interface in
   `src/fingerprintScanner.ts`.
2. Keep the platform-specific implementation in a platform adapter (for example,
   `.native.ts` for Android/iOS or `.web.ts` for web).
3. For desktop USB access, provide and separately install a local service. The
   current browser adapter expects a versioned API at
   `http://127.0.0.1:38721/v1` with `/status`, `/enroll`, and `/identify` endpoints.
   The service must not bind to public network interfaces and must authenticate
   or pair with the app before allowing enrollment or identification.
4. Store only an opaque credential reference in the app. Keep fingerprint
   templates in the vendor-backed local scanner service; do not put raw templates
   in app storage or reports.
5. Test enrollment and identification with multiple people and verify that an
   unavailable scanner never records attendance.

Do not treat the browser client contract as a working bridge or scanner
integration. Real hardware support requires the missing vendor SDK and local
service, followed by platform testing.
