<p align="center">
  <img src="assets/logo/zumm-logo.png" alt="Logo Zümm" width="220">
</p>

<h1 align="center">Zümm</h1>

<p align="center"><em>Système d'information géographique de gestion et de suivi apicole</em></p>

<p align="center">
  <a href="https://github.com/iyednefzi99/Z-mm/actions/workflows/ci.yml"><img src="https://github.com/iyednefzi99/Z-mm/actions/workflows/ci.yml/badge.svg" alt="CI applicative"></a>
  <a href="https://github.com/iyednefzi99/Z-mm/actions/workflows/build-pdfs.yml"><img src="https://github.com/iyednefzi99/Z-mm/actions/workflows/build-pdfs.yml/badge.svg" alt="Compilation des PDF"></a>
  <img src="https://img.shields.io/badge/couverture%20backend-81.2%25-2E9E3F" alt="Couverture backend 81,2 %">
  <img src="https://img.shields.io/badge/langues-FR%20%C2%B7%20EN%20%C2%B7%20AR-D9A521" alt="Trilingue FR EN AR">
</p>

<p align="center">
  <strong>Français</strong> · <a href="README.en.md">English</a>
</p>

---

## 1. Présentation

Zümm est une application web multi-exploitations qui pilote un cheptel apicole de
bout en bout : où sont les ruches, qui les visite, ce qu'elles produisent et
quand elles vont mal.

Un apiculteur professionnel suit aujourd'hui ses ruchers sur des carnets papier
et des tableurs dispersés : la position exacte des sites est un savoir oral, les
rapports de visite ne remontent jamais, et une colonie qui décroche est repérée
trop tard. Zümm remplace ce dispositif par un référentiel unique, cartographié,
alimenté par le terrain et par les capteurs, avec des alertes qui préviennent
avant la perte.

Le dépôt réunit **l'application** (backend Spring Boot, PWA React, microservice
IA, infrastructure Docker) **et les livrables d'ingénierie** qui la justifient :
cahier des charges trilingue, roadmap Scrum/DevOps, charte de design, registre de
décisions d'architecture.

## 2. Fonctionnalités

**Terrain et cheptel**

- Recenser les exploitations, les ruchers, les ruches et leur composition
  (corps, hausses, cadres), avec l'historique des reines et des remérages.
- Saisir un rapport de visite avec photos, puis l'exporter en PDF.
- Planifier les tournées, affecter les agents, faire approuver ou refuser un
  planning par un responsable.
- Travailler **hors connexion** : la PWA garde la saisie et la rejoue au retour
  du réseau.

**Cartographie**

- Visualiser les ruchers sur une carte, chercher les sites proches d'un point,
  détecter les grappes de ruchers et les voisinages qui se concurrencent.
- Les coordonnées GPS sortent **filtrées selon le rôle** : la position exacte
  d'un rucher est une donnée sensible (vol de ruches).

**Production et traçabilité**

- Enregistrer les récoltes, constituer des lots de conditionnement, remonter la
  traçabilité d'un lot jusqu'aux ruches d'origine.
- Générer la mention légale d'un lot et un QR code de traçabilité.

**Supervision et alertes**

- Ingérer les mesures de capteurs (poids, température, humidité) en série
  temporelle, avec agrégation journalière.
- Détecter les anomalies via un microservice Python, avec repli sur une
  détection EWMA locale si le service est absent.
- Alertes sanitaires, rappels de tâches, tableau de bord de synthèse, de
  production et de prévisions.

**Exploitation multi-clients**

- Cloisonnement strict entre exploitations, appliqué **par la base** (RLS
  PostgreSQL), pas seulement par le code applicatif.
- Quatre rôles métier — `apiculteur`, `superviseur`, `responsable`, `admin` —
  plus un rôle `capteur` limité à l'ingestion de mesures.
- Inscription par code d'invitation, journal d'audit, export CSV.
- Interface **trilingue** français / anglais / arabe, l'arabe en RTL complet.

