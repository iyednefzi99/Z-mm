# Prompt de developpement — Plateforme Zümm

## Contexte general

**Zümm** est un Systeme d'Information de Gestion et de Suivi Apicole (SIG apicole).
C'est une application multi-niveaux pour la gestion de ruchers : gestion des
operations, planification des visites, tableaux de bord de performance, alertes
sanitaires, suivi des reines, tracabilite des recoltes, detection d'anomalies
par capteurs IoT, et estimation de quantite de miel.

Le projet suit une metodologie **Scrum + DevOps**, avec 40 User Stories reparties
sur 8 SPRINTS (304+ Story Points). Tous les sprints sont livres.

---

## Stack technique

### Backend (API REST)
- **Framework :** Spring Boot 3.4.1 (JDK 17, Maven)
- **ORM :** Spring Data JPA + Hibernate 6.6
- **SGBD :** PostgreSQL 16 + PostGIS 3.4 (geolocalisation) + TimescaleDB (serie temporelle)
- **Migrations :** Flyway (11 migrations, `ddl-auto: none`)
- **Auth :** OAuth2 Resource Server (JWT/Keycloak OIDC), RBAC 4 roles
- **Multi-tenancy :** Hibernate `@TenantId` + PostgreSQL RLS (double isolation)
- **Observabilite :** Actuator + Micrometer + Prometheus + Grafana
- **Docs API :** OpenAPI 3.1 + Swagger UI (springdoc-openapi 2.8.4)
- **Tests :** 39 tests unitaires (JUnit 5 + Mockito) + 53 tests d'integration (Testcontainers)

### Frontend (PWA)
- **Framework :** React 19 + TypeScript (Vite 6)
- **Routing :** State-driven tab navigation (14 onglets, pas de react-router)
- **Auth :** OIDC/PKCE manuel (zero dependance externe) contre Keycloak
- **Offline :** File d'attente de mutations en localStorage avec replay automatique
- **i18n :** FR/EN/AR (RTL) via contexte React, pas de lib externe
- **Design :** CSS custom avec tokens (pas de framework CSS)
- **Tests :** Aucun ( dette technique )

### Infrastructure
- **Conteneurs :** Docker Compose (6 services)
- **Proxy :** Nginx (TLS 1.3, rate limiting, reverse proxy)
- **Auth :** Keycloak 26.0 (OIDC, realm pre-configurer)
- **Monitoring :** Prometheus + Grafana (dashboard pre-provisionne)
- **Backup :** pg_dump / pg_restore avec script automatise

---

## Architecture applicative

### Modele de donnees (15 entites métier)

```
Fermier (1) ──< (N) Ferme (1) ──< (N) Site (1) ──< (N) Ruche
                                                     │
Agent ────────────────────────────────────────────────┤
                                                     │
                               ┌──────────────────────┤
                               │                      │
                         Compartiment              Visite ──< Photo
                               │                      │
                          Planning                   Tache
                               │
                         Recolte (lot + QR)
                               │
                         SuiviReine
                               │
                          Mesure (TimescaleDB hypertable)
                               │
                          Alerte (seuil + hysteresis)
```

### Entites detaillees

| Entite | Table | Champs cles | Relations |
|---|---|---|---|
| **Fermier** | `fermier` | nom, contact | — |
| **Ferme** | `ferme` | nom | FK → Fermier (composite + tenant_id) |
| **Site** | `site` | nom, lat, lon, alt, 3 dates | FK → Ferme, PostGIS `geog` |
| **Agent** | `agent` | nom, role (4), email | FK → Ferme (optionnel) |
| **Ruche** | `ruche` | modele, etat (6 etats) | FK → Site/Ferme/Agent; 1 corps + 0-5 hausses |
| **Compartiment** | `compartiment` | type (corps/hausse), nbCadres (1-10) | FK → Ruche |
| **Visite** | `visite` | date, heure, duree, raison (6), constatations, sante, productivite | FK → Ruche/Agent/Planning |
| **Photo** | `photo` | url, legende | FK → Visite |
| **Planning** | `planning` | date, heure, raison, statut (propose/approuve/refuse) | FK → Ruche/Agent/Superviseur |
| **Tache** | `tache` | libelle, echeance, faite | FK → Ruche/Agent |
| **Recolte** | `recolte` | date, quantiteKg, typeMiel, lot (auto-genere), qrPayload | FK → Ruche |
| **SuiviReine** | `suivi_reine` | date, statut (5), couleurMarquage, race | FK → Ruche |
| **Mesure** | `mesure` | Composite PK (rucheId + typeIndicateur + instant), valeur | TimescaleDB hypertable |
| **Alerte** | `alerte` | typeIndicateur, niveau, message, valeurDeclenchement, ouverte | FK → Ruche |
| **Ping** | `ping` | libelle (walking skeleton, a supprimer) | — |

### Enums

