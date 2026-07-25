# 📋 Product Backlog - Zümm

**Projet:** Zümm - Système de gestion apicole  
**Date:** 2026-07-13  
**Méthode:** Scrum + DevOps  
**Total Story Points:** 398 (54 user stories, 13 epics)  

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
**Priorité:** Haute | **Source CdC:** §8.2, §8.3 | **Total Points:** 18

i18n FR/EN/AR, ConfigZumm.ini

| ID | Story | Points | Priorité | Critères d'Acceptation |
|:---|:---|:---:|:---|:---|
| US-024 | Internationalisation (FR/EN/AR) | 8 | Haute | RTL arabe + extensible |
| US-025 | Configuration ConfigZumm.ini | 5 | Haute | Seuils, unités, modules sans recompilation |
| US-053 | Formatage localisé des dates et des nombres | 5 | Haute | `Intl` par locale, messages d'erreur traduits, rendu vérifié dans les trois langues |

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
**Priorité:** Haute | **Source CdC:** Chapitre 10, 11, Annexe F | **Total Points:** 52

Plan de tests, UML, rapport, poster

| ID | Story | Points | Priorité | Critères d'Acceptation |
|:---|:---|:---:|:---|:---|
| US-036 | Tests unitaires JUnit 5 + Mockito | 8 | Haute | 70% couverture métier |
| US-037 | Tests d'intégration (Testcontainers) | 5 | Haute | Tests sur PostgreSQL réel (PostGIS + TimescaleDB) |
| US-038 | Tests de charge k6 | 5 | Moyenne | p95 < 500ms, erreurs < 1% |
| US-039 | Diagrammes UML complets | 13 | Haute | 10 diagrammes requis |
| US-040 | Rapport + Poster + Présentation | 8 | Haute | Livrables soutenance |
| US-048 | Alignement du backlog produit et de la roadmap | 5 | Haute | Backlog, `sprints.json` et chapitres LaTeX en phase avec le produit livré |
| US-049 | Socle de tests et de linting du front-end | 8 | Haute | Vitest + Testing Library, ESLint + Prettier, joués par la CI |

### EPIC-011: Exploitation et restitution
**Priorité:** Moyenne | **Source CdC:** §4.3, §6.5, Annexe G | **Total Points:** 26

Notifications d'alerte, restitution documentaire, traçabilité des actions et anticipation de la récolte

| ID | Story | Points | Priorité | Critères d'Acceptation |
|:---|:---|:---:|:---|:---|
| US-041 | Notifications e-mail des alertes de seuil | 5 | Moyenne | Envoi sur franchissement de seuil, désactivable par configuration |
| US-042 | Prévision de récolte (tendance du poids) | 8 | Moyenne | Régression linéaire sur la série de poids + projection 7 jours |
| US-043 | Journal d'audit (qui a fait quoi, quand) | 8 | Haute | Aspect AOP sur les services, table dédiée avec RLS, consultation restreinte |
| US-044 | Rapport de visite au format PDF | 5 | Moyenne | Téléchargement du rapport d'une visite, mise en page conforme à la charte |

### EPIC-012: Intelligence spatiale (PostGIS)
**Priorité:** Moyenne | **Source CdC:** §3.5 Perspectives, Annexe B | **Total Points:** 21

Exploitation de la base spatiale au-delà de l'affichage : regroupement, voisinage et ordonnancement des déplacements

| ID | Story | Points | Priorité | Critères d'Acceptation |
|:---|:---|:---:|:---|:---|
| US-045 | Regroupement spatial des sites (clustering) | 8 | Moyenne | `ST_ClusterDBSCAN` en base, sites isolés conservés en grappe singleton |
| US-046 | Distances inter-sites et plus proches voisins | 5 | Moyenne | Parcours d'index KNN, distance géodésique en mètres |
| US-047 | Ordre de tournée optimisé pour les visites planifiées | 8 | Moyenne | Heuristique plus proche voisin + 2-opt, ordre indicatif et non contraignant |

### EPIC-013: Robustesse et ergonomie du front
**Priorité:** Haute | **Source CdC:** §8.2, §8.3, Annexe I | **Total Points:** 29

Session durable, navigation adressable, listes paginées et dialogues cohérents avec le design system. Épic ouvert à la suite de l'audit du front mené après le SPRINT-10.

| ID | Story | Points | Priorité | Critères d'Acceptation |
|:---|:---|:---:|:---|:---|
| US-050 | Rafraîchissement du jeton OIDC | 8 | Haute | Jeton renouvelé avant expiration, verrou sur les rafraîchissements concurrents, saisie préservée |
| US-051 | Navigation adressable par URL | 8 | Haute | Une route par écran, bouton retour fonctionnel, retour sur la route demandée après reconnexion |
| US-052 | Pagination des listes | 8 | Haute | Paramètres `page`/`taille` côtés serveur et client, taille par défaut issue de `ConfigZumm.ini` |
| US-054 | Dialogues du design system | 5 | Moyenne | Plus aucun `window.alert/confirm/prompt`, dialogues traduits, pilotés au clavier avec piège de focus |
