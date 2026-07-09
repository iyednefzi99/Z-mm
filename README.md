# 🐝 Zümm — Système de gestion et de suivi apicole

🍯 Mini-projet **J2EE** : application multi-niveaux de gestion et de suivi de ruches
(🐝 gestion manuelle des opérations, 📅 planification des visites, 📊 tableaux de bord,
🔔 alertes) avec préparation d'un volet capteurs / supervision automatisée. 📡

📦 Ce dépôt contient, à ce stade, le **cahier des charges** et le **dossier de
conception** (documentation LaTeX + diagrammes UML). 🚧 Le code applicatif reste à
développer.

## 📁 Structure du dépôt

```
🐝 Zümm/
├── 📂 cahier de charge/
│   ├── 📘 cahier_des_charges.tex          # Document principal (LaTeX)
│   ├── 🚀 annexe_migration_springboot.tex # Annexe : J2EE -> Spring Boot
│   ├── 🧱 annexe_solid_patterns.tex       # Annexe E : SOLID & design patterns
│   ├── 🧩 annexe_complements.tex          # Annexe F : MLD, IHM, RBAC, risques, RGPD, tests
│   ├── 🖇️ *.puml                          # Sources des diagrammes (UML, MLD, maquettes)
│   ├── 🖼️ images/                         # PNG générés + illustrations
│   └── 📕 cahier_des_charges.pdf          # Document compilé (livrable)
├── 🙈 .gitignore
└── 📖 README.md
```

## 📚 Contenu du dossier

- 📋 **Cahier des charges** (méthode Manager-GO) : contexte, objectifs, périmètre,
  besoins fonctionnels, dictionnaire de données, cas d'utilisation, plan de tests.
- 📐 **Conception UML** : cas d'utilisation, classes, objets, séquence, activité,
  machine à états, collaboration, composants, déploiement.
- ✨ **Qualité de conception** : principes **SOLID** et **design patterns**
  (Composite, State, Strategy, Observer, Command, Factory, Builder, Adapter,
  Facade, Singleton, Proxy), documentés « avant / après » (annexe E).
- 🧩 **Compléments** : modèle logique de données (MLD), maquettes d'écrans,
  matrice des droits (RBAC), registre des risques, RGPD, stratégie de tests
  (annexe F).

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
sur `dot`) :

```bash
java -jar plantuml.jar -tpng -o images *.puml
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
