# Sécurité de Zümm — audit par couche

> **À quoi sert ce document.** Répondre à « le projet couvre-t-il tous les niveaux
> de sécurité ? » autrement que par oui. Une couche par section : ce qui est en
> place, **avec le fichier à l'appui**, puis ce qui manque. Les écarts résiduels
> sont écrits, pas maquillés — une revue de sécurité qui ne liste que ses réussites
> ne prouve rien.
>
> Les invariants à ne jamais défaire sont dans [`CLAUDE.md`](../CLAUDE.md) ; les
> décisions structurantes dans [`roadmap/operationnel/06_decisions/`](../roadmap/operationnel/06_decisions/).
>
> **Dernière revue :** 2026-07-26, sur l'état de la branche `main` au SPRINT-18.
> Les chiffres de ce document ont été recomptés sur le code à cette date.

---

## Vue d'ensemble

Sept couches, du réseau à la chaîne d'approvisionnement. Le principe qui les relie
est la **défense en profondeur** : aucune n'est supposée tenir seule.

| # | Couche | État | Écart résiduel |
|:---:|:---|:---:|:---|
| 1 | Réseau et transport | 🟢 | HSTS non préchargé (`preload`) |
| 2 | Authentification | 🟢 | Flux OIDC jamais joué en CI |
| 3 | Autorisation | 🟢 | — |
| 4 | Données et isolation | 🟢 | Pas de chiffrement au repos applicatif |
| 5 | Application | 🟡 | `motif` de refus non borné en longueur |
| 6 | Chaîne d'approvisionnement | 🟢 | Vulnérabilités de développement non bloquantes (assumé) |
| 7 | Observabilité et audit | 🟢 | Pas d'alerte sur anomalie d'accès |

Aucune couche n'est en rouge. Deux points appellent une décision plutôt qu'un
correctif : le chiffrement au repos (couche 4) et le DAST en continu (§ 8).

---

## 1. Réseau et transport

**Terminaison TLS au proxy inverse** — [`infra/nginx/nginx.conf`](../infra/nginx/nginx.conf).

| Mesure | Valeur | Ligne |
|:---|:---|:---|
| Protocole | **TLS 1.3 uniquement** — pas de repli 1.2 | `ssl_protocols TLSv1.3` |
| Suites | `TLS_AES_256_GCM_SHA384`, `TLS_CHACHA20_POLY1305_SHA256`, `TLS_AES_128_GCM_SHA256` | `ssl_conf_command Ciphersuites` |
| Tickets de session | **désactivés** — ils affaiblissent la confidentialité persistante | `ssl_session_tickets off` |
| Redirection | tout le port 80 en 301 vers HTTPS | `return 301 https://...` |
| Bannière serveur | masquée | `server_tokens off` |

