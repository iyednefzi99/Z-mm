# ADR-014 — Capteurs du commerce : le profil standard, et rien d'inventé

- **Date** : 2026-09-05
- **Statut** : ✅ **Accepté** — mis en œuvre au SPRINT-31 (lot F₂ du plan de couverture)
- **Décideurs** : architecte, développeur front
- **Tranche** : la décision **D3** de
  [`docs/PLAN-COUVERTURE-ECARTS.md`](../../../docs/PLAN-COUVERTURE-ECARTS.md)
- **Dépend de** : [ADR-013](ADR-013-ou-tourne-l-ia.md), dont il reprend la règle
  — ne pas livrer ce qu'on ne peut pas vérifier

---

## Contexte

Le plan posait la question ainsi : **achète-t-on du matériel ?** Quatre lignes du
§5 en dépendaient :

| Ligne | Ce qu'elle demande |
|---|---|
| Alarme anti-vol / basculement | Alerter quand une ruche disparaît ou se renverse |
| Connexion Bluetooth directe | Lire un capteur sans passerelle |
| Intégrations nommées | BroodMinder, BEEP, SensorPush, Inkbird… |
| Analyse vidéo / acoustique | Comptage de trafic, détection de perte de reine |

Deux obstacles étaient identifiés : éprouver une intégration nommée suppose de
**posséder** le matériel, et Web Bluetooth est **absent d'iOS Safari** — donc de
la moitié du parc.

---

## Décision

**Non, on n'achète pas de matériel. On livre ce qui se vérifie sans en avoir, et
on refuse le reste en le disant.**

Les quatre lignes ne se traitent pas de la même façon, et les traiter ensemble
était l'erreur du plan.

### 1. L'anti-vol ne demandait aucun capteur — ✅ livré

C'était le constat le plus cinglant du document d'écart : « Zümm **cache** la
position pour protéger du vol ; Onibi **alerte** quand il survient ».

Une ruche emportée ou renversée se voit dans la série de **poids** que l'API
ingère déjà. Ce qui manquait n'était pas une donnée, c'était une règle — et le
seul point délicat tient en une phrase : **une chute de vingt kilogrammes est une
récolte si une récolte a été enregistrée ce jour-là, et un vol sinon**.

Sans cette vérification, la première miellée réveillerait l'alarme sur tout le
rucher, et l'apiculteur la couperait. Une alarme qu'on coupe ne protège plus.

L'`inclinaison` s'ajoute comme sixième indicateur, sur le patron de
l'`alimentation` du SPRINT-26 : une valeur d'énumération, un seuil, et
`SeuilAlerteService` s'en occupe comme des autres. Peu de matériel en pousse
aujourd'hui ; le point n'est pas de le supposer présent, mais de ne pas obliger
celui qui en a à détourner un autre indicateur.

### 2. Le Bluetooth direct, mais **le profil standard seulement** — ✅ livré

Web Bluetooth lit un capteur sans passerelle. Ce qui est vendeur, c'est
« BroodMinder » ; ce qui est **vérifiable sans matériel**, c'est le profil
Bluetooth SIG :

- *Environmental Sensing* (`0x181A`) — température `0x2A6E`, humidité `0x2A6F` ;
- *Battery Service* (`0x180F`) — niveau `0x2A19`.

Ces identifiants sont **normalisés**, pas devinés. Tout capteur qui les
implémente fonctionne, et l'écran le dit : Zümm parle le profil standard.

Ce que la décision **refuse** : écrire un adaptateur pour la trame propriétaire
d'un fabricant dont personne ici n'a l'appareil. Ce serait deviner une structure
de données, et le résultat aurait l'apparence du support sans en avoir la
fiabilité. C'est le même refus qu'au SPRINT-27 pour le réfractomètre — mieux vaut
ne rien rendre que rendre une valeur qu'on croira exacte.

**Web Bluetooth est absent d'iOS Safari**, et l'écran l'écrit au lieu d'afficher
un bouton inerte. La passerelle reste le chemin de tout le monde ; le Bluetooth
direct est un raccourci pour ceux qui l'ont.

### 3. Les intégrations nommées restent 🟡 — et c'est un partenariat

Le plan le recommandait déjà, et l'expérience du lot le confirme. Ce qui est
livré à la place, et qui sert **toutes** les intégrations sans en privilégier
aucune : l'**ingestion par lot** (`POST /api/mesures/lot`). Quarante ruches et
quatre indicateurs relevés au quart d'heure faisaient cent soixante requêtes ;
elles en font une.

La ligne ne passe pas à ✅, et il ne faut pas qu'elle y passe : personne n'a
vérifié une seule trame BroodMinder ici.

### 4. L'analyse vidéo / acoustique est **refusée** — ❌ assumé

Elle demanderait un flux audio ou vidéo, donc un stockage binaire que le dépôt
n'a pas, et un modèle que l'[ADR-013](ADR-013-ou-tourne-l-ia.md) interdit de
faire tourner ailleurs que sur l'appareil.

Le détail qui tranche : détecter une colonie orpheline au son suppose de savoir
ce qu'on écoute. Sortir un verdict d'un pic de fréquence sans donnée de
validation produirait un chiffre inventé sur une question que l'apiculteur ne
peut pas vérifier autrement qu'en ouvrant la ruche — c'est-à-dire exactement le
geste qu'on prétendait lui épargner.

---

## Conséquences

- ✅ Deux lignes du §5 fermées, une passée à 🟡, une refusée. Le lot F₂ vaut
  **2,5 lignes sur 4**, et le document le dit ainsi plutôt que d'arrondir.
- ✅ `alerte` porte désormais une **catégorie** : une ruche peut être à la fois
  légère et volée, et la seconde alerte ne doit pas être bloquée par la première.
- ⚠️ L'alerte de vol **ne se referme jamais toute seule**. Une ruche volée ne
  revient pas ; une alerte qui se fermerait parce que la balance repose sur le
  sol serait pire que pas d'alerte. C'est l'apiculteur qui la clôt.
- ⚠️ Une passerelle qui pousse une fois par jour ne déclenchera jamais l'alarme
  anti-vol : la fenêtre de comparaison est de deux heures. C'est honnête — à
  cette cadence, elle ne peut pas voir un vol.
- La porte reste ouverte à une intégration nommée : le jour où le matériel entre
  dans le projet, l'adaptateur se branche sur l'ingestion existante sans toucher
  au reste.
