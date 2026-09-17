# PingHome — Project Specification


## Project Overview

PingHome is an academic personal-safety project: "A wearable + app safety companion that watches over solo commuters — automatically, quietly, and only when it matters."

It is a complete, integrated system — not a collection of disconnected demos — combining:
- A native Android mobile application (traveller + watcher roles in one app)
- A Spring Boot backend (owns authoritative state)
- MySQL (local for dev, cloud-hosted for deployment)
- At least three genuinely integrated cloud services
- An ESP32 BLE wearable with a physical SOS button (mandatory — never simulated)

At the time this file was written, the repository was empty. Nothing has been implemented yet.

## Development Principles (read before doing anything)

1. Inspect the repository before changing existing work.
2. Make a plan before major implementation.
3. Work incrementally; keep the project buildable; run tests/builds frequently.
4. Fix errors rather than merely reporting them.
5. Use Git checkpoints before major architectural changes.
6. **Never modify or delete `C:\Users\ayush\.git`.** All PingHome work stays inside `C:\Users\ayush\Documents\PingHome`, which has its own independent git repo.
7. Do not repeatedly ask about minor implementation decisions — make reasonable professional engineering calls when details are ambiguous, and preserve every mandatory requirement below.
8. Do not claim something was built, tested, or integrated if it wasn't actually done. No placeholder implementations, TODOs, fake API responses, hard-coded data, or fake wearable/connection states for mandatory features.

## Technology Stack

**Android:** Android Studio, Java, XML layouts, Android Jetpack, Material Design, ViewModel, repository-style architecture, Retrofit/OkHttp (or equivalent), Google Maps SDK, Fused Location Provider, Android Geofencing API, BLE, Firebase Cloud Messaging.

**Backend:** Java, Spring Boot, Spring Web, Spring Security, JWT auth, BCrypt password hashing, Spring Data JPA/Hibernate, Maven, `@Scheduled` (or equivalent) for the escalation scheduler.

**Database:** MySQL. Local MySQL for development; deployed architecture must use cloud-hosted MySQL.

**Wearable:** ESP32, Arduino/C++, BLE, physical SOS push button, LED/status indication where useful, low-power/sleep behavior, keychain-style form factor.

## Cloud Requirement

At least **three** cloud services must be genuinely integrated (not just mentioned in docs). Preferred stack:

1. **Google Cloud Run** — hosts the Spring Boot backend.
2. **Google Cloud SQL for MySQL** — hosts the deployed database.
3. **Firebase Cloud Messaging** — primary push notification mechanism.
4. **Google Maps Platform** — maps and location/geospatial functionality.
5. **Twilio** (optional) — SMS fallback for critical alerts only; do not trigger unnecessarily during development.

Prioritize free tiers/trial credits; avoid unnecessary paid services. `docs/cloud-services.md` must eventually document, for each service used: purpose, integration points, configuration, credentials/API key handling, deployment steps, free-tier/trial limits, possible charges, and how to disable/delete paid resources after a demo.

## Architecture

```
Android App
    |
   HTTPS/REST
    |
Cloud-hosted Spring Boot Backend
    |
    +-- Cloud MySQL
    +-- Firebase Cloud Messaging
    +-- Twilio (where appropriate)
    +-- Google Maps Platform

ESP32 Wearable
    |
   BLE
    |
Android App
```

**Backend owns:** authentication, users, contacts, trips, watcher permissions, trip state, escalation, alerts, authorization, persistence. Escalation logic is authoritative on the backend (via scheduler) — it must not depend on the Android app being open.

**Android owns:** UI, local interaction, location/geofencing integration, BLE communication, API communication, notification handling, fake-call experience.

**ESP32 owns:** minimal logic only — physical button → BLE SOS event → Android. No business logic beyond debouncing/sending the event.

## Database Schema (minimum fields)

- **Users:** id, name, email, phone, password hash, role, timestamps
- **Contacts:** id, user id, name, phone, email, relationship, timestamps
- **Trips:** id, traveller/user id, destination name, latitude, longitude, expected arrival, grace period, status, nudge state/time, timestamps
- **Watchers:** trip id, contact id, permission level
- **Locations:** trip id, latitude, longitude, timestamp
- **Alerts:** trip id, alert type, triggered time, resolved state, message
- **Wearable devices:** user id, device name, BLE identifier, pairing state, last seen, timestamps

Schema may be normalized/expanded when technically justified, but must cover the above.

## Mandatory Functional Features

Do not remove, replace, or simplify away any of the following.

**Authentication:** registration, login, BCrypt hashing, JWT auth, user profile, session persistence, logout.

**Trusted Circle:** add/edit/remove trusted contacts; select contacts as trip watchers; permission levels, at minimum `ALERT_ONLY` and `LOCATION_DURING_TRIP`. Unrestricted permanent location access must never be the default.

**Trip Creation:** destination (with map/location support), expected arrival time, grace period, watcher selection, preview/confirm, start. Recommended states: `ACTIVE`, `COMPLETED`, `ESCALATED`, `CANCELLED`.

**Active Trip screen:** destination, expected arrival, safety status, manual "I'm Safe", destination geofencing, manual SOS, fake incoming call, appropriate info to authorized watchers.

