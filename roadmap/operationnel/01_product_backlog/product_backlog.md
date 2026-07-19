# 📋 Product Backlog - Zümm

**Projet:** Zümm - Système de gestion apicole  
**Date:** 2026-07-13  
**Méthode:** Scrum + DevOps  
**Total Story Points:** 304  

---

## 🎯 Definition of Ready (DoR)

1. La story a un titre clair et une description
2. Les critères d'acceptation sont définis
3. Les dépendances sont identifiées
4. La story est estimée en points
5. Le Product Owner a validé la priorité

## ✅ Definition of Done (DoD)

1. Code développé et revu (peer review)
2. Tests unitaires passent (coverage ≥ 70%)
3. Tests d'intégration passent
4. Documentation mise à jour
5. Déployé sur l'environnement de staging
6. Validé par le Product Owner

---

## 📦 Epics et User Stories


### EPIC-001: Gestion des entités métier (CRUD)
**Priorité:** Haute | **Source CdC:** §4.1, §5.2, §7.1 | **Total Points:** 44

Opérations CRUD sur fermiers, fermes, sites, ruches, corps/hausses, cadres, agents

| ID | Story | Points | Priorité | Critères d'Acceptation |
|:---|:---|:---:|:---|:---|
| US-001 | CRUD Fermier | 5 | Haute | CRUD complet avec validation |
| US-002 | CRUD Ferme | 5 | Haute | Liaison fermier-ferme |
| US-003 | CRUD Site (avec géolocalisation) | 8 | Haute | Lat/long/altitude + PostGIS |
| US-004 | CRUD Ruche avec composition | 13 | Haute | 1 corps + 0-5 hausses + 1-10 cadres |
| US-005 | CRUD Agent avec rôles | 5 | Haute | Rôles: apiculteur, superviseur, responsable, admin |
| US-006 | Contraintes de composition (règles métier) | 8 | Haute | CHECK constraints SQL + validation Java |

### EPIC-002: Planification et suivi des visites
**Priorité:** Haute | **Source CdC:** §4.2, §4.2.1, §6.1, §6.2 | **Total Points:** 44

Planification, approbation, réalisation et rapport de visite

| ID | Story | Points | Priorité | Critères d'Acceptation |
|:---|:---|:---:|:---|:---|
| US-007 | Planifier une visite | 8 | Haute | Date, raison, actions prévues |
| US-008 | Approuver/Refuser un planning | 5 | Haute | Workflow approbation superviseur |
| US-009 | Réaliser une visite et remplir le rapport | 13 | Haute | Date, heure, durée, constatations, évaluations 1-3 |
| US-010 | Ajouter des photos au rapport | 5 | Haute | Upload multi-photos |
| US-011 | Mode hors-ligne et synchronisation différée | 13 | Haute | PWA + Service Worker + moteur de synchronisation (résolution de conflits) |

### EPIC-003: Tableaux de bord et visualisation
**Priorité:** Haute | **Source CdC:** §4.3, §4.3.1-4.3.4 | **Total Points:** 42

Calendrier, production, alertes, synthèse ROI

| ID | Story | Points | Priorité | Critères d'Acceptation |
|:---|:---|:---:|:---|:---|
| US-012 | Calendrier matrice agents × ruches | 13 | Haute | Filtres mois/semaine/saison/année + pop-up |
| US-013 | Tableau de bord production | 8 | Haute | Seuils paramétrables + collecte/extension |
| US-014 | Tableau de bord alertes sanitaires | 8 | Haute | Baisse anormale détectée + inspection |
| US-015 | Tableau de bord synthèse et ROI | 13 | Moyenne | Graphiques production, interventions, ROI |

### EPIC-004: Indicateurs et capteurs (préparation)
**Priorité:** Moyenne | **Source CdC:** §4.4, §4.4.1-4.4.3 | **Total Points:** 29

Modèle de données ouvert pour intégration future des capteurs

| ID | Story | Points | Priorité | Critères d'Acceptation |
|:---|:---|:---:|:---|:---|
| US-016 | Modèle de données Mesure | 8 | Moyenne | Horodatage, géolocalisation, unités paramétrables |
| US-017 | Interface ingestion REST/MQTT | 8 | Moyenne | POST /api/mesures + topic MQTT |
| US-018 | Seuils paramétrables et logique d'alerte | 8 | Moyenne | Anti-rebond/hystérésis |
| US-019 | Conversion d'unités hétérogènes | 5 | Moyenne | Strategy pattern |

