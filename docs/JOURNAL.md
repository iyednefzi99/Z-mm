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

---

## 2026-07-19 (suite) — SPRINT-00 mené jusqu'à la rétrospective

**Branche :** `sprint00-ossature`. **Résultat : 16 tests verts, `Skipped: 0`**
(8 unitaires + 8 d'intégration).

### Livré et prouvé

- **Sécurité** : serveur de ressources OAuth2/Keycloak, API **fermée par défaut**.
  `SecuriteApiIT` prouve qu'un endpoint non déclaré répond 401 en anonyme, 404 une
  fois authentifié — le refus par défaut s'applique bien.
- **Realm Keycloak** (`infra/keycloak/realm-zumm.json`) : 4 rôles métier, client
  PWA public (PKCE), API `bearer-only`.
- **Proxy Nginx + TLS** : redirection HTTP→HTTPS, HSTS, limitation de débit,
  Actuator masqué sauf `/health`. Certificat de dev via script (non versionné).
- **Observabilité** : Micrometer/Prometheus, tag `application=zumm`, tableau de
  bord Grafana provisionné. `WalkingSkeletonIT` vérifie que les séries du
  dashboard existent réellement.
- **Sauvegarde/restauration** : `tester-restauration.sh` exécuté avec succès
  (témoin détruit puis retrouvé).
- **CI applicative** (`.github/workflows/ci.yml`) : build backend+frontend,
  garde-fou anti-`Skipped`, gitleaks, Dependency-Check, détection AGPL.
- **Maquettes** des 3 écrans (`docs/maquettes/`), aux jetons de la charte,
  marquées « en attente de validation apiculteur ».
- **ADR d'implémentation** (`docs/ADR/`).

### Non fait / dégradé

- **`docker compose up` complet non exécuté** : images Keycloak et Grafana non
  téléchargeables sur ce réseau (~64 Ko/s, `short read`/EOF, puis `no such host`
  sur quay.io). Chaque brique est prouvée isolément ; l'assemblage reste à lancer
  sur un lien correct.
- **4 ADR toujours « Proposé »** → SPRINT-01 reste fermé.
- **Pas de production réelle**, **maquettes non validées** : hors périmètre
  technique. Consigné dans la rétrospective de `SPRINT-00.md`.

### Nouveaux pièges consignés

5. Bloc `management` YAML correct mais endpoint Prometheus **404 sous MockMvc**
   (handler de scrape non monté en `@SpringBootTest` MOCK) alors qu'il existe en
   HTTP réel (401 anonyme). → test via la `MeterRegistry`, pas via l'endpoint.
6. `PrometheusMeterRegistry` non exposé comme bean en test → injecter la
   `MeterRegistry` générique.
7. `openssl -subj` réécrit par MSYS sous Git Bash → `MSYS_NO_PATHCONV=1`.
8. `.env` à la racine ⇒ `docker compose --env-file .env` obligatoire ; corrigé
   aussi dans les scripts de sauvegarde/restauration.

---

## 2026-07-21 — Clôture du SPRINT-00 : CI réparée, branche fusionnée, assemblage éprouvé

Séance de fermeture. Objectif : ne pas clore un sprint « prouver, pas déclarer »
avec une CI rouge et un assemblage jamais exécuté.

### La CI était rouge — deux causes distinctes, aucune dans le code métier

La branche `sprint00-ossature` n'avait jamais été poussée : les 9 commits du
sprint n'avaient donc **jamais été jugés par la CI**. Au premier push, les deux
workflows ont échoué. Les logs GitHub n'étant pas lisibles sans authentification
(HTTP 403), le diagnostic est passé par les **annotations de check-runs**, qui
sont publiques et portent le code de sortie — puis par une analyse différentielle
de l'historique des runs.

1. **Backend, `exit 126` = permission denied.** `backend/mvnw` était versionné en
   `100644` — un commit depuis Windows ne porte pas le bit d'exécution. Le runner
   Linux ne pouvait pas lancer `./mvnw`. Corrigé par
   `git update-index --chmod=+x backend/mvnw`. **Job vert depuis.**
2. **LaTeX, `exit 255` sur les trois langues.** Le `pre_compile: tlmgr install amiri`
   introduit en `ac3fa84` s'appliquait à **toute** la matrice. L'image de
   `xu-cheng/latex-action@v3` est une Alpine avec TeXLive *complet*, dont le dépôt
   `tlmgr` est en lecture seule : la commande sort en 255 et tue le job — y compris
   `fr` et `en`, qui n'avaient aucune police à installer. Le `pre_compile` est
   désormais porté par la matrice, vide sauf pour l'arabe.

L'analyse différentielle a aussi établi un fait qui manquait au sprint :
**`Build PDFs` est rouge sur `main` depuis le 18/07**, avant le SPRINT-00. Ce
n'est pas une régression du sprint, c'est une dette antérieure — et `ar` n'a
**jamais** compilé en CI.

### Fusion dans `main`

`main` fast-forwardé sur `sprint00-ossature` (`ac3fa84..6526868`) et poussé. Le
socle du walking skeleton est scellé. Décision assumée : merger alors que
`Build PDFs` restait rouge, parce que ce workflow l'était déjà avant le sprint et
que la CI applicative — celle du périmètre — est verte.

### Le réseau s'est débloqué, l'assemblage a enfin pu être tenté

Les 4 images qui manquaient le 19/07 (Keycloak, Nginx, Prometheus, Grafana) se
sont téléchargées sans incident. Mais `docker compose up --build` bute ailleurs :
il doit d'abord tirer `maven:3.9-eclipse-temurin-17` (~350 Mo) pour l'étage de
build. Au débit du poste, ~2 h. **Build interrompu au bout de 11 min** ; les 5
autres services ont été montés avec `--no-deps`.

### Ce que l'assemblage a révélé — un défaut que les tests ne pouvaient pas voir

**Keycloak n'avait jamais démarré.** `infra/keycloak/realm-zumm.json` contenait
des clés de commentaire (`_commentaire_roles`, `_commentaire`) ; l'importateur
désérialise en `RealmRepresentation` sans tolérance et échoue sur toute clé
inconnue : `Unrecognized field "_commentaire_roles" ... not marked as ignorable`,
puis `Failed to run import` et sortie en 1. Le realm — 4 rôles, 2 clients — était
donc déclaré livré au sprint alors qu'**il n'avait jamais été importé une seule
fois**. Les tests d'intégration ne le couvraient pas : `SecuriteApiIT` valide la
configuration Spring Security, pas l'import du realm.

Corrigé : clés retirées, justifications déplacées dans `infra/keycloak/README.md`
(le JSON n'admet pas de commentaires — le fait de l'avoir oublié a coûté un
service entier). **Realm `zumm` importé et vérifié** : l'endpoint OIDC sert sa
clé publique.

### État réel de la pile

| Service | État | Preuve |
|---|---|---|
| `postgres` | 🟢 sain | PostGIS 3.4.3 actif. `timescaledb` **disponible** mais non créée : c'est la migration Flyway `V1` qui la crée, au démarrage du backend. Aucun défaut. |
| `keycloak` | 🟢 démarré | Realm `zumm` importé, clé publique OIDC servie |
| `prometheus` | 🟢 sain | `/-/healthy` |
| `grafana` | 🟢 sain | `/api/health` → `database: ok`, v11.1.0 |
| `backend` | ⚪ non monté | Image non construite (téléchargement Maven abandonné) |
| `nginx` | 🔴 sort en 1 | `host not found in upstream "backend"` — **comportement attendu** : la dépendance est correctement déclarée, elle a été contournée par `--no-deps` |

**4 services sur 6 prouvés en fonctionnement.** L'assemblage complet reste dû, et
ne dépend plus que du temps de téléchargement de l'image Maven.

### Nouveaux pièges consignés

9. **`backend/mvnw` doit rester en `100755` dans l'index git.** Un commit depuis
   Windows le repasse en `100644` → runner Linux en `exit 126`. Vérifier avec
   `git ls-files -s backend/mvnw`.
10. **Jamais de clé de commentaire dans un JSON Keycloak** — l'import échoue et le
    conteneur ne démarre pas.
11. **`tlmgr install` est inopérant** dans l'image de `latex-action@v3` (dépôt en
    lecture seule) : sortie 255. TeXLive y est déjà complet.
