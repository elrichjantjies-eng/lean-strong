# Lean & Strong

An offline-first workout app for two programs: a 12-week gym program and a 9-week home program
with a waistline focus. Guided workouts with rest and interval timers, set logging that remembers
last time, progression tips, animated exercise demos, weight, waist and steps tracking with the
target path, weekly check-ins, reminders, and backup and restore. All data stays on the device.

- **iPhone:** installable web app at https://elrichjantjies-eng.github.io/lean-strong/
- **Android:** signed APK on the [Releases page](https://github.com/elrichjantjies-eng/lean-strong/releases/latest)

How to install, update and back up: [docs/INSTALL.md](docs/INSTALL.md).

## Development

Vite + React + TypeScript, Tailwind, Dexie (IndexedDB), Zustand, zod, vite-plugin-pwa, and
Capacitor for Android. Program content lives in `src/content/programs/*.json` and is validated
against `src/content/types.ts`; a new program can be added by dropping in another JSON file.

```sh
npm ci
npm run dev          # local dev server
npm test             # unit tests (Vitest)
npm run e2e          # end-to-end tests at iPhone and Android sizes (Playwright)
npm run lint         # ESLint + Prettier
npm run icons        # re-render the app icons, splash and notification icon
```

### Releasing

- **Web app:** every push to `main` builds and deploys to GitHub Pages
  (`.github/workflows/pages.yml`).
- **Android:** bump `version` in `package.json`, commit, then push a tag such as `v1.0.2`.
  `.github/workflows/android.yml` runs the tests, builds a signed APK and publishes a GitHub
  Release with a changelog.
- **Local Android build** (optional): needs JDK 21 (a current Android Studio) and
  `android/keystore.properties` pointing at the signing key, then `npm run android:apk`.
  The APK lands in `android/app/build/outputs/apk/release/`.
