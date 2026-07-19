# 🏗️ SPRINT-00: Cadrage — décisions, socle et walking skeleton

**Objectif:** Lever les quatre décisions bloquantes et prouver que la chaîne technique tient de bout en bout, **en production**, avant d'écrire la moindre fonctionnalité métier

**Dates:** 2026-07-14 → 2026-07-27 (14 jours)
**Points:** hors vélocité produit — sprint de cadrage, aucune user story métier livrée

---

## ⚠️ Pourquoi ce sprint existe

La roadmap initiale attaquait directement le CRUD en SPRINT-01. Trois problèmes :

1. **Quatre décisions structurantes n'étaient pas arbitrées** (cf. [`06_decisions/`](../06_decisions/)). Coder le modèle de données sans avoir tranché le multi-tenant, c'est accepter de le réécrire.
2. **Aucune preuve que la chaîne technique fonctionne.** Spring Boot + PostGIS + TimescaleDB + Keycloak + TLS + CI/CD, c'est six briques qui doivent s'assembler. Le découvrir au Sprint 4 est trop tard.
3. **Aucun budget UX** alors que l'ergonomie est un critère de recette.

> 💡 **Règle de sortie :** si le *walking skeleton* n'est pas déployé en production à la fin de ce sprint, **aucune date ultérieure n'est crédible** et le planning doit être renégocié — pas absorbé en heures supplémentaires.

---

## 📋 Travaux

### 1. Arbitrage des décisions (ADR) — bloquant

| Tâche | Livrable | Sortie attendue |
|:---|:---|:---|
| Atelier multi-tenant avec le client | [ADR-001](../06_decisions/ADR-001-multi-tenant.md) | Statut **Accepté** |
| Chiffrage volumétrie réelle | [ADR-002](../06_decisions/ADR-002-volumetrie.md) | Statut **Accepté**, TimescaleDB confirmé ou retiré |
| Choix de la cible d'exploitation | [ADR-003](../06_decisions/ADR-003-exploitation.md) | Statut **Accepté**, SLO validés |
| Inventaire de l'existant à reprendre | [ADR-004](../06_decisions/ADR-004-reprise-donnees.md) | Statut **Accepté**, périmètre d'import arrêté |

### 2. Walking skeleton — bloquant

Un unique endpoint trivial (`GET /actuator/health` + une entité factice persistée), qui traverse **toute** la chaîne et tourne **en production** :

- [ ] Projet Spring Boot 3 / JDK 17, build Maven, image Docker multi-étapes
- [ ] PostgreSQL + PostGIS + TimescaleDB provisionné, migration Flyway initiale
- [ ] Multi-tenant : `tenant_id` + politique RLS sur l'entité factice (si ADR-001 accepté)
- [ ] Keycloak : realm, client, un utilisateur de test, jeton JWT validé par l'API
- [ ] Client React + TypeScript, appel authentifié à l'API
- [ ] Nginx + TLS (certificat Let's Encrypt), aucun port applicatif publié
- [ ] CI GitHub Actions : build, tests, image, déploiement staging
- [ ] OpenTelemetry → Prometheus → Grafana, un tableau de bord minimal
- [ ] Sauvegarde automatisée **et une restauration testée**

### 3. Environnements

- [ ] `dev`, `staging`, `prod` réellement provisionnés et joignables
- [ ] Secrets gérés hors dépôt, procédure de rotation documentée

### 4. UX — les trois écrans structurants

- [ ] Maquettes : calendrier agents × ruches, rapport de visite, carte des ruchers
- [ ] **Validation par un apiculteur réel**, pas par l'équipe projet
- [ ] Jetons de la charte (`design/tokens.json`) traduits en variables CSS

### 5. Conformité — démarrage (chemin critique)

- [ ] AIPD lancée (cf. [`AIPD.md`](../07_conformite/AIPD.md)) — le délai juridique est souvent plus long que le délai technique
- [ ] Scan de licences des dépendances en CI (détection AGPL)

---

## 🎯 Revue de fin de sprint

Démonstration en conditions réelles :

1. Ouvrir l'URL de production en HTTPS, se connecter via Keycloak.
2. Créer une entité, la voir persistée, la retrouver après redémarrage du conteneur.
3. Montrer le tableau de bord Grafana réagir à la requête.
4. **Restaurer la base depuis une sauvegarde** et montrer la donnée intacte.
5. Présenter les 4 ADR au statut « Accepté ».

## ⚠️ Risques

| Risque | Mitigation |
|:---|:---|
| Le client ne tranche pas les ADR dans les délais | Escalade dès J+5 ; sans arbitrage, SPRINT-01 ne démarre pas |
| L'assemblage Keycloak + Spring Security prend plus que prévu | C'est précisément l'objet du sprint : le découvrir maintenant |
| Pas d'apiculteur disponible pour valider les maquettes | Identifier le référent métier avant le début du sprint |

## ✅ Definition of Done du Sprint 0

- Les 4 ADR sont au statut **Accepté**, signés par le client.
- Le walking skeleton tourne **en production**, pas en local.
- Une restauration de sauvegarde a réussi.
- Les maquettes des 3 écrans sont validées par un utilisateur métier.
- L'AIPD est lancée.
