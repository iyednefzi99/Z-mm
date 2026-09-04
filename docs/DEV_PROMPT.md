# Prompt de developpement — Plateforme Zümm

> **Etat : releve le 29/08/2026, apres le SPRINT-20.** Ce fichier est un
> briefing, pas une source de verite : `CLAUDE.md` et le `README.md` font foi, et
> ce document se re-perime a chaque sprint. Toute valeur chiffree qu'on y lit doit
> etre re-mesuree avant d'etre recopiee ailleurs.

## Contexte general

**Zümm** est un Systeme d'Information de Gestion et de Suivi Apicole (SIG apicole).
C'est une application multi-niveaux pour la gestion de ruchers : gestion des
operations, planification des visites, tableaux de bord de performance, alertes
sanitaires, suivi des reines, tracabilite des recoltes, detection d'anomalies
par capteurs IoT, et estimation de quantite de miel.

Le projet suit une methodologie **Scrum + DevOps** : **92 User Stories** en
**21 epics**, reparties sur un sprint de cadrage et **20 sprints** de livraison,
pour **651 Story Points**. Tous sont livres, sauf US-039 (diagrammes UML) et
US-040 (rapport, poster, presentation) — 21 points documentaires que la charte
academique interdit de generer. Velocite applicative reelle : **630 points**.

---

## Stack technique

### Backend (API REST)
- **Framework :** Spring Boot 3.5.16 (JDK 17, Maven)
- **ORM :** Spring Data JPA + Hibernate 6.6
- **SGBD :** PostgreSQL 16 + PostGIS 3.4 (geolocalisation) + TimescaleDB (serie temporelle)
- **Migrations :** Flyway (**19 migrations**, V1 → V19, `ddl-auto: none`)
- **Auth :** **BFF a session serveur** (ADR-006/009) — le back mene le flux OIDC et
  garde les jetons ; le navigateur n'a qu'un cookie `HttpOnly`. La chaine « jeton
  porteur » subsiste pour les machines (client `zumm-capteur`). RBAC 4 roles
  humains + 1 role machine, en refus par defaut
- **Multi-tenancy :** Hibernate `@TenantId` + PostgreSQL RLS (double isolation),
  application connectee sous un role non-superutilisateur
- **Portee horizontale :** `FiltrePortee` limite un agent a ses affectations (US-057)
- **Observabilite :** Actuator + Micrometer + Prometheus + Grafana
- **Docs API :** OpenAPI 3.1 + Swagger UI (springdoc-openapi), contrat versionne et
  verifie en CI — **63 chemins / 99 operations**
- **Tests :** **79 unitaires** (JUnit 5 + Mockito) + **119 d'integration**
  (Testcontainers), couverture JaCoCo **81,2 %** en instructions (plancher
  bloquant 0,80) et 62,7 % en branches (plancher 0,60)

### Frontend (PWA)
- **Framework :** React 19 + TypeScript (Vite 6)
- **Routing :** routes plates **adressables** (`routage/routes.ts`, US-051) —
  19 onglets de console, 10 routes publiques, 1 route de session. Pas de
  react-router (ADR-005)
- **Auth :** **aucun jeton dans le navigateur** — cookie de session pose par le
  BFF ; `auth/session.ts` ne memorise que ce que le serveur dit de la session
- **Offline :** file d'attente de mutations avec rejeu et cle d'idempotence (US-055)
- **i18n :** FR/EN/AR (RTL) — ressources de locale JSON chargees a la demande
  (US-072), parite garantie **a la compilation**
- **Cartographie :** MapLibre GL + OSM, repli SVG sans WebGL (US-067)
- **Design :** CSS custom avec tokens, theme clair / sombre / systeme
- **Tests :** **Vitest + Testing Library**, ESLint + Prettier, joues par la CI

### Infrastructure
- **Conteneurs :** Docker Compose (**8 services**) — postgres, keycloak, backend,
  ia-service, frontend, nginx, prometheus, grafana
