# 🏃 SPRINT-19 : Le produit avant le compte

**Thème :** Ouvrir le site à qui n'a pas encore de session, et dire à qui en a une pourquoi une porte est fermée
**Objectif :** Qu'un visiteur puisse savoir ce que fait Zümm sans compte, qu'un refus de rôle cesse de se présenter comme une panne, et qu'aucune page légale ne passe pour finie alors qu'elle est vide
**Période :** 2027-04-27 → 2027-05-10 (14 jours)
**Story Points :** 34 / Capacity : 40

---

## 📅 Cérémonies Scrum

| Cérémonie | Date/Heure | Durée |
|:---|:---|:---|
| Sprint Planning | 2027-04-27 09:00-11:00 | 2h |
| Daily Scrum | Tous les jours 09:15 (15 min) | 15 min |
| Sprint Review | 2027-05-10 14:00-15:30 | 1h30 |
| Sprint Retrospective | 2027-05-10 15:30-16:30 | 1h |

---

## 📋 User Stories

| ID | Story | Points | Statut |
|:---|:---|:---:|:---|
| US-081 | Accueil public et coquille des pages hors console | 8 | 🟢 Livré |
| US-082 | Navigation filtrée par rôle, refus et panne expliqués | 5 | 🟢 Livré |
| US-083 | Pages d'information et pages légales (CGU, RGPD) | 8 | 🟢 Livré |
| US-084 | Pages d'acquisition — fonctionnalités, éditions, ressources, contact | 5 | 🟢 Livré |
| US-085 | Compte, récupération de mot de passe et matrice des permissions | 8 | 🟢 Livré |

Sprint **entièrement front-end** : aucune migration, aucun contrôleur, aucun
endpoint nouveau. C'est la conséquence directe du parti pris — tout ce que ces
écrans racontent existe déjà côté serveur, et ce qui n'existe pas n'est pas
raconté.

---

## 🔎 US-081 — l'application n'avait qu'une porte

Sans session, l'écran d'entrée occupait tout l'espace, quelle que soit l'URL. Un
visiteur — un apiculteur qui découvre le produit, un correcteur, un agent qui n'a
pas encore son code d'exploitation — ne pouvait donc rien apprendre avant d'avoir
un compte. **On ne demande pas un compte pour un logiciel dont on ignore ce qu'il
fait.**

`AccueilVue` est servie à tout le monde et reste consultable une fois connecté :
l'appel à l'action devient alors « Ouvrir la console ». La session lui est passée
en paramètre plutôt que lue depuis un contexte — la page reste ainsi rendable en
test sans monter toute l'application.

**Elle n'appelle aucune API, et c'est la règle du lot.** La seule page atteignable
sans jeton ne doit pas dépendre d'un endpoint protégé, sinon elle échoue
précisément pour le visiteur à qui elle s'adresse. Le nombre d'écrans qu'elle
annonce est dérivé de `ONGLETS` ; aucun chiffre n'y est écrit en dur.

`CoquillePublique` a été extraite dès la troisième page à réclamer la même barre
et le même pied. La dupliquer aurait garanti la dérive : un sélecteur de langue
ajouté ici, oublié là, et le visiteur perd sa langue en ouvrant les mentions
légales.

## 🔎 US-082 — un refus de rôle n'est pas une panne

Un 403 arrivait jusqu'ici sous la forme d'un bandeau rouge portant le message du
serveur, avec un bouton « Réessayer » — qui rejouait la même requête pour obtenir
le même refus.

Trois décisions, toutes visibles dans le code :

1. **L'écran de refus ne prend pas de route.** Une URL `/403` qu'on peut taper à
   la main est une page qui ment : elle affirme un refus que rien n'a prononcé.
   `InterditVue` se rend **à la place** du contenu, sur un refus réel — celui de
   la table des rôles avant l'appel, ou celui du serveur après.
2. **« Pas de session » et « session sans le rôle » sont séparés en amont**, dans
   `App`. Sans session on va vers l'écran d'entrée ; avec une session sans le
   rôle on arrive sur l'écran de refus, et on n'y redirige pas — une redirection
   vers une connexion déjà faite boucle.
3. **`PanneVue` ne sert pas au mode hors ligne.** Zümm est utilisable au rucher
   sans réseau : couper l'écran quand la connexion tombe retirerait exactement ce
   que la PWA apporte. Hors ligne, la barre signale l'état et la file d'attente
   prend le relais ; l'écran de panne est réservé aux 5xx, et ne dépend d'aucune
   donnée — un écran de panne qui a besoin de l'API ne s'affiche jamais au moment
   où on en a besoin.

