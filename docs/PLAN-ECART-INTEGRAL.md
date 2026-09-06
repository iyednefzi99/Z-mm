# Amener les 153 lignes de l'écart fonctionnel à ✅

> État mesuré le 06/09/2026, après la migration `V31` :
> **137 ✅ · 5 🟡 · 3 ❌ · 8 ⛔** sur 153 lignes de
> [`ECART-CONCURRENTS.md`](ECART-CONCURRENTS.md).
>
> Le [plan de couverture](PLAN-COUVERTURE-ECARTS.md) est **clos** : onze lots,
> du SPRINT-22 au SPRINT-32, l'écart passé de 66 à 137. Ce document-ci répond à
> une autre question — *que faudrait-il pour les seize dernières ?*

---

## 0. La réponse courte, et pourquoi elle n'est pas « seize lots de plus »

Les seize lignes restantes ne sont pas seize travaux du même ordre. Elles se
répartissent en **quatre natures**, et une seule se traite en écrivant du code :

| Nature | Lignes | Ce qui bloque |
|---|--:|---|
| **A — Du travail** | 3 | Rien. C'est à faire, c'est chiffrable, aucune décision préalable |
| **B — Une décision de produit** | 5 | Le code suit en quelques jours ; ce qui manque est un arbitrage de périmètre |
| **C — Une donnée ou un matériel qu'on n'a pas** | 5 | Aucun effort de développement ne les débloque seul |
| **D — Un refus qu'il faudrait révoquer** | 3 | Les fermer **dégraderait le produit**. Le coût n'est pas en jours, il est en crédibilité |

**137 + 3 + 5 = 145**, soit exactement le plafond que le plan de couverture
annonçait. Les huit dernières demandent de revenir sur une décision écrite.

> ⚠️ **Il n'existe pas de chemin honnête vers 153 ✅.** Trois lignes (nature D)
> ne peuvent devenir vertes qu'en produisant un chiffre que l'apiculteur ne peut
> pas vérifier — c'est la définition même de ce que les ADR 014 et 015 refusent.
> Les cocher serait un mensonge par tableau. Ce document dit ce qu'il faudrait
> faire ; il ne recommande pas de le faire.

---

## 1. Nature A — du travail, et rien d'autre  ·  3 lignes

Aucune décision à prendre, aucune dépendance externe. Ce sont les seules lignes
que je pourrais commencer aujourd'hui.

### A1 · *Ground truthing* — la culture réellement semée  ·  §13  ·  ❌ → ✅

**Ce que BeeGIS conseille à ses utilisateurs** : « vérifiez sur le terrain au
printemps la culture réellement semée ». Le contournement dit exactement ce qui
manque au logiciel — une couche d'occupation du sol a un millésime, et le
millésime se trompe.

**Ce qu'il faut écrire.** La `V31` porte déjà `couvert_sol` avec sa `source` et
son `millesime`. Il manque :

- une colonne `confirme` (trois états : jamais regardé, confirmé, corrigé) et
  une `classe_observee` sur le polygone ;
- une route `POST /api/environnement/couvert/{id}/confirmer` qui prend la classe
  vue sur place ;
- une **tâche engendrée** par le moteur de règles du SPRINT-22 : « vérifier
  l'assolement autour du rucher X », déclenchée au printemps sur les parcelles
  dont le millésime a plus d'un an ;
- l'affichage : les surfaces distinguent ce qui est confirmé de ce qui est
  déclaratif.

**Le piège, et c'est tout l'intérêt de la ligne.** Une classe corrigée sur le
terrain ne doit **pas** écraser la donnée versée : les deux coexistent, et la
réponse dit laquelle elle a utilisée. Sinon un nouveau versement de la couche
effacerait silencieusement le travail de terrain de l'année précédente.

**Poids** : migration `V32`, une route, une règle, un écran. Comparable au lot J.

### A2 · Logistique multi-sites  ·  §7  ·  🟡 → ✅

`GET /api/equipe/charge` (SPRINT-23) rend déjà la charge par agent en
**ruchers concernés** — trois ruches sur trois ruchers font trois déplacements.
Ce qui manque est la **chaîne d'approvisionnement** : ce qu'il faut emporter.

**Ce qu'il faut écrire.** Le croisement existe déjà en pièces détachées : les
tâches du jour (SPRINT-22), le stock à seuils (SPRINT-27), l'inventaire du
matériel (SPRINT-27), les transports planifiés (SPRINT-21). Une route
`GET /api/tournees/{date}/chargement` les joint : pour la tournée du jour, ce
qu'il faut charger dans le véhicule — hausses, cadres, candi, traitements — et
ce qui **manque en stock**.