| Enum | Valeurs |
|---|---|
| `RoleAgent` | apiculteur, superviseur, responsable, admin |
| `EtatRuche` | creee, peuplee, active, en_division, en_collecte, cloturee |
| `TypeCompartiment` | corps, hausse |
| `TypeIndicateur` | poids, temperature, humidite, activite |
| `RaisonVisite` | controle, recolte, traitement, nourrissage, division, autre |
| `StatutPlanning` | propose, approuve, refuse |
| `EtatSante` | bon, moyen, mauvais |
| `EffectifQualitatif` | faible, moyen, fort |

### API REST (17 controlleurs, 40+ endpoints)

| Controlleur | Base Path | Operations |
|---|---|---|
| InfoController | `/api` | `GET /api/info` (public) |
| FermierController | `/api/fermiers` | CRUD |
| FermeController | `/api/fermes` | CRUD |
| SiteController | `/api/sites` | CRUD + `GET /proches` (PostGIS) |
| AgentController | `/api/agents` | CRUD |
| RucheController | `/api/ruches` | CRUD (composition : 1 corps + N hausses) |
| VisiteController | `/api/visites` | CRUD + sous-ressource photos |
| PlanningController | `/api/plannings` | CRUD + `POST /approuver`, `POST /refuser` |
| TacheController | `/api/taches` | CRUD + `GET /rappels` |
| RecolteController | `/api/recoltes` | CRUD + `GET /tracabilite/{lot}` |
| ReineController | `/api/reines` | historique + enregistrer |
| MesureController | `/api/mesures` | ingestion + `GET /alertes` |
| AnomalieController | `/api/anomalies` | detection EWMA |
| MeteoController | `/api/meteo` | Open-Meteo API |
| ExportController | `/api/export` | CSV/TXT visites + ruches |
| TableauDeBordController | `/api/tableaux` | calendrier, production, alertes, synthese |
| ConfigurationController | `/api/configuration` | `/seuils` (ConfigZumm.ini) |
| ConversionController | `/api/conversions` | unites (masse, temperature) |
| ServiceTierceController | `/api/services` | `getZummHoneyActualQuantity` |

### Securite RBAC

| Operation | Roles autorises |
|---|---|
| Approuver/Refuser planning | superviseur, responsable, admin |
| CRUD referentiel (fermiers/fermes/sites/agents/ruches) | responsable, admin |
| Lecture + operations metier | Tout role authentifie |
| `/api/info`, `/actuator/health`, Swagger | Public |

### Multi-tenancy

- **Couche applicative :** `@TenantId` sur toutes les entites via `EntiteTenant` (superclasse)
- **Couche BDD :** PostgreSQL RLS policies sur toutes les tables (`ENABLE ROW LEVEL SECURITY`)
- **Resolution :** `TenantFilter` (servlet) extrait `tenant_id` du JWT → `TenantContext` (ThreadLocal)
- **Connexion :** `TenantConnectionProvider` appelle `set_config('app.current_tenant', ...)` a chaque connexion

---

## Frontend — 14 vues

| Onglet | Composant | Fonctionnalite |
|---|---|---|
| Fermiers | `FermiersVue` | CRUD fermiers (nom + contact) |
| Fermes | `FermesVue` | CRUD fermes (nom + fermier) |
| Sites | `SitesVue` | CRUD ruchers (lat/lon/alt, dates lifecycle) |
| Ruches | `RuchesVue` | CRUD ruches avec composition dynamique (corps + hausses) |
| Plannings | `PlanningsVue` | CRUD + workflow approbation/rejet |
| Visites | `VisitesVue` | CRUD + photos inline |
| Taches | `TachesVue` | CRUD + banderole rappels en retard |
| Tableaux | `TableauxVue` | 4 sous-onglets : calendrier, production, alertes sanitaires, synthese KPI |
| Capteurs | `CapteursVue` | Ingestion mesures, alertes seuils, meteo, quantite miel, anomalie EWMA |
| Reines | `ReinesVue` | Suivi journal par ruche |
| Recoltes | `RecoltesVue` | CRUD + QR codes tracabilite |
| Carte | `CarteVue` | SVG avec ruchers geolocalises + cercles 1/2/3 km |
| Agents | `AgentsVue` | CRUD agents (4 roles) |
| Config | `ConfigVue` | Lecture seuils ConfigZumm.ini |

### Composants UI reutilisables

`Bouton` (4 variantes), `Modale` (focus trap, Escape), `Table<E>` (generique),
`ChampTexte`, `ChampNombre`, `ChampDate`, `ChampZone`, `ChampSelect`,
`CorpsSection` (squelette de section avec titre, bouton Nouveau, etats loading/error/empty)

---

## Design system

### Palette
- **Miel :** `#D9A521` ( primaire, CTA, actif )
- **Vert ruche :** `#2E9E3F` ( secondaire, succes, croissance )
- **Vert ardoise :** `#2C4A42` ( neutre fort, titres, wordmark )
- **Fond :** `#FCFDFC` ( jamais `#FFFFFF` pur )
- **Texte :** `#1B2320` ( jamais `#000000` pur )
- **Degradé signature :** `linear-gradient(120deg, #D9A521 0%, #2E9E3F 100%)`