`ROLES_ONGLET` recopie ce que `SecurityConfig.matriceRbac` refuse déjà côté
serveur, et ne couvre que trois écrans : `audit`, `invitations` et la matrice des
permissions elle-même. Le référentiel n'y figure pas — un apiculteur a le droit
de **lire** la liste des agents ; c'est le bouton « Nouveau » qui doit
disparaître, pas l'onglet.

> **Ce filtrage est un confort, jamais une protection.** L'autorisation est posée
> par le serveur ; ce que le navigateur cache, il pourrait le montrer. On masque
> pour ne pas proposer une porte fermée, pas pour fermer la porte.

## 🔎 US-083 — le bandeau des manques

Les CGU et la politique de confidentialité n'ont pas été écrites à partir d'un
modèle. Chaque ligne des deux tableaux RGPD est relevée dans le code : les
catégories de données sont celles des entités JPA, et les destinataires sont les
**seuls appels sortants du produit** — il n'y en a que deux.

Deux points sont propres à Zümm et absents de tout modèle :

- **la position des ruchers** est une donnée sensible commercialement : elle vaut
  un vol de cheptel. `PolitiquePositions` la filtre déjà selon le rôle ; la page
  le dit, parce qu'un utilisateur a le droit de savoir ce qui protège ses ruchers ;
- **le fond de carte est appelé par le navigateur**, pas par le serveur. Son
  hébergeur voit donc l'adresse IP et la zone consultée, c'est-à-dire
  approximativement où sont les ruchers. La météo, elle, part du serveur :
  l'hébergeur du service météo voit des coordonnées, jamais l'utilisateur qui les
  consulte.

**Les durées de conservation ne sont pas écrites.** Aucune purge n'existe dans les
migrations Flyway ; en annoncer une serait promettre un effacement que rien
n'exécute. De même pour la raison sociale, l'adresse et l'hébergeur : le dépôt ne
les contient pas, et les remplir de plausible ferait lire un engagement que
personne n'a pris.

Ce qui manque est donc listé **en haut de la page**, pas dans un commentaire du
code : ces pages ne peuvent pas partir en ligne par mégarde en passant pour
finies.

## 🔎 US-084 — trois pages où ne rien construire était la bonne réponse

- **Contact : pas de formulaire.** Aucun contrôleur ne reçoit de message, et
  aucun serveur d'envoi n'est configuré dans les deux realms. Un formulaire posé
  ici n'enverrait rien, et l'utilisateur repartirait convaincu d'avoir été
  entendu. La page aiguille vers l'interlocuteur qui existe réellement — le
  responsable d'exploitation, seul à pouvoir émettre un code, attribuer un rôle
  ou relancer un mot de passe.
- **Éditions : aucun palier.** L'arbitrage du 18/08/2026 retenait une page
  descriptive sans afficher un montant. Reste que des paliers non plus ne
  s'inventent pas : le dépôt ne contient ni facturation, ni abonnement, ni
  fonction verrouillée. Fabriquer « Essentiel / Pro / Entreprise » aurait été
  inventer une stratégie produit, pas décrire un logiciel.
- **Ressources : le centre d'aide est public**, et non en console — second
  arbitrage du 18/08/2026. En console il faudrait le remettre à jour à chaque
  livraison sous peine de mentir ; en public il sert aussi celui qui n'a pas
  encore de compte, et c'est lui qui a le plus de questions. Rendu en
  `details`/`summary` : accordéon natif, accessible au clavier, sans une ligne de
  JavaScript.

La page « Fonctionnalités » se termine sur ce que Zümm **ne fait pas**. Une liste
de fonctions sans limite annoncée oblige le lecteur à essayer pour découvrir le
manque, et il le découvre au pire moment.

`AProposVue` est la **seule page publique qui appelle l'API**, sur le seul
endpoint métier ouvert sans jeton (`GET /api/info`, `permitAll`). L'appel n'est
pas obligatoire au rendu : serveur éteint, la page s'affiche sans la carte de
version plutôt que de montrer une erreur.

## 🔎 US-085 — ce que « Mon compte » refuse de faire

`CompteVue` ne lit que la session rendue par le serveur. Il n'y a rien à lire dans
le navigateur : aucun jeton n'y est stocké depuis l'ADR-006.

