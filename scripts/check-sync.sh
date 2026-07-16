#!/usr/bin/env bash
#===============================================================================
# check-sync.sh — Vérifie la cohérence structurelle des trois masters trilingues
#
# Le cahier des charges existe en fr (source), en et ar. Les trois masters
# doivent inclure EXACTEMENT la même liste de chapitres/annexes (mêmes \input,
# dans le même ordre). Ce script échoue (exit 1) si une divergence est détectée,
# ce qui en fait un garde-fou utilisable en local comme en CI.
#
# Usage : bash scripts/check-sync.sh   (depuis la racine du dépôt)
#===============================================================================
set -euo pipefail

BASE="cahier de charge"
FR="$BASE/fr/cahier_des_charges_fr.tex"
EN="$BASE/en/cahier_des_charges_en.tex"
AR="$BASE/ar/cahier_des_charges_ar.tex"

# Extrait la liste ordonnée des \input{...} d'un master.
inputs() { grep -oE '\\input\{[^}]+\}' "$1"; }

status=0
for f in "$FR" "$EN" "$AR"; do
  if [[ ! -f "$f" ]]; then
    echo "✗ Fichier maître introuvable : $f" >&2
    exit 2
  fi
done

echo "→ Master de référence : $FR"
ref="$(inputs "$FR")"

for lang in EN AR; do
  case "$lang" in
    EN) f="$EN" ;;
    AR) f="$AR" ;;
  esac
  if diff <(printf '%s\n' "$ref") <(inputs "$f") > /dev/null; then
    echo "✓ $lang synchronisé avec FR ($(printf '%s\n' "$ref" | grep -c . ) inclusions)"
  else
    echo "✗ $lang diverge de FR :"
    diff <(printf '%s\n' "$ref") <(inputs "$f") || true
    status=1
  fi
done

if [[ $status -eq 0 ]]; then
  echo "✅ Les trois masters sont synchronisés."
else
  echo "❌ Divergence détectée — corrige les \\input avant de committer." >&2
fi
exit $status