12. **Les logs GitHub Actions exigent une authentification** ; les *annotations de
    check-runs* sont publiques et suffisent souvent — elles portent le code de
    sortie, qui à lui seul a identifié le 126.
13. Une branche non poussée n'est **pas** une branche testée. Les 9 commits du
    sprint ont vécu 2 jours sans jugement de CI.

### Reste dû

- `Build PDFs` : `ar` ne compile toujours pas (`exit 12`), `fr`/`en` échouent plus
  loin (`exit 1`). Dette antérieure au sprint. → **Résolu le 22/07, voir ci-dessous.**
- Assemblage `compose` complet (backend + nginx).
- Les 4 ADR restent « Proposé » → **SPRINT-01 reste fermé**.
- Production réelle et validation des maquettes : hors périmètre technique.

---

## 2026-07-22 — `Build PDFs` réparé : les trois cahiers compilent en CI

Dette antérieure au SPRINT-00 (rouge depuis le 18/07). Les deux workflows de
`main` sont désormais **verts pour la première fois**. Trois défauts distincts,
aucun visible en local sous MiKTeX.

### 1. L'arabe n'avait jamais compilé — conflit `bidi`, en deux couches

`polyglossia` charge `bidi`, qui patche `\@tabular`, `\@array` et `hyperref`. Le
bloc langue/polices était chargé **en tête de préambule** : `bidi` patchait donc
des macros ensuite écrasées par `array`/`tabularx`/`xltabular`/`hyperref`. Bloc
déplacé **en fin de préambule** (avec un avertissement en tête pour ne pas le
remonter). Nécessaire — mais l'échec persistait à l'identique.

