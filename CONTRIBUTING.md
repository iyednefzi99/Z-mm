# 🤝 Contribuer au dépôt Zümm

Ce dépôt est un **livrable documentaire** (cahier des charges + charte de design).
Sa contrainte principale est la **cohérence trilingue** : 🇫🇷 (source) · 🇬🇧 · 🇸🇦.

## 🌍 Règle d'or : le français est la source

Toute modification de fond part de la version **française**, puis se répercute en
**anglais** et en **arabe**. Aucune des trois versions ne doit prendre de retard.

## ✅ Checklist : ajouter/modifier un chapitre ou une annexe

Quand tu ajoutes un chapitre ou une annexe (ex. une nouvelle annexe `H-…`) :

1. **FR** — créer `cahier de charge/fr/annexes/H-…/…​.tex` (texte + éventuels
   `diagrammes/*.puml` et `images/*.png`).
2. **EN** — créer la traduction sous `cahier de charge/en/annexes/H-…/`.
3. **AR** — créer la traduction sous `cahier de charge/ar/annexes/H-…/`.
4. **Ajouter le `\input`** correspondant dans **les trois** masters :
   - `fr/cahier_des_charges_fr.tex`
   - `en/cahier_des_charges_en.tex`
   - `ar/cahier_des_charges_ar.tex`
   …au **même endroit** et dans le **même ordre**.
5. **Figures** : les PNG vivent dans `fr/` et sont partagés par `en/` et `ar/`
   via `graphicspath` (seuls les libellés TikZ et légendes sont traduits).
6. **Vérifier la synchro** :
   ```bash
   bash scripts/check-sync.sh
   ```
7. **Recompiler les trois PDF** et les committer (ils font partie du livrable) :
   ```bash
   cd "cahier de charge/fr" && latexmk -pdf cahier_des_charges_fr.tex
   cd "cahier de charge/en" && latexmk -pdf cahier_des_charges_en.tex
   cd "cahier de charge/ar" && latexmk -pdf -xelatex cahier_des_charges_ar.tex
   ```

> 💡 La CI GitHub (`.github/workflows/build-pdfs.yml`) rejoue les étapes 6 et 7 à
> chaque push : un oubli de `\input` ou une erreur LaTeX fait échouer le build.

## 🔤 Identifiants stables entre langues

Les identifiants de code et d'API restent **identiques** dans les trois langues
(ils ne se traduisent pas) : `getZummHoneyActualQuantity`, `ConfigZumm.ini`, noms
de classes SQL/Java, etc. Seule la prose est traduite.

## 🎨 Charte de design

Avant toute création ou modification visuelle (slide, maquette, e-mail, visuel),
se référer à [`design/`](design/) : palette miel/vert dérivée du logo, typographie,
espacements, motion et tokens. Les trois versions `DESIGN.md` (FR/EN/AR) doivent
elles aussi rester cohérentes.

## 🎓 Contrainte académique

🚫 L'usage de générateurs de code pour produire le livrable est proscrit par
l'épreuve. ✍️ La documentation et la conception sont réalisées à la main.
