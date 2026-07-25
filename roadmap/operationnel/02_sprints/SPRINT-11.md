# 🏃 SPRINT-11: Fiabilité de la session et du front

**Thème:** Session durable, navigation adressable et restitution localisée
**Objectif:** Rendre la console utilisable une journée entière sur le terrain sans être déconnecté ni perdre sa saisie, et faire disparaître les approximations d'interface héritées du prototypage
**Période:** 2027-01-05 → 2027-01-18 (14 jours)
**Story Points:** 34 / Capacity: 40

> **Note de planification.** La cadence mécanique de 14 jours plaçait ce sprint du
> 15 au 28 décembre, à cheval sur la trêve de fin d'année. Il est décalé à janvier
> plutôt que planifié sur une capacité que l'équipe n'aurait pas. La capacité de
> référence reste à 40 points.

---

## 📅 Cérémonies Scrum

| Cérémonie | Date/Heure | Durée |
|:---|:---|:---|
| Sprint Planning | 2027-01-05 09:00-13:00 | 4h |
| Daily Scrum | Tous les jours 09:15 (15 min) | 15 min |
| Sprint Review | 2027-01-18 14:00-16:00 | 2h |
| Sprint Retrospective | 2027-01-18 16:00-17:00 | 1h |

---

## 📋 User Stories

