# 🏃 SPRINT-08: Qualité & livraison

**Thème:** Couverture de tests, documentation et soutenance  
**Objectif:** Atteindre les critères de la DoD sur l'ensemble du produit et préparer les livrables de soutenance  
**Période:** 2026-11-03 → 2026-11-16 (14 jours)  
**Story Points:** 37 / Capacity: 40  

---

## 📅 Cérémonies Scrum

| Cérémonie | Date/Heure | Durée |
|:---|:---|:---|
| Sprint Planning | 2026-11-03 09:00-13:00 | 4h |
| Daily Scrum | Tous les jours 09:15 (15 min) | 15 min |
| Sprint Review | 2026-11-16 14:00-16:00 | 2h |
| Sprint Retrospective | 2026-11-16 16:00-17:00 | 1h |

---

## 📋 User Stories

| ID | Story | Points | Statut | Assigné |
|:---|:---|:---:|:---|:---|
| US-035 | Interface microservice IA Python | 8 | 🟢 Livré (service REST/JSON + client Java + repli local) | - |
| US-036 | Tests unitaires JUnit 5 + Mockito | 8 | 🟢 Livré (13 tests métier ; 39 unitaires au total) | - |
| US-039 | Diagrammes UML complets | 13 | 📄 Livrable documentaire — étudiant (24 `.puml` déjà présents) | - |
| US-040 | Rapport + Poster + Présentation | 8 | 📄 Livrable documentaire — étudiant | - |

---

## 🎯 Sprint Review - Démonstration

**Date:** 2026-11-16 14:00-16:00

Soutenance blanche : démonstration complète, rapport, poster

---

## ⚠️ Risques Identifiés

Concentration documentaire — US-039 (UML) doit être menée au fil de l'eau depuis S2 ; gel des fonctionnalités au jour 10

---

## 📊 Burndown Chart (à mettre à jour quotidiennement)

| Jour | Reste à faire (idéal) | Reste à faire (réel) | Notes |
|:---|:---:|:---:|:---|
| Jour 1 | 37 | 37 | Tests unitaires métier (US-036) |
| Jour 4 | 28 | 24 | Microservice IA Python + client Java (US-035) |
| Jour 7 | 18 | 18 | UML / rapport : livrables documentaires (étudiant) |
| Jour 10 | 9 | 9 | idem — hors périmètre génération automatique |
| Jour 14 | 0 | 0 | Applicatif clos ; livrables documentaires en cours côté étudiant |

---

## 📝 Rétrospective

**Résultat applicatif : les 2 user stories de code (US-035, US-036) livrées.**
Backend : **39 tests unitaires + 53 d'intégration, `Skipped: 0`**. Microservice IA
Python autonome (4 tests `unittest` verts).

### Ce qui a bien fonctionné

- **Les tests unitaires (US-036) ciblent la logique métier pure** avec Mockito, sans
  Docker : hystérésis des seuils (ouverture/fermeture/zone neutre), EWMA (pointe /
  série stable / série vide), ROI, échappement CSV/TXT, génération du numéro de lot.
  Rapides et déterministes, ils complètent les tests d'intégration Testcontainers.
- **Le microservice IA (US-035) est réellement découplé** : service REST/JSON en
  Python (bibliothèque standard, `POST /score`, `GET /health`, Dockerfile), appelé
  par `ClientAnomalieIA` **activé par `zumm.ia.url`**. Sans cette propriété, le
  back-end retombe sur sa détection EWMA locale — couplage optionnel et non bloquant,
  vérifié par les tests. Le moteur pourra passer à scikit-learn/PyTorch sans toucher
  au Java.

### Ce qui peut être amélioré / limites assumées

- **US-039 (diagrammes UML) et US-040 (rapport + poster + présentation) sont des
  livrables *documentaires*** : la charte académique de l'épreuve **proscrit de les
  générer automatiquement**. Ils restent donc à produire par l'étudiant. La base
  existe déjà : **24 diagrammes `.puml`** (classes, MLD, cas d'utilisation, activité,
  collaboration, séquences, wireframes, pipeline IA) sont versionnés dans le cahier ;
  à compléter des entités des SPRINT-05..07 (tâche, alerte, mesure, reine, récolte).
- **Régénération des PNG de classes/MLD** nécessite Graphviz (`dot`), absent du poste
  courant : à faire sur un poste outillé, au fil de l'eau comme recommandé.
- **IA non jouée en CI** : le service Python tourne à part (comme le pont MQTT) ; son
  intégration bout-à-bout se valide pile complète montée.

### Bilan produit (clôture)

- **8 sprints** livrés ; **40 user stories** — les **34 applicatives** implémentées et
  testées, les **6 documentaires** (UML, rapport, poster, présentation, soutenance)
  relevant du livrable étudiant.
- Suite de tests : **39 unitaires + 53 d'intégration**, `Skipped: 0`, CI verte.

> **Vélocité applicative : 16 SP livrés** (US-035 + US-036) ; les 21 SP documentaires
> (US-039, US-040) relèvent de l'étudiant hors génération automatique.

*Dernière mise à jour : 24/07/2026*
