#!/usr/bin/env bash
# Builds the backend jar, uploads it (plus any updated env/Firebase files) to the VM, restarts
# the service, and checks the public health endpoint. Run any time you change backend code.
#
#   ./deploy/azure/deploy.sh pinghome@<vm-fqdn>
set -euo pipefail

TARGET="${1:?usage: deploy.sh user@host}"
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
ENV_FILE="$HERE/pinghome.env"
FIREBASE_FILE="$ROOT/backend/firebase-service-account.json"

[[ -f "$ENV_FILE" ]] || { echo "Missing $ENV_FILE"; exit 1; }
DOMAIN="$(sed -n 's/^PINGHOME_DOMAIN=//p' "$ENV_FILE")"

echo "==> Building backend jar"
(cd "$ROOT/backend" && ./mvnw -q -B -DskipTests package)

echo "==> Uploading to $TARGET"
scp -q "$ROOT/backend/target/pinghome-backend.jar" "$TARGET:/tmp/app.jar"
scp -q "$ENV_FILE" "$TARGET:/tmp/pinghome.env"
[[ -f "$FIREBASE_FILE" ]] && scp -q "$FIREBASE_FILE" "$TARGET:/tmp/firebase.json"

echo "==> Installing + restarting"
ssh "$TARGET" 'sudo bash -s' <<'REMOTE'
set -euo pipefail
mv /tmp/app.jar /opt/pinghome/app.jar
mv /tmp/pinghome.env /etc/pinghome/pinghome.env
[[ -f /tmp/firebase.json ]] && mv /tmp/firebase.json /etc/pinghome/firebase.json
chown pinghome:pinghome /opt/pinghome/app.jar /etc/pinghome/*
chmod 600 /etc/pinghome/*
systemctl restart pinghome
REMOTE

echo "==> Waiting for https://$DOMAIN/actuator/health"
for _ in $(seq 1 30); do
  if curl -fsS --max-time 5 "https://$DOMAIN/actuator/health" 2>/dev/null | grep -q '"UP"'; then
    echo "Backend is UP at https://$DOMAIN/"
    exit 0
  fi
  sleep 5
done
echo "Backend did not report healthy in time. Check logs: ssh $TARGET 'sudo journalctl -u pinghome -n 100'"
exit 1
