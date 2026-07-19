<p align="center">
  <img src="../assets/logo/zumm-logo.png" alt="Logo Zümm" width="240">
</p>

<h1 align="center">🐝 Zümm : Système de gestion et de suivi apicole</h1>

🍯 Application **multi-niveaux** de gestion et de suivi de ruches
(🐝 gestion manuelle des opérations, 📅 planification des visites, 📊 tableaux de bord,
🔔 alertes) avec préparation d'un volet capteurs / supervision automatisée. 📡

📦 Ce dépôt contient, à ce stade, le **cahier des charges** — décliné en
**trois langues** (🇫🇷 français · 🇬🇧 anglais · 🇸🇦 arabe) — et le **dossier de
conception** (documentation LaTeX + diagrammes UML). 🚧 Le code applicatif reste à
développer.

## 📁 Structure du dépôt

```
🐝 Zümm/
├── 🎨 assets/logo/                      # Logos Zümm (principal, icône, monochrome, brandsheet)
├── 📂 cahier de charge/                 # Cahier des charges trilingue (🇫🇷 🇬🇧 🇸🇦)
│   ├── 📖 README.md                     # Ce fichier
│   ├── 🇫🇷 fr/                          # Version française — SOURCE (texte + figures partagées)
│   │   ├── 📘 cahier_des_charges_fr.tex # pdflatex → cahier_des_charges_fr.pdf
│   │   ├── 📂 chapitres/                # 01-contexte … 14-references (📄 .tex · 🖼️ images/ · 🖇️ diagrammes/)
│   │   └── 📂 annexes/                  # A-structure-ruche … G-securite
│   ├── 🇬🇧 en/                          # English — pdflatex → cahier_des_charges_en.pdf
│   │   ├── 📘 cahier_des_charges_en.tex
│   │   └── 📂 chapitres/ + annexes/     # Traductions (figures réutilisées depuis fr/)
│   └── 🇸🇦 ar/                          # العربية — xelatex, RTL → cahier_des_charges_ar.pdf
│       ├── 📘 cahier_des_charges_ar.tex
│       └── 📂 chapitres/ + annexes/     # ترجمة (نفس الرسوم من fr/)
├── 🎨 design/                           # Charte de design (DESIGN.md FR/EN/AR + README)
└── 🙈 .gitignore
```

> 💡 Les **figures** (diagrammes UML en PNG et images) vivent dans `fr/` et sont
> **partagées** par les trois langues (`en/` et `ar/` y pointent via `graphicspath`) :
> le texte des figures reste donc en français, seuls les libellés TikZ et les
> légendes sont traduits. Chaque dossier de chapitre/annexe contient son `.tex`,
> ses `images/` (PNG) et, le cas échéant, ses `diagrammes/` (sources `.puml`).

## 📚 Contenu du dossier

- 📋 **Cahier des charges** (méthode Manager-GO) : contexte, objectifs, périmètre,
  besoins fonctionnels, dictionnaire de données, cas d'utilisation, plan de tests.
- 📐 **Conception UML** : cas d'utilisation, classes, objets, séquence, activité,
  machine à états, collaboration, composants, déploiement.
- ✨ **Qualité de conception** : principes **SOLID** et **design patterns**
  (Composite, State, Strategy, Observer, Command, Factory, Builder, Adapter,
  Facade, Singleton, Proxy), documentés « avant / après » (annexe D).
- 🧩 **Compléments** : modèle logique de données (MLD), maquettes d'écrans,
  matrice des droits (RBAC), registre des risques, RGPD, stratégie de tests
  (annexe E).
- 🤖 **Intelligence artificielle** : stratégie d'intégration de l'IA en aide à la
  décision — détection d'anomalie adaptative (EWMA + z-score), architecture
  découplée (Strategy + microservice), usages anticipés (acoustique, vision,
  saisie vocale) et éthique (annexe F).