### Typographie
- **UI :** Inter (fallback system stack)
- **Arabe :** Cairo / Tajawal / IBM Plex Sans Arabic
- **Mono :** JetBrains Mono
- **Echelle :** Display 40/700, H1 32/700, H2 25/600, Body 16/400, Small 14/400

### Espacement
- **Base :** 4px (4, 8, 12, 16, 24, 32, 48, 64)
- **Border radius :** sm 6px, md 12px, lg 20px, pill 999px
- **Ombres :** 3 niveaux, teintees vert-ardoise (pas noir pur)

### Motion
- **Rapide :** 120ms (hover, swap)
- **Base :** 200ms (fade, slide)
- **Lent :** 320ms (drawer, panel)
- **Modal :** 250ms ouverture / 150ms fermeture, scale 0.96→1
- **Accessibilite :** `prefers-reduced-motion` obligatoire

---

## Configuration metiere

Fichier `config/ConfigZumm.ini` (hot-reload toutes les 10s) :

```ini
[seuils]
poids_ruche_alerte_kg = 15
temperature_min_celsius = 32
temperature_max_celsius = 36
humidite_max_pourcent = 70

[visites]
delai_alerte_jours = 21

[carte]
arrondi_degres_public = 2
```

---

## Infrastructure Docker (6 services)

| Service | Image | Port | Role |
|---|---|---|---|
| postgres | `zumm/test-postgres:16` | 5432 (interne) | PostgreSQL + PostGIS + TimescaleDB |
| keycloak | `keycloak:26.0` | 8081 | OIDC identity provider |
| backend | `infra-backend` (build local) | 8080 | Spring Boot API |
| nginx | `nginx:alpine` | 80/443 | TLS termination + reverse proxy |
| prometheus | `prometheus:v2.53.0` | 9090 (interne) | Metriques |
| grafana | `grafana:11.1.0` | 3000 | Tableaux de bord |

---

## Commandes de lancement

```bash
# 1. Preconditions
cp .env.example .env                          # renseigner les mots de passe
cp config/ConfigZumm.example.ini config/ConfigZumm.ini
docker build -f infra/test-postgres.Dockerfile -t zumm/test-postgres:16 infra/
bash infra/generer-certificat-dev.sh           # certificat TLS auto-signe

# 2. Stack complete
docker compose --env-file .env -f infra/docker-compose.yml up -d --build

# 3. Donnees de demo
bash infra/seed-demo.sh

# 4. Frontend (dev separate)
cd frontend && npm ci && npm run dev
```

### Comptes de test (apres seed-demo)

| Identifiant | Mot de passe | Role | Permissions |
|---|---|---|---|
| admin-test | test | admin | Tout |
| responsable-test | test | responsable | Referentiel + operations |
| superviseur-test | test | superviseur | Approbation plannings |
| apiculteur-test | test | apiculteur | Visites, mesures, taches, recoltes |

### URLs d'acces

| Service | URL |
|---|---|
| Frontend (Vite dev) | http://localhost:5174 |
| API Backend | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI Docs | http://localhost:8080/v3/api-docs |
| Keycloak Console | http://localhost:8081 |
| Grafana Dashboard | http://localhost:3000 |
| Nginx (HTTPS) | https://localhost (cert auto-signe) |

---

## Tests

```bash
# Tests unitaires (pas de Docker requis)
cd backend && ./mvnw test

# Tests integration (Docker requis — Testcontainers)
cd backend && ./mvnw verify

# Verifier Skipped: 0 pour les tests integration
# Build SUCCESS avec tests ignores ne prouve rien
```

### Architecture des tests

- **39 tests unitaires** : logique metier pure (hysteresis seuils, EWMA, ROI, CSV escaping, generation lots)
- **53 tests integration** : CRUD complet tous les services, isolation inter-tenant, RBAC, workflows, ingestion, meteo, anomalie
- **Testcontainers** : PostgreSQL/PostGIS reel (image `zumm/test-postgres:16`)
- **CI** : `.github/workflows/ci.yml` avec garde `Skipped: 0`

---

## dette technique et ameliorations prevues

1. **Nettoyage Ping** : supprimer l'entite factice du walking skeleton
2. **Upload photo binaire** : S3/MinIO (actuellement URLs uniquement)
3. **MQTT bridge** : capteurs IoT en temps reel (REST couvre la demonstration)
4. **Google IdP** : federation Keycloak (configuration cote Keycloak, pas de code)
5. **Refresh tokens OIDC** : gestion dans le PWA
6. **Offline sync** : resolution de conflits + idempotence
7. **Erreurs API internationalisees** : ProblemDetail rattache aux messages_*.properties
8. **MapLibre GL** : remplacer le SVG de la carte par OpenStreetMap
9. **Pagination** : ajouter sur les endpoints de liste
10. **Tests frontend** : vitest + testing-library (aucun test actuellement)
11. **Linting frontend** : ESLint + Prettier (absent)

---

## Contraintes academiques

- L'usage de generateurs de code pour le livrable est **proscrit**
- Pas de mentions d'assistance IA dans les commits ou le contenu
- Les noms de variables et fonctions sont en **francais**
- Le produit s'appelle **Zümm** (ASCII "Zumm" dans le code)
