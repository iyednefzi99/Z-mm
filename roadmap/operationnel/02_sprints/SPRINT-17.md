# 🏃 SPRINT-17 : Dettes techniques

**Thème :** Solder les dettes consignées, plutôt que les reconduire d'un sprint à l'autre
**Objectif :** Que les tableaux de bord tiennent sur un parc réel, que le contrat publié soit vérifié, et que la session serveur survive à un redéploiement
**Période :** 2027-03-30 → 2027-04-12 (14 jours)
**Story Points :** 29 / Capacity : 40

> **Origine.** Ce sprint ne livre aucune fonctionnalité nouvelle. Il traite les
> cinq dettes que `docs/ARCHITECTURE-SOLID.md` recensait — une liste qui n'a de
> valeur que si elle finit par être vidée.

---

## 📅 Cérémonies Scrum

| Cérémonie | Date/Heure | Durée |
|:---|:---|:---|
| Sprint Planning | 2027-03-30 09:00-13:00 | 4h |
| Daily Scrum | Tous les jours 09:15 (15 min) | 15 min |
| Sprint Review | 2027-04-12 14:00-16:00 | 2h |
| Sprint Retrospective | 2027-04-12 16:00-17:00 | 1h |

---

## 📋 User Stories

| ID | Story | Points | Statut |
|:---|:---|:---:|:---|
| US-074 | Agrégats des tableaux de bord calculés en base | 8 | 🟢 Livré |
| US-075 | Scission de `TableauDeBordService` | 3 | 🟢 Livré |
| US-076 | Parité garantie entre client TypeScript et contrat OpenAPI | 8 | 🟢 Livré |
| US-077 | Couche anticorruption vers le microservice IA | 5 | 🟢 Livré |
| US-078 | Sessions serveur persistées en base | 5 | 🟢 Livré |

---

## 🔎 Ce qui a été fait

### US-074 — les agrégats descendent en base

`production()` chargeait **toutes** les mesures de poids du tenant pour les
réduire en mémoire ; `alertesSanitaires()` lisait **tout** l'historique des
visites pour n'en garder qu'une ligne par ruche. À raison d'un relevé par quart
d'heure, une exploitation de 500 ruches produit des dizaines de millions de
mesures par an : ce n'était pas lent, c'était intenable — la seule lecture aurait
saturé le tas avant d'agréger quoi que ce soit.

`last(valeur, instant)` est une agrégation TimescaleDB, et c'est exactement ce
pour quoi l'extension a été retenue (ADR-002) : la dernière valeur d'une série
sans la trier entièrement. `DISTINCT ON` rend la dernière visite de chaque ruche
en une requête.

### US-075 — un tableau de bord, une raison de changer

`TableauDeBordService` portait trois tableaux. Scindé en `CalendrierService`,
`ProductionService` et `AlerteSanitaireService`.

Le calendrier **garde** son agrégation en mémoire, et c'est délibéré : sa requête
est bornée par une période, ce qui limite le volume par construction. Toutes les
boucles ne sont pas des défauts ; seules celles qui balaient un historique entier
en sont.

### US-076 — la parité, plutôt que la génération

Le client était écrit à la main alors que le serveur publie un contrat. Le
remplacer par un client généré aurait touché quarante fonctions et seize vues pour
un gain limité au typage — les fonctions sont courtes, lisibles et stables.

Ce qui manquait était la **garantie**. `api/parite.ts` confronte chaque type écrit
à la main au contrat publié : une divergence casse `tsc`, donc la chaîne, en
nommant le champ fautif. La CI vérifie en outre que le contrat versionné
correspond à ce que l'application publie, et que les types générés en dérivent —
même règle de fraîcheur que pour les PDF du cahier des charges.

**Le dispositif a payé dès sa première exécution.** Il a révélé que le contrat
décrivait `LocalTime` comme un objet `{hour, minute, second, nano}` alors que
l'API sérialise `"14:30:00"`. Le défaut était dans le **contrat**, pas dans le
client — et il aurait produit du code cassé chez tout intégrateur tiers générant
son client depuis notre contrat (US-026). Corrigé au niveau du type, pas champ
par champ, pour que la règle vaille aussi pour les futurs.

### US-077 — le domaine ne traverse plus la frontière