## 3. Stack technique

| Technologie | Rôle dans le projet |
|---|---|
| **Spring Boot 3.5** (JDK 17) | API REST, couche métier, sécurité. Spring MVC + Spring Data JPA. |
| **PostgreSQL 16 + PostGIS + TimescaleDB** | Instance unique. PostGIS porte les requêtes spatiales (proximité, grappes, voisins) ; TimescaleDB l'hypertable des mesures capteurs. |
| **Flyway** | 18 migrations versionnées — le schéma se reconstruit à l'identique depuis zéro. |
| **Keycloak** | Fournisseur d'identité OIDC. Émet les jetons, porte les rôles et le claim `tenant_id`. |
| **Spring Session JDBC** | Sessions serveur du BFF : le navigateur ne reçoit qu'un cookie `HttpOnly`, jamais un jeton. |
| **React 19 + TypeScript + Vite** | PWA cliente. Routeur maison (ADR-005), pas de `react-router`. |
| **MapLibre GL** | Rendu cartographique des ruchers, fonds OpenStreetMap. |
| **springdoc-openapi** | Génère le contrat OpenAPI 3.1 depuis le code ; `openapi-typescript` en dérive les types du front — la CI échoue si les deux divergent. |
| **Python 3.12, bibliothèque standard** (`ia-service`) | Scoring d'anomalie déporté sur les séries de mesures. `requirements.txt` est volontairement vide : aucune dépendance externe à suivre tant que le moteur reste statistique. |
| **Nginx** | Proxy inverse, terminaison TLS, en-têtes de sécurité et CSP. |
| **Prometheus + Grafana** | Métriques Micrometer exposées par Actuator, tableaux de bord d'exploitation. |
| **Testcontainers** | Tests d'intégration sur un PostgreSQL/PostGIS **réel**, pas sur une base en mémoire. |
| **JaCoCo** | Couverture fusionnée unitaire + intégration, plancher bloquant à 80 %. |

## 4. Architecture

```mermaid
flowchart TB
    subgraph nav["Navigateur"]
        PWA["PWA React 19<br/>MapLibre · i18n FR/EN/AR<br/>Service worker (hors ligne)"]
    end

    PWA -- "HTTPS · cookie HttpOnly" --> NGINX

    subgraph docker["Pile Docker — infra/docker-compose.yml"]
        NGINX["Nginx<br/>TLS · CSP · proxy inverse<br/>:80 :443"]

        subgraph api["Spring Boot 3 — :8080"]
            BFF["Couche BFF<br/>/bff/connexion · /bff/session<br/>garde les jetons côté serveur"]
            SEC["Chaîne de sécurité<br/>TenantFilter · ValidateurAudience<br/>PolitiquePositions"]
            REST["22 contrôleurs REST<br/>/api/**"]
            METIER["Services métier<br/>+ Spring Data JPA"]
        end

        KC["Keycloak<br/>OIDC · rôles · tenant_id<br/>:8081"]
        IA["ia-service<br/>Python · scoring d'anomalie"]
        PG[("PostgreSQL 16<br/>PostGIS · TimescaleDB<br/>RLS par tenant")]
        PROM["Prometheus"]
        GRAF["Grafana<br/>:3000"]
    end

    NGINX --> BFF
    NGINX --> REST
    NGINX -- "/realms/ · /resources/" --> KC
    BFF <-- "flux OIDC (client confidentiel)" --> KC
    BFF --> SEC
    REST --> SEC
    SEC --> METIER
    METIER -- "connexion via rôle non-superutilisateur<br/>zumm_app → RLS effective" --> PG
    METIER -- "HTTP interne (repli EWMA local si absent)" --> IA
    BFF -- "Spring Session JDBC" --> PG
    PROM -- "/actuator/prometheus" --> REST
    GRAF --> PROM
```

