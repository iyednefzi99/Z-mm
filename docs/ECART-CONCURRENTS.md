# Écart fonctionnel — Zümm face à douze outils apicoles du marché

> Analyse du 18/08/2026, **revérifiée contre le code le 04/09/2026** (périmètre
> SPRINT-27 inclus — voir les dix notes de révision en fin de document, §14 à
> §23). Le §1 est intégralement couvert depuis la migration `V21` (§17) ; les
> six lots livrés du plan de couverture ont fermé quarante-six lignes —
> **A** onze (§18), **B** cinq (§19), **C** huit (§20), **J** six (§21),
> **F₁** quatre (§22) et **E** douze (§23). Le compte passe les **deux tiers**
> du document. Les verdicts des §§1 à 13 intègrent le **registre sanitaire**
> (migration `V19`) et le **terrain** (migration `V20`) ; les §15 et §16 disent
> ce que ces livraisons ont changé, et surtout ce qu'elles n'ont **pas** réglé.
>
> Douze catalogues concurrents ont été dépouillés
> fonctionnalité par fonctionnalité, puis confrontés au **code réel du dépôt**
> (`backend/src/main/java/com/zumm`, `frontend/src`, `ia-service/`, `infra/`) —
> pas au cahier des charges, pas à la roadmap. Chaque ligne « couvert » cite le
> fichier qui le prouve.
>
> Complète [`STRATEGIE-PRODUIT.md`](STRATEGIE-PRODUIT.md) (positionnement de
> marché) et [`BENCHMARK-UX.md`](BENCHMARK-UX.md) (conventions d'interface) ;
> celui-ci ne traite que du **périmètre fonctionnel**.

## Sources

Douze catalogues dépouillés, regroupés par ce qu'ils cherchent à être. La
famille compte plus que le nom : elle dit contre quoi Zümm se compare vraiment.

| Famille | Outils | Ce que la famille apporte au dépouillement |
|---|---|---|
| **SIG / environnement** | **BeeGIS** (ITSAP) | Le seul qui soit, comme Zümm, un système d'information *géographique* avant d'être un carnet. Occupe le terrain que Zümm revendique |
| **Carnets de terrain** | **HiveSense**, **HiveTracks**, **BuzzWise / QueenGuard**, **HiveBook**, **APiLOG**, **BeeKube** | L'ergonomie de saisie : gants, propolis, absence de réseau. C'est là que Zümm est le plus en retard |
| **Suites d'exploitation** | **APIGO**, **ApiManager**, **BeeKeepPal** | Comptabilité, stock, clients, conformité. Terrain largement hors périmètre — mais pas entièrement |
| **Plateformes matérielles** | **BeeLog Digital**, **Onibi App** (BeeFutures) | Télémétrie temps réel, et pour Onibi des **actionneurs** : la ruche n'est plus seulement observée, elle est commandée |

Deux outils supplémentaires ont été cités sans catalogue détaillé : **HivePal**
(open source et auto-hébergeable, transcription IA) et **HiveOS / HiveTracks
Enterprise** (coordination d'équipes et logistique multi-sites). Ils
n'apparaissent que là où ils apportent un axe absent des douze autres.

## Légende des verdicts

| | Signification |
|---|---|
| ✅ | Couvert par du code en place |
| 🟡 | Partiellement couvert — la donnée existe mais pas la fonction, ou l'inverse |
| ❌ | Absent, et pertinent pour Zümm |
| ⛔ | Absent et **hors périmètre assumé** (voir §9) |

---

## 1. Terrain, ruchers et ruches

| Fonctionnalité concurrente | Zümm | Preuve / manque |
|---|---|---|
| Ruchers avec GPS, carte interactive | ✅ | `Site` (lat/long/altitude), PostGIS, `SiteController` (`/proches`, `/grappes`, `/{id}/voisins`), `CarteVue.tsx` + `CarteFond.tsx` (MapLibre sur tuiles OpenStreetMap, comme BeeLog Digital) |
| Adresse postale du rucher | ✅ | `Site.adresseRue`, `codePostal`, `ville`, `pays` (ISO 3166-1 alpha-2, V20). Elle sort **masquée plus fort que les coordonnées** : `PolitiquePositions` retire rue et code postal aux profils non propriétaires — deux décimales situent un rucher au kilomètre, une rue le situe au portail — et laisse la commune, qui est la maille à laquelle on s'y rend |
| Type de rucher, exposition, miellées, sources de nectar | ✅ | `Site.typeSite` (sédentaire, transhumance, fécondation, élevage, conservatoire), `Site.exposition` (huit orientations) et la table fille `ressource_florale` (V20) — vingt sources nommées, du colza au **jujubier** et au palmier dattier, pour ne pas reproduire hors de France le défaut de portabilité reproché à BeeGIS au §2. Les **miellées** sont portées depuis la `V21` par `mois_debut`/`mois_fin` sur chaque ressource : des MOIS et non des dates, parce qu'une floraison revient chaque année et qu'une date obligerait à tout ressaisir tous les ans. La fenêtre peut **enjamber l'année** — l'eucalyptus du Sud fleurit de novembre à février —, ce qu'un `entre début et fin` lirait exactement à l'envers. Ce qui reste hors de cette ligne et appartient à l'écart 5 : la couche d'occupation du sol, c'est-à-dire la floraison **observée par télédétection** plutôt que déclarée par l'apiculteur |
| Historique d'emplacement / transhumance | ✅ | Table `emplacement_site` (V20) : une ligne par emplacement occupé, la courante étant la seule dont `date_fin` est nulle — un **index unique partiel** l'impose en base, pas seulement au service. `GET /api/sites/{id}/emplacements` et `POST /api/sites/{id}/demenagement`, distinct du `PUT` qui **corrige** une position mal saisie sans rien inscrire : confondre les deux remplirait l'historique de fausses transhumances à chaque faute de frappe |
| **Planification du transport** de transhumance | ✅ | Table `transport` (V21) : véhicule, créneau, destination, capacité et nombre de ruches — l'écart entre les deux donnant le **nombre de voyages**, calculé au service et jamais stocké (41 ruches dans un camion de 20 font trois voyages, pas deux). `POST /api/transports/{id}/realiser` fait déménager le rucher en appelant `SiteService.demenager` : deux chemins, une seule règle, un seul historique. Sans coordonnées de destination, le plan reste un plan et le service le dit en clair — ouvrir un emplacement sans position ferait un trou dans l'historique, et un trou dans un historique ne se voit pas |
| Fiche ruche (type, cadres, hausses) | ✅ | `Ruche.modele` + `Compartiment` (`CORPS`/`HAUSSE`, `nbCadres`) |
| Référentiel de types de ruche (Langstroth, Warré, Dadant, Top-Bar) | ✅ | `Ruche.typeRuche` + `ck_ruche_type` (V19) : `langstroth`, `dadant`, `warre`, `voirnot`, `top_bar`, `kenyane`, `autre`. `Ruche.modele` reste le **texte libre au-dessous** du référentiel — il dit ce qu'une énumération ne dira jamais ; le type, lui, se compte |
| Cycle de vie de la ruche | ✅ | `EtatRuche` : créée → peuplée → active → en division → en collecte → clôturée |
| Archivage plutôt que suppression (morte, fusionnée, vendue) | ✅ | `Ruche.causeCloture` (V19) : `morte`, `fusionnee`, `vendue`, `volee`, `reformee`, `essaimee`, `autre`. `ck_ruche_cause_cloture` **refuse** une cause sur une ruche encore active — une ruche morte ne se confond plus avec une ruche vendue en statistiques |
| Couleur de ruche (repérage visuel terrain) | ✅ | `Ruche.couleur` (V19), neuf valeurs contrôlées de `blanc` à `bois` |
| Origine de la colonie (essaim, division, nucléus) | ✅ | `Ruche.origine` (V19) : `essaim_capture`, `essaim_achete`, `division`, `nucleus`, `paquet`, `achat`, `autre`. Le **type** d'origine est acquis ; la **filiation** l'est depuis le SPRINT-21, à la ligne suivante |
| **Enregistrement d'une division comme événement de plein droit** | ✅ | Table `division` (V20) : mère, fille, méthode, cadres de couvain et de provisions transférés, origine de la reine. La **filiation** est portée par un index unique partiel sur la fille — une ruche n'a qu'une mère. La fille reste facultative, et c'est délibéré : on divise souvent vers un nucléus qui ne sera enregistré comme ruche que s'il prend, et l'exiger ferait renoncer à saisir la division. `/api/divisions`, écran `EssaimsVue` |
| **Capture d'essaim** | ✅ | Table `capture_essaim` (V20) : origine (essaim naturel, piège, récupération, signalement), lieu, poids, hauteur. Le **logement est différé** (`POST /api/captures/{id}/loger`) — on capture un jour, on loge quand la colonie a pris ; l'exiger à la saisie reviendrait à n'enregistrer que les captures réussies, donc à perdre la statistique qui a de la valeur. **Sans coordonnées, délibérément** : le lieu d'une capture se raconte, il ne se cartographie pas, et une colonne de position de plus serait une surface à masquer de plus |
| Photos rattachées | ✅ | `photo` porte cinq cibles possibles — visite, ruche, site, reine, récolte (V20) — et un `CHECK num_nonnulls(...) = 1` qui en impose **exactement une** : une photo attachée à tout n'est attachée à rien, et une photo attachée à rien est une fuite de stockage. `/api/photos?cible=RUCHE&cibleId=12` ; la route historique `/api/visites/{id}/photos` est inchangée |
| Recherche globale (ruche, site, agent…) | ✅ | `GET /api/recherche?q=` (`RechercheService`) cherche dans **sept familles** — ruches, sites, fermes, fermiers, agents, récoltes, lots — et la palette `Ctrl/⌘ + K` affiche les résultats sous les écrans. Elle est volontairement pauvre : ni position, ni adresse, ni courriel, deux caractères minimum et un plafond par famille, parce que c'est le seul appel qui rend d'un coup un échantillon de toutes les tables. Le cloisonnement n'y est pas écrit — la RLS et la portée d'agent (V16) s'appliquent d'elles-mêmes. Choisir un résultat **ouvre la fiche** : `/ruches?id=42` surligne la ligne et l'amène à l'écran. Les routes restent plates — l'ADR du SPRINT-11 écarte un routeur — mais une route plate accepte un paramètre, sans segment dynamique ni bibliothèque. Le surlignage est posé dans `Table`, donc acquis pour les **vingt écrans** d'un coup |
| Sync calendrier iCal (Google/Outlook/Apple) | ✅ | `GET /api/plannings/agenda.ics` (`AgendaIcsService`) produit un vrai `VCALENDAR` : `UID` stable — réimporter met à jour l'événement au lieu de le dupliquer, ce que trois des douze ratent —, visite datée distinguée de la visite horodatée, échappement RFC 5545, repliement à 75 octets. Deux usages : le **téléchargement** authentifié, et depuis la `V21` l'**abonnement** — l'URL qu'un client de calendrier relit seul, sans session. L'objection qui l'avait fait écarter (« un jeton permanent est un secret non révocable ») a été levée plutôt que contournée : 256 bits tirés d'un `SecureRandom`, **jamais stockés** — la base n'en garde que l'empreinte SHA-256 —, expiration **obligatoire** bornée à un an, révocation d'un clic, et dernière utilisation affichée pour qu'un jeton oublié se remarque. Le flux ne rend que l'agenda **d'un agent**, et **aucune position** : un `.ics` est recopié chez un tiers à chaque rafraîchissement |

---

## 2. Environnement, ressources florales et occupation du sol

**Le domaine BeeGIS**, que six autres effleurent par la floraison ou le pollen.
La brique spatiale de Zümm est en place ; la donnée d'entrée manque.

| Fonctionnalité concurrente | Zümm | Preuve / manque |
|---|---|---|
| Rayon de butinage tracé sur la carte | ✅ | `CarteFond.tsx` : polygones **géodésiques** (`cercle()`, correction en cos(lat)), `rayonsKm = [1, 2, 3]` ; repli SVG équivalent dans `CarteVue.tsx` |
| Rayon de butinage **configurable** par l'utilisateur | 🟡 | `rayonsKm` est une propriété figée à `[1, 2, 3]`. Le curseur de `CarteVue` (`rayonKm`) pilote le **regroupement DBSCAN**, pas le butinage — les deux se confondent visuellement |
| Couches d'occupation du sol (cultures, forêt, hydrographie, urbanisation) | ❌ | Le fond est **une seule tuile raster** (`VITE_TUILES_URL`). Aucune couche vectorielle thématique, aucun WMS |
| Calcul des surfaces par type de couvert dans le rayon | ❌ | **L'outil existe, la donnée non** : PostGIS sert déjà à `ST_ClusterDBSCAN` et au voisinage. Une intersection avec une couche de couvert en serait la suite |
| Historique et rotation des cultures sur plusieurs années | ❌ | Aucune donnée agricole n'entre dans le système |
| Comparaison de plusieurs emplacements candidats (transhumance) | ✅ | `GET /api/sites/comparaison?ids=` (`ComparaisonSitesService`, SPRINT-23) aligne rendement **par ruche** sur deux saisons, flore déclarée et en fleur, altitude, exposition et densité de voisinage à 3 km. **Aucune note globale** : mélanger des kilos, des espèces et une altitude donnerait un chiffre qui a l'autorité d'une mesure sans en avoir la matière. Et **aucune coordonnée** en sortie |
| **Calendrier de floraison / suivi des miellées** (*bloom calendar*, *nectar flow*) | ❌ | HiveBook et HiveTracks en font un module. Zümm n'a aucune notion de saison mellifère |
| **Comptage / prévision de pollen** | ❌ | HiveSense et APiLOG le géolocalisent par rucher |
| Croisement santé du rucher × flore environnante (biodiversité) | ❌ | HiveTracks en fait un produit à part entière (*DaaS*, module RSE) |
| Vérification du taux de cultures bio dans le rayon réglementaire | ⛔ | Dépend de CartoBio (Agence Bio) — voir la note ci-dessous |
| Évaluation de l'exposition aux zones traitées | ❌ | Même dépendance à une couche agricole |

> **Note de portabilité, à trancher avant tout développement.** Les référentiels
> qui font la valeur de BeeGIS — RPG, CartoBio, BD Forêt, BD TOPO — sont des
> produits de l'administration **française**. Ils ne couvrent pas le Maghreb ni
> le Moyen-Orient, c'est-à-dire précisément le marché que le trilinguisme
> FR/EN/AR de Zümm vise. Reprendre la fonctionnalité suppose de choisir d'abord
> un référentiel d'occupation du sol de couverture plus large, et d'accepter sa
> résolution plus grossière. C'est une décision produit, pas un branchement d'API.

---

## 3. Visites, sanitaire et suivi de colonie

C'était le domaine où l'écart de **granularité** était le plus net : Zümm
modélisait **la visite** là où les onze carnets concurrents modélisent
**l'observation**. Le **SPRINT-20** l'a comblé pour l'essentiel — onze colonnes
d'observation sur `visite` (plus quatre de météo figée, quinze au total), une
table fille de pathologies **nommées**, et trois
tables d'actes sanitaires de plein droit (`traitement`, `nourrissement`,
`comptage_varroa`, migration `V19`).

Ce qui reste a changé de nature, et c'est le point à retenir : **ce n'est plus la
donnée qui manque, c'est ce qu'on en fait**. Aucune règle n'interdit une récolte
sous carence, aucun événement n'engendre de tâche, aucun indice n'agrège la santé
d'une colonie — alors que les trois s'appuieraient désormais sur des colonnes
existantes et indexées.

| Fonctionnalité concurrente | Zümm | Preuve / manque |
|---|---|---|
| Inspection datée, horodatée, par ruche | ✅ | `Visite` (date, heure, durée, agent, raison) |
| Planification et **approbation** des visites | ✅ | `Planning` + `StatutPlanning` (proposé/approuvé/refusé) — **aucun des douze ne l'a** |
| Rapport de visite PDF | ✅ | `RapportVisitePdfService`, `GET /api/visites/{id}/rapport.pdf` |
| Saisie par cases à cocher (~50 points analysables) | ✅ | **Quarante-quatre** points dans un référentiel FERMÉ (`point_observation`, `V28`), huit familles, relevés par `releve_observation` et comptés par `GET /api/carnet/statistiques`. « Environ cinquante » est le chiffre annoncé par HiveTracks ; on n'en compte pas cinquante pour l'annoncer aussi. Deux règles portent tout le reste : le référentiel ne s'écrit **que par migration** — le rôle applicatif n'a que le SELECT dessus —, et **l'absence de relevé n'est pas un « non »**. La case a donc trois états à l'écran comme en base |
| Force de la colonie | ✅ | `EffectifQualitatif` reste l'echelle et `cadresCouvain`/`cadresMiel`/`cadresPollen` le compte ; l'**indice de sante** (`IndiceColonieService`, SPRINT-22) les agrege enfin, avec les reserves et le motif de ponte. Rien n'est stocke : l'indice se calcule a chaque lecture, comme le taux de varroa, pour qu'une formule qui change n'entre jamais en contradiction avec une valeur figee |
| Tempérament / agressivité | ✅ | `visite.temperament` (V19) : `doux`, `normal`, `agressif`. Six catalogues le demandaient |
| État du couvain (œufs, operculé, motif de ponte) | ✅ | `couvainOeufs`, `couvainLarves`, `couvainOpercule` — trois booléens **facultatifs** (`null` = non observé, ce qui n'est pas `false`) — et `motifPonte` (`compact`, `lacunaire`, `irregulier`, `absent`), V19 |
| Réserves miel / pollen, contenu des cadres | ✅ | `cadresCouvain`, `cadresMiel`, `cadresPollen` (V19), bornés de 0 à 40 par `ck_visite_cadres` |
| Cellules royales et cause (essaimage / supersédure / urgence) | ✅ | `cellulesRoyales` (le compte) et `cellulesRoyalesCause` (V19) : la cause n'est acceptée que si le compte est > 0 — une cause sans cellule serait une saisie incohérente |
| Reine vue / statut à chaque visite | ✅ | `visite.reineVue` (V19) est une case de la visite. `SuiviReine` reste l'événement séparé qui porte le **cycle de vie** (marquage, remplacement, essaimage) : les deux ne disent pas la même chose et n'avaient pas à fusionner |
| État sanitaire | ✅ | `EtatSante` reste le curseur a trois positions saisi au rucher, mais il n'est plus seul a repondre : l'indice croise etat declare, pathologies **confirmees** — `suspectee` etant la gravite par defaut, la compter comme une maladie ferait chuter toute colonie sur laquelle on a eu un doute —, reserves et verdict varroa. Chaque penalite sort avec son motif, pour que la note s'explique au lieu de s'asséner |
| Maladies et ravageurs nommés (loque, petit coléoptère…) | ✅ | `observation_pathologie` (V19) : onze valeurs — `varroose`, `loque_americaine`, `loque_europeenne`, `nosemose`, `petit_coleoptere`, `fausse_teigne`, `frelon_asiatique`, `couvain_sacciforme`, `mycose`, `pesticide`, `autre` — et quatre gravités dont `suspectee` **par défaut** : au rucher on constate un symptôme, on ne pose pas un diagnostic de laboratoire |
| **Suivi du varroa** (méthode, comptage, taux d'infestation calculé) | ✅ | Table `comptage_varroa` (V19), cinq méthodes (`lange`, `sucre_glace`, `alcool`, `co2`, `desoperculation`), **comptages bruts** stockés. Le taux et le verdict sont **calculés au service** (`ComptageVarroaService.taux()` / `verdict()`) et jamais stockés : ils n'ont pas la même unité selon la méthode. Route `/api/varroa`, écran `SanitaireVue.tsx` |
| **Traitements sanitaires** (produit, dose, cible, durée, délai de carence) | ✅ | Table `traitement` (V19) : produit commercial, **substance active** (pour raisonner l'alternance), cible parmi huit, dose **et son unité** (`ck_traitement_dose_unite` refuse une dose sans unité), période, délai de carence. `/api/traitements`, `TraitementService` |
| Référentiel de traitements pré-renseigné | ✅ | `produit_traitement` (`V28`) : treize varroacides, leur substance active, leur forme et leur délai. **Il pré-remplit, il ne fait pas autorité** — la notice et l'AMM du pays font foi, et la colonne `mention` le dit à chaque saisie. Le traitement enregistré garde sa PROPRE copie du délai : corriger le référentiel demain ne doit pas réécrire un registre d'élevage d'hier. Ce que le seul délai ne dit pas est porté par une colonne dédiée — sur la plupart de ces produits la contrainte réelle n'est pas une carence mais « hausses retirées », et zéro jour lu seul se comprend comme « on peut récolter » |
| Délai de carence / date de retrait avant récolte | ✅ | `traitement.date_retrait` est desormais **opposable** : `RecolteService` refuse la recolte d'une ruche sous carence en **409** — la requete est valide, c'est l'etat qui s'y oppose — et le message dit le produit et la date de fin. La porte de sortie est tracee : forcer exige un motif (`recolte.carence_forcee`, `motif_forcage`) et depose une entree d'audit sous l'action **`forcage`**, distincte des creations ordinaires. Sans cette porte, l'apiculteur cesserait d'enregistrer le TRAITEMENT, et le registre deviendrait faux la ou il n'etait qu'incomplet |
| **Nourrissements** (type, quantité, motif) | ✅ | Table `nourrissement` (V19) : sept types d'aliment (`sirop_1_1` … `eau`), quantité avec unité, six motifs (`stimulation`, `hivernage`, `disette`, `secours`, `transhumance`, `autre`). `/api/nourrissements` |
| Ordonnances vétérinaires | ✅ | `traitement.ordonnance_veterinaire` et `ordonnance_date` (`V28`) rendent la référence **vérifiable** — elle disait qu'une ordonnance existe, jamais qui l'a signée ni quand —, et le scan s'attache par la **sixième cible** de `Photo`. La base refuse une date sans référence ; l'inverse reste permis, parce qu'une référence notée au rucher se complète le soir. Le fichier lui-même reste hors du dépôt : `photo.url` ne porte qu'une adresse, comme depuis le SPRINT-21 |
| **Score de santé calculé par colonie** | ✅ | `GET /api/indices` : 100 moins les penalites observees, avec leurs motifs. **`composantes = 0` quand rien n'a pu etre evalue** — une colonie non visitee n'est pas saine, elle est inconnue, et l'ecran affiche « non evalue » plutot qu'une jauge sur du vide |
| **Score de risque d'essaimage** | ✅ | Meme route. Cellules royales et leur **cause** (60 points pour `essaimage`, 30 sinon), leur nombre, la densite de couvain et un corps plein. Il est distinct de la sante, et c'est le point : une colonie qui va essaimer se porte tres bien — les confondre ferait rater l'essaim |
| **Recommandations automatiques / tâches générées** | ✅ | `MoteurRegles` (SPRINT-22) et cinq regles : fin de carence a trois jours, controle de ponte a J+7, varroa au-dessus du seuil, reserves au plus bas, visite compromise par la meteo. Chaque tache porte la **cle** de ce qui l'a declenchee (`carence-retrait:42`) et un index unique partiel empeche la regle de la recreer a chaque passage — sans quoi la liste se remplirait de doublons jusqu'a n'etre plus lue |
| Rappels programmés (retrait de traitement, contrôle de ponte à J+7) | ✅ | Les deux exemples cites par le document sont exactement les deux premieres regles ecrites. Le retrait est propose **trois jours avant** la fin de carence : une tache qui arrive le matin ou elle est due n'est pas un rappel, c'est un constat de retard |
| Modèles / gabarits d'inspection réutilisables, champs activables | ✅ | `gabarit_inspection` et `gabarit_point` (`V28`), édités depuis l'écran de configuration. **Le noyau reste des colonnes** : les onze champs de la `V19` ne migrent pas dans le référentiel — ils sont typés, indexés et lus par le moteur de règles. Le gabarit les ALLUME ou les ÉTEINT, section par section ; masquer n'est pas effacer, et une visite déjà saisie garde ce qu'elle portait |
| Météo attachée à l'observation | ✅ | Quatre colonnes **figées** sur `visite` (V19) : `meteoTemperatureC`, `meteoHumiditePct`, `meteoVentKmh` et leur **source** (`open-meteo`, `simulation`, `saisie`) — une estimation ne se lit pas comme une mesure. La corrélation météo × production est débloquée ; elle n'est pas encore calculée |

---

## 4. Saisie au rucher : ergonomie et robustesse terrain

Axe le plus documenté du corpus, et déjà identifié comme critique par
[`BENCHMARK-UX.md`](BENCHMARK-UX.md) §1. Un apiculteur saisit **avec des gants,
les doigts pleins de propolis, souvent sans réseau**. HiveSense en a fait son
produit entier ; HiveTracks y répond par du papier.

| Fonctionnalité concurrente | Zümm | Preuve / manque |
|---|---|---|
| **Saisie vocale** des observations | ❌ | Aucune API vocale dans le front : ni `SpeechRecognition`, ni `MediaRecorder`, ni `getUserMedia`. Le seul geste qui fonctionne avec des gants |
| **Transcription IA embarquée, hors ligne** (Whisper sur l'appareil) | ❌ | HiveSense, HiveBook et HivePal transcrivent sans réseau ni serveur. Le microservice IA de Zümm (`ia-service/scoring.py`) fait de la **détection d'anomalie EWMA sur séries de capteurs** — rien à voir avec du langage |
| Notes vocales simplement enregistrées, sans transcription | 🟡 | `VisitesVue` enregistre (`MediaRecorder`) et rejoue une note pendant la saisie (SPRINT-24) — mais **elle ne quitte pas l'appareil**. Le dépôt n'a aucun stockage binaire : `photo.url` ne porte qu'une adresse, `traitement.ordonnance` qu'une référence. Encoder de l'audio en base64 dans un champ texte aurait fabriqué un stockage de fichiers clandestin, invisible en revue et impossible à purger |
| **Fiches d'inspection imprimables** (saisie au stylo, saisie différée) | ✅ | `GET /api/ruchers/{id}/fiche-inspection.pdf` (`FicheInspectionPdfService`, SPRINT-24) : une ligne par ruche, les colonnes de la grille structurée du SPRINT-20, et **une ligne vide de plus** — au rucher, on trouve toujours une colonie qui n'est pas encore au fichier |
| Identification par **QR code** sur la ruche | ✅ | `ui/etiquettes.tsx` (SPRINT-25) : QR par ruche (`zumm:ruche:42`), **code court** `R-42` lisible à l'œil nu quand le QR est sale ou propolisé, et planche imprimable par rucher. Le code court n'est pas un second identifiant — c'est celui de la ruche, préfixé : en inventer un opaque aurait créé deux façons de nommer la même colonie |
| Identification par **NFC** | ✅ | `terrain/nfc.ts` (SPRINT-25) écrit sur la puce **la charge du QR**, à l'identique — deux charges pour le même objet donneraient un jour deux réponses. Le bouton n'apparaît que là où `NDEFReader` existe : absent d'iOS et de Firefox, il reste un **complément** du QR, jamais un remplacement, et l'écran le dit ailleurs |
| **Interventions groupées / scan en masse** sur tout un rucher | ✅ | `POST /api/{traitements,nourrissements,recoltes}/lot` (`OperationsLotService`, SPRINT-23) : une cible cumulative (ruches nommées **et/ou** rucher entier, dédoublonnée au serveur), **une transaction par ruche**, et un rapport qui **nomme** les refus avec leur motif métier — sans quoi le rejeu porterait sur les quarante ruches au lieu des trois. Le scan par code-barres reste dû |
| Saisie hors ligne au rucher | ✅ | `frontend/src/offline/file.ts` — file de mutations persistée, rejeu à l'événement `online`, **clé d'idempotence stable** (`FiltreIdempotence`) |
| **Consultation** hors ligne des données | ✅ | `GET /api/ruchers/{id}/emport` + `offline/emport.ts` (SPRINT-24), sous [ADR-012](../roadmap/operationnel/06_decisions/ADR-012-hors-ligne-selectif.md). Le `navigateFallbackDenylist` **reste inchangé** : rien ne passe par le service worker. L'emport est déclenché, borné à un rucher, **daté par le serveur** et périssable à quatorze jours. Ni mesures de capteurs, ni météo, ni position exacte — l'objection de 2026 tenait pour elles, et elle est conservée |
| Résolution de conflits multi-agents hors ligne | ✅ | En-tête `X-Zumm-Version` sur `PUT /api/visites/{id}` → **409 avec la version du serveur** (SPRINT-24). La saisie refusée passe en **quarantaine** au lieu d'être jetée : avant, tout 4xx au rejeu était traité comme « traité » et l'observation faite au rucher disparaissait sans un mot. Le serveur ne fusionne pas — décider laquelle de deux observations dit vrai sur un couvain est un arbitrage d'apiculteur |
| PWA installable, sans passage par un store | ✅ | SPRINT-13. Couvre iOS **et** Android, là où HiveBook est réservé à l'écosystème Apple |
| Mode clair / sombre explicite, testé | ✅ | `theme/theme.test.tsx` — 7 tests, bascule clair/sombre/système persistée, `data-theme` sur `:root`. **BeeKeepPal se fait reprocher un texte noir sur fond sombre, illisible au rucher** |
| Fonctionnement dégradé quand un service tiers est bloqué | ✅ | `MeteoService` retombe sur une estimation déterministe si Open-Meteo est injoignable ; `CarteVue` retombe sur un rendu SVG sans WebGL ni tuiles. **BeeGIS se fait précisément reprocher d'être bloqué par les pare-feux** |

---

## 5. Matériel connecté, télémétrie et commande à distance

Nouvel axe apporté par HiveSense, BeeLog Digital, BeeKube et surtout Onibi.
C'est le domaine où Zümm est **structurellement en avance** sur dix des douze —
et où Onibi est hors de portée, parce qu'il vend du matériel.

| Fonctionnalité concurrente | Zümm | Preuve / manque |
|---|---|---|
| Balances connectées, poids en continu | ✅ | `Mesure` + TimescaleDB (hypertable, migration V5), `TypeIndicateur.POIDS`, `CapteursVue.tsx` — les carnets purs saisissent le poids à la main |
| Température / humidité en série temporelle | ✅ | `TypeIndicateur` couvre poids, température, humidité, activité |
| **API ouverte d'ingestion** (balances et stations DIY) | ✅ | `POST /api/mesures` (`MesureController.ingerer`), contrat publié en OpenAPI 3. C'est ce que BeeKube revendique comme différenciateur |
| **Connexion Bluetooth directe** aux capteurs du commerce (BroodMinder, BEEP, SensorPush, Inkbird…) | ❌ | Aucun appel Web Bluetooth. Zümm suppose une passerelle qui pousse vers l'API ; HiveSense supprime la passerelle |
| Intégrations nommées de capteurs du commerce | ❌ | Le point d'entrée est générique, l'adaptation reste à la charge du fabricant |
| Poids **par hausse** | ✅ | Table `mesure_compartiment` (`V26`), hypertable distincte, FK composite vers `compartiment`. **Distincte de `mesure`, et c'est le choix** : celle-là porte ce qu'une balance pèse sous la ruche entière, celle-ci ce qu'on attribue à un étage. Les fondre aurait demandé de rendre nullable une colonne de la clé primaire de l'hypertable la plus critique du système — ou un sentinel, qui aurait fait perdre la clé étrangère. Une hausse jamais pesée rend `null`, jamais 0 |
| Alertes à seuils sur capteurs | ✅ | `SeuilAlerteService` (hystérésis anti-rebond), `Alerte`, `NotificationAlerteService` |
| Notification e-mail à l'ouverture d'une alerte | ✅ | `NotificationAlerteService` — **un seul destinataire par message** (`setTo`), jamais de liste. Voir la leçon n°1 du §10 |
| Prévision de récolte | ✅ | `PrevisionRecolteService` — régression linéaire sur la série de poids, projection 7 j |
| Détection d'anomalie par IA | ✅ | `MoteurAnomalie` (port) + `ClientAnomalieIA` → microservice Python. HiveBook fait la même chose sur l'appareil |
| Partage d'un flux de télémétrie entre utilisateurs | ✅ | `partage_telemetrie` (`V26`) + `GET /api/flux/{jeton}`, **sans session** : la courbe d'UNE ruche, montrée à un mentor, un technicien sanitaire ou un groupement. Même forme que l'abonnement iCalendar du SPRINT-21 — jeton de 256 bits jamais stocké en clair, expiration obligatoire, révocation, usage horodaté — et le destinataire ne reçoit ni identifiant, ni rucher, ni position |
| **Analyse vidéo / acoustique à l'entrée** (comptage de trafic, perte de reine) | ❌ | Onibi seul. Le port `MoteurAnomalie` est prêt à recevoir un second moteur — l'architecture ne s'y oppose pas, la donnée manque |
| **Actionneurs à distance** (portes robotisées, protection anti-frelon) | ⛔ | Zümm observe, il ne commande pas. Piloter un actionneur engage la sécurité de la colonie et suppose du matériel propriétaire |
| **Alarme anti-vol / détection de basculement** | ❌ | Le vol de ruches est pourtant la menace qui justifie `PolitiquePositions` (voir sa javadoc). Zümm **cache** la position pour protéger du vol ; Onibi **alerte** quand il survient. Les deux réponses sont complémentaires, Zümm n'a que la première |
| Supervision de l'état des batteries des capteurs | ✅ | `TypeIndicateur.ALIMENTATION` (`V26`), en pourcent, avec son seuil dans `ConfigZumm.ini` (`batterie_min_pourcent`, 20 % par défaut). Le reproche fait à BeeLog et Onibi ne porte pas sur l'absence de mesure mais sur la panne **silencieuse** : l'indicateur passe donc par `SeuilAlerteService` comme les autres — même hystérésis, même table d'alertes, même notification |

---

## 6. Production, stock, matériel et commerce

| Fonctionnalité concurrente | Zümm | Preuve / manque |
|---|---|---|
| Récolte par ruche, quantité, type de miel, lot | ✅ | `Recolte` + `RecolteController` |
| Traçabilité du lot | ✅ | `GET /api/recoltes/tracabilite/{lot}` |
| Conditionnement et mention d'origine réglementaire | ✅ | `LotConditionnement` + `LotConditionnementService` — directive (UE) 2024/1438, consolidation par pays, somme à 100 %. **Aucun des douze ne l'implémente à ce niveau** |
| Chaîne récolte → maturation → mise en pot, DLUO/DDM | ✅ | `lot_conditionnement.date_maturation` et `date_durabilite` (`V27`). **Deux dates, pas un workflow** : la tentation était un enchaînement avec ses états et ses transitions ; deux dates disent la même chose et ne bloquent aucune saisie. La colonne porte le nom légal actuel — la DDM a remplacé la DLUO en 2015 |
| **Produits autres que le miel** (cire, pollen, propolis, gelée royale, essaims, reines) | ✅ | `recolte.type_produit` et `recolte.unite` (`V27`) : sept produits, deux unités. Une colonne et non une table — une récolte de cire est une récolte, faite le même jour sur la même ruche ; lui donner sa propre table aurait dupliqué la traçabilité, le lot, le forçage de carence et l'export. **L'unité est indispensable** : cinq essaims ne pèsent pas cinq kilogrammes, et la base refuse la combinaison incohérente |
| Comparaison des récoltes année par année | ✅ | `GET /api/saisons` (`ComparaisonSaisonsService`, SPRINT-27), en **années civiles** : une saison à cheval sur deux années rendrait toute comparaison ambiguë. Le rendement compte les ruches **qui ont produit**, pas celles qui existaient — doubler son cheptel double la production sans rien améliorer |
| Récolte sur tout un rucher en une saisie | ✅ | `POST /api/recoltes/lot` : la quantité saisie vaut pour **chaque** ruche, jamais comme un total à répartir — répartir quarante kilos en quarante lignes d'un kilo écrirait une masse fausse par colonie, et la traçabilité repose sur ces masses. Le forçage de carence n'y est pas proposé : passer outre est une décision **par colonie** |
| **Inventaire du matériel** (hausses, cadres, extracteurs) et état d'entretien | ✅ | Table `materiel` (`V27`), dix catégories, quatre états, et l'écran « Matériel ». La **prochaine échéance n'est pas stockée** : elle se calcule, la ranger en base créerait une valeur à maintenir en cohérence avec la dernière maintenance |
| Stock de consommables avec seuils de réapprovisionnement | ✅ | Table `consommable` (`V27`) et `RegleStockBas`. Le seuil est **obligatoire**, avec un défaut à zéro : un stock sans seuil est un inventaire — il dit ce qu'on a, jamais ce qui manque. Les mouvements sont relatifs (`+`/`−`) et non absolus, sans quoi deux personnes qui prélèvent le même jour s'écraseraient |
| Comptabilité : dépenses, recettes, rentabilité par ruche | ✅ | Table `depense` (`V27`) et `GET /api/depenses/bilan`. **Trois refus tiennent le module** : aucune dépense non affectée n'est répartie (une assurance ne se divise pas par le nombre de ruches) ; les recettes sont une *valorisation* au prix paramétré, pas un chiffre d'affaires ; et seul le miel est valorisé. La frontière du §9 est intacte : ni facturation, ni TVA, ni clients |
| Scan de reçus, rapports fiscaux | ⛔ | HiveBook le fait par IA embarquée ; hors périmètre (voir §9) |
| Gestion clients, fournisseurs, ventes | ⛔ | BeeKeepPal, APIGO, ApiManager ; hors périmètre |
| **Calculateurs apicoles** (sirop 1:1 et 2:1, infestation varroa, prix du miel, réfractomètre) | ✅ | Le **réfractomètre** ferme la ligne au SPRINT-28, après rectification du verdict précédent : la table de Chataway est **publiée** et vaut pour tout miel ; ce qui appartient à l'appareil, c'est son **étalonnage**. La conversion dit donc de quoi elle part — un indice, une température de mesure, un appareil étalonné —, corrige à 20 °C, et **refuse tout ce qui sort de la plage tabulée** plutôt que d'extrapoler. Le taux se range sur la récolte (`recolte.humidite_pct`), réservé au miel |

---

## 7. Élevage, génétique, pilotage et données

| Fonctionnalité concurrente | Zümm | Preuve / manque |
|---|---|---|
| Fiche reine (race, année, marquage, statut, origine) | ✅ | `SuiviReine` (statut, couleurMarquage, anneeNaissance, race), `ReineController`, `ReinesVue.tsx` |
| Historique des reines d'une ruche | ✅ | `SuiviReine` est événementiel — l'historique est natif |
| Ailes clippées, fournisseur, ruche-mère | ❌ | Champs absents |
| **Généalogie / arbre de lignées** | ❌ | Pas de lien reine → reine mère. BeeKube, APiLOG et HiveBook en font un argument fort |
| Dates de greffage, suivi d'élevage | ❌ | Absent |
| **Index génétique multicritère** (hygiène, résistance varroa, douceur) | ❌ | **La dépendance est levée** : les critères d'inspection dont il découle existent depuis le SPRINT-20 (couvain, tempérament, pathologies, comptages de varroa — §3). L'index lui-même n'est calculé nulle part |
| Photos de reine et de motif de ponte | ❌ | `Photo` ne se rattache qu'à une visite |
| **Registre d'élevage réglementaire** (PDF / Excel) | ❌ | Seul le rapport de visite est en PDF — mais **sa dépendance est levée** : traitements et nourrissements sont structurés depuis le SPRINT-20 (§3). Il ne reste qu'un service d'édition. **Cinq concurrents le génèrent automatiquement** |
| Rapports de conformité / certification bio | ❌ | HiveBook et APIGO les produisent |
| Déclaration NAPI, déclaration annuelle des ruches | ⛔ | Dispositifs nationaux français |
| Multi-utilisateurs et rôles | ✅ | `Agent` + `RoleAgent` (apiculteur/superviseur/responsable/admin), `InvitationController`, RBAC Keycloak (`SecurityConfig.matriceRbac`). **Plus fin** que les rôles d'ApiManager et sans plafond d'accès, là où BeeKeepPal limite à trois |
| Lisibilité des droits pour l'utilisateur | ✅ | SPRINT-19 : la navigation masque les écrans fermés au rôle (`routage/routes.ts`, `ROLES_ONGLET`), `InterditVue` explique le refus au lieu d'un 403 nu, et `PermissionsVue` publie la matrice complète — *voici les serrures, voici qui a les clés*. **Aucun des douze ne montre ses règles d'accès à ses utilisateurs** |
| Compte en libre-service (création, consultation) | ✅ | `ConnexionVue` (connexion **et** inscription depuis l'application), `CompteVue` : utilisateur, exploitation et rôles lus dans la session serveur, aucun jeton en mémoire du navigateur (ADR-006) |
| **Réinitialisation de mot de passe en libre-service** | 🟡 | Le chemin est **ouvert côté produit** (SPRINT-25) : `/api/info` publie l'URL du parcours du fournisseur d'identité, et `RecuperationVue` affiche le lien dès qu'elle est renseignée — sinon elle continue d'aiguiller vers le responsable. Ce qui manque n'est plus du code : c'est un **serveur d'envoi**, que l'exploitant configure (`infra/keycloak/README.md`). Le lien reste caché tant qu'il n'y en a pas, parce qu'un formulaire dont le courriel ne part jamais est pire que pas de lien |
| Cloisonnement des données entre exploitations | ✅ | Multi-tenant + RLS PostgreSQL, `TenantFilter`, `tenant_id` obligatoire dans le JWT. **Aucun des douze ne le documente** |
| Piste d'audit | ✅ | `AuditEntree`, `AuditAspect`, `AuditController`, `AuditVue.tsx` |
| Tâches avec échéance | ✅ | `Tache` + `GET /api/taches/rappels` |
| Priorité, type et notification de tâche | ✅ | `tache.priorite` (quatre niveaux), `categorie` (huit valeurs) et `origine` — `manuelle` ou `regle`, avec le code de la regle, pour qu'une tache proposee puisse se justifier a l'ecran. Notification par courriel des seules taches **critiques** : notifier chaque creation reviendrait a n'en notifier aucune, les messages etant filtres des la troisieme semaine. Un message par destinataire, `setTo` d'une seule adresse — la faute de BeeKeepPal (§10) reste structurellement impossible |
| Tableau de bord de synthèse | ✅ | `TableauDeBordController` : calendrier, production, alertes sanitaires, synthèse, prévisions |
| **Vision à trois niveaux** (ruche → rucher → exploitation) | ✅ | `GET /api/ruchers/synthese` (`SyntheseRucherService`, SPRINT-23) + volet « Par rucher » : santé moyenne, risque d'essaimage **maximal** (et non moyen — une colonie prête à essaimer ne se dilue pas), colonies sous carence, alertes, tâches et production, triés du plus préoccupant au plus calme. La moyenne ne porte que sur les colonies **réellement évaluées**, et un rucher jamais visité rend `null` : « inconnu » n'est pas « en mauvaise santé » |
| Tournée optimisée du jour | ✅ | `OptimiseurTournee` (plus proche voisin + 2-opt), `GET /api/plannings/tournee` — **APIGO l'annonce, Zümm l'a** |
| Coordination d'équipes terrain, logistique multi-sites | 🟡 | `GET /api/equipe/charge` (SPRINT-23) ajoute la charge par agent — ruches, **ruchers concernés** (trois ruches sur trois ruchers font trois déplacements), tâches ouvertes, en retard, critiques, visites à sept jours — assortie de la phrase qui dit qu'elle ne sert **pas** à comparer des personnes. Restent la logistique et la chaîne d'approvisionnement, le terrain de HiveOS |
| Assistant / mentor IA, briefing quotidien | ❌ | L'IA de Zümm surveille des séries de capteurs ; elle ne lit pas l'historique d'une colonie et ne propose rien |
| **Météo prévisionnelle** | ✅ | `OpenMeteoFournisseur` : `current=` + `daily=` dans **un seul appel**, `timezone=auto`, horizon borné à 16 j, `GET /api/meteo?siteId=&jours=`. Repli simulation déterministe hors ligne |
| Tâches programmées selon la météo | ✅ | `RegleMeteoDefavorable` croise les plannings des cinq prochains jours avec la prevision du site — une seule interrogation par RUCHER, pas par ruche — et propose de replanifier sous 5 mm de pluie, 40 km/h de vent ou 12 °C. Elle ne **deplace rien** : decider a la place d'un agent peut-etre deja en route, sur la foi d'une prevision a cinq jours, serait pire que le probleme. Fournisseur indisponible = pas d'avis, jamais « beau temps » |
| Graphiques | ✅ | `ui/graphiques.tsx` (SVG maison, ADR-007) |
| Corrélation météo ↔ production | ✅ | `GET /api/correlations/meteo` : coefficient de Pearson entre chaque indicateur **fige sur la visite** (V19) et ce que la ruche a produit dans les trente jours suivants. Le service **refuse de conclure** en dessous de douze paires, et rend `null` — jamais 0 — quand le coefficient n'existe pas : zero dirait « aucun lien mesure », ce qui est une affirmation. Une visite sans recolte compte pour zero kilo et non pour rien, sans quoi la question deviendrait « quand on recolte, recolte-t-on ? » |
| Export CSV | ✅ | `ExportService` (CSV RFC 4180 + TXT), `GET /api/export/{visites,ruches}` |
| Export XLSX | ✅ | `ClasseurXlsx` (SPRINT-27), **sans dépendance** : cinq parties XML dans un ZIP, cellules en chaînes en ligne. Apache POI pèse une douzaine de mégaoctets pour produire ici une grille sans style ni formule — le même arbitrage que [ADR-007](../roadmap/operationnel/06_decisions/ADR-007-graphiques-svg.md) sur Chart.js. Le jour où un format de nombre manque vraiment, POI redevient le bon choix, et le commentaire de la classe le dit |
| Export du reste (récoltes, mesures, lots, tâches…) | ✅ | **Quatorze ressources** (SPRINT-27), la ressource devenant un paramètre de chemin. Le refactor était le point : chaque ressource produit une *grille*, le rendu est fait une fois par format — sans quoi la neutralisation des formules (`CWE-1236`) serait à refaire dans quatorze branches, et disparaîtrait de l'une d'elles. Les mesures sont bornées à quatre-vingt-dix jours, et l'export des ruchers ne porte **aucune coordonnée** : un fichier circule, il n'a pas de rôle porteur |
| Bilan annuel PDF | ✅ | `GET /api/saisons/{annee}/bilan.pdf` (`BilanAnnuelPdfService`, SPRINT-27) : production, dépenses par poste, rentabilité ruche par ruche. Un document qu'on **archive**, pas un tableau de bord — et un pied de page rappelle que les recettes sont une valorisation, parce qu'un PDF survit à la conversation qui l'a produit |
| Trilingue FR / EN / AR avec RTL | ✅ | `i18n/locales/{fr,en,ar}.json`, parité vérifiée par test. **Aucun des douze ne propose l'arabe** — différenciateur le plus net vers le Maghreb et le Moyen-Orient |

---

## 8. Confidentialité, souveraineté et déploiement

Axe apporté par HiveSense (*mode local uniquement*), HiveBook (*IA sur
l'appareil*), HivePal (*open source, auto-hébergeable*) et APIGO (*hébergement
européen*). Quatre outils sur douze font de la propriété des données un
argument de vente — c'est une tendance, pas une exception.

| Fonctionnalité concurrente | Zümm | Preuve / manque |
|---|---|---|
| Auto-hébergement complet | ✅ | `infra/docker-compose.yml` (API, PostGIS/TimescaleDB, Keycloak, Nginx, Prometheus, Grafana) + `sauvegarde.sh` / `restauration.sh`. Onze des douze sont des SaaS |
| Sauvegarde **et restauration éprouvée** | ✅ | `infra/tester-restauration.sh` : le scénario détruit la donnée avant de la restaurer — « une sauvegarde jamais restaurée n'est pas une sauvegarde ». HiveBook se fait reprocher la perte définitive des données sans iCloud |
| Cloisonnement fort entre exploitations | ✅ | RLS PostgreSQL, pas seulement un filtre applicatif |
| Traitement local, sans aucun trafic sortant | ❌ | Zümm appelle `api.open-meteo.com` et `tile.openstreetmap.org`, et délègue l'anomalie à un microservice. Les deux premiers ont un repli hors ligne ; il n'existe pas de **bascule explicite** « aucun appel sortant » comme le mode local de HiveSense |
| IA embarquée sur l'appareil | ❌ | L'inférence est serveur (`ia-service`). C'est un choix d'architecture, pas un oubli — mais il coûte le mode 100 % local |
| Licence ouverte, code réutilisable | ⛔ | **Aucun fichier `LICENSE`, tous droits réservés** — décision assumée du cadre académique (`README.md` §Licence). HivePal est open source ; Zümm est auto-hébergeable **sans** être libre. Ne pas confondre les deux |

---

## 9. Hors périmètre assumé

| Bloc | Raison |
|---|---|
| Facturation Factur-X, devis, TVA, relances, scan de reçus, rapports fiscaux | Zümm est un **SIG apicole**, pas un ERP. Ce bloc a le poids d'un produit à lui seul |
| Gestion clients, fournisseurs, achats, revente | Idem |
| Déclaration NAPI, déclaration annuelle des ruches | Dispositifs nationaux français |
| Référentiels agricoles français (RPG, CartoBio, BD Forêt, BD TOPO) | Non portables hors de France — voir §2. La *fonction* reste pertinente, ces *sources* non |
| **Actionneurs et matériel propriétaire** (portes robotisées, bases connectées) | Vendre du matériel est un autre métier. Zümm reste du côté de la donnée : il ingère ce que d'autres mesurent |
| Applications natives Android / iOS, écosystème Apple exclusif | La PWA couvre le besoin terrain, installation et hors-ligne compris — et couvre les deux plateformes, là où HiveBook en exclut une |
| Licence libre, distribution du code | Cadre académique, tous droits réservés (`README.md`) |
| Support prioritaire, communauté Discord, offre freemium | Offre commerciale, pas fonctionnalité |

---

## 10. Ce que les *limites* des concurrents apprennent

Les reproches faits à ces douze outils sont des spécifications gratuites. Six
sont directement actionnables ou déjà couverts :

1. **Une notification de masse est une fuite de données en puissance.**
   BeeKeepPal a envoyé des rappels automatiques avec les adresses de plusieurs
   utilisateurs **en copie visible** (CC au lieu de CCI). Chez Zümm,
   `NotificationAlerteService` compose un message par alerte et appelle
   `setTo(destinataire)` avec **une seule adresse** : la faute est
   structurellement impossible aujourd'hui. Elle redeviendrait possible le jour
   où quelqu'un ajoutera un envoi groupé — c'est un invariant à protéger, pas un
   acquis.
2. **Le mode sombre n'est pas cosmétique, c'est une condition de lisibilité au
   soleil.** BeeKeepPal rend du texte noir sur fond sombre, illisible au rucher.
   Zümm a une bascule clair/sombre/système persistée et **sept tests** qui la
   couvrent (`theme/theme.test.tsx`). À garder tel quel.
3. **Toute donnée environnementale doit afficher son millésime.** Le reproche
   principal fait à BeeGIS est l'inertie : les données agricoles reflètent une
   campagne antérieure de un à deux ans. C'est exactement le raisonnement qui a
   fait refuser la mise en cache de `/api` dans `vite.config.ts`. La réponse est
   la même : afficher la fraîcheur, pas masquer la donnée.
4. **La dépendance à un domaine tiers est un risque d'exploitation.** BeeGIS est
   inaccessible derrière certains pare-feux d'entreprise. Zümm dépend de
   `tile.openstreetmap.org` et `api.open-meteo.com` ; les deux ont un repli
   (`CarteVue` en SVG, `MeteoService` en simulation). **Ces replis sont un actif —
   ils doivent être testés, pas seulement écrits.**
5. **La voix a un coût caché : le bruit, et la batterie.** HiveSense recommande
   de fermer les applications en arrière-plan parce que Whisper sur l'appareil
   consomme ; APiLOG recommande un micro-casque parce que le vent dégrade la
   transcription. Si Zümm ajoute la saisie vocale, la relecture avant validation
   n'est pas optionnelle — et le trilinguisme FR/EN/AR multiplie la difficulté
   par trois. C'est aussi ce qui en ferait un différenciateur : **personne ne le
   fait en arabe**.
6. **La réponse la moins chère au problème des gants est du papier.**
   HiveTracks imprime des fiches vierges ; l'apiculteur note au stylo et saisit
   au retour. Zümm produit déjà des PDF (`RapportVisitePdfService`) : la fiche
   vierge est le même moteur, dans l'autre sens. Coût sans commune mesure avec
   celui de la reconnaissance vocale, pour le même problème.

---

## 11. Synthèse

**Là où Zümm est devant les douze** — aucun de ces points n'est dans leurs
catalogues, et cela vaut d'être dit en soutenance :

- multi-tenant avec RLS PostgreSQL, audit de bout en bout et restauration
  éprouvée par un test destructif ;
- capteurs en continu (TimescaleDB), alertes à hystérésis et **API d'ingestion
  ouverte**, là où dix des douze saisissent le poids à la main ou dépendent d'un
  matériel propriétaire ;
- intelligence spatiale réelle (PostGIS : sites proches, grappes, voisinage),
  cercles de butinage géodésiques et tournée optimisée ;
- workflow de planification **avec approbation** par un superviseur ;
- mention d'origine conforme à la directive (UE) 2024/1438 ;
- trilinguisme FR/EN/AR avec RTL, parité vérifiée par test ;
- détection d'anomalie déléguée à un microservice, derrière un port ;
- auto-hébergement complet, et replis quand un service tiers tombe.

**Les écarts qui comptent, par coût d'opportunité décroissant** — l'ordre est
celui du 18/08/2026 et il est **conservé** malgré la livraison du SPRINT-20, pour
que les renvois « écart n° x » du reste du document restent lisibles. Les écarts
1 et 3 sont barrés : ils sont faits.

1. ~~**Traitements, nourrissements et varroa comme entités de plein droit.**~~
   ✅ **Livré au SPRINT-20** : migration `V19`, trois tables, trois services,
   trois routes (`/api/traitements`, `/api/nourrissements`, `/api/varroa`) et
   l'écran `SanitaireVue`. La ligne est conservée à son rang plutôt que
   supprimée : elle était le socle des écarts 2, 4 et 7, et c'est ce qui explique
   qu'ils soient devenus attaquables. **Ce qu'il en reste** : le délai de carence
   est consigné, calculé et affiché mais **n'interdit rien** (aucune règle ne
   refuse une récolte sur une ruche sous carence), et le référentiel de produits
   pré-renseigné reste absent.
2. ~~**Interventions groupées et scan en masse**~~ ✅ **Livré au SPRINT-23**
   (lot B) : `POST /api/{traitements,nourrissements,recoltes}/lot`, une
   transaction par ruche et un rapport qui nomme les refus. Le cas d'école — un
   traitement saisi quarante fois sur un rucher de quarante ruches — est réglé.
   **Ce qu'il en reste** : le **scan** proprement dit, c'est-à-dire le QR par
   ruche et la planche d'étiquettes, qui relève du §12.
3. ~~**Observations d'inspection structurées**~~ ✅ **Livré au SPRINT-20** :
   onze colonnes d'observation sur `visite` (couvain, motif de ponte, cadres,
   cellules royales et leur cause, tempérament, reine vue) et la table fille
   `observation_pathologie` pour les maladies **nommées**. Le texte libre
   subsiste, il n'est plus la seule trace. **Ce qu'il en reste**, et qui change de
   nature : le **score de santé** et le **risque d'essaimage** que ces colonnes
   rendent enfin calculables, et les gabarits d'inspection paramétrables.
4. **Tâches et rappels engendrés par les événements.** Six catalogues
   recommandent ou programment ; `TacheService` ne fait qu'enregistrer. Le
   retrait d'un traitement après son délai de carence est le cas d'école : la
   règle est mécanique, la valeur immédiate — et depuis le SPRINT-20 la donnée
   est là, `traitement.date_retrait` étant en base et indexée. Il ne manque plus
   que la règle.
5. **Couche d'occupation du sol et calendrier de floraison.** Le seul axe où un
   concurrent (BeeGIS) joue sur le terrain revendiqué par Zümm — le SIG. PostGIS
   est déjà là ; il manque la donnée d'entrée et le choix d'un référentiel
   portable (§2).
6. **Saisie vocale.** La **fiche imprimable** qui la précédait dans cette ligne
   est ✅ **livrée au SPRINT-24** (`FicheInspectionPdfService`) — deux ordres de
   grandeur moins chère, pour le même problème des gants. **Ce qu'il en reste** :
   la note vocale est enregistrée et rejouable, mais **locale à l'appareil**, le
   dépôt n'ayant aucun stockage binaire ; et la transcription reste suspendue à
   la décision D4 du plan de couverture — on ne peut pas promettre « traitement
   local, aucun trafic sortant » et « assistant IA » tant qu'on n'a pas dit **où**
   le modèle s'exécute.
7. **Généalogie des reines.** Trois concurrents en font leur argument central ;
   c'est une clé étrangère réflexive sur `SuiviReine` et une vue d'arbre.

**Écarts à faible coût, à prendre en même temps :**

| Écart | Coût |
|---|---|
| ~~Fiches d'inspection vierges imprimables~~ | ✅ **Livré au SPRINT-24** : `FicheInspectionPdfService`, une ligne par ruche et les colonnes de la grille du SPRINT-20 |
| ~~Calculateurs (sirop 1:1 et 2:1, prix du miel)~~ | ✅ **Livrés au SPRINT-27** : `CalculateurApicole`, fonctions pures. Le réfractomètre reste dehors — voir §23 |
| ~~Agrégats au niveau **rucher**~~ | ✅ **Livré au SPRINT-23** : `SyntheseRucherService` et le volet « Par rucher », six requêtes en bloc quel que soit le nombre de ruchers |
| Rayons de butinage réglables | `rayonsKm` est déjà une propriété de `CarteFond` ; il manque le contrôle d'interface |
| ~~Photos rattachées à une ruche, une reine, une récolte~~ | ✅ **Livré au SPRINT-21** : cinq cibles sur `photo` (V20) et un `CHECK` qui en impose exactement une |
| ~~QR code par ruche et planche d'étiquettes imprimable~~ | ✅ **Livré au SPRINT-25** : `ui/etiquettes.tsx`, code court `R-42` et planche imprimable par rucher |
| ~~Référentiel de types de ruche, couleur, cause de clôture~~ | ✅ **Livré au SPRINT-20** : quatre colonnes sur `ruche` (V19), avec un `CHECK` qui refuse une cause de clôture sur une ruche encore active |
| ~~Priorité et catégorie sur les tâches~~ | ✅ **Livré au SPRINT-22** (lot A) : quatre colonnes sur `tache`, dont l'origine et la clé de déclenchement |
| ~~Export CSV étendu aux autres entités, puis XLSX~~ | ✅ **Livré au SPRINT-27** : quatorze ressources, trois formats, et un écrivain XLSX sans dépendance |
| ~~Météo figée sur la visite~~ | ✅ **Livré au SPRINT-20** : quatre colonnes figées sur `visite` **avec leur source** (`open-meteo`, `simulation`, `saisie`). La corrélation météo ↔ production est calculée depuis le lot A (`CorrelationMeteoService`), qui refuse d'interpréter sous douze paires |
| ~~Indicateur d'alimentation des capteurs~~ | ✅ **Livré au SPRINT-26** : `TypeIndicateur.ALIMENTATION` (`V26`) et son seuil dans `ConfigZumm.ini` |

**~~Un écart à trancher explicitement : la consultation hors ligne.~~ Tranché,
et livré au SPRINT-24.** Le commentaire de `vite.config.ts` refusait de mettre
`/api` en cache — une mesure de capteur périmée induirait l'apiculteur en erreur.
L'argument était juste pour les mesures, beaucoup moins pour la liste des ruches
d'un rucher, qui ne change pas dans la journée. **Six des douze concurrents
mettent la consultation hors ligne en tête de leur argumentaire**, et c'est le
reproche n°1 fait à HiveTracks, BeeKube et BuzzWise. La forme retenue est celle
que le §13 avait déduite des concurrents, et que fixe désormais
[ADR-012](../roadmap/operationnel/06_decisions/ADR-012-hors-ligne-selectif.md) :
un « emporter ce rucher hors ligne » **déclenché** par l'apiculteur, **borné** à
un rucher, **daté** par le serveur et **périssable** — et non un cache
automatique. Le `navigateFallbackDenylist` n'a pas bougé d'une ligne ; les
mesures de capteurs restent dehors, ce qui était l'objection réelle.

> Rappel du cadre : toute nouvelle table métier issue de ces écarts porte
> `tenant_id`, sa politique RLS et une clé étrangère composite `(id, tenant_id)`
> (voir `CLAUDE.md`, invariants de sécurité). Tout nouveau DTO portant des
> coordonnées passe par `PolitiquePositions`.

---

## 12. Où tombe le travail : ventilation par couche

Les onze sections précédentes classent les écarts par **domaine métier**. Celle-ci
les reclasse par **couche technique** : c'est la vue dont on a besoin pour
estimer, séquencer et répartir le travail — un écart « facile » côté produit peut
être une migration lourde, et un écart « profond » peut n'être qu'un composant
React.

### Ce que coûte une tranche complète dans ce dépôt

Mesuré sur la tranche existante la plus comparable, le suivi de reine
(SPRINT-07) : **6 fichiers back** (`domain/SuiviReine`, `repository/`,
`service/`, `controller/`, DTO `Corps` + `Reponse`), **1 migration**
(`V9__suivi_reine_sprint07.sql`), **5 fichiers front** (`api/types.ts`,
`api/client.ts`, `api/parite.ts`, une vue, `routage/routes.ts`), **3 fichiers de
locale**, plus les tests des deux côtés.

Trois chaînes traversent toute nouvelle fonctionnalité et ne se négocient pas :

| Chaîne | Ce qu'elle impose | Où elle est vérifiée |
|---|---|---|
| **Contrat d'API** | Toute route ou tout DTO nouveau ⇒ régénérer `openapi.json` (`./mvnw -B verify -Dit.test=ContratOpenApiIT`, Docker requis), puis `npm run api:contrat`, puis déclarer le type dans `api/parite.ts` | `tsc` échoue sur toute dérive silencieuse |
| **Sécurité multi-tenant** | Toute table métier porte `tenant_id`, sa politique RLS et une clé étrangère **composite** `(id, tenant_id)` ; tout DTO à coordonnées passe par `PolitiquePositions` | `ModeleMetierIsolationIT`, `RbacIT`, `SecuriteApiIT` |
| **Trilinguisme** | Toute chaîne visible existe en `fr`, `en` et `ar`, à structure identique | parité des traductions (`i18n/langue.test.tsx`) + `PariteI18nTest` côté back |

### Matrice écart × couche

`●` travail substantiel · `◐` travail léger · `—` rien à faire.
La colonne **contrat** couvre `openapi.json` + `contrat.ts` + `parite.ts` :
mécanique, mais jamais gratuite (Docker requis).

| Écart | DB / Flyway | Back | Contrat | Front | i18n | Infra / CI |
|---|:--:|:--:|:--:|:--:|:--:|:--:|
| ~~Traitements, nourrissements, varroa~~ ✅ S20 | ● 3 tables | ● 3 tranches | ● | ● 1 écran à 3 volets | ● | — |
| ~~Observations d'inspection structurées~~ ✅ S20 | ● colonnes **et** table fille | ● `Visite` + DTO | ● | ● formulaire à cases | ● nombreux libellés | — |
| Interventions groupées / scan en masse | — | ● routes de lot, transaction, idempotence | ● | ● sélection multiple | ◐ | — |
| Tâches et rappels engendrés par événement | ◐ colonnes `priorite`, `type`, `origine` | ● règles + événements applicatifs | ● | ◐ affichage existant | ◐ | — |
| Score de santé / risque d'essaimage | — dérivé | ● service d'agrégation (ou `ia-service`) | ● | ● | ◐ | ◐ si l'IA s'en mêle |
| Registre d'élevage réglementaire | — | ● service PDF | ● | ◐ un bouton | ◐ | — |
| Couche d'occupation du sol | ● table + index GiST | ● intersection PostGIS | ● | ● couche MapLibre + légende | ◐ | ● ingestion et volume |
| Calendrier de floraison / miellées | ● | ● | ● | ● | ● | — |
| Généalogie des reines | ● FK réflexive sur `suivi_reine` | ● | ● | ● vue d'arbre SVG | ◐ | — |
| Saisie vocale | — | — si transcription locale, ● si serveur | ◐ | ● Web Speech / `MediaRecorder`, permissions PWA | ● 3 langues de reconnaissance | ◐ |
| ~~Fiches d'inspection imprimables~~ ✅ | — | ● `FicheInspectionPdfService` | ◐ | ◐ un bouton | ◐ | — |
| ~~Consultation hors ligne~~ ✅ | — | ● route d'emport horodatée | ● | ● `offline/emport.ts`, écran dédié | ◐ | — |
| Bluetooth direct vers les capteurs | — | — l'API d'ingestion existe | — | ● Web Bluetooth (absent d'iOS Safari) | ◐ | — |
| Indicateur d'alimentation des capteurs | ◐ valeur d'énumération | ◐ seuils | ◐ | ◐ | ◐ | — |
| Agrégats au niveau **rucher** | — | ● requêtes + DTO | ● | ● | ◐ | — |
| Calculateurs apicoles | — | ● fonctions pures + contrôleur | ● | ● | ● | — |
| Export étendu, puis XLSX | — | ● `ExportService` (+ dépendance `pom.xml`) | ◐ | ◐ boutons | ◐ | — |
| Photos hors visite | ● colonnes + FK composites | ● | ● | ● | — | — |
| ~~Type de ruche, couleur, cause de clôture~~ ✅ S20 | ● colonnes + énumération | ● | ● | ● | ● | — |
| Rayons de butinage réglables | — | — | — | ● un contrôle d'interface | ◐ | — |
| QR par ruche, planche d'étiquettes | — | ◐ endpoint PDF | ◐ | ● | ◐ | — |
| ~~Météo figée sur la visite~~ ✅ S20 | ● colonnes sur `visite` | ● | ● | — | — | — |
| Actionneurs, anti-vol, vidéo à l'entrée | ⛔ | ⛔ | ⛔ | ⛔ | ⛔ | ⛔ matériel |

> **Les quatre lignes barrées ont été livrées au SPRINT-20.** Leurs `●` ne sont
> donc plus une estimation mais un **coût constaté** — utile pour calibrer les
> lignes qui restent : le bloc sanitaire complet (migration, trois tranches back,
> régénération de contrat, un écran, trois locales) a tenu en un sprint de 35
> points.

### Lecture par couche

**Base de données (`db/migration`, prochaine version `V20`).** Neuf écarts
exigeaient une migration, et **c'était la couche qui commandait le calendrier** :
rien ne se code au-dessus d'une table qui n'existe pas. Les trois tables
sanitaires (traitement, nourrissement, comptage varroa) devaient être posées d'un
bloc — même forme (ruche, date, agent, produit, quantité), même politique RLS ;
les séparer aurait multiplié les revues de sécurité pour rien. **C'est ce qui a
été fait** : `V19__sanitaire_observations_sprint20.sql` porte les trois tables,
la table fille de pathologies, les colonnes d'observation et de météo sur
`visite` et les quatre colonnes de référentiel sur `ruche` — une migration, une
revue RLS. Le verrou est donc levé, et **ce qui reste est parallélisable**. Un piège propre au dépôt : la
couche d'occupation du sol n'est pas une table de plus mais un **volume de
données géographiques**, avec index GiST et stratégie de mise à jour. C'est la
seule ligne de la matrice où l'infra travaille vraiment. Et
[ADR-008](../roadmap/operationnel/06_decisions/ADR-008-rls-contre-compression.md)
reste vrai : pas de compression TimescaleDB sous RLS.

**Back-end (`com.zumm`).** La couche qui porte la majorité du travail, mais la
mieux outillée : la tranche `domain → repository → service → controller → dto`
est répétitive et éprouvée dix-neuf fois, et le plancher JaCoCo à 80 % en fait
le seul endroit où l'effort de test est **contraint par le build**. Deux écarts
n'y touchent presque pas — le Bluetooth direct (l'API d'ingestion existe déjà) et
les rayons réglables (rien du tout).

**Contrat.** Colonne discrète et systématiquement sous-estimée : Docker, un cycle
Maven complet et une régénération côté front pour **chaque** DTO nouveau.
Regrouper les écarts qui touchent l'API en une seule campagne divise ce coût ;
les prendre un par un le paie autant de fois.

**Front-end (`frontend/src`).** Quatre écarts y sont **majoritaires ou
exclusifs** : la consultation hors ligne (service worker et `vite.config.ts`, pas
une ligne de Java), la saisie vocale, le Bluetooth direct, les rayons réglables.
Ce sont aussi ceux qui pèsent le plus dans les comparatifs concurrents — la
matrice montre donc que **l'écart perçu par l'apiculteur n'est pas là où se
trouve le gros du code**. À l'inverse, la couche d'occupation du sol demande du
travail sur les cinq colonnes à la fois : c'est l'écart le plus transversal du
document.

**i18n.** Jamais dominante, jamais nulle. Deux écarts y coûtent réellement : les
observations structurées (chaque case cochable est un libellé × 3 langues, arabe
RTL compris) et la saisie vocale, où la langue n'est plus un libellé mais un
**modèle de reconnaissance**.

**Infra / CI.** Presque vide, et c'est un bon signe : la pile
(`docker-compose.yml`, Keycloak, Prometheus, Grafana, sauvegarde et restauration
éprouvée) absorbe les écarts fonctionnels sans se réoutiller. Seules l'ingestion
géographique et, marginalement, l'IA vocale la feraient bouger.

### Ordre de passage qu'impose cette lecture

1. ~~**Une seule migration `V19`**~~ ✅ **faite au SPRINT-20**, dans la forme
   prescrite ici : les trois tables sanitaires, les colonnes d'observation, la
   météo figée sur la visite et les colonnes bon marché (couleur, cause de
   clôture, type de ruche) en une migration et une revue RLS.
2. ~~**Les tranches back correspondantes**~~ ✅ **faites** — trois services,
   trois contrôleurs, une seule régénération de contrat pour l'ensemble.
3. **Le front** : ✅ l'écran sanitaire et le formulaire d'inspection à cases sont
   livrés ; ❌ les **interventions groupées** ne le sont pas, et c'est désormais
   la première tranche à prendre.
4. **En parallèle et sans dépendance** — les rayons réglables, la fiche
   imprimable, les calculateurs, la consultation hors ligne. Aucun des quatre
   n'attend une migration ; aucun n'a bougé au SPRINT-20.
5. **Nouvelles têtes de file, désormais sans verrou en amont** (§15) :
   l'interdiction de récolte sous carence (back pur, la donnée existe), les
   tâches engendrées par la fin de carence (back puis front), et le score de
   santé que les colonnes d'observation rendent calculable.
---

## 13. Les contournements conseillés, convertis en exigences

Les douze catalogues se terminent tous par une rubrique « solutions pour éviter
les inconvénients ». Ces conseils sont adressés à l'**utilisateur** — emporter
une batterie externe, glisser le téléphone dans une pochette, exporter tous les
mois, charger les fiches avant de partir. Lus autrement, ce sont des **aveux de
conception** : chaque manœuvre manuelle recommandée est une fonction que le
logiciel n'a pas rendue.

C'est le matériau le plus utile du corpus, parce qu'il est déjà validé par
l'usage : personne ne conseille un contournement pour un problème qui ne se pose
pas. La règle de lecture est donc simple — **si trois outils conseillent la même
manœuvre, c'est une fonctionnalité manquante, pas une bonne pratique.**

### Contournements récurrents (conseillés par trois outils ou plus)

| Contournement conseillé à l'utilisateur | Qui le conseille | Exigence pour Zümm | Verdict | Couche |
|---|---|---|:--:|---|
| « Ouvrez les fiches de vos ruchers **avant** d'entrer en zone blanche » | BeeKube, BuzzWise, HiveTracks | **Emporter un rucher hors ligne** : préchargement explicite, déclenché par l'utilisateur, avec date de fraîcheur affichée | ✅ | `GET /api/ruchers/{id}/emport` en **un seul appel**, écran « Hors ligne » (SPRINT-24). La date du prélèvement est répétée à chaque lecture, et un emport de plus de quatorze jours n'est plus servi |
| « Notez au stylo ou en mémo vocal, saisissez au retour sur ordinateur » | ApiManager, BuzzWise, HiveTracks | **Brouillon de visite reprenable** sur un autre appareil | ✅ | table `brouillon_visite` (`V24`), `PUT /api/brouillons` idempotent sur (agent, ruche). Contenu **opaque** au serveur : valider une saisie en cours ferait perdre ce qu'on cherche à sauver. Seule table du schéma dont la RLS **n'ouvre pas** sur une portée globale |
| « Exportez en CSV/PDF chaque mois, ou en fin de saison » | ApiManager, HiveSense, BuzzWise, HiveTracks, HiveBook | **Export intégral** + **rappel d'archivage saisonnier** | ✅ | Quatorze ressources en trois formats, et `RegleArchivageSaisonnier` — une tâche par an, en **novembre** : la saison est close au nord, et il reste l'hiver pour ressaisir ce qui manque. Janvier arriverait après les déclarations, août tomberait en pleine miellée |
| « Emportez une batterie externe, fermez les applications gourmandes » | BeeKube, HiveSense, HiveBook | **Mode économie** assumé : rendu léger, pas de WebGL, pas de rafraîchissement de fond | ✅ | `terrain/economie.ts` (SPRINT-24) : interrupteur explicite, jamais déclenché par le niveau de batterie — une application qui change de comportement sans le dire passe pour cassée. L'écran **dit ce qu'il coupe** |
| « Collez des QR codes / posez des puces NFC plutôt que des autocollants » | APiLOG, ApiManager, APIGO | **Étiquetage durable** : QR par ruche, planche imprimable, identifiant court lisible à l'œil nu quand le scan échoue | ✅ | `ui/etiquettes.tsx` + `terrain/nfc.ts` (SPRINT-25). La planche est rendue par le **navigateur** et non par le serveur : la bibliothèque QR y vit déjà depuis l'US-033, et l'ajouter au back-end aurait introduit une dépendance Java pour ce que le navigateur sait faire. L'impression ne sort que les étiquettes (`@media print`) |
| « Équipez d'abord vos ruches souches ou vos ruchers stratégiques » | BeeLog Digital, Onibi | **Marquage de priorité** sur la ruche et le rucher, repris dans le tableau de bord et la tournée | ✅ | migration `V23`, trois niveaux (`basse`/`normale`/`haute`), index partiels sur ce qui n'est pas `normale` ; repris dans la synthèse par rucher |

**Verdicts détaillés.**

- *Mode économie* 🟡 : `CarteVue.tsx` bascule déjà sur un rendu SVG sans WebGL, et
  son commentaire cite explicitement l'économie de batterie. Mais c'est une
  bascule locale à un écran, pas un **mode** applicatif — rien ne le propose, rien
  ne le mémorise, rien ne l'étend aux autres vues.
- *Étiquetage* 🟡 : `RecoltesVue.tsx` génère déjà un QR de lot de récolte ; la
  bibliothèque est là. Manquent le QR par ruche, la planche imprimable, et
  l'identifiant court de repli.
- *Interface progressive* 🟡 **(relevé au 25/08/2026)** : le SPRINT-19 a rangé les
  dix-neuf écrans en cinq familles métier et **retire de la navigation** ceux que
  le rôle n'ouvre pas (`ROLES_ONGLET`). L'interface s'adapte donc déjà — mais au
  **rôle**, pas au besoin : l'apiculteur de trois ruches et celui de trois cents
  voient exactement le même produit. Le gardiennage, lui, ne s'arrête plus à
  l'onglet : `ROLES_ECRITURE` et `peutEcrire()` retirent les commandes d'écriture
  des cinq écrans de référentiel (`fermiers`, `fermes`, `sites`, `ruches`,
  `agents`) — « le bouton *Nouveau* qui doit disparaître, pas l'onglet » est
  fait, et `routage.test.ts` le vérifie.

### Contournements propres à un outil, mais transposables

| Contournement conseillé | Qui | Exigence pour Zümm | Verdict | Couche |
|---|---|---|:--:|---|
| « Vérifiez sur le terrain au printemps la culture réellement semée » (*ground truthing*) | BeeGIS | Toute donnée environnementale porte son **millésime** et peut être marquée « à confirmer », ce qui engendre une tâche de vérification | ❌ | DB + back + front |
| « Servez-vous de l'historique de rotation sur 3 à 5 ans » | BeeGIS | **Comparaison saison contre saison** | ✅ | `GET /api/saisons` (SPRINT-27) : années civiles, rendement par ruche productive, ventilation par produit. Tous les autres agrégats du produit glissent — douze mois qui reculent chaque jour ne permettent pas de dire « 2026 a mieux donné que 2025 » |
| « Vérifiez la couverture réseau du site **avant** d'installer » | Onibi | Champ **couverture réseau** sur le `Site`, au même titre que l'exposition | ✅ | `site.couverture_reseau` (`V24`), quatre niveaux, index partiel sur ce qui manque. NULLE = inconnue : un défaut à « correcte » ferait partir un apiculteur sans emport sur un rucher en zone blanche |
| « Vérifiez le niveau de charge des capteurs via le tableau de bord » | BeeLog Digital, Onibi | `TypeIndicateur.ALIMENTATION` + seuil d'alerte | ✅ | `V26`, une valeur d'énumération et un seuil. Inventer une table « état des capteurs » aurait créé un second mécanisme d'alerte à maintenir en parallèle du premier, pour dire la même chose. Un test d'intégration a d'ailleurs montré que `alerte` portait **sa propre** liste d'indicateurs : les deux contraintes disaient la même chose à deux endroits |
| « Nettoyez les optiques, grattez la propolis sur les glissières » | Onibi | **Plan de maintenance du matériel** : tâches récurrentes attachées à un équipement, pas à une ruche | ✅ | `tache.materiel_id` (`V27`) et `RegleMaintenanceMateriel`, dans le moteur du SPRINT-22 — même clé d'idempotence, même anti-doublon. La clé **porte l'échéance** : une fois l'entretien fait, la date recule et la règle repropose au terme suivant, sans jamais dupliquer celle du terme courant |
| « Configurez une seule ruche *test* avant de basculer l'exploitation » | BeeLog Digital | **Jeu de démonstration réversible** | ✅ | `POST`/`DELETE /api/demonstration` (`JeuDemonstrationService`, `V25`). Le mot qui compte est **réversible** : la table `jeu_demonstration` note ce que le chargement a créé, et la purge ne supprime que cela — jamais une donnée saisie par l'utilisateur, même si elle porte le même nom. Éteint par défaut, réservé au rôle `admin` |
| « N'activez les modules avancés qu'au moment où vous en avez besoin » | APIGO, HiveTracks | **Interface progressive** : les modules avancés ne s'imposent pas à l'apiculteur de trois ruches | ✅ | `terrain/interface.ts` (SPRINT-25) : l'étendue « essentielle » retire cinq écrans de la navigation — capteurs, lots, essaims, reines, audit. **Masquer n'est pas interdire** : les rôles décident de ce qui est permis, ce réglage de ce qui est montré ; un lien direct continue de fonctionner, et `interface.test.ts` vérifie que les deux ne se rejoignent pas |
| « Ajoutez l'application à l'écran d'accueil avant de partir au rucher » | APIGO | **Invite d'installation contextuelle** | ✅ | `pwa.ts` capte `beforeinstallprompt` et la rejoue depuis l'écran « Hors ligne », c'est-à-dire au moment où elle a un sens (SPRINT-24). Safari ne l'émet jamais : l'écran explique alors l'ajout par le menu de partage, faute de quoi la moitié du parc verrait un écran qui ne propose rien |
| « Utilisez le champ Notes libre pour préciser l'action à mener » | HiveTracks | **Action attachée à l'observation** : une case cochée engendre la tâche correspondante. Le champ libre ne doit pas être la soupape du modèle | ✅ | DB + back + front |
| « Désactivez les notifications e-mail pour éviter la fuite d'adresses » | BeeKeepPal | **Réglage par utilisateur** | ✅ | `agent.notifications_email` (`V25`), lu par `NotificationAlerteService`. Le réglage global demeure et **s'ajoute** : les deux doivent être vrais. Vrai par défaut — une alerte s'ouvre parce que quelque chose ne va pas, et un défaut à faux ferait taire au premier déploiement ce que le produit promet de signaler. Un corps qui omet le champ ne le réécrit pas |
| « Créez votre compte depuis la version Web, l'application mobile est instable » | BeeKeepPal | **Parité stricte web / mobile** — un seul code, une seule PWA | ✅ | — |
| « Activez impérativement la sauvegarde iCloud, sinon les données sont perdues » | HiveBook | **Sauvegarde restaurée pour de vrai** : `infra/sauvegarde.sh`, `restauration.sh` et `tester-restauration.sh`, dont le scénario détruit la donnée avant de la restaurer | ✅ | — |
| « Désactivez le mode sombre du téléphone » | BeeKeepPal | Thème clair / sombre / système, persisté et **testé** (`theme/theme.test.tsx`, 7 tests) | ✅ | — |
| « Désactivez votre VPN ou passez en 4G si la page ne s'affiche pas » | BeeGIS | Aucune dépendance bloquante à un domaine tiers : repli SVG sans tuiles, météo simulée hors ligne | ✅ | — |
| « Consultez en parallèle le tableau de bord de vos balances connectées » | BuzzWise, HiveBook | Capteurs intégrés au même produit (`Mesure`, TimescaleDB, `POST /api/mesures`) — pas de second écran à ouvrir | ✅ | — |
| « Contactez les agriculteurs voisins pour connaître leur assolement » | BeeGIS | Hors périmètre : Zümm ne gère pas d'annuaire de tiers | ⛔ | — |
| « Restez sous le plafond gratuit, ou évaluez le retour sur investissement » | HiveSense, BeeKeepPal, HiveTracks, HiveBook | Sans objet : Zümm est **auto-hébergé, sans plafond de ruches ni d'utilisateurs** | ⛔ | — |

### Ce que ce dépouillement change

Cinq conseils sur les douze rubriques décrivent un produit que Zümm est **déjà** :
sauvegarde réellement restaurée, thème sombre testé, parité web/mobile, replis
hors ligne, capteurs intégrés. Ce sont des arguments de soutenance, à condition
de les dire dans ces termes — « le contournement que ce concurrent conseille à
ses utilisateurs n'a pas lieu d'être ici, et voici le fichier qui le prouve ».

Deux exigences méritent d'être promues au rang d'écart principal, parce qu'elles
sont conseillées par trois outils ou plus et qu'aucune ne demande de migration :

1. **Emporter un rucher hors ligne.** C'est la sortie du dilemme posé au §11 :
   ni cache aveugle de `/api` (une mesure de capteur périmée trompe
   l'apiculteur), ni rien du tout. Un préchargement **déclenché par
   l'utilisateur**, borné à un rucher, avec la date de fraîcheur affichée, donne
   la consultation hors ligne sans jamais faire passer une donnée périmée pour
   fraîche. Trois concurrents font aujourd'hui exécuter cette manœuvre à la
   main ; c'est le signe qu'elle est juste, et qu'elle devrait être automatique.
2. **Le brouillon de visite reprenable.** Trois outils conseillent la même
   chose : noter au rucher, saisir au retour. La file de mutations hors ligne
   (`offline/file.ts`) résout le trajet *terrain → serveur* ; elle ne résout pas
   le trajet *téléphone → ordinateur*. C'est la même intention que la fiche
   papier imprimable du §10, en version numérique.

> Ces deux exigences sont **exclusivement front** (plus quelques en-têtes de
> fraîcheur côté back pour la première). Elles figurent donc parmi les chantiers
> parallélisables de la §12, sans attendre la migration `V19`.

---

## 14. Note de révision — 25/08/2026

Le document a été **rejoué ligne à ligne contre le dépôt** une semaine après sa
rédaction. Le principe est le même qu'au premier passage : rien n'est reconduit
sur parole, chaque verdict est reconstitué depuis le code.

### Ce qui a bougé

Deux lots ont été livrés entre les deux relevés.

| Livraison | Effet sur ce document |
|---|---|
| **Prévisions météo** (`PrevisionJour`, `MeteoReponse.previsions`, `GET /api/meteo?siteId=&jours=`, affichage dans `CapteursVue`) | Aucun : le §7 décrivait déjà cette tranche, alors en cours. « Tâches programmées selon la météo » et « corrélation météo ↔ production » restent ❌ — la prévision est livrée, le déclenchement et le figeage sur la visite non |
| **SPRINT-19** : accueil public, pages d'information, navigation filtrée par rôle, refus expliqué, compte et matrice des permissions | Quatre lignes du §7 (dont deux nouvelles), une du §1, une du §13 |

Trois verdicts changent, tous vers le haut sauf un :

- §7 — deux acquis nouveaux : **lisibilité des droits** (`ROLES_ONGLET`,
  `InterditVue`, `PermissionsVue`) et **compte en libre-service**
  (`ConnexionVue`, `CompteVue`) ;
- §7 — un manque nouveau, qui n'existait pas tant qu'il n'y avait pas de parcours
  de compte : la **réinitialisation de mot de passe** n'aboutit nulle part, faute
  de serveur d'envoi dans les realms Keycloak ;
- §13 — « interface progressive » passe de ❌ à 🟡 : l'interface s'adapte au
  **rôle**, pas encore au besoin, et le gardiennage s'arrête à l'onglet.

Deux formulations étaient devenues fausses et ont été corrigées :

- le §3 affirmait que `grep -ri varroa` ne renvoyait **rien**. Il renvoie
  aujourd'hui deux lignes, toutes deux en **texte libre** dans
  `infra/seed-demo.sql`. Le verdict ❌ ne bouge pas — il se renforce même : le jeu
  de démonstration doit écrire « présence de varroa » dans une `constatation`
  faute de champ pour le dire. **Caduc depuis le SPRINT-20 — voir §15** : le
  champ existe, et le jeu de démonstration peuple désormais les trois tables
  sanitaires ;
- le §1 opposait les filtres par vue à l'absence de recherche transverse sans
  mentionner la **palette `Ctrl/⌘ + K`**. Elle existe, mais cherche des *écrans*,
  pas des *ruches* : le verdict ❌ tient, la preuve était incomplète.

### Ce qui a été revérifié et n'a pas bougé

Relevé le 25/08/2026, dans cet ordre :

| Fait | Valeur | Commande |
|---|---|---|
| Entités JPA | **19** — et non 26 : `grep -rl "@Entity"` matche aussi `@EntityGraph`, présent dans sept repositories. C'est le même piège que celui déjà signalé pour `@RestControllerAdvice` | `grep -rl "^@Entity" backend/src/main/java/com/zumm \| wc -l` |
| Contrôleurs REST | 24 (25 `@RestController` **moins** `GestionnaireExceptions`, qui est un `@RestControllerAdvice`) | `grep -rl "@RestController" …` |
| Migrations Flyway | 18, dernière `V18__code_invitation_sprint18.sql` — le §12 vise donc bien **`V19`** | `ls backend/src/main/resources/db/migration/` |
| Entités exportables | 2 sur 19 (`/api/export/visites`, `/api/export/ruches`) | `ExportController` |
| `Photo` | toujours `optional = false` sur la visite | `domain/Photo.java:37` |
| `rayonsKm` | toujours figé à `[1, 2, 3]` | `vues/CarteFond.tsx:84` |
| Cache d'API | toujours refusé | `navigateFallbackDenylist: [/^\/api/, …]` |
| Voix, Bluetooth, NFC | aucun `SpeechRecognition`, `MediaRecorder`, `getUserMedia`, `requestDevice`, `NDEFReader` dans `frontend/src` | grep |
| Invite d'installation PWA | `beforeinstallprompt` toujours absent | grep |
| Licence | aucun fichier `LICENSE` | `ls` |
| Thème clair/sombre | 7 tests | `theme/theme.test.tsx` |
| Notification | toujours `setTo` avec **une seule** adresse | `NotificationAlerteService` |
| Énumérations | `EtatRuche`, `RaisonVisite`, `TypeIndicateur`, `EtatSante`, `EffectifQualitatif`, `StatutPlanning`, `RoleAgent` inchangées | `domain/` |
| `Site`, `Ruche`, `Visite` | aucun champ nouveau : ni adresse, ni couleur, ni type de ruche en référentiel, ni météo figée | `domain/` |

Aucun des sept écarts principaux du §11 n'a été entamé, et l'ordre de passage du
§12 reste valable tel quel : la migration `V19` est toujours le premier verrou.

> **Comment rejouer cette révision.** Les commandes de la colonne de droite
> suffisent : elles ne demandent ni Docker, ni compilation. Les seuls chiffres qui
> exigent un `./mvnw -B verify` ou un `npm test` — nombre de tests, couverture —
> ne figurent volontairement pas dans ce document ; ils vivent dans `CLAUDE.md`
> et dans le `README`, où ils se mesurent (voir l'avertissement de `CLAUDE.md`
> sur les chiffres recopiés de fiche en fiche).


---

## 15. Note de révision — 29/08/2026, après le SPRINT-20

Le SPRINT-20 a livré la migration `V19` et tout ce qui va avec, du domaine à
l'écran. **Les corrections ont été reportées dans les §§1 à 13 le même jour** :
les verdicts qu'on y lit sont ceux du 29/08/2026. Cette note reste comme
**trace** — elle dit ce que le document affirmait encore la veille, et surtout ce
qu'il continue d'affirmer malgré la livraison. La seconde liste est la plus utile
des deux, et c'est celle qu'on lit mal après un sprint réussi.

### Ce qui n'est plus vrai

| Ce que les §§1 à 13 affirmaient jusqu'au 29/08/2026 | Ce qu'ils disent depuis |
|---|---|
| « Traitements, nourrissements et varroa ne sont qu'une valeur de `RaisonVisite` » (§3, §11 écart 1) | **Faux.** Trois tables de plein droit : `traitement` (produit, substance active, dose **et son unité**, cible, période, délai de carence, fin de carence générée et indexée), `nourrissement` (type, quantité, unité, motif), `comptage_varroa` (méthode et comptages **bruts**) |
| « Le dépôt ne nomme le varroa que dans la prose de `seed-demo.sql` » (§3) | **Faux.** Il le nomme dans une table, une route (`/api/varroa`), un service qui calcule son taux et son verdict, et un écran |
| « Les observations d'inspection vivent en texte libre » (§3, §11 écart 3) | **Faux.** Onze colonnes d'observation sur `visite` — couvain, motif de ponte, cellules royales et leur cause, cadres, tempérament — plus une table fille `observation_pathologie` pour les maladies **nommées**. Le texte libre subsiste, il n'est plus la seule trace |
| « Météo figée sur la visite : la prévision est livrée, le figeage non » (§11, écarts à faible coût) | **Faux.** Quatre colonnes figées sur `visite`, avec leur **source** — `open-meteo`, `simulation` ou `saisie` : une estimation ne se lit pas comme une mesure |
| « Référentiel de types de ruche, couleur, cause de clôture : trois colonnes et une énumération » (§11, écarts à faible coût) | **Faux.** Quatre colonnes sur `ruche`, avec un `CHECK` qui refuse une cause de clôture sur une ruche encore active |
| « Aucun des sept écarts principaux du §11 n'a été entamé » (§14) | **Faux.** Les écarts **1** et **3** sont livrés ; ils étaient le socle des autres |
| « La migration `V19` est le premier verrou » (§12, §14) | **Levé.** Elle est écrite, appliquée et testée sous le rôle applicatif `zumm_app` |

### Ce qui reste vrai, et qu'il ne faut pas croire réglé

C'est la partie que la livraison rend la plus facile à mal lire : trois manques
ressemblent désormais à des acquis.

1. **Le délai de carence n'interdit rien.** Il est consigné, calculé, affiché,
   et la liste des ruches sous carence se lit d'un écran — mais aucune règle ne
   refuse d'enregistrer une récolte sur l'une d'elles. Le registre est opposable
   en **lecture**, pas contraignant en **écriture**. C'est le premier écart de
   cette liste, et il ne coûte plus qu'une vérification : la donnée existe.
2. **Les interventions groupées** (§11, écart 2) sont intactes. Toutes les
   mutations restent unitaires : sur un rucher de quarante ruches, un traitement
   se saisit quarante fois. Le registre est juste, il n'est pas encore utilisable
   à l'échelle qu'il prétend viser — et c'est désormais le manque le plus coûteux
   du document.
3. **Les tâches engendrées par un événement** (§11, écart 4) n'ont pas bougé.
   `TacheService` enregistre, il ne programme toujours rien. Le retrait d'un
   traitement après sa carence reste le cas d'école, à ceci près qu'il est
   maintenant *possible* : la date de fin est en base et indexée.

Inchangés également, et sans lien avec ce sprint : la couche d'occupation du sol
et le calendrier de floraison (écart 5), la saisie vocale et la fiche imprimable
(écart 6), la généalogie des reines (écart 7), et l'arbitrage sur la consultation
hors ligne (§11, §13).

### Ce qui a été relevé, et comment

| Fait | Valeur | Commande |
|---|---|---|
| Entités JPA | **23** — et non 30 : `grep -rl "@Entity"` matche aussi `@EntityGraph` | `grep -rl "^@Entity" backend/src/main/java/com/zumm \| wc -l` |
| Contrôleurs REST | **27** (28 `@RestController` **moins** `GestionnaireExceptions`, qui est un `@RestControllerAdvice`) | `grep -rl "@RestController" …` |
| Migrations Flyway | **19**, dernière `V19__sanitaire_observations_sprint20.sql` | `ls backend/src/main/resources/db/migration/` |
| Le varroa dans le dépôt | table, entité, service, route, écran et libellés dans les trois locales | `grep -ril varroa backend/src frontend/src` |
| Entités exportables | **2 sur 23** — l'écart s'est creusé, mécaniquement, avec les quatre entités ajoutées | `ExportController` |
| `Photo` | toujours `optional = false` sur la visite | `domain/Photo.java` |
| `rayonsKm` | toujours figé à `[1, 2, 3]` | `vues/CarteFond.tsx` |
| Cache d'API | toujours refusé | `navigateFallbackDenylist: [/^\/api/, …]` |
| Voix, Bluetooth, NFC | toujours aucune API correspondante dans `frontend/src` | grep |
| Licence | toujours aucun fichier `LICENSE` | `ls` |

> L'ordre de passage du §12 tient toujours pour ce qui reste, à une correction
> près : il plaçait la migration en tête parce qu'elle bloquait tout le reste.
> Ce verrou est levé, et les trois manques ci-dessus sont désormais
> **parallélisables** — l'interdiction de récolte est back pur, les interventions
> groupées touchent les deux couches, les tâches engendrées sont back puis front.

---

## 16. Note de révision — 31/08/2026, après le SPRINT-21

Le SPRINT-21 a livré la migration `V20` et le §1 avec elle : c'était la dernière
section où Zümm restait en retard sur ce que **douze catalogues sur douze**
demandent dès la création d'un rucher. Les corrections sont reportées dans les
§§1 à 13 le même jour ; cette note dit ce que la livraison a réglé, ce qu'elle a
**délibérément laissé de côté**, et ce qui reste dû.

### Ce qui n'est plus vrai

| Ce que le §1 affirmait jusqu'au 31/08/2026 | Ce qu'il dit depuis |
|---|---|
| « `Site` ne porte que des coordonnées — pas de rue/CP/ville/pays » | **Faux.** Quatre colonnes d'adresse (V20), et un masque qui les retire **plus fort** que les coordonnées : une rue situe un rucher au portail |
| « Aucune vue d'historique » d'emplacement | **Faux.** `emplacement_site`, une ligne par période, index unique partiel sur l'emplacement courant, et un `POST /demenagement` distinct du `PUT` qui corrige |
| « Ni la ruche fille, ni le nombre de cadres transférés ne sont saisis » | **Faux.** `division` porte la filiation, la méthode et ce que la mère a cédé |
| « Capture d'essaim : aucune notion » | **Faux.** `capture_essaim`, avec logement différé — la capture existe avant d'avoir une ruche |
| « `Photo` est liée à une visite uniquement » | **Faux.** Cinq cibles, et un `CHECK` qui en impose exactement une |
| « La palette cherche parmi les écrans, jamais parmi les objets métier » | **Faux, à moitié.** `/api/recherche` couvre sept familles ; mais les routes restent plates, donc on ouvre l'écran porteur, pas la fiche |
| « `CalendrierService` ne produit pas de flux `.ics` » | **Faux.** `AgendaIcsService` produit un `VCALENDAR` conforme — en téléchargement, jamais en abonnement |

### Ce qui a été écarté, et pourquoi ce n'est pas un oubli

1. **`capture_essaim` ne porte aucune coordonnée.** La tentation était forte —
   on capture un essaim quelque part. Mais une colonne de position est une
   surface de fuite de plus à filtrer (invariant `PolitiquePositions`) pour une
   donnée dont personne ne fait rien : le lieu d'une capture ne se cartographie
   pas, il se raconte. Un champ `lieu` libre le dit mieux et ne se trilatère pas.
2. **`ressource_florale` dit QUOI, jamais QUAND.** Les dates de floraison sont le
   calendrier de miellées de l'écart 5 du §11, et celui-là suppose d'abord de
   trancher un référentiel d'occupation du sol portable hors de France. Une
   migration n'avait pas à préempter cette décision produit.
3. **Le `.ics` est un téléchargement, pas un abonnement.** Un abonnement iCal
   suppose une URL appelée **sans session** par le client de calendrier, donc un
   jeton permanent : un secret de plus, non révocable en pratique, recopié dans
   les réglages de trois appareils et transmis en clair à chaque intermédiaire.
   Le téléchargement authentifié donne l'usage réel — « mes visites de la semaine
   dans mon agenda » — sans créer ce secret.
4. **La planification du transport de transhumance n'est pas là.** L'historique
   dit où le rucher a été, pas comment il y va. Véhicule, créneau, ordre de
   chargement : c'est un métier à part, et il a sa propre ligne ❌ au §1.

### Ce qui reste dû sur le §1 lui-même

- **La recherche n'ouvre pas la fiche.** Choisir « Ruche 42 » dans la palette
  ouvre l'écran des ruches, pas la ruche 42. Y remédier demande des routes
  paramétrées, c'est-à-dire le routeur que l'ADR du SPRINT-11 a écarté : c'est
  une décision à rouvrir, pas un correctif.
- **Les miellées** (dates, pas ressources) et la **planification de transport**
  restent ❌, l'une renvoyée à l'écart 5, l'autre nouvellement nommée.

### Ce qui a été relevé, et comment

| Fait | Valeur | Commande |
|---|---|---|
| Entités JPA | **27** (23 + `RessourceFlorale`, `EmplacementSite`, `Division`, `CaptureEssaim`) | `grep -rl "^@Entity" backend/src/main/java/com/zumm \| wc -l` |
| Contrôleurs REST | **31** (32 `@RestController` **moins** `GestionnaireExceptions`) | `grep -rl "@RestController" …` |
| Migrations Flyway | **20**, dernière `V20__terrain_ruchers_sprint21.sql` | `ls backend/src/main/resources/db/migration/` |
| Écrans de la console | **20** — `essaims` rejoint la famille `cheptel` | `routage/routes.ts`, vérifié par `routage.test.ts` |
| Tests unitaires back | **122** (dont 14 pour le moteur de règles, la carence et la corrélation) | `./mvnw -B test` |
| Tests d'intégration | **158** (dont 6 pour `ReglesEtCarenceIT`), `Skipped: 0` | `./mvnw -B verify` |
| Couverture JaCoCo | **81,3 %** instructions · **62,3 %** branches · 82,2 % lignes | `target/site/jacoco/` |
| Tests front | **293** | `npm test` |
| Contrat OpenAPI | **85 chemins**, 87 schémas, dont les 9 types des deux sprints | `api/parite.ts` |

> **Vérifié le 31/08/2026, Docker démarré** : la migration `V20` s'applique, les
> 146 tests d'intégration passent sans un seul ignoré, les deux planchers de
> couverture tiennent, et `openapi.json` / `contrat.ts` sont régénérés — les cinq
> types du sprint sont désormais sous la garde d'`api/parite.ts`, qui casse `tsc`
> à la moindre dérive de nom ou de type.
>
> Un défaut avait été corrigé juste avant, à la relecture : Hibernate ordonne ses
> écritures par type — tous les `INSERT` avant les `UPDATE` —, si bien que le
> nouvel emplacement serait parti en base **avant** la clôture de l'ancien, et
> l'index unique partiel aurait refusé les deux ; tout déménagement aurait échoué
> en 500. Un `flush()` explicite dans `SiteService.demenager` remet les deux
> écritures dans l'ordre du métier, et le test d'intégration `demenagement` le
> vérifie désormais — c'est lui qui aurait attrapé la faute si la relecture
> l'avait manquée.

---

## 17. Note de révision — 31/08/2026, second lot du SPRINT-21

La `V20` avait comblé cinq des huit manques du §1 et laissé quatre lignes en
attente : trois 🟡 et un ❌. La `V21` les ferme, et **le §1 est désormais
intégralement couvert — seize lignes sur seize**. C'est la première section du
document dans ce cas.

### Ce que la V21 ajoute

| Ligne | Ce qui manquait | Ce qui la comble |
|---|---|---|
| Miellées | La table `ressource_florale` disait QUOI, jamais QUAND | `mois_debut` / `mois_fin`, en **mois** et non en dates — une floraison revient chaque année. La fenêtre peut enjamber l'année, et `RessourceFlorale.enFloraison` la lit modulo douze |
| Planification du transport | L'historique constatait un déplacement sans jamais l'organiser | Table `transport` : véhicule, créneau, capacité, destination. `POST /realiser` **appelle** `SiteService.demenager` au lieu de le réimplémenter |
| Ouvrir la fiche depuis la recherche | Les routes plates n'acceptaient pas d'objet | `/ruches?id=42`. Pas de routeur — une route plate accepte un paramètre — et le surlignage vit dans `Table`, donc dans les vingt écrans à la fois |
| Abonnement iCal | Refusé au premier lot : « un jeton permanent est un secret non révocable » | Un jeton **révocable et expirant**, stocké en empreinte seule. L'objection est levée, pas contournée |

### L'abonnement iCal, et ce qu'il coûte

C'est la décision la plus lourde du lot, et elle mérite d'être lisible : **une
route de l'API répond désormais sans authentification**. C'est la définition même
d'un abonnement — Google Agenda appelle l'URL toutes les quelques heures, sans
session ni en-tête —, mais cela ouvre une surface qui n'existait pas.

Ce qui la borne, et qui doit rester vrai :

1. **256 bits d'aléa**, tirés d'un `SecureRandom` : une URL ne se devine pas.
2. **Rien en clair en base.** Seul le SHA-256 est stocké ; le jeton n'existe que
   dans la réponse à la création, montrée une fois. Une fuite de la base ne rend
   aucune URL utilisable.
3. **Expiration obligatoire**, bornée à un an par le DTO. Il n'y a pas d'option
   « sans expiration », et c'est le point.
4. **Révocation** d'un clic, la ligne survivant à la coupure pour la documenter.
5. **Usage visible** : `derniereUtilisation` s'affiche, si bien qu'un jeton
   oublié qui sert encore se remarque.
6. **Portée d'un agent**, jamais du parc : le service pose `Portee.agent(...)`
   avant de lire, et la requête filtre en plus sur l'agent — la RLS seule ne
   suffirait pas, puisqu'en test l'application se connecte avec le rôle
   propriétaire de la base.
7. **Aucune position dans le fichier**, comme pour le téléchargement.

Ce qui n'y est **pas**, et qu'il faut savoir : aucune limitation de débit sur
cette route. Le dépôt n'en a nulle part, et en ajouter une pour ce seul chemin
aurait été un dispositif isolé, donc mal éprouvé. Le coût d'un balayage reste
borné par l'entropie du jeton, mais c'est une brique à prévoir si l'application
s'ouvre largement.

### Une table hors du modèle multi-tenant, et pourquoi

`abonnement_calendrier` est la **seule table métier sans `tenant_id` discriminant
ni politique RLS**. Elle est lue *avant* que le tenant soit connu — c'est sa
fonction : résoudre un jeton opaque en couple (exploitation, agent). Le précédent
existe et le justifie : `SPRING_SESSION` (V17) est hors périmètre pour
exactement la même raison, une session étant créée avant toute connaissance du
tenant.

La contrepartie est que le cloisonnement y devient **applicatif**.
`AbonnementCalendrierService` est le seul code qui touche cette table, et chacune
de ses requêtes cite le tenant explicitement — la signature du repository l'exige.
Un oubli y serait une fuite entre exploitations : c'est le point à auditer en
priorité si cette table venait à être lue ailleurs.

---

## 18. Note de révision — 01/09/2026, lot A du plan de couverture

Premier lot du [plan de couverture](PLAN-COUVERTURE-ECARTS.md), et le seul qui
n'ajoute presque **aucune donnée** : le SPRINT-20 avait livré les colonnes
d'observation, personne n'en tirait de conclusion. Onze lignes passent à ✅ —
le compteur va de **66 à 77 sur 153**.

### Ce que la V22 ajoute, et ce qu'elle refuse d'ajouter

| Ajouté | Refusé, et pourquoi |
|---|---|
| Quatre colonnes sur `tache` : priorité, catégorie, origine, **clé de déclenchement** | Aucune **table de scores**. L'indice de santé et le risque d'essaimage se calculent à chaque lecture — les stocker créerait la même dette que pour le taux de varroa : une valeur figée qui ne suit plus la formule quand celle-ci change |
| Deux colonnes sur `recolte` : forçage de carence et son motif | Aucune **table de règles**. Les règles sont du code : elles lisent des colonnes, comparent des seuils et rendent une tâche. Une table paramétrable serait un moteur d'expression à écrire, tester et sécuriser, pour un besoin que personne n'a exprimé |
| Une action d'audit de plus : `forcage` | — |

### Les cinq règles, et ce qu'elles ont en commun

`RegleRetraitTraitement`, `RegleControlePonte`, `RegleVarroaATraiter`,
`RegleReservesBasses`, `RegleMeteoDefavorable`. Chacune **propose** ; c'est
`MoteurRegles` qui enregistre, et lui seul touche le dépôt. Trois propriétés en
découlent :

1. **une règle se teste sans base** — elle rend une liste d'objets inertes ;
2. **l'anti-doublon vit à un seul endroit** — la clé de déclenchement et son
   index unique partiel. Une règle qui s'enregistrerait elle-même devrait
   réimplémenter cette garde, et l'oublierait un jour ;
3. **le moteur est idempotent** : le passer deux fois dans la journée ne produit
   rien la seconde fois. C'est ce qui permettra de le brancher un jour sur un
   planificateur — pour l'instant il est déclenché à la main, parce qu'une
   exploitation qui découvre un matin quinze tâches non demandées cesse de lire
   sa liste.

### La carence opposable, et sa porte de sortie

C'est le seul endroit du produit où une donnée consultative devient un **refus
d'écriture**, et il a fallu le border :

- **409, pas 400.** La requête est valide ; c'est l'état de la ruche qui s'y
  oppose, et l'appelant n'a rien à corriger dans son corps de requête.
- **Le message dit jusqu'à quand.** Un refus qui ne dit pas « et après ? » se
  contourne.
- **On peut passer outre, mais on s'explique**, et la décision part au journal
  d'audit sous une action distincte. Sans cette porte, l'apiculteur cesserait
  d'enregistrer le *traitement* — et le registre deviendrait faux là où il
  n'était qu'incomplet. C'est le piège que le plan de couverture nommait avant
  d'écrire la première ligne.

### Ce que le lot A ne prétend pas faire

L'indice de santé **n'est pas un diagnostic**. Il porte le nombre
d'observations qui l'ont nourri, et vaut zéro composante quand rien n'a été vu :
l'écran doit alors afficher « non évalué », jamais une jauge — une jauge sur du
vide fait passer l'ignorance pour un avis.

La corrélation météo **n'est pas une cause**, et le service refuse d'interpréter
en dessous de douze paires. Deux mois chauds qui coïncident avec une miellée
d'acacia ne prouvent pas que la chaleur produit le miel.

---

## 19. Note de révision — 02/09/2026, lot B du plan de couverture

Deuxième lot du [plan de couverture](PLAN-COUVERTURE-ECARTS.md). Là où le lot A
tirait des conclusions de données déjà là, celui-ci change **l'échelle du
geste** : jusqu'ici tout se saisissait ruche par ruche et se lisait ruche par
ruche, alors que le travail réel se fait au rucher. Cinq lignes passent à ✅ —
le compteur va de **77 à 82 sur 153**.

### Ce que la V23 ajoute, et ce qu'elle refuse d'ajouter

| Ajouté | Refusé, et pourquoi |
|---|---|
| `priorite` sur `ruche` et sur `site`, trois niveaux, avec un index partiel `WHERE priorite <> 'normale'` | Aucune **table d'agrégats par rucher**. La synthèse se recalcule à chaque lecture : la stocker créerait une valeur figée qui ment dès qu'une visite est saisie, et il faudrait l'invalider depuis six services |
| — | Aucune **table de lots d'opération**. Un lot n'est pas une entité du métier : c'est une manière de saisir. Le tracer donnerait un identifiant de plus à afficher, et une jointure de plus sur chaque registre |

### Le lot, et pourquoi une transaction par ruche

`OperationsLotService` est le seul point d'entrée des trois opérations
groupées, et il tient en une décision : **`REQUIRES_NEW` par ruche**. Trois
propriétés en découlent, et ce sont elles qui rendent le lot utilisable au
rucher :

1. **un refus n'annule pas les autres.** Trente-sept traitements ont bien eu
   lieu ; les défaire en base ne les défait pas dans le rucher, et obligerait à
   tout ressaisir ;
2. **le rapport nomme les refus**, avec le motif métier du service — « sous
   carence jusqu'au 14 septembre », pas « échec ». Sans le motif, le rejeu
   porte sur les quarante ruches au lieu des trois ;
3. **la cible est cumulative et dédoublonnée** : scanner trente ruches puis
   demander « tout le rucher » donne quarante lignes, jamais soixante-dix.

Une ruche clôturée est écartée **en silence** quand elle vient d'un rucher
entier — faire échouer des lignes pour des colonies mortes il y a six mois
transformerait chaque rapport en liste de bruit — mais produit un **échec
visible** si elle a été nommée : c'est alors une erreur de l'appelant, et la
taire lui laisserait croire que l'acte a eu lieu.

### Le niveau rucher, et ce qu'il refuse de moyenner

`SyntheseRucherService` ferme le maillon faible de la vision à trois niveaux.
Deux choix y sont plus importants que le reste :

- **la santé moyenne ne porte que sur les colonies réellement évaluées**, et un
  rucher jamais visité rend `null`. Compter une colonie non vue comme zéro
  ferait chuter un rucher qu'on n'a pas encore visité ; la compter comme cent le
  ferait mentir dans l'autre sens. Le nombre de colonies évaluées accompagne
  donc la moyenne, et l'écran affiche « non évalué » plutôt qu'une jauge ;
- **le risque d'essaimage est le maximum, pas la moyenne.** Une colonie prête à
  essaimer au milieu de trente colonies calmes est exactement ce qu'il faut
  voir ; la moyenne la dilue.

L'ordre de la liste est celui du travail — carence, puis alertes, puis taille —
et non l'ordre alphabétique, qui obligerait à chercher.

### Ce que le lot B ne prétend pas faire

La **comparaison d'emplacements ne classe pas**. Elle aligne des critères
comparables et laisse l'apiculteur trancher : une note unique — « ce rucher vaut
78 sur 100 » — mélangerait des kilos, des espèces florales, une altitude et une
densité de voisinage, c'est-à-dire des grandeurs qui ne s'additionnent pas. Elle
aurait l'autorité d'un chiffre sans en avoir la matière. Le rendement y est
**par ruche** et non total, sans quoi un rucher de vingt colonies gagnerait
toujours contre un rucher de cinq ; et un rucher sans colonie n'a pas un
rendement de zéro, il n'en a pas.

La comparaison ne rend **aucune coordonnée**. C'est précisément l'écran où l'on
serait tenté d'en donner — on compare des emplacements — et elle n'en a pas
besoin pour comparer.

La **charge d'équipe ne note personne**. Les chiffres servent à répartir ; dix
tâches en retard, c'est le plus souvent trois jours de pluie. La phrase est dans
l'interface, pas seulement dans le code, parce qu'un tableau qui s'y prêterait
finirait par servir à cela.

### Ce qui reste dû sur les lignes touchées

La **coordination d'équipes** reste 🟡 : la charge par agent est là, la
logistique et la chaîne d'approvisionnement — le terrain de HiveOS — ne le sont
pas. Et le **scan en masse** au sens propre (code-barres ou QR par ruche) reste
❌ : le lot répond à « appliquer le même acte à quarante ruches », pas à
« identifier une ruche en la scannant ».

---

## 20. Note de révision — 04/09/2026, lot C du plan de couverture

Troisième lot du [plan de couverture](PLAN-COUVERTURE-ECARTS.md), et le premier
qui **rouvre une décision consignée** plutôt que d'ajouter des tables. Huit
lignes passent à ✅ et une à 🟡 — le compteur va de **82 à 90 sur 153**.

C'est aussi le lot le plus directement dicté par le §13 : sur ses neuf lignes,
**six sont des contournements que des éditeurs concurrents enseignent à leurs
propres utilisateurs** — ouvrir les fiches avant la zone blanche, noter au stylo
pour saisir au retour, emporter une batterie, vérifier la couverture réseau,
ajouter l'application à l'écran d'accueil. Un contournement enseigné par trois
éditeurs n'est pas une bonne pratique : c'est une fonction manquante.

### La décision rouverte, et ce qu'elle est devenue

Depuis le SPRINT-13, `vite.config.ts` refusait tout cache de `/api` avec un
argument juste : *« une mesure de capteur périmée ou une position de rucher
servie depuis le disque induirait l'apiculteur en erreur »*. L'erreur n'était pas
l'argument, c'était sa **portée** : il vaut pour les mesures, pas pour la liste
des ruches d'un rucher, qui ne change pas dans la journée.

[ADR-012](../roadmap/operationnel/06_decisions/ADR-012-hors-ligne-selectif.md)
précise donc la décision sans la contredire — le `navigateFallbackDenylist` n'a
pas bougé d'une ligne, et **rien ne passe par le service worker**. L'emport est
une ressource explicite :

| Propriété | Ce qu'elle empêche |
|---|---|
| **Déclenché** — un bouton, jamais un cache | Consulter sans le savoir une donnée qu'on n'a pas demandé d'emporter |
| **Borné** — un rucher, pas le parc | Un emport opaque, impossible à expliquer ou à purger |
| **Daté par le serveur** — affiché à chaque lecture | Qu'une donnée du disque passe pour fraîche : c'était l'objection réelle |
| **Périssable** — quatorze jours, puis plus servi | Qu'un instantané oublié traverse la saison |

Trois choses restent **dehors**, et ce sont exactement celles que le commentaire
de 2026 visait : les **mesures de capteurs** (une courbe de poids figée trompe là
où une liste de ruches ne trompe pas), la **météo** (une prévision de trois jours
est du bruit) et les **positions exactes** — l'instantané passe par
`PolitiquePositions` comme toute autre sortie, parce qu'emporter un rucher serait
le moyen idéal d'obtenir en clair, sur un appareil sans session, ce que l'API
dégrade en ligne.

### Le bogue silencieux que ce lot corrige

Il n'était dans aucun backlog, et c'est le plus coûteux des trois : au rejeu de
la file hors ligne, **tout 4xx était traité comme « traité »** et la mutation
disparaissait sans un mot. Une observation faite au rucher trois heures plus tôt
s'évaporait donc dès que le serveur la refusait — y compris, et surtout, sur le
409 de conflit, c'est-à-dire précisément le cas que le §11 nommait comme la
limite du hors ligne.

Deux pièces le ferment :

1. **`X-Zumm-Version`** sur `PUT /api/visites/{id}` — la version que l'appareil
   avait sous les yeux. Absente, la garde ne joue pas : les écrans en ligne
   modifient ce qu'ils viennent de lire, et leur imposer une précondition
   n'aurait protégé personne. Le refus est un **409 qui porte la version du
   serveur**, et non un 412 : l'appelant doit pouvoir proposer un choix, pas
   seulement constater un échec.
2. **La quarantaine** — une saisie refusée quitte la file (l'y garder la
   bloquerait) mais reste consultable avec le motif du serveur. L'apiculteur
   tranche : réappliquer, ou abandonner. Réappliquer **retire la garde de
   version**, sans quoi le refus se reproduirait en boucle.

Le serveur, lui, ne fusionne rien. Décider laquelle de deux observations dit vrai
sur le couvain d'une colonie est un arbitrage d'apiculteur, pas une règle de
précédence.

### Le brouillon, et pourquoi ce n'est pas une visite

La tentation était d'ajouter un état `brouillon` à `visite`. Elle est mauvaise
pour une raison qui se voit en lecture : une visite est un **acte**, dont le
registre, les agrégats, les exports et les règles se servent. Un brouillon est
une **saisie en cours** — incomplète, parfois incohérente, et susceptible de ne
jamais devenir une visite. Les mélanger aurait obligé chaque lecture du registre
à se souvenir de l'exclure ; il aurait suffi d'un oubli pour qu'une saisie
abandonnée entre dans un comptage réglementaire.

Deux conséquences assumées :

- le **contenu est opaque** au serveur, qui ne le valide pas. Exiger d'une saisie
  en cours qu'elle soit déjà complète ferait perdre exactement ce qu'on cherche à
  sauver ;
- `brouillon_visite` est la **seule table du schéma dont la politique RLS n'ouvre
  pas sur une portée globale**. Un responsable lit toutes les visites de
  l'exploitation ; il n'a pas à lire les phrases inachevées de ses collègues.

### Ce que le lot C ne fait pas

La **note vocale reste locale** — c'est le 🟡 du lot. Elle s'enregistre et se
rejoue pendant la saisie, sur l'appareil, et s'efface avec le formulaire. Elle ne
part pas au serveur, et ce n'est pas un raccourci : le dépôt n'a **aucun stockage
binaire** (`photo.url` ne porte qu'une adresse, `traitement.ordonnance` qu'une
référence). Encoder de l'audio en base64 dans un champ texte aurait fabriqué un
stockage de fichiers clandestin — invisible en revue, impossible à purger, et
hors de toute politique de rétention. La contrainte `ck_brouillon_taille` de la
`V24` est là pour ça, et son commentaire le dit.

La **transcription** reste suspendue à la décision D4 : « traitement local, aucun
trafic sortant » et « assistant IA » se contredisent tant qu'on n'a pas dit **où**
le modèle s'exécute.

---

## 21. Note de révision — 04/09/2026, lot J du plan de couverture

Quatrième lot du [plan de couverture](PLAN-COUVERTURE-ECARTS.md), et le seul
dont les lignes n'ont **aucun lien technique entre elles** : QR par ruche, NFC,
planche d'étiquettes, mot de passe en libre-service, interface progressive,
notifications par utilisateur, jeu de démonstration. Elles partagent une seule
propriété — chacune tient en moins d'une journée, et aucune ne dépend d'un autre
lot. Six passent à ✅ et une à 🟡 : le compteur va de **90 à 96 sur 153**.

Cinq des sept viennent du §13, c'est-à-dire de contournements que des éditeurs
concurrents enseignent à leurs propres utilisateurs.

### Deux décisions qui tiennent le lot

**1. Le code court n'est pas un second identifiant.** C'est celui de la ruche,
préfixé : `R-42`. La tentation était d'encoder quelque chose d'opaque et de joli
— base 32, damier, six caractères. Elle aurait créé **deux façons de nommer la
même colonie**, et le jour où elles divergent, personne ne sait laquelle fait
foi. Le code se lit à l'œil nu quand le QR est sale, mouillé ou propolisé, ce qui
est tout ce qu'on lui demande ; la puce NFC porte exactement la même charge que
le QR, pour la même raison.

**2. Masquer n'est pas interdire.** L'interface « essentielle » retire cinq
écrans de la navigation — capteurs, lots, essaims, reines, audit : ceux qui
supposent un équipement (du matériel), une activité (vendre, élever) ou une
échelle (une équipe). Aucun ne concerne la tenue d'un rucher. Mais les **rôles**
décident de ce qui est permis, ce réglage de ce qui est **montré** : un écran
masqué reste atteignable par son lien, la recherche continue d'y mener, et le
réglage revient en un clic. Confondre les deux produirait une interface qui a
l'air de retirer des droits — et un utilisateur qui appelle son responsable pour
un réglage qu'il pouvait changer lui-même. `interface.test.ts` vérifie que les
deux filtres ne se rejoignent pas.

### Le jeu de démonstration, et le seul mot qui compte

BeeLog Digital conseille de « configurer une seule ruche <em>test</em> avant de
basculer l'exploitation ». Le dépôt avait bien `infra/seed-demo.sql`, mais côté
exploitant, avec `psql`, sur le tenant de développement.

Le mot qui compte dans la ligne du §13 est **réversible**. Une démonstration
qu'on ne peut pas défaire n'est pas une démonstration, c'est une pollution :
l'exploitation garderait des ruches fictives mêlées aux vraies, et plus personne
n'oserait supprimer quoi que ce soit de peur de se tromper de cible.

D'où la table `jeu_demonstration` : elle note **ce que le chargement a créé**,
ligne par ligne, et la purge ne supprime que cela. Deviner par le nom — « tout ce
qui commence par démo » — aurait détruit le rucher d'un apiculteur ayant eu le
tort d'appeler le sien « Démonstration » ; c'est exactement ce que vérifie
`ConfortIdentificationIT.purgeNeToucheQueLaDemonstration`.

Trois verrous, et aucun n'est de trop : la fonction est **éteinte par défaut**
(`zumm.demonstration.activee`) — une production ne doit pas seulement refuser
d'écrire vingt lignes fictives, elle ne doit pas proposer le bouton ; elle est
réservée au rôle **admin**, lecture comprise ; et un second chargement est refusé
tant que le premier n'est pas purgé, parce qu'empiler deux jeux rendrait la trace
ambiguë — et c'est la trace qui rend la purge sûre.

### Ce qui reste dû : le serveur d'envoi

La **réinitialisation de mot de passe** passe de ❌ à 🟡, et pas plus loin. Le
chemin est ouvert côté produit : `/api/info` — route publique, car celui qui a
oublié son mot de passe n'est par construction pas connecté — publie l'URL du
parcours du fournisseur d'identité, et la page de récupération affiche le lien
dès qu'elle est renseignée.

Ce qui manque n'est plus du code : c'est un **serveur d'envoi**, que l'exploitant
configure (`infra/keycloak/README.md` détaille les deux gestes). Et le lien reste
caché tant qu'il n'y en a pas — un formulaire qui accepte une adresse et
n'envoie rien fait attendre un courriel qui n'arrivera jamais, ce qui est pire
que l'absence de lien. Le raisonnement est celui de `RecuperationVue` depuis le
SPRINT-19 ; il n'a pas changé, c'est la condition qui s'est déplacée.

### Ce que le NFC ne prétend pas être

`NDEFReader` n'existe ni sur iOS, ni sur Firefox, ni sur Safari macOS. Le bouton
n'apparaît donc **que là où l'API existe**, et l'écran explique l'absence
ailleurs : un bouton visible partout qui échoue une fois sur deux apprend à
l'utilisateur que la fonction ne marche pas, et il cesse de l'essayer là où elle
marche. Le QR reste le socle ; la puce est un confort.

---

## 22. Note de révision — 04/09/2026, lot F₁ du plan de couverture

Cinquième lot du [plan de couverture](PLAN-COUVERTURE-ECARTS.md) — la moitié bon
marché du lot F, celle qui ne suppose pas d'acheter du matériel (l'autre moitié
dépend de la décision **D3**). Quatre lignes passent à ✅ : le compteur va de
**96 à 100 sur 153**.

### Trois travaux, trois raisonnements différents

**1. La batterie : une valeur d'énumération, et rien de plus.** BeeLog Digital et
Onibi se font tous deux reprocher les pannes de batterie *silencieuses* — la
balance cesse d'émettre, et personne ne s'en aperçoit avant la visite suivante.
Le reproche ne porte pas sur l'absence de mesure : il porte sur l'absence
d'**alerte**. `alimentation` rejoint donc la liste des indicateurs, et
`SeuilAlerteService` s'en occupe comme des autres — même hystérésis, même table
d'alertes, même notification. Inventer une table « état des capteurs » aurait
créé un second mécanisme d'alerte à maintenir en parallèle du premier, pour dire
la même chose.

Un test d'intégration a montré ce qu'une relecture n'avait pas vu : `alerte`
porte **sa propre** liste d'indicateurs (`ck_alerte_indicateur`). L'ingestion
réussissait, l'ouverture de l'alerte échouait — un 409 sur une mesure par
ailleurs valide. Les deux contraintes disent la même chose à deux endroits, et
rien ne les tient ensemble sinon la vigilance.

**2. Le poids par hausse : une table à part, et c'est le point.** La tentation
était d'ajouter `compartiment_id` à `mesure`. Trois raisons s'y opposent, et
elles se cumulent :

- `mesure` est une **hypertable** dont la clé primaire est
  `(ruche_id, type_indicateur, instant)`. Y glisser une colonne nullable
  obligerait à remplacer la clé primaire par un index unique en `NULLS NOT
  DISTINCT` — sur la table la plus critique du système, celle que lisent les
  alertes, la prévision de récolte et la détection d'anomalie ;
- la contourner par un sentinel (`compartiment_id = 0` pour « toute la ruche »)
  ferait perdre la **clé étrangère**, dans un dépôt où toutes les autres tables
  la portent, et composite ;
- surtout, **ce ne sont pas les mêmes données**. `mesure` porte ce qu'une balance
  pèse *sous* une ruche ; `mesure_compartiment` ce qu'on *attribue* à un étage.
  Les mélanger obligerait chaque lecture existante — sans exception — à se
  souvenir d'exclure les lignes de hausse, et il suffirait d'un oubli pour qu'une
  prévision de récolte compte deux fois le même miel.

Le commentaire de la `V5` dit « une mesure = une ruche, un indicateur, un
instant ». La table séparée le laisse vrai au lieu de le casser en silence. Et
l'écran affiche « jamais pesé » là où aucune pesée n'a eu lieu : une hausse
jamais pesée n'est pas une hausse vide, et 0 kg ferait croire à des réserves
perdues.

**3. Le partage : le précédent était déjà écrit.** « Aucun partage
inter-exploitations comme chez BeeLog Digital » (§5). Le partage *interne*
existait — c'est le tenant. Ce qui manquait était de montrer une courbe à
quelqu'un du **dehors**.

`abonnement_calendrier` (SPRINT-21) avait déjà instruit la question, et sa
réponse se reprend telle quelle : jeton de 256 bits tiré d'un `SecureRandom`,
**jamais stocké** — la base n'en garde que l'empreinte SHA-256 —, expiration
obligatoire bornée à un an, révocation qui laisse la ligne en place, dernier
usage visible. Avec la même conséquence assumée : la table **porte** le tenant
sans le discriminer, puisque c'est le jeton qui le résout ; son cloisonnement est
donc applicatif et tient dans un seul service.

Deux bornes propres à ce partage, et elles sont délibérées : il porte sur **une
ruche** — un jeton qui ouvrirait l'exploitation ne serait plus un partage, ce
serait un compte sans mot de passe — et il ne rend qu'une série journalière, sans
identifiant, sans rucher et **sans position**. Le destinataire regarde une
courbe ; il n'explore pas un cheptel.

### Ce que ce lot ajoute au périmètre exposé sans authentification

C'est la **seconde et dernière route publique** du produit, après le flux
iCalendar. Elle vit sous un préfixe séparé (`/api/flux/`) de celui de la gestion
(`/api/partages`), et ce n'est pas une préférence d'URL : `TenantFilter` exempte
ses chemins publics par **préfixe**, si bien que servir le flux sous
`/api/partages/{jeton}` aurait exempté du même coup `DELETE /api/partages/{id}` —
et la révocation serait partie sans tenant.

La faiblesse du flux iCalendar reste la sienne, et il faut la redire : **aucune
limitation de débit** sur ce chemin. À prévoir si l'application s'ouvre
largement.

### Ce qui reste dans le lot F

Les quatre autres lignes — alarme anti-vol, intégrations nommées de capteurs du
commerce, Bluetooth direct, analyse acoustique — forment le **lot F₂**, suspendu
à la décision **D3** : les éprouver suppose de posséder le matériel, et Web
Bluetooth est absent d'iOS Safari, donc de la moitié du parc.

---

## 23. Note de révision — 04/09/2026, lot E du plan de couverture

Sixième et plus large lot du [plan de couverture](PLAN-COUVERTURE-ECARTS.md) :
quatorze lignes visées, **douze fermées**. Le compteur va de **100 à 112 sur
153** — les deux tiers du document sont franchis.

Le lot est mécanique en contenu ; toute sa difficulté était de savoir **où
s'arrêter**.

### La frontière, posée une fois pour toutes

La comptabilité s'arrête à la **rentabilité par ruche**. `depense` porte un
montant, une date, une catégorie et une affectation facultative. Elle ne porte
ni fournisseur, ni numéro de pièce, ni TVA, ni échéance de paiement — chacune de
ces colonnes appellerait la suivante, et la troisième rendrait le module
obligatoire pour boucler un exercice, alors que personne n'a demandé à Zümm de
tenir des comptes. Facturation, TVA, devis et clients restent ⛔ (§9).

Trois refus tiennent le module, et ils sont dans le code comme à l'écran :

1. **aucune dépense non affectée n'est répartie.** Une assurance, une formation,
   un véhicule ne se divisent pas par le nombre de ruches : la clé de
   répartition serait inventée, et le résultat aurait l'autorité d'un chiffre
   sans en avoir la matière. Elles figurent entières, à part, où elles se voient ;
2. **les recettes sont une valorisation**, pas un chiffre d'affaires. Zümm ne
   sait pas à quel prix le miel a été vendu, et ne cherche pas à le savoir ;
3. **seul le miel est valorisé.** Appliquer un prix du miel à de la cire ou à un
   essaim donnerait un total qui ne veut rien dire.

### Trois décisions de modèle

**Le produit est une colonne, pas une table.** « Le modèle présuppose du miel »
(§6) : la correction tient dans `recolte.type_produit` et `recolte.unite`. Une
récolte de cire est une récolte, faite le même jour sur la même ruche par le même
agent ; lui donner sa propre table aurait dupliqué la traçabilité, le lot, le
forçage de carence et l'export. La seconde colonne est indispensable — **cinq
essaims ne pèsent pas cinq kilogrammes** —, et la base refuse la combinaison
incohérente plutôt que de laisser produire des totaux faux.

**La maturation est une date, pas une étape.** La tentation était un workflow
récolte → maturation → mise en pot, avec ses états et ses transitions. Deux
dates disent la même chose et ne bloquent aucune saisie. La colonne porte le nom
légal actuel : la DDM a remplacé la DLUO en 2015.

**Les échéances ne se stockent pas.** Ni la prochaine maintenance d'un
équipement, ni l'état « à racheter » d'un consommable : les deux se calculent.
Les ranger en base créerait des valeurs à maintenir en cohérence avec leur
source — la dette que `ComptageVarroaService` évite depuis le SPRINT-20 pour le
taux de varroa.

### Ce que le refactor de l'export a protégé

Passer de deux à quatorze ressources aurait pu se faire en copiant douze fois la
méthode existante. Chaque ressource produit désormais une **grille**, et le rendu
est fait une fois par format. Sans cela, la neutralisation des amorces de formule
(`CWE-1236`, l'injection CSV) serait à refaire dans quatorze branches — et c'est
exactement la manière dont ce genre de garde disparaît de l'une d'entre elles.

Deux bornes accompagnent l'export : les **mesures** sont limitées à
quatre-vingt-dix jours — une hypertable de capteurs compte des millions de
lignes, et un fichier de cette taille ne sert personne —, et l'export des
**ruchers ne porte aucune coordonnée**. Un CSV circule, et il n'a pas de rôle
porteur : `PolitiquePositions` n'a aucune prise sur lui.

### Le XLSX sans dépendance, et sa condition

`ClasseurXlsx` écrit un classeur d'une feuille : cinq parties XML dans un ZIP,
cellules en chaînes en ligne. Apache POI est la bibliothèque de référence et elle
est excellente ; elle pèse une douzaine de mégaoctets de dépendances transitives
pour produire ici une grille sans style, sans formule et sans image. C'est le
même arbitrage que celui d'[ADR-007](../roadmap/operationnel/06_decisions/ADR-007-graphiques-svg.md)
sur Chart.js.

**Écrire un format de fichier à la main n'est défendable que si le test le
tient** : `ClasseurXlsxTest` vérifie les cinq parties obligatoires, les
références de cellules au-delà de la colonne Z, l'échappement XML, le retrait des
caractères de contrôle et la troncature du nom d'onglet à trente et un
caractères. Le jour où un format de nombre ou une seconde feuille manqueront
vraiment, POI redeviendra le bon choix — et le commentaire de la classe le dit,
pour que la question se repose plutôt que le fichier ne grossisse.

### Trois règles de plus dans le moteur du SPRINT-22

Aucune n'a demandé de mécanisme nouveau : le moteur, sa clé d'idempotence et son
index unique partiel accueillent des tâches **sans ruche** depuis une colonne
ajoutée à `TacheProposee`.

- `RegleMaintenanceMateriel` — la clé **porte l'échéance**, et c'est ce qui rend
  la récurrence possible : une fois l'entretien fait, la date recule, la clé
  change, et la règle repropose au terme suivant sans dupliquer le terme courant ;
- `RegleStockBas` — une proposition par **mois** et par consommable. Une clé fixe
  ne reproposerait jamais rien, même un an plus tard ; une clé portant le jour
  rendrait la liste illisible en une semaine ;
- `RegleArchivageSaisonnier` — une tâche par an, en **novembre** : la saison est
  close au nord et il reste l'hiver pour ressaisir. Janvier arriverait après les
  déclarations, août tomberait en pleine miellée.

### Les deux lignes qui restent 🟡

Le **réfractomètre** n'est pas livré, et c'est délibéré : convertir un indice de
réfraction en taux d'humidité demande la table de Chataway, propre à chaque
appareil et à sa température de calibration. L'implémenter au jugé donnerait un
chiffre faux sur la mesure qui décide de la conservation du miel — au-delà de
18 %, il fermente. Mieux vaut ne rien rendre que rendre une valeur qu'on croira
exacte.

La **logistique multi-sites**, reliquat du lot B, n'a pas été reprise : la charge
d'équipe existe, la chaîne d'approvisionnement — le terrain de HiveOS — reste
hors du produit.

---

## 24. Note de révision — 05/09/2026, lot I du plan de couverture

Septième lot du [plan de couverture](PLAN-COUVERTURE-ECARTS.md) : cinq lignes
visées, **cinq fermées**. Le compteur va de **112 à 117 sur 153**.

Le lot est petit, et il porte pourtant la décision la plus risquée du document :
rendre la grille d'inspection paramétrable **sans détruire la statistique**.

### Le piège, et la sortie retenue

Un formulaire paramétrable qui autorise des champs libres tue exactement ce que
le SPRINT-20 était venu construire. Dix exploitations inventent dix libellés pour
la même observation, et plus rien ne se compte ni ne se compare — c'est le défaut
que la `V19` corrigeait en remplaçant du texte libre par des colonnes, et il
serait revenu par la porte de derrière.

La sortie est un référentiel **fermé** : quarante-quatre points, huit familles,
écrits par une migration et par rien d'autre. Ce n'est pas une intention notée
dans un commentaire — la `V28` retire `INSERT`, `UPDATE` et `DELETE` au rôle
applicatif sur `point_observation` et `produit_traitement`. L'application ne peut
pas écrire ces tables, même si quelqu'un ajoutait la route demain. On active des
cases existantes ; on n'en invente pas.

### Le noyau reste des colonnes

Les onze champs de la `V19` ne migrent **pas** dans le référentiel. Ils sont
typés, indexés, et lus par le moteur de règles, les indices de colonie et la
fiche d'inspection : les transformer en lignes clé-valeur aurait cassé tout cela
pour une uniformité dont personne n'a l'usage.

Le gabarit les **allume ou les éteint**, section par section — quatre booléens,
pas une table. Masquer n'est pas effacer : la colonne reste, et une visite déjà
saisie garde ce qu'elle portait. C'est la même distinction qu'au lot J entre les
rôles, qui décident de ce qui est *permis*, et l'interface progressive, qui
décide de ce qui est *montré*.

Conséquence tenue dans la migration : un point du référentiel ne redit jamais un
champ du noyau, et ne redit jamais une pathologie. Le référentiel décrit ce qu'on
**voit** — ailes déformées, dysenterie, odeur au couvain ; `observation_pathologie`
nomme ce qu'on **diagnostique**, avec une gravité. Un point « traces de fausse
teigne » aurait doublonné la pathologie du même nom : il n'y est pas.

### Trois états, et non deux

C'est le point qui décide si tout le reste sert à quelque chose. Une case à
cocher ordinaire n'a que deux positions, et la position « vide » y sert à deux
choses incompatibles : « je n'ai pas regardé » et « j'ai regardé, ce n'est pas
là ». Les confondre ferait compter comme constats négatifs les visites où
personne n'a ouvert la ruche — et **tous les taux du parc descendraient dans le
sens rassurant**, qui est le pire des deux.

La distinction est donc portée de bout en bout : l'absence de ligne dans
`releve_observation` dit « pas regardé », une ligne à `false` dit « regardé,
absent ». À l'écran, c'est le motif standard de la case indéterminée —
`role="checkbox"` avec `aria-checked="mixed"` — dont un clic fait tourner
l'état. Le geste courant reste à un seul tap, celui qu'on fait avec des gants.
Et `GET /api/carnet/statistiques` rapporte les présences aux **relevés**, jamais
aux visites de la période.

C'est aussi pourquoi la ligne annonce quarante-quatre points et non cinquante :
« environ cinquante » est le chiffre de HiveTracks, et on n'en fabrique pas six
de plus pour l'annoncer aussi.

### Le référentiel de produits pré-remplit, il ne fait pas autorité

Treize varroacides, leur substance active, leur forme et leur délai. Trois
précautions le rendent défendable :

1. **la notice fait foi**, et la colonne `mention` le dit à chaque saisie. Le
   référentiel est indicatif — l'autorisation de mise sur le marché varie d'un
   pays à l'autre, et un chiffre affiché sans réserve serait cru ;
2. **le traitement enregistré garde sa propre copie du délai.** Corriger le
   référentiel demain ne doit pas réécrire un registre d'élevage d'hier, qui est
   un document opposable ;
3. **une colonne dédiée porte ce que le délai ne dit pas.** Sur la plupart de ces
   produits, la contrainte réelle n'est pas une carence mais « hausses
   retirées » : un délai de zéro jour, lu seul, se comprend comme « on peut
   récolter ». Une phrase dans une note n'aurait pas suffi.

Le catalogue s'arrête au varroa, et ce n'est pas une paresse : c'est la seule
cible pour laquelle des médicaments sont couramment autorisés en rucher. Les
antibiotiques contre les loques ne le sont pas dans l'Union européenne, et
pré-remplir un formulaire avec des produits interdits serait pire qu'un
formulaire vide.

### Le réfractomètre, et une rectification

Le SPRINT-27 l'avait écarté au motif que « la table de Chataway est propre à
chaque appareil ». **C'était confondre deux choses.** La correspondance entre
indice de réfraction et taux d'eau est publiée et vaut pour tout miel ; ce qui
appartient à l'appareil, c'est son **étalonnage** — le zéro fait à l'eau
distillée — et l'échelle qu'il affiche.

Le reste du raisonnement tenait, et il est appliqué tel quel : hors de la plage
tabulée, la conversion ne rend rien plutôt que d'extrapoler, et la réponse porte
l'indice ramené à 20 °C pour que le calcul soit vérifiable au lieu d'être pris
sur parole. La correction de température a son sens : l'indice monte quand la
température baisse, si bien qu'une lecture faite au frais décrit un miel plus
humide qu'il n'en a l'air — 1,4915 vaut 18,0 % à 20 °C et 18,9 % à 10 °C. Le
seuil de fermentation est à 18 %, celui de la norme de commercialisation à 20 %.

### Ce que la fiche imprimée a gagné

La fiche d'inspection vierge du SPRINT-24 suit désormais le gabarit par défaut :
les sections éteintes disparaissent, les points retenus s'ajoutent. Une fiche qui
demanderait au stylo autre chose que ce que l'écran demande au doigt serait la
pire des deux — impossible à ressaisir sans traduire.

Une limite l'accompagne, et elle est physique : cinq points supplémentaires au
plus. Au-delà, sur une A4 paysage, chaque colonne passe sous le centimètre, et
plus personne n'y écrit debout avec des gants. Le pied de page dit combien de
points ont été laissés de côté, plutôt que de produire en silence une feuille
inutilisable.

### Le compte, et la commande qui le donne

La commande inscrite au §6 du plan de couverture **ne reproduisait pas** le
nombre publié : elle ne lit que la deuxième colonne, ce qui lui fait manquer tout
le §13 — dont la table a une colonne de plus — et compter les quatre lignes de la
légende. Elle rendait 131 là où le document en porte 153. Le compte n'était pas
faux ; la commande censée le vérifier l'était, ce qui est plus grave, puisque
c'est elle qu'on relance pour ne pas recopier un chiffre. Elle est corrigée dans
le plan : le verdict se cherche **où qu'il soit dans la ligne**, et l'inventaire
s'arrête aux §§1 à 8 et 13.
