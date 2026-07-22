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

---

## 📊 État en fin de sprint

Sprint réalisé **en mode dégradé** : la partie technique à la portée de l'équipe
est faite et prouvée ; trois éléments dépendent du client ou d'un environnement
réel et restent dus.

| Livrable DoD | État | Preuve / reste dû |
|:---|:---|:---|
| 4 ADR | 🟢 **Accepté (hypothèses)** | Arbitrés le 22/07 par l'équipe projet, faute d'arbitrage client dans les délais (escalade J+5 échue). Décisions proposées retenues, sous réserves documentées (cf. `06_decisions/`). **SPRINT-01 débloqué.** Signature client encore due. |
| Walking skeleton | 🟡 **Prouvé en local, assemblage partiel** | Chaîne complète testée (`mvn verify`, 16 tests, `Skipped: 0`) et **CI applicative verte** depuis le 21/07 ; 4 services sur 6 assemblés et vérifiés. **Pas déployé en production réelle** (hébergeur/domaine/Let's Encrypt hors périmètre technique). |
| Restauration de sauvegarde | 🟢 **Réussie** | `infra/tester-restauration.sh` : témoin détruit puis retrouvé après restauration. |
| Maquettes des 3 écrans | 🟡 **Produites** | `docs/maquettes/` aux jetons de la charte ; **non validées par un apiculteur**. |
| AIPD | 🟢 **Lancée** | `07_conformite/AIPD.md` présent ; scan de licences AGPL câblé en CI. |

**Détail technique livré et prouvé :** ossature Spring Boot 3 / React 19 ;
PostgreSQL + PostGIS + TimescaleDB via Flyway ; sécurité OAuth2/Keycloak, API
fermée par défaut ; proxy Nginx + TLS ; Prometheus + Grafana ; CI applicative
(build, tests, gitleaks, Dependency-Check, licences) ; sauvegarde/restauration.

**Assemblage `compose` — repris le 21/07, partiellement prouvé.** Le réseau s'est
débloqué : les 4 images manquantes sont descendues. 4 services sur 6 tournent et
sont vérifiés (`postgres` sain avec PostGIS 3.4.3, `keycloak` avec le realm `zumm`
importé, `prometheus` et `grafana` sains). Restent `backend` — dont l'image n'a pas
pu être construite, l'étage Maven exigeant ~2 h de téléchargement — et `nginx`, qui
en dépend directement (`host not found in upstream "backend"`).

> ⚠️ **L'assemblage a démenti un livrable déclaré.** Le realm Keycloak était annoncé
> livré alors qu'il n'avait **jamais été importé** : le JSON contenait des clés de
> commentaire, rejetées par l'importateur, et le conteneur sortait en 1 à chaque
> démarrage. Les tests d'intégration ne pouvaient pas le voir — `SecuriteApiIT`
> valide la configuration Spring Security, pas l'import du realm. Corrigé et
> vérifié le 21/07. C'est l'illustration la plus nette de la règle du sprint :
> une brique « prouvée isolément » n'est pas une brique assemblée.

## 📝 Rétrospective

### Ce qui a bien fonctionné

- **La règle « prouver, pas déclarer » a payé.** Un `BUILD SUCCESS` a menti
  plusieurs fois (tests d'intégration silencieusement ignorés, processus tués).
  Exiger `Skipped: 0` sur les tests d'intégration a évité de fausses validations.
- **Ordre walking skeleton avant métier** confirmé : les frictions (versions
  Docker, réseau, TLS) sont apparues sur une entité factice, pas sur le CRUD.
- **Le socle documentaire a servi de contrat** : annexe B et registre des ADR ont
  cadré les choix sans improvisation.

- **L'assemblage a payé immédiatement.** Monter la pile a révélé, en une heure, un
  livrable déclaré mais inexistant (le realm Keycloak). Aucun test unitaire ou
  d'intégration ne pouvait le voir.

### Ce qui peut être amélioré

- **Le réseau de développement (~64 Ko/s) est un risque projet réel.** Il a
  dominé le sprint : images Docker inaccessibles, URLs signées expirées. À traiter
  (miroir de registre local, ou lien dédié) avant les sprints suivants.
- **Compatibilité Testcontainers ↔ Docker Engine 29** : perdue à découvrir ; à
  épingler dans les prérequis.
- **`.env` à la racine + `--env-file`** : source d'échecs répétés ; documenté,
  mais à surveiller.
- **Le travail a vécu 2 jours sur une branche non poussée**, donc jamais jugé par
  la CI. Au premier push, les deux workflows étaient rouges — pour des causes
  triviales (bit d'exécution sur `mvnw`, `pre_compile` LaTeX mal ciblé) qui
  auraient été vues immédiatement. **Pousser la branche dès le premier commit.**
- **La CI documentaire était rouge depuis le 18/07 sans que personne le sache** :
  aucune alerte, aucun suivi. Un workflow rouge toléré cesse d'être un signal.

### Actions pour le prochain sprint

1. ~~**Faire arbitrer les 4 ADR**~~ — ✅ **fait le 22/07.** Arbitrés par l'équipe
   projet sur hypothèses par défaut (escalade J+5 échue), dans le sens des
   décisions proposées. ADR-001 (multi-tenant → `tenant_id`/RLS) est acté :
   SPRINT-01 peut démarrer. **Reste dû : confirmation/signature client**, et pour
   ADR-002 la volumétrie réelle **avant EPIC-004**.
2. **Résoudre le débit réseau / registre d'images** avant d'ajouter des services.
3. **Exécuter `docker compose up` de bout en bout** sur un lien correct, puis
   présenter la démo complète.
4. **Planifier la validation des maquettes** avec un apiculteur référent.
5. ~~**Réparer `Build PDFs`**~~ — ✅ **fait le 22/07.** Les trois cahiers
   compilent en CI ; les deux workflows de `main` sont verts. Détail et pièges
   dans `docs/JOURNAL.md` (entrée du 22/07). Conflit `bidi`/`array` + macro
   `\UseMathForPositioningText` indéfinie dans l'image CI ; contrôle de fraîcheur
   basé sur le contenu remplacé par un contrôle sur les dates de commit.
6. **Terminer l'assemblage** : construire l'image backend, puis relancer la pile
   complète pour que `nginx` trouve son *upstream*.

> **Vélocité :** sprint de cadrage, hors vélocité produit (0 point). Aucune
> conséquence sur le burndown des sprints métier.

*Dernière mise à jour : 22/07/2026*
