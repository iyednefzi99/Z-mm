# 🏃 SPRINT-10: Intelligence spatiale & remise à niveau

**Thème:** Exploitation de la base spatiale PostGIS et rattrapage de la dette documentaire et de test
**Objectif:** Transformer la carte descriptive en outil d'aide à la décision terrain, et remettre la roadmap publiée en phase avec le produit réellement livré
**Période:** 2026-12-01 → 2026-12-14 (14 jours)
**Story Points:** 34 / Capacity: 40

---

## 📅 Cérémonies Scrum

| Cérémonie | Date/Heure | Durée |
|:---|:---|:---|
| Sprint Planning | 2026-12-01 09:00-13:00 | 4h |
| Daily Scrum | Tous les jours 09:15 (15 min) | 15 min |
| Sprint Review | 2026-12-14 14:00-16:00 | 2h |
| Sprint Retrospective | 2026-12-14 16:00-17:00 | 1h |

---

## 📋 User Stories

| ID | Story | Points | Statut | Assigné |
|:---|:---|:---:|:---|:---|
| US-045 | Regroupement spatial des sites (clustering) | 8 | 📋 À faire | - |
| US-046 | Distances inter-sites et plus proches voisins | 5 | 📋 À faire | - |
| US-047 | Ordre de tournée optimisé pour les visites planifiées | 8 | 📋 À faire | - |
| US-048 | Alignement du backlog produit et de la roadmap | 5 | 📋 À faire | - |
| US-049 | Socle de tests et de linting du front-end | 8 | 📋 À faire | - |

**Répartition :** 21 SP de valeur métier (US-045 → US-047, épic 008) + 13 SP de dette
et de conformité documentaire (US-048, US-049, épic 010).

---

## 🎯 Détail des user stories

### US-045 — Regroupement spatial des sites (8 pts)

> **En tant qu'**apiculteur gérant plusieurs dizaines de sites,
> **je veux** voir mes sites regroupés par proximité géographique,
> **afin de** raisonner par zone d'exploitation plutôt que site par site.

**Critères d'acceptation**

- `GET /api/sites/grappes?distanceMetres=…&minimumSites=…` renvoie, pour le tenant
  courant, la liste des grappes : identifiant, centroïde, nombre de sites, nombre de
  ruches cumulé, liste des sites membres.
- Le regroupement est calculé **en base** par `ST_ClusterDBSCAN` sur la colonne
  générée `site.geog` (`V2__modele_metier_sprint01.sql:107`) — pas en Java, pas
  dans le navigateur.
- L'index GiST existant `ix_site_geog` est utilisé (vérifié par `EXPLAIN`).
- Les sites isolés (bruit DBSCAN) sont retournés comme grappes singleton, jamais omis.
- L'isolation RLS s'applique : un tenant ne voit jamais un site d'un autre tenant
  dans une grappe (test d'intégration dédié).
- `CarteVue.tsx` affiche une pastille de grappe avec le compte de sites au-dessus
  d'un seuil de zoom, et la déplie en marqueurs individuels en-dessous.

### US-046 — Distances inter-sites et plus proches voisins (5 pts)

> **En tant que** responsable planifiant une campagne de visites,
> **je veux** connaître les sites les plus proches d'un site donné et leur distance
> réelle, **afin de** grouper les déplacements.

**Critères d'acceptation**

- `GET /api/sites/{id}/voisins?limite=…` renvoie les *n* sites les plus proches,
  triés par distance croissante, avec la distance en mètres.
- La requête utilise l'opérateur KNN `<->` sur `geog` (parcours d'index, pas de
  produit cartésien) ; test de non-régression sur un jeu de 200 sites.
- La distance est une distance géodésique (`ST_Distance` sur `geography`), pas une
  distance euclidienne en degrés.
- L'endpoint réutilise le contrôle d'accès de `GET /api/sites/proches` déjà en place
  (`SiteController.java:52`).
- Le détail d'un site affiche ses trois voisins les plus proches, cliquables.

### US-047 — Ordre de tournée optimisé pour les visites planifiées (8 pts)

> **En tant qu'**agent partant en tournée,
> **je veux** que mes visites du jour soient ordonnées par un itinéraire court,
> **afin de** réduire le temps et le kilométrage de déplacement.

**Critères d'acceptation**

- `GET /api/plannings/tournee?agentId=…&date=…` renvoie les visites planifiées de
  l'agent pour la date, **ordonnées**, avec la distance cumulée estimée.
- L'ordre est calculé par heuristique du plus proche voisin depuis un point de
  départ paramétrable, puis amélioré par 2-opt ; l'optimalité n'est pas garantie et
  c'est documenté dans l'OpenAPI (problème NP-difficile, pas de pgRouting).
- Distances à vol d'oiseau via `geog` — le routage sur réseau routier reste hors
  périmètre et est consigné comme tel.
- Une tournée de 0 ou 1 visite renvoie le résultat trivial sans erreur.
- `PlanningsVue.tsx` affiche l'ordre proposé et le tracé sur la carte ; l'agent peut
  ignorer l'ordre proposé (aucune contrainte imposée).
- Test unitaire de l'heuristique sur un cas dont l'optimum est connu.

### US-048 — Alignement du backlog produit et de la roadmap (5 pts)

