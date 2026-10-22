#!/usr/bin/env bash
# Vérifie de bout en bout que la plateforme répond (à lancer après `docker compose up -d`).
set -euo pipefail

TOKEN_URL=http://localhost:8180/realms/ntan/protocol/openid-connect/token
ATTENTE_MAX=${ATTENTE_MAX:-60}   # nombre d'essais (2 s chacun) avant d'abandonner

# Port de la gateway : variable d'environnement d'abord (comme Compose), puis infra/.env.
ENV_FILE="$(dirname "${BASH_SOURCE[0]}")/.env"
if [ -z "${GATEWAY:-}" ] && [ -z "${NTAN_GATEWAY_PORT:-}" ] && [ -f "$ENV_FILE" ]; then
  NTAN_GATEWAY_PORT=$(sed -n -E 's/^[[:space:]]*(export[[:space:]]+)?NTAN_GATEWAY_PORT[[:space:]]*=[[:space:]]*//p' "$ENV_FILE" \
    | tr -d '\r' | tail -1 | sed -E "s/^[\"']//; s/[\"']?[[:space:]]*\$//") || true
fi
GATEWAY=${GATEWAY:-http://localhost:${NTAN_GATEWAY_PORT:-8080}}

ok()    { echo "OK     $1"; }
echec() { echo "ECHEC  $1"; exit 1; }

statut() { curl -s -o /dev/null -w '%{http_code}' "$@" || true; }

jeton() {
  curl -s -d client_id=ntan-dev-cli -d grant_type=password \
       -d "username=$1" -d "password=$2" "$TOKEN_URL" \
    | sed -E 's/.*"access_token":"([^"]+)".*/\1/' || true
}

verifier_statut() { # description attendu url [options curl...]
  local description=$1 attendu=$2; shift 2
  local obtenu; obtenu=$(statut "$@")
  [ "$obtenu" = "$attendu" ] && ok "$description ($obtenu)" || echec "$description : attendu $attendu, obtenu $obtenu"
}

verifier_me() { # description base_url utilisateur mot_de_passe role_attendu
  local corps; corps=$(curl -s -H "Authorization: Bearer $(jeton "$3" "$4")" "$2/api/me" || true)
  echo "$corps" | grep -q "\"$5\"" && ok "$1 : $corps" || echec "$1 : $corps"
}

attendre_gateway() {
  local i
  echo "Attente de la gateway ($GATEWAY/actuator/health), jusqu'à $((ATTENTE_MAX * 2)) s..."
  for ((i = 0; i < ATTENTE_MAX; i++)); do
    [ "$(statut "$GATEWAY/actuator/health")" = 200 ] && return 0
    sleep 2
  done
  echec "la gateway ne répond pas sur $GATEWAY après $((ATTENTE_MAX * 2)) s (statut 000 = injoignable)"
}

attendre_gateway
verifier_statut "santé de la gateway" 200 "$GATEWAY/actuator/health"
verifier_statut "/api/me sans jeton" 401 "$GATEWAY/api/me"
verifier_me "/api/me admin via gateway" "$GATEWAY" admin.ntan admin123 ADMIN
verifier_me "/api/me magasinier via gateway" "$GATEWAY" magasinier.ntan magasinier123 MAGASINIER

echo "Tous les contrôles sont passés."
