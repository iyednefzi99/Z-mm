# 🏃 SPRINT-16 : Sessions sans jeton et portée par affectation

**Thème :** Fermer les deux derniers écarts du modèle d'autorisation
**Objectif :** Que le navigateur ne détienne plus de jeton, et qu'un agent ne puisse plus énumérer le parc entier de son exploitation
**Période :** 2027-03-16 → 2027-03-29 (14 jours)
**Story Points :** 34 / Capacity : 40

---

## 📅 Cérémonies Scrum

| Cérémonie | Date/Heure | Durée |
|:---|:---|:---|
| Sprint Planning | 2027-03-16 09:00-13:00 | 4h |
| Daily Scrum | Tous les jours 09:15 (15 min) | 15 min |
| Sprint Review | 2027-03-29 14:00-16:00 | 2h |
| Sprint Retrospective | 2027-03-29 16:00-17:00 | 1h |

---

## 📋 User Stories

| ID | Story | Points | Statut |
|:---|:---|:---:|:---|
| US-073 | BFF : jetons détenus par le serveur (ADR-006) | 21 | 🟢 Livré |
| US-057 | Portée d'autorisation par affectation d'agent | 13 | 🟢 Livré |

---

## 🔎 US-057 — la RLS isolait les exploitations, pas les agents

Une exploitation ne voyait pas les données d'une autre (ADR-001). Mais **à
l'intérieur** d'une exploitation, tout le monde voyait tout : un saisonnier, un
stagiaire ou un compte compromis pouvait énumérer l'intégralité du parc — toutes
les ruches, tous les sites, donc **la carte des ruchers**.

C'est l'écart signalé depuis le SPRINT-12 dans `PolitiquePositions` : arrondir les
coordonnées limite ce qu'un profil non propriétaire lit d'un site, mais ne
l'empêche pas de tous les lister.

### Où la règle est posée

**Dans le SGBD**, comme l'isolation de tenant. La poser dans les services
reviendrait à ajouter un `WHERE` à chaque requête et à espérer que personne ne
l'oublie — précisément le mode de défaillance que la RLS a été introduite pour
rendre impossible.

Deux variables de session, posées par l'application avec le tenant et **dans la
même requête préparée** : les dissocier ouvrirait une fenêtre pendant laquelle le
tenant serait posé et la portée non, donc pendant laquelle une requête verrait
toute l'exploitation.

| Profil | Portée |
|---|---|
| responsable, admin | toute l'exploitation — c'est leur fonction |
| capteur | globale, mais le RBAC le borne au seul dépôt de mesures |
| apiculteur, superviseur | leurs ruches, et ce qui s'y rattache |

**L'absence de portée vaut « rien voir », jamais « tout voir ».** C'est le mode de
défaillance qui compte : une tâche planifiée, une connexion du pool reprise hors
contexte ou un test mal isolé ne doivent pas ouvrir le parc.

### Le lien entre le compte et l'agent

Une colonne `sujet_oidc` porte le claim `sub` — identifiant stable chez le
fournisseur. Le courriel ne convient pas comme clé : il change, et un changement
silencieux romprait l'affectation sans que rien ne le signale. La liaison se fait
**à la première connexion**, par le courriel, puis le sujet fait foi. Aucune
reprise de données n'est donc nécessaire.

### Ce qui n'est pas filtré, et pourquoi

`fermier` et `ferme` restent visibles — un agent doit savoir pour qui il travaille,
et ces tables ne portent aucune position. `recolte`, `reine`, `photo` sont
rattachées à une ruche déjà filtrée. Ces choix sont à rejuger si l'une de ces
tables se met à porter une donnée de localisation.

---

## 🎯 Sprint Review - Démonstration

**Date :** 2027-03-29 14:00-16:00

Les deux US de ce sprint ferment des trous d'autorisation. Elles se démontrent
donc comme le SPRINT-12 : en **tentant l'attaque** devant les parties prenantes,
sur l'image d'avant puis sur celle du jour.

**US-073 — le navigateur ne détient plus de jeton.**

1. `localStorage` et `sessionStorage` ouverts dans la console du navigateur, en
   séance. Avant : le jeton d'accès et le jeton de rafraîchissement s'y lisent en
   clair. Après : **rien**. La session tient dans un cookie `HttpOnly`, `Secure`,
   `SameSite`, que `document.cookie` ne rend pas.
2. Exécution d'un extrait de code hostile dans la console — le geste que simule une
   XSS. Avant : le jeton est exfiltrable en une ligne. Après : il n'y a rien à
   exfiltrer ; le BFF rattache la requête à la session côté serveur.
3. Le flux OIDC complet est rejoué : l'échange du code, le rafraîchissement et la
   révocation se passent **entre le BFF et Keycloak**, jamais dans le navigateur.
   Déconnexion : le cookie est invalidé et la session SSO fermée.

C'est la dette ouverte au SPRINT-12 par l'[ADR-006](../06_decisions/ADR-006-stockage-des-jetons.md),
acceptée alors comme bloquante pour la mise en production. Elle est soldée.

**US-057 — un agent ne peut plus énumérer le parc.**

