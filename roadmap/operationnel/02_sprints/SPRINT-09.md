# 🏃 SPRINT-09: Exploitation & durcissement

**Thème:** Notifications, restitution documentaire, traçabilité et anticipation
**Objectif:** Compléter le produit par des fonctionnalités d'exploitation à valeur immédiate et fiabiliser le déploiement de production
**Période:** 2026-11-17 → 2026-11-30 (14 jours)
**Story Points:** 26 / Capacity: 40

---

## 📅 Cérémonies Scrum

| Cérémonie | Date/Heure | Durée |
|:---|:---|:---|
| Sprint Planning | 2026-11-17 09:00-13:00 | 4h |
| Daily Scrum | Tous les jours 09:15 (15 min) | 15 min |
| Sprint Review | 2026-11-30 14:00-16:00 | 2h |
| Sprint Retrospective | 2026-11-30 16:00-17:00 | 1h |

---

## 📋 User Stories

| ID | Story | Points | Statut | Assigné |
|:---|:---|:---:|:---|:---|
| US-041 | Notifications e-mail des alertes de seuil | 5 | 🟢 Livré (colonne `agent.email` V11, service tolérant aux pannes, désactivé par défaut) | - |
| US-042 | Prévision de récolte (tendance du poids) | 8 | 🟢 Livré (`GET /api/tableaux/previsions`, régression linéaire + projection 7 j, widget) | - |
| US-043 | Journal d'audit (qui a fait quoi, quand) | 8 | 🟢 Livré (table `audit_entree` V12 + RLS, aspect AOP, `GET /api/audit`, onglet responsable/admin) | - |
| US-044 | Rapport de visite au format PDF | 5 | 🟢 Livré (OpenPDF, `GET /api/visites/{id}/rapport.pdf`, bouton de téléchargement) | - |

---

## 🎯 Sprint Review - Démonstration

**Date:** 2026-11-30 14:00-16:00

Notification d'alerte, export PDF d'un rapport de visite, consultation du journal
d'audit (création/suppression tracées) et widget de prévision de récolte, sur données
de démonstration.

---

## ⚠️ Risques Identifiés

Dépendances tierces (SMTP, bibliothèque PDF) et durcissement de production (isolation
de la base Keycloak, validation de l'émetteur des jetons) — à valider sur pile neuve.

---

## 📊 Burndown Chart

| Jour | Reste à faire (idéal) | Reste à faire (réel) | Notes |
|:---|:---:|:---:|:---|
| Jour 1 | 26 | 26 | Notifications e-mail (US-041) |
| Jour 4 | 20 | 18 | Prévision de récolte (US-042) |
| Jour 7 | 13 | 13 | Journal d'audit (US-043) |
| Jour 10 | 7 | 5 | Rapport PDF (US-044) |
| Jour 14 | 0 | 0 | Durcissement déploiement + tests |

---

## 📝 Rétrospective

**Résultat : les 4 user stories livrées et testées.**
Backend : **36 tests unitaires + 55 d'intégration, `Skipped: 0`**, `BUILD SUCCESS`.

### Ce qui a bien fonctionné

- **Chaque fonctionnalité a été branchée sur l'existant** : la notification s'insère
  dans la logique d'hystérésis (`SeuilAlerteService`), la prévision réutilise la série
  de poids TimescaleDB, l'audit intercepte les services par un aspect AOP, le PDF part
  du DTO de visite déjà chargé.
- **Deux blocages de déploiement de production corrigés** : Keycloak dispose désormais
  d'une base dédiée (le schéma `public` de `zumm` redevient la propriété exclusive de
  Flyway), et la validation JWT récupère les clés via le JWKS interne tout en validant
  l'émetteur réel (paramétrable) — évite la course de démarrage et le rejet des jetons.
- **Authentification finalisée** : déconnexion OIDC complète (RP-Initiated Logout).

### Ce qui peut être amélioré / limites assumées

- **Notifications e-mail désactivées par défaut** : aucune dépendance SMTP en local ni
  en CI ; activation par `ZUMM_NOTIF_EMAIL_ENABLED` + `spring.mail.*` en production.
- **Isolation Keycloak** effective uniquement sur volume vierge (script
  `docker-entrypoint-initdb.d`) : un volume antérieur exige un `down -v`.

*Dernière mise à jour : 25/07/2026*
