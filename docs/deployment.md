# Deployment (backend → Azure VM + Azure Database for MySQL)

The backend runs as a plain jar under systemd on a small Azure VM, behind Caddy for HTTPS, with
Azure Database for MySQL as the database and Application Insights for telemetry. Everything is
scripted under `deploy/azure/` and lives in one resource group, so teardown is one command.

> **Status (2026-09-22):** deployed for real on a Free Trial subscription. Region **southindia**
> (Central India had no B-series capacity), VM size **Standard_B2ats_v2** (B1s was capacity-blocked
> in both Indian regions; B2ats_v2 is also in the free 750-hour allowance). First boot ran Flyway
> against the empty Azure database, Caddy obtained a Let's Encrypt certificate, and the release
> APK completed the SOS → FCM push test against the cloud backend. Two snags, both now handled in
> `provision.sh`: new subscriptions must register resource providers first, and the
> `az consumption budget create` call is rejected by the current API - set the budget alert in
> the portal (Cost Management → Budgets) instead.
>
> **Moved 2026-10-19** to a new Free Trial subscription as the first one approached its day-30
> limit. Same region and VM size (B1s was again capacity-blocked in South India); the new host
> is `pinghome-ayushi-5884.southindia.cloudapp.azure.com`. The old database was dumped with
> `mysqldump` through an SSH tunnel via the old VM (the MySQL firewall only admits the VM) and
> imported into the new, empty database through the new VM *before* the first `deploy.sh`, so
> Flyway found the schema at version 1 and migrated nothing. `PINGHOME_JWT_SECRET` was kept, so
> existing app sessions stay valid. The release APK must be rebuilt with the new base URL.

Why a VM rather than a serverless/container platform: `EscalationScheduler` is an in-process
`@Scheduled` job. It only runs while the JVM is alive, so the backend needs a host that is
always on - not one that scales to zero or sleeps on idle. The free-account B1s VM is exactly
that. See [cloud-services.md](cloud-services.md) for the cost/free-tier picture.

## 0. Prerequisites (your machine)

