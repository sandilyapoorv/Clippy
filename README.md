# Clippy

Very small Android clipboard history. Text and images you save stay on the phone until you delete them. There is no account, no cloud, and no automatic expiry.

## Install

GitHub Actions builds a signed APK and AAB, then attaches them to the [GitHub Release](https://github.com/sandilyapoorv/Clippy/releases/tag/v1.0.0).

## How to use it

Android 10+ only lets a normal app read the clipboard while that app is visible. Clippy does not pretend otherwise.

Save a clip by:

1. Opening Clippy and copying (it records while the screen is open)
2. Tapping **Save clipboard now**
3. Tapping the persistent notification
4. Using the **Save clipboard** quick-settings tile
5. Sharing text or an image to Clippy

Duplicates of the same text or image bytes are stored once.

## Build locally

```bash
./gradlew :core:test :app:assembleRelease :app:bundleRelease
```

The release keystore is `app/clippy-release.jks` with passwords in `gradle.properties`. Treat it as a sideload key, not a Play-store secret.

## Tests

`./gradlew :core:test` runs fingerprint and preview tests on the JVM. Instrumented UI tests are in `app/src/androidTest` and need a device or emulator.