4. Connexion avec un compte **apiculteur** affecté à trois ruches sur un parc de
   plus de deux cents. `GET /api/ruches` : trois lignes. `GET /api/sites` : les
   seuls sites où il intervient. Avant ce sprint, la même requête rendait
   **l'intégralité du parc — donc la carte des ruchers**.
5. Connexion **responsable** sur le même jeu : tout le parc, c'est sa fonction.
6. Le point qui compte, joué explicitement : une requête émise **sans portée
   posée** (tâche planifiée, connexion du pool reprise hors contexte) ne rend
   **rien**. L'absence de portée vaut « rien voir », jamais « tout voir ».
7. Changement d'adresse électronique d'un agent déjà lié, puis reconnexion :
   l'affectation tient. Le `sub` OIDC fait foi, pas le courriel — un courriel qui
   change silencieusement romprait l'affectation sans que rien ne le signale.

**Ce que la revue a retenu.** La première version des tests passait par l'API et
était **verte à tort** : en test, l'application se connecte avec le propriétaire de
la base, qui contourne la RLS. Les tests ont été refaits sous le rôle applicatif
`zumm_app`. La règle posée en séance : *un test vert ne dit rien tant qu'on n'a pas
vérifié qu'il peut rougir.*

**Réserve inscrite au sprint suivant.** La session serveur suppose une affinité de
session ou un stockage partagé le jour d'une réplication horizontale.

---

## ⚠️ Risques Identifiés

| Risque | Impact | Mitigation |
|:---|:---|:---|
| Le BFF devient un point de passage unique de **toutes** les requêtes | Une régression y coupe l'application entière | Livré derrière les 109 tests d'intégration existants ; aucun changement de contrat côté API métier |
| Session serveur et réplication horizontale | Déconnexions aléatoires le jour d'un second nœud | Limite explicitement consignée, portée au SPRINT-17 |
| Poser la portée dans les services plutôt qu'en base | Un `WHERE` oublié dans une requête = fuite silencieuse | Règle posée **dans le SGBD**, comme l'isolation de tenant (ADR-001) |
| Poser le tenant et la portée dans deux requêtes distinctes | Fenêtre pendant laquelle une requête voit toute l'exploitation | Les deux variables de session sont posées **dans la même requête préparée** |
| Lier le compte à l'agent par le courriel | Affectation rompue en silence au premier changement d'adresse | Colonne `sujet_oidc` sur le claim `sub` ; le courriel ne sert qu'à la première liaison |
| Filtrer trop large et casser une fonctionnalité métier | Un agent ne sait plus pour qui il travaille | `fermier` et `ferme` non filtrés — aucune position portée ; choix à rejuger si ces tables évoluent |

---

## 📊 Burndown Chart

| Jour | Reste à faire (idéal) | Reste à faire (réel) | Notes |
|:---|:---:|:---:|:---|
| Jour 1 | 34 | 34 | Reprise de l'ADR-006 ; conception du BFF et du modèle de portée |
| Jour 4 | 26 | 28 | US-073 : échange de code côté serveur, cookie de session — plus lourd que chiffré |
| Jour 7 | 18 | 18 | US-073 : rafraîchissement et révocation déplacés côté BFF ; front dépouillé de ses jetons |
| Jour 9 | 13 | 13 | US-073 livrée ; `localStorage` vide, prouvé par test |
| Jour 11 | 8 | 10 | US-057 : `V16`, `sujet_oidc`, `ResolveurPortee`, `FiltrePortee` |
| Jour 13 | 3 | 5 | **Tests refaits sous `zumm_app`** : la première version était verte à tort |
| Jour 14 | 0 | 0 | 55 unitaires + 109 d'intégration, `Skipped: 0` |

*Écart au plan : deux jours perdus au jour 13 à refaire des tests qui passaient
déjà. C'est la dépense la plus rentable du sprint — ils ne prouvaient rien.*

---

## ✅ Definition of Done

- [x] `./mvnw verify` : 55 unitaires + **109** d'intégration, **Skipped : 0**
- [x] Restriction **prouvée sous le rôle applicatif réel** — en test, l'application
      se connecte avec le propriétaire, qui contourne la RLS : un test passant par
      l'API n'aurait rien prouvé
- [x] Le fait que l'application **pose** les variables est testé séparément :
      des politiques justes mais non alimentées resteraient lettre morte

## 🔁 Rétrospective

**Ce qui a marché.** Se demander « ce test prouverait-il quelque chose si je
retirais la protection ? ». La réponse était non pour la première version, qui
passait par l'API : l'application se connecte en test avec le propriétaire de la
base, lequel ignore la RLS. Le test a été refait sous `zumm_app`.

**Ce qu'on retient.** Un test vert ne dit rien tant qu'on n'a pas vérifié qu'il
peut rougir.

**Ce qui reste ouvert.** Agrégats des tableaux de bord calculés en Java ; client
d'API front toujours écrit à la main alors que le contrat OpenAPI existe ;
`TableauDeBordService` porte trois tableaux ; une session serveur suppose une
affinité ou un stockage partagé le jour d'une réplication horizontale.
