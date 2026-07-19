# Démarrage du développement — Zümm

Ce document permet de lancer l'application en local. Il est écrit dès l'ossature
pour que la vérification de bout en bout ait un flux réel à exercer.

## Prérequis

| Outil | Version | Vérifier |
|---|---|---|
| JDK | 17 ou supérieur | `java -version` |
| Docker + Compose | démon démarré | `docker info` |
| Node.js | 20 ou supérieur | `node -v` |

Maven n'est **pas** requis : le dépôt embarque le wrapper (`backend/mvnw`).

Ports utilisés : `5432` (PostgreSQL, non publié), `8080` (API), `5173` (client Vite).

## Backend

```bash
cd backend
./mvnw test          # tests unitaires — ne requiert pas Docker
./mvnw verify        # + tests d'intégration Testcontainers — requiert Docker
./mvnw spring-boot:run
```

Sans Docker, les tests d'intégration sont **ignorés automatiquement** plutôt que
mis en échec (`@Testcontainers(disabledWithoutDocker = true)`). En intégration
continue, Docker est présent : ils s'exécutent réellement et font autorité.

> ⚠️ **Vigilance : un `BUILD SUCCESS` ne prouve pas que les tests d'intégration
> ont tourné.** Ignorés, ils laissent le build vert. Vérifier la ligne
> `Tests run: N, ... Skipped: 0 -- in ...IT` avant de conclure quoi que ce soit.

### Docker Engine 29 et Testcontainers

Docker Engine 29 impose une version d'API minimale de `1.40`. La version de
Testcontainers gérée par défaut par Spring Boot 3.4.1 (**1.20.4**) embarque un
`docker-java` antérieur : la découverte du démon échoue avec un `HTTP 400` et
tous les tests d'intégration sont **silencieusement ignorés**, build vert à
l'appui. Le `pom.xml` force donc `testcontainers.version` à **1.21.4**.

Ni `DOCKER_HOST` ni `DOCKER_API_VERSION` ne corrigent ce cas : seule la montée
de version fonctionne.

### Image de test PostGIS + TimescaleDB — à construire une fois

Les tests d'intégration utilisent une image locale, à construire **avant le
premier `mvn verify`** :

```bash
docker build -f infra/test-postgres.Dockerfile -t zumm/test-postgres:16 infra/
```

Pourquoi une image maison plutôt que `timescale/timescaledb-ha` (l'image de la
roadmap, qui embarque les deux extensions) : son dépôt s'est révélé
impraticable depuis certains réseaux — les couches ne démarrent pas, et 1,5 Go
sur une liaison lente dépasse l'heure. On repart donc de `postgis/postgis`, qui
se télécharge de façon fiable, en y ajoutant TimescaleDB via APT.

**La cible d'exécution reste `timescale/timescaledb-ha`** (cf.
`infra/docker-compose.yml`) : seule la base de *test* diffère.

Le Dockerfile utilise des montages de cache APT (`RUN --mount=type=cache`), qui
exigent **BuildKit** — d'où la construction manuelle plutôt que par
Testcontainers, qui passe par le builder historique de l'API Docker et échoue
sur `--mount`. Le paquet TimescaleDB (~65 Mo) étant servi par une URL signée à
durée limitée, `Acquire::Retries` est indispensable sur liaison lente : chaque
reprise obtient une signature fraîche.

L'API répond alors sur `http://localhost:8080` :

- `GET /api/info` — identité de l'application, traduite selon `Accept-Language`
- `GET /actuator/health` — état de santé

```bash
curl http://localhost:8080/api/info
curl -H "Accept-Language: ar" http://localhost:8080/api/info
```

## Frontend

```bash
cd frontend
npm install
npm run dev          # http://localhost:5173
npm run build
```

Le serveur de développement relaie `/api` et `/actuator` vers le backend : le
client et l'API partagent la même origine, aucun CORS à ouvrir.

## Stack complète (Docker)

```bash
cp .env.example .env                 # puis renseigner DB_PASSWORD
cp config/ConfigZumm.example.ini config/ConfigZumm.ini
docker compose -f infra/docker-compose.yml up --build
```

La base n'expose aucun port sur l'hôte. Pour l'interroger :

```bash
docker compose -f infra/docker-compose.yml exec postgres psql -U zumm -d zumm
```

> **État au SPRINT-00.** La stack contient PostgreSQL et le backend. Keycloak,
> Nginx/TLS, Prometheus et Grafana sont ajoutés tranche par tranche, chacun
> lorsqu'il a été réellement vérifié. La cible complète est décrite dans
> `roadmap/operationnel/03_devops_pipeline/docker-compose.yml`.

## Base de données

Le schéma appartient à **Flyway** (`backend/src/main/resources/db/migration`) ;
Hibernate ne le modifie jamais (`ddl-auto: none`).

La migration `V1` crée les extensions PostGIS et TimescaleDB et une table
`ping`, **entité factice** du walking skeleton — elle sera supprimée dès que le
modèle métier existera. Aucune colonne `tenant_id` ni politique RLS n'est
introduite tant qu'**ADR-001 est au statut « Proposé »**.

## Configuration métier

`config/ConfigZumm.ini` (copié depuis le gabarit `.example.ini`, non versionné)
porte les seuils métier. Son chargement effectif et sa relecture à chaud sont
livrés par l'**US-025** au SPRINT-01.

Aucun secret dans ce fichier : identifiants et mots de passe passent par
l'environnement (`.env`).