**Ce qu'il ne faut pas faire** : inventer des quantités. Si une tâche dit
« nourrir » sans dire combien, la ligne sort « quantité non précisée » et non un
chiffre calculé sur une moyenne.

**Poids** : aucune migration, un service d'agrégation, un écran. Deux jours.

### A3 · Déclaration annuelle des ruches  ·  §7  ·  ⛔ → ✅

Classée hors périmètre au motif que NAPI est un **dispositif national
français**. Le motif tient pour la *télédéclaration* — Zümm ne se connectera pas
à un téléservice — mais pas pour ce qui la précède.

**Ce qu'il faut écrire.** Un export PDF/CSV du parc au 31 décembre : nombre de
ruches par rucher, commune, numéro NAPI de l'exploitation (champ à saisir),
dates d'ouverture et de clôture. C'est exactement ce que le registre porte déjà,
mis en forme.

**Ce que ça change au verdict.** La ligne devient ✅ « le dossier de déclaration
est produit », et non « la déclaration est transmise ». La distinction doit
rester écrite, comme pour le dossier de conformité bio du SPRINT-29 : **Zümm
rassemble les pièces, il ne certifie ni ne télédéclare**.

**Poids** : un service PDF de plus, sur le patron de `RegistreElevagePdfService`.
Un jour.

---

## 2. Nature B — une décision de produit  ·  5 lignes

Le code n'est pas le sujet. Chacune demande un arbitrage que je ne peux pas
prendre à votre place, parce qu'il engage ce que Zümm **est**.

### B1 · Réinitialisation de mot de passe  ·  §7  ·  🟡 → ✅

**Il n'y a plus de code à écrire.** Le SPRINT-25 a ouvert le chemin :
`/api/info` publie l'URL du parcours Keycloak, `RecuperationVue` affiche le lien
dès qu'elle est renseignée. Ce qui manque est un **serveur d'envoi de courriel**
configuré dans le realm (`infra/keycloak/README.md`).

**La décision** : accepter que le déploiement de référence dépende d'un SMTP
externe. Aujourd'hui la pile Docker n'en a aucun — c'est cohérent avec le mode
local du SPRINT-30, qui coupe le trafic sortant.

**Deux issues acceptables**, et la seconde est plus intéressante :

1. documenter la configuration d'un SMTP et fournir un `docker-compose` de
   démonstration avec un relais de test (MailHog) ;
2. **assumer que la ligne reste 🟡** et l'écrire au §8 comme une conséquence du
   choix d'auto-hébergement sans dépendance sortante.

**Poids** : une demi-journée pour l'option 1, zéro pour l'option 2.

### B2 · Comptabilité — reçus, TVA, clients, ventes  ·  §6  ·  2 lignes ⛔ → ✅

Le SPRINT-27 s'est arrêté à la **rentabilité par ruche**, et l'a écrit :

> `depense` ne porte ni fournisseur, ni numéro de pièce, ni TVA : chacune de ces
> colonnes appellerait la suivante, et la troisième rendrait le module
> obligatoire pour boucler un exercice.

**C'est la décision à révoquer, et elle est structurante.** Ouvrir la
facturation transforme Zümm d'un outil de suivi apicole en logiciel de gestion :
il faut alors une numérotation de pièces inaltérable, des taux de TVA
paramétrables par pays, un plan de comptes, un export FEC. Le module cesse
d'être optionnel — un exploitant qui l'utilise à moitié aura une comptabilité
fausse.

**Poids honnête** : trois à quatre lots, soit davantage que tout ce qu'a coûté
le registre sanitaire. Et un risque juridique que le reste du produit n'a pas.

**Ma recommandation** : les laisser ⛔. Un apiculteur a déjà un comptable ou un
logiciel de facturation ; il n'a pas de registre sanitaire.

### B3 · Licence ouverte  ·  §8  ·  ⛔ → ✅

**Une ligne, un fichier, cinq minutes de code** — et une décision qui ne
m'appartient pas. Le dépôt est « tous droits réservés » par **choix assumé du
cadre académique** (`README.md` §Licence).

Poser une licence (AGPL-3.0 conviendrait à un produit auto-hébergeable) rendrait
la ligne ✅ immédiatement. Cela suppose de vérifier que le cadre de l'épreuve
l'autorise, et d'accepter que le travail soit réutilisable par des tiers.

**C'est votre décision, pas la mienne.** Dites-le et je l'applique.

### B4 · Actionneurs à distance  ·  §5  ·  ⛔ → ✅