`AnomalieService` dépendait du client HTTP concret et lui passait des entités
JPA : le domaine traversait la frontière, et l'adaptateur connaissait le modèle de
persistance. Un port `MoteurAnomalie` les sépare, avec un type d'entrée neutre —
un instant, une valeur.

Bénéfice immédiat et mesurable : le test unitaire n'instancie plus un client
réseau pour ne surtout pas s'en servir ; un double du port suffit.

### US-078 — la session survit au redéploiement

Le BFF (ADR-006) avait introduit une session serveur, et l'ADR signalait lui-même
la dette : en mémoire, elle interdit de répliquer le back-end et fait perdre
toutes les sessions à chaque redémarrage — y compris un simple déploiement.

Déportée dans PostgreSQL, déjà présent, déjà sauvegardé, déjà surveillé. Le schéma
est créé par **Flyway** et non par Spring Session : Flyway est seul propriétaire
du schéma, et une seconde autorité sur les tables réintroduirait le désordre que
cette règle existe pour éviter.

Ces deux tables ne portent ni `tenant_id` ni politique RLS, et c'est justifié :
une session est créée **avant** que le tenant soit connu — il se lit dans le
jeton, donc après l'ouverture de session. Lui imposer la convention multi-tenant
rendrait la connexion impossible.

---

## 🎯 Sprint Review - Démonstration

**Date :** 2027-04-12 14:00-16:00

Un sprint de dette ne se démontre pas par un écran nouveau — il n'en produit
aucun. Il se démontre en **remettant l'application dans les conditions où la dette
faisait mal**, sur l'image du SPRINT-16 puis sur celle du jour.

1. **Le tableau de bord sur un parc réel.** Jeu de 500 ruches et **huit millions
   de relevés**, monté avant la séance. Avant : la page de production ne rend pas,
   le processus part en `OutOfMemoryError` au bout de quelques dizaines de
   secondes. Après : elle s'affiche, et le plan d'exécution est projeté —
   `last(valeur, instant)` de TimescaleDB rend la dernière valeur d'une série sans
   la trier entièrement, `DISTINCT ON` rend la dernière visite de chaque ruche en
   une requête. La démonstration insiste sur le point : ce n'était pas une question
   de lenteur, c'était une question de faisabilité.
2. **Le calendrier, lui, ne change pas — et c'est délibéré.** Même écran, même
   code d'agrégation en mémoire. Sa requête est bornée par une période : toutes les
   boucles ne sont pas des défauts, seules celles qui balaient un historique entier
   en sont.
3. **La scission n'a rien changé de visible.** Les trois services sont montrés à
   côté de l'ancien fichier de 202 lignes, puis la même requête HTTP est rejouée
   sur les deux images : réponse **identique**. Une scission qui change le contrat
   n'est pas une scission.
4. **La dérive de contrat est provoquée devant témoins.** Un champ de DTO est
   renommé côté serveur, le contrat régénéré, `npm run typecheck` lancé : **`tsc`
   échoue en nommant le champ**. Avant ce sprint, le même geste franchissait toutes
   les portes et se manifestait en production par un champ vide.
