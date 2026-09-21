#!/usr/bin/env bash
# One-time bootstrap of the PingHome VM. Run from your own machine; it copies itself to the VM
# and runs the remote part over SSH.
#
#   ./deploy/azure/setup-vm.sh pinghome@<vm-fqdn>
#
# Installs: Java 21 runtime, Caddy (automatic HTTPS via Let's Encrypt for the VM's public
# hostname), a systemd service for the backend, the Application Insights Java agent, and a
# 1 GB swap file (the B1s VM has 1 GB RAM; the JVM + Caddy fit, swap is the safety margin).
set -euo pipefail

TARGET="${1:?usage: setup-vm.sh user@host}"
HERE="$(cd "$(dirname "$0")" && pwd)"
ENV_FILE="$HERE/pinghome.env"
FIREBASE_FILE="$HERE/../../backend/firebase-service-account.json"
AI_AGENT_VERSION="${AI_AGENT_VERSION:-3.7.10}"

[[ -f "$ENV_FILE" ]] || { echo "Missing $ENV_FILE - copy pinghome.env.example and fill it in first."; exit 1; }
DOMAIN="$(sed -n 's/^PINGHOME_DOMAIN=//p' "$ENV_FILE")"
[[ -n "$DOMAIN" ]] || { echo "PINGHOME_DOMAIN is not set in $ENV_FILE"; exit 1; }

echo "==> Uploading config to $TARGET"
scp -q "$ENV_FILE" "$HERE/pinghome.service" "$HERE/Caddyfile" "$TARGET:/tmp/"
if [[ -f "$FIREBASE_FILE" ]]; then
  scp -q "$FIREBASE_FILE" "$TARGET:/tmp/firebase.json"
else
  echo "   (no backend/firebase-service-account.json found - FCM will run in log-only mode until you add it and re-run deploy.sh)"
fi

echo "==> Bootstrapping the VM (this takes a few minutes)"
ssh "$TARGET" "sudo env DOMAIN='$DOMAIN' AI_AGENT_VERSION='$AI_AGENT_VERSION' bash -s" <<'REMOTE'
set -euo pipefail
export DEBIAN_FRONTEND=noninteractive

apt-get update -q
apt-get install -y -q openjdk-21-jre-headless debian-keyring debian-archive-keyring apt-transport-https curl

# Caddy (official repo)
if ! command -v caddy >/dev/null; then
  curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
  curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' > /etc/apt/sources.list.d/caddy-stable.list
  apt-get update -q
  apt-get install -y -q caddy
fi

# Swap: the B1s has 1 GB RAM; this keeps a JVM GC spike from OOM-killing the service.
if [[ ! -f /swapfile ]]; then
  fallocate -l 1G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

# Service account + directories
id -u pinghome >/dev/null 2>&1 || useradd --system --home /opt/pinghome --shell /usr/sbin/nologin pinghome
mkdir -p /opt/pinghome /etc/pinghome
mv /tmp/pinghome.env /etc/pinghome/pinghome.env
[[ -f /tmp/firebase.json ]] && mv /tmp/firebase.json /etc/pinghome/firebase.json
chown -R pinghome:pinghome /opt/pinghome /etc/pinghome
chmod 600 /etc/pinghome/*

# Application Insights Java agent (telemetry only; the app itself has no Azure code)
if [[ ! -f /opt/pinghome/applicationinsights-agent.jar ]]; then
  curl -fsSL -o /opt/pinghome/applicationinsights-agent.jar \
    "https://github.com/microsoft/ApplicationInsights-Java/releases/download/${AI_AGENT_VERSION}/applicationinsights-agent-${AI_AGENT_VERSION}.jar"
  chown pinghome:pinghome /opt/pinghome/applicationinsights-agent.jar
fi

# systemd unit + Caddy reverse proxy
mv /tmp/pinghome.service /etc/systemd/system/pinghome.service
sed "s/__DOMAIN__/${DOMAIN}/" /tmp/Caddyfile > /etc/caddy/Caddyfile
rm -f /tmp/Caddyfile
systemctl daemon-reload
systemctl enable pinghome caddy
systemctl restart caddy

echo "VM ready. Upload the jar with deploy.sh to start the backend."
REMOTE
