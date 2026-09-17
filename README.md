# PingHome

A wearable + app safety companion that watches over solo commuters — automatically, quietly, and only when it matters.

PingHome is an academic capstone project combining a native Android app, a cloud-hosted Spring Boot backend, MySQL, Firebase Cloud Messaging, Google Maps/location services, and a physical ESP32 BLE wearable with a real SOS button into one integrated safety product — not a set of disconnected demos.

## What it does

A traveller starts a **trip**: a destination, an expected arrival time, a grace period, and a set of **trusted watchers** (contacts with either `ALERT_ONLY` or `LOCATION_DURING_TRIP` visibility). While the trip is active, PingHome:

- detects arrival automatically via a destination **geofence** (no continuous GPS polling),
- lets the traveller confirm safety manually with one tap (**"I'm Safe"**),
- **escalates automatically** on the backend if the traveller doesn't respond to a "still safe?" nudge after the grace period elapses — independent of whether the app is open,
- accepts a **manual SOS** from inside the app or from a **physical button on the ESP32 wearable** over BLE, which immediately escalates and notifies watchers with the traveller's last known location,
- offers a **fake incoming call** the traveller can trigger to get out of an uncomfortable situation, purely as a local simulation.

## Repository layout

```
backend/    Spring Boot 3 / Java 21 REST API - the authoritative source of trip/escalation state
android/    Native Android app (Java) - traveller + watcher UI, BLE, geofencing, FCM
wearable/   ESP32 firmware (Arduino/PlatformIO) - the physical SOS button over BLE
docs/       Requirements, architecture, database, API, setup, deployment, cloud services, testing
```

## Quick start

| Component | Command | Details |
|---|---|---|
| Backend | `cd backend && ./mvnw spring-boot:run` | [docs/setup.md](docs/setup.md) |
| Backend tests | `cd backend && ./mvnw test` | [docs/testing.md](docs/testing.md) |
| Android | Open `android/` in Android Studio | [docs/setup.md](docs/setup.md) |
| Wearable | `cd wearable/pinghome-sos && pio run -t upload` | [docs/wearable.md](docs/wearable.md) |

## Documentation

- [docs/requirements.md](docs/requirements.md) — what PingHome must do, and what it deliberately doesn't
- [docs/architecture.md](docs/architecture.md) — system design, ownership boundaries, the escalation state machine
- [docs/database.md](docs/database.md) — schema
- [docs/api.md](docs/api.md) — REST API reference
- [docs/setup.md](docs/setup.md) — local dev setup for all three components
- [docs/deployment.md](docs/deployment.md) — deploying the backend to Cloud Run + Cloud SQL
- [docs/cloud-services.md](docs/cloud-services.md) — which cloud services are used, why, and how to turn off billing after a demo
- [docs/testing.md](docs/testing.md) — what's actually been tested, and how
- [docs/wearable.md](docs/wearable.md) — wiring, BLE protocol, flashing the ESP32

## Status

Implemented end-to-end: backend (built, unit/integration tested, 13 passing tests), Android app (full source for every mandatory screen/flow), ESP32 firmware (BLE SOS peripheral). Not yet done by a human: buying/flashing physical hardware, provisioning real Firebase/Google Cloud/Twilio accounts, and running the manual test matrix in [docs/testing.md](docs/testing.md) against real devices. See each doc's "limitations" section for specifics — nothing here claims to be verified that hasn't actually been run.
