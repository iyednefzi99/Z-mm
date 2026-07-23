# 🏃 SPRINT-01: Fondation — CRUD Core & configuration

**Thème:** Architecture, entités métier de base et paramétrage  
**Objectif:** Avoir un socle technique fonctionnel avec les entités principales et la configuration externalisée  
**Période:** 2026-07-28 → 2026-08-10 (14 jours)  
**Story Points:** 36 / Capacity: 40  

---

## 📅 Cérémonies Scrum

| Cérémonie | Date/Heure | Durée |
|:---|:---|:---|
| Sprint Planning | 2026-07-28 09:00-13:00 | 4h |
| Daily Scrum | Tous les jours 09:15 (15 min) | 15 min |
| Sprint Review | 2026-08-10 14:00-16:00 | 2h |
| Sprint Retrospective | 2026-08-10 16:00-17:00 | 1h |

---

## 📋 User Stories

| ID | Story | Points | Statut | Assigné |
|:---|:---|:---:|:---|:---|
| US-001 | CRUD Fermier | 5 | 🟢 Livré (code + IT) | - |
| US-002 | CRUD Ferme | 5 | 🟢 Livré (code + IT) | - |
| US-003 | CRUD Site (avec géolocalisation) | 8 | 🟢 Livré (code + IT, proximité PostGIS) | - |
| US-005 | CRUD Agent avec rôles | 5 | 🟢 Livré (code + IT) | - |
| US-006 | Contraintes de composition (règles métier) | 8 | 🟢 Livré (CHECK + validation + IT) | - |
| US-025 | Configuration ConfigZumm.ini | 5 | 🟢 Livré (lecture, hot-reload, endpoint) | - |

---

## 🎯 Sprint Review - Démonstration

**Date:** 2026-08-10 14:00-16:00

Démonstration CRUD Fermier/Ferme/Site/Agent + seuils lus depuis ConfigZumm.ini

> Support de démonstration livré : **PWA de gestion** (console CRUD React, aux
> jetons de la charte, installable et responsive — le « mobile » du cahier est la
> PWA, pas une app native). Détail dans `docs/JOURNAL.md`.

---

## ⚠️ Risques Identifiés

Complexité PostGIS, configuration Spring Boot / Docker — spike technique de 2–3 jours en ouverture de sprint

> ℹ️ **Avance prise avant l'ouverture** : la fondation technique (modèle de données,
> multi-tenant `tenant_id` + RLS, durcissement du rôle applicatif, lecture de
> `ConfigZumm.ini`) et le **CRUD Fermier/Ferme (US-001/002)** sont déjà livrés et
> testés (cf. `docs/SPRINT-01-FONDATION.md`, `docs/JOURNAL.md`). Le spike PostGIS
> est largement absorbé. Restent, pour clore US-001/002 : matrice RBAC par rôle
> (US-022) et pagination des listes si le volume l'exige.

---

## 📊 Burndown Chart (à mettre à jour quotidiennement)

| Jour | Reste à faire (idéal) | Reste à faire (réel) | Notes |
|:---|:---:|:---:|:---|
| Jour 1 | 36 | 36 | US-001/002 démarrés sur la fondation |
| Jour 4 | 27 | 26 | US-001/002 livrés (Fermier, Ferme) |
| Jour 7 | 18 | 13 | US-025 (config) + US-006 (contraintes) |
| Jour 10 | 9 | 5 | US-003 (Site + PostGIS) |
| Jour 14 | 0 | 0 | US-005 (Agent) — sprint clos, 36/36 SP |

---

## 📝 Rétrospective

**Résultat : les 6 user stories (36 SP) livrées et prouvées** — 11 tests unitaires
et 29 d'intégration contre un PostgreSQL réel, `Skipped: 0`. Le CRUD des quatre
entités (Fermier, Ferme, Site, Agent), la géolocalisation PostGIS, les contraintes
de composition et l'exposition des seuils de `ConfigZumm.ini` sont démontrables.

### Ce qui a bien fonctionné

- **La fondation a tenu ses promesses.** Le multi-tenant (`tenant_id` + RLS) posé en
  amont a rendu l'isolation *automatique* : les services CRUD ne contiennent aucun
  filtre `tenant_id`, et chaque IT vérifie qu'un tenant ne voit pas les données d'un
  autre à travers l'API. Le spike PostGIS redouté a été absorbé sans douleur.
- **« Prouver, pas déclarer », appliqué aux règles métier.** Les contraintes de
  composition (US-006) sont vérifiées à trois niveaux — Bean Validation (rejet
  précoce), service (règles croisant plusieurs champs, ex. ordre des dates), et
  `CHECK` en base (garde-fou ultime) — et chaque niveau a son test.
- **La géolocalisation exploite réellement PostGIS** : recherche de proximité par
  `ST_DWithin` + index GiST, filtrée explicitement par tenant (une requête native
  échappe au discriminant Hibernate, leçon déjà tirée du durcissement RLS).
- **Erreurs normalisées** (ProblemDetail / RFC 7807) : 404 / 400 / 409 cohérents sur
  toutes les entités, sans jamais exposer de trace technique.

### Ce qui peut être amélioré

- **Un défaut d'architecture latent a failli passer** : les contrôleurs mappaient
  les entités en DTO *hors transaction*, ce qui a levé une `LazyInitializationException`
  sur la première association `LAZY` réellement lue (la ferme d'un site en recherche
  de proximité). Corrigé en **construisant les DTO dans les services, dans la
  transaction** — la bonne frontière. À tenir pour toutes les entités à relations.
- **Pas encore de RBAC par rôle** : l'API est fermée par défaut (jeton requis), mais
  tout rôle authentifié peut tout faire. La matrice fine (US-022) reste à venir.
- **Pas de pagination** sur les listes : acceptable aux volumes actuels, à ajouter
  avant que le nombre de sites/agents ne croisse.

### Actions pour le prochain sprint

1. **Nettoyer `ping`** (entité, code et test du walking skeleton) maintenant que le
   modèle métier existe — dette explicitement reportée depuis la V1.
2. **Matrice RBAC par rôle** (US-022) : décliner qui peut créer / lire / modifier /
   supprimer quoi, au-delà du « fermé par défaut ».
3. **Valider `zumm_app` de bout en bout** en montant la pile `compose` complète (le
   rôle et son isolation RLS sont déjà prouvés unitairement).
4. **Pagination** des listes, dès qu'une story l'exige.
5. **Arrondi des positions** des sites pour les profils non propriétaires
   (`arrondi_degres_public`), donnée sensible — dépend de la matrice RBAC.

> **Vélocité : 36 SP livrés** (capacité 40). La fondation technique (hors vélocité
> produit, préparée en amont) a permis de tenir le périmètre sans déborder.

*Dernière mise à jour : 23/07/2026*