> **En tant que** Product Owner,
> **je veux** que la roadmap publiée décrive le produit réellement livré,
> **afin que** le suivi d'avancement soit exploitable en revue.

**Constat** : US-041 à US-044 (26 SP, livrées au SPRINT-09) n'existent que dans
`SPRINT-09.md`. Elles sont absentes de `product_backlog.json`, `product_backlog.md`,
`sprints.json` (arrêté à 8 sprints / 304 SP) et des chapitres LaTeX
`roadmap/chapitres/02-backlog.tex` et `03-sprints.tex`. La roadmap publiée
sous-déclare un sprint entier.

**Critères d'acceptation**

- US-041 → US-044 intégrées au product backlog (JSON **et** Markdown), rattachées à
  un épic « Exploitation et restitution » ; total recalculé (304 → 330 SP, puis
  → 364 SP avec le présent sprint).
- US-045 → US-049 ajoutées au product backlog.
- `sprints.json` contient SPRINT-09 et SPRINT-10 (total_sprints, total_points à jour).
- `02-backlog.tex` et `03-sprints.tex` régénérés en cohérence, roadmap recompilée
  (2 passes `pdflatex`) et PDF committé.
- Le périmètre livré du cahier des charges reste cohérent avec le backlog
  (§ « Perspectives d'évolution » du chapitre 3).

### US-049 — Socle de tests et de linting du front-end (8 pts)

> **En tant que** développeur,
> **je veux** un filet de sécurité automatisé sur le front,
> **afin de** ne pas régresser à chaque évolution d'interface.

**Constat** : `frontend/package.json` ne déclare aujourd'hui aucun outil de test ni
de lint, et le job « Frontend — build » de `.github/workflows/ci.yml` se limite à
`npm run typecheck` puis `npm run build`. Le back-end est couvert par 36 tests
unitaires et 55 d'intégration ; le front ne l'est par aucun.

**Critères d'acceptation**

- Vitest + Testing Library installés, script `npm test` opérationnel.
- Au moins 12 tests couvrant : la file de mutations hors-ligne (`offline/file.ts`,
  rejeu et idempotence), le client API (`api/client.ts`, gestion des erreurs), la
  bascule de langue et le sens RTL (`i18n/langue.tsx`), et deux vues de formulaire.
- ESLint + Prettier configurés, `npm run lint` sans erreur sur l'existant.
- Le job « Frontend — build » de `.github/workflows/ci.yml` exécute `npm run lint`
  et `npm test` en plus du typecheck et du build ; un test rouge fait échouer la CI.
- La dette « tests frontend : aucun » est retirée de la documentation de suivi.

---

## 🎯 Sprint Review - Démonstration

**Date:** 2026-12-14 14:00-16:00

Sur données de démonstration : carte affichant les grappes de sites puis leur
dépliage au zoom, consultation des voisins les plus proches d'un site, génération
d'une tournée ordonnée pour un agent sur une journée chargée. Puis passage sur la
roadmap recompilée montrant les 10 sprints et les 49 user stories, et exécution de
la CI front (lint + tests) sur un commit volontairement fautif.

---

## ⚠️ Risques Identifiés

| Risque | Impact | Mitigation |
|:---|:---|:---|
| Optimisation de tournée assimilée à un routage routier réel | Attente client déçue en review | Cadrer dès le planning : distances à vol d'oiseau, heuristique non optimale, pgRouting hors périmètre |
| Rendu des grappes sur la carte SVG autonome (US-030 dégradée au SPRINT-07, MapLibre non intégré) | US-045 partiellement démontrable | Le calcul reste serveur et testable indépendamment de l'affichage ; le rendu est le seul élément à risque |
| Volumétrie de démonstration trop faible pour que le clustering soit parlant | Démonstration peu convaincante | Jeu de données de démonstration élargi à ~200 sites avant la review |
| US-048 touche des fichiers LaTeX vérifiés par la CI documentaire | Build rouge en fin de sprint | Recompiler la roadmap et rejouer `check-sync.sh` dès la première modification, pas la veille de la review |
| Introduction d'ESLint sur une base existante non lintée | Volume de corrections imprévu | Démarrer sur une configuration recommandée, traiter les règles bruyantes en avertissement, pas en erreur |

---

## 📊 Burndown Chart (prévisionnel)

| Jour | Reste à faire (idéal) | Reste à faire (réel) | Notes |
|:---|:---:|:---:|:---|
| Jour 1 | 34 | - | Clustering serveur (US-045) |
| Jour 4 | 26 | - | Grappes affichées sur la carte + voisins (US-046) |
| Jour 7 | 18 | - | Heuristique de tournée (US-047) |
| Jour 10 | 10 | - | Alignement roadmap et backlog (US-048) |
| Jour 12 | 5 | - | Socle de tests front (US-049) |
| Jour 14 | 0 | - | CI front branchée, roadmap recompilée |

---

## 📝 Rétrospective

*À compléter en fin de sprint (2026-12-14).*

---

## 🔭 Après ce sprint

Restent au chapitre « Perspectives d'évolution » du cahier des charges :
connecteur IoT durci (`/api/v1/telemetry` + token *device*), détection acoustique et
vision par ordinateur (ML), jumeau numérique de colonie et crédits carbone, et
l'application mobile native — cette dernière n'étant à engager que sur exigence
explicite du client.

*Dernière mise à jour : 25/07/2026*
