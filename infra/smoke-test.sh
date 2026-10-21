#!/usr/bin/env bash
# Vérifie de bout en bout que la plateforme répond (à lancer après `docker compose up -d`).
set -euo pipefail

TOKEN_URL=http://localhost:8180/realms/ntan/protocol/openid-connect/token
GATEWAY=${GATEWAY:-http://localhost:8080}

ok()    { echo "OK     $1"; }
echec() { echo "ECHEC  $1"; exit 1; }

statut() { curl -s -o /dev/null -w '%{http_code}' "$@"; }

jeton() {
  curl -s -d client_id=ntan-dev-cli -d grant_type=password \
       -d "username=$1" -d "password=$2" "$TOKEN_URL" \
    | sed -E 's/.*"access_token":"([^"]+)".*/\1/'
}

verifier_statut() { # description attendu url [options curl...]
  local description=$1 attendu=$2; shift 2
  local obtenu; obtenu=$(statut "$@")
  [ "$obtenu" = "$attendu" ] && ok "$description ($obtenu)" || echec "$description : attendu $attendu, obtenu $obtenu"
}

verifier_me() { # description base_url utilisateur mot_de_passe role_attendu
  local corps; corps=$(curl -s -H "Authorization: Bearer $(jeton "$3" "$4")" "$2/api/me")
  echo "$corps" | grep -q "\"$5\"" && ok "$1 : $corps" || echec "$1 : $corps"
}

verifier_statut "santé de la gateway" 200 "$GATEWAY/actuator/health"
verifier_statut "/api/me sans jeton" 401 "$GATEWAY/api/me"
verifier_me "/api/me admin via gateway" "$GATEWAY" admin.ntan admin123 ADMIN
verifier_me "/api/me magasinier via gateway" "$GATEWAY" magasinier.ntan magasinier123 MAGASINIER

echo "Tous les contrôles sont passés."
