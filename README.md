# Pillzilla

An Android app that makes sure you take your meds on time. Each reminder pings and buzzes, and keeps nagging until you scan the NFC tag you placed next to your pills.

## How it works

- **Reminders:** starts with one; add or delete as many as you need. Each has its own time (circular clock picker), weekdays and NFC tag. A reminder can't be deleted while it's ringing.
- **Nudges:** a short two-tone ping and two short buzzes (Pillzilla's own sound, not the phone's default) at the set time, then +15, +30, +45, +60 min, then every 10 min. After 2 h 30 min the dose is logged as missed.
- **Clearing** only works with the reminder's own tag, matched by the tag's serial number (UID). A scan up to 5 min early also counts.
- **Background scanning:** when a tag is assigned, Pillzilla writes a small NDEF record (its own MIME type plus an Android Application Record, about 60 bytes, so NTAG213 is plenty) to it. Android then opens Pillzilla directly when the tag is scanned, even if the app is closed. The phone has to be unlocked with the screen on for NFC to read.
- **Lost tag:** a reminder's tag can't be changed while that reminder is active, only after it ends. Losing a tag never skips a dose.
- **Log:** week and month views with on-time rate, misses, average delay and points.
- **Points and rewards:** points for doses within 20 min or late, perfect-day bonus, streak milestones with multipliers, an optional weekly streak shield, and your own rewards to redeem. All values are editable.

## Build

Requires JDK 17 and the Android SDK (API 35).

```
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

The APK ends up in `app/build/outputs/apk/debug/`. Every push and pull request also builds it on GitHub Actions; download it from the run's **Artifacts** section.

Android 12 (API 31) or newer, NFC required.

## Install

The easiest way is Droid-ify (or F-Droid): open https://mousaka20.github.io/pillzilla/ on your phone and tap the link to add the repository, then install Pillzilla from it. Updates then arrive automatically.

You can also sideload the APK (enable "Install unknown apps" for your browser or file manager). On first launch, Pillzilla shows banners for the permissions it needs: notifications, alarms and reminders, full-screen alarms (Android 14+), NFC, and exclusion from battery optimisation.

## Project layout

- `data/` Room database: reminders, doses (occurrences), points ledger, rewards
- `alarm/` timing rules (`Schedule`), AlarmManager scheduling, notifications, boot handling
- `nfc/` tag reading and writing, background scan activity
- `points/` points rules, streaks and rewards logic
- `ui/` Jetpack Compose screens and the full-screen alarm

## Releases (F-Droid / Droid-ify)

`.github/workflows/release.yml` builds a signed release APK, attaches it to a GitHub release and publishes an F-Droid repository on GitHub Pages. Droid-ify and F-Droid can subscribe to that repository.

One-time setup:

1. Create a signing key and **keep a backup** (without it, phones can't install updates):
   ```
   keytool -genkeypair -v -keystore pillzilla-release.jks -alias pillzilla -keyalg RSA -keysize 4096 -validity 10000
   ```
2. Add these repository secrets (Settings > Secrets and variables > Actions):
   - `PZ_KEYSTORE_BASE64`: output of `base64 -w0 pillzilla-release.jks` (macOS: `base64 -i pillzilla-release.jks`)
   - `PZ_KEYSTORE_PASSWORD`, `PZ_KEY_PASSWORD`: the passwords you chose
   - `PZ_KEY_ALIAS`: `pillzilla`
3. Settings > Pages > Build and deployment > Source: **GitHub Actions**.

For each release:

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.
3. Tag and push: `git tag v<versionName> && git push origin v<versionName>`.

The run summary shows the repository address and fingerprint. A debug APK and a release APK are signed with different keys, so uninstall a debug build before installing from the repository.

The `fastlane/` metadata also has the layout F-Droid's main repository and IzzyOnDroid expect, if you want to submit it there later.