**En-têtes de sécurité**, tous en `always` (donc émis aussi sur les réponses
d'erreur, où on les oublie le plus souvent) :

- `Strict-Transport-Security: max-age=31536000; includeSubDomains` — également
  émis par l'application (`SecurityConfig#appliquerCommun`), le TLS étant terminé
  en amont ;
- `Content-Security-Policy` — `default-src 'self'`, `object-src 'none'`,
  `base-uri 'self'`, `form-action 'self'`, `frame-ancestors 'none'`,
  `upgrade-insecure-requests`. **`script-src 'self'` sans `unsafe-inline`** :
  l'ajouter serait un retour en arrière, jamais un correctif ;
- `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`,
  `Referrer-Policy: same-origin` ;
- `Permissions-Policy` — géolocalisation et caméra limitées à l'origine,
  micro/paiement/USB refusés, `interest-cohort` désactivé ;
- `Cross-Origin-Opener-Policy` et `Cross-Origin-Resource-Policy: same-origin`.

**Limitation de débit**, deux zones distinctes — c'est le point qui compte :

```
limit_req_zone $binary_remote_addr zone=api:10m  rate=20r/s;   # API métier
limit_req_zone $binary_remote_addr zone=auth:10m rate=2r/s;    # authentification
```

`/oauth2/`, `/login/` et `/realms/` sont sur la zone `auth` (2 r/s, rafale 10) :
une attaque par force brute sur la connexion ne bénéficie pas du débit accordé à
l'API. **La console d'administration Keycloak (`/admin/`) et les endpoints Actuator
autres que `health` rendent 404**, pas 403 : ne pas confirmer leur existence.

> **`style-src` conserve `'unsafe-inline'`.** MapLibre injecte ses styles de
> contrôle à l'exécution. L'impact est borné — une injection de style ne s'exécute
> pas — mais le point est connu et non refermé.

**Écart résiduel.** HSTS n'a pas la directive `preload` et le domaine n'est pas
inscrit sur la liste des navigateurs : la toute première visite d'un poste neuf
reste théoriquement interceptable. À traiter au moment de la mise en production,
pas avant — `preload` est difficile à défaire.

## 2. Authentification

Deux populations, **deux chaînes de filtres**, une seule matrice de règles —
[`backend/src/main/java/com/zumm/config/SecurityConfig.java`](../backend/src/main/java/com/zumm/config/SecurityConfig.java).

| | Machines (passerelles, intégrations) | Navigateurs |
|:---|:---|:---|
| Porteur | jeton `Bearer` | cookie de session `HttpOnly` |
| État | `STATELESS` | session serveur persistée (`V17`) |
| CSRF | **désactivé** — aucun cookie, donc rien à forger | **actif** — le navigateur envoie le cookie tout seul |
| Aiguillage | `RequestMatcher` sur l'en-tête `Authorization` | tout le reste |

Le risque d'une telle séparation est la **dérive** : deux chaînes, et un jour l'une
oublie une règle. La matrice RBAC est donc écrite **une fois**
(`SecurityConfig#matriceRbac`) et appliquée aux deux ; un test le vérifie sur les
deux chemins.

**BFF (US-073, SPRINT-16, [ADR-006](../roadmap/operationnel/06_decisions/ADR-006-stockage-des-jetons.md)).**
Le navigateur ne détient plus aucun jeton : l'échange du code, le rafraîchissement
et la révocation se passent entre le back-end et Keycloak. Une XSS ne peut plus
exfiltrer de session réutilisable ailleurs. Le jeton CSRF, lui, est délibérément
dans un cookie **lisible par le script** (`withHttpOnlyFalse`) — ce n'est pas un
secret, c'est une preuve que la requête vient de notre page.

**Validation du jeton** — `SecurityConfig#decodeurDeJeton` :
signature, expiration, émetteur (défauts Spring) **plus l'audience**
([`ValidateurAudience`](../backend/src/main/java/com/zumm/config/ValidateurAudience.java)).
Sans elle, un jeton émis pour n'importe quel autre client du royaume — dont
`account`, livré par défaut — passerait : bon émetteur, signature valide.

**Déconnexion propagée** ([`DeconnexionOidc`](../backend/src/main/java/com/zumm/config/DeconnexionOidc.java)) :
effacer la seule session locale laisserait la session SSO ouverte, et un simple
retour sur la page reconnecterait silencieusement.

**Écart résiduel.** Le flux OIDC n'est **toujours pas joué en CI** : les tests
utilisent un Keycloak simulé. C'est exactement l'angle mort qui avait laissé passer
l'absence de rafraîchissement de jeton jusqu'au SPRINT-11. Ouvert depuis.

## 3. Autorisation

Trois mécanismes, à trois profondeurs différentes. Ils ne se remplacent pas.

**a) RBAC — refus par défaut.** `anyRequest().hasAnyRole("apiculteur",
"superviseur", "responsable", "admin")`, **jamais `.authenticated()`**. Un jeton
valide sans rôle métier — compte de service, capteur — n'atteint aucun endpoint.
Le client machine `zumm-capteur` porte le rôle `capteur`, qui n'ouvre que
`POST /api/mesures`.

**b) Isolation de tenant** — [`TenantFilter`](../backend/src/main/java/com/zumm/tenant/TenantFilter.java).
Le claim `tenant_id` est **obligatoire** : une identité sans lui reçoit **403**, pas
une liste vide. Le défaut inverse a réellement existé (SPRINT-12) et se déguisait
en base vide. Le claim est lu sur les deux porteurs, `Jwt` et `OidcUser`, via le
point unique [`IdentiteAppelant`](../backend/src/main/java/com/zumm/securite/IdentiteAppelant.java).

**c) Portée par affectation d'agent** (US-057, SPRINT-16) —
[`FiltrePortee`](../backend/src/main/java/com/zumm/securite/FiltrePortee.java),
[`ResolveurPortee`](../backend/src/main/java/com/zumm/securite/ResolveurPortee.java),
migration `V16`. La règle est posée **dans le SGBD**, pas dans les services : un
`WHERE` par requête serait un `WHERE` à oublier. Le tenant et la portée sont posés
**dans la même requête préparée** — les dissocier ouvrirait une fenêtre pendant
laquelle une requête verrait toute l'exploitation.

