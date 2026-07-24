# 🏃 SPRINT-05: Tableaux de bord & pilotage

**Thème:** Calendrier, production, alertes sanitaires  
**Objectif:** Donner à l'apiculteur ses trois vues de pilotage quotidien  
**Période:** 2026-09-22 → 2026-10-05 (14 jours)  
**Story Points:** 39 / Capacity: 40  

---

## 📅 Cérémonies Scrum

| Cérémonie | Date/Heure | Durée |
|:---|:---|:---|
| Sprint Planning | 2026-09-22 09:00-13:00 | 4h |
| Daily Scrum | Tous les jours 09:15 (15 min) | 15 min |
| Sprint Review | 2026-10-05 14:00-16:00 | 2h |
| Sprint Retrospective | 2026-10-05 16:00-17:00 | 1h |

---

## 📋 User Stories

| ID | Story | Points | Statut | Assigné |
|:---|:---|:---:|:---|:---|
| US-012 | Calendrier matrice agents × ruches | 13 | 🟢 Livré (endpoint période + matrice + vue) | - |
| US-013 | Tableau de bord production | 8 | 🟢 Livré (poids/ruche + seuil + productivité) | - |
| US-014 | Tableau de bord alertes sanitaires | 8 | 🟢 Livré (état sanitaire + délai sans visite) | - |
| US-031 | Liste de tâches et rappels | 5 | 🟢 Livré (CRUD + rappels échus) | - |
| US-027 | Export CSV/TXT | 5 | 🟢 Livré (visites/ruches, CSV + TXT) | - |

---

## 🎯 Sprint Review - Démonstration

**Date:** 2026-10-05 14:00-16:00

Démonstration calendrier matriciel, tableaux production et alertes, export CSV

---

## ⚠️ Risques Identifiés

Performance des agrégations SQL, rendu Chart.js — scinder US-012 (matrice / filtres) si le burndown décroche

---

## 📊 Burndown Chart (à mettre à jour quotidiennement)

| Jour | Reste à faire (idéal) | Reste à faire (réel) | Notes |
|:---|:---:|:---:|:---|
| Jour 1 | 39 | 39 | Tâches/rappels (US-031), migration V7 |
| Jour 4 | 29 | 26 | Export CSV/TXT (US-027) + calendrier matriciel (US-012) |
| Jour 7 | 20 | 18 | Tableau de bord production (US-013) |
| Jour 10 | 10 | 8 | Alertes sanitaires (US-014) |
| Jour 14 | 0 | 0 | Vues console + IT vert — sprint clos 39/39 |

---

## 📝 Rétrospective

**Résultat : les 5 user stories (39 SP) livrées.** Backend : nouvel `IT`
`TableauxTachesExportIT` (4 scénarios) vert, `Skipped: 0`. Frontend : build vert,
deux onglets ajoutés (Tâches, Tableaux de bord) avec parité FR/EN/AR.

### Ce qui a bien fonctionné

- **Les trois tableaux de bord (US-012/013/014) partagent un même service**
  `TableauDeBordService` : agrégations en mémoire à partir de lectures filtrées par
  le tenant (`@TenantId` + RLS), donc sans requête native fragile. Le calendrier
  regroupe les visites par couple (agent, ruche) sur une période ; la production
  agrège le poids par ruche et lève un drapeau `sousSeuil` contre
  `ConfigZumm.ini` ; les alertes hiérarchisent les ruches (critique → attention → ok).
- **Les tâches (US-031) réutilisent le socle multi-tenant** (migration V7, RLS,
  clés étrangères composites). L'endpoint `GET /api/taches/rappels` renvoie les
  tâches échues non faites : un simple *derived query* Spring Data, testé.
- **L'export (US-027) échappe proprement** : CSV conforme RFC 4180 (guillemets
  doublés) et TXT tabulé, avec `Content-Disposition` de téléchargement. Côté PWA, le
  téléchargement passe par `fetch` + blob pour porter le jeton JWT.

### Ce qui peut être amélioré / limites assumées

- **Agrégations en mémoire** : acceptables tant que le volume de mesures reste
  modeste (capteurs encore en préparation, EPIC-004). Dès l'afflux réel, basculer
  sur des *continuous aggregates* TimescaleDB (poids courant/min/max par ruche).
- **Production sans mesures ce sprint** : faute d'ingestion (US-017, SPRINT-06), le
  tableau production affiche des poids nuls ; la validation « poids réel » est
  reportée à l'IT du SPRINT-06 (ingestion → production).
- **Rendu Chart.js non intégré** : les tableaux de bord sont tabulaires ; la
  visualisation graphique (Chart.js, prévue au cahier) viendra avec la synthèse ROI
  (US-015, SPRINT-06).

### Actions pour le prochain sprint

1. **Ingestion des mesures (US-017)** puis vérifier le tableau production de bout en bout.
2. **Synthèse ROI (US-015)** avec graphiques.
3. **Seuils et hystérésis (US-018)** pour fiabiliser les alertes.
4. **Nettoyer `ping`** — dette persistante.

> **Vélocité : 39 SP livrés** (capacité 40).

*Dernière mise à jour : 24/07/2026*