**Le chemin d'une requête, en une phrase :** le navigateur n'a qu'un cookie de
session ; Nginx relaie vers le BFF, qui rattache la session au jeton qu'il garde
côté serveur ; `ValidateurAudience` vérifie l'émetteur *et* l'audience,
`TenantFilter` exige le claim `tenant_id` (403 sinon) et le pousse dans la
session PostgreSQL ; la RLS filtre alors les lignes **dans la base**, sous un
rôle non-superutilisateur qui ne peut pas la contourner.

## 5. Structure du projet

```
Zümm/
├── backend/                  API Spring Boot (Maven, wrapper embarqué)
│   └── src/main/
│       ├── java/…/controller/    22 contrôleurs REST + 2 contrôleurs BFF
│       ├── java/…/domain/        26 entités JPA + énumérations
│       ├── java/…/service/       services métier
│       ├── java/…/tenant/        TenantFilter, contexte et résolveur multi-tenant
│       ├── java/…/securite/      PolitiquePositions, portée des agents
│       ├── java/…/config/        SecurityConfig, ValidateurAudience, OpenAPI
│       ├── java/…/web/           DTO, pagination, idempotence, gestion d'erreurs
│       └── resources/db/migration/  18 migrations Flyway (V1 → V18)
├── frontend/                 PWA React 19 + TypeScript (Vite)
│   └── src/
│       ├── vues/                 écrans métier (ruches, sites, visites, lots, carte…)
│       ├── ui/                   composants transverses (modale, toasts, graphiques SVG)
│       ├── api/                  openapi.json + types générés + garde de parité
│       ├── auth/  routage/       session BFF et routeur maison (ADR-005)
│       ├── offline/              file de rejeu hors connexion
│       └── i18n/locales/         ressources FR / EN / AR
├── ia-service/               microservice Python de détection d'anomalie
├── infra/                    docker-compose, Nginx, Keycloak, Prometheus, Grafana
├── config/                   ConfigZumm.example.ini — paramètres métier
├── scripts/                  demarrer.ps1, check-sync.sh, check-pdf-current.sh
├── docs/                     guide dev, sécurité, SOLID, stratégie produit, maquettes
├── cahier de charge/{fr,en,ar}/   cahier des charges trilingue (LaTeX → 3 PDF)
├── roadmap/                  roadmap Scrum/DevOps + sources opérationnelles et ADR
├── design/                   charte de design FR/EN/AR + tokens DTCG
└── assets/logo/              logos (SVG maîtres, PNG, favicons, PDF print)
```

## 6. Installation et démarrage

### Prérequis

| Outil | Version | Vérifier |
|---|---|---|
| JDK | 17 ou plus | `java -version` |
| Docker Engine / Desktop | démon démarré | `docker info` |
| Node.js | 20 ou plus | `node -v` |

Maven n'est pas requis : le dépôt embarque `backend/mvnw`.

### Cloner et configurer

```bash
git clone https://github.com/iyednefzi99/Z-mm.git
cd Z-mm
cp .env.example .env
```

Le `.env` vit **à la racine**, pas dans `infra/`. Tous les mots de passe y sont
déclarés obligatoires : la pile refuse de démarrer sans eux plutôt que de
retomber sur une valeur devinable.

