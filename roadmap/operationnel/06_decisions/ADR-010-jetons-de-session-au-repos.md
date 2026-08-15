# ADR-010 — Les jetons de session au repos

- **Date** : 2026-08-15
- **Statut** : 🟢 Accepté
- **Décideurs** : architecte, responsable sécurité
- **Dépend de** : [ADR-006](ADR-006-stockage-des-jetons.md) — les jetons quittent le navigateur ; [ADR-009](ADR-009-connexion-dans-l-application.md) — le BFF les obtient lui-même
- **Bloque** : sauvegardes, restauration, procédure d'incident

---

## Contexte

L'ADR-006 a sorti les jetons du navigateur, et c'était le bon mouvement : une
XSS ne peut plus exfiltrer de session réutilisable ailleurs. Mais un jeton qui
sort d'un endroit entre dans un autre. Depuis le SPRINT-17, cet autre endroit est
la base de données.

Spring Session JDBC sérialise les attributs de session dans
`SPRING_SESSION_ATTRIBUTES.ATTRIBUTE_BYTES`, de type `BYTEA`
(`V17__sessions_serveur_sprint17.sql`). Parmi ces attributs se trouve le client
autorisé OAuth2 — donc le **jeton d'accès et le jeton de rafraîchissement** émis
par Keycloak. Ils y sont **en clair** : la sérialisation Java n'est pas un
chiffrement, et personne n'a jamais prétendu le contraire.

Trois propriétés distinguent cette table de toutes les autres du schéma, et
c'est leur conjonction qui justifie un ADR plutôt qu'une ligne de journal :

1. **elle ne porte pas de `tenant_id`**, donc aucune politique RLS ne s'y
   applique — l'invariant « toute table métier porte sa politique » ne la couvre
   pas, et c'est correct : une session n'appartient pas à une exploitation, elle
   appartient à une personne ;
2. **son contenu n'est pas une donnée, c'est un moyen d'authentification.** Une
   ligne de `mesure` divulguée est une fuite ; un jeton de rafraîchissement
   divulgué est un accès. Il rejoue, et il rejoue depuis n'importe où ;
3. **elle part dans les sauvegardes.** `infra/sauvegarde.sh` dumpe la base
   entière. Le fichier de sauvegarde contient donc, pendant toute sa durée de
   rétention, des jetons qui étaient vivants au moment du dump.

Le vecteur n'est pas théorique et n'est pas non plus dramatique : il faut déjà
avoir obtenu une lecture de la base ou d'une sauvegarde. Mais c'est précisément
le scénario où l'on aimerait que la compromission s'arrête, au lieu de se
prolonger en sessions rejouables.

---

## Options examinées

**A. Chiffrer les attributs de session dans l'application.** Un
`SpringSessionBackedSessionRegistry` avec un sérialiseur maison AES-GCM, la clé
venant de l'environnement. Coût réel : le sérialiseur devient un composant
critique — un bug y rend toutes les sessions illisibles d'un coup — et il faut
une rotation de clé, donc un mécanisme de double lecture pendant la transition.
Pour une clé qui vit sur la même machine que la base, le gain se limite au vol de
sauvegarde.

**B. Chiffrer côté base avec `pgcrypto`.** Écarté immédiatement : la clé
transiterait dans les requêtes, et donc dans `pg_stat_statements` et les journaux
de requêtes lentes. On déplacerait le secret d'un endroit non chiffré vers un
endroit non chiffré **et** journalisé.

**C. Ne rien garder de rejouable.** Ne stocker que le jeton d'accès, sans le
jeton de rafraîchissement : l'exposition tombe à sa durée de vie (quelques
minutes). En contrepartie, l'utilisateur est renvoyé vers Keycloak à chaque
expiration — soit, avec un `timeout` de session de 8 h, plusieurs interruptions
par journée de travail. Pour un apiculteur en rucher, avec une connexion
intermittente, c'est le parcours qui casse.

**D. Assumer, et le documenter.**

---

## Décision

**Option D**, avec les contrôles compensatoires suivants — chacun vérifié dans le
dépôt, pas seulement énoncé :

- **le moindre privilège tient déjà.** `V17` n'accorde le DML sur
  `SPRING_SESSION` et `SPRING_SESSION_ATTRIBUTES` qu'à `${role_app}`. Aucun autre
  rôle ne lit cette table, et `zumm_app` n'a pas le DDL ;
- **la connexion applicative n'est plus superutilisateur par défaut.** Le défaut
  de `spring.datasource.username` est `zumm_app` ; l'ancien défaut `zumm`
  (propriétaire) ouvrait un mode où la RLS s'éteignait sans bruit ;
- **la déconnexion est propagée à Keycloak** (`DeconnexionOidc`) : la session
  supprimée côté Zümm ne laisse pas une session SSO ouverte derrière elle ;
- **le cookie porte `Secure`, `HttpOnly` et `SameSite=Lax`** explicitement, et
  non plus par déduction du contexte de requête.

### Ce que cela impose

- **Une sauvegarde de la base de Zümm est un secret**, au même titre qu'un
  fichier `.env`. Elle se chiffre au repos, ne se copie pas sur un poste de
  travail et ne s'attache pas à un ticket. À reporter dans la procédure
  d'exploitation avant toute mise en production.
- **Une restauration réveille des jetons.** Restaurer un dump de la veille
  réinjecte des sessions dont les jetons peuvent encore être valides. La
  restauration doit donc **purger `SPRING_SESSION`** — `tester-restauration.sh`
  ne le fait pas aujourd'hui.
- **En cas de suspicion de fuite de base**, la révocation ne passe pas par Zümm :
  c'est une invalidation des sessions du royaume, côté Keycloak. À écrire dans la
  procédure d'incident.

---

## Ce qui ferait revenir sur cette décision

- une base hébergée ailleurs que sur la machine applicative, ou une sauvegarde
  déposée chez un tiers : la clé de l'option A cesserait alors d'être au même
  endroit que le chiffré, et l'option A deviendrait rentable ;
- l'arrivée d'un secret de plus grande valeur dans la session (jeton d'un service
  de paiement, identifiants d'une intégration tierce) ;
- une exigence contractuelle de chiffrement au repos des éléments
  d'authentification.

---

## Réserves

La durée de vie du jeton de rafraîchissement émis par le royaume `zumm`
**n'a pas été relevée** au moment d'écrire cet ADR ; c'est elle qui fixe la
fenêtre réelle d'exposition d'une sauvegarde. À confirmer dans la configuration
Keycloak, et à reporter ici.
