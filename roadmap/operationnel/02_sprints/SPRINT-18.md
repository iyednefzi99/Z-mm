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
