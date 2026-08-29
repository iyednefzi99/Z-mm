# Roadmap de développement Zümm (LaTeX)

Version LaTeX consolidée du dossier `roadmap/operationnel/`
(backlog, sprints, pipeline CI/CD, releases, monitoring), dans la même
charte que le cahier des charges (palette « miel & noir élégant »).

## Structure

```
roadmap/
├── roadmap_zumm.tex          # Document maître
├── roadmap_zumm.pdf          # PDF compilé (55 pages)
├── operationnel/             # Sources opérationnelles (JSON/MD/CI) — voir GUIDE_MODIFICATION.md
│   ├── 01_product_backlog/   # product_backlog.json | .md
│   ├── 02_sprints/           # SPRINT-00…20.md + sprints.json + REVUE-CONSOLIDEE.md
│   ├── 03_devops_pipeline/   # Dockerfile, docker-compose.yml, github-actions.yml
│   ├── 04_releases/          # releases.json
│   ├── 05_monitoring/        # monitoring.json
│   ├── 06_decisions/         # ADR-001…011 + registre
│   └── 07_conformite/        # AIPD (RGPD)
└── chapitres/
    ├── 01-methodologie.tex   # Scrum + DevOps, DoR, DoD
    ├── 02-backlog.tex        # 21 epics, 92 US, 651 points
    ├── 03-sprints.tex        # Gantt + recommandations + plan d'exécution détaillé par sprint
    ├── 04-risques.tex        # Registre des risques coté (13 risques), charge j-h, vélocité
    ├── 05-devops.tex         # Environnements, pipeline CI/CD, IaC, mise en place progressive
    ├── 06-releases.tex       # Plan SemVer v0.1.0 → v1.5.0
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

Le plan initial couvrait 8 sprints pour **304 points**, après un
rééquilibrage de la charge dans une fourchette de 36 à 39 points sous une
capacité de référence portée à 40 (détail au chapitre 3). Le périmètre s'est
ensuite étendu jusqu'au SPRINT-20, pour un total de **21 epics, 92 user
stories et 651 points**.

⚠️ Sur ces 651 points, **21 ne sont pas livrés, et ne peuvent pas l'être ici** —
US-039 (diagrammes UML) et US-040 (rapport, poster, présentation) sont des
livrables documentaires que la charte académique de l'épreuve interdit de
générer, et qui restent à produire.

Les 35 points du SPRINT-20 (registre sanitaire), en cours au relevé précédent,
sont livrés depuis le 29/08/2026 — de la migration `V19` à l'écran.

La vélocité applicative **réellement livrée** est donc de **630 points**.
