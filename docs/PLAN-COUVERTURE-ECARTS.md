# Plan de couverture intégrale de l'écart fonctionnel

> État mesuré le 04/09/2026, après les migrations `V19` à `V26` :
> **100 ✅ · 9 🟡 · 36 ❌ · 8 ⛔** sur 153 lignes de
> [`ECART-CONCURRENTS.md`](ECART-CONCURRENTS.md).
>
> **Les lots A, B, C, J et F₁ sont livrés** (migrations `V22` à `V26`, notes §18 à §22
> du document d'écart). A a fermé ses onze lignes, exactement celles annoncées
> ici ; B en a fermé **cinq sur six**, la coordination d'équipes restant 🟡 — sa
> part logistique est reportée au lot E ; C **huit sur neuf**, la note vocale
> restant 🟡 faute de stockage binaire ; J **six sur sept**, la réinitialisation
> de mot de passe restant 🟡 tant qu'aucun serveur d'envoi n'est configuré ; et
> **F₁** ses quatre lignes, exactement celles annoncées. Restent **45 lignes**
> ouvertes.
>
> Ce plan dit comment les amener à ✅, dans quel ordre, et à quelle condition. Il ne dit pas qu'il faut le faire : trois des
> dix lots coûtent plus cher que tout ce qui a été livré depuis le SPRINT-00, et
> le §3 nomme les quatre décisions à prendre avant d'écrire la première ligne.

## Comment lire ce plan

**Les 79 lignes ne sont pas 79 fonctionnalités.** Le §13 du document d'écart
reformule en exigences les contournements que les concurrents conseillent à
leurs utilisateurs ; **six** de ses seize lignes décrivent le même travail qu'une
ligne d'une autre section — le hors-ligne, l'export intégral, l'alimentation des
capteurs, la comparaison saison contre saison, l'étiquetage et l'action attachée
à l'observation. Après dédoublonnage, il reste **73 travaux distincts**, groupés
ici en **dix lots**.

Chaque lot porte le nombre de **lignes du document** qu'il ferme — recouvrements
compris, puisqu'une ligne du §13 et son jumeau d'une autre section tombent
ensemble. La somme fait exactement 79, et le §5 en donne la progression cumulée.

**L'ordre est celui des dépendances, pas celui du confort.** Un lot n'apparaît
avant un autre que s'il le rend possible, ou s'il coûte moins pour le même gain.
Le coût d'opportunité du §11 est respecté partout où les dépendances le
permettent.

**Le poids est exprimé en couches**, comme la matrice du §12 : `●` travail
substantiel · `◐` travail léger · `—` rien à faire. Aucune estimation en jours :
le dépôt n'a pas d'historique de vélocité qui la rendrait honnête.

---

## 1. Ce qui porte déjà le reste

Trois migrations récentes ont mis en place les colonnes sur lesquelles la moitié
du plan s'appuie. C'est ce qui explique que des lots entiers soient désormais
« back pur » alors qu'ils supposaient une migration il y a un mois.

| Acquis | Ce qu'il débloque |
|---|---|
| `V19` — observations structurées, registre sanitaire | Score de santé, risque d'essaimage, tâches engendrées : les colonnes sont là, indexées |
| `V20` — terrain, filiation, photos multi-cibles | Élevage et généalogie (la filiation existe), étiquetage, agrégats par rucher |
| `V21` — miellées, transport, abonnement iCal | Calendrier de floraison **déclaré**, planification logistique, notifications sortantes |
| `PolitiquePositions`, RLS + portée (V16) | Tout partage de données entre utilisateurs part d'un masque déjà écrit |
| `ExportService`, `RapportVisitePdfService` | Les exports et les PDF du lot 6 sont des extensions, pas des créations |

---

## 2. Les dix lots

### Lot A — Les règles qui manquent aux données existantes  ·  11 lignes  ·  ✅ LIVRÉ

> Livré le 01/09/2026 par la migration `V22` : cinq règles dans un moteur unique
> (`MoteurRegles`), les deux indices calculés (`IndiceColonieService`), la
> corrélation météo, et la carence rendue **opposable** avec sa porte de sortie
> tracée. Ce que la mise en œuvre a confirmé du plan : la clé d'idempotence était
> bien la colonne décisive, et le piège de la carence sans issue était réel — il
> a fallu écrire la porte de sortie *avant* le refus, pas après.

**Le lot au meilleur rapport, et de loin.** Rien à ajouter en base : le
SPRINT-20 a livré les colonnes, personne n'en tire encore de conclusion.

| Ligne couverte | Section |
|---|---|
| Score de santé calculé par colonie | §3 |
| Score de risque d'essaimage | §3 |
| Recommandations automatiques / tâches engendrées | §3 |
| Rappels programmés (retrait de traitement, ponte à J+7) | §3 |
| Action attachée à l'observation | §13 |
| Délai de carence **opposable en écriture** | §3 |
| Force de la colonie, état sanitaire (agrégation) | §3 |
| Priorité et type sur la tâche | §7, §13 |
| Tâches programmées selon la météo | §7 |
| Corrélation météo ↔ production | §7 |
| Notification de tâche | §7 |

| DB | Back | Contrat | Front | i18n |
|:--:|:--:|:--:|:--:|:--:|
| ◐ deux colonnes sur `tache` | ● moteur de règles + service d'agrégation | ● | ◐ affichage existant | ◐ |

**Le point de conception** : ces règles doivent vivre dans **un** endroit
nommé — un `MoteurRegles` avec des règles déclaratives — et non éparpillées dans
dix services. Le dépôt a déjà le précédent inverse à éviter : `SeuilAlerteService`
tient l'hystérésis seul, et c'est ce qui le rend lisible.

**Le piège** : le délai de carence opposable transforme une donnée consultative
en refus d'écriture. Il faut une porte de sortie tracée (forçage motivé,
consigné à l'audit), sans quoi l'apiculteur contournera en ne saisissant plus le
traitement — ce qui coûte plus cher que le trou qu'on ferme.

---

### Lot B — Le rucher comme unité de travail  ·  6 lignes  ·  ✅ LIVRÉ (5/6)

> Livré le 02/09/2026 par la migration `V23` : `OperationsLotService` et les
> trois routes `/lot`, la synthèse par rucher, la comparaison d'emplacements, la
> charge d'équipe et le marquage de priorité. **Cinq lignes sur six** passent à
> ✅ ; la coordination d'équipes reste 🟡 — la charge par agent est là, la
> logistique et la chaîne d'approvisionnement ne le sont pas.
>
> Ce que la mise en œuvre a **corrigé** du plan : l'idempotence n'était pas le
> point dur. `FiltreIdempotence` protège du double envoi d'une même requête ;
> ce qu'il fallait, c'était une **transaction par ruche** (`REQUIRES_NEW`), sans
> quoi le trente-neuvième refus annulait les trente-huit écritures précédentes.
> Le plan visait juste sur la faillibilité partielle, à côté sur le mécanisme.

Aujourd'hui, toute mutation est unitaire. C'est **le premier écart en coût
d'opportunité** du §11 depuis la livraison du registre : sur un rucher de
quarante ruches, un traitement se saisit quarante fois.

| Ligne couverte | Section | État |
|---|---|---|
| Interventions groupées / scan en masse | §4 | ✅ (le scan reste dû) |
| Récolte sur tout un rucher en une saisie | §6 | ✅ |
| Agrégats au niveau rucher (vision à trois niveaux) | §7 | ✅ |
| Comparaison de plusieurs emplacements candidats | §2 | ✅ |
| Marquage de priorité sur la ruche et le rucher | §13 | ✅ |
| Coordination d'équipes, logistique multi-sites | §7 | 🟡 charge par agent |

| DB | Back | Contrat | Front | i18n |
|:--:|:--:|:--:|:--:|:--:|
| ◐ `priorite` sur ruche et site | ● routes de lot, transaction, idempotence | ● | ● sélection multiple, écrans d'agrégat | ◐ |

**Le point de conception** : une route de lot doit être **partiellement
faillible**. Trente-huit succès et deux échecs sur quarante ruches est le cas
normal au rucher, pas une exception : la réponse doit dire lesquels, et le rejeu
ne doit pas dupliquer les trente-huit.

**Ce qui a été appris en l'écrivant** : `FiltreIdempotence` (V14) protège du
double envoi d'une même requête, mais ne dit rien de ce qui se passe **à
l'intérieur** d'un lot. La propriété décisive est ailleurs : chaque ruche a sa
propre transaction (`REQUIRES_NEW`), et le service de lot attrape l'exception
métier ruche par ruche pour la retranscrire dans le rapport. Sans cela, le
trente-neuvième refus emportait les trente-huit écritures faites juste avant.

---

### Lot C — Le terrain sans réseau  ·  9 lignes  ·  ✅ LIVRÉ (8/9)

> Livré le 04/09/2026 par la migration `V24` et l'[ADR-012](../roadmap/operationnel/06_decisions/ADR-012-hors-ligne-selectif.md),
> qui lève la décision **D2** : emport déclenché, borné, daté et périssable —
> le `navigateFallbackDenylist` n'a pas bougé. S'y ajoutent le brouillon de
> visite, la garde de version `X-Zumm-Version` avec quarantaine des refus, la
> fiche d'inspection vierge, la couverture réseau du site, le mode économie et
> l'invite d'installation. **Huit lignes sur neuf** ; la note vocale reste 🟡,
> locale à l'appareil.
>
> Ce que la mise en œuvre a **révélé**, et que le plan n'avait pas vu : au rejeu
> de la file, tout 4xx était traité comme « traité » et la saisie disparaissait
> sans un mot — y compris le 409 de conflit, c'est-à-dire le cas même que cette
> ligne visait. Le plan promettait une « résolution de conflits » ; il fallait
> d'abord **cesser de détruire la saisie**.

Six des douze concurrents mettent la consultation hors ligne en tête de leur
argumentaire, et c'est le reproche n° 1 fait à trois d'entre eux.

| Ligne couverte | Section | État |
|---|---|---|
| Consultation hors ligne des données | §4 | ✅ |
| Emporter un rucher hors ligne (préchargement explicite) | §13 | ✅ |
| Brouillon de visite reprenable sur un autre appareil | §13 | ✅ |
| Résolution de conflits multi-agents hors ligne | §4 | ✅ |
| Champ couverture réseau sur le site | §13 | ✅ |
| Mode économie assumé | §13 | ✅ |
| Invite d'installation contextuelle | §13 | ✅ |
| Fiches d'inspection imprimables | §4 | ✅ |
| Notes vocales enregistrées (sans transcription) | §4 | 🟡 locale à l'appareil |

| DB | Back | Contrat | Front | i18n |
|:--:|:--:|:--:|:--:|:--:|
| ◐ brouillon + couverture réseau | ● brouillon partagé, en-têtes de fraîcheur | ● | ● service worker, cache par entité, conflits | ◐ |

**Ce lot rouvrait une décision consignée**, et c'est fait : l'ADR-012 précise le
refus de cacher `/api` sans le contredire. Un **préchargement déclenché par
l'utilisateur**, borné à un rucher, daté à l'écran et périssable — et non un
cache automatique.

**Ce que l'écriture a corrigé du plan.** Le plan disait « purgé à la
synchronisation ». C'est faux à l'usage : une barre de réseau qui réapparaît dix
secondes au sommet d'une côte effacerait ce que l'apiculteur vient d'emporter,
précisément quand il en a encore besoin. La **péremption à quatorze jours**
répond au même souci — aucun instantané ne traverse la saison — sans dépendre
d'un événement que le terrain déclenche au hasard.

---

### Lot D — Élevage, reines et généalogie  ·  7 lignes  ·  ✅ LIVRÉ (7/7)

Trois concurrents en font leur argument central. La filiation des colonies
existe depuis la `V20` ; celle des **reines** n'existe pas.

| Ligne couverte | Section |
|---|---|
| Généalogie / arbre de lignées | §7 |
| Ailes clippées, fournisseur, ruche-mère | §7 |
| Dates de greffage, suivi d'élevage | §7 |
| Index génétique multicritère | §7 |
| Photos de reine et de motif de ponte | §7 |
| Registre d'élevage réglementaire (PDF) | §7 |
| Rapports de conformité / certification bio | §7 |

| DB | Back | Contrat | Front | i18n |
|:--:|:--:|:--:|:--:|:--:|
| ● clé étrangère réflexive + colonnes d'élevage | ● service PDF + index | ● | ● vue d'arbre SVG | ◐ |

**Déjà à moitié acquis** : les photos de reine fonctionnent depuis la `V20`
(`Photo.Cible.REINE`) — la ligne du §7 qui les disait absentes était un verdict
périmé —, et le moteur PDF existe.

**Livré le 05/09/2026** (migration `V29`). Quatre décisions le tiennent, et la
première corrige ce plan :

1. **Ce plan se trompait.** Il annonçait « une clé étrangère réflexive sur
   `suivi_reine` » : elle aurait relié des **événements**. `suivi_reine` est le
   journal d'une RUCHE — une même ruche en porte des dizaines, appartenant à des
   reines successives —, et « de quelle mère descend cette reine ? » n'y aurait
   eu aucune réponse stable. La reine devient une **table**.
2. **`suivi_reine.reine_id` est nullable.** Les événements antérieurs ne
   désignent aucune reine ; deviner laquelle aurait fabriqué une généalogie, et
   un arbre faux est pire qu'un arbre absent — on le lit sans le savoir.
3. **L'index génétique n'a aucune note globale.** Cinq critères, chacun dans son
   unité, chacun avec son nombre d'observations, et `null` en dessous du
   minimum. Il est **borné au règne** : sans cela, une reine introduite en
   juillet hériterait de la récolte de printemps de la précédente.
4. **Le dossier de conformité ne certifie pas.** Il rassemble les pièces et
   nomme ce qu'il ne peut pas vérifier — l'origine biologique des sucres, celle
   de la cire, le statut du foncier sortent en « à justifier ».

Le lot a aussi révélé un défaut ancien : **cinq clés étrangères posées en `V20`
et `V27` écrivaient `ON DELETE SET NULL` sans liste de colonnes**, ce qui met
aussi `tenant_id` à `NULL`. Supprimer une ruche portant une dépense échouait en
500. La `V29` les répare, et un test le prouve.

---

### Lot E — Production, stock, matériel  ·  13 lignes

Le lot le plus large en nombre de lignes, le plus mécanique en contenu.

| Ligne couverte | Section |
|---|---|
| Produits autres que le miel (cire, pollen, propolis, gelée royale, essaims) | §6 |
| Chaîne récolte → maturation → mise en pot, DLUO/DDM | §6 |
| Comparaison des récoltes année par année / saison contre saison | §6, §13 |
| Inventaire du matériel et état d'entretien | §6 |
| Plan de maintenance du matériel | §13 |
| Stock de consommables avec seuils | §6 |
| Comptabilité : dépenses, recettes, rentabilité par ruche | §6 |
| Calculateurs apicoles (sirop, prix du miel) | §6 |
| Export XLSX | §7 |
| Export du reste des entités (récoltes, mesures, lots, tâches) | §7 |
| Bilan annuel PDF | §7 |
| Export intégral + rappel d'archivage saisonnier | §13 |

| DB | Back | Contrat | Front | i18n |
|:--:|:--:|:--:|:--:|:--:|
| ● 3 tables (produit, matériel, mouvement de stock) | ● + extension d'`ExportService` | ● | ● 2 écrans | ● nombreux libellés |

**La frontière à tenir** : la comptabilité s'arrête à la **rentabilité par
ruche**. Facturation, TVA, devis, clients restent ⛔ (§9) — Zümm est un SIG
apicole, pas un ERP, et ce bloc a le poids d'un produit à lui seul.

---

### Lot F — Capteurs et télémétrie  ·  8 lignes

| Ligne couverte | Section |
|---|---|
| Supervision de l'état des batteries | §5 |
| `TypeIndicateur.ALIMENTATION` + seuil d'alerte | §13 |
| Poids par hausse | §5 |
| Partage d'un flux de télémétrie entre utilisateurs | §5 |
| Alarme anti-vol / détection de basculement | §5 |
| Intégrations nommées de capteurs du commerce | §5 |
| Connexion Bluetooth directe aux capteurs | §5 |
| Analyse vidéo / acoustique à l'entrée | §5 |

| DB | Back | Contrat | Front | i18n |
|:--:|:--:|:--:|:--:|:--:|
| ◐ valeurs d'énumération | ◐ seuils, ● partage | ◐ | ● Web Bluetooth | ◐ |

**Le lot F₁ — les quatre premières lignes — est ✅ LIVRÉ** le 04/09/2026
(migration `V26`) : indicateur d'alimentation et son seuil, poids par hausse dans
une table distincte, partage d'un flux hors de l'exploitation.

**Le lot F₂ — les quatre suivantes — est ✅ LIVRÉ le 05/09/2026** (migration
`V30`), et c'est **le premier lot dont le résultat est inférieur à l'annonce** :
deux lignes fermées, une passée à 🟡, une **refusée**. La décision D3 est tranchée
par [ADR-014](../roadmap/operationnel/06_decisions/ADR-014-capteurs-du-commerce.md) —
on n'achète pas de matériel, on livre ce qui se vérifie sans en avoir, et on
refuse le reste en le disant.

| Ligne | Verdict | Pourquoi |
|---|:--:|---|
| Alarme anti-vol / basculement | ✅ | Aucun capteur nouveau n'était requis : une ruche emportée se voit dans la série de poids. Toute la règle tient dans une phrase — une chute est une *récolte* si une récolte est enregistrée ce jour-là, un *vol* sinon |
| Connexion Bluetooth directe | ✅ | Le **profil SIG** seulement, dont les identifiants sont normalisés. Absent d'iOS Safari, et l'écran l'écrit |
| Intégrations nommées | 🟡 | `POST /api/mesures/lot` sert toutes les intégrations. Les adaptateurs nommés restent dehors : **personne n'a vérifié une trame BroodMinder ici** |
| Analyse vidéo / acoustique | ❌ | **Refusée.** Il faudrait un stockage binaire que le dépôt n'a pas et un modèle que l'ADR-013 interdit d'exécuter ailleurs que sur l'appareil |

> Ce que la mise en œuvre a **ajouté**, une fois de plus par un test : `V8`
> garantissait « au plus une alerte ouverte par (ruche, indicateur) ». Une ruche
> peut être **à la fois légère et volée** — l'index unique a dû apprendre la
> catégorie, faute de quoi l'alerte de vol échouait en 409 au moment précis où
> elle sert. Et `GET /api/mesures/alertes` ne pouvait réussir que sur une liste
> **vide** depuis le SPRINT-06 : la lecture vivait hors transaction, et
> `Alerte.ruche` est `LAZY`. Aucun test ne l'avait jamais appelée avec une alerte
> ouverte.

> Ce que la mise en œuvre a **ajouté** au plan : « `SeuilAlerteService` sait déjà
> alerter, il suffit d'ajouter un indicateur » était juste — mais `alerte` porte
> **sa propre** liste d'indicateurs, que le plan n'avait pas vue. C'est un test
> d'intégration qui l'a montrée, sur un 409 en pleine ingestion valide.
>
> Et le poids par hausse n'était pas « une colonne de plus » : la fondre dans
> `mesure` aurait demandé de toucher à la clé primaire de l'hypertable la plus
> critique du système. Une table distincte ne coûte rien de plus et ne met rien
> en jeu.

---

### Lot G — Saisie vocale et assistance  ·  5 lignes  ·  ✅ LIVRÉ (6/5)

| Ligne couverte | Section |
|---|---|
| Saisie vocale des observations | §4 |
| Transcription IA embarquée, hors ligne | §4, §8 |
| Assistant / mentor IA, briefing quotidien | §7 |
| Traitement local, sans aucun trafic sortant | §8 |
| IA embarquée sur l'appareil | §8 |

| DB | Back | Contrat | Front | i18n |
|:--:|:--:|:--:|:--:|:--:|
| — | ● si transcription serveur, — si locale | ◐ | ● Web Speech / `MediaRecorder` | ● 3 langues de reconnaissance |

Le microservice `ia-service` existe et sert déjà la détection d'anomalie derrière
un port : l'ossature est là. Ce qui manquait relevait de la décision **D4**.

**Livré le 05/09/2026**, et la décision D4 est tranchée par
[ADR-013](../roadmap/operationnel/06_decisions/ADR-013-ou-tourne-l-ia.md) : **rien
de ce qui est personnel ne quitte l'appareil, et rien n'est envoyé à un tiers**.
Six lignes fermées au lieu de cinq — le 🟡 des notes vocales tombe avec la
transcription. Trois conséquences, qui doivent tenir ensemble :

1. **La transcription se fait sur l'appareil, ou pas du tout.** La dictée
   n'utilise `SpeechRecognition` que si le navigateur expose un réglage de
   traitement local ; ailleurs, elle refuse et l'écrit. Whisper WASM est écarté —
   quarante mégaoctets contredisent la raison d'être d'une PWA qui monte au
   rucher — mais le point d'entrée est unique.
2. **L'assistance ne passe par aucun modèle de langue.** Le briefing lit quatre
   registres et **cite ce qui le fonde**. Une phrase agréable qu'on ne peut pas
   remonter à sa source vaut moins qu'une liste sèche — et il faudrait envoyer
   l'historique dehors, ce que le point 1 interdit.
3. **Le mode local est une bascule, pas une promesse en prose** — et il y en a
   **deux**, parce qu'il y a deux trafics : le serveur coupe la météo et le
   microservice, le navigateur coupe les tuiles de carte. Les confondre ferait
   croire à l'exploitant que son poste est muet alors qu'il ne l'est qu'à moitié.

La ligne « IA embarquée sur l'appareil » se ferme en portant l'EWMA dans le
navigateur. Le risque n'y est pas l'erreur mais la **dérive** : `ewma.test.ts` et
`AnomalieEmbarqueeTest` fixent les mêmes nombres sur la même série.

**Ce que le lot ne ferme pas** : la réinitialisation de mot de passe (laissée par
J) attend un serveur d'envoi, pièce d'infrastructure et non ligne de code ; la
logistique multi-sites (laissée par B) reste un produit à elle seule.

---

### Lot I — Le carnet paramétrable  ·  5 lignes  ·  ✅ LIVRÉ (5/5)

Ce que le SPRINT-20 a structuré, il l'a figé : onze colonnes d'observation, les
mêmes pour tout le monde. Les concurrents qui gagnent sur ce terrain laissent
l'apiculteur **choisir ses cases**.

| Ligne couverte | Section | Verdict |
|---|---|:--:|
| Modèles / gabarits d'inspection réutilisables, champs activables | §3 | ✅ |
| Saisie par cases à cocher (~50 points analysables) | §3 | ✅ |
| Référentiel de traitements pré-renseigné | §3 | ✅ |
| Ordonnances vétérinaires | §3 | ✅ |
| Calculateurs apicoles : réfractomètre *(reporté du lot E)* | §6 | ✅ |

| DB | Back | Contrat | Front | i18n |
|:--:|:--:|:--:|:--:|:--:|
| ● gabarit + champs + référentiel produits | ● | ● | ● formulaire dynamique | ● libellés du référentiel |

**Le point de conception, et il est piégeux** : un formulaire paramétrable
détruit la statistique s'il autorise des champs libres. La sortie est un
référentiel **fermé** de points d'observation — les cinquante de HiveTracks —
dont un gabarit choisit un sous-ensemble. On active des cases existantes ; on
n'en invente pas.

**Ce qui existait déjà** : les onze colonnes de la `V19` et la colonne
`traitement.ordonnance`.

**Livré le 05/09/2026** (migration `V28`, 44 points, 13 produits). Quatre
décisions le tiennent :

1. **Le référentiel est fermé, et PostgreSQL le tient.** La `V28` retire
   `INSERT`, `UPDATE` et `DELETE` au rôle applicatif sur `point_observation` et
   `produit_traitement` : ces tables ne s'écrivent que par migration. Sans ce
   `REVOKE`, la fermeture ne serait qu'une intention écrite dans un commentaire.
2. **Le noyau reste des colonnes.** Les onze champs de la `V19` ne migrent pas :
   ils sont typés, indexés et lus par le moteur de règles. Le gabarit les allume
   ou les éteint — quatre booléens, pas une table — et masquer n'est pas effacer.
3. **Trois états, et non deux.** L'absence de relevé dit « pas regardé » ; une
   ligne à `false` dit « regardé, absent ». Les confondre ferait descendre tous
   les taux du parc dans le sens rassurant. L'écran emploie la case indéterminée
   (`aria-checked="mixed"`), un clic faisant tourner l'état.
4. **Le référentiel de produits pré-remplit, il ne fait pas autorité.** La notice
   fait foi, le traitement enregistré garde sa propre copie du délai, et une
   colonne dédiée porte la contrainte que le délai ne dit pas — « hausses
   retirées », là où zéro jour se lirait « on peut récolter ».

Le **réfractomètre**, reporté du lot E, ferme sa ligne après rectification du
verdict : la table de Chataway est publiée et vaut pour tout miel ; ce qui
appartient à l'appareil, c'est son étalonnage. La conversion refuse tout ce qui
sort de la plage tabulée plutôt que d'extrapoler.

---

### Lot J — Identification, comptes et confort  ·  7 lignes  ·  ✅ LIVRÉ (6/7)

> Livré le 04/09/2026 par la migration `V25` — QR et code court par ruche,
> écriture NFC, planche d'étiquettes imprimable, interface progressive,
> préférence de notification par agent, jeu de démonstration réversible. **Six
> lignes sur sept** ; la réinitialisation de mot de passe reste 🟡 : le chemin
> est ouvert côté produit, il manque un serveur d'envoi côté exploitation.
>
> Ce que la mise en œuvre a **ajouté** au plan : la distinction entre masquer et
> interdire. Le plan disait « interface progressive » ; il ne disait pas que la
> tentation serait de la construire sur les rôles. Les deux filtres coexistent
> désormais, et un test vérifie qu'ils ne se rejoignent pas.

Sept petites lignes sans lien technique entre elles, réunies parce qu'elles
partagent une propriété : chacune tient en moins d'une journée, et aucune ne
dépend d'un autre lot. C'est le lot qu'on glisse entre deux autres.

| Ligne couverte | Section | État |
|---|---|---|
| Identification par QR code sur la ruche | §4 | ✅ |
| Identification par NFC | §4 | ✅ là où l'API existe |
| Étiquetage durable : QR par ruche, planche imprimable, identifiant court | §13 | ✅ |
| Réinitialisation de mot de passe en libre-service | §7 | 🟡 serveur d'envoi |
| Interface progressive selon la taille de l'exploitation | §13 | ✅ |
| Réglage des notifications **par utilisateur** | §13 | ✅ |
| Jeu de démonstration réversible depuis l'application | §13 | ✅ |

| DB | Back | Contrat | Front | i18n |
|:--:|:--:|:--:|:--:|:--:|
| ◐ préférences utilisateur | ◐ + délégation à Keycloak | ◐ | ● planche imprimable, palette de réglages | ◐ |

**Trois précisions qui évitent de refaire le travail** :

- la **bibliothèque QR est déjà là** (`RecoltesVue.tsx` génère le QR d'un lot) ;
  manquent le QR par ruche, la planche imprimable et l'identifiant court lisible
  à l'œil quand le scan échoue — le §13 le déduit de trois concurrents ;
- le **NFC** n'est pas le jumeau du QR : `NDEFReader` n'existe ni sur iOS ni sur
  Firefox. Il se livre en complément, jamais en remplacement ;
- la **réinitialisation de mot de passe** ne s'écrit pas ici : le mot de passe
  appartient à Keycloak, et `CompteVue` dit déjà pourquoi il n'y touche pas. Le
  travail est d'exposer le parcours du fournisseur d'identité, pas de le
  réimplémenter. **C'est ce qui a été fait** — et ce qui reste n'est pas du
  code : sans serveur d'envoi, le lien mènerait à un formulaire dont le courriel
  ne part jamais, ce qui est pire que pas de lien du tout.

---

### Lot H — Le SIG environnemental  ·  9 lignes

**Le seul lot où un concurrent joue sur le terrain que Zümm revendique** —
BeeGIS — et le plus cher des huit.

| Ligne couverte | Section |
|---|---|
| Couches d'occupation du sol | §2 |
| Calcul des surfaces par type de couvert dans le rayon | §2 |
| Historique et rotation des cultures | §2 |
| Calendrier de floraison **observé** (au-delà du déclaratif de la `V21`) | §2 |
| Comptage / prévision de pollen | §2 |
| Croisement santé × flore environnante | §2 |
| Évaluation de l'exposition aux zones traitées | §2 |
| Rayon de butinage configurable | §2 |
| Millésime des données environnementales | §13 |

| DB | Back | Contrat | Front | i18n | Infra |
|:--:|:--:|:--:|:--:|:--:|:--:|
| ● table + index GiST | ● intersections PostGIS | ● | ● couches MapLibre + légende | ◐ | ● ingestion et volume |

**L'outil existe, la donnée non.** PostGIS sert déjà au voisinage et aux
grappes ; une intersection avec une couche de couvert en est la suite naturelle.
Tout le coût est dans la donnée d'entrée — voir la décision **D1**. Le rayon
configurable, lui, est une exception : c'est un curseur d'interface, `rayonsKm`
étant déjà une propriété de `CarteFond`.

---

## 3. Les quatre décisions à prendre avant d'écrire

Aucune ne se tranche en écrivant du code, et trois d'entre elles bloquaient un
lot entier. Les laisser implicites, c'est écrire du code qu'il faudra jeter.

**Trois sont tranchées** — D2 le 04/09/2026 (ADR-012), D4 et D3 le 05/09/2026
(ADR-013, ADR-014) — et chacune a débloqué son lot dans la journée qui a suivi.
La dernière, **D1**, ne dépend pas du code : elle demande de choisir un
référentiel de données, et ce choix décide du marché que le produit peut servir.

### D1 — Quel référentiel d'occupation du sol ? *(bloque le lot H)*

Les référentiels qui font la valeur de BeeGIS — RPG, CartoBio, BD Forêt, BD TOPO
— sont des produits de l'administration **française**. Ils ne couvrent ni le
Maghreb ni le Moyen-Orient, c'est-à-dire précisément le marché que le
trilinguisme FR/EN/AR vise.

| Option | Ce qu'on gagne | Ce qu'on perd |
|---|---|---|
| Référentiels français | Précision parcellaire, cultures nommées | Le produit ne sort pas de France |
| Copernicus / ESA WorldCover (10 m, mondial) | Couverture mondiale, gratuit | Classes grossières : « cultures », pas « colza » |
| OpenStreetMap `landuse` | Mondial, gratuit, déjà en fond de carte | Complétude très inégale selon les régions |

**Sans ce choix, le lot H ne commence pas.** Une architecture qui accepterait les
trois sources est possible — une couche générique `couvert_sol` alimentée par des
ingesteurs interchangeables — mais elle coûte plus cher que de trancher.

### ~~D2 — Le hors-ligne sélectif amende-t-il l'ADR existant ?~~ ✅ *tranchée le 04/09/2026*

Le refus de cacher `/api` est écrit et argumenté. Le lot C ne le contredisait pas
frontalement : il propose une ressource **explicite**, **déclenchée**, **datée**
et **périssable**. Il fallait néanmoins un ADR qui l'écrive, sans quoi la
prochaine relecture aurait défait le travail au nom du commentaire précédent.

C'est [ADR-012](../roadmap/operationnel/06_decisions/ADR-012-hors-ligne-selectif.md),
accepté. Le `navigateFallbackDenylist` n'a pas bougé ; mesures de capteurs, météo
et positions exactes restent hors de l'emport, ce qui était l'objection réelle.

### ~~D3 — Achète-t-on du matériel ?~~ ✅ *tranchée le 05/09/2026*

Les intégrations nommées (BroodMinder, BEEP, Sensorii) supposent de posséder le
matériel pour l'éprouver, et le Bluetooth direct est **absent d'iOS Safari** —
donc inaccessible à la PWA sur iPhone, qui est la moitié du parc. L'API
d'ingestion ouverte, elle, fonctionne déjà avec n'importe quelle passerelle.

**Recommandation suivie**, et étendue par
[ADR-014](../roadmap/operationnel/06_decisions/ADR-014-capteurs-du-commerce.md) :
non, on n'achète pas de matériel. L'anti-vol n'en demandait aucun — la donnée
était déjà là, il manquait une règle. Le Bluetooth se livre sur le **profil
standard**, dont les identifiants sont normalisés et non devinés. Les
intégrations nommées restent un partenariat. Et l'analyse vidéo/acoustique est
**refusée** : elle produirait un verdict inventé sur une question que
l'apiculteur ne peut vérifier qu'en ouvrant la ruche.

### ~~D4 — Où tourne l'IA ?~~ ✅ *tranchée le 05/09/2026*

« Traitement local, sans aucun trafic sortant » et « assistant IA » se
contredisaient tant qu'on n'avait pas dit **où** le modèle s'exécute.

C'est [ADR-013](../roadmap/operationnel/06_decisions/ADR-013-ou-tourne-l-ia.md),
accepté : **sur l'appareil, ou pas du tout**. La transcription n'a lieu que si le
navigateur la traite localement ; l'assistance ne passe par aucun modèle de
langue, parce qu'il faudrait lui envoyer l'historique de l'exploitation dehors —
et le briefing cite ses sources, ce qu'une phrase générée ne fait pas.

Le renoncement est réel et il est écrit dans l'ADR : Zümm n'aura pas de
conversation en langage naturel tant que cette décision tient.

---

## 4. Ce que le plan ne couvre pas, et pourquoi

Les **8 lignes ⛔** restent hors périmètre, et le plan ne les touche pas. Elles
ne sont pas des oublis : chacune est un produit à part entière ou une décision
déjà prise.

| Bloc ⛔ | Raison |
|---|---|
| Facturation, TVA, devis, clients, fournisseurs | Zümm est un SIG apicole, pas un ERP |
| Actionneurs et matériel propriétaire | Vendre du matériel est un autre métier |
| Applications natives Android / iOS | La PWA couvre le besoin, et les deux plateformes |
| Déclarations NAPI, référentiels agricoles français | Dispositifs nationaux, non portables |
| Licence libre, distribution du code | Cadre académique, tous droits réservés |
| Support prioritaire, freemium, Discord | Offre commerciale, pas fonctionnalité |

Les couvrir supposerait de rouvrir le positionnement produit, pas d'ajouter des
tables. Si c'est l'objectif, il commence par
[`STRATEGIE-PRODUIT.md`](STRATEGIE-PRODUIT.md), pas par ce plan.

---

## 5. Séquencement recommandé

| Ordre | Lot | Lignes | Cumul ✅ | Pourquoi ici | Décision |
|---|---|--:|--:|---|---|
| ~~1~~ | ~~**A** — règles~~ ✅ | 11 | **77** | *Livré le 01/09/2026 (V22)* | — |
| ~~2~~ | ~~**B** — lot par rucher~~ ✅ | 5 | **82** | *Livré le 02/09/2026 (V23)* — 5 sur 6 ; le reliquat (logistique multi-sites) passe au lot E | — |
| ~~3~~ | ~~**C** — hors ligne~~ ✅ | 8 | **90** | *Livré le 04/09/2026 (V24, ADR-012)* — 8 sur 9 ; la note vocale passe au lot G, faute de stockage binaire | ~~D2~~ **levée** |
| ~~4~~ | ~~**J** — identification et confort~~ ✅ | 6 | **96** | *Livré le 04/09/2026 (V25)* — 6 sur 7 ; la réinitialisation de mot de passe attend un serveur d'envoi | — |
| ~~5~~ | ~~**F₁** — batteries, poids par hausse, partage~~ ✅ | 4 | **100** | *Livré le 04/09/2026 (V26)* — les quatre lignes annoncées | — |
| ~~6~~ | ~~**E** — production, stock, matériel~~ ✅ | 12 | **112** | *Livré le 04/09/2026 (V27)* — 12 sur 14 ; le réfractomètre passe au lot I, la logistique multi-sites au lot G | — |
| ~~7~~ | ~~**I** — carnet paramétrable~~ ✅ | 5 | **117** | *Livré le 05/09/2026 (V28)* — les cinq lignes annoncées, réfractomètre compris | — |
| ~~8~~ | ~~**D** — élevage et généalogie~~ ✅ | 7 | **124** | *Livré le 05/09/2026 (V29)* — les sept lignes ; le §7 n'a plus de ❌ | ~~Critères de l'index~~ **tranchée** : cinq critères, aucune note globale |
| ~~9~~ | ~~**F₂** — capteurs du commerce~~ ✅ | 2 | **132** | *Livré le 05/09/2026 (V30, ADR-014)* — 2 lignes sur 4 : les intégrations nommées passent à 🟡, l'analyse vidéo/acoustique est refusée | ~~D3~~ **tranchée** : on n'achète pas de matériel |
| ~~10~~ | ~~**G** — voix et assistance~~ ✅ | 6 | **130** | *Livré le 05/09/2026 (ADR-013)* — 6 lignes au lieu des 5 annoncées : la note vocale laissée par C tombe avec la transcription. La réinitialisation laissée par J attend toujours un serveur d'envoi | ~~D4~~ **tranchée** : sur l'appareil, ou pas du tout |
| 11 | **H** — SIG environnemental | 9 | 145 | Le plus cher, et bloqué tant que la source de données n'est pas choisie | **D1** |

**Le lot C est remonté du huitième au troisième rang**, et le plan avait tort de
le placer si bas : il ne coûtait pas plus cher que J ou F₁, et il portait le
reproche n° 1 fait à trois des douze concurrents. Ce qui le retenait était la
décision D2, qu'un ADR d'une page a levée.

Dix lots étant livrés, **132 ✅ sur 153**. Il ne reste qu'**un seul lot** — le
lot H, SIG environnemental, 9 lignes — et une seule décision, **D1** : quel
référentiel d'occupation du sol. Trois des quatre décisions du départ sont
tranchées.

Le plafond reste **145 ✅**, jamais 153 : les 8 lignes ⛔ sont des décisions de
périmètre. À quoi s'ajoutent désormais **4 lignes 🟡 et 9 ❌** dont une partie ne
franchira pas la barre sans matériel, sans données de validation ou sans un
produit à part entière — le document les nomme une par une.

Le compte plafonne à **145 ✅**, jamais à 153 : les **8 lignes ⛔** ne sont pas des
travaux mais des décisions de périmètre (§4). 145 + 8 = 153, et le document est
alors intégralement statué.

---

## 6. Comment vérifier la couverture

Le document d'écart est la source ; ce plan n'en est que la vue ordonnée. Le
compte se relève, il ne se recopie pas :

> ⚠️ **La commande qui suit a été corrigée le 05/09/2026, et c'est instructif.**
> La précédente ne lisait que la **deuxième colonne** de chaque ligne : elle
> manquait donc tout le §13, dont la table porte une colonne de plus, et comptait
> en revanche les quatre lignes de la légende. Elle rendait **131** là où le
> document en porte **153**. Le compte publié n'était pas faux ; la commande
> censée le vérifier l'était — ce qui est plus grave, puisque c'est elle qu'on
> relance pour ne pas recopier un chiffre.

```bash
# Verdicts de l'inventaire, tels qu'ils sont écrits dans le document.
# Le verdict se cherche OU QU'IL SOIT dans la ligne : la table du §13 en a une
# colonne de plus que celles des §1 a 8. Et l'inventaire s'arrete a ces
# sections-la : la legende et la ventilation par couche portent les memes
# symboles sans etre des lignes d'ecart.
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
print(Counter(verdicts), 'total', len(verdicts))
PY
```

Une ligne ne passe à ✅ que si elle **cite le fichier qui le prouve**, comme
toutes celles des §§1 à 13 : c'est la règle du document depuis le 18/08/2026, et
c'est elle qui a permis d'y corriger trois verdicts périmés.