| ID | Story | Points | Statut | Assigné |
|:---|:---|:---:|:---|:---|
| US-050 | Rafraîchissement du jeton OIDC | 8 | 🟢 Livré (`auth/rafraichissement.ts` : échéance JWT, verrou, planification ; rejeu du 401 dans le client) | - |
| US-051 | Navigation adressable par URL | 8 | 🟢 Livré (routeur maison sur l'API History, 15 routes, `React.lazy` par route, écran 404) | - |
| US-052 | Pagination des listes | 8 | 🟢 Livré (`page`/`taille`/`tri` optionnels, total en `X-Total-Count`, barre dans `CorpsSection`) | - |
| US-053 | Formatage localisé des dates et des nombres | 5 | 🟢 Livré (`i18n/formats.ts` adossé à `Intl`, appliqué aux 6 colonnes de date et aux distances) | - |
| US-054 | Dialogues du design system à la place des dialogues natifs | 5 | 🟢 Livré (`ui/dialogues.tsx`, 12 appels natifs supprimés, piège de focus, `jsx-a11y`) | - |

**Origine :** audit du front réalisé après le SPRINT-10. US-050 et US-051 forment un
tout — corriger la session sans rendre les écrans adressables reconnecte
l'utilisateur sur le premier onglet, quoi qu'il ait été en train de faire.

---

## 🎯 Détail des user stories

### US-050 — Rafraîchissement du jeton OIDC (8 pts)

> **En tant qu'**apiculteur remplissant un rapport de visite au rucher,
> **je veux** rester connecté tant que je travaille,
> **afin de** ne pas perdre ma saisie toutes les cinq minutes.

**Constat** : `grep -rn "refresh" frontend/src` ne rend **aucune occurrence**.
L'échange du code (`auth/oidc.ts:99`) ne lit que `access_token` et jette le
`refresh_token`. Le realm ne redéfinit pas `accessTokenLifespan` : Keycloak applique
son défaut de 300 s. À l'expiration, `client.ts:112` reçoit un 401, appelle
`fermerSession()` et renvoie à l'écran de connexion — **saisie en cours perdue**.
Rien ne l'a révélé jusqu'ici : le flux OIDC n'est joué ni en CI ni en test.

**Critères d'acceptation**

- Le `refresh_token` est conservé au retour de Keycloak et le scope
  `offline_access` demandé si le realm l'exige.
- Le jeton est rafraîchi **avant** expiration (marge d'au moins 30 s), sans
  interaction ni rechargement de page.
- Un 401 déclenche une tentative unique de rafraîchissement puis rejoue la requête ;
  seul un échec de rafraîchissement ferme la session.
- Deux requêtes concurrentes recevant un 401 ne déclenchent **qu'un** rafraîchissement
  (verrou), et les deux sont rejouées.
- La déconnexion révoque le `refresh_token` en plus de la session SSO.
- Le rafraîchissement échoué hors ligne n'efface pas la file de mutations (US-011).
- Tests : expiration simulée, rafraîchissement concurrent, échec définitif —
  Keycloak simulé, aucun réseau réel en CI.

### US-051 — Navigation adressable par URL (8 pts)

> **En tant qu'**utilisateur,
> **je veux** que chaque écran ait son adresse,
> **afin de** revenir en arrière, partager un lien et retrouver ma place après une
> reconnexion.

**Constat** : les 15 onglets vivent dans un `useState` (`App.tsx:87`). Ni lien
profond, ni bouton retour du navigateur, ni partage — et, en PWA installée, aucun
moyen de rouvrir l'application ailleurs que sur « Fermiers ».

**Critères d'acceptation**

- Chaque onglet a une route (`/sites`, `/plannings`, `/carte`…) ; l'URL change à la
  navigation et l'écran suit le bouton retour.
- Une URL inconnue rend un écran « page introuvable » traduit, pas un écran blanc.
- Après reconnexion, l'utilisateur revient sur la route demandée avant l'expiration.
- Le choix routeur (`react-router` ou routeur maison) est tranché par un ADR court :
  le projet n'a aujourd'hui **aucune** dépendance de routage, et l'ajout doit être
  justifié — cf. `docs/ADR/`.
- Les vues sont chargées en `React.lazy` par route (voir la note de bundle ci-dessous).

### US-052 — Pagination des listes (8 pts)

> **En tant que** responsable d'une exploitation de plusieurs centaines de ruches,
> **je veux** que les listes se chargent par pages,
> **afin que** la console reste utilisable quand les données s'accumulent.

**Constat** : `lister()` charge la table entière, sans exception (`client.ts:141`).
Sur une exploitation réelle, la liste des ruches, des mesures ou des visites croît
sans borne.

**Critères d'acceptation**

- Les endpoints de liste acceptent `page` et `taille` et rendent le total ; le
  contrat OpenAPI est mis à jour.
- Le composant `Table` reçoit une barre de pagination (page courante, total,
  précédent/suivant), traduite et navigable au clavier.
- La taille de page par défaut vient de `ConfigZumm.ini` (US-025), pas d'une
  constante de code.
- Le tri serveur suit sur au moins une colonne par liste.
- Test d'intégration back : 250 enregistrements, la page 3 rend les bons éléments et
  l'isolation tenant tient sur les pages suivantes.

### US-053 — Formatage localisé des dates et des nombres (5 pts)

> **En tant qu'**utilisateur arabophone ou anglophone,
> **je veux** lire les dates et les nombres dans les conventions de ma langue,
> **afin de** ne pas déchiffrer un format ISO.

**Constat** : `grep -rn "Intl\." frontend/src` → **0 occurrence**. Les dates
s'affichent en ISO brut (`2026-12-04`) en français, en anglais *et* en arabe
(`PlanningsVue.tsx:67`, `VisitesVue.tsx:71`, `RecoltesVue.tsx:49`) ; les nombres
passent par `toFixed`, donc toujours avec un point décimal. Par ailleurs
`hooks.ts:9` renvoie un message d'erreur **en français en dur**, hors du système
de traduction.

**Critères d'acceptation**

- Un module de formatage expose date, date-heure, nombre et distance, adossés à
  `Intl` et à la langue courante du contexte.
- Toutes les colonnes de date et toutes les valeurs numériques des vues y passent.
- Le message d'erreur générique de `hooks.ts` rejoint `console.ts` dans les trois
  langues ; le test de parité existant le couvre automatiquement.
- Les tests vérifient le rendu dans les trois locales — y compris que l'arabe reste
  lisible en RTL.

### US-054 — Dialogues du design system à la place des dialogues natifs (5 pts)

> **En tant qu'**utilisateur,
> **je veux** des confirmations cohérentes avec le reste de l'application,
> **afin de** ne pas tomber sur une boîte système en anglais au milieu d'une console
> en arabe.

**Constat** : 12 appels à `window.alert`, `window.confirm` et `window.prompt`
répartis dans 9 vues. Ils sont non traduits (le navigateur impose ses libellés de
boutons), non stylés, bloquants, et intestables autrement qu'en les simulant.

**Critères d'acceptation**

- Un composant `Confirmation` et un composant `Invite` rejoignent
  `ui/composants.tsx`, construits sur la `Modale` existante.
- Les 12 appels natifs sont remplacés ; `grep -rn "window\.\(alert\|confirm\|prompt\)"`
  ne rend plus rien sous `frontend/src`.
- Les dialogues sont pilotés au clavier (Échap ferme, Entrée valide) et **piègent le
  focus** — la `Modale` actuelle gère Échap et le focus initial, mais Tab s'en échappe
  et le focus n'est pas restitué à la fermeture. Corrigé ici pour tous les usages.
- `eslint-plugin-jsx-a11y` est activé pour empêcher la régression.

---

## 🎯 Sprint Review - Démonstration

**Date:** 2027-01-18 14:00-16:00

Session laissée ouverte plus de trente minutes pendant la démonstration, sans
déconnexion ni perte de saisie. Navigation au bouton retour entre plusieurs écrans,
partage d'une URL d'écran, reconnexion qui retombe sur la bonne route. Liste de
ruches paginée sur un jeu volumineux. Bascule FR → AR montrant dates et nombres
reformatés. Suppression confirmée par le dialogue maison, au clavier seul.

---

## ⚠️ Risques Identifiés

| Risque | Impact | Mitigation |
|:---|:---|:---:|
| Le rafraîchissement du jeton touche le chemin critique de **toutes** les requêtes | Régression globale si le verrou est mal posé | Isoler la logique dans un module dédié testé unitairement, avant tout branchement dans `client.ts` |
| Ajout d'une première dépendance de routage | Rupture avec le choix « zéro dépendance » assumé jusqu'ici | ADR court tranché au planning, pas en cours de route |
| La pagination modifie un contrat d'API déjà consommé | Front et back désynchronisés | Paramètres optionnels, comportement inchangé quand ils sont absents |
| `offline_access` non activé sur le realm | US-050 bloquée en fin de sprint | Vérifier la configuration du realm **au jour 1**, pas à la démonstration |
| Focus trap ajouté à une `Modale` utilisée par 9 vues | Régression d'ergonomie diffuse | Tests de clavier sur la modale avant de brancher les dialogues |

---

## 📊 Burndown Chart (prévisionnel)

| Jour | Reste à faire (idéal) | Reste à faire (réel) | Notes |
|:---|:---:|:---:|:---|
| Jour 1 | 34 | 34 | Vérification du realm, module de rafraîchissement (US-050) |
| Jour 4 | 26 | 26 | Session durable branchée et testée |
| Jour 7 | 18 | 18 | Routes adressables + chargement paresseux (US-051) |
| Jour 10 | 10 | 13 | Formatage localisé (US-053) puis dialogues (US-054) |
| Jour 12 | 5 | 8 | Pagination bout en bout (US-052), passée en dernier |
| Jour 14 | 0 | 0 | Vérifications complètes, CI verte |

---

## 📝 Rétrospective

**Résultat : les 5 user stories livrées et testées.**
Backend : **44 tests unitaires + 77 d'intégration, `Skipped: 0`**, `BUILD SUCCESS`.
Front : **108 tests Vitest** (42 avant ce sprint), `lint` sans erreur, `typecheck` et
`build` verts.

### Ce qui a bien fonctionné

- **La mécanique de session a été isolée avant d'être branchée.** `rafraichissement.ts`
  ne connaît ni Keycloak ni le client d'API : échéance, verrou et planification sont
  testés sans réseau ni horloge réelle (13 tests). Le branchement dans `client.ts` n'a
  ensuite demandé que six lignes, elles-mêmes couvertes par 6 tests de rejeu.
- **Le routeur maison a tenu la promesse de l'ADR** : 60 lignes, zéro dépendance, et le
  chargement paresseux par route a fait tomber le paquet d'entrée de **302 à 235 kB**
  (`qrcode` sort du chemin critique avec `RecoltesVue`).
- **La pagination n'a rien cassé** : sans `page` ni `taille`, la réponse est exactement
  celle d'avant. Aucun test d'intégration existant n'a eu à être retouché.

### Ce qui peut être amélioré / limites assumées

- **Le total voyage dans un en-tête** (`X-Total-Count`) plutôt que dans une enveloppe
  JSON. C'est un choix : envelopper la liste aurait changé la forme de la réponse selon
  la présence d'un paramètre, contrat indescriptible en OpenAPI. Le revers est qu'un
  client qui ignore les en-têtes ne voit pas le total.
- **Pagination livrée sur 7 listes** (fermiers, fermes, sites, ruches, agents,
  plannings, tâches). Visites et récoltes ont un chargement composite (photos, tri
  métier) : leur pagination demande un passage dédié.
- **Le flux OIDC reste non joué en CI.** US-050 est couverte par 19 tests, mais tous
  avec un Keycloak simulé — c'est précisément l'angle mort qui avait laissé passer
  l'absence de rafraîchissement.
- **Prettier toujours pas imposé** ; les 8 vulnérabilités `high` de développement
  (`brace-expansion` via ESLint 9) sont inchangées.

### Défauts trouvés en chemin

1. **`Modale` volait le focus à chaque frappe.** Le piège de focus ajouté par US-054
   restituait le focus au déclencheur dans le nettoyage d'un `useEffect` dépendant de
   `onFermer` — une lambda recréée à chaque rendu. Taper dans un dialogue de saisie
   perdait donc le curseur à chaque caractère. Corrigé par une référence sur le
   callback et un effet monté une seule fois. **Trouvé par un test**, pas à l'œil.
2. **Deux boutons « Fermer » dans le même dialogue** : la croix de l'en-tête et
   l'action du dialogue d'information portaient le même nom accessible. L'action
   devient « J'ai compris ».

### Écart avec le plan

Le plan prévoyait la pagination au jour 10 et les dialogues au jour 14 ; l'ordre a été
inversé, US-052 touchant à la fois le back, la configuration métier et le front.

---

## 🔭 Reste de l'audit du front, non retenu ici

Ces points sont identifiés et chiffrés, mais tiennent hors des 40 de capacité :

- **Couverture des vues** — 34,9 % au global, mais **12,9 % sur `src/vues`** et
  12,1 % sur `src/auth` (13 pts).
- **Découpage du bundle** — 302 kB en un seul *chunk* ; en partie soldé par le
  `React.lazy` d'US-051, à confirmer par une mesure (3 pts).
- **Frontière d'erreur React** — aucune aujourd'hui : une exception de rendu donne un
  écran blanc (2 pts).
- **Échelle de la carte** — `CarteVue` fige 26 px/km ; un site à Paris et un à Cahors
  produisent un SVG d'environ 13 000 px de large (5 pts).
- **Client d'API généré depuis OpenAPI** — `client.ts:3` porte lui-même
  l'avertissement ; `types.ts` (499 lignes) est recopié à la main d'un contrat publié
  depuis le SPRINT-06 (8 pts).
- **Jeton en `localStorage`** — durcissement propre = cookie `HttpOnly` via un BFF,
  donc une décision d'architecture, pas une tâche de sprint.
- **Reformatage Prettier** — 28 fichiers hors format ; un commit dédié, isolé.

*Dernière mise à jour : 25/07/2026*
