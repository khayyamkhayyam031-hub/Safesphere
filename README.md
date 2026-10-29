# Safesphere

**SafeHer — Your Safety, Your Power.** A women's safety and emergency-response app for Android, built with Java, XML layouts and a local SQLite database (no Firebase, no server).

## Features

- **Local accounts** — register and log in with a phone number and password (salted PBKDF2 hashes), profile setup, optional biometric app lock.
- **SOS** — press and hold for 2 seconds, then a cancellable countdown. SafeHer saves an emergency record, tries to get your location, and texts every guardian from your SIM, showing the real Sent / Delivered / Failed result for each one.
- **Guardians** — add, edit and remove trusted contacts (Tier 1 / Tier 2), or pick them from your contacts.
- **Safe Journey** — set a destination and expected arrival time; late journeys are marked Delayed and ask whether you're safe.
- **My Location** — current or last-known location with accuracy and age, opened in your maps app.
- **Fake Call** — a simulated incoming call on a timer.
- **Emergency Helplines** — 15, 1122, 1043 and 1098 (Pakistan), opened in the dialer.
- **Evidence** — visible audio recording saved to private app storage.
- **History and Settings** — filterable event history; settings for account, safety, privacy and security.

## Build and run

1. Open the project in Android Studio (JDK 21 is bundled), or use the command line:
   ```bash
   ./gradlew :app:assembleDebug
   ```
2. Install on a device with USB debugging on:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

Min SDK 24, target SDK 36.

## Project layout

```
app/src/main/java/com/example/safesphere/
├── auth/        Splash, Welcome, Login, Register, Profile Setup
├── home/        MainActivity (bottom navigation, shake SOS), HomeFragment
├── guardians/   Guardian list and add/edit
├── sos/         Countdown, emergency status, SMS sending and delivery receipts
├── journey/     Safe Journey
├── location/    Location helper and My Location screen
├── fakecall/    Simulated call
├── helplines/   Emergency numbers
├── evidence/    Audio evidence
├── history/     History list and details
├── settings/    Settings and safety preferences
├── database/    SQLiteOpenHelper and repositories
├── data/        Models
├── session/     Local session
└── utils/       Validation, hashing, threading, UI helpers
```

## Limitations

- Everything is stored on the phone. Guardians only hear from you by SMS: there is no live tracking or cloud backup, and nothing is uploaded.
- SOS texts use your SIM, so normal SMS charges apply.
- Shake SOS works only while the app is open on screen. Voice and power-button triggers are not implemented.
- Evidence recording stops when you leave its screen. There is no built-in map and no route-deviation detection.
- Accounts are device-local, so passwords can't be reset by SMS.
