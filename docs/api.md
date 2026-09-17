# API reference

Base URL: `http://localhost:8080/` locally, the Cloud Run URL in production (see [deployment.md](deployment.md)). Interactive docs are also served at `/swagger-ui.html` (springdoc-openapi) once the backend is running.

Every endpoint except `POST /api/auth/register` and `POST /api/auth/login` requires `Authorization: Bearer <jwt>`. Missing/invalid tokens get `403`; requests for a resource you don't own or watch get `403` (never a `404` that would leak existence, never someone else's data).

## Auth

| Method | Path | Body | Notes |
|---|---|---|---|
| POST | `/api/auth/register` | `{name, email, phone?, password}` | Returns `{token, user}` |
| POST | `/api/auth/login` | `{email, password}` | Returns `{token, user}` |

Logout is client-side only (discard the stored JWT) — stateless tokens have no server-side session to invalidate.

## Users

| Method | Path | Body | Notes |
|---|---|---|---|
| GET | `/api/users/me` | | Current profile |
| PUT | `/api/users/me` | `{name, phone}` | |
| PUT | `/api/users/me/fcm-token` | `{fcmToken}` | Called after the app obtains an FCM registration token |

## Trusted Circle (contacts)

| Method | Path | Body |
|---|---|---|
| GET | `/api/contacts` | |
| POST | `/api/contacts` | `{name, phone?, email?, relationship?}` |
| PUT | `/api/contacts/{id}` | same |
| DELETE | `/api/contacts/{id}` | |

## Trips

| Method | Path | Body | Notes |
|---|---|---|---|
| POST | `/api/trips` | `{destinationName, latitude, longitude, expectedArrival, gracePeriodMinutes, geofenceRadiusMeters?, watchers: [{contactId, permission}]}` | `permission` is `ALERT_ONLY` or `LOCATION_DURING_TRIP` |
| GET | `/api/trips/mine` | | Trips where you're the traveller |
| GET | `/api/trips/watching` | | Trips where your account is a registered watcher |
| GET | `/api/trips/{id}` | | Traveller or authorized watcher only |
| POST | `/api/trips/{id}/confirm-safe` | | Idempotent |
| POST | `/api/trips/{id}/complete-arrival` | | Called by the Android app on geofence entry; idempotent |
| POST | `/api/trips/{id}/cancel` | | |
| POST | `/api/trips/{id}/locations` | `{latitude, longitude}` | Traveller only, only while `ACTIVE` |
| GET | `/api/trips/{id}/locations/latest` | | `204` if none/not permitted; traveller always allowed, watcher needs `LOCATION_DURING_TRIP` and trip must be `ACTIVE`/`ESCALATED` |

## SOS

| Method | Path | Body | Notes |
|---|---|---|---|
| POST | `/api/sos` | `{tripId?, latitude?, longitude?, bleIdentifier?}` | In-app SOS button. `202 Accepted`. |
| POST | `/api/sos/wearable` | same | Relayed by the Android app after it receives the SOS characteristic notification from the ESP32. |

Both de-duplicate repeated SOS from the same traveller within `pinghome.sos.dedup-seconds` (default 15s).

## Alerts

| Method | Path | Notes |
|---|---|---|
| GET | `/api/alerts/mine` | Alerts on trips you travelled on |
| GET | `/api/alerts/trip/{tripId}` | Traveller or authorized watcher |
| PATCH | `/api/alerts/{id}/resolve` | Traveller or authorized watcher |

## Wearables

| Method | Path | Body |
|---|---|---|
| GET | `/api/wearables` | |
| POST | `/api/wearables` | `{deviceName, bleIdentifier}` |
| DELETE | `/api/wearables/{id}` | |

## Error shape

```json
{
  "timestamp": "2026-...",
  "status": 403,
  "error": "Forbidden",
  "message": "This trip does not belong to you",
  "path": "/api/trips/42",
  "fieldErrors": []
}
```
