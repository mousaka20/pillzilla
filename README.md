# Pillzilla

An Android app that makes sure you take your meds on time. Each of up to three daily reminders rings like an alarm clock and keeps nagging until you scan the NFC tag you placed next to your pills.

## How it works

- **Three reminders**, each with its own time (circular clock picker), weekdays and NFC tag.
- **Nudges** at the set time, then +15, +30, +45, +60 min, then every 10 min. After 2 h 30 min the dose is logged as missed.
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

Sideload the APK (enable "Install unknown apps" for your browser or file manager). On first launch, Pillzilla shows banners for the permissions it needs: notifications, alarms and reminders, full-screen alarms (Android 14+), NFC, and exclusion from battery optimisation.

## Project layout

- `data/` Room database: reminders, doses (occurrences), points ledger, rewards
- `alarm/` timing rules (`Schedule`), AlarmManager scheduling, notifications, boot handling
- `nfc/` tag reading and writing, background scan activity
- `points/` points rules, streaks and rewards logic
- `ui/` Jetpack Compose screens and the full-screen alarm