| Variable | Requis | Défaut | Rôle |
|---|---|---|---|
| `DB_PASSWORD` | oui | — | Rôle propriétaire `zumm` : migrations Flyway (DDL) et base Keycloak. |
| `DB_APP_USER` | oui | `zumm_app` | Rôle applicatif non-superutilisateur, créé par la migration V3. |
| `DB_APP_PASSWORD` | oui | — | Mot de passe du rôle applicatif. C'est par lui que l'application se connecte, pour que la RLS soit effective. |
| `KC_ADMIN_USER` | oui | `admin` | Console d'administration Keycloak. |
| `KC_ADMIN_PASSWORD` | oui | — | Mot de passe administrateur Keycloak. |
| `GRAFANA_PASSWORD` | oui | — | Interface Grafana. |
| `SPRING_PROFILES_ACTIVE` | non | `dev` | Profil Spring : `dev` ou `prod`. |
| `ZUMM_BFF_CLIENT` | non | `zumm-bff` | Client Keycloak confidentiel utilisé par le BFF. |
| `ZUMM_BFF_SECRET` | non | `secret-bff-dev` | Secret du client BFF. **À régénérer en production** (`openssl rand -base64 32`) et à faire correspondre au realm. |
| `ZUMM_OIDC_ISSUER_URI` | non | URL interne Keycloak | En production, l'URL **publique** de Keycloak — celle que portent les jetons émis pour le navigateur. |
| `ZUMM_IA_URL` | non | `http://ia-service:8000` | Microservice d'anomalie. **Vider** la variable désactive le couplage : repli sur la détection EWMA locale, sans erreur. |
| `POSTGRES_IMAGE` | non | image locale PostGIS+TimescaleDB | Cible de production : `timescale/timescaledb-ha:pg16-ts2.14`. |

### Lancer la pile complète

Deux prérequis ne sont **pas** versionnés et doivent être produits une fois, sinon
`postgres` et `nginx` refusent de démarrer : l'image PostgreSQL locale (défaut de
`POSTGRES_IMAGE`) et le certificat TLS de développement.

```bash
docker build -f infra/test-postgres.Dockerfile -t zumm/test-postgres:16 infra/
bash infra/generer-certificat-dev.sh
docker compose --env-file .env -f infra/docker-compose.yml up -d --build
```

Sur Windows, `Demarrer-Zumm.cmd` (double-clic) fait la même chose via
`scripts/demarrer.ps1` : il réveille Docker Desktop, monte les conteneurs, attend
que `/actuator/health` réponde `UP` puis ouvre le navigateur.

| Service | Adresse |
|---|---|
| Application (via Nginx) | `https://localhost` |
| Keycloak | `http://localhost:8081` |
| Grafana | `http://localhost:3000` |

### Développer sans la pile

```bash
# Backend seul — API sur http://localhost:8080
cd backend && ./mvnw spring-boot:run

# Frontend seul — http://localhost:5173, relaie /api et /actuator vers :8080
cd frontend && npm install && npm run dev
```

### Base de données

Aucune étape manuelle : **Flyway applique les 18 migrations au démarrage**, crée
le rôle applicatif, les politiques RLS, l'extension PostGIS et l'hypertable
TimescaleDB. Pour repartir de zéro :

```bash
docker compose --env-file .env -f infra/docker-compose.yml down -v
```

## 7. Utilisation

1. **Se connecter** sur `https://localhost`. L'écran d'entrée poste les
   identifiants au BFF — ils ne transitent jamais par l'URL, et aucun jeton
   n'atteint le navigateur.
2. **S'inscrire**, le cas échéant, avec un **code d'exploitation** : c'est lui
   qui rattache le nouveau compte à son exploitation. Sans code valide,
   l'inscription est refusée — on ne rejoint pas un tenant par hasard.
3. **Déclarer le cheptel**, dans cet ordre : exploitation → site (rucher) →
   ruches → composition. La position du site alimente directement la carte.
4. **Faire une visite** : ouvrir une ruche, remplir le rapport, joindre des
   photos, exporter le PDF (`GET /api/visites/{id}/rapport.pdf`).
5. **Planifier une tournée** : un responsable affecte les agents, le planning
   part en approbation, l'agent le retrouve sur son téléphone — y compris hors
   réseau.
6. **Suivre la production** : saisir les récoltes, constituer les lots, obtenir
   la traçabilité (`/api/recoltes/tracabilite/{lot}`) et la mention légale.
7. **Surveiller** : les tableaux de bord (synthèse, production, prévisions,
   alertes sanitaires) et les anomalies de mesures remontent ce qui décroche.

