# 🏃 SPRINT-20 : Le registre sanitaire

**Thème :** Faire dire aux actes sanitaires ce qu'ils contiennent, et non seulement qu'ils ont eu lieu
**Objectif :** Qu'un traitement porte son produit, sa dose et son délai de carence ; que « varroa » existe ailleurs que dans une phrase ; et que ce qu'on observe au rucher se compte
**Période :** 2027-05-11 → 2027-05-24 (14 jours)
**Story Points :** 35 / Capacity : 40

> ✅ **Sprint clos le 29/08/2026.** Les sept stories sont livrées de la migration
> à l'écran, et les 35 points comptés. La fiche a d'abord existé en version
> partielle, arrêtée au 26/08/2026 alors que seuls le schéma et le domaine
> étaient posés ; les sections de clôture ci-dessous ont été remplies **après**
> la revue, pas avant.

---

## 📅 Cérémonies Scrum

| Cérémonie | Date/Heure | Durée |
|:---|:---|:---|
| Sprint Planning | 2027-05-11 09:00-11:00 | 2h |
| Daily Scrum | Tous les jours 09:15 (15 min) | 15 min |
| Sprint Review | 2027-05-24 14:00-15:30 | 1h30 |
| Sprint Retrospective | 2027-05-24 15:30-16:30 | 1h |

---

## 📋 User Stories

| ID | Story | Points | Statut |
|:---|:---|:---:|:---|
| US-086 | Traitement sanitaire comme entité de plein droit | 8 | ✅ Terminé |
| US-087 | Nourrissements | 5 | ✅ Terminé |
| US-088 | Comptage de varroa et taux calculé selon la méthode | 8 | ✅ Terminé |
| US-089 | Pathologies nommées par visite | 3 | ✅ Terminé |
| US-090 | Observations d'inspection structurées | 5 | ✅ Terminé |
| US-091 | Météo figée sur la visite | 3 | ✅ Terminé |
| US-092 | Référentiel de la ruche : type, couleur, origine, cause de clôture | 3 | ✅ Terminé |

---

## 🔎 Pourquoi ce sprint

`docs/ECART-CONCURRENTS.md` classe les manques par coût d'opportunité
décroissant. Les trois premiers sont ici, et ils tiennent ensemble :

1. **Traitements, nourrissements et varroa n'étaient qu'une valeur de
   `RaisonVisite`.** On savait qu'on avait traité — jamais avec quoi, à quelle
   dose, ni sous quel délai de carence. C'est ce dernier qui bloque tout registre
   d'élevage opposable : sans lui, **rien n'interdit de récolter du miel encore
   sous traitement**.
2. **Le varroa n'était nommé nulle part** dans le dépôt, hors de la prose du jeu
   de démonstration — alors que huit des douze catalogues concurrents en font un
   argument, plusieurs un module complet.
3. **Les observations d'inspection vivaient en texte libre.** Couvain, réserves,
   cellules royales : rien n'en était analysable, ce qui plafonnait tout le
   module analytique en aval — corrélations, score de santé, index génétique.

C'est aussi le socle des écarts suivants : ni registre réglementaire, ni
corrélation météo × production, ni tâche engendrée par un délai de carence ne
sont possibles avant lui.

## 🔎 Les décisions structurantes, et leur raison

**Une seule migration pour tout le bloc.** Chaque table ajoutée demande la même
revue — `tenant_id`, politique RLS, clé étrangère **composite**. Les séparer en
trois migrations aurait triplé la revue sans rien isoler, puisque rien ne se livre
utilement sans les trois.

**Trois tables, et non une table « intervention » discriminée par un type.** La
tentation existait. Elle est écartée : la dose et le délai de carence n'ont de
sens que pour un traitement, le motif que pour un nourrissement, la méthode de
comptage que pour le varroa. Une table unique aurait rendu **nullable tout ce qui
fait la valeur de chaque acte**, et aucune contrainte n'aurait plus pu exiger ce
qui est obligatoire — c'est exactement le défaut qu'on corrige.

**La fin de carence est une colonne générée ; le taux d'infestation ne l'est
pas.** Les deux cas se ressemblent et se tranchent en sens inverse, ce qui mérite
d'être écrit :

