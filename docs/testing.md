# Testing

SPEC.md is explicit: "Do not claim something was tested if it was not actually tested." This document tracks the required scenarios against what has genuinely been run, and by what.

## Backend — automated, actually run, passing

`cd backend && ./mvnw test` — 13 integration tests (`@SpringBootTest` + `TestRestTemplate` against an in-memory H2 database; no MySQL/Docker needed, see the tradeoff noted in `src/test/resources/application-test.yml`) plus 3 unit tests in `JwtServiceTest` covering the deployed profile's refusal to start with the development JWT secret. 16/16 passing at the last run.

Also verified by hand against real MySQL 8 (2026-09-19): booting against an **empty** database runs `V1__init.sql` via Flyway and passes Hibernate's schema validation; booting against a database that already had Hibernate-created tables baselines without re-running V1 and leaves the data untouched (row counts compared before/after).

| # | SPEC.md scenario | Test | Verified |
|---|---|---|---|
| 1 | Safe arrival through geofence | `TripFlowTest.arrivalGeofenceCompletionIsIdempotent` | Backend endpoint + idempotency. The Android geofence *trigger* itself is not exercised by this test - see the Android section below. |
| 2 | Manual "I'm Safe" | `TripFlowTest.confirmSafeIsIdempotentAndStopsLocationSharingAfterCompletion` | Yes, including the idempotent double-call and post-completion location purge. |
| 3 | Missed arrival → nudge → escalation → watcher alert | `SosAndEscalationTest.missedArrivalNudgesThenEscalatesViaScheduler` | Yes - directly drives `EscalationScheduler.tick()` twice, asserts nudge then escalation, asserts an `ESCALATION` alert row exists. |
| 4 | Manual SOS | `SosAndEscalationTest.manualSosEscalatesTripAndCreatesAlertAndDeduplicatesRepeatedTaps` | Yes, including the duplicate-tap dedup. |
| 5 | Physical ESP32 SOS → BLE → Android → backend → watcher alert | `WearableFlowTest.pairDeviceThenRelayWearableSos` | **Only the Android→backend leg** (via the `/api/sos/wearable` endpoint, as if Android had already received a BLE notification). The actual ESP32 firmware → BLE → Android leg has **not** been run on real hardware - see below. |
| 6 | Fake incoming call without safety alert | — | Nothing to test on the backend: the fake call is a local-only Android simulation with zero backend interaction by design. See Android section. |
| 7 | Privacy after trip completion | `TripFlowTest.confirmSafeIsIdempotentAndStopsLocationSharingAfterCompletion` | Yes - asserts `204` on latest-location after completion and `409` on a new location push. |
| 8 | Unauthorized trip/location access rejection | `TripFlowTest.unauthorizedUserCannotAccessAnotherUsersTrip`, `.watcherWithoutLocationPermissionCannotSeeLocation` | Yes - IDOR rejection (403, not a data leak or a 404) and watcher-permission enforcement. |
| 9 | Duplicate event handling | `SosAndEscalationTest` (SOS dedup), `TripFlowTest.arrivalGeofenceCompletionIsIdempotent`, `WearableFlowTest` | Yes at the backend layer. |
| 10 | Poor connectivity / retry behaviour | — | Not covered by an automated test (would need a network-fault-injection harness). The client-side behaviour (OkHttp retry-on-connection-failure, best-effort location pushes, SOS de-dup guard) is implemented but only exercised by reading the code, not by a test that actually severs a connection mid-request. |

Also covered, beyond the required list: registration/login/duplicate-email/wrong-password (`AuthFlowTest`), missing-token rejection, wearable pairing + ownership check on unpair (`WearableFlowTest.cannotUnpairAnotherUsersDevice`), standalone SOS with no active trip.

```
cd backend
./mvnw test
```
Last run in this repository's history: 13/13 passing (see the commit that added the backend for the exact console output).

## Android — builds and runs; scenarios not yet walked end-to-end

The Android module (`android/`) compiles with the real Android Gradle Plugin (`assembleDebug`, verified 2026-09-20 after the switch from Google Maps SDK to osmdroid) and has been launched in the emulator against the local backend: registration/login and the Trusted Circle → add-contact flow were exercised by hand (that's how the FAB-overlap bug in `MainActivity` was found and fixed), plus, after the SheGuard redesign (2026-09-21), the Home, Circle, dark-mode Home and Active Trip screens were captured on the emulator with real backend data (screenshots reviewed), and the New Trip map was driven via `adb` on a Pixel 6 emulator: OpenStreetMap tiles render with attribution, a tap places the destination marker and zooms in, and zoom-15 tiles load (screenshots reviewed). Trip creation end-to-end, geofence arrival, SOS, fake call and the watcher views (including the watcher's map) have **not** yet been tapped through by a human on this build. Before trusting it:

1. ~~Replace the placeholder `google-services.json`~~ Done 2026-09-21: with the real Firebase project (`pinghome1234`, free Spark plan) wired into both the app and the backend, the emulator registered an FCM token with the backend (`PUT /api/users/me/fcm-token` → 204) and a manual SOS on another user's trip produced a real push notification on the emulator ("<traveller> triggered an SOS / Destination: …") via Firebase - scenario 4's watcher-alert leg is verified end-to-end, not just logged.
2. Manually walk scenarios 1, 2, 4, 6, 9 (client side) end-to-end against a running backend, on an emulator image with Google Play services.
3. Scenario 1 specifically needs a real or simulated location change crossing the geofence boundary (Android Studio's emulator location controls, or a GPX route, can simulate this without leaving your desk).
4. Check the two map screens load OpenStreetMap tiles (they need internet; the emulator has it by default).

## ESP32 — not run, because there's no ESP32 hardware in the environment this was built in

Firmware source is at `wearable/pinghome-sos/`. It has been **compiled** (`pio run`, espressif32 platform, `esp32dev` board): build succeeded, producing `firmware.bin` - Flash 85.2% (1,116,193 / 1,310,720 bytes, mostly the BLE stack), RAM 12.1% (39,492 / 327,680 bytes). That confirms the code is syntactically and type-correct against the real ESP32 Arduino/BLE APIs, nothing more.

Flashing it to a real board, wiring the button, and confirming the physical-press → BLE-notify → Android leg of scenario 5 has **not** been done — that requires hardware this environment doesn't have. This is the single most important thing to verify manually before calling PingHome's wearable integration complete, since SPEC.md is explicit that a software test trigger (see `wearable.md`'s note on the debug-only simulate button) must never stand in for it.

## What "done" looks like for this document

Every row above that says "not run" needs a human, with real hardware/an emulator, replacing that line with an actual pass/fail and (for bugs found) a fix. Don't edit this file to claim something passed without having actually run it.