Ce que l'on voit dépend du rôle : un `apiculteur` lit le référentiel et saisit
visites, mesures et récoltes ; un `superviseur` y ajoute l'approbation des
plannings ; un `responsable` crée et supprime ruchers, ruches, agents et fermes,
et consulte l'audit et les codes d'invitation ; un `admin` a le même périmètre au
sommet. Un jeton valide **sans rôle métier** n'atteint aucun endpoint.

## 8. Aperçu du produit

Pas de démonstration en ligne : la pile est prévue pour tourner en local
(section 6).

<!-- TODO — captures à prendre, puis décommenter le tableau ci-dessous.
     Format : 1440×900, thème clair, sur le jeu de démonstration
     (tenant `exploitation-demo`), aucune donnée réelle :
       docs/screenshots/tableau-de-bord.png  synthèse, production, alertes
       docs/screenshots/carte.png            ruchers et ruches sur MapLibre
       docs/screenshots/visite.png           saisie d'un rapport de visite
       docs/screenshots/hors-ligne.png       bandeau hors-ligne + file de rejeu
       docs/screenshots/rtl-ar.png           interface en arabe, RTL complet

     Tableau à rétablir tel quel, et à répercuter dans README.en.md :

     | Tableau de bord | Carte des ruchers |
     |:-:|:-:|
     | ![Tableau de bord](docs/screenshots/tableau-de-bord.png) | ![Carte des ruchers](docs/screenshots/carte.png) |
-->

En attendant, les maquettes HTML statiques de conception se consultent sans rien
démarrer : [`docs/maquettes/`](docs/maquettes/) — calendrier des agents, rapport
de visite, carte des ruchers.

## 9. Documentation de l'API

L'API expose **56 chemins / 89 opérations** sous OpenAPI 3.1. Le contrat est
**généré depuis le code** et versionné dans
[`frontend/src/api/openapi.json`](frontend/src/api/openapi.json) ; la CI échoue
si le code et le contrat divergent.

- Interface interactive : `http://localhost:8080/swagger-ui.html`
- Contrat brut : `http://localhost:8080/v3/api-docs`

Ces deux adresses supposent le **backend joint directement** (`./mvnw
spring-boot:run`). Sous la pile Docker complète, le port 8080 n'est pas publié et
Nginx ne relaie que `/api/`, `/bff/`, `/actuator/`, `/oauth2/`, `/login/` et les
routes Keycloak : Swagger UI n'y est pas exposé — c'est de l'outillage de
développement, pas une surface de production.

**Authentification.** Toutes les routes `/api/**` exigent une session
authentifiée (cookie `HttpOnly`) et **au moins un rôle métier**. Le jeton sous-
jacent doit porter un claim `tenant_id`, faute de quoi le filtre répond `403`.
Les routes `/bff/**` sont le point d'entrée public d'identité.

### Routes d'identité

| Méthode | Chemin | Auth | Corps / Paramètres | Réponse |
|---|---|---|---|---|
| `POST` | `/bff/connexion` | aucune | `{ identifiant, motDePasse }` | `204` + cookie de session. `401` sur identifiants invalides — sans divulguer si le compte existe ; `403` si le compte est suspendu ; `503` si l'IdP est injoignable |
| `POST` | `/bff/inscription` | aucune | `{ nom, courriel, motDePasse, code }` | `201`. `422` si le code d'exploitation est inconnu ou le mot de passe refusé ; `409` si le courriel est déjà pris |
| `GET` | `/bff/session` | cookie | — | Identité courante, rôles et tenant ; `401` hors session |

### Exemples représentatifs