- **Proxy :** Nginx (TLS 1.3, CSP sans `unsafe-inline` sur `script-src`, zone de
  debit sur l'authentification, reverse proxy)
- **Auth :** Keycloak 26.0 (OIDC) — deux realms : reference et developpement
- **IA :** microservice Python autonome (`ia-service`), repli EWMA local s'il manque
- **Monitoring :** Prometheus + Grafana (dashboard pre-provisionne)
- **Backup :** pg_dump / pg_restore, avec un exercice de restauration destructif

---

## Architecture applicative

### Modele de donnees (23 entites métier)

> `grep -rl "@Entity"` en renvoie 30 : il matche aussi `@EntityGraph`, present
> dans sept repositories. Le compte juste est **23** (`grep -rl "^@Entity"`).

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
| **AuditEntree** | `audit_entree` | acteur, action, cible, instant | aspect AOP, table sous RLS |
| **CodeInvitation** | `code_invitation` | code, role, consomme | rattache un compte a son exploitation |
| **LotConditionnement** | `lot_conditionnement` | numero, mention d'origine | conformite UE 2024/1438 |
| **LotComposition** | `lot_composition` | part par pays, quantite | FK → LotConditionnement / Recolte |
| **Ping** | `ping` | libelle | **sonde de bout en bout du walking skeleton, conservee volontairement** (cf. javadoc de `domain/Ping.java`) |

### Enums

| Enum | Valeurs |
|---|---|
| `RoleAgent` | apiculteur, superviseur, responsable, admin |
| `EtatRuche` | creee, peuplee, active, en_division, en_collecte, cloturee |
| `TypeCompartiment` | corps, hausse |
| `TypeIndicateur` | poids, temperature, humidite, activite |
| `RaisonVisite` | controle, recolte, traitement, nourrissage, division, autre |
| Referentiels SPRINT-20 | cible de traitement, unite de dose, type d'aliment, methode de comptage, pathologie, gravite, motif de ponte, temperament, type/couleur/origine/cause de cloture de ruche — tenus par `@Pattern` + `CHECK`, pas par des enumerations Java |
| `StatutPlanning` | propose, approuve, refuse |
| `EtatSante` | bon, moyen, mauvais |
| `EffectifQualitatif` | faible, moyen, fort |

### API REST (27 controleurs — 25 sous `/api`, 2 sous `/bff` — 99 operations)

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
| AuditController | `/api/audit` | journal d'audit (responsable, admin) |
| InvitationController | `/api/invitations` | codes d'exploitation (responsable, admin) |
| LotController | `/api/lots` | lots de conditionnement + mention d'origine |
| TraitementController | `/api/traitements` | registre des traitements + `GET /carence` |
| NourrissementController | `/api/nourrissements` | registre des nourrissements |
| ComptageVarroaController | `/api/varroa` | comptages ; taux et verdict calcules au service |
| SessionBffController | `/bff` | `POST /connexion`, `POST /inscription` |
| IdentiteBffController | `/bff` | `GET /session` — identite, roles, tenant |

> `grep -rl "@RestController"` en renvoie 28 : il matche aussi
> `@RestControllerAdvice` (`GestionnaireExceptions`, qui n'expose aucune route).

### Securite RBAC

| Operation | Roles autorises |
|---|---|
| Approuver/Refuser planning | superviseur, responsable, admin |
| CRUD referentiel (fermiers/fermes/sites/agents/ruches) | responsable, admin |
| Journal d'audit, codes d'invitation | responsable, admin |
| Depot de mesures | capteur + tous les roles humains |
| Lecture + operations metier (visites, taches, recoltes) | Tout role **metier** |
| `/api/info`, `/actuator/health` | Public |

> Refus par defaut : `anyRequest().hasAnyRole(...)`, jamais `.authenticated()`.
> Un jeton valide **sans role metier** n'atteint aucun endpoint. Cote front, les
> commandes d'ecriture du referentiel sont masquees pour les roles qui
> recevraient un 403 (`ROLES_ECRITURE` dans `routage/routes.ts`) — confort
> d'affichage, jamais une autorisation.

### Multi-tenancy

- **Couche applicative :** `@TenantId` sur toutes les entites via `EntiteTenant` (superclasse)
- **Couche BDD :** PostgreSQL RLS policies sur toutes les tables (`ENABLE ROW LEVEL SECURITY`)
- **Resolution :** `TenantFilter` (servlet) extrait `tenant_id` du JWT → `TenantContext` (ThreadLocal)
- **Connexion :** `TenantConnectionProvider` appelle `set_config('app.current_tenant', ...)` a chaque connexion

---

## Frontend — 19 onglets de console, plus les pages publiques

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
| Sanitaire | `SanitaireVue` | Traitements, nourrissements, comptages de varroa ; bandeau des carences en cours |
| Recoltes | `RecoltesVue` | CRUD + QR codes tracabilite |
| Carte | `CarteVue` | SVG avec ruchers geolocalises + cercles 1/2/3 km |
| Agents | `AgentsVue` | CRUD agents (4 roles) |
| Config | `ConfigVue` | Lecture seuils ConfigZumm.ini |
| Lots | `LotsVue` | Lots de conditionnement, mention d'origine |
| Invitations | `InvitationsVue` | Codes d'exploitation (responsable, admin) |
| Permissions | `PermissionsVue` | Matrice roles x regles (responsable, admin) |
| Audit | `AuditVue` | Journal d'audit (responsable, admin) |

### Pages hors console (SPRINT-19)

| Route | Composant | Role |
|---|---|---|
| `/accueil` | `AccueilVue` | Vitrine publique — aucun appel d'API |
| `/fonctionnalites`, `/editions`, `/ressources`, `/contact` | `FonctionnalitesVue`, `EditionsVue`, `RessourcesVue`, `ContactVue` | Acquisition et centre d'aide |
| `/a-propos` | `AProposVue` | Seule page publique qui appelle l'API (`GET /api/info`) |
| `/cgu`, `/confidentialite` | `ConditionsVue`, `ConfidentialiteVue` | Pages legales, avec bandeau des manques |
| `/recuperation` | `RecuperationVue` | Mot de passe oublie — aiguillage, pas de traitement |
| `/compte` | `CompteVue` | Session, roles, preferences (exige une session) |
| — | `InterditVue`, `PanneVue`, `IntrouvableVue` | Refus de role, panne 5xx, 404 |

### Composants UI reutilisables

`Bouton` (4 variantes), `Modale` (focus trap, Escape), `Table<E>` (generique),
`ChampTexte`, `ChampNombre`, `ChampDate`, `ChampZone`, `ChampSelect`,
`CorpsSection` (ossature de section : titre, compteur, bouton Nouveau **selon le
role**, etats chargement / erreur / vide / 403 / panne), `Pagination`, `Toasts`
(avec annulation differee), `SelecteurTheme`, `Squelette`, `EtatVide`,
`CoquillePublique` (barre et pied des pages sans session), `PageLegale`

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

## Infrastructure Docker (8 services)

| Service | Port | Role |
|---|---|---|
| postgres | 5432 (interne) | PostgreSQL + PostGIS + TimescaleDB |
| keycloak | 8081 | Fournisseur d'identite OIDC |
| backend | interne | Spring Boot API + BFF |
| ia-service | interne | Scoring d'anomalie (Python) |
| frontend | interne | PWA servie par le proxy |
| nginx | 80/443 | Terminaison TLS + reverse proxy |
| prometheus | interne | Metriques |
| grafana | 3000 | Tableaux de bord |

> Seul Nginx publie des ports. Ni l'application ni Prometheus ne sont joignables
> autrement que par le proxy ou le reseau interne.

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
| Frontend (Vite dev) | http://localhost:5173 |
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

- **92 tests unitaires** : logique metier pure (hysteresis seuils, EWMA, ROI, CSV
  escaping, generation lots, mention d'origine)
- **133 tests d'integration** : CRUD, isolation inter-tenant **sous le role
  applicatif** `zumm_app`, RBAC, portee par affectation, workflows, ingestion
  idempotente, requetes PostGIS, generation PDF, conformite du contrat OpenAPI
- **Front** : Vitest + Testing Library — relever le compte dans la sortie de
  `npm test`, il bouge a chaque lot
- **Testcontainers** : PostgreSQL/PostGIS reel (image `zumm/test-postgres:16`),
  version epinglee a 1.21.4 — sous Docker Engine 29, une version anterieure
  **ignore silencieusement** toute la campagne, build vert a l'appui
- **CI** : `.github/workflows/ci.yml` avec garde `Skipped: 0`

---

## Dette technique et ameliorations prevues

**Soldees depuis** : les jetons ont quitte le navigateur (BFF, SPRINT-16), le
rejeu hors ligne est idempotent (SPRINT-14), la carte utilise MapLibre + OSM
(SPRINT-13), la pagination est en place, le front a ses tests et son linting
(SPRINT-10), et `Ping` **reste** — c'est la sonde de bout en bout du walking
skeleton, pas un oubli.

**Ce qui reste ouvert :**

1. **Upload photo binaire** : `PhotoCorps` ne porte qu'une URL. Stockage objet
   (S3/MinIO), upload multipart et **purge des metadonnees EXIF** — exigee par
   l'AIPD, une photo de rucher geolocalise le rucher
2. **Pont MQTT** : capteurs IoT en temps reel (REST couvre la demonstration)
3. **Federation Google** : configuration cote Keycloak, pas de code
4. **Flux OIDC joue en CI** : les tests utilisent un Keycloak simule — dette
   ouverte depuis le SPRINT-11
5. **Administration de la plateforme** : transverse aux exploitations, a
   distinguer de l'administration d'une exploitation ; suppose de trancher la
   contradiction avec la RLS
6. **Chiffrement au repos des positions GPS** : arbitrage, incompatible avec les
   requetes PostGIS — a trancher par ADR avant mise en production
7. **Ecarts fonctionnels du marche** : traitements et varroa comme entites,
   interventions groupees, observations d'inspection structurees
   (`docs/ECART-CONCURRENTS.md`)

---

## Contraintes academiques

- L'usage de generateurs de code pour le livrable est **proscrit**
- Pas de mentions d'assistance IA dans les commits ou le contenu
- Les noms de variables et fonctions sont en **francais**
- Le produit s'appelle **Zümm** (ASCII "Zumm" dans le code)
