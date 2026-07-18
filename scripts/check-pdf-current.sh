#!/usr/bin/env bash
#===============================================================================
# check-pdf-current.sh — Vérifie qu'un PDF versionné correspond bien à ses sources
#
# Les trois cahiers des charges sont commités sous forme de PDF. Rien ne garantit
# qu'un PDF versionné a été régénéré après une modification de ses sources : ce
# script compare le PDF committé au PDF fraîchement compilé et échoue (exit 1) en
# cas de divergence de CONTENU.
#
# Pourquoi comparer le texte et non les fichiers : un PDF n'est jamais
# reproductible au bit près (pdfTeX y embarque /CreationDate, /ModDate et /ID à
# chaque compilation). Pire pour l'arabe : en CI la police « Traditional Arabic »
# est absente et le master bascule sur Amiri (\IfFontExistsTF), ce qui change les
# métriques, donc les coupures de lignes et la pagination — sans rien changer au
# contenu. Une comparaison d'octets, de taille ou de nombre de pages produirait
# donc des faux positifs permanents.
#
# La comparaison porte sur le texte extrait, débarrassé de TOUT espacement, afin
# d'être insensible aux coupures de lignes et de pages induites par la police.
#
# Usage : bash scripts/check-pdf-current.sh <pdf-committé> <pdf-recompilé>
#===============================================================================
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "Usage : bash scripts/check-pdf-current.sh <pdf-committé> <pdf-recompilé>" >&2
  exit 2
fi

COMMITTED="$1"
REBUILT="$2"

for f in "$COMMITTED" "$REBUILT"; do
  if [[ ! -f "$f" ]]; then
    echo "✗ PDF introuvable : $f" >&2
    exit 2
  fi
done

if ! command -v pdftotext > /dev/null 2>&1; then
  echo "✗ pdftotext est requis (paquet poppler-utils)." >&2
  exit 2
fi

# Texte brut en UTF-8, sans -layout : la mise en colonnes dépend des métriques
# de police et n'a pas à entrer dans la comparaison.
extract() { pdftotext -q -enc UTF-8 "$1" - 2> /dev/null || true; }

# Normalisation : suppression de tout caractère d'espacement. Deux rendus du même
# contenu avec des polices différentes coupent les lignes ailleurs mais émettent
# la même suite de caractères.
normalise() { extract "$1" | tr -d '[:space:]'; }

a="$(normalise "$COMMITTED")"
b="$(normalise "$REBUILT")"

if [[ -z "$b" ]]; then
  echo "✗ Le PDF recompilé ne contient aucun texte extractible : $REBUILT" >&2
  echo "  (compilation muette ou pdftotext en échec — à investiguer)" >&2
  exit 2
fi

if [[ "$a" == "$b" ]]; then
  echo "✓ $(basename "$COMMITTED") est à jour (${#b} caractères comparés)"
  exit 0
fi

echo "✗ $(basename "$COMMITTED") n'est PAS à jour : son contenu diffère des sources." >&2
echo "  versionné  : ${#a} caractères" >&2
echo "  recompilé  : ${#b} caractères" >&2
echo "" >&2
echo "  Premier écart :" >&2
# Localise la première divergence pour rendre l'échec exploitable.
python3 - "$COMMITTED" "$REBUILT" <<'PY' >&2 || true
import subprocess, sys

def norm(p):
    out = subprocess.run(["pdftotext", "-q", "-enc", "UTF-8", p, "-"],
                         capture_output=True).stdout.decode("utf-8", "replace")
    return "".join(out.split())

a, b = norm(sys.argv[1]), norm(sys.argv[2])
i = next((i for i in range(min(len(a), len(b))) if a[i] != b[i]), min(len(a), len(b)))
lo = max(0, i - 60)
print("    position %d" % i)
print("    versionné : …%s…" % a[lo:i + 60])
print("    recompilé : …%s…" % b[lo:i + 60])
PY
echo "" >&2
echo "  → Recompilez le document et commitez le PDF régénéré." >&2
exit 1