« Zümm observe, il ne commande pas. » Piloter une porte robotisée engage la
**sécurité de la colonie** : une porte fermée par erreur en pleine miellée
étouffe une ruche.

Fermer la ligne suppose du matériel propriétaire, un protocole de commande, un
accusé d'exécution, un état de repli en cas de perte réseau — et la
responsabilité qui va avec. **C'est un produit différent**, pas une
fonctionnalité de plus.

**Ma recommandation** : ⛔ définitif, et le dire ainsi plutôt que « pas encore ».

---

## 3. Nature C — une donnée ou un matériel qu'on n'a pas  ·  5 lignes

Aucun effort de développement ne les débloque seul. Chacune attend quelque chose
d'extérieur au dépôt.

### C1 · Intégrations nommées de capteurs  ·  §5  ·  🟡 → ✅

`POST /api/mesures/lot` sert **toutes** les intégrations sans en privilégier
aucune ; le Bluetooth SIG couvre les capteurs normalisés. Ce qui manque est un
adaptateur pour une trame propriétaire — BroodMinder, par exemple.

**Ce qu'il faut** : **un appareil**. [ADR-014](../roadmap/operationnel/06_decisions/ADR-014-capteurs-du-commerce.md)
l'assume — écrire un décodeur pour une trame que personne n'a vérifiée revient à
deviner une structure de données, et produirait l'apparence du support sans sa
fiabilité.

**Le chemin réel** : un partenariat ou un achat de matériel, puis deux jours de
décodage **vérifié**. Le plan de couverture le recommandait déjà : *un
partenariat, pas un développement*.

### C2 · Exposition aux zones traitées  ·  §2  ·  🟡 → ✅

`distanceCultureM` rend la distance à la parcelle cultivée la plus proche. **Ce
n'est pas une distance à une zone traitée** : aucune couche ouverte ne dit ce
qui a été épandu ni quand.

**Ce qu'il faudrait** : un registre des traitements phytosanitaires par
parcelle. Il n'en existe pas d'ouvert. Le versement explicite de `couvert_sol`
(ADR-015) accepterait une telle couche le jour où elle existera — l'architecture
est prête, la donnée non.

### C3 · Taux de cultures bio dans le rayon  ·  §2  ·  ⛔ → ✅

Dépend de **CartoBio** (Agence Bio), un référentiel français. Le versement
générique de la `V31` l'accepterait tel quel : il suffirait d'ajouter une classe
`culture_bio` à la taxonomie fermée et d'un ingesteur.

**Mais la taxonomie est fermée à dix classes**, et pour une raison : chaque
source nomme les siennes autrement. Ajouter une onzième classe uniquement
disponible en France rendrait deux exploitations incomparables — exactement ce
que la fermeture évite.

**Chemin possible** : traiter le bio non comme une *classe* mais comme un
**attribut** du polygone (`bio boolean`), orthogonal à la classe. La donnée
resterait absente hors de France, mais le modèle ne mentirait pas.

**Poids** : une migration, un ingesteur, une ligne de réponse. Un jour — le jour
où la donnée est là.

### C4 · Croisement santé × flore  ·  §2  ·  🟡 → ✅

Les deux moitiés se lisent déjà côte à côte : surfaces par classe autour du
rucher, indices de colonie, comparaison d'emplacements. **Aucun coefficient de
corrélation n'est calculé**, et c'est délibéré : sur la dizaine de ruchers d'une
exploitation, il serait du bruit présenté comme un résultat.

**Ce qu'il faudrait** : plusieurs centaines de ruchers-années, c'est-à-dire un
jeu de données multi-exploitations que Zümm n'a pas et ne collectera pas —
`PolitiquePositions` et le mode local existent précisément pour qu'il ne les
collecte pas.

**Le seul chemin honnête** : afficher la corrélation **avec son intervalle de
confiance et son n**, et refuser de l'afficher sous un seuil d'observations. La
ligne passerait alors à ✅ pour ce qu'elle est réellement — un outil de lecture,
pas une conclusion.

**Poids** : un jour de code, et une garde qui la rendra silencieuse chez presque
tout le monde.

### C5 · « Contactez les agriculteurs voisins »  ·  §13  ·  ⛔ → ✅

Le contournement de BeeGIS suppose un **annuaire de tiers** — des personnes qui
ne sont pas utilisatrices du logiciel, dont on stockerait le nom et le contact.

Fermer la ligne, c'est ouvrir un fichier de données personnelles sur des gens
qui n'ont rien signé. **⛔ définitif**, et pour une raison de fond, pas de
périmètre.

---

## 4. Nature D — un refus qu'il faudrait révoquer  ·  3 lignes