> **L'absence de portée vaut « rien voir », jamais « tout voir ».** C'est le mode
> de défaillance qui compte : une tâche planifiée, une connexion du pool reprise
> hors contexte ou un test mal isolé ne doivent pas ouvrir le parc.

**d) Confidentialité des positions** —
[`PolitiquePositions`](../backend/src/main/java/com/zumm/securite/PolitiquePositions.java).
Le vol de ruches est le premier sinistre du métier : arrondi des coordonnées selon
le rôle, altitude masquée, distances des voisins dégradées à 100 m (une distance
précise depuis un site connu se trilatère). **Tout nouveau DTO portant des
coordonnées doit y passer.**

## 4. Données et isolation

**RLS multi-tenant** ([ADR-001](../roadmap/operationnel/06_decisions/ADR-001-multi-tenant.md)) :
**18 tables sous `ENABLE ROW LEVEL SECURITY`, 24 politiques** réparties sur les
17 migrations Flyway. Le rôle applicatif `zumm_app` **n'est pas superutilisateur**
et ne possède pas les tables — sans quoi il contournerait la RLS (`V3`).

**Clés étrangères composites `(id, tenant_id)`** : c'est ce qui empêche un
rattachement inter-tenant même quand la vérification de clé contourne la RLS.
Règle applicable à toute nouvelle table métier.

**Deux exceptions, toutes deux motivées dans le code :**

1. `SPRING_SESSION` / `SPRING_SESSION_ATTRIBUTES` (`V17`) — ni `tenant_id` ni RLS.
   Une session est créée **avant qu'aucun tenant ne soit connu** : le tenant se lit
   dans le jeton, donc après l'ouverture de session. L'isolation tient à
   l'identifiant aléatoire de 36 caractères transmis par cookie `HttpOnly`.
2. `Photo` et `LotComposition` n'héritent pas d'`EntiteTenant` — elles ne se
   modifient jamais, donc n'ont pas de `maj_le`. Elles restent rattachées à un
   parent filtré.

**Garde-fous d'exécution** posés sur le rôle applicatif (`V13`) :
`statement_timeout`, `lock_timeout`, `idle_in_transaction_session_timeout`. Une
requête qui dérape ne monopolise pas la base.

**Écart résiduel — chiffrement au repos.** Il n'y en a pas au niveau applicatif :
aucune colonne n'est chiffrée. Les positions GPS des ruchers, qui sont l'actif
sensible du métier, sont donc en clair pour qui accède au volume ou à une
sauvegarde. La protection actuelle est le chiffrement de volume de l'hébergeur et
le contrôle d'accès au SGBD. **C'est une décision à prendre, pas un oubli** :
chiffrer les coordonnées applicativement interdirait les requêtes PostGIS —
regroupement spatial, plus proches voisins, ordre de tournée — c'est-à-dire tout
l'EPIC-012. À trancher par ADR avant la mise en production.

## 5. Application

**Validation d'entrée.** Bean Validation sur les DTO, `@Valid` sur les endpoints de
mutation de **12 des 22 contrôleurs** — les dix autres n'exposent que des lectures
ou des actions sans corps.

**Gestion d'erreurs centralisée** —
[`GestionnaireExceptions`](../backend/src/main/java/com/zumm/web/GestionnaireExceptions.java),
réponses au format `ProblemDetail` (RFC 7807). Le point de sécurité est la
**non-fuite** : pas de trace d'exécution, pas de nom de table, pas de requête SQL
dans la réponse.

**Idempotence** (US-055) — `FiltreIdempotence` + `MagasinIdempotence` + table
`requete_idempotente` **sous RLS**. Trois comportements : clé inconnue → traitement
et mémorisation (des seules réponses **réussies**) ; clé connue et même empreinte →
rejeu ; clé connue et empreinte différente → **409**, parce que c'est un bug client.

**Injection SQL.** Les requêtes natives (PostGIS, TimescaleDB) passent par des
paramètres liés ; aucune concaténation de chaîne dans une requête. CodeQL couvre
cette classe de défaut en continu (§ 6).

**Écart résiduel.** `PlanningController#refuser` reçoit `DecisionCorps` **sans
`@Valid`**. La règle métier tient — `PlanningService#refuser` refuse un motif vide
— mais la **longueur** du motif n'est bornée nulle part : un superviseur
authentifié peut écrire un motif arbitrairement long en base. Impact faible
(acteur déjà authentifié et autorisé), correctif simple (`@Size`), non traité ici
pour ne pas mêler un changement de contrat à cet audit.

