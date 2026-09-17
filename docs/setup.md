# Local setup

## Prerequisites

- JDK 21
- MySQL 8.x running locally (or point `DB_URL` at any reachable MySQL instance)
- Android Studio (Koala/2024.1+) with an SDK for API 34 and an emulator or device on API 26+
- PlatformIO (`pip install platformio`) or the PlatformIO IDE extension, for the ESP32 firmware
- A physical ESP32 dev board + push button for the wearable (mandatory per SPEC.md — there is no way to fully exercise the SOS-over-BLE flow without real hardware)

## Backend

```bash
cd backend
cp .env.example .env   # then edit values, or export them in your shell / IDE run config
./mvnw spring-boot:run
```

Minimum local setup: a MySQL database named `pinghome` (`CREATE DATABASE pinghome;`) reachable with the credentials in `DB_URL`/`DB_USERNAME`/`DB_PASSWORD`. Everything else (Firebase, Twilio, Maps) is optional locally — the backend boots and is fully testable without any of them configured (see [cloud-services.md](cloud-services.md) for what happens when they're absent).

Run the test suite (uses an in-memory H2 database, no MySQL needed):

```bash
./mvnw test
```

Demo mode (shorter grace periods / nudge timeout / scheduler interval, for a live demonstration):

```bash
SPRING_PROFILES_ACTIVE=demo DEMO_MODE=true ./mvnw spring-boot:run
```

## Android

1. Open `android/` in Android Studio and let it sync (first sync will download the Gradle wrapper distribution and dependencies — needs internet).
2. Replace `android/app/google-services.json` with your own from the Firebase console (see [cloud-services.md](cloud-services.md)) — the checked-in file is a non-functional placeholder.
3. Supply a Google Maps API key: either pass `-PMAPS_API_KEY=...` to Gradle, or add a `gradle.properties` entry (a personal, non-committed one under `~/.gradle/gradle.properties`, or `android/local.properties` wired into `build.gradle`) — do not hard-code it into `build.gradle` itself.
4. Point the app at your backend: `PINGHOME_API_BASE_URL_DEBUG` Gradle property (defaults to `http://10.0.2.2:8080/`, which reaches a backend running on your host machine from the Android emulator).
5. Run on an emulator or device with Google Play services (required for Maps, FCM, Location/Geofencing).

The app cannot be built/run inside the sandbox this codebase was originally written in — no Android SDK is available there. It has been checked for structural correctness (balanced braces, well-formed XML, every `R.id`/`R.layout`/manifest-component reference resolving to a real definition) but **not compiled with the real Android Gradle Plugin**. Build it in Android Studio and fix anything AGP flags before relying on it — see [testing.md](testing.md) for exactly what has and hasn't been verified.

## Wearable (ESP32)

See [wearable.md](wearable.md) for wiring and the BLE protocol. Quick version:

```bash
cd wearable/pinghome-sos
pio run                 # build
pio run -t upload       # flash (board connected over USB)
pio device monitor -b 115200
```

## Putting it together for a demo

1. Start MySQL and the backend (demo profile).
2. Start the Android app pointed at that backend (emulator: default `10.0.2.2` works out of the box; physical device: point it at your machine's LAN IP and make sure the backend binds to `0.0.0.0`, which Spring Boot does by default).
3. Pair the ESP32 wearable from the app's Profile → PingHome wearable screen.
4. Add a second account (a watcher) — either a second emulator/device, or log the same backend into a second app instance — and add it as a trusted contact using that account's email.
