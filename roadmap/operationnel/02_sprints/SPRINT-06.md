# 🏃 SPRINT-06: Synthèse, capteurs & API

**Thème:** ROI, ingestion de mesures et interopérabilité  
**Objectif:** Compléter les tableaux de bord par la synthèse ROI et ouvrir le système aux capteurs et aux tiers  
**Période:** 2026-10-06 → 2026-10-19 (14 jours)  
**Story Points:** 39 / Capacity: 40  

---

## 📅 Cérémonies Scrum

| Cérémonie | Date/Heure | Durée |
|:---|:---|:---|
| Sprint Planning | 2026-10-06 09:00-13:00 | 4h |
| Daily Scrum | Tous les jours 09:15 (15 min) | 15 min |
| Sprint Review | 2026-10-19 14:00-16:00 | 2h |
| Sprint Retrospective | 2026-10-19 16:00-17:00 | 1h |

---

## 📋 User Stories

| ID | Story | Points | Statut | Assigné |
|:---|:---|:---:|:---|:---|
| US-015 | Tableau de bord synthèse et ROI | 13 | 🟢 Livré (agrégats + ROI indicatif + cartes) | - |
| US-017 | Interface ingestion REST/MQTT | 8 | 🟢 Livré (REST ; MQTT = pont documenté) | - |
| US-018 | Seuils paramétrables et logique d'alerte | 8 | 🟢 Livré (hystérésis + index partiel unique) | - |
| US-026 | Service web REST getZummHoneyActualQuantity | 5 | 🟢 Livré (REST + contrat OpenAPI 3) | - |
| US-029 | Contexte météo local | 5 | 🟢 Livré (Open-Meteo + repli simulation) | - |

---

## 🎯 Sprint Review - Démonstration

**Date:** 2026-10-19 14:00-16:00

Démonstration synthèse ROI, ingestion REST/MQTT d'une mesure, appel REST depuis un client externe généré depuis OpenAPI

---

## ⚠️ Risques Identifiés

Anti-rebond/hystérésis des seuils, complétude du contrat OpenAPI

---

## 📊 Burndown Chart (à mettre à jour quotidiennement)

| Jour | Reste à faire (idéal) | Reste à faire (réel) | Notes |
|:---|:---:|:---:|:---|
| Jour 1 | 39 | 39 | Ingestion REST des mesures (US-017), migration V8 |
| Jour 4 | 29 | 25 | Seuils + hystérésis (US-018) |
| Jour 7 | 20 | 16 | Synthèse ROI (US-015) |
| Jour 10 | 10 | 7 | getZummHoneyActualQuantity + OpenAPI (US-026) |
| Jour 14 | 0 | 0 | Météo (US-029) + vues console — sprint clos 39/39 |

---

## 📝 Rétrospective

**Résultat : les 5 user stories (39 SP) livrées.** Backend : nouvel `IT`
`IngestionSyntheseServiceMeteoIT` (5 scénarios) vert, `Skipped: 0`. Contrat OpenAPI 3
publié (`/v3/api-docs`) via springdoc. Frontend : build vert, onglet Capteurs +
sous-onglet Synthèse, parité FR/EN/AR.

### Ce qui a bien fonctionné

- **L'ingestion (US-017) et les seuils (US-018) partagent un point d'entrée**
  `MesureService.ingerer(...)` : le canal REST (`POST /api/mesures`) et le pont MQTT
  appellent le même code, et chaque mesure est immédiatement confrontée aux seuils.
- **L'hystérésis (US-018) évite le battement** : une alerte ne s'ouvre qu'au
  franchissement, et ne se referme qu'après retour dans une bande de sécurité (5 %
  du seuil). L'unicité « une alerte ouverte par ruche × indicateur » est garantie
  **en base** par un index partiel unique, en plus de la logique applicative.
- **Le contrat OpenAPI 3 (US-026) est généré** depuis les contrôleurs (springdoc) :
  il débloque la génération du client TypeScript prévue au cahier. L'identifiant
  `getZummHoneyActualQuantity` est resté tel quel (les identifiants d'API ne se
  traduisent pas).
- **La météo (US-029) ne casse jamais une requête** : Open-Meteo est appelé avec un
  timeout court et un repli déterministe hors-ligne — validable en CI sans réseau
  (`zumm.meteo.mode=simulation`).
- **La synthèse ROI (US-015) boucle le cross-sprint** : l'IT ingère des poids puis
  vérifie que le tableau production (US-013) et la synthèse les reflètent.

### Ce qui peut être amélioré / limites assumées

- **MQTT = pont documenté, pas branché** : brancher un broker (Paho + broker de
  test) alourdirait la CI ; le canal REST couvre la démonstration, l'ingestion MQTT
  réutilisera `MesureService.ingerer`. À implémenter en conditions réelles.
- **ROI sur économie de référence indicative** : prix du miel et coût de visite sont
  des constantes documentées, à externaliser dans `ConfigZumm.ini` avec le module
  récolte (SPRINT-07). Le poids reste un *proxy* de la quantité de miel.
- **Pas d'anti-rebond temporel** : l'hystérésis est en valeur, pas en durée (« N
  mesures consécutives »). Suffisant ici, à affiner si les capteurs sont bruités.

### Actions pour le prochain sprint

1. **Récolte + QR code (US-033)** : remplacer le proxy « poids » par une quantité réelle.
2. **Détection d'anomalie EWMA (US-034)** au-dessus des seuils statiques.
3. **Carte des ruchers (US-030)** exploitant PostGIS.
4. **Externaliser les constantes économiques** du ROI.

> **Vélocité : 39 SP livrés** (capacité 40).

*Dernière mise à jour : 24/07/2026*
