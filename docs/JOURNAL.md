# Journal de développement

Une entrée par séance : ce qui a été livré, l'état exact, les décisions prises,
la prochaine action et les blocages. Objectif : pouvoir reprendre le travail sans
relire tout le dépôt.

---

## 2026-07-19 — SPRINT-00, ossature du walking skeleton

**Branche :** `sprint00-ossature` (créée depuis `migration-spring-boot`).

### Livré

- **Backend** Spring Boot 3.4.1, Maven wrapper embarqué, bytecode **17**
  (`maven.compiler.release`) quel que soit le JDK de développement.
- `GET /api/info` (identité + message traduit) et `GET /actuator/health`.
- **Flyway `V1`** : extensions PostGIS et TimescaleDB + table factice `ping`.
- **JPA** : entité `Ping`, `PingRepository`, `ddl-auto: none` (Flyway seul
  propriétaire du schéma).
- **Frontend** React 19 + TypeScript (Vite) : écran d'état de l'API, bascule
  FR/EN/AR avec `lang`/`dir` réels, jetons de la charte en CSS, mode sombre.
- **Infra** : `infra/backend.Dockerfile` (multi-étapes, utilisateur non-root),
  `infra/docker-compose.yml` (PostgreSQL + backend).
- **Config** : `config/ConfigZumm.example.ini`, `.env.example`, `.gitignore`
  complété.
- **Docs** : `docs/README.md` (lancement), ce journal.

### Vérifié

| Vérification | Résultat |
|---|---|
| `./mvnw test` | ✅ **8 tests verts** (3 web + 5 parité i18n) |
| `npm run build` | ✅ build Vite réussi (197 kB JS, 62 kB gzip) |
| `scripts/check-sync.sh` | ✅ 3 masters synchronisés |
| `./mvnw verify` (Testcontainers) | ✅ **3 tests verts, Skipped: 0** (44 s) |

Le `verify` prouve, sur une base PostgreSQL réelle : migrations Flyway
appliquées, extensions **PostGIS et TimescaleDB toutes deux actives**, entité
persistée puis relue, `/actuator/health` à `UP` base comprise.

### Non vérifié

`docker compose up` (la stack complète) n'a pas encore été lancé. Keycloak,
Nginx/TLS, l'observabilité et la sauvegarde/restauration restent à faire
(tranches 3 à 7).

### Chemin parcouru pour y arriver — à ne pas refaire

1. **Testcontainers 1.20.4** (version imposée par Spring Boot 3.4.1) est
   incompatible avec **Docker Engine 29** (API minimale 1.40) : découverte du
   démon en `HTTP 400`, tests d'intégration **ignorés en silence, build vert**.
   Ni `DOCKER_HOST` ni `DOCKER_API_VERSION` n'y changent rien → version forcée
   à **1.21.4** dans le `pom.xml`.
2. Le dépôt **`timescale/*`** est impraticable depuis ce réseau (~64 Ko/s
   mesurés, couches qui ne démarrent pas). Quota Docker Hub vérifié : 100/100,
   donc hors de cause. → image de test reconstruite sur base `postgis/postgis`.
3. Le paquet TimescaleDB (~65 Mo) est servi par une **URL signée qui expire**
   avant la fin du téléchargement sur liaison lente → `Acquire::Retries "10"`
   plus cache APT persistant.
4. **Testcontainers ne peut pas construire ce Dockerfile** : les montages de
   cache exigent BuildKit, l'API Docker utilise le builder historique. →
   l'image est construite manuellement et référencée par son nom.

**Règle retenue :** un `BUILD SUCCESS` ne prouve rien tant qu'on n'a pas lu
`Skipped: 0` sur la ligne des tests d'intégration.

### Décisions

- **Spring Boot 3.4.1** choisi pour être certain de la disponibilité de la
  version ; à faire évoluer une fois le build stabilisé.
- **Pas de `tenant_id` ni de RLS** : ADR-001 est au statut « Proposé ».
  À réintroduire par migration dès l'arbitrage.
- **Langue du code** : identifiants en anglais ASCII, commentaires et noms de
  tests en français.
- Le client d'API est **écrit à la main à titre provisoire** ; il devra être
  **généré depuis le contrat OpenAPI** dès que celui-ci existe.
- `docker-compose.yml` ne contient que les services réellement vérifiables ;
  Keycloak, Nginx, Prometheus et Grafana sont ajoutés à leur tranche.

### Blocages

1. **Docker Desktop arrêté** — bloque les tranches 2 à 7 du walking skeleton.
2. **Les 4 ADR sont « Proposé »** — la porte du SPRINT-01 reste fermée.
3. Livrables SPRINT-00 hors de portée de l'équipe technique : arbitrage client
   des ADR, déploiement en production réelle, validation des maquettes par un
   apiculteur.

### Prochaine action

Démarrer Docker, lancer `./mvnw verify` et `docker compose up` pour prouver la
chaîne, puis enchaîner sur Keycloak (tranche 3).
