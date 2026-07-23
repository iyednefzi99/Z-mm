# 🏃 SPRINT-02: Ruche, i18n & socle qualité

**Thème:** Composition des ruches, internationalisation et harnais de test  
**Objectif:** Modéliser la hiérarchie ruche/corps/hausse/cadre et outiller la qualité dès le début  
**Période:** 2026-08-11 → 2026-08-24 (14 jours)  
**Story Points:** 39 / Capacity: 40  

---

## 📅 Cérémonies Scrum

| Cérémonie | Date/Heure | Durée |
|:---|:---|:---|
| Sprint Planning | 2026-08-11 09:00-13:00 | 4h |
| Daily Scrum | Tous les jours 09:15 (15 min) | 15 min |
| Sprint Review | 2026-08-24 14:00-16:00 | 2h |
| Sprint Retrospective | 2026-08-24 16:00-17:00 | 1h |

---

## 📋 User Stories

| ID | Story | Points | Statut | Assigné |
|:---|:---|:---:|:---|:---|
| US-004 | CRUD Ruche avec composition | 13 | 🟢 Livré (code + IT + UI) | - |
| US-024 | Internationalisation (FR/EN/AR) | 8 | 🟢 Livré (console 3 langues + RTL) | - |
| US-016 | Modèle de données Mesure | 8 | 🟢 Livré (hypertable + IT) | - |
| US-023 | Chiffrement TLS 1.3 / X.509 | 5 | 🟢 Livré (nginx TLSv1.3) | - |
| US-037 | Tests d'intégration (Testcontainers) | 5 | 🟢 Livré (harnais, 36 IT) | - |

---

## 🎯 Sprint Review - Démonstration

**Date:** 2026-08-24 14:00-16:00

Démonstration composition ruche avec règles métier, bascule FR/EN/AR (RTL) et HTTPS actif

---

## ⚠️ Risques Identifiés

Contraintes SQL CHECK, pattern Composite, rétrofit RTL — tester le rendu arabe dès la première maquette

---

## 📊 Burndown Chart (à mettre à jour quotidiennement)

| Jour | Reste à faire (idéal) | Reste à faire (réel) | Notes |
|:---|:---:|:---:|:---|
| Jour 1 | 39 | 39 | Modèle Ruche/Compartiment (V4) |
| Jour 4 | 29 | 26 | US-004 CRUD + composition, US-037 harnais |
| Jour 7 | 20 | 18 | US-016 Mesure (hypertable V5) |
| Jour 10 | 10 | 8 | US-023 TLS 1.3 |
| Jour 14 | 0 | 0 | US-024 i18n FR/EN/AR + RTL — sprint clos 39/39 |

---

## 📝 Rétrospective

**Résultat : les 5 user stories (39 SP) livrées et prouvées.** Backend :
11 tests unitaires + 36 d'intégration contre un PostgreSQL réel, `Skipped: 0`.
Frontend : build `tsc` strict + `vite` vert. Composition ruche avec règles métier,
bascule FR/EN/AR (RTL) et TLS 1.3 démontrables.

### Ce qui a bien fonctionné

- **Le pattern Composite s'est modélisé sans friction** sur la fondation
  multi-tenant : Ruche → Compartiments, avec les règles de l'annexe A (un corps
  obligatoire, ≤ 5 hausses, 1–10 cadres) verrouillées à trois niveaux — Bean
  Validation, service (cardinalités inter-lignes), et base (CHECK + index unique
  partiel garantissant « au plus un corps »). Chaque règle a son test.
- **TimescaleDB a tenu ses promesses sur le modèle Mesure** : hypertable avec clé
  naturelle incluant `instant`, RLS et `tenant_id` comme les autres tables ; un IT
  vérifie que `mesure` est bien une hypertable.
- **L'i18n RTL a été traitée par structure, pas par rustine** : `Record<Langue,
  typeof fr>` force EN et AR à couvrir toute l'arborescence de libellés (une clé
  manquante casse la compilation), et la direction du document bascule avec la
  langue. Le risque « rétrofit RTL » identifié en ouverture ne s'est pas matérialisé.
- **Le harnais Testcontainers (US-037)**, déjà en place depuis le Sprint 0, a
  absorbé les nouvelles entités sans effort : les tests d'isolation inter-tenant
  couvrent désormais ruches et mesures.

### Ce qui peut être amélioré

- **La console reste FR-only côté saisie de certains messages d'erreur serveur** :
  les `ProblemDetail` renvoyés par l'API ne sont pas encore internationalisés (le
  backend a pourtant `messages_*.properties`). À câbler.
- **Pas d'ingestion de mesures** : US-016 pose le modèle, mais l'alimentation
  (REST/MQTT) et les seuils/alertes sont EPIC-004 — bien séparer pour ne pas
  déborder.
- **TLS auto-signé en dev** : le certificat Let's Encrypt réel dépend d'un domaine
  et d'un hébergeur (hors périmètre technique, cf. SPRINT-00).

### Actions pour le prochain sprint

1. **Nettoyer `ping`** (dette du walking skeleton) — toujours en attente.
2. **Internationaliser les messages d'erreur API** (relier `ProblemDetail` aux
   `messages_*.properties`).
3. **Matrice RBAC par rôle** (US-022) — la sécurité reste « fermé par défaut ».
4. **Flux OIDC Keycloak** dans la PWA (remplacer l'écran de session de dev).
5. Préparer l'**ingestion de mesures** (EPIC-004) sur le modèle posé ici.

> **Vélocité : 39 SP livrés** (capacité 40). La fondation des sprints précédents
> continue de payer : le sprint a tenu son périmètre complet.

*Dernière mise à jour : 24/07/2026*