| Méthode | Chemin | Rôles requis | Paramètres | Réponse |
|---|---|---|---|---|
| `GET` | `/api/info` | **aucun** (public) | en-tête `Accept-Language` | Identité de l'application, traduite |
| `GET` | `/api/ruches` | tout rôle métier | pagination | Ruches du tenant courant |
| `POST` | `/api/ruches` | `responsable`, `admin` | Ruche | `201` + ruche créée |
| `POST` | `/api/visites` | tout rôle métier | Rapport de visite | `201` + visite créée |
| `GET` | `/api/visites/{id}/rapport.pdf` | tout rôle métier | — | PDF du rapport |
| `GET` | `/api/sites/proches` | tout rôle métier | `lat`, `lon`, `rayon` | Sites dans le rayon, **coordonnées filtrées selon le rôle** |
| `GET` | `/api/sites/grappes` | tout rôle métier | — | Grappes de ruchers (agrégation PostGIS) |
| `POST` | `/api/mesures` | `capteur` **ou** tout rôle métier | Mesure capteur | `201`, écriture idempotente dans l'hypertable |
| `GET` | `/api/mesures/journalier` | tout rôle métier | plage de dates, ruche | Série agrégée par jour |
| `GET` | `/api/anomalies` | tout rôle métier | — | Anomalies détectées (microservice IA ou repli EWMA) |
| `POST` | `/api/plannings/{id}/approuver` | `superviseur`, `responsable`, `admin` | — | Planning approuvé |
| `GET` | `/api/recoltes/tracabilite/{lot}` | tout rôle métier | — | Chaîne de traçabilité du lot |
| `GET` | `/api/tableaux/synthese` | tout rôle métier | — | Indicateurs du tableau de bord |
| `GET` | `/api/audit` | `responsable`, `admin` | filtres | Journal d'audit |
| `POST` | `/api/invitations` | `responsable`, `admin` | — | Code d'invitation d'exploitation |
| `GET` | `/api/export/visites` | tout rôle métier | filtres | Export CSV |

`POST`, `PUT` et `DELETE` sur `fermiers`, `fermes`, `sites`, `agents` et `ruches`
sont réservés à `responsable` et `admin` — la lecture reste ouverte à tout rôle
métier. Le rôle `capteur` n'ouvre **que** `POST /api/mesures` : il ne donne accès
à aucun autre endpoint.