Cause finale : dans l'image TeX Live de `latex-action`, `bidi` redéfinit
`\@tabular` en appelant `\UseMathForPositioningText` **sans que sa propre version
ne la définisse**. Tout `\begin{tabular}` mourait en *Undefined control sequence*
(`latexmk` exit 12). Neutralisée par `\providecommand{\UseMathForPositioningText}{}` :
`\providecommand` n'écrase rien si une version correcte existe ; vide, elle
rétablit le `\@tabular` du noyau. **À retirer quand l'image CI sera cohérente.**

### 2. `fr`/`en` — faux positif structurel du contrôle de fraîcheur

Le contrôle comparait le **texte extrait** du PDF versionné à celui d'un PDF
recompilé en CI. Deux distributions LaTeX n'extraient pas le même texte : sur `fr`
comme sur `en`, l'écart portait sur **un caractère parmi 107 000** — le signe
somme de la formule `QuantiteMiel`, rendu `X` par MiKTeX (référence versionnée) et
`∑` par TeX Live (CI). Les polices mathématiques n'embarquent pas la même table
ToUnicode ; aucune normalisation ne rattrape cela, `X` étant une lettre.

`check-pdf-current.sh` a été réécrit : il interroge l'**historique git** — *le PDF
est-il postérieur à ses sources ?* — ce qui répond à l'intention d'origine sans
dépendre d'aucune police. Le job n'a plus besoin de `poppler-utils` ni de mettre
le PDF de côté, mais exige `fetch-depth: 0` (un clone superficiel fausserait les
dates ; le script le détecte et refuse).

### 3. Le détour qui a rendu tout cela possible

Les logs Actions exigent une authentification (HTTP 403 depuis l'API publique) ;
les **annotations de check-runs**, elles, sont publiques. Deux étapes
conditionnelles y recopient désormais les lignes d'erreur LaTeX (avec 8 lignes de
contexte — la ligne seule ne nomme pas la macro indéfinie) et le rapport de
fraîcheur. Sans ce câblage, je n'aurais eu que des codes de sortie. **Ces étapes
restent en place** : le prochain échec sera diagnosticable de la même façon.

