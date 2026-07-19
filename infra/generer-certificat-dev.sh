#!/usr/bin/env bash
# Genere un certificat TLS auto-signe pour le DEVELOPPEMENT local.
#
# En production, le certificat vient de Let's Encrypt : ce script ne doit
# jamais servir a produire un certificat destine a un environnement expose.
set -euo pipefail

DESTINATION="$(dirname "$0")/nginx/ssl"
mkdir -p "$DESTINATION"

if [[ -f "$DESTINATION/zumm.crt" && "${1:-}" != "--force" ]]; then
  echo "Certificat deja present dans $DESTINATION (relancer avec --force pour le remplacer)."
  exit 0
fi

# Sous Git Bash (Windows), MSYS reecrit tout argument commencant par « / » en
# chemin Windows et corrompt le sujet du certificat. MSYS_NO_PATHCONV le desactive ;
# la variable est simplement ignoree ailleurs.
MSYS_NO_PATHCONV=1 openssl req -x509 -nodes -newkey rsa:2048 -days 365 \
  -keyout "$DESTINATION/zumm.key" \
  -out "$DESTINATION/zumm.crt" \
  -subj "/C=TN/O=Zumm/CN=localhost" \
  -addext "subjectAltName=DNS:localhost,IP:127.0.0.1"

chmod 600 "$DESTINATION/zumm.key"

echo "Certificat auto-signe genere dans $DESTINATION."
echo "Le navigateur affichera un avertissement : c'est attendu en local."