- 🔒 **Sécurité et robustesse** : défense en profondeur (WAF, TLS/X.509, RBAC,
  anti-injection, en-têtes de sécurité), grille de durcissement, tests de
  charge / fiabilité avec **k6** et checklist de pré-déploiement (annexe G).

## 🎨 Charte de design

📐 Le dossier [`../design/`](../design/) contient la **charte de design** de Zümm :
la source de vérité visuelle (couleurs, typographie, espacements, composants, usage
du logo). Elle suit le format [`DESIGN.md`](https://getdesign.md) et la méthode
[Adobe Spectrum](https://spectrum.adobe.com), et existe en trois langues :
🇫🇷 [`DESIGN.md`](../design/DESIGN.md) · 🇬🇧 [`DESIGN.en.md`](../design/DESIGN.en.md) ·
🇸🇦 [`DESIGN.ar.md`](../design/DESIGN.ar.md). 🚦 C'est une **référence** pour le futur
front-end, pas du code.

## 🛠️ Prérequis (outillage documentaire)

- 📝 **LaTeX** : une distribution complète (MiKTeX, TeX Live) avec `latexmk`.
- 🌱 **PlantUML** (`plantuml.jar`) + ☕ **Java 11+** pour régénérer les diagrammes.
- 📊 **Graphviz** (`dot`) : requis par PlantUML pour les diagrammes de classes,
  d'objets, de cas d'utilisation et le MLD (le moteur Smetana intégré ne suffit
  pas pour ces diagrammes).

## 📄 Compiler les documents

Chaque langue se compile depuis son propre dossier :

```bash
cd "cahier de charge/fr" && latexmk -pdf cahier_des_charges_fr.tex           # 🇫🇷 français (pdflatex)
cd "cahier de charge/en" && latexmk -pdf cahier_des_charges_en.tex           # 🇬🇧 anglais  (pdflatex)
cd "cahier de charge/ar" && latexmk -pdf -xelatex cahier_des_charges_ar.tex  # 🇸🇦 arabe    (XeLaTeX, RTL)
```

> 💡 La version **arabe** nécessite **XeLaTeX** (RTL via `polyglossia` / `bidi` +
> police *Traditional Arabic*) ; le français et l'anglais utilisent **pdflatex**.
> Les identifiants de code et d'API (SQL, `getZummHoneyActualQuantity`,
> `ConfigZumm.ini`, noms de classes) restent identiques dans les trois langues.

## 🔄 Régénérer les diagrammes

Depuis `cahier de charge/fr/`, avec Graphviz installé (ou `GRAPHVIZ_DOT` pointant
sur `dot`). Chaque `.puml` est régénéré vers le dossier `images/` voisin :

```bash
for d in chapitres/*/diagrammes annexes/*/diagrammes; do
  java -jar plantuml.jar -tpng -o ../images "$d"/*.puml
done
```

> 💡 Les maquettes (`wireframe_*.puml`) utilisent la syntaxe **Salt** de PlantUML.

## ⚙️ Notes techniques

- 🔑 L'identifiant d'API `getZummHoneyActualQuantity(int zummID)` et le fichier de
  configuration `ConfigZumm.ini` portent le nom du produit **Zümm** (forme ASCII
  « Zumm » pour les identifiants de code et noms de fichiers).
- 🏗️ Architecture cible (annexe B « technologies ») : back-end **Spring Boot 3**
  (JDK 17, Docker) avec Spring MVC, Spring Data JPA + jOOQ ; **PostgreSQL + PostGIS
  + TimescaleDB** ; front **React 19 + TypeScript** en PWA ; API REST + OpenAPI 3 ;
  **Keycloak** (OIDC + fédération Google), JWT et RBAC ; échanges TLS 1.3 / X.509.

## 🎓 Contrainte académique

🚫 L'usage de générateurs de code pour produire le livrable est proscrit par
l'épreuve. ✍️ La documentation et la conception de ce dépôt sont réalisées à la main.
