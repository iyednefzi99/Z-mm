# 🏃 SPRINT-03: Visites & rapports

**Thème:** Planification et suivi des visites  
**Objectif:** Workflow complet : planification → approbation → réalisation → rapport photo  
**Période:** 2026-08-25 → 2026-09-07 (14 jours)  
**Story Points:** 36 / Capacity: 40  

---

## 📅 Cérémonies Scrum

| Cérémonie | Date/Heure | Durée |
|:---|:---|:---|
| Sprint Planning | 2026-08-25 09:00-13:00 | 4h |
| Daily Scrum | Tous les jours 09:15 (15 min) | 15 min |
| Sprint Review | 2026-09-07 14:00-16:00 | 2h |
| Sprint Retrospective | 2026-09-07 16:00-17:00 | 1h |

---

## 📋 User Stories

| ID | Story | Points | Statut | Assigné |
|:---|:---|:---:|:---|:---|
| US-007 | Planifier une visite | 8 | 🟢 Livré (code + IT + UI) | - |
| US-008 | Approuver/Refuser un planning | 5 | 🟢 Livré (décision + motif) | - |
| US-009 | Réaliser une visite et remplir le rapport | 13 | 🟢 Livré (code + IT + UI) | - |
| US-010 | Ajouter des photos au rapport | 5 | 🟢 Livré (sous-ressource) | - |
| US-028 | Photos d'inspection | 5 | 🟢 Livré (avec US-010) | - |

---

## 🎯 Sprint Review - Démonstration

**Date:** 2026-09-07 14:00-16:00

Démonstration workflow visite avec rapport complet et photos d'inspection

---

## ⚠️ Risques Identifiés

Volumétrie des photos (stockage fichier + chemin en base, jamais de BLOB) ; prototyper Service Worker/IndexedDB en fin de sprint pour dérisquer S4

---

## 📊 Burndown Chart (à mettre à jour quotidiennement)

| Jour | Reste à faire (idéal) | Reste à faire (réel) | Notes |
|:---|:---:|:---:|:---|
| Jour 1 | 36 | 36 | Modèle planning/visite/photo (V6) |
| Jour 4 | 27 | 23 | US-007/008 planning + décision |
| Jour 7 | 18 | 10 | US-009 visite + rapport |
| Jour 10 | 9 | 5 | US-010/028 photos |
| Jour 14 | 0 | 0 | UI Plannings + Visites — sprint clos 36/36 |

---

## 📝 Rétrospective

**Résultat : les 5 user stories (36 SP) livrées et prouvées.** Backend :
11 tests unitaires + **39 d'intégration**, `Skipped: 0`. Frontend : build vert.
Le workflow planifier → approuver/refuser → réaliser + rapport → photos est
démontrable de bout en bout.

### Ce qui a bien fonctionné

- **Le workflow s'est modélisé proprement** sur la fondation existante : Planning
  et Visite réutilisent Ruche et Agent, avec les cardinalités et l'énumération
  d'états (proposé/approuvé/refusé) verrouillées en base (`CHECK`) et en service.
- **La décision du superviseur est un vrai garde-fou** : un refus sans motif est
  rejeté (400), le motif est conservé. Testé.
- **Les photos en sous-ressource** (`/api/visites/{id}/photos`) gardent l'API
  lisible ; l'appartenance d'une photo à sa visite est vérifiée avant suppression.
- **L'i18n a suivi sans friction** : les nouveaux libellés (raisons, statuts,
  effectifs, états sanitaires) sont couverts dans les trois langues, contrôlés à la
  compilation par `Record<Langue, typeof fr>`.

### Ce qui peut être amélioré

- **Pas d'upload binaire des photos** : on stocke une URL/référence, le stockage
  objet réel (S3/MinIO) reste à câbler. Volontairement hors périmètre de ce sprint.
- **Transitions d'état du planning non contraintes** : on peut ré-approuver un
  planning déjà refusé. Une machine à états stricte serait plus sûre.
- **Le rapport de visite est riche** : la saisie terrain gagnerait à être guidée
  (formulaire par étapes) — à revoir avec le mode hors-ligne du SPRINT-04.

### Actions pour le prochain sprint

1. **Mode hors-ligne** (US-011) : la saisie de visite est le cas d'usage terrain
   par excellence — prioriser la file de synchronisation.
2. **RBAC** (US-022) : seul un superviseur devrait approuver un planning ; à
   verrouiller par rôle.
3. **Nettoyer `ping`** — dette toujours en attente.
4. **Upload binaire des photos** (stockage objet) — quand une story l'exige.

> **Vélocité : 36 SP livrés** (capacité 40).

*Dernière mise à jour : 24/07/2026*
