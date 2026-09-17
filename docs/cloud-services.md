# Cloud services

PingHome genuinely integrates three cloud services in code (not just in documentation), plus two more that round out the product. None of these accounts have been provisioned by the assistant that wrote this code — you'll need to create them yourself following the steps below before anything cloud-hosted actually runs.

## 1. Google Cloud Run — hosts the backend

**Purpose:** runs the Spring Boot backend as a container, autoscaling to zero when idle (important for keeping an academic project's bill at $0 between demos).

**Integration:** `backend/Dockerfile` builds a self-contained JRE image; `application.yml` reads `PORT` (Cloud Run injects this) and every secret/connection value from environment variables, so the same jar runs unmodified locally and on Cloud Run.

**Setup:** see [deployment.md](deployment.md) for the exact `gcloud run deploy` command.

**Free tier:** Cloud Run's always-free tier includes 2 million requests/month and 360,000 GB-seconds of memory — a coursework demo won't come close. Configure `min-instances=0` (the deploy command in deployment.md already does) so it scales to zero and costs nothing while not in use.

**Turning it off after a demo:** `gcloud run services delete pinghome-backend --region <region>`.

## 2. Google Cloud SQL for MySQL — the deployed database

**Purpose:** the persistent store for users/trips/contacts/watchers/locations/alerts/wearable_devices described in [database.md](database.md).

**Integration:** `application-cloud.yml` + `application.yml`'s `DB_URL`/`DB_USERNAME`/`DB_PASSWORD` env vars point Spring Data JPA at it exactly like local MySQL — no code difference.

**Setup:** see [deployment.md](deployment.md).

**Free tier / cost:** Cloud SQL has **no** always-free tier (unlike Cloud Run) — it bills for the instance whenever it exists, even idle, typically a few dollars/day for the smallest tier (`db-f1-micro`). Google Cloud's free trial credit ($300, 90 days on a new account as of this writing — verify current terms at cloud.google.com/free) comfortably covers a semester's development if you stop the instance between sessions.

**Cost control:**
- Use the smallest tier (`db-f1-micro`, 10GB SSD).
- `gcloud sql instances patch pinghome-db --activation-policy=NEVER` stops billing for compute between sessions (storage still bills a small amount).
- **Delete entirely after the project is graded:** `gcloud sql instances delete pinghome-db`.

## 3. Firebase Cloud Messaging — primary push notifications

**Purpose:** the primary notification channel for safety nudges, escalation alerts, watcher alerts, and trip-completion pushes, per SPEC.md.

**Integration:** `backend/.../notification/FcmService.java` wraps the Firebase Admin SDK; `android/.../notification/PingHomeFirebaseMessagingService.java` receives them. The backend reads a service-account credential from `PINGHOME_FIREBASE_CREDENTIALS_JSON` (or `_PATH`) — **without it configured, `FcmService` logs what it would have sent and returns `false` instead of throwing**, so the rest of the backend stays fully testable without live Firebase credentials (see the 13 passing backend tests, none of which need a real Firebase project).

**Setup:**
1. Create a Firebase project at console.firebase.google.com (Firebase projects wrap a normal GCP project, so this can be the same project as Cloud Run/Cloud SQL).
2. Add an Android app with package name `com.pinghome.app`; download `google-services.json` and replace the placeholder at `android/app/google-services.json`.
3. Project settings → Service accounts → Generate new private key → paste the JSON contents into `PINGHOME_FIREBASE_CREDENTIALS_JSON` (or save the file and point `PINGHOME_FIREBASE_CREDENTIALS_PATH` at it) on the backend.

**Free tier:** FCM is free, unlimited, no billing account required for messaging itself.

**Delivery is best-effort**, not guaranteed — see [testing.md](testing.md) and the reliability notes in SPEC.md. Nothing in this codebase claims otherwise.

## 4. Google Maps Platform — maps & destination picking

**Purpose:** the destination-picker map in trip creation and the last-known-location map in the watcher trip detail screen (`android/.../ui/trip/CreateTripActivity.java`, `.../ui/watcher/WatcherTripDetailActivity.java`).

**Integration:** Android-only — the Maps SDK for Android reads an API key from `android/app/build.gradle`'s `MAPS_API_KEY` manifest placeholder.

**Setup:**
1. In the same GCP project, enable "Maps SDK for Android" (APIs & Services → Library).
2. Create an API key (APIs & Services → Credentials), restrict it to Android apps with package `com.pinghome.app` and your debug/release SHA-1 fingerprints.
3. Set it via `-PMAPS_API_KEY=...` on the Gradle command line, or add `MAPS_API_KEY=...` to a local (never-committed) `android/local.properties`-style file and wire it into `build.gradle` — see [setup.md](setup.md).

**Free tier:** Google Maps Platform gives $200/month of free usage credit, which is thousands of map loads — a demo won't come close to a charge. Keep the key restricted to your app so it can't be abused if it ever leaks.

**Turning it off:** delete the API key, or disable the Maps SDK for Android API, in Cloud Console.

## 5. Twilio — optional SMS fallback (off by default)

**Purpose:** an optional critical-alert SMS fallback for escalation/SOS, in case a watcher's FCM push doesn't land (no connectivity, app uninstalled, etc).

**Integration:** `backend/.../notification/SmsService.java`, gated by `pinghome.sms.enabled` (defaults to **`false`**). `NotificationDispatcher` only calls it for `critical` sends, and only when the FCM push already failed. It is never triggered during ordinary development/demo runs unless you explicitly opt in.

**Setup (only if you want this feature live):**
1. Create a Twilio trial account (free, no card required for the trial); trial accounts can only SMS **verified** numbers, which is fine for a demo where you control the watcher's phone.
2. Grab the Account SID, Auth Token, and your Twilio trial phone number from the console.
3. Set `PINGHOME_SMS_ENABLED=true`, `PINGHOME_TWILIO_ACCOUNT_SID`, `PINGHOME_TWILIO_AUTH_TOKEN`, `PINGHOME_TWILIO_FROM_NUMBER`.

**Free tier / cost:** Twilio's trial includes a small free credit; SMS beyond that is billed per message (fractions of a cent to a few cents depending on destination). Leave `PINGHOME_SMS_ENABLED=false` (the default) unless you specifically want to demo the fallback path, to guarantee zero Twilio charges.

**Turning it off:** set `PINGHOME_SMS_ENABLED=false` (or just don't set it — that's the default) and/or delete the Twilio account.

## Summary table

| Service | Required? | Default state | Monthly cost if left running |
|---|---|---|---|
| Cloud Run | Yes (deployment) | scales to zero | $0 (always-free tier covers coursework usage) |
| Cloud SQL | Yes (deployment) | must be stopped manually between uses | a few $/day if left running; $0 if stopped |
| Firebase Cloud Messaging | Yes (primary notifications) | no-ops without credentials | $0 (free) |
| Google Maps Platform | Yes (Android maps) | inert without a key | $0 within the $200/month credit for a demo's usage |
| Twilio | No (optional fallback) | disabled | $0 while disabled |
