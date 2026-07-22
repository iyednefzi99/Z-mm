# Fondation technique du SPRINT-01

Ce document décrit la **fondation** posée avant l'exécution du SPRINT-01 (CRUD
Fermier/Ferme/Site/Agent + `ConfigZumm.ini`). Il ne remplace pas
`roadmap/operationnel/02_sprints/SPRINT-01.md` (le plan Scrum) : il documente le
**socle de code** sur lequel les user stories viennent se brancher, et les
décisions d'ingénierie qu'il matérialise.

Périmètre livré ici : modèle de données, multi-tenant (`tenant_id` + RLS), lecture
de `ConfigZumm.ini`. **Hors périmètre** (travail du sprint) : les contrôleurs REST,
les services applicatifs et la validation métier fine, US par US.

---

## 1. Modèle de données (migration `V2__modele_metier_sprint01.sql`)

Dérivé du dictionnaire de données (cahier, chapitre 5) et de l'annexe A. Hiérarchie
`Fermier → Ferme → Site`, plus `Agent`.

| Table | Rôle | Points notables |
|---|---|---|
| `fermier` | Propriétaire exploitant (US-001) | Racine de la hiérarchie |
| `ferme` | Exploitation d'un fermier (US-002) | FK composite `(fermier_id, tenant_id)` |
| `site` | Emplacement géolocalisé (US-003) | `latitude`/`longitude`/`altitude` + colonne PostGIS `geog` générée, index GiST |
| `agent` | Intervenant et rôle (US-005) | Rôle contraint par `CHECK`, aligné sur Keycloak |

**Choix structurants :**

- **`tenant_id` sur chaque table** (ADR-001), non nul.
- **Clés étrangères composites incluant `tenant_id`** : `ferme.(fermier_id, tenant_id)
  → fermier.(id, tenant_id)`. La vérification d'intégrité référentielle de Postgres
  contourne la RLS ; sans cette composition, un tenant pourrait référencer l'entité
  d'un autre. La contrainte l'interdit au niveau du SGBD.
- **PostGIS (US-003)** : `latitude`/`longitude` restent la vue métier ; une colonne
  `geog geography(Point,4326)` **générée** (`STORED`) en dérive pour les requêtes
  spatiales (proximité, emprise), indexée en GiST. Aucune dépendance
  `hibernate-spatial` n'est requise côté Java tant qu'on ne requête pas le spatial
  depuis JPA — à ajouter le jour où une story le demande.
- **Contraintes de composition (US-006)** exprimées en `CHECK` : bornes de
  coordonnées, altitude plausible, ordre des dates de cycle de vie du site, énumération
  des rôles, libellés non vides. Doublées par Bean Validation côté entités pour un
  rejet précoce et un message clair.
- **`ping` (walking skeleton) est conservée** : elle prouve toujours la chaîne de
  bout en bout et ne porte pas de tenant. Sa suppression est une tâche de nettoyage
  du sprint, volontairement hors de cette migration de fondation.

---

## 2. Multi-tenant : `tenant_id` + RLS (ADR-001)

Isolation **à deux couches**, conformément à l'ADR : la discipline applicative ne
doit jamais être l'unique garde-fou.

```
Requête HTTP
  └─ JWT (claim tenant_id)                     ← Keycloak, realm unique
       └─ TenantFilter        → TenantContext (ThreadLocal)
            ├─ TenantIdentifierResolver  → discriminant Hibernate @TenantId  (couche 1)
            └─ TenantConnectionProvider  → SET app.current_tenant → RLS PostgreSQL (couche 2)
```

