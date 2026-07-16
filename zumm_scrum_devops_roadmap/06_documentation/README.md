# 🐝 Zümm - Roadmap de Développement Scrum + DevOps

> **Projet:** Système de gestion et suivi apicole (J2EE)  
> **Méthodologie:** Scrum (8 sprints × 2 semaines) + DevOps (CI/CD)  
> **Date de création:** 2026-07-13  
> **Version:** 1.0  

---

## 📁 Structure du Dossier

```
zumm_scrum_devops_roadmap/
├── 01_product_backlog/          # Backlog produit complet (Epics + Stories)
│   ├── product_backlog.json     # Format machine (JSON)
│   └── product_backlog.md       # Format lisible (Markdown)
│
├── 02_sprints/                  # 8 Sprints Scrum détaillés
│   ├── sprints.json             # Planification globale
│   ├── SPRINT-01.md             # Sprint 1: Fondation CRUD
│   ├── SPRINT-02.md             # Sprint 2: Ruche & Composition
│   ├── SPRINT-03.md             # Sprint 3: Visites & Rapports
│   ├── SPRINT-04.md             # Sprint 4: Hors-ligne & Sync
│   ├── SPRINT-05.md             # Sprint 5: Tableaux de Bord
│   ├── SPRINT-06.md             # Sprint 6: Capteurs & API
│   ├── SPRINT-07.md             # Sprint 7: Fonctions Avancées
│   └── SPRINT-08.md             # Sprint 8: Qualité & Livraison
│
├── 03_devops_pipeline/          # Pipeline CI/CD complète
│   ├── devops_pipeline.json     # Configuration pipeline
│   ├── github-actions.yml       # Workflow GitHub Actions
│   ├── Dockerfile               # Conteneurisation WildFly
│   └── docker-compose.yml       # Stack complète (App + DB + Monitoring)
│
├── 04_releases/                 # Gestion des versions
│   └── releases.json            # Plan de releases (SemVer)
│
├── 05_monitoring/               # Observabilité
│   └── monitoring.json          # Dashboards & Alertes
│
└── 06_documentation/            # Documentation projet
    └── README.md                # Ce fichier
```

---

## 🎯 Vue d'ensemble Scrum

| Sprint | Thème | Points | Période | Livrable Clé |
|:---:|:---|:---:|:---|:---|
| 1 | Fondation - CRUD Core | 31 | 14/07 → 27/07 | CRUD entités métier |
| 2 | Ruche & Composition | 26 | 28/07 → 10/08 | Hiérarchie ruche complète |
| 3 | Visites & Rapports | 31 | 11/08 → 24/08 | Workflow visite complet |
| 4 | Hors-ligne & Sync | 26 | 25/08 → 07/09 | PWA + OAuth |
| 5 | Tableaux de Bord | 42 | 08/09 → 21/09 | Dashboards analytics |
| 6 | Capteurs & API | 39 | 22/09 → 05/10 | Ingestion + API SOAP |
| 7 | Fonctions Avancées | 47 | 06/10 → 19/10 | Carte, IA, QR code |
| 8 | Qualité & Livraison | 42 | 20/10 → 02/11 | Tests + Soutenance |

**Total:** 284 story points sur 16 semaines

---

## 🔄 Pipeline DevOps

```
┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐
│  BUILD  │ → │  TEST   │ → │  QUALITÉ│ → │  DOCKER │ → │ DEPLOY  │
│  Maven  │    │ JUnit   │    │ Sonar   │    │ Build   │    │ Staging │
│  (3min) │    │ (5min)  │    │ (5min)  │    │ (6min)  │    │ (2min)  │
└─────────┘    └─────────┘    └─────────┘    └─────────┘    └─────────┘
                                              │
                                              ▼
                                       ┌─────────────┐
                                       │  E2E + Load │
                                       │  Selenium   │
                                       │  k6         │
                                       │  (25min)    │
                                       └─────────────┘
                                              │
                                              ▼ (approval)
                                       ┌─────────────┐
                                       │ Production  │
                                       │  (manuel)   │
                                       └─────────────┘
```

---

## 🚀 Démarrage Rapide

### 1. Cloner et configurer
```bash
git clone <repo-zumm>
cd zumm
```

### 2. Lancer en local (Docker)
```bash
docker-compose up -d
# Accès: http://localhost:8080/zumm
```

### 3. Lancer les tests
```bash
mvn clean test                    # Tests unitaires
mvn verify -P integration-tests  # Tests intégration
k6 run tests/load/load_test.js   # Tests charge
```

### 4. Déployer
```bash
# Staging (auto)
git push origin release/v0.x

# Production (manuel)
git push origin main
# → Approval requis dans GitHub Actions
```

---

## 📊 Monitoring

- **Grafana:** http://localhost:3000 (admin/admin)
- **Prometheus:** http://localhost:9090
- **App Health:** http://localhost:8080/zumm/api/health

---

## 📝 Comment Modifier ce Roadmap

Ce dossier est **entièrement modifiable**. Pour mettre à jour:

1. **Modifier le backlog:** Éditer `01_product_backlog/product_backlog.json`
2. **Ajuster les sprints:** Modifier les fichiers dans `02_sprints/`
3. **Changer la CI/CD:** Éditer `03_devops_pipeline/github-actions.yml`
4. **Mettre à jour les releases:** Modifier `04_releases/releases.json`

> 💡 **Astuce:** Utiliser un éditeur JSON avec validation pour éviter les erreurs de syntaxe.

---

## 👥 Équipe Scrum

| Rôle | Responsable | Description |
|:---|:---|:---|
| **Product Owner** | *À définir* | Priorise le backlog, valide les livrables |
| **Scrum Master** | *À définir* | Facilite les cérémonies, enlève les blocages |
| **Dev Team** | *Équipe étudiante* | Développe, teste, documente |
| **DevOps Engineer** | *À définir* | Gère la CI/CD, le monitoring, le déploiement |

---

## 📚 Références

- **Cahier des Charges:** `cahier_des_charges_fr.pdf`
- **Méthode Manager-GO:** Pour la rédaction du CdC
- **Scrum Guide:** https://scrumguides.org/
- **DevOps Handbook:** Pour les pratiques CI/CD

---

*Document généré automatiquement le 2026-07-13. Dernière mise à jour: ___/___/______*