5. **Le microservice d'analyse est débranché en séance.** `docker compose stop
   ia-service` pendant que la vue d'anomalie est ouverte : la consultation
   continue, servie par le repli EWMA local. Puis un double du port est substitué
   en direct — `AnomalieService` n'est pas modifié d'une ligne.
6. **Le redéploiement ne déconnecte plus personne.** Session ouverte, saisie en
   cours, `docker compose restart backend` en direct. Avant : retour à l'écran de
   connexion, saisie perdue. Après : la session est relue dans PostgreSQL,
   l'utilisateur ne voit rien passer. Un second nœud back-end est démarré derrière
   le proxy pour montrer que la réplication horizontale est désormais possible.

**Le point qui a le plus marqué la revue.** Le contrôle de parité a trouvé un
défaut réel **à sa première exécution**, et un défaut dans le contrat *publié* —
`LocalTime` y était décrit comme `{hour, minute, second, nano}` alors que l'API
sérialise `"14:30:00"`. Tout intégrateur tiers générant son client depuis notre
contrat (US-026) aurait produit du code cassé. Corrigé au niveau du type, pour que
la règle vaille aussi pour les champs à venir.

**Décision de la revue.** La dette « agrégats en mémoire » est déclarée soldée
**pour la production et les alertes seulement**. `SyntheseService` itère toujours
sur `findAll()` : volume moindre, même trajectoire. Inscrite au SPRINT-18 avec une
date, plutôt que reconduite une quatrième fois en « ce qui reste ouvert ».

---

## ⚠️ Risques Identifiés

| Risque | Impact | Mitigation |
|:---|:---|:---|
| Descendre les agrégats en SQL contourne la RLS si la requête est mal écrite | Fuite inter-tenant sur le tableau de bord — le pire défaut possible | Requêtes jouées **sous le rôle applicatif `zumm_app`**, jamais sous le propriétaire (leçon du SPRINT-16) |
| Scinder un service utilisé par un contrôleur exposé | Régression de contrat invisible en test unitaire | Réponse comparée avant/après ; aucun test d'intégration du contrôleur n'est retouché |
| Un port mal typé laisse quand même fuir le modèle | La couche anticorruption ne corrige rien | Type d'entrée réduit à `(instant, valeur)` : l'adaptateur ne peut plus compiler contre Hibernate |
| Un moteur d'anomalie qui **lève** au lieu de rendre vide | Substitution de Liskov rompue — chaque appelant devrait connaître l'implémentation | Contrat explicite dans la Javadoc du port, et test de l'indisponibilité |
| Deux artefacts dérivés versionnés de plus (`openapi.json`, `contrat.ts`) | Ils divergent de leur source sans que rien ne le voie | Contrôle de fraîcheur en CI, sur le modèle de celui des PDF du cahier |
| Spring Session prend la main sur le schéma | Deux autorités sur les tables, migrations non reproductibles | `initialize-schema: never` ; le schéma vient de `V17` |
| Exception à la règle « toute table porte `tenant_id` et sa RLS » | Précédent invoqué plus tard pour de vraies tables métier | Exception **motivée dans la migration elle-même**, et bornée à l'infrastructure d'authentification |

---

## 📊 Burndown Chart

| Jour | Reste à faire (idéal) | Reste à faire (réel) | Notes |
|:---|:---:|:---:|:---|
| Jour 1 | 29 | 29 | Cadrage : cinq dettes recensées, chacune qualifiée avant d'être ouverte |
| Jour 3 | 24 | 24 | US-078 : `V17`, Spring Session JDBC, `initialize-schema: never` |
| Jour 5 | 20 | 20 | US-075 : scission en trois services, contrat inchangé |
| Jour 8 | 13 | 15 | US-074 : agrégats SQL ; rejeu sous `zumm_app` imposé en cours de route |
| Jour 10 | 9 | 9 | US-074 livrée, mesurée sur 8 M de relevés |
| Jour 12 | 4 | 4 | US-077 : port `MoteurAnomalie`, `ClientAnomalieIA` devient adaptateur |
| Jour 14 | 0 | 0 | US-076 : contrat publié, `parite.ts`, deux portes de fraîcheur en CI |

*Écart au plan : +2 points au jour 8. La première version des requêtes
d'agrégation était testée sous le propriétaire de la base — exactement le piège du
SPRINT-16. Détecté cette fois **par la rétrospective précédente**, pas par hasard.*

---

## ✅ Definition of Done

- [x] `./mvnw verify` : 55 unitaires + **110** d'intégration, **Skipped : 0**
- [x] Front : 101 tests, 0 erreur ESLint, build OK
- [x] Seuil de couverture tenu
- [x] Deux contrôles de fraîcheur ajoutés à la CI (contrat, types générés)

## 🔁 Rétrospective

**Ce qui a marché.** Le contrôle de parité a trouvé un défaut réel dès sa première
exécution — et un défaut dans le contrat **publié**, c'est-à-dire celui que des
tiers consomment. Un contrôle qui ne trouve rien à sa mise en place est rarement
un bon contrôle.

**Ce qu'on retient.** Toutes les dettes ne se soldent pas de la même manière.
Trois ont demandé du code, une a demandé un arbitrage — écrire une garantie
plutôt que réécrire quarante fonctions — et une a surtout demandé de nommer
précisément ce qui manquait.

**Ce qui reste ouvert.** `SyntheseService` agrège encore en mémoire, sur un volume
moindre ; les courbes journalières gagneraient un agrégat continu TimescaleDB.
→ **Soldés au [SPRINT-18](SPRINT-18.md)** — dont un autrement que prévu.
