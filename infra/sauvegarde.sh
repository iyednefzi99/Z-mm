#!/usr/bin/env bash
# Sauvegarde de la base Zümm.
#
# Une sauvegarde jamais restauree n'est pas une sauvegarde : voir
# `restauration.sh`, et le test de bout en bout `tester-restauration.sh`.
set -euo pipefail

ICI="$(cd "$(dirname "$0")" && pwd)"
# .env est a la racine du depot, pas a cote du compose : --env-file est requis.
COMPOSE="${COMPOSE:-docker compose --env-file $ICI/../.env -f $ICI/docker-compose.yml}"
DESTINATION="${DESTINATION:-$ICI/../sauvegardes}"
HORODATAGE="$(date +%Y%m%d-%H%M%S)"
FICHIER="$DESTINATION/zumm-$HORODATAGE.dump"

mkdir -p "$DESTINATION"

echo "Sauvegarde vers $FICHIER"
# Format « custom » : compresse, et restaurable table par table si besoin.
$COMPOSE exec -T postgres pg_dump -U zumm -d zumm -Fc > "$FICHIER"

TAILLE="$(wc -c < "$FICHIER")"
if [[ "$TAILLE" -lt 1000 ]]; then
  echo "ERREUR : sauvegarde suspecte ($TAILLE octets). Echec plutot que fausse securite." >&2
  rm -f "$FICHIER"
  exit 1
fi

echo "Sauvegarde terminee : $TAILLE octets."
echo "$FICHIER"
