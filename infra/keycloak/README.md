# Realm Keycloak — `realm-zumm.json`

> ⚠️ **Ne jamais ajouter de clés de commentaire dans le JSON.** L'importateur de
> Keycloak désérialise le fichier en `RealmRepresentation` sans tolérance : toute
> clé inconnue — y compris un `"_commentaire"` — fait échouer l'import **et le
> démarrage du conteneur** avec `Unrecognized field ... not marked as ignorable`.
> Le format JSON n'admet pas de commentaires ; ce fichier les accueille à leur place.

## Rôles métier

`apiculteur`, `superviseur`, `responsable`, `admin` — profils repris du cahier des
charges (US-005). La matrice RBAC profils × fonctions est arrêtée au SPRINT-01 :
ces rôles en sont le support, pas la définition.

## Clients

| Client | Nature | Justification |
|---|---|---|
| `zumm-frontend` | public, PKCE obligatoire | Aucun secret ne peut être gardé dans un navigateur : un client confidentiel y serait un secret publié. PKCE couvre l'interception du code d'autorisation. |
| `zumm-backend` | `bearer-only` | Serveur de ressources : il valide les jetons, il n'en émet aucun. |

## Utilisateur de test

Le compte livré dans le realm est un compte de **développement uniquement**. Son
mot de passe est un secret de test, sans valeur : ce realm n'est jamais importé en
production, où les comptes sont créés par l'administrateur.