**Il ne change pas le mot de passe**, et ce n'est pas un oubli — le mot de passe
appartient à Keycloak, et le BFF n'expose que la connexion et l'inscription. Poser
ici un formulaire qui n'aboutirait nulle part serait le défaut du formulaire de
contact, en pire : l'utilisateur croirait avoir changé son mot de passe. Même
raisonnement sur la page de récupération, qui n'affiche même pas de lien
« recevoir un courriel » : vérification faite dans `realm-zumm.json` et
`realm-zumm.dev.json`, aucun serveur d'envoi n'y est configuré.

`PermissionsVue` répond à la question que le filtrage de US-082 a déplacée :
l'utilisateur ne voit plus la porte, donc il ignore qu'elle existe. La matrice
recopie `SecurityConfig.matriceRbac`, et **cette recopie est un risque assumé** —
aucun endpoint ne publie la matrice. Deux garde-fous : la liste des écrans retirés
de la navigation est *dérivée* de `ROLES_ONGLET` plutôt que réécrite, et les rôles
restent des identifiants, hors des fichiers de langue — les écrire en toutes
lettres dans trois locales aurait été le meilleur moyen de voir la matrice
diverger d'une langue à l'autre.

---

## 🎯 Sprint Review - Démonstration

**Date :** 2027-05-10 14:00-15:30

1. **Le parcours du visiteur, en navigation privée.** `https://localhost` ouvre
   l'accueil, pas l'écran d'entrée. Fonctionnalités, éditions, ressources,
   contact, à propos, CGU, confidentialité : sept pages atteignables sans compte.
   Le réseau est ensuite coupé côté serveur — les pages continuent de se rendre,
   à l'exception de la carte de version de « À propos », qui disparaît sans
   bandeau d'erreur.
2. **Le même parcours en arabe.** Bascule de langue depuis la coquille publique :
   la langue est conservée d'une page à l'autre, RTL compris, et le pied garde
   ses liens légaux. 702 chaînes par locale, parité vérifiée à la compilation.
3. **Le refus de rôle, joué avec `apiculteur-test`.** L'onglet « Audit » n'est pas
   dans le rail. L'URL `/audit` est tapée à la main : écran de refus nommant les
   rôles qui l'ouvrent, sans bouton « Réessayer », sans redirection. Puis
   `responsable-test` : l'onglet réapparaît.
4. **La matrice des permissions**, ouverte depuis le menu de profil, met les deux
   comportements précédents côte à côte.
5. **Le bandeau des manques**, montré sur les CGU et la confidentialité :
   raison sociale, hébergeur, durées de conservation. La séance a explicitement
   validé de **ne pas** les combler par des valeurs plausibles.

**Ce que la revue a retenu.** Le filtrage de la navigation ne couvre que la
**lecture** d'un écran entier. Les écritures du référentiel — créer un site,
supprimer une ruche — restent proposées à un apiculteur qui recevra un 403 en
cliquant. Le gardiennage au niveau de l'**action** (le bouton « Nouveau ») n'a pas
été fait, et il est reporté explicitement plutôt que laissé implicite.

**Décision de la revue.** L'administration **de la plateforme** (rôle `admin`,
transverse aux exploitations) n'entre pas dans ce sprint et n'est pas non plus
absorbée dans l'administration du tenant : ce sont deux produits différents. Elle
part au backlog, avec la réserve que l'usurpation d'identité (« se connecter en
tant que »), si elle est retenue un jour, devra être tracée au journal d'audit,
bornée dans le temps et visible de l'utilisateur concerné.

---

## ⚠️ Risques Identifiés

| Risque | Impact | Mitigation |
|:---|:---|:---|
| Une page publique qui dépend d'un endpoint protégé | Elle échoue exactement pour le visiteur à qui elle s'adresse | Aucune page servie sans jeton n'appelle l'API, sauf « À propos » sur `permitAll` — et l'appel n'y est pas obligatoire au rendu |
| Un texte légal complété par du plausible | On lit un engagement que personne n'a pris ; le défaut est invisible à la relecture | Bandeau des manques **en tête de page**, pas en commentaire : la page ne peut pas passer pour finie |
| Une matrice de permissions recopiée du serveur | Divergence silencieuse le jour où `matriceRbac` change | Les écrans masqués sont dérivés de `ROLES_ONGLET`, pas réécrits ; les rôles restent hors des locales |
| Confondre le masquage d'onglet avec une autorisation | On croit avoir fermé une porte qu'on a seulement cachée | Écrit dans la javadoc de `ROLES_ONGLET` ; l'autorisation reste posée par le serveur, et les tests d'intégration back n'ont pas bougé |
| Un écran de panne servi sur une coupure réseau | La PWA perd sa raison d'être au rucher | `PanneVue` est réservée aux 5xx ; le hors-ligne garde la barre d'état et la file d'attente |
| Trois locales à faire grandir de 300 chaînes | Un libellé oublié dans une langue que personne ne relit | Parité garantie par le type (`Record<Langue, typeof fr>`) : une clé manquante casse la compilation |

