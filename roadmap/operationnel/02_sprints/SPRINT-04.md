# 🏃 SPRINT-04: Hors-ligne, authentification & RBAC

**Thème:** Saisie terrain déconnectée et contrôle d'accès  
**Objectif:** Permettre la saisie terrain sans réseau et verrouiller les accès par rôle  
**Période:** 2026-09-08 → 2026-09-21 (14 jours)  
**Story Points:** 39 / Capacity: 40  

---

## 📅 Cérémonies Scrum

| Cérémonie | Date/Heure | Durée |
|:---|:---|:---|
| Sprint Planning | 2026-09-08 09:00-13:00 | 4h |
| Daily Scrum | Tous les jours 09:15 (15 min) | 15 min |
| Sprint Review | 2026-09-21 14:00-16:00 | 2h |
| Sprint Retrospective | 2026-09-21 16:00-17:00 | 1h |

---

## 📋 User Stories

| ID | Story | Points | Statut | Assigné |
|:---|:---|:---:|:---|:---|
| US-011 | Mode hors-ligne et synchronisation différée | 13 | 🟢 Livré (file + rejeu + indicateur) | - |
| US-020 | Authentification OIDC (Keycloak, fédération Google) | 8 | 🟢 Livré (flux PKCE ; Google = config realm) | - |
| US-021 | Authentification locale (fallback) | 5 | 🟢 Livré (comptes locaux Keycloak) | - |
| US-022 | Contrôle d'accès RBAC | 8 | 🟢 Livré (matrice + IT) | - |
| US-019 | Conversion d'unités hétérogènes | 5 | 🟢 Livré (service + endpoint + test) | - |

---

## 🎯 Sprint Review - Démonstration

**Date:** 2026-09-21 14:00-16:00

Démonstration PWA hors-ligne, OAuth Google + repli local, matrice RBAC appliquée

---

## ⚠️ Risques Identifiés

Service Workers, IndexedDB, synchronisation différée et résolution de conflits

---

## 📊 Burndown Chart (à mettre à jour quotidiennement)

| Jour | Reste à faire (idéal) | Reste à faire (réel) | Notes |
|:---|:---:|:---:|:---|
| Jour 1 | 39 | 39 | Matrice RBAC (US-022) |
| Jour 4 | 29 | 26 | RBAC + IT, conversion d'unités (US-019) |
| Jour 7 | 20 | 18 | OIDC PKCE (US-020/021) |
| Jour 10 | 10 | 8 | File hors-ligne + rejeu (US-011) |
| Jour 14 | 0 | 0 | Indicateur de synchro — sprint clos 39/39 |

---

## 📝 Rétrospective

**Résultat : les 5 user stories (39 SP) livrées.** Backend : 14 tests unitaires +
41 d'intégration, `Skipped: 0` (RBAC prouvé par `RbacIT`, conversion par un test
unitaire). Frontend : build vert (OIDC PKCE, file hors-ligne, indicateur de synchro).

### Ce qui a bien fonctionné

- **La matrice RBAC (US-022) est centralisée** dans la configuration de sécurité
  (par méthode + chemin), pas dispersée en annotations : lisible et testée.
  L'approbation d'un planning est réservée au superviseur, l'écriture du référentiel
  au responsable/administrateur — conforme aux rôles du cahier.
- **La conversion d'unités (US-019)** passe toujours par une unité de référence par
  famille (masse → kg, température → Celsius) : ajouter une unité est trivial, et les
  incompatibilités sont refusées proprement.
- **L'OIDC PKCE (US-020/021) est écrit sans dépendance** : le flux « authorization
  code + PKCE » d'un client public, avec repli « coller un jeton » en développement.
  La page de connexion Keycloak sert à la fois les comptes locaux (US-021) et la
  fédération Google (US-020).
- **La file hors-ligne (US-011)** capture les mutations en panne réseau, les persiste
  et les rejoue au retour du réseau, avec un indicateur visible.

### Ce qui peut être amélioré / limites assumées

- **Fédération Google (US-020) = configuration Keycloak**, pas du code : elle exige
  un *Identity Provider* Google (client id/secret) dans le realm. Non vérifiable
  hors d'un environnement réel ; le flux applicatif, lui, est prêt.
- **Synchro hors-ligne sans résolution de conflits ni idempotence** : un rejeu peut
  recréer une ressource déjà créée côté serveur. Le cahier prévoit une « résolution
  de conflits au retour du réseau » — à implémenter (clés d'idempotence, versionnage).
- **Le flux OIDC n'est pas testé en CI** (build seulement) : à valider en montant la
  pile complète avec un navigateur.

### Actions pour le prochain sprint

1. **Idempotence + résolution de conflits** pour la synchro hors-ligne.
2. **Configurer l'IdP Google** dans le realm de production et valider le flux.
3. **Nettoyer `ping`** — dette persistante.
4. **Rafraîchissement du jeton OIDC** (refresh token) et expiration gérée dans la PWA.

> **Vélocité : 39 SP livrés** (capacité 40).

*Dernière mise à jour : 24/07/2026*
