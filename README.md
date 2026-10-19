# GPS Tracker 📍

Records your tracks, saves them on the phone, and shows the **elevation history** of every track. **Urdu, English and Arabic** (Urdu/Arabic are right-to-left). Portrait only. Works fully offline.

## Record
- Start / Pause / Resume / Stop & save. An optional track name box (text you type is **black on a white box**).
- Live: time, distance, current speed, average speed (while moving), elevation, ascent, descent, the route drawn so far and the **live elevation profile** (touch and drag on it to read any point).
- GPS status: accuracy (green / amber / red) and satellites used. Shows "GPS is off" or "waiting for fix" when needed.
- **Keeps recording with the screen off or the app closed** (a small ongoing notification appears — this is how Android allows it).
- Every point is written to the phone's storage as it arrives: if the app is killed or the battery dies, the unfinished recording is recovered and saved at the next start.

## Tracks
- List of saved tracks with distance, time and climb, plus totals.
- Tap a track: route, **elevation profile** (lowest/highest, ascent, descent), distance, duration, moving time, average and max speed.
- Rename, delete, and **Export GPX** (you choose where to save it; GPX opens in Google Earth, Garmin, Strava, Komoot, etc.).

## Settings
Language, units (km/m or mi/ft), recording interval (1 s / 3 s / 10 s), ignore weak GPS fixes (20 / 40 / 100 m), keep the screen on while recording.

## How the numbers are measured
- **Distance** is measured on a smoothed path, not by adding every raw GPS wobble (raw adding can show several times the real distance). Standing still adds nothing.
- **Time moving and top speed** use the GPS receiver's own speed, which does not jitter.
- **Elevation** is the GPS altitude (above sea level on Android 14+ phones), smoothed; changes smaller than 3 m are not counted as climb. GPS altitude can be several metres off, especially under trees or between tall buildings, so treat ascent/descent as good estimates, not survey values.

Android 7.0 (API 24) or newer. Permissions: Location (needed), Notifications (to show the recording notification), keep-awake while recording.

Brought to you By: Black Falcon 🦅 (shown in the lower right corner)

## Build
Push to GitHub -> Actions -> "Build APK" -> download `GpsTracker-APK`.
Local: `gradle assembleDebug` (JDK 17, Gradle 8.7+).
