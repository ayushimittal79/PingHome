# Architecture

## System diagram

```
Android App (Java)
    |
   HTTPS/REST + JWT
    |
Spring Boot Backend (Azure VM, systemd + Caddy for HTTPS)
    |
    +-- Azure Database for MySQL      (all persistent state)
    +-- Azure Application Insights    (telemetry/logs, via the Java agent)
    +-- Firebase Cloud Messaging      (primary push notifications)
    +-- Twilio                       (optional SMS fallback, off by default)

Android App also uses, on-device with no backend involvement:
    +-- Google Play Services          (Fused Location Provider, Geofencing API)
    +-- OpenStreetMap tiles (osmdroid) (map display - no API key or account)

ESP32 Wearable
    |
   BLE (custom GATT service, notify-only SOS characteristic)
    |
Android App
```

## UI design

The Android UI follows the "SheGuard" design language supplied as a Jetpack Compose prototype (teal/lavender light mode, crimson/maroon dark mode, pulsing SOS button, rounded cards and pills, rounded bottom navigation). It was ported into the existing Java/XML app rather than adopting the Compose code, so every screen keeps its backend wiring: `res/values/themes.xml` + `colors.xml` (theme tokens), `ui/components/SosButtonView` and `LiveBadgeView` (the two animated components), `ui/main/HomeFragment` (SOS-first landing screen), `ui/trip/TripsFragment` (My trips / Watching toggle). The Home screen's sun/moon toggle switches light/dark and is remembered (`ThemePrefs`). The prototype's static "Tips" screen was not ported.

## Who owns what

**Backend** (`backend/`) owns everything authoritative: authentication, users, contacts, trip state, watcher permissions, escalation, alerts, persistence, authorization. This is deliberate — a phone that's asleep, backgrounded, or killed by the OS must not be a single point of failure for escalation, so the escalation state machine (`EscalationScheduler`, `TripService`) runs entirely server-side on a `@Scheduled` poll, independent of any client.

**Android** (`android/`) owns UI, local interaction, the Fused Location Provider + Geofencing API integration, BLE communication with the wearable, API communication, notification handling, and the fake-call simulation. It never holds authoritative trip state — every mutating action (confirm safe, complete arrival, SOS, cancel) is a network call to the backend, and the backend's response is what the UI reflects.

**ESP32** (`wearable/`) owns almost nothing. It advertises a BLE service, debounces a physical button, and notifies a single-byte SOS event. It does not know about trips, users, or the backend — Android is the only thing it talks to, and only over BLE.

## Escalation state machine

Implemented in `backend/.../trip/TripService.java` and `backend/.../escalation/EscalationScheduler.java`.

```
ACTIVE
  |  expected arrival passes
  |  grace period passes
  v
ACTIVE (nudge sent) --- traveller confirms ---> COMPLETED
  |
  | nudge timeout elapses with no confirmation
  v
ESCALATED --- watchers notified (FCM, latest location if permitted) ---
```

`EscalationScheduler.tick()` runs on a configurable interval (`pinghome.scheduler.interval-ms`, shortened in demo mode) and does two things each pass: send a nudge to any `ACTIVE` trip whose `expectedArrival + gracePeriodMinutes` has passed and hasn't been nudged yet, and escalate any nudged trip whose `nudgeSentAt + nudgeTimeoutMinutes` has passed without a safety confirmation. Both `Trip.nudgeSentAt` and `Trip.status` are read/written only through this scheduler and `TripService`, so there's a single place that can move a trip into `ESCALATED`.

Manual "I'm Safe" (`TripService.confirmSafe`) and automatic arrival (`TripService.completeArrival`) are both idempotent: calling either on an already-`COMPLETED`/`ESCALATED`/`CANCELLED` trip is a no-op that returns the current state rather than erroring, which is what lets a duplicate geofence `ENTER` event or a double-tap be handled safely without ad-hoc client-side debouncing.

## Watcher access model

A traveller's trusted contact (`Contact`, owned by the traveller) is *not* automatically a PingHome account. `WatcherAccessService` resolves watcher access by matching the current authenticated user's account email against the traveller's contact email for that trip's `Watcher` rows — if a contact's email doesn't correspond to a registered account, that person simply can't open the trip in-app (they'd still need to be reachable through some other channel, which is out of scope here). This keeps the same Android app serving both traveller and watcher roles without a separate invite/accept flow, at the cost of requiring watchers to register with the same email their contact record uses.

Every trip/location/alert endpoint re-derives access through this service or through direct ownership checks (`TripService.findOwned`) — nothing trusts a client-supplied trip ID without checking who's asking. `TripFlowTest` in the backend test suite exercises this directly (an unrelated authenticated user gets `403`, not `200` with someone else's data, and not a `404` that would leak existence).

## Location privacy boundary

- Before a trip starts: nothing is tracked.
- During `ACTIVE`: the Android app pushes periodic position samples only while the Active Trip screen is open (`LocationTracker`) — arrival detection itself doesn't need continuous tracking, it needs the one-shot geofence transition, which works even if the screen is closed.
- On completion (manual or automatic): `TripService.completeTrip` purges the trip's location history (`LocationPingRepository.deleteByTripId`) and location updates are rejected by the API from that point on (`409 Conflict`).
- During `ESCALATED`: `LOCATION_DURING_TRIP` watchers can read the latest position via `GET /api/trips/{id}/locations/latest`; `ALERT_ONLY` watchers cannot, regardless of trip status.

## Why geofencing instead of GPS polling

SPEC.md is explicit about this, and it's also just the right engineering call: Android's Geofencing API is backed by Play services' fused location provider running a single shared, battery-optimized location subscription across every app that registers a geofence, rather than each app independently polling GPS. PingHome registers exactly one circular `ENTER` geofence per active trip (`GeofenceManager`) and lets the OS decide when to actually wake up and check — `ArrivalGeofenceReceiver` only runs when a transition actually fires.

## Known architectural tradeoffs

- **Location tracking is screen-bound, not a background service.** A traveller who starts a trip and then locks their phone in a pocket won't push new position samples to watchers until they reopen the Active Trip screen, though arrival detection still works via the geofence. A background foreground-location service would close this gap at the cost of more battery drain and a heavier permission ask — deliberately out of scope for this pass; see the Android UI hint comments in `LocationTracker.java`.
- **BLE reconnect keeps the phone's Bluetooth radio active in a foreground service** (`WearableWatchService`) whenever a wearable is paired, independent of whether a trip is active, so an SOS button press is caught at any time — this is a deliberate battery-for-reliability tradeoff.
