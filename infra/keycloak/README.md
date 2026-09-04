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

## Mot de passe oublié — ce qu'il faut activer, et pourquoi ce n'est pas fait ici

`resetPasswordAllowed` est **absent** des deux realms, et c'est délibéré. Activé
sans serveur d'envoi, Keycloak affiche un lien « Mot de passe oublié ? » qui mène
à un formulaire qui accepte l'adresse et n'envoie rien : l'utilisateur attend un
courriel qui n'arrivera jamais. C'est le pire des trois états possibles — pire
que l'absence du lien, qui au moins ne ment pas. `RecuperationVue` porte le même
raisonnement côté application.

Pour ouvrir le libre-service (SPRINT-25, lot J), **deux choses, dans cet ordre** :

1. **Configurer un serveur d'envoi** dans le realm — administration Keycloak,
   *Realm settings → Email* — ou en ajoutant un bloc `smtpServer` au fichier
   importé. Les identifiants SMTP sont des secrets : ils ne se committent pas,
   ils passent par des variables d'environnement ou par la console.
2. **Activer** *Realm settings → Login → Forgot password*, puis renseigner
   `ZUMM_AUTH_REINITIALISATION_URL` côté back-end — typiquement
   `https://<keycloak>/realms/zumm/login-actions/reset-credentials?client_id=zumm-bff`.
   Tant que cette variable est vide, l'application continue d'aiguiller vers le
   responsable d'exploitation, ce qui reste le seul chemin qui fonctionne.

Le renseignement de la variable est ce qui fait apparaître le lien dans la page
de récupération : l'application ne devine pas la configuration du fournisseur
d'identité, elle en relaie ce que l'exploitant lui déclare.
