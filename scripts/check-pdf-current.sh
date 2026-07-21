#!/usr/bin/env bash
#===============================================================================
# check-pdf-current.sh — Vérifie qu'un PDF versionné a bien été régénéré après
#                        la dernière modification de ses sources
#
# Les trois cahiers des charges sont commités sous forme de PDF. Rien ne garantit
# qu'un PDF a été recompilé après une modification de ses sources : ce script
# compare, dans l'historique git, la date du dernier commit touchant le PDF à
# celle du dernier commit touchant ses sources.
#
# POURQUOI PAS UNE COMPARAISON DE CONTENU
# ---------------------------------------
# La version précédente recompilait le document en CI et comparait le texte
# extrait par pdftotext au texte du PDF versionné. Cette approche a été
# abandonnée : elle produit des FAUX POSITIFS PERMANENTS dès que le PDF de
# référence et le PDF de CI sortent de deux distributions LaTeX différentes.
#
# Cas réel observé (2026-07-21) : sur `fr` comme sur `en`, l'écart portait sur
# UN caractère parmi 107 000 — le signe somme de la formule QuantiteMiel. Le PDF
# de référence (MiKTeX, Windows) l'expose en « X », celui de la CI (TeX Live,
# Alpine) en « ∑ ». Contenu identique, extraction différente : les polices
# mathématiques n'embarquent pas la même table ToUnicode. Aucune normalisation
# raisonnable ne rattrape cela — « X » est une lettre, pas un symbole.
#
# La date de commit, elle, ne dépend d'aucune police : elle répond exactement à
# la question posée — « ce PDF est-il postérieur à ses sources ? ».
#
# Usage : bash scripts/check-pdf-current.sh <pdf-versionné> <répertoire-sources>
# Requiert : un dépôt git avec l'historique complet (actions/checkout
#            fetch-depth: 0 — un clone superficiel fausserait les dates).
#===============================================================================
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "Usage : bash scripts/check-pdf-current.sh <pdf-versionné> <répertoire-sources>" >&2
  exit 2
fi

PDF="$1"
SRC_DIR="${2%/}"

if [[ ! -f "$PDF" ]]; then
  echo "✗ PDF introuvable : $PDF" >&2
  exit 2
fi

if ! git rev-parse --is-inside-work-tree > /dev/null 2>&1; then
  echo "✗ Pas un dépôt git : la vérification de fraîcheur est impossible." >&2
  exit 2
fi

# Un clone superficiel ne contient pas l'historique nécessaire : le dire plutôt
# que de rendre un verdict faux.
if [[ "$(git rev-parse --is-shallow-repository 2> /dev/null)" == "true" ]]; then
  echo "✗ Dépôt superficiel (shallow) : utilisez actions/checkout avec fetch-depth: 0." >&2
  exit 2
fi

horodatage_pdf="$(git log -1 --format=%ct -- "$PDF" || true)"
if [[ -z "$horodatage_pdf" ]]; then
  echo "✗ $PDF n'a aucun commit dans l'historique." >&2
  exit 2
fi

# Sources = tout le répertoire de la langue SAUF le PDF lui-même. Les images
# partagées vivent dans fr/ : elles sont couvertes par la vérification de fr.
horodatage_src="$(git log -1 --format=%ct -- "$SRC_DIR" ":(exclude)$PDF" || true)"
if [[ -z "$horodatage_src" ]]; then
  echo "✗ Aucune source trouvée sous $SRC_DIR." >&2
  exit 2
fi

if [[ "$horodatage_pdf" -ge "$horodatage_src" ]]; then
  echo "✓ $(basename "$PDF") est à jour"
  echo "  PDF     : $(git log -1 --format='%h %ad %s' --date=short -- "$PDF")"
  exit 0
fi

echo "✗ $(basename "$PDF") n'est PAS à jour : des sources ont été modifiées après lui." >&2
echo "" >&2
echo "  Dernier commit du PDF     : $(git log -1 --format='%h %ad %s' --date=short -- "$PDF")" >&2
echo "  Dernier commit des sources: $(git log -1 --format='%h %ad %s' --date=short -- "$SRC_DIR" ":(exclude)$PDF")" >&2
echo "" >&2
echo "  Fichiers modifiés depuis la dernière régénération :" >&2
git log --format='' --name-only --since="@${horodatage_pdf}" -- "$SRC_DIR" ":(exclude)$PDF" 2> /dev/null \
  | sort -u | sed '/^$/d' | head -20 | sed 's/^/    /' >&2 || true
echo "" >&2
echo "  → Recompilez le document et commitez le PDF régénéré." >&2
exit 1
