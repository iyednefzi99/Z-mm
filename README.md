<p align="center">
  <img src="assets/logo/zumm-logo.png" alt="Logo Zümm" width="260">
</p>

<h1 align="center">🐝 Zümm</h1>

<p align="center"><em>Système d'information de gestion et de suivi apicole</em></p>

<p align="center">
  🇫🇷 Français · 🇬🇧 English · 🇸🇦 العربية
</p>

---

🍯 **Zümm** est une application multi-niveaux de gestion et de suivi de ruches :
gestion des opérations, planification des visites, tableaux de bord de performance
et alertes, avec préparation d'un volet capteurs / supervision automatisée. 📡

📦 À ce stade, ce dépôt réunit les **livrables documentaires** du projet :
le **cahier des charges** (trilingue) et la **charte de design**. 🚧 Le code
applicatif reste à développer.

## 🗂️ Contenu du dépôt

| Dossier | Contenu |
|---|---|
| 📂 [`cahier de charge/`](cahier%20de%20charge/) | Cahier des charges **trilingue** (🇫🇷 source · 🇬🇧 · 🇸🇦), LaTeX modulaire + diagrammes UML → 3 PDF. Voir son [README](cahier%20de%20charge/README.md). |
| 🎨 [`design/`](design/) | **Charte de design** (palette, typographie, composants, motion, tokens) en FR/EN/AR. Voir son [README](design/README.md). |
| 🗺️ [`roadmap/`](roadmap/) | **Roadmap Scrum/DevOps** : document LaTeX → PDF + sources opérationnelles (`operationnel/` : backlog, sprints, pipeline CI/CD, releases, monitoring). Voir son [README](roadmap/README.md). |
| 🖼️ [`assets/logo/`](assets/logo/) | Logos Zümm (SVG masters, PNG, favicons, PDF print). Voir son [README](assets/logo/README.md). |

## 🚀 Démarrage rapide

Les PDF compilés sont **déjà versionnés** — inutile d'installer LaTeX pour lire le
dossier. Pour (re)compiler, voir la procédure détaillée dans le
[README du cahier des charges](cahier%20de%20charge/README.md) :

```bash
cd "cahier de charge/fr" && latexmk -pdf cahier_des_charges_fr.tex           # 🇫🇷 (pdflatex)
cd "cahier de charge/en" && latexmk -pdf cahier_des_charges_en.tex           # 🇬🇧 (pdflatex)
cd "cahier de charge/ar" && latexmk -pdf -xelatex cahier_des_charges_ar.tex  # 🇸🇦 (XeLaTeX, RTL)
```

> 💡 `latexmk -c` (dans chaque dossier) supprime les fichiers intermédiaires
> (`.aux`, `.log`, `.fls`, `.toc`…) sans toucher au PDF.

## 🤝 Contribution

La cohérence **trilingue** est une contrainte forte du dépôt : toute modification
de structure côté 🇫🇷 doit être répercutée en 🇬🇧 et 🇸🇦. Voir
[`CONTRIBUTING.md`](CONTRIBUTING.md) et le script de vérification
[`scripts/check-sync.sh`](scripts/check-sync.sh).

## 🎓 Contrainte académique

🚫 L'usage de générateurs de code pour produire le livrable est proscrit par
l'épreuve. ✍️ La documentation et la conception de ce dépôt sont réalisées à la main.
