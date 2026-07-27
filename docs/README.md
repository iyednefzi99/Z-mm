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

## Démarrage en un clic (Windows)

`Demarrer-Zumm.cmd`, à la racine, enchaîne toute la procédure ci-dessous :
démarrage du démon Docker si besoin, prérequis manquants (`ConfigZumm.ini`,
certificat de développement, image PostGIS de test), montée de la pile **avec la
surcouche de développement** — sans elle, le realm importé est celui de
production et aucun compte ne permet de se connecter —, attente de la santé
réelle du back-end, puis ouverture de `https://localhost`.

```powershell
.\Demarrer-Zumm.cmd                  # ou : .\scripts\demarrer.ps1
.\scripts\demarrer.ps1 -Rapide       # sans reconstruire les images
.\scripts\demarrer.ps1 -Donnees      # (re)charger le jeu de démonstration
.\scripts\demarrer.ps1 -Arreter      # arrêter la pile, volumes conservés

.\scripts\creer-raccourci.ps1        # raccourcis « Zümm » sur le Bureau
```

Le jeu de démonstration est chargé automatiquement au **tout premier**
démarrage (volume PostgreSQL vierge). `-Donnees` le rejoue : il purge d'abord le
tenant `exploitation-demo`, donc les saisies faites sur ce tenant sont perdues.

## Stack complète (Docker)

```bash
cp .env.example .env                 # puis renseigner les mots de passe
cp config/ConfigZumm.example.ini config/ConfigZumm.ini
docker build -f infra/test-postgres.Dockerfile -t zumm/test-postgres:16 infra/
bash infra/generer-certificat-dev.sh
docker compose --env-file .env -f infra/docker-compose.yml up --build
```

> Le fichier `.env` est à la **racine** du dépôt, pas à côté du compose : d'où le
> `--env-file .env` obligatoire. Sans lui, l'interpolation des mots de passe échoue.

Services exposés en local : Nginx `https://localhost` (proxy TLS, certificat
auto-signé → avertissement navigateur attendu), Keycloak `http://localhost:8081`,
Grafana `http://localhost:3000`. L'application et Prometheus ne sont **pas**
publiés : ils ne sont joignables qu'à travers le proxy ou le réseau interne.

La base n'expose aucun port sur l'hôte. Pour l'interroger :

```bash
docker compose --env-file .env -f infra/docker-compose.yml exec postgres psql -U zumm -d zumm
```

## Sauvegarde et restauration

```bash
bash infra/sauvegarde.sh                    # produit un dump horodaté
bash infra/restauration.sh <fichier.dump>   # restaure depuis un dump
bash infra/tester-restauration.sh           # exercice complet : écrit, sauvegarde,
                                            # DÉTRUIT, restaure, vérifie
```

L'exercice de restauration est un livrable de la Definition of Done du SPRINT-00 :
une sauvegarde jamais restaurée n'est pas une sauvegarde.

> **État de la pile.** `infra/docker-compose.yml` démarre huit services :
> PostgreSQL (PostGIS + TimescaleDB), Keycloak, le back-end, la PWA, le
> microservice IA, Nginx/TLS, Prometheus et Grafana. Seul Nginx publie des ports ;
> tout le reste est joint par le réseau interne.

## Base de données

Le schéma appartient à **Flyway** (`backend/src/main/resources/db/migration`) ;
Hibernate ne le modifie jamais (`ddl-auto: none`).

Dix-sept migrations, de `V1` à `V17`. La `V1` crée les extensions PostGIS et
TimescaleDB et la table `ping`, sonde du walking skeleton — **conservée
volontairement** : `WalkingSkeletonIT` s'en sert pour prouver la chaîne complète
sur une base réelle (voir la javadoc de `domain/Ping.java`).

Le multi-tenant arrive en `V2`/`V3`, ADR-001 ayant été accepté le 22/07/2026 :
**toute table métier porte `tenant_id`, sa politique RLS et une clé étrangère
composite `(id, tenant_id)`**. L'application se connecte avec le rôle
non-superutilisateur `zumm_app`, soumis à la RLS ; les migrations, elles, tournent
avec le propriétaire `zumm`.

## Configuration métier

`config/ConfigZumm.ini` (copié depuis le gabarit `.example.ini`, non versionné)
porte les seuils métier, les langues actives et les hypothèses de valorisation du
ROI (section `[economie]`). Il est **relu à chaud** dès que sa date de
modification change (US-025) : le modifier ne demande ni recompilation ni
redémarrage. Absent, l'application démarre sur les valeurs de repli de
`SeuilsMetier`.

Aucun secret dans ce fichier : identifiants et mots de passe passent par
l'environnement (`.env`).