Ces trois lignes ne coûtent pas des jours. Elles coûtent la propriété que le
produit défend depuis onze sprints : **ne pas afficher un chiffre que
l'apiculteur ne peut pas vérifier**.

| Ligne | Section | Refus |
|---|---|---|
| Analyse vidéo / acoustique à l'entrée | §5 | [ADR-014](../roadmap/operationnel/06_decisions/ADR-014-capteurs-du-commerce.md) |
| Comptage / prévision de pollen | §2 | [ADR-015](../roadmap/operationnel/06_decisions/ADR-015-occupation-du-sol.md) |
| *(le troisième ❌ est A1, du travail ordinaire)* | | |

**L'acoustique.** Détecter une colonie orpheline au son suppose de savoir ce
qu'on écoute. Sortir un verdict d'un pic de fréquence sans donnée de validation
produirait un chiffre inventé sur une question que l'apiculteur ne peut vérifier
qu'en **ouvrant la ruche** — c'est-à-dire exactement le geste qu'on prétendait
lui épargner. Il faudrait aussi un stockage binaire, que le dépôt refuse
(`ck_brouillon_taille`, ADR-012).

**Le pollen.** Un comptage vient de réseaux d'aérobiologie nationaux, pas d'un
capteur de rucher. L'estimer depuis le couvert donnerait un nombre plausible et
faux.

**Ce qu'il faudrait pour les fermer honnêtement** : dans les deux cas, un jeu de
données de validation — des enregistrements étiquetés par un apiculteur ayant
ouvert la ruche, des comptages de pollen mesurés au rucher. Sans cela, fermer
ces lignes revient à cocher une case en produisant du bruit.

**Ma recommandation** : les laisser ❌, avec leur motif. Une ligne refusée et
expliquée vaut mieux qu'une ligne verte et fausse — c'est ce que le document
d'écart fait depuis le SPRINT-31, et c'est ce qui le rend lisible.

---

## 5. Ce que je recommande, et dans quel ordre

| # | Ligne | Nature | Poids | Verdict visé |
|---|---|---|--:|---|
| 1 | *Ground truthing* (A1) | travail | ~1 lot | ✅ |
| 2 | Logistique multi-sites (A2) | travail | 2 j | ✅ |
| 3 | Déclaration annuelle (A3) | travail | 1 j | ✅ *(dossier produit, pas télédéclaré)* |
| 4 | Croisement santé × flore (C4) | garde statistique | 1 j | ✅ *(avec n et intervalle)* |
| 5 | Licence (B3) | **votre décision** | 5 min | ✅ si vous le décidez |
| 6 | Mot de passe (B1) | **votre décision** | ½ j | ✅ ou 🟡 assumé |

**Ces six lignes portent l'écart de 137 à 143**, sans révoquer aucune décision
et sans inventer aucun chiffre. C'est le maximum atteignable de l'intérieur.

Les **dix restantes** se répartissent ainsi : quatre attendent une donnée ou un
matériel (C1, C2, C3, C5), trois demandent d'ouvrir un module de gestion
commerciale ou de commande d'actionneurs (B2 ×2, B4), et trois sont des refus
argumentés dont la levée dégraderait le produit (nature D, plus le pollen).

> **La question à trancher n'est donc pas « comment couvrir les 153 »**, mais
> « accepte-t-on que le tableau porte des lignes non vertes ». Le document
> d'écart vaut précisément parce qu'il en porte : un inventaire où tout est ✅ ne
> se lit plus, il se signe.

---

## 6. Comment vérifier le compte

Le verdict se relève, il ne se recopie pas — et la commande qui le relève a
elle-même été fausse une fois (§6 de [`PLAN-COUVERTURE-ECARTS.md`](PLAN-COUVERTURE-ECARTS.md)).

```bash
python - <<'PY'
import io
from collections import Counter
SYM = ('✅', '🟡', '❌', '⛔')
INVENTAIRE = tuple(str(n) + '.' for n in list(range(1, 9)) + [13])
s = io.open('docs/ECART-CONCURRENTS.md', encoding='utf-8').read()
dedans, verdicts = False, []
for ligne in s.split('\n'):
    if ligne.startswith('## '):
        dedans = ligne[3:].strip().startswith(INVENTAIRE)
    if dedans and ligne.startswith('|'):
        trouves = [c.strip() for c in ligne.strip('|').split('|') if c.strip() in SYM]
        if trouves:
            verdicts.append(trouves[0])
c = Counter(verdicts)
print('%d ✅ · %d 🟡 · %d ❌ · %d ⛔ — total %d'
      % (c['✅'], c['🟡'], c['❌'], c['⛔'], len(verdicts)))
PY
```
