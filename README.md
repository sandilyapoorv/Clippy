# Clippy

Very small Android clipboard history. Text and images stay on the phone until you delete them. There is no account, no cloud, and no automatic expiry.

## Install

GitHub Actions builds a signed APK and AAB, then attaches them to the [GitHub Release](https://github.com/sandilyapoorv/Clippy/releases).

## Always watching

Leave **Always watching** on. Clippy runs a persistent foreground service and comes back after reboot.

Android still only lets a normal app *read* clipboard bytes while it has screen focus. So when you copy, Clippy briefly pops a transparent capture screen (needs **Display over other apps**) and saves the text or image. It is not a silent system-level keylogger; that permission does not exist for ordinary apps.

Also allow the persistent notification and battery exemption so the phone does not kill it.

## Build locally

```bash
./gradlew :core:test :app:assembleRelease :app:bundleRelease
```

The release keystore is `app/clippy-release.jks` with passwords in `gradle.properties`. Treat it as a sideload key, not a Play-store secret.

## Tests

`./gradlew :core:test` runs fingerprint and preview tests on the JVM. Instrumented UI tests are in `app/src/androidTest` and need a device or emulator.