**Automatic Arrival Detection:** use Android Geofencing API (not continuous GPS polling). On geofence entry: complete trip, stop monitoring, stop location sharing, record completion. Handle permission issues, background restrictions, geofence registration failures, connectivity problems, duplicate events.

**Manual "I'm Safe":** completes trip, stops monitoring/sharing, prevents unnecessary escalation, must be idempotent.

**Automatic Escalation flow:**
1. Trip `ACTIVE` → expected arrival passes → grace period passes.
2. Traveller gets a nudge ("Still safe? Tap to confirm.").
3. Confirm → complete trip. No response within nudge timeout → escalate: notify authorized watchers, provide latest appropriate location, persist the alert.
Backend owns authoritative escalation state via a scheduler (e.g. `@Scheduled`); do not rely on the app being open.

**Manual SOS:** prominent action, highest priority, reaches backend, creates + immediately escalates an alert, notifies authorized watchers with latest location, persists the event, prevents accidental duplicate submissions without hindering use in an emergency.

**Fake Incoming Call:** local-only simulated incoming call UI, configurable caller name/avatar, accept/decline, no real call placed, must never itself create a safety alert.

**ESP32 Wearable (mandatory, never simulated as a replacement):** physical SOS button, BLE communication, status indication, low-power behavior, keychain-style concept. Primary job: physical button → BLE SOS event → Android.

**BLE — Android side:** scan, identify, connect, discover GATT service/characteristic, subscribe/read SOS characteristic, detect SOS, handle connect/disconnect and reconnect, show wearable connection status.

**BLE — ESP32 side:** advertise BLE service, expose SOS characteristic, detect + debounce button presses, prevent accidental repeats, send SOS event, low-power behavior where practical. A software test trigger may exist only as an optional dev fallback — it must never replace real ESP32 integration.

## Notifications

FCM is the primary push mechanism (safety reminders, escalation, watcher alerts, trip completion). Twilio SMS is an optional critical fallback only. Never claim guaranteed delivery. Handle failed notifications, retries where appropriate, duplicate prevention, network/API failures.

## Location Privacy

- Before a trip: no tracking.
- During an active trip: location used only for geofencing and authorized watcher visibility per permission level.
- After normal completion: stop monitoring and sharing immediately; no continued tracking.
- After escalation: authorized watchers may receive latest appropriate location per permission.
- All location endpoints require authentication + authorization; users must never access another user's trip/location by changing an ID (IDOR).
- Minimize/purge unnecessary historical location data after completion/resolution where practical.

## Security

Implement: BCrypt hashing, JWT auth, authorization, ownership checks, watcher permission checks, role checks where appropriate, input validation, safe error handling, env/local config for secrets.

Never: store plaintext passwords, expose password hashes, hard-code production secrets, expose arbitrary trip/location info, trust client-supplied IDs without authorization checks.

## Reliability

Account for poor connectivity: retry, queueing where appropriate, graceful failure, idempotency, duplicate prevention. SOS has highest priority. Do not claim guaranteed emergency delivery under all network conditions.

## UI Direction

Polished safety product, not a CRUD student project. Calm, discreet, trustworthy, modern, premium, practical.

Palette: deep navy/charcoal, off-white/light surfaces, muted blue; green = safe, amber = warning, red primarily reserved for SOS.

Use Material components, rounded cards, clean typography, generous spacing, accessible contrast. Avoid excessive red, childish graphics, unnecessary gradients, visual clutter, excessive animation, generic cybersecurity imagery.

## Demo Mode

Provide a documented `DEMO_MODE=true` configuration that may shorten grace periods, nudge timeout, and scheduler interval for demonstration purposes. It must not change fundamental safety logic, and must be clearly distinguished from normal configuration.

## Testing (must actually be performed, not just claimed)

1. Safe arrival through geofence.
2. Manual "I'm Safe".
3. Missed arrival → nudge → escalation → watcher alert.
4. Manual SOS.
5. Physical ESP32 SOS → BLE → Android → backend → watcher alert.
6. Fake incoming call without triggering a safety alert.
7. Privacy after trip completion.
8. Unauthorized trip/location access rejection.
9. Duplicate event handling.
10. Poor connectivity/retry behavior.

## Code Quality

Avoid: placeholder implementations of mandatory features, TODOs for mandatory functionality, fake API responses, hard-coded user data, a fake wearable replacing the real one, fake connection states, unused dependencies, unnecessary duplication, giant controllers, business logic in UI, insecure authentication.

Prefer: separation of concerns, maintainability, readable code, reusable components, appropriate validation, meaningful tests.

## Documentation

Eventually the repo should contain:

```
README.md
docs/
    requirements.md
    architecture.md
    database.md
    api.md
    setup.md
    deployment.md
    cloud-services.md
    testing.md
    wearable.md
```

Covering: purpose, requirements, architecture, tech stack, database, APIs, Android setup, backend setup, local database, cloud services, deployment, Firebase, Google Maps, Twilio, ESP32, BLE protocol, testing, demo mode, and limitations. `cloud-services.md` must explicitly name the ≥3 cloud services actually used and their roles.

## Out of Scope

Do not add: police/emergency dispatch APIs, AI danger detection, smartwatch apps, unnecessary social features, "Walk Me Home" systems, duress PIN systems, or complicated enterprise infrastructure. These may be mentioned as future scope only.
