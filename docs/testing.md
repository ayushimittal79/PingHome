# Testing

SPEC.md is explicit: "Do not claim something was tested if it was not actually tested." This document tracks the required scenarios against what has genuinely been run, and by what.

## Backend — automated, actually run, passing

`cd backend && ./mvnw test` — 13 integration tests, `@SpringBootTest` + `TestRestTemplate` against an in-memory H2 database (no MySQL/Docker needed to run them; see the tradeoff noted in `src/test/resources/application-test.yml`).

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

## Android — not run, because there's no Android SDK in the environment this was built in

The Android module (`android/`) has full source for every mandatory screen and flow, and has been checked for structural soundness (every Java file brace-balanced, every XML file well-formed, every `R.id`/`R.layout` reference and every manifest-declared component resolving to a real definition — see the commit history for the exact grep-based checks run). It has **not** been compiled with the real Android Gradle Plugin, run on an emulator, or exercised by a human tapping through it. Before trusting it:

1. Open in Android Studio, let Gradle sync, fix whatever AGP/lint flags that this environment couldn't catch.
2. Manually walk scenarios 1, 2, 4, 6, 9 (client side) end-to-end against a running backend.
3. Scenario 1 specifically needs a real or simulated location change crossing the geofence boundary (Android Studio's emulator location controls, or a GPX route, can simulate this without leaving your desk).

## ESP32 — not run, because there's no ESP32 hardware in the environment this was built in

Firmware source is at `wearable/pinghome-sos/`. Whether it *compiles* against the espressif32 PlatformIO platform is stated in this repository's setup notes/commit history rather than here, since that result depends on when you read this doc - run `pio run` yourself to check current status. Flashing it to a real board, wiring the button, and confirming the physical-press → BLE-notify → Android leg of scenario 5 has **not** been done — that requires hardware this environment doesn't have. This is the single most important thing to verify manually before calling PingHome's wearable integration complete, since SPEC.md is explicit that a software test trigger (see `wearable.md`'s note on the debug-only simulate button) must never stand in for it.

## What "done" looks like for this document

Every row above that says "not run" needs a human, with real hardware/an emulator, replacing that line with an actual pass/fail and (for bugs found) a fix. Don't edit this file to claim something passed without having actually run it.