---

## 📊 Burndown Chart

| Jour | Reste à faire (idéal) | Reste à faire (réel) | Notes |
|:---|:---:|:---:|:---|
| Jour 1 | 34 | 34 | Plan des pages validé écran par écran avant toute ligne de JSX |
| Jour 3 | 29 | 26 | US-081 : accueil public, deux routes publiques, 56 chaînes × 3 |
| Jour 5 | 24 | 21 | US-082 : `ROLES_ONGLET`, écrans de refus et de panne, statut HTTP remonté dans `CorpsSection` |
| Jour 8 | 17 | 13 | US-083 : coquille publique extraite, CGU et RGPD relevés dans le code |
| Jour 11 | 10 | 8 | US-084 : quatre pages d'acquisition ; contact et éditions livrés **sans** ce qu'ils ne peuvent pas porter |
| Jour 14 | 0 | 0 | US-085 : compte, récupération, matrice ; 210 tests, lint et build verts |

*Écart au plan : en avance constante, pour une raison qui n'est pas un mérite —
aucune de ces cinq stories ne touche la base ni le contrat. Un sprint front pur
n'a ni migration à reprendre ni contrat à régénérer, et son estimation est
d'autant plus fiable. Ne pas en tirer une vélocité de référence.*

---

## ✅ Definition of Done

- [x] Front : **210 tests** sur 23 fichiers, `npm run typecheck` sans erreur,
      `npm run lint` sans erreur (10 avertissements `react-refresh`), `npm run build` OK
- [x] Trois locales à **702 chaînes** chacune, parité tenue à la compilation
- [x] Aucune route ajoutée hors de `routage/routes.ts` ; l'invariant « un onglet,
      une famille » et la disjonction onglets / routes publiques / routes de
      session sont vérifiés par `routage.test.ts`
- [x] Backend, migrations et contrat OpenAPI **inchangés** — rien à régénérer

## 🔁 Rétrospective

**Ce qui a marché.** Écrire d'abord le plan page par page — statut, route,
fichier, endpoint, rôles, clés i18n — et le faire valider avant d'ouvrir un
éditeur. Trois pages ont changé de nature à ce moment-là, pour un coût de
relecture au lieu d'un coût de réécriture : le formulaire de contact est devenu un
aiguillage, la grille tarifaire une page d'édition unique, et le centre d'aide est
passé de la console au public.

**Ce qu'on retient.** La question utile, sur ce sprint, n'a jamais été « comment
construire cet écran » mais « qu'est-ce que cet écran n'a pas le droit de
prétendre ». Un formulaire qui n'envoie rien, un lien de réinitialisation sans
serveur d'envoi, une durée de conservation sans purge : les trois se ressemblent,
et les trois auraient été livrés sans que rien ne casse. **Aucun test ne rougit
quand une interface promet ce que le back ne fait pas** — seule la relecture
attrape ce défaut-là.

**Ce qui reste ouvert.**

1. **Le gardiennage au niveau de l'action.** Le référentiel reste en lecture pour
   tous, avec ses boutons d'écriture visibles ; un apiculteur reçoit un 403 en
   cliquant. Noté dans la javadoc de `ROLES_ONGLET`.
2. **L'administration de la plateforme** (rôle `admin`, transverse aux
   exploitations) : tableau de bord inter-tenants, fiche de compte, suspension,
   audit élargi. Écarté du sprint, décision de revue.
3. **Le contexte météo réel et les prévisions à sept jours** — extension d'US-029
   engagée pendant le sprint, non close à la clôture : elle touche le back et le
   contrat, et sort donc du périmètre front de SPRINT-19. À reprendre au sprint
   suivant.
4. **Les captures d'écran du README** (`docs/screenshots/`), toujours au `TODO` :
   le site public leur donne enfin un premier écran à montrer.

*Dernière mise à jour : 25/08/2026*
