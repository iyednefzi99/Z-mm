# ADR-001 (implémentation) — Socle technique du walking skeleton

- **Statut** : Accepté
- **Date** : 2026-07-19
- **Portée** : code applicatif, SPRINT-00

## Contexte

Le SPRINT-00 exige de prouver que la chaîne technique tient de bout en bout avant
toute fonctionnalité métier. L'annexe B du cahier des charges fixe la cible
(Spring Boot 3, PostgreSQL/PostGIS/TimescaleDB, Keycloak, React, OpenAPI) ; il
restait à trancher les choix d'implémentation qui n'y figurent pas.

## Décision

- **Build** : Maven avec wrapper versionné (`mvnw`), pour rendre le dépôt
  compilable sans installation préalable de Maven. Bytecode **17**
  (`maven.compiler.release`), quel que soit le JDK de développement.
- **Schéma** : propriété exclusive de **Flyway** (`ddl-auto: none`) ; Hibernate ne
  modifie jamais le schéma.
- **Sécurité** : serveur de ressources OAuth2, jetons validés par Keycloak,
  **API fermée par défaut** (tout est `authenticated` sauf exceptions explicites).
- **Base de test** : image locale PostGIS + TimescaleDB
  (`infra/test-postgres.Dockerfile`) plutôt que `timescale/timescaledb-ha`, dont
  le dépôt s'est révélé impraticable depuis le réseau de développement. La cible
  d'exécution reste l'image de la roadmap.
- **Observabilité** : Micrometer → Prometheus → Grafana, tag commun `application=zumm`.

## Conséquences

- Le dépôt est compilable et testable sur un poste neuf sans dépendance à un
  registre d'images pour la base de test.
- Un `mvn verify` prouve la chaîne base de données réelle ; la CI vérifie que les
  tests d'intégration ne sont pas silencieusement ignorés.
- La cible de production et la base de test diffèrent : un écart existe et est
  assumé (même version majeure, mêmes extensions).

## Alternatives écartées

- **Liquibase** plutôt que Flyway : Flyway suffit au périmètre et l'annexe B ne
  l'impose pas.
- **`ddl-auto: validate`** : reporterait sur Hibernate une responsabilité qui
  revient aux migrations.
