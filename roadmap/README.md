# Roadmap de développement Zümm (LaTeX)

Version LaTeX consolidée du dossier `zumm_scrum_devops_roadmap/`
(backlog, sprints, pipeline CI/CD, releases, monitoring), dans la même
charte que le cahier des charges (palette « miel & noir élégant »).

## Structure

```
roadmap/
├── roadmap_zumm.tex          # Document maître
├── roadmap_zumm.pdf          # PDF compilé (31 pages)
└── chapitres/
    ├── 01-methodologie.tex   # Scrum + DevOps, DoR, DoD
    ├── 02-backlog.tex        # 10 epics, 40 US, 304 points
    ├── 03-sprints.tex        # Gantt + recommandations + plan d'exécution détaillé par sprint
    ├── 04-risques.tex        # Registre des risques coté (13 risques), charge j-h, vélocité
    ├── 05-devops.tex         # Environnements, pipeline CI/CD, IaC, mise en place progressive
    ├── 06-releases.tex       # Plan SemVer v0.1.0 → v1.0.0
    └── 07-monitoring.tex     # Dashboards Grafana, alertes, logs
```

## Compilation

```bash
cd roadmap
pdflatex roadmap_zumm.tex
pdflatex roadmap_zumm.tex   # 2e passe : sommaire et références croisées
```

Prérequis : distribution LaTeX avec `pgfgantt`, `babel-french`, `booktabs`,
`mdframed`, `titlesec` (MiKTeX les installe à la volée). Le logo est lu
depuis `../assets/logo/zumm-logo.png`.

## Note sur les story points

Les fichiers JSON source annonçaient 284 points planifiés ; la somme réelle
des stories affectées aux sprints est de **304 points** (écarts corrigés sur
les sprints 5, 7 et 8 : 50, 57 et 44 points). Le document signale la
surcharge des sprints 5 et 7 par rapport à la vélocité cible (38 pts) et
propose un rééquilibrage.