## 6. Chaîne d'approvisionnement

Quatre gardes dans [`.github/workflows/ci.yml`](../.github/workflows/ci.yml) (job
`securite`) et un workflow dédié.

| Garde | Outil | Portée | Bloquant |
|:---|:---|:---|:---:|
| Secrets versionnés | gitleaks | historique complet (`fetch-depth: 0`) | ✅ |
| Dépendances Java | SBOM CycloneDX → **OSV** | arbre Maven réellement résolu | ✅ |
| Dépendances navigateur | `npm audit --omit=dev` | ce qui part chez l'utilisateur | ✅ |
| Dépendances de développement | `npm audit` | ESLint, Vitest | ⬜ informatif |
| Configuration d'infrastructure | Trivy `config` | Dockerfiles, compose, manifestes | ✅ |
| **Images construites** | Trivy `image` | distribution de base et paquets système | ✅ |
| Licences | `license-checker` | détection AGPL | ✅ |
| **Code source** | **CodeQL** `security-and-quality` | Java, TypeScript, **Python** | ✅ |

**Le point de méthode qui a coûté le plus cher** (SPRINT-12) : *ne pas faire
résoudre l'arbre de dépendances par le scanner*. Maven l'a déjà fait. Dependency-
Check bloquant sans clé NVD échouait en 85 s pour une cause étrangère au code ; OSV
lisant `pom.xml` se faisait limiter en 429 par Maven Central. Le SBOM produit
localement rend un verdict en quelques millisecondes, sans dépendre d'un registre
tiers. Le garde ainsi réparé a immédiatement trouvé **29 vulnérabilités, dont trois
entre 9.1 et 9.6**.

**Scan d'image (ajouté à cette revue).** Ni le SBOM, ni `npm audit`, ni
`scan-type: config` ne lisent ce que les images **contiennent** une fois
construites : `eclipse-temurin:17-jre-jammy`, `nginx:alpine`, leurs paquets
système. C'est la couche qui vieillit toute seule — une image figée accumule les
CVE de son OS sans qu'une ligne du projet ne change. `ignore-unfixed: true` est
délibéré : bloquer sur une vulnérabilité sans correctif disponible rendrait la
chaîne rouge sans action possible, et l'équipe finirait par neutraliser la porte.

**Actions tierces épinglées sur un commit**, pas sur une étiquette : une étiquette
se repointe, et une action tierce qui s'exécute dans le job de sécurité est
exactement l'endroit où l'on ne veut pas de contenu mouvant.

**Secrets d'exécution.** Aucun secret en dur : `infra/docker-compose.yml` utilise
partout la forme `${VAR:?message}` — le compose **échoue au démarrage** si la
variable manque, au lieu de partir sur une valeur par défaut. **Cinq** variables
sont concernées, pour huit occurrences : `DB_PASSWORD` (trois services),
`DB_APP_PASSWORD`, `KC_ADMIN_PASSWORD`, `ZUMM_BFF_SECRET`, `GRAFANA_PASSWORD`.

## 7. Observabilité et audit

**Journal d'audit applicatif** (US-043) —
[`AuditAspect`](../backend/src/main/java/com/zumm/config/AuditAspect.java) :
un aspect intercepte `creer`, `mettreAJour` et `supprimer` des services métier et
dépose une entrée **dans la même transaction** que l'opération, donc atomique avec
elle. Table `audit` sous RLS (`V12`), consultation réservée à `responsable` et
`admin`.

> **Défaut trouvé pendant cette revue, et corrigé.** `AuditAspect` ne lisait que le
> `Jwt` et retombait sur `Authentication#getName()` — lequel rend le `sub` pour une
> session de navigateur. Depuis le BFF (SPRINT-16), le journal inscrivait donc un
> **UUID pour les utilisateurs humains** et un nom d'utilisateur pour les machines,
> soit l'inverse de ce qui est utile pendant un incident. C'est exactement la
> dérive que la Javadoc d'`IdentiteAppelant` annonçait : *« un jour l'un des deux
> serait oublié »*. L'aspect passe désormais par ce point unique
> (`IdentiteAppelant#nomPourAudit`), et
> [`IdentiteAppelantTest`](../backend/src/test/java/com/zumm/securite/IdentiteAppelantTest.java)
> vérifie la parité des deux chaînes.