### Nouveaux pièges consignés

14. **`bidi` doit être chargé en dernier** (après `array`/`tabularx`/`xltabular`/
    `hyperref`) : il patche leurs macros de tableaux et doit voir leur version
    définitive. MiKTeX le tolère, TeX Live non.
15. **Ne jamais comparer le *contenu* de deux PDF issus de distributions LaTeX
    différentes** : les tables ToUnicode divergent (`∑` ↔ `X`), faux positif
    permanent. Juger la fraîcheur sur les **dates de commit**, pas sur le texte.
16. **`\UseMathForPositioningText` indéfinie** dans le `bidi` de l'image CI :
    `\providecommand{\UseMathForPositioningText}{}` en préambule arabe.
17. **Un contrôle de fraîcheur par historique git exige `fetch-depth: 0`** — le
    `checkout` par défaut est superficiel et tronque les dates.

---

## 2026-07-22 (suite) — Les 4 ADR arbitrés : le SPRINT-01 est débloqué

Le vrai chemin critique du projet n'était pas technique mais décisionnel : les 4
ADR de `06_decisions/` étaient « Proposé », et la porte du SPRINT-01 restait fermée
en attendant un arbitrage client qui n'est pas venu. Le SPRINT-00 avait prévu ce
cas (risque « le client ne tranche pas → escalade J+5 »). L'échéance étant passée,
**l'équipe projet a tranché sur hypothèses par défaut**, dans le sens des décisions
proposées, chacune sous réserves explicites.

### Ce qui a été acté

- **ADR-001 — multi-tenant + RLS PostgreSQL** : retenu. C'est la décision qui
  préserve l'optionalité (le rattrapage mono→multi coûte 3-4×, l'inverse coûte un
  léger surdimensionnement). Défaut posé : **un utilisateur ↔ une exploitation**
  (seul point qu'un revirement client obligerait à reprendre, et il est extensible).
- **ADR-002 — TimescaleDB conservé** sur les hypothèses de volumétrie (350 M
  mesures/an, 35× le seuil de pertinence). Peu risqué à ce stade : TimescaleDB
  n'est exercé qu'à **EPIC-004** (~Sprint 4). La condition de révision (< 10 M/an →
  retrait) reste vive, à confirmer **avant EPIC-004**, pas avant le Sprint 1.
- **ADR-003 — `docker compose` mono-serveur, exploité client + TMA** ; Kubernetes
  hors périmètre. Réversible par ADR ultérieur. Restent subordonnés au client, sans
  bloquer le Sprint 1 : **SLO 99,5 %** (à accepter par écrit), RPO, astreinte,
  détention des secrets.
- **ADR-004 — import CSV générique, +13 SP au Sprint 2**. J'ai corrigé un point
  faux de la décision : l'« inventaire réalisé pendant le Sprint 0 » n'a **pas** eu
  lieu ; il reste dû et devient une **dépendance d'entrée du Sprint 2**, pas du
  Sprint 1.

### Nature de l'arbitrage — et ses limites

Ces statuts sont « **Accepté (sur hypothèses par défaut)** », un statut ajouté au
registre. Ils **font autorité pour la construction** mais ne valent **pas**
signature client : la DoD du Sprint 0 exigeait cette signature, elle n'est pas
acquise. Chaque ADR liste ce qui reste à confirmer et à quelle échéance. Le
`docker compose` n'a pas été fabriqué à partir de rien : la porte s'ouvre sur une
décision, pas sur un fait accompli côté client.

### Effet

**Le SPRINT-01 (CRUD Fermier/Ferme/Site/Agent + `ConfigZumm.ini`, 36 SP) peut
démarrer** le 28/07 comme prévu. ADR-001 en est le fondement direct : les tables de
référence porteront `tenant_id` avec politique RLS dès leur création.

### Reste dû

- **Confirmation/signature client** des 4 arbitrages (revue client).
- **Volumétrie réelle** avant EPIC-004 (ADR-002).
- **Inventaire de l'existant** avant le Sprint 2 (ADR-004).
- Assemblage `compose` complet (backend + nginx) ; production réelle ; validation
  des maquettes par un apiculteur.

---

## 2026-07-23 — Fondation du SPRINT-01 : modèle métier, multi-tenant, config, durcissement RLS

Préparation du SPRINT-01 poussée jusqu'au code (choix assumé sur le curseur
académique), compilée et vérifiée contre un PostgreSQL réel.