### EPIC-005: Authentification et sécurité
**Priorité:** Haute | **Source CdC:** §8.2, §8.3, Annexe I | **Total Points:** 26

Keycloak (OIDC, fédération Google), RBAC, TLS/X.509

| ID | Story | Points | Priorité | Critères d'Acceptation |
|:---|:---|:---:|:---|:---|
| US-020 | Authentification OIDC (Keycloak, fédération Google) | 8 | Haute | Authorization Code Flow + JWT |
| US-021 | Authentification locale (fallback) | 5 | Haute | Comptes locaux Keycloak si fédération indisponible |
| US-022 | Contrôle d'accès RBAC | 8 | Haute | Matrice 6 profils × 15 fonctions |
| US-023 | Chiffrement TLS 1.3 / X.509 | 5 | Haute | HTTPS forcé + certificats |

### EPIC-006: Internationalisation et configuration
**Priorité:** Haute | **Source CdC:** §8.2, §8.3 | **Total Points:** 13

i18n FR/EN/AR, ConfigZumm.ini

| ID | Story | Points | Priorité | Critères d'Acceptation |
|:---|:---|:---:|:---|:---|
| US-024 | Internationalisation (FR/EN/AR) | 8 | Haute | RTL arabe + extensible |
| US-025 | Configuration ConfigZumm.ini | 5 | Haute | Seuils, unités, modules sans recompilation |

### EPIC-007: Service web API tierce
**Priorité:** Moyenne | **Source CdC:** §6.5, §8.2 | **Total Points:** 10

Exposition API pour applications externes

| ID | Story | Points | Priorité | Critères d'Acceptation |
|:---|:---|:---:|:---|:---|
| US-026 | Service web REST getZummHoneyActualQuantity | 5 | Moyenne | REST + contrat OpenAPI 3 |
| US-027 | Export CSV/TXT | 5 | Moyenne | Données maîtrisées par l'utilisateur |

### EPIC-008: Fonctionnalités avancées (inspiration marché)
**Priorité:** Moyenne | **Source CdC:** §4.6, §4.7 | **Total Points:** 36

Fonctions complémentaires HiveTracks, Apiary Book, BeePlus

| ID | Story | Points | Priorité | Critères d'Acceptation |
|:---|:---|:---:|:---|:---|
| US-028 | Photos d'inspection | 5 | Haute | Attachées au rapport |
| US-029 | Contexte météo local | 5 | Moyenne | API météo par géolocalisation |
| US-030 | Carte et rayons de butinage | 8 | Moyenne | MapLibre GL + OpenStreetMap + cercles 1/2/3km |
| US-031 | Liste de tâches et rappels | 5 | Haute | Calendrier rappels |
| US-032 | Suivi de la reine | 5 | Haute | Historique statut reine |
| US-033 | Récolte et QR code | 8 | Moyenne | QR par lot + traçabilité |

### EPIC-009: Intelligence Artificielle (préparation)
**Priorité:** Moyenne | **Source CdC:** Annexe H | **Total Points:** 21

Détection d'anomalie adaptative, pipeline IA

| ID | Story | Points | Priorité | Critères d'Acceptation |
|:---|:---|:---:|:---|:---|
| US-034 | Détection d'anomalie adaptative (EWMA) | 13 | Moyenne | Ligne de base par ruche + z-score |
| US-035 | Interface microservice IA Python | 8 | Moyenne | REST/JSON découplé |

### EPIC-010: Tests, qualité et documentation
**Priorité:** Haute | **Source CdC:** Chapitre 10, 11, Annexe F | **Total Points:** 39

Plan de tests, UML, rapport, poster

| ID | Story | Points | Priorité | Critères d'Acceptation |
|:---|:---|:---:|:---|:---|
| US-036 | Tests unitaires JUnit 5 + Mockito | 8 | Haute | 70% couverture métier |
| US-037 | Tests d'intégration (Testcontainers) | 5 | Haute | Tests sur PostgreSQL réel (PostGIS + TimescaleDB) |
| US-038 | Tests de charge k6 | 5 | Moyenne | p95 < 500ms, erreurs < 1% |
| US-039 | Diagrammes UML complets | 13 | Haute | 10 diagrammes requis |
| US-040 | Rapport + Poster + Présentation | 8 | Haute | Livrables soutenance |