| | `traitement.date_retrait` | Taux de varroa |
|---|---|---|
| Nature | **Donnée dérivée** — `date_fin + délai` | **Règle métier** à expliquer |
| Sert à | Filtrer et indexer : « quelles ruches sous carence ? » | Rendre un verdict à l'utilisateur |
| Où | Colonne `GENERATED ... STORED` + index partiel | `ComptageVarroaService` |
| Pourquoi pas l'inverse | La calculer en Java obligerait à relire toutes les lignes | Un lange donne des varroas/jour, un lavage des varroas pour cent abeilles : **un champ unique mélangeant les deux serait faux** |

C'est la même ligne de partage que la règle des 100 % de `lot_composition` (V15) :
ce qu'on doit savoir **expliquer** ne se cache pas dans une colonne générée.

**Chaque méthode de comptage exige son dénominateur**, et refuse celui de
l'autre : `ck_varroa_denominateur`. Sans lui, une ligne serait incalculable —
donc inutile, et découverte comme telle des mois plus tard.

**Toutes les cases d'observation sont facultatives.** Une visite éclair — poser
une hausse — ne remplit rien, et exiger la grille complète ferait sauter la saisie
plutôt que la compléter. Le texte libre reste : il porte ce qu'aucune case ne
prévoit, mais il cesse d'être la **seule** trace.

**La météo de la visite est figée, jamais rappelée.** Une prévision se révise, un
relevé non — et c'est le relevé qui permet de corréler conditions et production.
Aller rechercher la météo du 12 mars six mois plus tard rendrait la corrélation
fausse sans que rien ne le signale.

**Valeurs contraintes par `@Pattern` plutôt que par des énumérations.** Sept jeux
de valeurs sur quatre entités auraient demandé autant d'énumérations et
d'`AttributeConverter`. Le `CHECK` en base reste la garantie dure ; l'annotation
la remonte au niveau du 400, avant l'aller-retour SQL. C'est le choix déjà fait
pour `SuiviReine`.

---

## 📦 Ce qui a été livré, couche par couche

| Couche | Livré |
|---|---|
| Base | `V19__sanitaire_observations_sprint20.sql` : quatre tables (`traitement`, `nourrissement`, `comptage_varroa`, `observation_pathologie`), chacune avec `tenant_id`, `ENABLE`/`FORCE ROW LEVEL SECURITY`, sa politique, sa clé étrangère **composite** `(id, tenant_id)` et son trigger `maj_le` ; quinze colonnes d'observation et de météo figée sur `visite`, quatre colonnes de référentiel sur `ruche` |
| Domaine | `Traitement`, `Nourrissement`, `ComptageVarroa`, `ObservationPathologie` — 23 classes `@Entity` dans l'arbre |
| Persistance | Quatre repositories, dont `TraitementRepository.sousCarenceAu` qui s'appuie sur l'index partiel plutôt que sur un balayage |
| Service | `TraitementService`, `NourrissementService`, `ComptageVarroaService` — taux et verdict calculés selon la méthode — et `VisiteService` étendu à la grille, à la météo et aux pathologies |
| Web | 8 DTO, 3 contrôleurs (`/api/traitements`, `/api/nourrissements`, `/api/varroa`), contrat OpenAPI régénéré **en une fois** pour tout le lot — 27 contrôleurs REST au total |
| Front | Écran `SanitaireVue` (trois registres, bandeau des carences), grille d'inspection et météo figée dans le formulaire de visite, référentiel dans le formulaire de ruche, types et parité dérivés du contrat |
| Tests | 92 unitaires + 133 d'intégration côté back (`Skipped: 0`), dont `RegistreSanitaireIT` (14) sous le rôle applicatif `zumm_app` ; 238 Vitest côté front |
| i18n | Les sept référentiels dans les trois locales — 732 chaînes par langue, parité tenue à la compilation |