| Composant | Rôle |
|---|---|
| `TenantContext` | Porte le tenant courant pour le thread ; nettoyé en fin de requête |
| `TenantFilter` | Lit le claim `tenant_id` du JWT après authentification, alimente le contexte |
| `TenantIdentifierResolver` | Fournit le tenant à Hibernate (`@TenantId` : pose à l'insert, filtre en lecture) |
| `TenantConnectionProvider` | Positionne `app.current_tenant` sur la connexion, via `set_config(?, false)` en requête préparée |
| `MultiTenancyHibernateConfig` | Enregistre explicitement les deux briques auprès d'Hibernate |
| `EntiteTenant` | Superclasse mappée : `id`, `@TenantId tenantId`, `creeLe`, `majLe` |

**Couche 1 — discriminant `@TenantId`** : Hibernate ajoute `tenant_id = ?` à chaque
lecture/écriture et pose la valeur à l'insertion. Le code applicatif ne manipule
jamais `tenant_id`.

**Couche 2 — RLS PostgreSQL** : chaque table métier a `ENABLE` **et** `FORCE ROW
LEVEL SECURITY` (le `FORCE` est indispensable : le propriétaire de la table, notre
utilisateur applicatif, contournerait sinon la RLS). La politique filtre sur
`current_setting('app.current_tenant', true)`. Hors contexte tenant, la variable
est absente → **zéro ligne** (refus par défaut).

> ⚠️ **Contrainte de conception à connaître.** La variable de session est fixée à
> l'acquisition de la connexion, à partir du tenant courant à cet instant. Une unité
> de travail scoppée à un tenant doit donc **commencer sa transaction dans le bon
> contexte** ; changer de tenant au milieu d'une transaction ne repositionne pas la
> variable. En pratique : une opération = une transaction ouverte sous le bon tenant
> (`TenantContext.executer(...)` pour les usages hors requête HTTP).

**Preuve (`ModeleMetierIsolationIT`)** — trois tests contre un Postgres réel :
`tenant_id` posé automatiquement ; un tenant ne voit pas les fermiers d'un autre
(couche 1) ; et, **sous un rôle non-superutilisateur**, la RLS masque les lignes
d'un autre tenant (couche 2). C'est le test d'isolation inter-tenant exigé par
l'ADR-001.

### Durcissement du rôle applicatif (livré) — la RLS rendue effective

Un **superutilisateur Postgres contourne toujours la RLS**, même avec `FORCE`. Or
l'utilisateur `zumm` du conteneur et de `docker-compose` est superutilisateur :
tant que l'application s'y connecte, la couche 2 est inerte. Corrigé (migration
`V3__role_applicatif_rls.sql`) :

| Rôle | Usage | Privilèges |
|---|---|---|
| `zumm` (propriétaire, superutilisateur) | **Migrations Flyway** uniquement (DDL) | tout |
| `zumm_app` (**non-superutilisateur**) | **Application** (Hibernate/Hikari) | DML seul, soumis à la RLS |

- **V3** crée `zumm_app` (idempotent, `NOSUPERUSER`, `LOGIN`), lui accorde
  `SELECT/INSERT/UPDATE/DELETE` sur les tables et `ALTER DEFAULT PRIVILEGES` pour
  les tables futures. Rôle et mot de passe via **placeholders Flyway** — aucun
  secret versionné ; `DB_APP_PASSWORD` alimente à la fois la création du rôle et
  la source de données applicative.
- **Dissociation des connexions** : en production, l'application se connecte en
  `zumm_app` (`SPRING_DATASOURCE_USERNAME`), tandis que **Flyway** garde `zumm`
  (`SPRING_FLYWAY_USER/URL/PASSWORD`) pour les DDL. Câblé dans `docker-compose.yml`.
- **Preuve** (`RoleApplicatifIT`) : connexion **directe avec le vrai rôle
  `zumm_app`** — il est bien non-superutilisateur, la RLS l'isole réellement entre
  tenants, et il ne peut pas faire de DDL (moindre privilège).

> ℹ️ **En test et en dev local**, l'application se connecte encore en `zumm`
> (Testcontainers `@ServiceConnection`, défaut local) : la couche 1 (`@TenantId`)
> protège, et `RoleApplicatifIT` prouve la couche 2 sur le rôle réel. C'est en
> **production** (compose) que l'application bascule sur `zumm_app`. La validation
> de bout en bout de ce câblage se fait en montant la pile complète.

---

## 3. Configuration métier `ConfigZumm.ini` (US-025)

Configuration **externe au code**, modifiable sans recompilation ni redémarrage.

| Composant | Rôle |
|---|---|
| `LecteurIni` | Parseur INI sans dépendance : sections, `clé = valeur`, commentaires `;`/`#` |
| `SeuilsMetier` | Vue immuable typée des seuils, avec défauts alignés sur le gabarit versionné |
| `ConfigurationMetier` | Charge le fichier, expose les seuils, **relit à chaud** quand le fichier change |

- Chemin configurable (`zumm.config-metier.chemin`, défaut `config/ConfigZumm.ini`).
- **Fichier absent → défauts** et l'application démarre quand même : un poste de dev
  n'a pas à fournir le fichier.
- **Relecture à chaud** : une tâche planifiée recharge le fichier quand sa date de
  modification change (intervalle réglable, `zumm.config-metier.intervalle-relecture-ms`).
- **Aucun secret** dans ce fichier : identifiants et mots de passe passent par
  l'environnement.

---

## 4. Ce qui reste à faire dans le sprint

La fondation s'arrête au socle. Restent, US par US, le travail d'exécution :

- **Contrôleurs REST + services** pour les 4 CRUD (US-001/002/003/005), avec DTO,
  pagination et gestion d'erreurs.
- **Matrice RBAC par rôle** (au-delà du « fermé par défaut » déjà en place) :
  quel rôle peut créer/lire/modifier/supprimer quoi.
- **Arrondi des positions** des sites pour les profils non propriétaires
  (`arrondi_degres_public`), donnée sensible.
- **Suppression de `ping`** (entité et code du walking skeleton) et de son test.
- **Exposition des seuils** via l'API si une story le requiert.
- **Validation de bout en bout** du rôle `zumm_app` en montant la pile `compose`
  complète (le rôle et son isolation sont déjà prouvés par `RoleApplicatifIT` ;
  reste à confirmer le câblage applicatif en conditions réelles).

## 5. Rappels de vérification

```bash
cd backend && ./mvnw -B verify      # unitaires + ITs (Docker requis)
```

L'isolation inter-tenant est prouvée par `ModeleMetierIsolationIT`, la lecture de
configuration par `ConfigurationMetierTest`. Le garde-fou CI `Skipped: 0` sur les
tests d'intégration reste en vigueur.
