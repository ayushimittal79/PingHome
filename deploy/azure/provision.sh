#!/usr/bin/env bash
# Creates everything PingHome needs on Azure, inside one resource group so teardown is a single
# command. Run once, from your own machine, after `az login`. Safe to re-run: every step is
# create-if-missing.
#
#   ./deploy/azure/provision.sh
#
# Sizes are deliberately the ones covered by the Azure free account's 12-month allowance
# (B1s VM, Burstable B1ms MySQL with 32 GB). Do not change them without checking the cost
# estimate the portal shows before creating. See docs/deployment.md for the full runbook.
set -euo pipefail
# Git Bash on Windows otherwise rewrites Azure resource IDs (/subscriptions/...) into file paths.
export MSYS_NO_PATHCONV=1

# ---- Settings (override by exporting before running) --------------------------------------
RG="${PINGHOME_RG:-pinghome-rg}"
LOCATION="${PINGHOME_LOCATION:-centralindia}"
VM_NAME="${PINGHOME_VM_NAME:-pinghome-vm}"
VM_ADMIN="${PINGHOME_VM_ADMIN:-pinghome}"
# Free-account eligible sizes: Standard_B1s, Standard_B2ats_v2 (AMD), Standard_B2pts_v2 (Arm).
# Capacity for B-series varies per region/subscription; override if a region reports SkuNotAvailable.
VM_SIZE="${PINGHOME_VM_SIZE:-Standard_B1s}"
# Becomes <DNS_LABEL>.<LOCATION>.cloudapp.azure.com - must be globally unique in the region.
DNS_LABEL="${PINGHOME_DNS_LABEL:-pinghome-$(whoami | tr -cd 'a-z0-9' | cut -c1-10)-$RANDOM}"
MYSQL_NAME="${PINGHOME_MYSQL_NAME:-pinghome-mysql-$RANDOM}"
MYSQL_ADMIN="${PINGHOME_MYSQL_ADMIN:-pinghome}"
INSIGHTS_NAME="${PINGHOME_INSIGHTS_NAME:-pinghome-insights}"
LOGS_NAME="${PINGHOME_LOGS_NAME:-pinghome-logs}"

if [[ -z "${PINGHOME_MYSQL_PASSWORD:-}" ]]; then
  echo "Set PINGHOME_MYSQL_PASSWORD (the MySQL admin password) before running, e.g."
  echo "  export PINGHOME_MYSQL_PASSWORD=\"\$(openssl rand -base64 24)\""
  exit 1
fi

echo "==> Registering resource providers (one-time on a new subscription)"
for ns in Microsoft.Compute Microsoft.Network Microsoft.DBforMySQL Microsoft.Insights Microsoft.OperationalInsights; do
  if [[ "$(az provider show --namespace "$ns" --query registrationState -o tsv 2>/dev/null)" != "Registered" ]]; then
    az provider register --namespace "$ns" --wait --output none
  fi
done

echo "==> Resource group $RG in $LOCATION"
az group create --name "$RG" --location "$LOCATION" --output none

echo "==> VM $VM_NAME ($VM_SIZE, Ubuntu 24.04, DNS label $DNS_LABEL)"
if ! az vm show --resource-group "$RG" --name "$VM_NAME" --output none 2>/dev/null; then
  az vm create \
    --resource-group "$RG" \
    --name "$VM_NAME" \
    --image Ubuntu2404 \
    --size "$VM_SIZE" \
    --admin-username "$VM_ADMIN" \
    --generate-ssh-keys \
    --public-ip-sku Standard \
    --public-ip-address-dns-name "$DNS_LABEL" \
    --os-disk-size-gb 30 \
    --output none
  az vm open-port --resource-group "$RG" --name "$VM_NAME" --port 80 --priority 1001 --output none
  az vm open-port --resource-group "$RG" --name "$VM_NAME" --port 443 --priority 1002 --output none
fi

