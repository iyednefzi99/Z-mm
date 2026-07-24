# 🏃 SPRINT-07: Fonctions avancées & détection d'anomalie

**Thème:** Cartographie, traçabilité et détection adaptative  
**Objectif:** Livrer les fonctions différenciantes et la détection d'anomalie sur les mesures  
**Période:** 2026-10-20 → 2026-11-02 (14 jours)  
**Story Points:** 39 / Capacity: 40  

---

## 📅 Cérémonies Scrum

| Cérémonie | Date/Heure | Durée |
|:---|:---|:---|
| Sprint Planning | 2026-10-20 09:00-13:00 | 4h |
| Daily Scrum | Tous les jours 09:15 (15 min) | 15 min |
| Sprint Review | 2026-11-02 14:00-16:00 | 2h |
| Sprint Retrospective | 2026-11-02 16:00-17:00 | 1h |

---

## 📋 User Stories

| ID | Story | Points | Statut | Assigné |
|:---|:---|:---:|:---|:---|
| US-034 | Détection d'anomalie adaptative (EWMA) | 13 | 🟢 Livré (ligne de base + z-score) | - |
| US-030 | Carte et rayons de butinage | 8 | 🟢 Livré (carte SVG + cercles 1/2/3 km) | - |
| US-032 | Suivi de la reine | 5 | 🟢 Livré (journal d'événements par ruche) | - |
| US-033 | Récolte et QR code | 8 | 🟢 Livré (lot + QR + traçabilité) | - |
| US-038 | Tests de charge k6 | 5 | 🟢 Livré (script + seuils p95/erreurs) | - |

---

## 🎯 Sprint Review - Démonstration

**Date:** 2026-11-02 14:00-16:00

Démonstration carte et rayons de butinage, suivi de reine, QR code de lot, alerte EWMA

---

## ⚠️ Risques Identifiés

Calibrage de la ligne de base EWMA sans historique réel — prévoir un jeu de données simulé

---

## 📊 Burndown Chart (à mettre à jour quotidiennement)

| Jour | Reste à faire (idéal) | Reste à faire (réel) | Notes |
|:---|:---:|:---:|:---|
| Jour 1 | 39 | 39 | Suivi de la reine (US-032), migration V9 |
| Jour 4 | 29 | 26 | Récolte + lot + QR + traçabilité (US-033), migration V10 |
| Jour 7 | 20 | 15 | Détection d'anomalie EWMA (US-034) |
| Jour 10 | 10 | 7 | Carte et rayons de butinage (US-030) |
| Jour 14 | 0 | 0 | Tests de charge k6 (US-038) + vues — sprint clos 39/39 |

---

## 📝 Rétrospective

**Résultat : les 5 user stories (39 SP) livrées.** Backend : nouvel `IT`
`ReineRecolteAnomalieIT` (3 scénarios) vert, `Skipped: 0` (suite complète : 14
unitaires + 53 d'intégration). Frontend : build vert, onglets Reines, Récoltes et
Carte, panneau d'anomalie dans Capteurs, parité FR/EN/AR.

### Ce qui a bien fonctionné

- **La détection d'anomalie EWMA (US-034) est adaptative et sans dépendance** :
  ligne de base et variance EWMA incrémentales (formule de Finch), z-score comparé
  à un seuil. La ligne de base absorbe la dérive lente (saison, croissance de la
  colonie) tout en signalant les ruptures — l'IT prouve qu'une pointe de poids est
  repérée dans une série bruitée.
- **La traçabilité (US-033) part d'un lot unique** (`ZUMM-<ruche>-<jour>-<seq>`,
  unique par tenant en base) ; la réponse porte le `qrPayload` encodé côté PWA
  (bibliothèque `qrcode`), et `GET /api/recoltes/tracabilite/{lot}` remonte l'origine
  complète (ruche, site, ferme) — la fiche scannée depuis le pot.
- **Le suivi de la reine (US-032) est un journal d'événements** (introduction,
  ponte, remplacement, disparition, essaimage) avec couleur de marquage au code
  international, exposé hors de `/api/ruches` pour rester ouvert à l'apiculteur.
- **Un convertisseur de paramètre** (`?type=poids` → `TypeIndicateur`) a été ajouté :
  le `@JsonCreator` ne couvrait que les corps JSON, pas les paramètres de requête.
- **Les tests de charge k6 (US-038)** encodent les objectifs du cahier en *thresholds*
  (p95 < 500 ms, erreurs < 1 %) : le script échoue si un seuil est dépassé, prêt pour
  une étape CI non bloquante sur staging.

### Ce qui peut être amélioré / limites assumées

- **Carte SVG autonome plutôt que MapLibre** (US-030) : le rendu (sites + cercles de
  butinage 1/2/3 km) est sans tuiles externes, robuste hors-ligne et en CI.
  L'intégration **MapLibre GL + OpenStreetMap** (fond cartographique réel) reste
  l'évolution prévue au cahier — le calcul des rayons est déjà en place.
- **EWMA statistique, pas ML** : suffisant et explicable ; le microservice IA Python
  (US-035) prendra le relais pour des modèles plus riches (acoustique, vision).
- **k6 non joué en CI ici** : le script est livré et documenté ; son exécution exige
  la pile déployée (staging), hors du `mvn verify`.

### Actions pour le prochain sprint (SPRINT-08 / clôture)

1. **MapLibre GL** en remplacement de la carte SVG.
2. **Microservice IA Python (US-035)** derrière l'API d'anomalie.
3. **Externaliser** seuils économiques (ROI) et paramètres EWMA dans `ConfigZumm.ini`.
4. **Nettoyer `ping`** — dette persistante depuis le walking skeleton.

> **Vélocité : 39 SP livrés** (capacité 40).

*Dernière mise à jour : 24/07/2026*
