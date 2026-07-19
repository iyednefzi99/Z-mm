#!/usr/bin/env bash
# Restauration de la base Zümm depuis une sauvegarde produite par `sauvegarde.sh`.
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Usage : $0 <fichier.dump>" >&2
  exit 1
fi

FICHIER="$1"
ICI="$(cd "$(dirname "$0")" && pwd)"
COMPOSE="${COMPOSE:-docker compose --env-file $ICI/../.env -f $ICI/docker-compose.yml}"

if [[ ! -f "$FICHIER" ]]; then
  echo "ERREUR : fichier introuvable : $FICHIER" >&2
  exit 1
fi

echo "Restauration de $FICHIER dans la base zumm."
# --clean --if-exists : la restauration doit etre reproductible, donc repartir
# d'un schema propre plutot que de se superposer a l'existant.
$COMPOSE exec -T postgres pg_restore -U zumm -d zumm --clean --if-exists --no-owner < "$FICHIER"

echo "Restauration terminee."
