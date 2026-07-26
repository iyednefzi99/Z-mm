# 🏃 SPRINT-17 : Dettes techniques

**Thème :** Solder les dettes consignées, plutôt que les reconduire d'un sprint à l'autre
**Objectif :** Que les tableaux de bord tiennent sur un parc réel, que le contrat publié soit vérifié, et que la session serveur survive à un redéploiement
**Période :** 2027-03-30 → 2027-04-12 (14 jours)
**Story Points :** 29 / Capacity : 40

> **Origine.** Ce sprint ne livre aucune fonctionnalité nouvelle. Il traite les
> cinq dettes que `docs/ARCHITECTURE-SOLID.md` recensait — une liste qui n'a de
> valeur que si elle finit par être vidée.

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
moindre ; les courbes journalières gagneraient un agrégat continu TimescaleDB. Les
deux sont consignés dans `docs/ARCHITECTURE-SOLID.md`.
