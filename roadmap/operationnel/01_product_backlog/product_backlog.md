# 📋 Product Backlog - Zümm

**Projet:** Zümm - Système de gestion apicole  
**Date:** 2026-07-13  
**Méthode:** Scrum + DevOps  
**Total Story Points:** 569 (78 user stories, 19 epics)  
**Dernière mise à jour :** 2026-07-26 (fin du SPRINT-17)  

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

### EPIC-014: Durcissement de la sécurité
**Priorité:** Critique | **Source CdC:** Annexe G, §6.4 | **Total Points:** 34

Fermer les écarts entre la sécurité **documentée** et la sécurité **réelle**. Épic
ouvert à la suite d'une revue d'architecture menée sur `main` : six garanties
annoncées au cahier reposaient sur une configuration absente, un contrôle manquant
ou une règle jamais appliquée. Aucun de ces écarts n'était visible en test.

| ID | Story | Points | Priorité | Critères d'Acceptation |
|:---|:---|:---:|:---|:---|
| US-058 | Claim `tenant_id` garanti et refus explicite | 5 | Critique | Mapper présent dans **les deux** realms ; un jeton sans `tenant_id` reçoit 403, jamais une liste vide |
| US-059 | Validation d'audience du jeton | 3 | Critique | Un jeton émis pour un autre client du même royaume est refusé en 401 |
| US-060 | Confidentialité des positions de ruchers | 8 | Critique | Arrondi selon le rôle, altitude masquée, distances des voisins dégradées à 100 m ; tout DTO portant des coordonnées y passe |
| US-061 | RBAC en refus par défaut et identité machine | 8 | Critique | `anyRequest().hasAnyRole(...)` ; un jeton valide sans rôle métier n'atteint aucun endpoint ; client `zumm-capteur` borné au dépôt de mesures |
| US-062 | En-têtes de sécurité du proxy inverse | 5 | Haute | CSP sans `unsafe-inline`, `Permissions-Policy`, COOP/CORP, zone de débit sur l'authentification, console d'administration Keycloak bloquée |
| US-063 | Portes de sécurité bloquantes dans la CI | 5 | Haute | SBOM CycloneDX produit depuis les artefacts **résolus**, lu par OSV ; une dépendance vulnérable fait échouer la chaîne |

### EPIC-015: PWA déployable et restitution visuelle
**Priorité:** Haute | **Source CdC:** §8.1, Annexe B, [ADR-007](../06_decisions/ADR-007-graphiques-svg.md) | **Total Points:** 32

Rendre le front servi par la pile, démarrable sans réseau, et capable de montrer
les données au lieu de les tabuler. Le front était complet et testé depuis trois
sprints — et n'était servi par personne.

| ID | Story | Points | Priorité | Critères d'Acceptation |
|:---|:---|:---:|:---|:---|
| US-064 | Déploiement de la PWA dans la pile | 5 | Critique | Image front construite et servie par le proxy ; `/` rend la console, plus la console d'administration Keycloak |
| US-065 | Démarrage hors ligne réel (précache généré) | 8 | Haute | Précache produit **au build** ; l'application relancée à froid sans réseau rend ses écrans ; fond cartographique exclu du précache |
| US-066 | Graphiques des tableaux de bord | 8 | Haute | Courbe multi-séries, barres, tuiles en SVG natif ; légende et équivalent tabulaire obligatoires, jamais d'identité par la seule couleur |
| US-067 | Fond cartographique réel (MapLibre + OSM) | 8 | Haute | Rayons de butinage **géodésiques**, justes à toute latitude ; repli SVG quand WebGL manque ; tuiles paramétrables |
| US-068 | Bandeau de mise à jour de la PWA | 3 | Moyenne | Une nouvelle version déployée propose sa mise à jour sans perdre l'écran courant |

### EPIC-016: Fiabilité de la synchronisation et tenue à l'échelle
**Priorité:** Critique | **Source CdC:** §7.3, Annexe D | **Total Points:** 21

Qu'une saisie hors ligne ne se duplique ni ne se perde, et que les lectures
tiennent sur un parc réel. Épic issu du scénario de terrain « le réseau tombe
entre la requête et la réponse ».

