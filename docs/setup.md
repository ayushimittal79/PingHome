# Local setup

## Prerequisites

- JDK 21
- MySQL 8.x running locally (or point `DB_URL` at any reachable MySQL instance)
- Android Studio (Koala/2024.1+) with an SDK for API 34 and an emulator or device on API 26+
- For deployment: Azure CLI (`az`) — see [deployment.md](deployment.md)
- PlatformIO (`pip install platformio`) or the PlatformIO IDE extension, for the ESP32 firmware
- A physical ESP32 dev board + push button for the wearable (mandatory per SPEC.md — there is no way to fully exercise the SOS-over-BLE flow without real hardware)

## Backend

```bash
cd backend
cp .env.example .env   # then edit values, or export them in your shell / IDE run config
./mvnw spring-boot:run
```

Minimum local setup: a MySQL database named `pinghome` with a user that can use it, e.g.

```sql
CREATE DATABASE pinghome;
CREATE USER 'pinghome'@'localhost' IDENTIFIED BY 'pinghome';
GRANT ALL PRIVILEGES ON pinghome.* TO 'pinghome'@'localhost';
```

(those are the defaults in `application.yml`; override with `DB_URL`/`DB_USERNAME`/`DB_PASSWORD`). Flyway creates the tables on first boot. Everything else (Firebase, Twilio) is optional locally — the backend boots and is fully testable without any of them configured (see [cloud-services.md](cloud-services.md) for what happens when they're absent).

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
2. Replace `android/app/google-services.json` with your own from the Firebase console (see [cloud-services.md](cloud-services.md)) — the checked-in file is a non-functional placeholder. Nothing else needs a key: the map uses OpenStreetMap tiles via `osmdroid`.
3. Point the app at your backend: `PINGHOME_API_BASE_URL_DEBUG` Gradle property (defaults to `http://10.0.2.2:8080/`, which reaches a backend running on your host machine from the Android emulator). Release builds take `PINGHOME_API_BASE_URL`, set to the deployed backend in `android/gradle.properties` — see [deployment.md](deployment.md).
4. Run on an emulator or device image **with Google Play services** (required for FCM and for Location/Geofencing).
5. Release signing (optional, for a distributable APK): put a keystore somewhere outside the repo and pass `-PPINGHOME_KEYSTORE_FILE=... -PPINGHOME_KEYSTORE_PASSWORD=... -PPINGHOME_KEY_ALIAS=... -PPINGHOME_KEY_PASSWORD=...` (or set them in `~/.gradle/gradle.properties`, never in the repo). Without them `assembleRelease` signs with the debug key, which is enough for sideloading onto demo devices.

The app builds with the real Android Gradle Plugin (`./gradlew assembleDebug`) and runs in the emulator; see [testing.md](testing.md) for exactly which flows have and haven't been exercised by hand.

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
