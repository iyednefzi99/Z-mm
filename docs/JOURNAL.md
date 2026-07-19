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
| `./mvnw verify` (Testcontainers) | ⏭️ **3 tests ignorés — Docker arrêté** |

### Non vérifié — à faire dès que Docker tourne

`docker compose up`, migration Flyway sur une vraie base, extensions PostGIS et
TimescaleDB, persistance de bout en bout. **Rien de tout cela n'est prouvé à ce
jour** : `WalkingSkeletonIT` couvre ces points mais n'a pas encore pu s'exécuter.

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