- Azure CLI (`az`), logged in: `az login`. Use a **personal** Microsoft account, never a
  university one - see [cloud-services.md](cloud-services.md#account-rules).
- Git Bash (Windows) or any bash; `ssh`, `scp`, `curl`, `openssl` on the PATH (Git Bash has all of them).
- JDK 21 (the deploy script builds the jar locally).
- `backend/firebase-service-account.json` - your Firebase service-account key (gitignored). Without
  it the backend still runs; FCM just stays in log-only mode.

## 1. Provision (once)

```bash
export PINGHOME_MYSQL_PASSWORD="$(openssl rand -base64 24)"   # keep this - you'll need it in step 2
./deploy/azure/provision.sh
```

Creates, in resource group `pinghome-rg` (region `centralindia` by default; override with
`PINGHOME_LOCATION`, and the VM size with `PINGHOME_VM_SIZE` - the deployed instance uses
`PINGHOME_LOCATION=southindia PINGHOME_VM_SIZE=Standard_B2ats_v2`):

| Resource | Size | Free-account coverage |
|---|---|---|
| VM `pinghome-vm` | Standard_B1s (or B2ats_v2 / B2pts_v2), Ubuntu 24.04, 30 GB disk | 750 h/month for 12 months |
| MySQL flexible server | Burstable Standard_B1ms, 32 GB, MySQL 8.0 | 750 h/month + 32 GB for 12 months |
| Application Insights + Log Analytics workspace | - | 5 GB/month ingestion always free |
| Public IP (Standard) | - | **not** free: roughly $3-4/month after the first 30 days |
| Budget alert `pinghome-budget` | $5/month, email | - |

It prints the VM hostname (`<label>.centralindia.cloudapp.azure.com`), MySQL host, and the
Application Insights connection string. The MySQL firewall is opened only for the VM's IP.

If `az vm create` fails with `SkuNotAvailable` / capacity restrictions, re-run with another free
size (`PINGHOME_VM_SIZE=Standard_B2ats_v2`) and/or another region (`PINGHOME_LOCATION=southindia`,
`southeastasia`). The script is create-if-missing, but the MySQL server name gets a random
suffix on every run: when re-running after the server was already created, pass the existing
name (`PINGHOME_MYSQL_NAME=pinghome-mysql-NNNNN`) or a second server is created.
On Windows, run the scripts from Git Bash with a small `az` shim on the PATH that calls
`az.cmd` (the CLI installs as a batch file). `provision.sh` sets `MSYS_NO_PATHCONV=1`, because
Git Bash otherwise rewrites Azure resource IDs (`/subscriptions/...`) into Windows paths and the
Application Insights step fails.

## 2. Configure

```bash
cp deploy/azure/pinghome.env.example deploy/azure/pinghome.env
```

Fill in `pinghome.env` from the provision output: `PINGHOME_DOMAIN`, `DB_URL` (keep
`sslMode=REQUIRED` - Azure MySQL enforces TLS), `DB_PASSWORD`, `APPLICATIONINSIGHTS_CONNECTION_STRING`,
and a fresh `PINGHOME_JWT_SECRET` (`openssl rand -base64 48`). The `cloud` profile refuses to
start if the JWT secret is left at the development default. The file is gitignored.

## 3. Bootstrap the VM + first deploy

```bash
./deploy/azure/setup-vm.sh pinghome@<label>.centralindia.cloudapp.azure.com   # once
./deploy/azure/deploy.sh   pinghome@<label>.centralindia.cloudapp.azure.com   # every code change
```

`setup-vm.sh` installs Java 21, Caddy, the systemd unit, the Application Insights agent and a
1 GB swap file. `deploy.sh` builds the jar, uploads it, restarts the service and polls
`https://<host>/actuator/health` until it reports `UP`. On the first boot, Flyway creates the
schema in the empty Azure database automatically (see [database.md](database.md)).

Caddy obtains the Let's Encrypt certificate on the first HTTPS request; if the first health
check fails with a TLS error, wait a minute and retry - the certificate issuance can take that
long on first boot. If it keeps failing: `ssh ... 'sudo journalctl -u caddy -n 50'`.

## 4. Point the Android app at it

Build the release APK against the real HTTPS origin (the debug build keeps pointing at the
emulator loopback):

```bash
cd android
./gradlew assembleRelease -PPINGHOME_API_BASE_URL=https://<label>.centralindia.cloudapp.azure.com/
```

Without a `PINGHOME_KEYSTORE_FILE` property the release APK is signed with the debug key,
which is fine for sideloading onto demo devices. For a real keystore see [setup.md](setup.md).

## 5. Verify

- `curl https://<host>/actuator/health` → `{"status":"UP"}`
- `curl -X POST https://<host>/api/auth/login -H 'Content-Type: application/json' -d '{"email":"x","password":"y"}'` → HTTP 401 with a JSON error body (proves routing + TLS)
- Azure portal → Application Insights → Live Metrics shows the instance; the `EscalationScheduler`
  log lines appear under Logs → traces during an active trip past its grace period.
- Run the manual scenarios in [testing.md](testing.md) against the deployed URL.

## 6. Day 30: the mandatory upgrade (read this before signing up)

The Azure free account has a **spending limit for the first 30 days only**. Microsoft's terms:
*"Within 30 days of sign-up or upon exhaustion of the credits (whichever occurs first), you must
upgrade to a pay-as-you-go subscription by removing the spending limit."* If you don't, the
subscription - including the "free" VM and database - is disabled.

- **If the demo is within 30 days of signup:** do nothing. Tear down afterwards (step 7).
- **If it's later:** around day 28 Azure emails you. In the portal: Subscriptions → your
  subscription → the "Upgrade" banner → choose pay-as-you-go, confirm the card. The 12-month free
  quotas keep applying; what you'll actually be billed is the public IP (≈$3-4/month) and anything
  outside the free sizes. Keep the `pinghome-budget` alert (or set one in Cost Management →
  Budgets) so an accidental overage emails you.

Do **not** change VM or database sizes after upgrading - the free quotas only cover the sizes
in the table above.

## 7. Teardown after the demo (stops all billing)

```bash
az group delete --name pinghome-rg --yes
```

Everything PingHome created lives in that one group. Optionally also cancel the subscription
in the portal (Subscriptions → Cancel) so nothing can ever be created on it again. Then delete
`deploy/azure/pinghome.env` locally.

## Redeploy / troubleshoot

| Symptom | Do |
|---|---|
| Code change | `./deploy/azure/deploy.sh pinghome@<host>` |
| Backend not healthy | `ssh pinghome@<host> 'sudo journalctl -u pinghome -n 100 --no-pager'` |
| `PINGHOME_JWT_SECRET` error in logs | fill it in `pinghome.env`, re-run `deploy.sh` |
| MySQL connection refused | VM IP changed (VM was deallocated and restarted): `az mysql flexible-server firewall-rule create --resource-group pinghome-rg --name <mysql-name> --rule-name vm --start-ip-address <new-ip> --end-ip-address <new-ip>` |
| HTTPS certificate error | `ssh ... 'sudo systemctl restart caddy'`, wait 60 s |
| VM rebuilt from scratch | `provision.sh` (create-if-missing, safe to re-run) → `setup-vm.sh` → `deploy.sh` |

