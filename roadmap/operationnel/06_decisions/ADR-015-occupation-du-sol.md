# ADR-015 — Occupation du sol : la donnée est accueillie, jamais interrogée

- **Date** : 2026-09-05
- **Statut** : ✅ **Accepté** — mis en œuvre au SPRINT-32 (lot H du plan de couverture)
- **Décideurs** : architecte, développeur back
- **Tranche** : la décision **D1** de
  [`docs/PLAN-COUVERTURE-ECARTS.md`](../../../docs/PLAN-COUVERTURE-ECARTS.md),
  dernière des quatre
- **Dépend de** : [ADR-011](ADR-011-positions-au-repos.md) (positions au repos) et
  [ADR-013](ADR-013-ou-tourne-l-ia.md) (mode local), dont elle est la conséquence

---

## Contexte

Le plan posait la question du **référentiel** : lequel choisir ?

| Option | Ce qu'on gagne | Ce qu'on perd |
|---|---|---|
| RPG, CartoBio, BD Forêt (France) | Précision parcellaire, cultures nommées | Le produit ne sort pas de France |
| Copernicus / ESA WorldCover | Couverture mondiale, gratuit | Classes grossières |
| OpenStreetMap `landuse` | Mondial, gratuit, déjà en fond de carte | Complétude très inégale |

Et il ajoutait : « une architecture qui accepterait les trois sources est
possible — une couche générique `couvert_sol` alimentée par des ingesteurs
interchangeables — mais elle coûte plus cher que de trancher. »

**Elle ne coûte plus plus cher**, et ce n'est pas un revirement de confort : deux
décisions prises depuis ont changé le calcul.

---

## Décision

**La question du référentiel n'est pas la bonne. La vraie question est : qui
interroge qui ?**

### 1. Zümm n'interroge personne

Interroger une source tierce avec les coordonnées d'un rucher — une requête
Overpass, un WMS, une API de couverture — **lui apprend où sont les ruches**.

C'est exactement ce que `PolitiquePositions` protège depuis le SPRINT-12, ce que
[ADR-011](ADR-011-positions-au-repos.md) protège au repos, et ce que le mode
local du SPRINT-30 vient de couper pour les tuiles de carte, avec cet argument :
« leur seule séquence révèle où sont les ruchers ». Une requête d'occupation du
sol est pire qu'une tuile : elle porte **une** coordonnée, et c'est celle du
rucher.

À quoi s'ajoute le fait mécanique : en mode local (`zumm.reseau.sortant=false`),
une couche interrogée en direct serait **muette** — c'est-à-dire inutilisable
chez l'exploitant qui tient le plus à sa discrétion.

**L'exploitation verse donc sa couche** (`POST /api/environnement/couvert`,
GeoJSON) dans son propre PostGIS, et toutes les intersections se font chez elle.
Aucun appel sortant, aucune coordonnée communiquée.

### 2. La source devient un paramètre, la taxonomie reste fermée

Puisque personne n'est interrogé, le choix du référentiel **n'appartient plus au
produit** : il appartient à l'exploitant, qui verse le RPG en France, WorldCover
au Maghreb, ou un extrait OSM ailleurs. Le trilinguisme FR/EN/AR aurait été
contredit par un choix français.

Ce qui **ne** devient pas un paramètre, c'est la taxonomie. Dix classes
contraintes en base : `culture`, `prairie`, `foret`, `lande`, `verger`, `vigne`,
`eau`, `urbain`, `sol_nu`, `autre`. Chaque source nomme les siennes autrement —
« prairie permanente », *grassland*, `landuse=meadow` — et les laisser entrer
telles quelles rendrait deux exploitations incomparables, et une somme de
surfaces par classe dépourvue de sens.

**L'ingesteur traduit ; il n'invente pas**, et le versement échoue en entier sur
une classe inconnue. Accepter le reste laisserait une couche partielle dont
personne ne saurait ce qu'elle omet. C'est le référentiel fermé du SPRINT-28,
appliqué à un autre sujet.

### 3. Le millésime est obligatoire, et affiché

`couvert_sol.millesime` est `NOT NULL`. Une donnée d'occupation du sol de 2019
présentée comme l'état du jour n'est pas une approximation, c'est une
affirmation fausse : les parcelles tournent d'une année sur l'autre. La source
et le millésime accompagnent **chaque** réponse — c'est la ligne « millésime des
données environnementales » du §13, et c'est aussi ce qui rend la ligne
« historique et rotation » possible : la rotation ne se déduit pas d'une couche,
elle se **lit** en comparant deux années.

---

## Ce que la décision ne permet pas, et qu'il faut dire

- **Rien n'arrive tout seul.** Une exploitation qui ne verse aucune couche n'a
  aucune surface, et l'API le dit (`millesime: null`, liste vide) plutôt que de
  rendre des zéros qui se liraient comme un environnement vide. C'est le coût
  assumé du point 1.
- **La couverture partielle est rendue explicite.** `couverte` dit quelle part du
  cercle la couche décrit réellement. Trente pour cent de couverture et soixante-dix
  pour cent de silence ne disent pas « 70 % de sol nu ».
- **La distance à une culture n'est pas une distance à une zone traitée.** Aucune
  couche ouverte ne dit ce qui a été épandu ni quand. Le champ s'appelle
  `distanceCultureM`, et la ligne du §2 reste 🟡 pour cette raison.
- **Le comptage de pollen reste ❌**, et sans échéance. Il vient de réseaux
  d'aérobiologie nationaux, pas d'un capteur de rucher ; l'estimer à partir du
  couvert produirait un chiffre inventé sur une donnée que l'apiculteur ne peut
  pas vérifier.
- **Le croisement santé × flore reste 🟡** : les surfaces et les indices de
  colonie sont rendus côte à côte, avec le nombre de ruchers. Aucun coefficient
  de corrélation n'est calculé — sur la dizaine de ruchers d'une exploitation, il
  serait du bruit présenté comme un résultat. Même refus qu'au SPRINT-23 pour la
  note globale de comparaison d'emplacements.

---

## Conséquences

- ✅ Six lignes du §2 et du §13 se ferment sans qu'aucun appel sortant
  n'apparaisse, et sans qu'une coordonnée de rucher quitte l'exploitation.
- ✅ Le produit reste utilisable hors de France, ce que le trilinguisme
  supposait depuis le début.
- ⚠️ La charge d'obtenir la donnée passe à l'exploitant. C'est un vrai coût, et
  la contrepartie d'une promesse tenue plutôt qu'affichée.
- Le plafond du document d'écart n'est pas atteint, et ne le sera pas : trois
  lignes de ce lot restent 🟡 ou ❌ **par honnêteté**, pas par manque de temps.
