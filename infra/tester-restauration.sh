#!/usr/bin/env bash
# Exercice de restauration reel, exige par la Definition of Done du SPRINT-00.
#
# Le scenario est volontairement destructif : on ecrit une donnee, on sauvegarde,
# on DETRUIT la donnee, puis on restaure et on verifie qu'elle est revenue.
# Sans l'etape de destruction, le test ne prouverait rien.
#
# A executer sur un environnement jetable, jamais en production.
set -euo pipefail

ICI="$(cd "$(dirname "$0")" && pwd)"
# Le fichier .env vit a la racine du depot, pas a cote du compose : sans
# --env-file, l'interpolation des mots de passe echoue.
COMPOSE="${COMPOSE:-docker compose --env-file $ICI/../.env -f $ICI/docker-compose.yml}"
export COMPOSE
TEMOIN="temoin-restauration-$(date +%s)"

psql_zumm() {
  $COMPOSE exec -T postgres psql -U zumm -d zumm -tAc "$1"
}

echo "1. Insertion du temoin « $TEMOIN »"
psql_zumm "INSERT INTO ping (libelle) VALUES ('$TEMOIN');" > /dev/null

AVANT="$(psql_zumm "SELECT count(*) FROM ping WHERE libelle = '$TEMOIN';")"
[[ "$AVANT" == "1" ]] || { echo "ERREUR : le temoin n'a pas ete insere." >&2; exit 1; }

echo "2. Sauvegarde"
FICHIER="$("$ICI/sauvegarde.sh" | tail -1)"

echo "3. Destruction du temoin"
psql_zumm "DELETE FROM ping WHERE libelle = '$TEMOIN';" > /dev/null
PENDANT="$(psql_zumm "SELECT count(*) FROM ping WHERE libelle = '$TEMOIN';")"
[[ "$PENDANT" == "0" ]] || { echo "ERREUR : le temoin n'a pas ete detruit, le test ne prouverait rien." >&2; exit 1; }

echo "4. Restauration depuis $FICHIER"
"$ICI/restauration.sh" "$FICHIER"

echo "5. Verification"
APRES="$(psql_zumm "SELECT count(*) FROM ping WHERE libelle = '$TEMOIN';")"
if [[ "$APRES" == "1" ]]; then
  echo
  echo "SUCCES : le temoin detruit a ete retrouve apres restauration."
  psql_zumm "DELETE FROM ping WHERE libelle = '$TEMOIN';" > /dev/null
  exit 0
fi

echo "ECHEC : le temoin est absent apres restauration (compte = $APRES)." >&2
exit 1
