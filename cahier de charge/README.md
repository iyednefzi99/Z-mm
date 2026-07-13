<p align="center">
  <img src="../assets/logo/zumm-logo.png" alt="Logo Zümm" width="240">
</p>

<h1 align="center">🐝 Zümm : Système de gestion et de suivi apicole</h1>

🍯 Mini-projet **J2EE** : application multi-niveaux de gestion et de suivi de ruches
(🐝 gestion manuelle des opérations, 📅 planification des visites, 📊 tableaux de bord,
🔔 alertes) avec préparation d'un volet capteurs / supervision automatisée. 📡

📦 Ce dépôt contient, à ce stade, le **cahier des charges** et le **dossier de
conception** (documentation LaTeX + diagrammes UML). 🚧 Le code applicatif reste à
développer.

## 📁 Structure du dépôt

```
🐝 Zümm/
├── 🎨 assets/logo/                      # Logos Zümm (principal, icône, monochrome, brandsheet)
├── 📂 cahier de charge/
│   ├── 📘 cahier_des_charges.tex        # Préambule + \input des chapitres et annexes
│   ├── 📖 README.md                     # Ce fichier
│   ├── 📂 chapitres/                     # Un dossier par chapitre
│   │   ├── 01-contexte/ … 05-dictionnaire/   (📄 .tex)
│   │   ├── 06-cas-utilisation/          (📄 .tex · 🖼️ images/ · 🖇️ diagrammes/)
│   │   ├── 07-conception/               (📄 .tex · 🖼️ images/ · 🖇️ diagrammes/)
│   │   ├── 08-contraintes/ … 11-tests/       (📄 .tex)
│   │   ├── 12-referentiel/              (📄 .tex · 🖼️ images/)
│   │   └── 13-glossaire/ · 14-references/    (📄 .tex)
│   ├── 📂 annexes/
│   │   ├── A-structure-ruche/           (📄 .tex · 🖼️ images/)
│   │   ├── B-technologies/ · C-migration/    (📄 .tex)
│   │   ├── D-solid-patterns/            (📄 .tex · 🖼️ images/ · 🖇️ diagrammes/)
│   │   ├── E-complements/               (📄 .tex · 🖼️ images/ · 🖇️ diagrammes/)
│   │   └── F-intelligence-artificielle/ (📄 .tex · 🖼️ images/ · 🖇️ diagrammes/)
│   └── 📕 cahier_des_charges.pdf        # Document compilé (livrable)
├── 🎨 design/                           # Charte de design (DESIGN.md FR/EN/AR + README)
└── 🙈 .gitignore
```

> 💡 Chaque dossier de chapitre/annexe contient son `.tex`, ses `images/` (PNG) et,
> le cas échéant, ses `diagrammes/` (sources `.puml`). Les chapitres sans figure
> n'ont qu'un `.tex`.

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

## 📄 Compiler le document

```bash
cd "cahier de charge"
latexmk -pdf cahier_des_charges.tex
```

## 🔄 Régénérer les diagrammes

Depuis `cahier de charge/`, avec Graphviz installé (ou `GRAPHVIZ_DOT` pointant
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
- 🏗️ Architecture cible : MVC, Servlets, JSP, EJB, JDBC, internationalisation XML,
  services web sécurisés (OAuth Google, X.509 / SSL). Une trajectoire de
  modernisation vers Spring Boot est documentée en annexe.

## 🎓 Contrainte académique

🚫 L'usage de générateurs de code pour produire le livrable est proscrit par
l'épreuve. ✍️ La documentation et la conception de ce dépôt sont réalisées à la main.
