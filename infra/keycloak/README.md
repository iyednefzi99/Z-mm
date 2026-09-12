# Realms Keycloak — `realm-zumm.json` et `realm-zumm.dev.json`

> ⚠️ **Ne jamais ajouter de clés de commentaire dans le JSON.** L'importateur de
> Keycloak désérialise le fichier en `RealmRepresentation` sans tolérance : toute
> clé inconnue — y compris un `"_commentaire"` — fait échouer l'import **et le
> démarrage du conteneur** avec `Unrecognized field ... not marked as ignorable`.
> Le format JSON n'admet pas de commentaires ; ce fichier les accueille à leur place.

## Deux fichiers, un seul realm `zumm`

| Fichier | Monté par | Contenu |
|---|---|---|
| `realm-zumm.json` | `infra/docker-compose.yml` | Realm de référence. **Aucun compte humain** : seul le compte de service du BFF y figure. |
| `realm-zumm.dev.json` | surcouche `infra/docker-compose.dev.yml` | Même realm, plus quatre comptes de test (un par rôle) et les secrets de développement. |

Les deux s'importent sous le même nom (`zumm`) : la surcouche de développement
**remplace** le montage, elle ne s'y ajoute pas. Sans elle, la pile démarre sur le
realm de référence et aucun compte ne permet de se connecter — c'est ce que gère
`scripts/demarrer.ps1`.

## Rôles métier

`apiculteur`, `superviseur`, `responsable`, `admin` — profils repris du cahier des
charges (US-005). La matrice RBAC profils × fonctions est arrêtée au SPRINT-01 :
ces rôles en sont le support, pas la définition. S'y ajoute `capteur`, rôle
machine qui n'ouvre que le dépôt de mesures.

## Clients

| Client | Nature | Justification |
|---|---|---|
| `zumm-bff` | confidentiel, `standard` + `direct access`, compte de service | Le back-end mène lui-même le flux OIDC et garde les jetons côté serveur ([ADR-006](../../roadmap/operationnel/06_decisions/ADR-006-stockage-des-jetons.md), [ADR-009](../../roadmap/operationnel/06_decisions/ADR-009-connexion-dans-l-application.md)). Son secret vient de l'environnement (`ZUMM_BFF_SECRET`) et doit être régénéré en production. |
| `zumm-backend` | `bearer-only` | Serveur de ressources : il valide les jetons, il n'en émet aucun. |
| `zumm-capteur` | confidentiel, `client_credentials` seul | Client machine porteur du rôle `capteur` : ni flux navigateur, ni mot de passe utilisateur. |
| `zumm-frontend` | public, PKCE obligatoire | **Vestige du flux navigateur d'avant l'ADR-006.** Aucun secret ne peut être gardé dans un navigateur : un client confidentiel y serait un secret publié. Conservé dans le realm, mais la PWA ne l'utilise plus — elle ne voit qu'un cookie de session. |

## Comptes de test

Les comptes humains (`apiculteur-test`, `superviseur-test`, `responsable-test`,
`admin-test`) n'existent **que** dans `realm-zumm.dev.json`. Leurs mots de passe
sont des secrets de développement, sans valeur : ce fichier n'est jamais importé
en production, où les comptes sont créés par l'administrateur ou par le code
d'invitation d'exploitation.

## Mot de passe oublié

Activé depuis le SPRINT-33 : `resetPasswordAllowed: true` sur les deux realms,
et un `smtpServer` sur chacun — pointé en dur sur `mailpit:1025` dans
`realm-zumm.dev.json` (la pile de dev fournit Mailpit, voir
`infra/docker-compose.dev.yml`), paramétrable par variables d'environnement
(`ZUMM_SMTP_*`) dans `realm-zumm.json`, à servir par un vrai serveur d'envoi en
production. `ZUMM_AUTH_REINITIALISATION_URL` (backend) publie le lien côté
`/api/info`, lu par `RecuperationVue`. Avant ce sprint, les deux étaient
délibérément absents : activé sans serveur d'envoi, Keycloak affiche un lien qui
mène à un formulaire qui n'envoie rien — pire que l'absence du lien, qui au
moins ne ment pas.

**Piège : un volume Postgres existant ne réimporte pas le realm.** Keycloak
n'importe `realm-zumm(.dev).json` qu'à la CRÉATION du realm en base, jamais aux
démarrages suivants. Une pile démarrée avant ce sprint (ou tout volume
`postgres` antérieur) continue de tourner sur l'ancien realm — sans
`smtpServer`, sans `resetPasswordAllowed` — même après avoir tiré ce commit et
reconstruit les images. Aucun message d'erreur ne le signale : l'e-mail échoue
en silence côté Keycloak (`No sender address configured`), et rien côté
application ne le voit. Pour repartir sur le realm à jour :
`docker compose --env-file .env -f infra/docker-compose.yml
-f infra/docker-compose.dev.yml down -v`, puis relancer `up -d --build` (Flyway
et l'import Keycloak rejouent tous les deux depuis zéro).