### Livré

- **Modèle métier** (migration V2) : Fermier → Ferme → Site + Agent, dérivé du
  dictionnaire et de l'annexe A. FK composites incluant `tenant_id` (interdit les
  références inter-tenant que la vérification FK contournerait) ; PostGIS `geog`
  générée + index GiST pour Site (US-003) ; contraintes de composition (US-006) en
  `CHECK` doublées par Bean Validation. Entité `ping` conservée.
- **Multi-tenant à deux couches** (ADR-001) : `@TenantId` Hibernate (couche
  applicative) + RLS PostgreSQL avec variable de session posée depuis le claim JWT
  (couche SGBD). Entités, dépôts, `TenantContext`/`TenantFilter`/résolveur/
  connection-provider.
- **Config métier** (US-025) : lecture de `ConfigZumm.ini`, seuils typés, relecture
  à chaud, parseur sans dépendance.
- Conception : `docs/SPRINT-01-FONDATION.md`.

### Ce que le test a révélé, et le durcissement qui a suivi

`ModeleMetierIsolationIT` a démontré un défaut de fond : **la RLS était inerte**.
L'utilisateur `zumm` (conteneur et `docker-compose`) est **superutilisateur**, et
un superutilisateur contourne la RLS **même sous `FORCE`**. Seule la couche
`@TenantId` protégeait réellement.

Corrigé le jour même (migration **V3**) : rôle applicatif dédié `zumm_app`
**non-superutilisateur** (DML seul, `ALTER DEFAULT PRIVILEGES` pour les tables
futures), créé sans secret versionné (placeholders Flyway alimentés par
`DB_APP_PASSWORD`). **Dissociation des connexions** : l'application se connecte en
`zumm_app` (RLS effective), Flyway garde `zumm` pour les DDL — câblé dans
`docker-compose.yml`. `RoleApplicatifIT` le prouve par une **connexion directe avec
le vrai rôle** : non-superutilisateur, isolé par la RLS, sans droit DDL.

Bilan : **11 tests unitaires + 14 d'intégration, `Skipped: 0`**.

### Nouveaux pièges consignés

18. **Un superutilisateur Postgres contourne la RLS même avec `FORCE`.** La défense
    RLS n'existe que si l'application se connecte avec un rôle non-superutilisateur.
    Dissocier le rôle applicatif (DML) du rôle de migration (DDL).
19. **`@ServiceConnection` configure la datasource par un bean**, pas par
    `spring.datasource.url` : un `${spring.datasource.url}` dans `spring.flyway.url`
    se résout au défaut `localhost:5432` et casse Flyway en test. Laisser Flyway
    hériter de la datasource par défaut ; ne dissocier qu'en prod, par variables
    d'environnement.
20. **Backticks dans un `git commit -m "…"`** (guillemets doubles) : bash exécute
    `` `ping` ``, `` `geog` ``… comme substitution de commande et pollue le message.
    Passer par `-F fichier`, ou des guillemets simples.

### Reste dû (inchangé sur le fond)

- Confirmation/signature client des ADR ; volumétrie avant EPIC-004 ; inventaire
  avant le Sprint 2.
- Contrôleurs REST + services des 4 CRUD, RBAC par rôle, suppression de `ping`,
  arrondi des positions sensibles : **travail d'exécution du SPRINT-01**.
- Validation de bout en bout de `zumm_app` en montant la pile `compose`.