**Métriques et supervision** : Prometheus + Grafana (`infra/`). L'endpoint
`/actuator/health` est le seul exposé au travers du proxy ; tout le reste
d'Actuator rend 404.

**Écart résiduel.** Le journal enregistre, personne ne le regarde en continu :
aucune alerte n'est déclenchée sur une anomalie d'accès (rafale de suppressions,
consultation massive de sites par un compte peu actif). Un journal qu'on ne lit
qu'après coup ne détecte rien — il documente.

---

## 8. Test dynamique (DAST)

Les couches 1 à 7 sont vérifiées par lecture de code, tests et analyse statique.
**Rien de tout cela ne prouve le comportement de l'application en marche.** Le DAST
comble cet écart : il attaque la pile réellement démarrée.

`nikto` n'est pas installé sur le poste de développement de référence (Windows,
pas de paquet WSL) ; la procédure retenue est **OWASP ZAP par conteneur**, qui n'a
aucun prérequis hors Docker.

```bash
# 1. Monter la pile complète (le .env est à la RACINE)
docker compose --env-file .env -f infra/docker-compose.yml up -d --build

# 2. Balayage passif — aucune requête offensive, sûr à jouer n'importe quand
mkdir -p rapports/dast
docker run --rm --network host \
  -v "$PWD/rapports/dast:/zap/wrk:rw" \
  ghcr.io/zaproxy/zaproxy:stable \
  zap-baseline.py -t https://localhost -r zap-baseline.html -I

# 3. Balayage actif — envoie de VRAIES charges utiles (injection, XSS, traversée)
#    À ne jouer QUE sur un environnement jetable, jamais sur des données réelles.
docker run --rm --network host \
  -v "$PWD/rapports/dast:/zap/wrk:rw" \
  ghcr.io/zaproxy/zaproxy:stable \
  zap-full-scan.py -t https://localhost -r zap-full.html -I
```

**Ce que le balayage passif doit confirmer** — c'est le point : il rejoue de
l'extérieur ce que le § 1 affirme de l'intérieur.

| Contrôle | Attendu |
|:---|:---|
| En-têtes de sécurité | CSP, HSTS, `nosniff`, `X-Frame-Options`, `Referrer-Policy` présents sur **toutes** les réponses, erreurs comprises |
| Bannière serveur | pas de version nginx (`server_tokens off`) |
| Cookies | session `HttpOnly` + `Secure` + `SameSite` ; jeton CSRF lisible **et c'est voulu** |
| Surface exposée | `/admin/` et `/actuator/*` (hors `health`) en **404** |
| Contenu mixte | aucun — `upgrade-insecure-requests` |

**Faux positifs attendus, à ne pas « corriger ».** ZAP signale `style-src
'unsafe-inline'` (requis par MapLibre, § 1) et l'absence de `HttpOnly` sur le
cookie CSRF (délibéré, § 2). Les documenter dans le rapport plutôt que durcir à
l'aveugle.

**Pourquoi ce n'est pas dans la CI.** Un balayage utile exige la pile entière —
PostgreSQL, Keycloak, back-end, front-end, proxy — soit plusieurs minutes de
démarrage à chaque `push`, pour un résultat qui ne change qu'au rythme de la
configuration du proxy. **Décision : exécution manuelle avant chaque release**, et
rapport archivé dans `rapports/dast/`. À rejuger le jour où un environnement de
pré-production permanent existe : le brancher dessus en nocturne coûterait alors
presque rien.

---

## 9. Ce qui reste à décider

| Point | Nature | Proposition |
|:---|:---|:---|
| Chiffrement au repos des positions GPS | **arbitrage** — incompatible avec les requêtes PostGIS de l'EPIC-012 | ADR avant mise en production |
| DAST en continu | **arbitrage** — suppose un environnement permanent | à rejuger quand la pré-production existera |
| Flux OIDC joué en CI | dette, ouverte depuis le SPRINT-11 | conteneur Keycloak dans la campagne d'intégration |
| Alerte sur anomalie d'accès | manque | règle Prometheus sur le taux d'écriture du journal d'audit |
| `HSTS preload` | manque | au déploiement du domaine définitif, pas avant |
| `@Size` sur le motif de refus | manque, impact faible | à joindre au prochain passage sur `PlanningController` |
| `style-src 'unsafe-inline'` | contrainte MapLibre | à rejuger si MapLibre expose une option de style externe |
