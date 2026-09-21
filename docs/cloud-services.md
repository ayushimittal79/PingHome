# Cloud services

PingHome integrates four cloud services in code (not just in documentation): three from
Microsoft Azure, which hosts the backend, plus Firebase Cloud Messaging from Google for push
notifications. An optional fifth (Twilio SMS) is off by default.

**Why Azure and not Google Cloud, which the original plan named:** Google Cloud's managed
database (Cloud SQL) has no free tier, and enabling billing on a Google Cloud account - which
Cloud SQL, Cloud Run *and* Google Maps all require - repeatedly failed for this project's
personal accounts (Google Payments error `OR_BACR2_59`). University-managed Google accounts were
deliberately not used (see [Account rules](#account-rules)). Azure's free account covers a
managed MySQL server and an always-on VM, which is what the backend's `@Scheduled` escalation
job needs. Nothing in the backend code was tied to Google Cloud: the only change was
deployment configuration. See [deployment.md](deployment.md).

## Account rules

- All cloud resources belong to a **personal** account. Nothing is deployed under the
  university's Google or Microsoft organisation - that would put project infrastructure on
  university-owned tenancy without authorisation.
- The Azure free account requires a Visa/Mastercard credit or non-prepaid debit card (a
  refundable US$1 verification hold), a phone number and a personal Microsoft account. It does
  not set up an autopay mandate.

## 1. Azure Virtual Machine — hosts the backend

**Purpose:** runs the Spring Boot jar as a systemd service, with Caddy in front for HTTPS.

**Integration:** `deploy/azure/provision.sh` creates it; `deploy/azure/setup-vm.sh` installs
Java 21, Caddy and the `deploy/azure/pinghome.service` unit; `deploy/azure/deploy.sh` ships the
jar. The app reads `PORT` and all secrets from `/etc/pinghome/pinghome.env`. The backend code
has no Azure dependency - the same jar runs locally with `./mvnw spring-boot:run`.

**Free tier:** 750 hours/month of a Standard_B1s VM (1 vCPU, 1 GB RAM) for 12 months - enough
to run continuously. The Standard public IP attached to it is **not** free (≈ US$3-4/month)
once the account is upgraded after its first 30 days.

**Why always-on matters:** `EscalationScheduler` polls active trips on a timer inside the JVM.
Serverless hosts that scale to zero (Cloud Run at `min-instances=0`, Render's free tier)
would pause it between requests - a silent failure for a safety product.

**Turning it off:** `az group delete --name pinghome-rg --yes` deletes the VM with everything else.

## 2. Azure Database for MySQL (flexible server) — the deployed database

**Purpose:** the persistent store for users/trips/contacts/watchers/locations/alerts/wearable
devices described in [database.md](database.md).

**Integration:** the same `DB_URL`/`DB_USERNAME`/`DB_PASSWORD` environment variables as local
MySQL, with `sslMode=REQUIRED` because Azure enforces TLS. Flyway creates the schema on first
boot; no code or migration changes were needed to move from local MySQL 8 to Azure MySQL 8.

**Free tier:** 750 hours/month of a Burstable Standard_B1ms server plus 32 GB storage and 32 GB
backup for 12 months. The provisioning script uses exactly those sizes.

**Cost control:** don't scale the tier or storage. The MySQL firewall only admits the VM's IP.

**Turning it off:** deleted with the resource group.

## 3. Azure Application Insights — telemetry and logs

**Purpose:** request/exception telemetry and the backend's logs (including the escalation
scheduler's `Sent safety nudge` / `Escalated trip` lines) in the Azure portal, without SSH.

**Integration:** the Application Insights Java agent (`-javaagent` in `pinghome.service`) reads
`APPLICATIONINSIGHTS_CONNECTION_STRING` from the env file. It instruments Spring MVC, JDBC and
logging automatically; the application code is unchanged.

**Free tier:** the first 5 GB/month of ingestion into the Log Analytics workspace is free with
no expiry; a demo backend produces a few megabytes.

**Turning it off:** deleted with the resource group.

## 4. Firebase Cloud Messaging — primary push notifications

**Purpose:** safety nudges, escalation alerts, watcher alerts and trip-completion pushes, per
SPEC.md. Firebase is a Google Cloud service; it is the one piece of the original Google plan
that survived, because it needs no billing account.

**Integration:** `backend/.../notification/FcmService.java` wraps the Firebase Admin SDK using a
service-account JSON (`PINGHOME_FIREBASE_CREDENTIALS_PATH` on the VM, `_JSON` or `_PATH`
locally). Without credentials it logs what it would have sent and returns `false` instead of
throwing, so the backend is fully testable without a Firebase project. The Android side is
`android/.../notification/PingHomeFirebaseMessagingService.java` plus `google-services.json`.

**Setup:**
1. console.firebase.google.com → create a project on a personal Google account (free "Spark"
   plan - do not add a billing account, it isn't needed).
2. Add an Android app with package `com.pinghome.app`; download `google-services.json` and
   replace the placeholder at `android/app/google-services.json`.
3. Project settings → Service accounts → Generate new private key → save as
   `backend/firebase-service-account.json` (gitignored). `deploy.sh` uploads it to the VM.

**Free tier:** FCM is free and unlimited. Delivery is best-effort, not guaranteed - see
[testing.md](testing.md) and the reliability notes in SPEC.md.

## Maps — OpenStreetMap via osmdroid (not a cloud account)

The destination picker (`CreateTripActivity`) and the watcher's last-known-location map
(`WatcherTripDetailActivity`) render OpenStreetMap tiles with the `osmdroid` library. This
replaced the Google Maps SDK named in the original plan because Google Maps requires a
billing-enabled Google Cloud project (see the top of this file). It needs no API key and no
account. Location itself - GPS via Fused Location Provider and the Android Geofencing API - is
Google Play Services on the device and was never affected; it needs no key or billing either.

Limitation worth stating honestly: the public OSM tile servers are volunteer-run with a
[usage policy](https://operations.osmfoundation.org/policies/tiles/). The app identifies itself
with its package name as required, and demo-scale traffic is well within policy; a real product
would use a commercial tile provider.

## 5. Twilio — optional SMS fallback (off by default)

**Purpose:** an optional critical-alert SMS fallback for escalation/SOS if a watcher's FCM push
doesn't land.

**Integration:** `backend/.../notification/SmsService.java`, gated by `pinghome.sms.enabled`
(default `false`). `NotificationDispatcher` only calls it for critical sends after an FCM
failure. Never triggered unless you explicitly opt in.

**Setup (only if wanted):** Twilio trial account (no card; trial can only SMS verified numbers),
then `PINGHOME_SMS_ENABLED=true` plus the SID/token/from-number variables.

**Cost:** trial credit, then per-message. Leave disabled for zero charges.

## Summary

| Service | Provider | Required? | Free-tier state | Cost if left running |
|---|---|---|---|---|
| Virtual Machine (B1s) | Azure | Yes | 750 h/month, 12 months | $0 within quota, + ≈$3-4/month public IP after day 30 |
| Database for MySQL (B1ms) | Azure | Yes | 750 h/month + 32 GB, 12 months | $0 within quota |
| Application Insights | Azure | Yes (telemetry) | 5 GB/month, no expiry | $0 |
| Firebase Cloud Messaging | Google | Yes (primary notifications) | free, unlimited | $0 |
| Twilio | Twilio | No | disabled | $0 while disabled |

**The first 30 days of the Azure account have a hard spending limit** - nothing can be charged.
After that the account must be upgraded to keep running (see
[deployment.md §6](deployment.md#6-day-30-the-mandatory-upgrade-read-this-before-signing-up)),
the free quotas continue, and a budget alert emails you about anything outside them. Tear down
with one command when the demo is over ([deployment.md §7](deployment.md#7-teardown-after-the-demo-stops-all-billing)).
