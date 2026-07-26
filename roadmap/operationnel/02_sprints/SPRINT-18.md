# 🏃 SPRINT-18 : Les deux dernières dettes

**Thème :** Vider la liste des dettes, y compris celle qui s'est révélée impossible
**Objectif :** Que la synthèse cesse d'être la page la plus coûteuse de l'application, et que les courbes ne transportent plus cent fois trop de points
**Période :** 2027-04-13 → 2027-04-26 (14 jours)
**Story Points :** 13 / Capacity : 40

---

## 📅 Cérémonies Scrum

| Cérémonie | Date/Heure | Durée |
|:---|:---|:---|
| Sprint Planning | 2027-04-13 09:00-11:00 | 2h |
| Daily Scrum | Tous les jours 09:15 (15 min) | 15 min |
| Sprint Review | 2027-04-26 14:00-15:00 | 1h |
| Sprint Retrospective | 2027-04-26 15:00-16:00 | 1h |

---

## 📋 User Stories

| ID | Story | Points | Statut |
|:---|:---|:---:|:---|
| US-079 | Synthèse de pilotage agrégée en base | 5 | 🟢 Livré |
| US-080 | Courbes journalières agrégées côté serveur | 8 | 🟡 Livré **autrement** — voir ci-dessous |

---

## 🔎 US-079 — la synthèse portait le défaut déjà corrigé ailleurs

`poidsTotalActuel()` chargeait **toutes** les mesures de poids du tenant pour n'en
retenir qu'une valeur par ruche — exactement ce que le SPRINT-17 avait corrigé sur
le tableau de bord, mais laissé ici. La synthèse comptait par ailleurs les motifs
de visite en parcourant l'historique, et mesurait le nombre d'alertes ouvertes en
chargeant leur liste.

Autrement dit, **l'écran d'accueil du responsable était la page la plus coûteuse
de l'application**. Tout est désormais agrégé en base ; la somme des poids
réutilise l'agrégat déjà calculé pour le tableau de bord.

## 🔎 US-080 — l'agrégat continu est impossible, le bénéfice ne l'est pas

L'intention était d'entretenir un `CONTINUOUS AGGREGATE` TimescaleDB. PostgreSQL
refuse :

```
ERROR: cannot create continuous aggregate on hypertable with row security
```

C'est la **seconde** manifestation du conflit tranché par l'ADR-008 après la
compression. Le contournement envisagé — matérialiser sans RLS puis n'exposer
qu'une vue `security_barrier` filtrante — n'a même pas pu être tenté : le refus
tombe à la **création**, pas à la lecture.

L'ADR-008 a donc été **généralisé** : sous RLS, aucune fonctionnalité
*matérialisante* de TimescaleDB n'est disponible. Restent le partitionnement, les
index, `time_bucket` et les agrégations `last`/`first` — c'est-à-dire l'essentiel.

**Le bénéfice recherché a été obtenu autrement** : une agrégation `time_bucket` à
la demande, exposée par `GET /api/mesures/journalier`. Le volume transporté tombe
de ~105 000 points à ~1 100 pour trois ans d'historique d'une ruche, pour un
graphique identique à l'œil — un tracé de 640 pixels de large ne montrera jamais
plus de points que sa largeur. Ce qu'on perd est la mémorisation : le résultat est
recalculé à chaque appel.

La série **brute** reste disponible pour la détection d'anomalie, qui a besoin du
détail.

---

## 🎯 Sprint Review - Démonstration

**Date :** 2027-04-26 14:00-15:00

Revue courte, et sur un sujet inconfortable : **une des deux user stories n'a pas
été livrée comme elle avait été planifiée**. C'est ce qui en fait la séance la
plus utile du sprint.

1. **L'écran d'accueil du responsable.** Avant / après sur le même jeu de
   données, chronomètre à l'écran. `poidsTotalActuel()` chargeait toutes les
   mesures de poids du tenant pour n'en garder qu'une par ruche — exactement le
   défaut corrigé au SPRINT-17 sur le tableau de bord, et laissé ici. Le comptage
   des motifs de visite parcourait l'historique, le nombre d'alertes ouvertes
   chargeait leur liste. La page la plus coûteuse de l'application était sa page
   d'accueil.
2. **L'échec de US-080, montré tel quel.** La commande de création d'agrégat
   continu est tapée en séance :
   `ERROR: cannot create continuous aggregate on hypertable with row security`.
   Le contournement envisagé — matérialiser hors RLS puis n'exposer qu'une vue
   filtrante — n'a même pas pu être tenté : le refus tombe à la **création**, pas
   à la lecture. Seconde manifestation du conflit tranché par l'ADR-008, après la
   compression au SPRINT-14.
3. **Le bénéfice obtenu autrement.** `GET /api/mesures/journalier` agrège à la
   demande. Trois ans d'historique d'une ruche : **~105 000 points transportés
   avant, ~1 100 après**, et les deux courbes sont projetées côte à côte —
   indiscernables. Un tracé de 640 pixels de large ne montrera jamais plus de
   points que sa largeur. Ce qui est perdu est la mémorisation : le résultat est
   recalculé à chaque appel, et c'est dit explicitement.
