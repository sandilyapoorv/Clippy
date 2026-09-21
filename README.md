# Clippy

Very small Android clipboard history. Text and images stay on the phone until you delete them. There is no account, no cloud, and no automatic expiry.

## Install

GitHub Actions builds a signed APK and AAB, then attaches them to the [GitHub Release](https://github.com/sandilyapoorv/Clippy/releases).

## Catch copies in other apps

The main switch is **Enable Clippy accessibility**. Android Settings → Accessibility → Clippy clipboard capture → On.

That service watches Copy/Cut taps and selected text (`canRetrieveWindowContent=true`). If the clipboard itself is blocked, it briefly focuses a 1px accessibility overlay and pastes to recover the clip. This is what makes history work while Clippy is closed.

Keep **Always watching** on as a backup, and allow the notification / battery exemption so the process is not killed.

## Build locally

```bash
./gradlew :core:test :app:assembleRelease :app:bundleRelease
```

The release keystore is `app/clippy-release.jks` with passwords in `gradle.properties`. Treat it as a sideload key, not a Play-store secret.

## Tests

`./gradlew :core:test` runs fingerprint, preview, and copy-detector tests on the JVM. Instrumented UI tests are in `app/src/androidTest` and need a device or emulator.