Ressources CRUD complètes (`GET`/`POST` sur la collection, `GET`/`PUT`/`DELETE`
sur l'élément) : `agents`, `fermes`, `fermiers`, `lots`, `plannings`, `ruches`,
`sites`, `taches`, `visites`.

## 10. Décisions d'ingénierie

Chaque décision structurante est tracée dans un ADR
([`roadmap/operationnel/06_decisions/`](roadmap/operationnel/06_decisions/)).
Les plus lourdes de conséquences :

**Multi-tenant en base plutôt que dans le code** ([ADR-001](roadmap/operationnel/06_decisions/ADR-001-multi-tenant.md)).
Le cloisonnement passe par la Row Level Security de PostgreSQL, et l'application
se connecte sous un rôle **non-superutilisateur** — un superutilisateur
contournerait la RLS. Coût : toute table métier doit porter `tenant_id`, sa
politique RLS et une clé étrangère **composite** `(id, tenant_id)`. Bénéfice :
un `WHERE` oublié dans une requête ne fait pas fuiter les données d'une autre
exploitation.

**Aucun jeton dans le navigateur** ([ADR-006](roadmap/operationnel/06_decisions/ADR-006-stockage-des-jetons.md), [ADR-009](roadmap/operationnel/06_decisions/ADR-009-connexion-dans-l-application.md)).
Le backend mène lui-même le flux OIDC et garde les jetons côté serveur (Spring
Session JDBC) ; le navigateur ne reçoit qu'un cookie `HttpOnly`. On échange
l'apatridie du serveur contre l'immunité au vol de jeton par XSS. La CSP de
`infra/nginx/nginx.conf` reste la seconde ligne de défense — `unsafe-inline` sur
`script-src` serait un retour en arrière, jamais un correctif.

**RLS plutôt que compression TimescaleDB** ([ADR-008](roadmap/operationnel/06_decisions/ADR-008-rls-contre-compression.md)).
PostgreSQL interdit la compression sur une table sous RLS : il fallait choisir.
L'isolation l'emporte sur le gain de stockage ; les mesures restent non
compressées, ce qui est tenable à la volumétrie cible ([ADR-002](roadmap/operationnel/06_decisions/ADR-002-volumetrie.md)).

**Graphiques SVG maison plutôt que Chart.js** ([ADR-007](roadmap/operationnel/06_decisions/ADR-007-graphiques-svg.md)).
Le cahier prévoyait Chart.js. Une bibliothèque de graphiques impose son thème,
son poids et son rendu canvas — donc rien à lire pour un lecteur d'écran. Les
graphiques sont dessinés en SVG, aux couleurs de la charte, accessibles. Coût :
du code à écrire et à tester.

**Routeur maison plutôt que `react-router`** ([ADR-005](roadmap/operationnel/06_decisions/ADR-005-routage-front.md)).
L'application a une dizaine de vues et aucun besoin de routes imbriquées ni de
chargement différé complexe. Une dépendance de moins à suivre.

**Le contrat OpenAPI fait foi, et la CI le prouve.** Le contrat est régénéré à
chaque `verify` ; s'il diffère de la version committée, le build casse. Les
types TypeScript du front en sont dérivés et une garde de parité
(`frontend/src/api/parite.ts`) vérifie **à la compilation** que les types écrits
à la main décrivent la même chose. Un front qui décrit une API disparue devient
une erreur de build, pas un bug d'exécution.

**Tests d'intégration sur une base réelle.** Testcontainers démarre un
PostgreSQL/PostGIS/TimescaleDB authentique : RLS, index spatiaux et hypertables
ne se simulent pas en base mémoire. Coût : Docker requis, et une image de test
maison — le dépôt `timescale/timescaledb-ha` s'est révélé impraticable depuis
certains réseaux, l'image de test repart donc de `postgis/postgis` + TimescaleDB
via APT. **La cible d'exécution reste `timescale/timescaledb-ha`** ; seule la
base de test diffère.

**jOOQ écarté.** Le cahier le prévoyait ; il n'a jamais été nécessaire. Les
requêtes analytiques passent par JPQL et du SQL natif.

Cartographie SOLID et dettes identifiées :
[`docs/ARCHITECTURE-SOLID.md`](docs/ARCHITECTURE-SOLID.md).
Invariants de sécurité à ne pas défaire : [`docs/SECURITE.md`](docs/SECURITE.md).

## 11. Tests

Chiffres relevés le 15/08/2026 dans la sortie des suites, pas recopiés :

| Suite | Volume | Outillage |
|---|---|---|
| Backend — unitaires | **79** tests, 0 échec, 0 ignoré | JUnit 5, Mockito |
| Backend — intégration | **119** tests, 0 échec, **0 ignoré** | Testcontainers sur PostgreSQL/PostGIS/TimescaleDB réel |
| Backend — couverture | **81,2 %** d'instructions, 81,5 % de lignes (branches 63,5 %) | JaCoCo, campagnes fusionnées, plancher **bloquant** à 80 % |
| Frontend | **155** tests, 20 fichiers | Vitest, Testing Library, jsdom |

Ce qui est couvert : la chaîne de sécurité (tenant manquant, audience invalide,
jeton sans rôle), l'isolation RLS entre exploitations, les requêtes spatiales
PostGIS, l'ingestion idempotente des mesures, la génération PDF, la conformité du
contrat OpenAPI, et côté front les vues métier, les dialogues, les graphiques et
le parcours de connexion.

```bash
# Backend — unitaires seuls (Docker non requis)
cd backend && ./mvnw test

# Image PostgreSQL de test — à construire une fois, avant le premier verify
docker build -f infra/test-postgres.Dockerfile -t zumm/test-postgres:16 infra/

# Backend — unitaires + intégration + couverture (Docker requis)
cd backend && ./mvnw -B verify
# Rapport : backend/target/site/jacoco/index.html

# Frontend — les quatre étapes que joue la CI, dans l'ordre
cd frontend && npm ci
npm run typecheck && npm run lint && npm test && npm run build
npm run test:couverture
```

> ⚠️ **Un `BUILD SUCCESS` ne prouve rien si les tests d'intégration ont été
> ignorés faute de Docker.** Vérifier la ligne `Tests run: N, … Skipped: 0` sur
> les rapports `…IT`. La CI le contrôle explicitement et échoue sinon.

Testcontainers ≥ 1.21 est requis à partir de Docker Engine 29 : les versions
antérieures échouent en `HTTP 400` à la découverte du démon et **ignorent
silencieusement** toute la campagne d'intégration, build vert à l'appui. Le
`pom.xml` force donc la version.

## 12. Limites connues et suites

**Ce qui ne fonctionne pas encore parfaitement**

- **Couverture de branches à 63,5 %**, non contrôlée par le seuil : seules les
  instructions bloquent le build. Les chemins d'erreur sont moins couverts que
  les chemins nominaux.
- **Mesures non compressées** : conséquence assumée de l'ADR-008. À très grande
  volumétrie, il faudra trancher autrement (partitionnement, archivage froid).
- **`Ping`** subsiste comme sonde de bout en bout du SPRINT-00. C'est une
  décision documentée dans sa javadoc, pas un oubli.
- **Détection d'anomalie** : le microservice IA reste un scoring statistique.
  Le repli EWMA local est plus fruste encore.
- **Pas de déploiement public** : la pile est prévue pour un mono-hôte Docker.
  L'exécution en production suppose un vrai certificat, un secret BFF régénéré
  et `ZUMM_OIDC_ISSUER_URI` réglé sur l'URL publique de Keycloak.
- **Pas de fichier `LICENSE`** : projet académique, aucun droit d'usage accordé
  par défaut.

**Ce qui viendrait ensuite**

- Porter le plancher JaCoCo sur les branches, pas seulement les instructions.
- Notifications push sur les alertes sanitaires plutôt que consultation active.
- Modèle d'anomalie entraîné sur l'historique réel du cheptel, au lieu d'un
  seuil statistique.
- Résolution de conflits plus fine sur la file de rejeu hors connexion.
- Rotation automatisée du secret BFF et des mots de passe de la pile.

Backlog priorisé et positionnement : [`docs/STRATEGIE-PRODUIT.md`](docs/STRATEGIE-PRODUIT.md).

## Livrables documentaires

Les PDF compilés sont versionnés : inutile d'installer LaTeX pour lire le dossier.

```bash
cd "cahier de charge/fr" && latexmk -pdf cahier_des_charges_fr.tex           # pdflatex
cd "cahier de charge/en" && latexmk -pdf cahier_des_charges_en.tex           # pdflatex
cd "cahier de charge/ar" && latexmk -pdf -xelatex cahier_des_charges_ar.tex  # XeLaTeX, RTL
```

La cohérence trilingue est une contrainte forte : toute modification de structure
côté FR doit être répercutée en EN et AR. `bash scripts/check-sync.sh` le
vérifie, la CI le rejoue, et `scripts/check-pdf-current.sh` échoue si un PDF
committé ne correspond plus à ses sources. Voir
[`CONTRIBUTING.md`](CONTRIBUTING.md).

## Licence et contrainte académique

Projet académique — **tous droits réservés**. Aucun fichier `LICENSE` n'accompagne
le dépôt : aucune licence d'utilisation, de modification ou de redistribution
n'est accordée.

L'usage de générateurs de code pour produire le livrable est proscrit par
l'épreuve. La documentation et la conception de ce dépôt sont réalisées à la main.