**Ce que le SPRINT-19 avait laissé ouvert et qui se referme ici :** le contexte
météo réel et les prévisions à sept jours (extension d'US-029). Ce n'était pas un
hasard de calendrier — la météo **figée** d'US-091 n'a de sens que si une source
réelle existe à recopier. Une valeur simulée figée aurait donné une corrélation
météo × production bâtie sur du bruit.

**Ce que ce sprint N'a PAS fait, et qu'il faut dire :** le délai de carence est
consigné, calculé et affiché — il n'**interdit** rien. Une récolte reste
enregistrable sur une ruche sous carence. Le registre est opposable en lecture,
pas encore contraignant en écriture ; c'est un écart à part entière, porté au
backlog (voir la rétrospective).

---

## ⚠️ Risques identifiés

| Risque | Impact | Mitigation |
|:---|:---|:---|
| Quatre tables ajoutées d'un coup | Un `tenant_id`, une politique RLS ou une clé composite oubliés passeraient inaperçus jusqu'à une fuite inter-exploitations | Revue unique sur une migration unique, et test d'intégration d'isolation **par table**, joué sous `zumm_app` |
| Un taux d'infestation stocké en colonne | Deux unités confondues rendent faux le nombre **et** les seuils qu'on en tire | Comptages bruts + méthode en base ; le taux est calculé et expliqué au service |
| Une grille d'inspection obligatoire | La saisie au rucher est abandonnée au lieu d'être complétée | Toutes les cases facultatives ; le texte libre subsiste |
| Sept jeux de valeurs en `@Pattern` | Une valeur admise par la base et refusée par l'annotation, ou l'inverse | Les deux listes viennent du même bloc de migration ; à couvrir par un test qui tente une valeur hors référentiel |
| Le délai de carence existe mais n'interdit rien | On croit le registre opposable alors que la récolte reste permise sous traitement | **Hors de ce sprint, et à dire comme tel** : l'interdiction de récolte est un écart à part entière, à porter au backlog |
| Une requête native oubliée | Une seule barrière au lieu de deux, invisible en production où la RLS rattrape | Filtre de tenant explicite sur toute requête native — règle posée en rétrospective du SPRINT-18 |

---

## 🎯 Sprint Review — Démonstration

**Date :** 2027-05-24 14:00-15:30

1. **Un traitement de bout en bout.** Apivar, deux lanières, amitraze, du 1er au
   20 août, quatorze jours de carence. La fin de carence est calculée par la base
   (`date_fin + delai_carence_jours`) et non par le service ; la ruche apparaît
   aussitôt dans le bandeau « Carences en cours », **avec sa date de fin**. Une
   dose saisie sans unité est refusée avec un message qui dit lequel des deux
   champs manque — et le formulaire garde la saisie.
2. **Le même comptage de varroa, deux fois.** 21 varroas sur 3 jours de lange →
   7,00 varroas/jour, verdict « Traiter ». 6 varroas sur 300 abeilles au sucre
   glace → 2,00 % des abeilles, verdict « À surveiller ». Deux unités, deux
   seuils, et l'unité voyage **avec** la valeur jusqu'à l'écran. Une ligne dont
   le dénominateur ne correspond pas à la méthode est refusée par le service
   avant la base, avec un message explicable.
3. **Deux visites.** L'une remplie à la grille — œufs vus, ponte compacte, cinq
   cadres de couvain, varroose modérée constatée — l'autre éclair, qui ne remplit
   rien. Les deux sont acceptées, et la seconde ne stocke **aucun** « non » :
   « non observé » et « non » restent deux choses différentes, de la base au
   formulaire.
4. **La météo relevée depuis la visite.** Un bouton recopie l'instantané du site
   avec sa source (`open-meteo` ou `simulation`), et la valeur ne bouge plus. Une
   saisie à la main bascule la source à `saisie` : une estimation ne se lit pas
   comme une mesure.
5. **L'isolation des quatre nouvelles tables**, jouée sous `zumm_app` entre deux
   exploitations (`RegistreSanitaireIT`) — pas sous le propriétaire, qui ne
   prouverait rien (leçon du SPRINT-16).

**Ce que la revue a retenu.** Le registre se **lit** comme une contrainte et ne
s'**applique** nulle part : rien n'empêche d'enregistrer une récolte sur une ruche
sous carence. La séance a préféré livrer la donnée juste sans le blocage plutôt
que l'inverse — un blocage bâti sur une donnée qu'on ne saisit pas encore n'aurait
protégé personne — mais elle refuse de laisser le point implicite.

**Décision de la revue.** L'interdiction de récolte sous carence, les tâches
engendrées par un délai de carence et les interventions groupées (traiter
quarante ruches d'un geste) partent au backlog comme un lot cohérent : les trois
supposent le registre livré ici, et aucune ne se tient sans les deux autres.

---

## 📊 Burndown Chart

| Jour | Reste à faire (idéal) | Reste à faire (réel) | Notes |
|:---|:---:|:---:|:---|
| Jour 1 | 35 | 35 | Migration écrite d'un bloc, revue avant toute ligne de Java : `tenant_id`, RLS, clé composite vérifiés table par table |
| Jour 3 | 30 | 31 | Schéma et quatre entités posés ; **retard assumé** — la question « une table d'intervention ou trois ? » a été tranchée là, pas plus tard |
| Jour 6 | 24 | 22 | Repositories et services ; le calcul du taux et son verdict couverts par des tests unitaires avant tout endpoint |
| Jour 9 | 17 | 14 | DTO, trois contrôleurs, contrat OpenAPI régénéré **une seule fois** pour l'ensemble du lot |
| Jour 12 | 8 | 7 | `RegistreSanitaireIT` sous `zumm_app` ; grille et météo branchées sur la visite |
| Jour 14 | 0 | 0 | `SanitaireVue`, référentiel de la ruche, trois locales ; back et front verts |

*Écart au plan : en retard au tiers du sprint, en avance à la fin. La cause est
la même dans les deux sens — une migration unique, revue une fois, coûte cher au
départ et ne se repaye qu'à partir du moment où quatre couches s'écrivent en
parallèle sans jamais rouvrir le schéma. Il n'a été rouvert aucune fois.*

---

## ✅ Definition of Done

- [x] Back : `./mvnw -B verify` vert — **92 tests unitaires + 133 d'intégration**,
      `Skipped: 0`, couverture JaCoCo **81,9 %** en instructions et **65,3 %** en
      branches (planchers 80 % et 60 %)
- [x] Les quatre tables portent `tenant_id`, `ENABLE`/`FORCE ROW LEVEL SECURITY`,
      leur politique et une clé étrangère **composite** ; isolation vérifiée sous
      le rôle applicatif `zumm_app`, jamais sous le propriétaire
- [x] Contrat OpenAPI régénéré et `contrat.ts` avec lui ; six nouveaux types
      passés au vérificateur de parité (`api/parite.ts`)
- [x] Front : **238 tests** sur 26 fichiers, `npm run typecheck`, `npm run lint`
      (0 erreur, 10 avertissements `react-refresh`) et `npm run build` verts
- [x] Trois locales à **732 chaînes**, parité tenue à la compilation
- [x] Le nouvel écran est déclaré dans `routage/routes.ts` — famille, icône,
      palette de commandes — et l'invariant « un onglet, une famille » vérifié

## 🔁 Rétrospective

**Ce qui a marché.** Trancher par écrit, dans la migration elle-même, les deux
questions qui décidaient de tout : trois tables plutôt qu'une table
« intervention » discriminée par un type, et un taux calculé au service plutôt
qu'une colonne générée. Les deux se ressemblent — une donnée dérivée — et se
tranchent en sens inverse. Écrire *pourquoi* dans le fichier a évité de rejouer
le débat trois fois, une par couche.

**Ce qu'on retient.** La distinction entre « non » et « non observé » n'est pas
une subtilité de modélisation : c'est la seule chose qui rend la grille
d'inspection utilisable pour une statistique. Une case à cocher ordinaire l'aurait
détruite en silence, à l'endroit le plus banal du produit — un formulaire. Elle
est tenue de bout en bout : colonne `NULLABLE`, DTO qui rend `null` plutôt qu'un
objet vide, sélecteur à trois états dans le formulaire, et un test qui échouerait
si l'un des trois cédait.

**Ce qui reste ouvert.**

1. **Le délai de carence n'interdit pas la récolte.** Il est consigné, calculé,
   affiché, mais aucune règle ne refuse un enregistrement de récolte sur une ruche
   sous carence. C'est le premier candidat du sprint suivant, et il ne coûte plus
   qu'une vérification — la donnée, elle, existe.
2. **Les interventions groupées.** Toutes les mutations restent unitaires : sur un
   rucher de quarante ruches, un traitement se saisit quarante fois. Le registre
   est juste, il n'est pas encore utilisable à l'échelle qu'il prétend viser
   (§ 2 de `docs/ECART-CONCURRENTS.md`).
3. **Les tâches engendrées par un événement.** Le retrait d'un traitement après
   sa carence est le cas d'école : la règle est mécanique, la valeur immédiate,
   et `TacheService` ne fait toujours qu'enregistrer.
4. **Le rapport PDF de visite n'imprime pas la grille.** Il imprime les
   constatations en texte libre, comme avant. Ce n'est pas bloquant — rien n'est
   perdu — mais le document remis à un vétérinaire reste moins riche que l'écran.

*Dernière mise à jour : 29/08/2026 — sprint clos.*
