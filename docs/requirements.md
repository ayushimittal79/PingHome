# Requirements

Source of truth for these requirements is the project-level `SPEC.md` in the repository root; this document summarizes it for readers who don't need the engineering-process instructions that file also carries.

## Purpose

PingHome watches over a solo commuter during a single planned trip and gets help moving — quietly by default, loudly (escalation, SOS) only when something looks wrong.

## Mandatory functional scope

- **Authentication**: registration, login, BCrypt + JWT, profile, session persistence, logout.
- **Trusted Circle**: add/edit/remove contacts; assign them as trip watchers with `ALERT_ONLY` or `LOCATION_DURING_TRIP` permission. Unrestricted permanent location access is never the default.
- **Trip lifecycle**: create (destination, expected arrival, grace period, watchers, preview/confirm) → `ACTIVE` → `COMPLETED` / `ESCALATED` / `CANCELLED`.
- **Active trip screen**: destination, expected arrival, safety status, manual "I'm Safe", destination geofencing, manual SOS, fake incoming call, wearable connection status.
- **Automatic arrival detection** via Android geofencing (not continuous GPS polling), idempotent, handling permission/background/geofence-registration/connectivity failures.
- **Manual "I'm Safe"**: idempotent completion, stops monitoring/sharing.
- **Automatic escalation**: backend-owned state machine — expected arrival + grace period elapses → nudge → confirm or escalate → notify watchers with latest location. Does not depend on the app being open.
- **Manual SOS**: highest priority, reaches the backend, escalates immediately, notifies watchers, de-duplicates accidental repeats.
- **Fake incoming call**: local-only simulation, configurable caller name, accept/decline, never creates a safety alert.
- **ESP32 wearable**: mandatory physical hardware — a real button, real BLE, never a simulated stand-in for the actual device. Minimal firmware logic: button → BLE SOS event → Android.
- **BLE**: Android scans/connects/discovers GATT/subscribes/detects SOS/handles disconnects with bounded reconnect and shows connection status; ESP32 advertises, debounces, and notifies.

## Non-functional requirements

- **Privacy**: no tracking before a trip; location used only for geofencing + permitted watcher visibility during an `ACTIVE`/`ESCALATED` trip; stops immediately on completion; historical location purged on completion.
- **Security**: BCrypt, JWT, ownership + watcher-permission authorization on every trip/location endpoint, no IDOR, no plaintext secrets, safe error responses.
- **Reliability**: graceful handling of poor connectivity, idempotent safety-critical operations, SOS treated as highest priority — without ever claiming guaranteed delivery under all network conditions.
- **Cloud**: at least three genuinely integrated cloud services (Cloud Run, Cloud SQL, FCM are the primary three; Maps Platform and optional Twilio SMS round it out) — see [cloud-services.md](cloud-services.md).
- **UI**: calm, trustworthy, modern — navy/charcoal + off-white, green=safe, amber=warning, red reserved for SOS. No generic cybersecurity imagery, no excessive animation.

## Explicitly out of scope

Police/emergency dispatch integration, AI danger detection, a separate smartwatch app, social features, "Walk Me Home" live-follow mode, duress PINs, enterprise infrastructure. These may be mentioned as future scope but are not built here.