VM_IP="$(az vm show --resource-group "$RG" --name "$VM_NAME" --show-details --query publicIps --output tsv)"
VM_FQDN="$(az vm show --resource-group "$RG" --name "$VM_NAME" --show-details --query fqdns --output tsv)"

echo "==> MySQL flexible server $MYSQL_NAME (Burstable Standard_B1ms, 32 GB)"
if ! az mysql flexible-server show --resource-group "$RG" --name "$MYSQL_NAME" --output none 2>/dev/null; then
  az mysql flexible-server create \
    --resource-group "$RG" \
    --name "$MYSQL_NAME" \
    --location "$LOCATION" \
    --admin-user "$MYSQL_ADMIN" \
    --admin-password "$PINGHOME_MYSQL_PASSWORD" \
    --sku-name Standard_B1ms \
    --tier Burstable \
    --storage-size 32 \
    --version 8.0.21 \
    --public-access "$VM_IP" \
    --database-name pinghome \
    --backup-retention 7 \
    --yes \
    --output none
fi
MYSQL_HOST="$(az mysql flexible-server show --resource-group "$RG" --name "$MYSQL_NAME" --query fullyQualifiedDomainName --output tsv)"

echo "==> Application Insights $INSIGHTS_NAME (workspace $LOGS_NAME)"
az extension add --name application-insights --upgrade --only-show-errors --output none
if ! az monitor log-analytics workspace show --resource-group "$RG" --workspace-name "$LOGS_NAME" --output none 2>/dev/null; then
  az monitor log-analytics workspace create --resource-group "$RG" --workspace-name "$LOGS_NAME" --location "$LOCATION" --output none
fi
WORKSPACE_ID="$(az monitor log-analytics workspace show --resource-group "$RG" --workspace-name "$LOGS_NAME" --query id --output tsv)"
if ! az monitor app-insights component show --resource-group "$RG" --app "$INSIGHTS_NAME" --output none 2>/dev/null; then
  az monitor app-insights component create \
    --resource-group "$RG" \
    --app "$INSIGHTS_NAME" \
    --location "$LOCATION" \
    --application-type web \
    --workspace "$WORKSPACE_ID" \
    --output none
fi
INSIGHTS_CONN="$(az monitor app-insights component show --resource-group "$RG" --app "$INSIGHTS_NAME" --query connectionString --output tsv)"

echo "==> Budget alert (email only - Azure does not auto-stop resources after the free month)"
SUB_ID="$(az account show --query id --output tsv)"
BUDGET_EMAIL="${PINGHOME_BUDGET_EMAIL:-$(az account show --query user.name --output tsv)}"
az consumption budget create \
  --budget-name pinghome-budget \
  --amount 5 \
  --category cost \
  --time-grain monthly \
  --start-date "$(date -u +%Y-%m-01)" \
  --end-date "$(date -u -d '+12 months' +%Y-%m-01 2>/dev/null || date -u -v+12m +%Y-%m-01)" \
  --resource-group "$RG" \
  --output none 2>/dev/null || echo "   (budget already exists or the consumption API isn't available yet on this subscription - set one in the portal: Cost Management -> Budgets)"

cat <<EOF

==================== PingHome on Azure ====================
Resource group        : $RG
Backend host (HTTPS)  : https://$VM_FQDN/
VM public IP          : $VM_IP
SSH                   : ssh $VM_ADMIN@$VM_FQDN
MySQL host            : $MYSQL_HOST
MySQL admin user      : $MYSQL_ADMIN
App Insights conn str : $INSIGHTS_CONN
===========================================================

Next:
  1. Write deploy/azure/pinghome.env (copy pinghome.env.example) using the values above.
  2. ./deploy/azure/setup-vm.sh $VM_ADMIN@$VM_FQDN     # one-time VM bootstrap
  3. ./deploy/azure/deploy.sh   $VM_ADMIN@$VM_FQDN     # build + upload + restart, any time
Teardown (stops all billing): az group delete --name $RG --yes
EOF