| ID | Story | Points | Priorité | Critères d'Acceptation |
|:---|:---|:---:|:---|:---|
| US-055 | Idempotence des mutations rejouées | 8 | Critique | Clé générée **avant** la première tentative ; même empreinte → rejeu, empreinte différente → 409 ; un 401 n'évacue plus la saisie de la file |
| US-069 | Index et garde-fous d'exécution sur `mesure` | 5 | Haute | Index avec le tenant en tête ; `statement_timeout`, `lock_timeout` et `idle_in_transaction_session_timeout` posés sur le rôle applicatif |
| US-070 | Suppression des N+1 sur les listages | 5 | Haute | Six repositories ramenés à une requête par listage, vérifié au compteur de requêtes |
| US-071 | Mesure et plancher de couverture (JaCoCo) | 3 | Moyenne | Campagnes unitaire et intégration **fusionnées** ; la chaîne échoue sous le plancher |

### EPIC-017: Conformité réglementaire du miel
**Priorité:** Critique | **Source CdC:** §3.4, Annexe F | **Total Points:** 13

Étiquetage conforme à la directive (UE) 2024/1438, applicable au **14 juin 2026**
(décret n° 2026-312).

| ID | Story | Points | Priorité | Critères d'Acceptation |
|:---|:---|:---:|:---|:---|
| US-056 | Lot de conditionnement et mention d'origine | 13 | Critique | Parts consolidées **par pays**, triées par ordre décroissant, totalisant 100 % ; référence de récolte **facultative** pour représenter le miel acquis à un tiers ; mention rendue dans la langue négociée |

### EPIC-018: Modèle d'autorisation complet
**Priorité:** Critique | **Source CdC:** Annexe G, [ADR-006](../06_decisions/ADR-006-stockage-des-jetons.md) | **Total Points:** 42

Que le navigateur ne détienne plus de jeton, et qu'un agent ne puisse pas énumérer
le parc entier de son exploitation. La RLS isolait les exploitations entre elles —
pas les agents à l'intérieur d'une même exploitation.

| ID | Story | Points | Priorité | Critères d'Acceptation |
|:---|:---|:---:|:---|:---|
| US-072 | Traductions en ressources de locale, chargées à la demande | 8 | Moyenne | Trois JSON ouvrables par un traducteur ; la garantie de parité tient toujours **à la compilation** ; une session ne télécharge que la langue affichée |
| US-073 | BFF : jetons détenus par le serveur | 21 | Critique | Aucun jeton dans `localStorage` ni `sessionStorage` ; cookie `HttpOnly`/`Secure`/`SameSite` ; échange, rafraîchissement et révocation entre le BFF et Keycloak |
| US-057 | Portée d'autorisation par affectation d'agent | 13 | Critique | Règle posée **dans le SGBD** ; tenant et portée posés dans la **même** requête préparée ; l'absence de portée vaut « rien voir » ; lien compte-agent par le `sub` OIDC, pas par le courriel |

### EPIC-019: Dette technique et contrat vérifié
**Priorité:** Haute | **Source CdC:** Annexe B, [ADR-002](../06_decisions/ADR-002-volumetrie.md) | **Total Points:** 29

Vider la liste de dettes de [`docs/ARCHITECTURE-SOLID.md`](../../../docs/ARCHITECTURE-SOLID.md)
plutôt que la reconduire de sprint en sprint. Trois de ces points figuraient **mot
pour mot** dans « ce qui reste ouvert » des SPRINT-14, 15 et 16.

| ID | Story | Points | Priorité | Critères d'Acceptation |
|:---|:---|:---:|:---|:---|
| US-074 | Agrégats des tableaux de bord calculés en base | 8 | Haute | Production et alertes agrégées en SQL, prouvées **sous le rôle applicatif `zumm_app`** ; le calendrier reste en mémoire, sa requête étant bornée par une période |
| US-075 | Scission de `TableauDeBordService` | 3 | Moyenne | Trois services, une raison de changer chacun ; contrat HTTP inchangé, aucun test d'intégration retouché |
| US-076 | Parité garantie entre client TypeScript et contrat OpenAPI | 8 | Haute | Contrat publié sous forme de fichier versionné ; une divergence casse `tsc` **en nommant le champ** ; fraîcheur des deux artefacts dérivés vérifiée en CI |
| US-077 | Couche anticorruption vers le microservice IA | 5 | Moyenne | Port au type d'entrée neutre (instant, valeur) ; l'adaptateur ne compile plus contre Hibernate ; l'indisponibilité rend `Optional.empty()`, jamais une exception |
| US-078 | Sessions serveur persistées en base | 5 | Haute | Schéma créé par **Flyway** et non par Spring Session ; la session survit à un redémarrage du back-end ; exception à la convention multi-tenant motivée dans la migration |