4. **La série brute reste disponible** pour la détection d'anomalie, qui a besoin
   du détail — démontré en ouvrant la vue d'anomalie sur la même ruche.

**Ce que la revue a retenu, et qui dépasse ce sprint.** Une requête **native**
échappe au discriminant `@TenantId` d'Hibernate, qui ne réécrit que le JPQL. En
production la RLS couvre ce trou, mais le projet a toujours voulu **deux**
barrières — et une requête native n'en gardait qu'une. Les trois requêtes natives
portent désormais un filtre de tenant explicite. Le défaut a été trouvé par les
tests d'intégration, pas par les tests unitaires.

**Décision de la revue.** L'ADR-008 est **généralisé** plutôt que complété au cas
par cas : sous RLS, aucune fonctionnalité *matérialisante* de TimescaleDB n'est
disponible. Restent le partitionnement, les index, `time_bucket` et les
agrégations `last`/`first` — c'est-à-dire l'essentiel. Écrire la règle une fois
évite de la redécouvrir une troisième fois.

---

## ⚠️ Risques Identifiés

| Risque | Impact | Mitigation |
|:---|:---|:---|
| Une user story dont la faisabilité n'a pas été vérifiée au planning | Un sprint de 2 US en perd la moitié | Constaté : US-080 était irréalisable telle quelle. Le bénéfice a été redéfini, pas abandonné — et l'impossibilité est consignée en ADR |
| Répéter un défaut déjà corrigé ailleurs | La correction paraît faite alors qu'elle est partielle | Recherche du **motif** (`findAll()` suivi d'une réduction) plutôt que du seul symptôme, sur tout le paquet `service` |
| Une agrégation à la demande remplace un cache | Le coût revient à chaque appel | Assumé et chiffré : le gain porte sur le **volume transporté**, pas sur le calcul ; la borne est le nombre de pixels du tracé |
| Une requête native échappe au discriminant de tenant | Une seule barrière au lieu de deux — invisible en production, où la RLS rattrape | Filtre de tenant explicite sur les trois requêtes natives, et test d'intégration sous le rôle applicatif |
| Une projection déclarée `String` sur une énumération | Les clés changent de casse **sans qu'aucune signature ne bouge** | Projection typée sur l'énumération ; la conversion reste dans le seul endroit qui la connaît |

---

## 📊 Burndown Chart

| Jour | Reste à faire (idéal) | Reste à faire (réel) | Notes |
|:---|:---:|:---:|:---|
| Jour 1 | 13 | 13 | Recherche du motif `findAll()` + réduction dans tout le paquet `service` |
| Jour 4 | 9 | 8 | US-079 : synthèse agrégée en base, réutilisant l'agrégat du SPRINT-17 |
| Jour 7 | 6 | 8 | US-080 : l'agrégat continu est refusé à la création ; le contournement tombe aussi |
| Jour 9 | 4 | 6 | Redéfinition du bénéfice : `time_bucket` à la demande plutôt que matérialisé |
| Jour 11 | 2 | 3 | Deux régressions attrapées par les tests d'intégration, dont le trou de tenant |
| Jour 14 | 0 | 0 | ADR-008 généralisé ; contrat et types régénérés |

*Écart au plan : deux jours perdus au jour 7 sur une impossibilité technique qui
aurait dû être vérifiée au planning. La dépense n'est pas nulle pour autant — elle
produit la règle générale qui évitera une troisième découverte.*

---

## ✅ Definition of Done

- [x] `./mvnw verify` : 55 unitaires + **111** d'intégration, **Skipped : 0**
- [x] Front : 101 tests, 0 erreur ESLint, build OK
- [x] Contrat OpenAPI et types générés régénérés

## 🔁 Rétrospective

**Ce qui a marché.** Les tests d'intégration ont attrapé deux régressions que les
tests unitaires laissaient passer, et la seconde était sérieuse.

1. Une projection déclarée `String` sur une énumération faisait renvoyer le nom
   de la constante Java (`RECOLTE`) au lieu de la valeur en base (`recolte`) : les
   clés de la synthèse changeaient de casse **sans qu'aucune signature ne bouge**.
2. Plus grave : une requête **native** échappe au discriminant `@TenantId`
   d'Hibernate, qui ne réécrit que le JPQL. En production la RLS couvre ce trou,
   mais le projet a toujours voulu **deux** barrières — et une requête native n'en
   gardait qu'une. Les trois requêtes natives portent désormais un filtre de
   tenant explicite.

**Ce qu'on retient.** Optimiser en descendant du JPQL vers le SQL natif fait
franchir une frontière de sécurité, discrètement. À vérifier systématiquement à
chaque requête native ajoutée.

**Ce qui reste ouvert.** Rien dans `docs/ARCHITECTURE-SOLID.md`. La liste se
remplira de nouveau — c'est normal — mais elle est vide à ce jour.
