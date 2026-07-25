#!/usr/bin/env bash
#===============================================================================
# seed-demo.sh — Charge le jeu de données de démonstration dans la base en cours
#
# Injecte infra/seed-demo.sql dans le conteneur PostgreSQL de la pile, sous le
# tenant « exploitation-demo » (celui des comptes de test Keycloak de dev).
#
# ⚠️ Développement / démonstration uniquement. La pile doit tourner.
#
# Usage : bash infra/seed-demo.sh   (depuis la racine du dépôt)
#===============================================================================
set -euo pipefail

cd "$(dirname "$0")/.."

if [[ ! -f .env ]]; then
  echo "✗ .env introuvable à la racine (cf. .env.example)." >&2
  exit 1
fi

COMPOSE=(docker compose --env-file .env -f infra/docker-compose.yml)

echo "→ Chargement des données de démonstration (tenant « exploitation-demo »)…"
"${COMPOSE[@]}" exec -T postgres psql -U zumm -d zumm -v ON_ERROR_STOP=1 < infra/seed-demo.sql

echo "✅ Données chargées. Connecte-toi sur la console avec un compte de test :"
echo "     admin-test / test        (référentiel + tout)"
echo "     responsable-test / test  (référentiel)"
echo "     superviseur-test / test  (approbation des plannings)"
echo "     apiculteur-test / test    (visites, mesures, tâches, récoltes…)"
