# Écart fonctionnel — Zümm face à douze outils apicoles du marché

> Analyse du 18/08/2026, **revérifiée contre le code le 12/09/2026** (périmètre
> SPRINT-33 inclus — voir les dix-sept notes de révision en fin de document,
> §14 à §30). Le §1 est intégralement couvert depuis la migration `V21` (§17) ;
> les onze lots du plan de couverture l'ont mené de 66 à 137 lignes (§18 à
> §28), le lot **K** du SPRINT-33 (§29) a fermé les **trois dernières lignes de
> travail identifié** — le ground truthing, l'exposition aux zones traitées et
> la logistique de tournée —, et deux des trois 🟡 que cette note laissait
> ouverts pour décision ou dépendance d'exploitation sont désormais fermés à
> leur tour (§30) : le croisement flore et la réinitialisation de mot de passe.
> Le compte est de **142 sur 153** ; tout le reste est refus argumenté ou
> hors-périmètre assumé. Les verdicts des §§1 à 13 intègrent le **registre
> sanitaire** (`V19`), le **terrain** (`V20`) et le **SIG** (`V31`).
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
| Rayon de butinage **configurable** par l'utilisateur | ✅ | `site.rayon_butinage_km` (`V31`), saisi sur la fiche du rucher, borné à 15 km — au-delà, une abeille ne rentre pas. Sur le SITE et non dans la configuration globale : deux ruchers d'une même exploitation n'ont pas le même terrain, et c'est exactement ce que les surfaces mesurent. Vide, le défaut de `ConfigZumm.ini` s'applique. C'est un **réglage et non un lieu** — il traverse le masquage de `PolitiquePositions`, et un test le vérifie |
| Couches d'occupation du sol (cultures, forêt, hydrographie, urbanisation) | ✅ | Table `couvert_sol` (`V31`, index GiST), versée par `POST /api/environnement/couvert` en GeoJSON. **La donnée est accueillie, jamais interrogée** ([ADR-015](../roadmap/operationnel/06_decisions/ADR-015-occupation-du-sol.md)) : demander à un service tiers ce qu'il y a autour d'un rucher lui apprendrait où sont les ruches — ce que `PolitiquePositions` protège depuis le SPRINT-12 et ce que le mode local du SPRINT-30 vient de couper pour les tuiles. La **taxonomie est fermée** (dix classes) : chaque source nomme les siennes autrement, et les laisser entrer telles quelles rendrait deux exploitations incomparables. L'ingesteur traduit ; une classe inconnue fait échouer le versement **entier** |
| Calcul des surfaces par type de couvert dans le rayon | ✅ | `GET /api/environnement/sites/{id}/couvert` : `ST_Intersection` sur des `geography`, donc des surfaces **géodésiques réelles** — une projection plane dériverait de plusieurs pourcents, et le produit vise aussi le Maghreb. L'intersection est **bornée au tampon avant** d'être mesurée : sans cela, une parcelle qui déborde compterait en entier, et « 60 % de cultures » désignerait un département. La réponse porte aussi `couverte`, la part du cercle que la couche décrit **réellement** : 30 % de couverture et 70 % de silence ne disent pas « 70 % de sol nu » |
| Historique et rotation des cultures sur plusieurs années | ✅ | `GET /api/environnement/sites/{id}/rotation` rend les mêmes surfaces millésime par millésime. **La rotation ne se déduit pas d'une couche, elle se lit** en comparant deux années — c'est pour cela que `couvert_sol.millesime` est `NOT NULL` dès la première ligne versée, et qu'un versement REMPLACE son millésime au lieu de s'y ajouter |
| Comparaison de plusieurs emplacements candidats (transhumance) | ✅ | `GET /api/sites/comparaison?ids=` (`ComparaisonSitesService`, SPRINT-23) aligne rendement **par ruche** sur deux saisons, flore déclarée et en fleur, altitude, exposition et densité de voisinage à 3 km. **Aucune note globale** : mélanger des kilos, des espèces et une altitude donnerait un chiffre qui a l'autorité d'une mesure sans en avoir la matière. Et **aucune coordonnée** en sortie |
| **Calendrier de floraison / suivi des miellées** (*bloom calendar*, *nectar flow*) | ✅ | Table `floraison_observee` (`V31`) et `/api/environnement/floraisons`. **L'observé ne remplace pas le déclaratif de la `V21`, il le confronte** : le déclaratif prévoit — « le colza fleurit en avril » — et se trompe trois années sur dix, une gelée tardive décalant tout d'une quinzaine ; l'observé constate. La réponse rend les deux et leur **écart en jours**, seule chose qui permette de dire « cette année, c'était en avance ». Une ressource ne fleurit qu'une fois par an : une seconde saisie COMPLÈTE la première au lieu d'échouer, parce qu'on note le début en avril et le pic en mai |
| **Comptage / prévision de pollen** | ❌ | HiveSense et APiLOG le géolocalisent par rucher. **Refusé explicitement au SPRINT-32** ([ADR-015](../roadmap/operationnel/06_decisions/ADR-015-occupation-du-sol.md)) : un comptage de pollen vient de réseaux d'aérobiologie nationaux, pas d'un capteur de rucher, et l'estimer à partir du couvert produirait un chiffre inventé sur une donnée que l'apiculteur ne peut pas vérifier. C'est le même refus que l'acoustique au SPRINT-31 et le réfractomètre au SPRINT-27 |
| Croisement santé du rucher × flore environnante (biodiversité) | ✅ | **Fermé au SPRINT-33.** `CorrelationFloreService` et `GET /api/correlations/flore` croisent la part de chaque classe de couvert autour d'un rucher (`CouvertSolRepository.partsParSite`, une requête PostGIS groupée pour tous les ruchers de l'exploitation) avec la santé moyenne des colonies évaluées du même rucher (indices du SPRINT-22). L'unité d'observation est le RUCHER et non la ruche — apparier chaque colonie ferait entrer le même couvert plusieurs fois et gonflerait artificiellement l'échantillon. Même refus de conclure qu'à la météo (`Coefficient`, partagé entre les deux corrélations depuis ce sprint) : en dessous de douze ruchers, le coefficient sort nu, sans verdict. Dix classes testées sur le même échantillon ne sont pas corrigées de la comparaison multiple, et l'écran le dit. Onglet dédié dans `TableauxVue.tsx` (« Flore × santé »). Le produit à part entière de HiveTracks (*DaaS*, module RSE) reste hors périmètre |
| Vérification du taux de cultures bio dans le rayon réglementaire | ⛔ | Dépend de CartoBio (Agence Bio) — voir la note ci-dessous |
| Évaluation de l'exposition aux zones traitées | ✅ | Table `zone_traitee` (`V32`, index GiST) et `GET /api/environnement/sites/{id}/exposition` : les zones qui recoupent le rayon, leur distance, et le nombre encore **sous délai de rentrée**. L'objection du SPRINT-32 n'a pas été contournée, elle a été prise au mot — aucune couche ouverte ne dit ce qui a été épandu, donc Zümm n'en interroge aucune : il **accueille une déclaration** et en nomme la source (`origine` : voisin, observation, avis officiel, autre). C'est ce qu'un apiculteur peut réellement obtenir, et `distanceCultureM` reste à sa place — une parcelle cultivée n'est pas une parcelle traitée. `RegleZoneTraiteeProche` engendre une tâche par rucher exposé sur une fenêtre de **quatorze jours** : au-delà, la déclaration reste consultable mais ne réveille plus personne. La tâche dit d'aller **regarder les planches d'envol** ; elle n'affirme ni la dose, ni la dérive, ni le vent de ce jour-là |

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
| **Recommandations automatiques / tâches générées** | ✅ | `MoteurRegles` (SPRINT-22) et **dix** règles : fin de carence à trois jours, contrôle de ponte à J+7, varroa au-dessus du seuil, réserves au plus bas, visite compromise par la météo, stock bas, maintenance du matériel, archivage saisonnier, et depuis le SPRINT-33 la vérification terrain d'une couche et le voisinage d'une zone traitée. Chaque tâche porte la **clé** de ce qui l'a déclenchée (`carence-retrait:42`) et un index unique partiel empêche la règle de la recréer à chaque passage — sans quoi la liste se remplirait de doublons jusqu'à n'être plus lue. La maille de la clé est un choix par règle : `zone-traitee-proche` porte la date de la dernière déclaration, si bien qu'une nouvelle déclaration rouvre une tâche là où une clé figée resterait muette |
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
| **Saisie vocale** des observations | ✅ | `voix/dictee.ts` et `BoutonDictee`, posés sur les constatations de visite (SPRINT-30). Le texte **s'ajoute** au champ et ne le remplace pas — une dictée qui écrase la saisie au premier appui malheureux est inutilisable avec des gants, et l'appui malheureux est la règle. Trois langues de reconnaissance, l'arabe compris |
| **Transcription IA embarquée, hors ligne** (Whisper sur l'appareil) | ✅ | [ADR-013](../roadmap/operationnel/06_decisions/ADR-013-ou-tourne-l-ia.md) tranche la décision D4 : **sur l'appareil, ou pas du tout**. La dictée n'utilise `SpeechRecognition` que si le navigateur expose un réglage de traitement local ; là où il route l'audio vers un service de reconnaissance, l'interface **refuse et l'écrit**. Whisper WASM est écarté — quarante mégaoctets à télécharger contredisent la raison d'être d'une PWA qui monte au rucher — mais le point d'entrée est unique, et lui donner un second moteur ne touchera aucun écran |
| Notes vocales simplement enregistrées, sans transcription | ✅ | La note enregistrée du SPRINT-24 est **doublée d'une transcription** au SPRINT-30, et l'audio ne quitte toujours pas l'appareil — il n'est même plus le seul support, puisque le texte, lui, se ressaisit. La décision de 2026-09-02 (« encoder de l'audio en base64 aurait fabriqué un stockage de fichiers clandestin ») est généralisée au transport par l'ADR-013 |
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
| **Connexion Bluetooth directe** aux capteurs du commerce (BroodMinder, BEEP, SensorPush, Inkbird…) | ✅ | `capteurs/bluetooth.ts` (SPRINT-31) lit un capteur sans passerelle, sur le **profil Bluetooth SIG** : *Environmental Sensing* (`0x181A`) et *Battery* (`0x180F`). Ces identifiants sont **normalisés, pas devinés** — tout capteur qui les implémente fonctionne. [ADR-014](../roadmap/operationnel/06_decisions/ADR-014-capteurs-du-commerce.md) refuse d'aller plus loin : écrire un décodeur pour la trame propriétaire d'un fabricant dont personne n'a l'appareil reviendrait à deviner une structure de données, et le résultat aurait l'apparence du support sans en avoir la fiabilité. **Web Bluetooth est absent d'iOS Safari**, et l'écran l'écrit au lieu d'afficher un bouton inerte |
| Intégrations nommées de capteurs du commerce | 🟡 | `POST /api/mesures/lot` (SPRINT-31) sert **toutes** les intégrations sans en privilégier aucune : quarante ruches et quatre indicateurs relevés au quart d'heure faisaient cent soixante requêtes, elles en font une. Les adaptateurs nommés, eux, restent dehors — et [ADR-014](../roadmap/operationnel/06_decisions/ADR-014-capteurs-du-commerce.md) l'assume : **personne n'a vérifié une seule trame BroodMinder ici**, et un adaptateur écrit contre un format supposé se présenterait comme du support. C'est un partenariat, pas un développement |
| Poids **par hausse** | ✅ | Table `mesure_compartiment` (`V26`), hypertable distincte, FK composite vers `compartiment`. **Distincte de `mesure`, et c'est le choix** : celle-là porte ce qu'une balance pèse sous la ruche entière, celle-ci ce qu'on attribue à un étage. Les fondre aurait demandé de rendre nullable une colonne de la clé primaire de l'hypertable la plus critique du système — ou un sentinel, qui aurait fait perdre la clé étrangère. Une hausse jamais pesée rend `null`, jamais 0 |
| Alertes à seuils sur capteurs | ✅ | `SeuilAlerteService` (hystérésis anti-rebond), `Alerte`, `NotificationAlerteService` |
| Notification e-mail à l'ouverture d'une alerte | ✅ | `NotificationAlerteService` — **un seul destinataire par message** (`setTo`), jamais de liste. Voir la leçon n°1 du §10 |
| Prévision de récolte | ✅ | `PrevisionRecolteService` — régression linéaire sur la série de poids, projection 7 j |
| Détection d'anomalie par IA | ✅ | `MoteurAnomalie` (port) + `ClientAnomalieIA` → microservice Python. HiveBook fait la même chose sur l'appareil |
| Partage d'un flux de télémétrie entre utilisateurs | ✅ | `partage_telemetrie` (`V26`) + `GET /api/flux/{jeton}`, **sans session** : la courbe d'UNE ruche, montrée à un mentor, un technicien sanitaire ou un groupement. Même forme que l'abonnement iCalendar du SPRINT-21 — jeton de 256 bits jamais stocké en clair, expiration obligatoire, révocation, usage horodaté — et le destinataire ne reçoit ni identifiant, ni rucher, ni position |
| **Analyse vidéo / acoustique à l'entrée** (comptage de trafic, perte de reine) | ❌ | Onibi seul, et **refusé explicitement** au SPRINT-31 ([ADR-014](../roadmap/operationnel/06_decisions/ADR-014-capteurs-du-commerce.md)). Il faudrait un flux audio ou vidéo — donc un stockage binaire que le dépôt n'a pas — et un modèle que l'[ADR-013](../roadmap/operationnel/06_decisions/ADR-013-ou-tourne-l-ia.md) interdit de faire tourner ailleurs que sur l'appareil. Le détail qui tranche : sortir « colonie orpheline » d'un pic de fréquence sans donnée de validation produirait un verdict inventé sur une question que l'apiculteur ne peut vérifier qu'en ouvrant la ruche — c'est-à-dire le geste qu'on prétendait lui épargner. Le port `MoteurAnomalie` reste prêt ; c'est la donnée, et la preuve, qui manquent |
| **Actionneurs à distance** (portes robotisées, protection anti-frelon) | ⛔ | Zümm observe, il ne commande pas. Piloter un actionneur engage la sécurité de la colonie et suppose du matériel propriétaire |
| **Alarme anti-vol / détection de basculement** | ✅ | `AlerteAntivolService` (SPRINT-31) : une chute de poids au-delà de `chute_vol_kg` entre deux mesures espacées de moins de deux heures ouvre une alerte **critique**. Aucun capteur nouveau n'était requis — une ruche emportée se voit dans la série de poids que l'API ingère déjà ; ce qui manquait était une règle. **Et la règle tient en une phrase** : une chute de vingt kilogrammes est une *récolte* si une récolte a été enregistrée ce jour-là, et un *vol* sinon. Sans cette vérification, la première miellée réveillerait l'alarme sur tout le rucher, et l'apiculteur la couperait. L'`inclinaison` s'ajoute comme sixième indicateur (`V30`), sur le patron de l'alimentation du SPRINT-26. L'alerte **ne se referme jamais toute seule** : une ruche volée ne revient pas |
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
| Ailes clippées, fournisseur, ruche-mère | ✅ | Colonnes de `reine` (`V29`). `ailes_clippees` est un booléen **nullable** : « on ne sait pas » n'est pas « non clippée », et une reine achetée arrive souvent sans qu'on l'ait vérifié. `fournisseur` n'est accepté que sur une reine achetée (`ck_reine_fournisseur`) — sans cette contrainte, la colonne se remplirait de noms d'éleveurs sur des reines d'essaimage, et « qu'ai-je acheté cette année ? » n'aurait plus de réponse. `ruche_mere_id` est distinct de `mere_id` : on greffe souvent depuis une colonie dont la reine n'est pas enregistrée |
| **Généalogie / arbre de lignées** | ✅ | Table `reine` avec clé étrangère réflexive (`V29`), `GET /api/elevage/reines/{id}/genealogie`, arbre SVG dans `ArbreLignee.tsx`. **Le plan de couverture se trompait** en annonçant cette clé sur `suivi_reine` : cette table-là est le JOURNAL d'une ruche, et la clé aurait relié des *événements* — « de quelle mère descend cette reine ? » n'aurait eu aucune réponse stable. Les cycles sont refusés au service ; la base n'en voit qu'un pas |
| Dates de greffage, suivi d'élevage | ✅ | Quatre dates sur `reine` (greffage, naissance, fécondation, introduction) et la table `serie_elevage` (`V29`). **Un lot, pas une reine à la fois** : ce que l'éleveur note, c'est quarante cupules greffées un lundi, trente et une acceptées, vingt-huit nées. Les comptes sont *saisis*, jamais déduits des reines enregistrées — on n'enregistre que celles qu'on garde, et le taux d'acceptation serait faux, toujours trop bas |
| **Index génétique multicritère** (hygiène, résistance varroa, douceur) | ✅ | `IndexGenetiqueService` (SPRINT-29) : cinq critères — douceur, infestation varroa, miel récolté, épisodes d'essaimage, test hygiénique —, **bornés au règne** de la reine sur sa ruche. Trois refus le tiennent : **aucune note globale** (des kilos, une douceur et un taux d'infestation ne s'additionnent pas — même refus qu'au §7 de la comparaison d'emplacements) ; **rien n'est stocké**, tout se recalcule ; et un critère sans assez d'observations vaut `null` plutôt qu'un zéro qui se lirait comme un mauvais résultat. Le test hygiénique est entré au référentiel **fermé** du SPRINT-28 par la `V29` — c'est la porte prévue pour cela |
| Photos de reine et de motif de ponte | ✅ | **Verdict périmé depuis la `V20`**, corrigé le 05/09/2026 : `Photo.Cible.REINE` existe depuis le SPRINT-21, et le motif de ponte se photographie sur la visite (`Cible.VISITE`). La photo se rattache à l'ÉVÉNEMENT du journal, c'est-à-dire au moment où elle a été prise — et non à la reine en tant qu'individu, ce qui obligerait à choisir laquelle de ses photos la représente |
| **Registre d'élevage réglementaire** (PDF / Excel) | ✅ | `GET /api/elevage/registre.pdf` (`RegistreElevagePdfService`, SPRINT-29) : traitements et nourrissements de la période, dans l'ordre où un contrôle les demande. **Aucune ligne n'y est estimée** — un registre est opposable, et le combler par une moyenne le rendrait faux là où il n'était qu'incomplet. Un registre vide sort tout de même, avec la mention « aucune saisie sur la période » : il se présente devant un contrôle, il ne se refuse pas |
| Rapports de conformité / certification bio | ✅ | `GET /api/elevage/conformite` et son PDF (SPRINT-29). **Zümm ne certifie rien, et le document le dit en tête** : la certification est prononcée par un organisme agréé, sur pièce et sur place. Trois états, dont le troisième porte tout le sens — `verifie`, `signale`, et surtout `a_justifier` pour ce que le système ignore : l'origine biologique des sucres, celle de la cire, le statut du foncier. Les compter comme conformes serait un mensonge par omission, et le dossier perdrait toute valeur au premier contrôle. La seule non-conformité constatée d'elle-même est la récolte sous carence forcée — et elle n'est visible que parce que le SPRINT-22 a rendu le forçage traçable au lieu de l'interdire |
| Déclaration NAPI, déclaration annuelle des ruches | ⛔ | Dispositifs nationaux français |
| Multi-utilisateurs et rôles | ✅ | `Agent` + `RoleAgent` (apiculteur/superviseur/responsable/admin), `InvitationController`, RBAC Keycloak (`SecurityConfig.matriceRbac`). **Plus fin** que les rôles d'ApiManager et sans plafond d'accès, là où BeeKeepPal limite à trois |
| Lisibilité des droits pour l'utilisateur | ✅ | SPRINT-19 : la navigation masque les écrans fermés au rôle (`routage/routes.ts`, `ROLES_ONGLET`), `InterditVue` explique le refus au lieu d'un 403 nu, et `PermissionsVue` publie la matrice complète — *voici les serrures, voici qui a les clés*. **Aucun des douze ne montre ses règles d'accès à ses utilisateurs** |
| Compte en libre-service (création, consultation) | ✅ | `ConnexionVue` (connexion **et** inscription depuis l'application), `CompteVue` : utilisateur, exploitation et rôles lus dans la session serveur, aucun jeton en mémoire du navigateur (ADR-006) |
| **Réinitialisation de mot de passe en libre-service** | ✅ | **Fermé au SPRINT-33.** Le chemin était déjà ouvert côté produit (SPRINT-25) : `/api/info` publie l'URL du parcours du fournisseur d'identité, et `RecuperationVue` affiche le lien dès qu'elle est renseignée. Le **serveur d'envoi** qui manquait est désormais fourni — Mailpit en développement (`infra/docker-compose.dev.yml`), un `smtpServer` paramétrable par variables d'environnement en production (`realm-zumm.json`). Vérifié en conditions réelles et non seulement câblé : `execute-actions-email` déclenche un envoi réel, capté par Mailpit, avec un lien qui ouvre une vraie page Keycloak. Piège trouvé et documenté au passage (`infra/keycloak/README.md`) : Keycloak n'importe le realm qu'à sa création en base, jamais aux démarrages suivants — un volume Postgres antérieur au sprint continue de tourner sans SMTP même après reconstruction des images |
| Cloisonnement des données entre exploitations | ✅ | Multi-tenant + RLS PostgreSQL, `TenantFilter`, `tenant_id` obligatoire dans le JWT. **Aucun des douze ne le documente** |
| Piste d'audit | ✅ | `AuditEntree`, `AuditAspect`, `AuditController`, `AuditVue.tsx` |
| Tâches avec échéance | ✅ | `Tache` + `GET /api/taches/rappels` |
| Priorité, type et notification de tâche | ✅ | `tache.priorite` (quatre niveaux), `categorie` (huit valeurs) et `origine` — `manuelle` ou `regle`, avec le code de la regle, pour qu'une tache proposee puisse se justifier a l'ecran. Notification par courriel des seules taches **critiques** : notifier chaque creation reviendrait a n'en notifier aucune, les messages etant filtres des la troisieme semaine. Un message par destinataire, `setTo` d'une seule adresse — la faute de BeeKeepPal (§10) reste structurellement impossible |
| Tableau de bord de synthèse | ✅ | `TableauDeBordController` : calendrier, production, alertes sanitaires, synthèse, prévisions |
| **Vision à trois niveaux** (ruche → rucher → exploitation) | ✅ | `GET /api/ruchers/synthese` (`SyntheseRucherService`, SPRINT-23) + volet « Par rucher » : santé moyenne, risque d'essaimage **maximal** (et non moyen — une colonie prête à essaimer ne se dilue pas), colonies sous carence, alertes, tâches et production, triés du plus préoccupant au plus calme. La moyenne ne porte que sur les colonies **réellement évaluées**, et un rucher jamais visité rend `null` : « inconnu » n'est pas « en mauvaise santé » |
| Tournée optimisée du jour | ✅ | `OptimiseurTournee` (plus proche voisin + 2-opt), `GET /api/plannings/tournee` — **APIGO l'annonce, Zümm l'a** |
| Coordination d'équipes terrain, logistique multi-sites | ✅ | Deux moitiés, et la seconde ferme la ligne. `GET /api/equipe/charge` (SPRINT-23) donne la charge par agent — ruches, **ruchers concernés** (trois ruches sur trois ruchers font trois déplacements), tâches ouvertes, en retard, critiques, visites à sept jours —, assortie de la phrase qui dit qu'elle ne sert **pas** à comparer des personnes. `GET /api/plannings/chargement` (`ChargementService`, SPRINT-33) donne l'autre : ce qu'il faut avoir dans le véhicule avant de partir, consolidé pour la tournée et détaillé par étape. **Le manque est nommé, jamais corrigé** — un consommable insuffisant s'affiche comme tel, décider quelle ruche on saute étant une décision d'exploitation et non un arbitrage de logiciel. Le service n'invente rien : il assemble la tournée du SPRINT-23, les tâches échues et le stock ; le seul ajout du lot est la colonne qui relie une tâche à ce qu'elle prélève |
| Assistant / mentor IA, briefing quotidien | ✅ | `GET /api/briefing` (`BriefingService`, SPRINT-30), en tête du tableau de bord : alertes ouvertes, tâches échues **groupées en une ligne**, carences qui se terminent dans la semaine, colonies non ouvertes depuis trois semaines. **Aucun modèle de langue n'intervient, et l'écran le dit.** Chaque ligne cite ce qui la fonde — un compte, une date, un nom de ruche — et se vérifie d'un clic. Une phrase du genre « votre colonie 12 semble affaiblie » serait plus agréable et moins vérifiable ; le jour où elle serait fausse, personne ne saurait d'où elle vient. Et il faudrait envoyer l'historique de l'exploitation dehors, ce que la ligne §8 ci-dessous vient d'interdire |
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
| Traitement local, sans aucun trafic sortant | ✅ | **Deux bascules, parce qu'il y a deux trafics.** Côté serveur, `PolitiqueReseau` (`zumm.reseau.sortant=false`) coupe `api.open-meteo.com` et le microservice ; la météo retombe sur la simulation déterministe, et la réponse **dit** que la valeur est simulée. Côté navigateur, le mode local coupe les tuiles `tile.openstreetmap.org`, dont la seule séquence révèle où sont les ruchers. `GET /api/info` publie l'état du serveur pour que l'écran l'affiche : les confondre en un seul interrupteur ferait croire à l'exploitant que son poste est muet alors qu'il ne l'est qu'à moitié |
| IA embarquée sur l'appareil | ✅ | `local/ewma.ts` : la détection d'anomalie s'exécute **dans le navigateur** en mode local, sur les mesures déjà chargées. Ce n'est pas un modèle et il ne faut pas l'appeler ainsi — c'est une moyenne mobile exponentielle, dont le mérite est justement de tenir dans un navigateur sans rien télécharger. **Le risque réel n'est pas l'erreur, c'est la dérive** : deux implémentations de la même formule s'écartent en silence. `ewma.test.ts` et `AnomalieEmbarqueeTest` fixent les **mêmes trois nombres sur la même série** ; toucher l'un sans l'autre fait échouer une des deux campagnes |
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
4. ~~**Tâches et rappels engendrés par les événements.**~~ ✅ **Livré au
   SPRINT-22** (lot A) : `MoteurRegles` et le cas d'école cité ici — le retrait
   d'un traitement — est la première règle écrite, proposée **trois jours avant**
   la fin de carence. Elles sont **dix** aujourd'hui, les deux dernières datant du
   SPRINT-33. **Ce qu'il en reste** : rien. Une règle nouvelle est désormais une
   classe et une clé, pas un chantier.
5. ~~**Couche d'occupation du sol et calendrier de floraison.**~~ ✅ **Livré aux
   SPRINT-32 et 33** : `couvert_sol` et `floraison_observee` (`V31`), le
   versement, les surfaces géodésiques par classe, la rotation d'un millésime à
   l'autre, la floraison observée confrontée au déclaratif — puis, au lot K, le
   ground truthing et les zones traitées (`V32`). L'axe où un concurrent jouait
   sur le terrain revendiqué par Zümm est fermé, **sans connecteur sortant** :
   [ADR-015](../roadmap/operationnel/06_decisions/ADR-015-occupation-du-sol.md)
   tranche que la donnée est accueillie, jamais interrogée. **Ce qu'il en reste**
   : le choix d'un référentiel portable hors de France, qui est une décision
   produit et non un développement (§2).
6. ~~**Saisie vocale.**~~ ✅ **Livrée au SPRINT-30** : `voix/dictee.ts` et
   `BoutonDictee`, trois langues de reconnaissance, l'arabe compris — **personne
   ne le fait en arabe**. La décision D4 est tranchée par
   [ADR-013](../roadmap/operationnel/06_decisions/ADR-013-ou-tourne-l-ia.md) :
   sur l'appareil, ou pas du tout ; là où le navigateur enverrait la voix à un
   service tiers, l'interface **refuse et l'écrit**. La fiche imprimable qui la
   précédait dans cette ligne est livrée depuis le SPRINT-24. **Ce qu'il en
   reste** : Whisper WASM, écarté — quarante mégaoctets contredisent la raison
   d'être d'une PWA qui monte au rucher.
7. ~~**Généalogie des reines.**~~ ✅ **Livrée au SPRINT-29** : table `reine` à clé
   étrangère réflexive (`V29`) et arbre SVG. **La formulation de cette ligne était
   fausse**, et c'est ce qui la rend utile à relire : la clé n'allait pas sur
   `SuiviReine`, qui est le JOURNAL d'une ruche — elle aurait relié des
   *événements*, et « de quelle mère descend cette reine ? » n'aurait eu aucune
   réponse stable.

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
| « Vérifiez sur le terrain au printemps la culture réellement semée » (*ground truthing*) | BeeGIS | Toute donnée environnementale porte son **millésime** et peut être marquée « à confirmer », ce qui engendre une tâche de vérification | ✅ | `couvert_sol.a_confirmer`, `classe_constatee` et `constate_le` (`V32`), `RegleVerificationCouvert` — une tâche par rucher et **par saison**, le ground truthing étant un geste de printemps. Le constat **n'écrase pas** `classe` : les lectures prennent `COALESCE(classe_constatee, classe)`, et `GET /api/environnement/couvert/fiabilite` compte ce que le terrain a confirmé, démenti, ou pas encore regardé. Écraser aurait détruit ce que le ground truthing établit — que la couche se trompait —, et la fiabilité d'un millésime ne se mesurerait plus |
| « Servez-vous de l'historique de rotation sur 3 à 5 ans » | BeeGIS | **Comparaison saison contre saison** et **millésime des données environnementales** | ✅ | `GET /api/saisons` (SPRINT-27) : années civiles, rendement par ruche productive, ventilation par produit. Tous les autres agrégats du produit glissent — douze mois qui reculent chaque jour ne permettent pas de dire « 2026 a mieux donné que 2025 ». Et depuis le SPRINT-32, `couvert_sol.millesime` est **obligatoire** : une occupation du sol de 2019 présentée comme l'état du jour n'est pas une approximation, c'est une affirmation fausse. La source et le millésime accompagnent chaque réponse |
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

---

## 25. Note de révision — 05/09/2026, lot D du plan de couverture

Huitième lot du [plan de couverture](PLAN-COUVERTURE-ECARTS.md) : sept lignes
visées, **sept fermées**. Le compteur va de **117 à 124 sur 153** — quatre
cinquièmes du document. Le §7, le plus fourni des treize, n'a plus de ❌.

### Le plan se trompait, et c'est le cœur du lot

Le plan annonçait « une clé étrangère réflexive sur `suivi_reine` ». Elle aurait
été **fausse**, et il vaut mieux le dire que de l'implémenter.

`suivi_reine` (V9) n'est pas une reine : c'est le **journal d'une ruche** —
introduite, en ponte, remplacée, disparue, essaimée. Une ligne y est un
événement, et une même ruche en porte des dizaines, appartenant à des reines
successives. Une clé réflexive sur cette table aurait relié des *événements*
entre eux : la question « de quelle mère descend cette reine ? » n'aurait eu
aucune réponse stable — quel événement désigne la mère, celui de son
introduction ou celui de sa disparition ? L'arbre aurait dépendu de la manière
dont on l'a construit, et deux écrans auraient dessiné deux généalogies.

La reine devient donc une **table**, `reine`, avec sa filiation. `suivi_reine`
reste le journal, et gagne un `reine_id` **facultatif** — voir plus bas.

### Le `NULL` qui protège une généalogie

`suivi_reine.reine_id` est nullable, et ce n'est pas un oubli de migration. Les
événements enregistrés avant ce lot ne désignent aucune reine : la table ne
portait que la ruche. Deviner laquelle après coup — en regroupant par année de
naissance, par couleur de marquage — aurait **fabriqué** une généalogie.

Un arbre faux est pire qu'un arbre absent : on le lit sans le savoir. Les
événements antérieurs restent donc rattachés à la ruche seule, et le disent.

### L'index génétique, et trois refus

C'était le seul morceau du lot qui demandait une décision métier : quels
critères, quelle pondération, et comment afficher une note dont personne ne doit
croire qu'elle est une mesure.

**Il n'y a pas de note.** Cinq critères — douceur, infestation varroa, miel
récolté, épisodes d'essaimage, test hygiénique —, chacun dans son unité propre,
et rien qui les additionne. Les ramener à un seul nombre demanderait une
pondération que personne n'a demandée, qui ne se justifie par rien, et qui serait
pourtant le seul chiffre retenu. C'est le même refus qu'au SPRINT-23 pour la
comparaison d'emplacements, où « des kilos, des espèces florales et une altitude
ne s'additionnent pas ».

**Rien n'est stocké** : tout se recalcule à chaque lecture, comme les indices de
colonie du SPRINT-22 et le taux de varroa du SPRINT-20.

**Chaque critère porte son nombre d'observations**, et vaut `null` en dessous du
minimum. Une douceur mesurée sur une seule visite n'est pas une douceur : c'est
un jour de vent, et l'afficher comme une note ferait écarter une reine sur une
mauvaise journée. L'écran écrit « pas assez observé » plutôt qu'un tiret, qui se
lirait comme un zéro.

Deux détails d'exécution méritent d'être signalés. Le calcul est **borné au
règne** : sans cette borne, une reine introduite en juillet hériterait de la
récolte de printemps de la précédente, et le classement de l'éleveur serait
exactement inversé. Et les méthodes de comptage du varroa **ne se mélangent
pas** — un lange donne des varroas par jour, un échantillon un pourcentage ; le
SPRINT-20 avait déjà refusé de les confondre, et en faire une moyenne unique ici
aurait défait ce refus. On retient la méthode la plus employée pendant le règne,
et l'unité le dit.

Enfin, le **test hygiénique** — le seul des critères du §7 dont aucune donnée
n'existait — entre par la porte prévue à cet effet : une migration ajoute un
point au référentiel **fermé** du SPRINT-28. C'est exactement pour cela que cette
porte existe. Il vaut `null` pour tout le monde tant que personne ne l'a relevé,
et l'index le dit au lieu d'afficher une note bâtie sur rien.

### Le dossier de conformité ne certifie rien

Une application qui déclarerait une exploitation « conforme bio » émettrait une
affirmation réglementaire qu'elle n'a aucun moyen de tenir — et elle serait crue,
précisément parce qu'elle sortirait d'un logiciel. La certification est prononcée
par un organisme agréé, sur pièce et sur place.

Le dossier **rassemble** donc ce qu'un contrôleur demande, et **nomme ce qu'il ne
peut pas vérifier**. Trois états, et le troisième porte tout le sens :
`a_justifier` pour l'origine biologique des sucres, celle de la cire gaufrée, le
statut du foncier — tout ce qui se prouve par facture ou par attestation. Les
compter comme conformes serait un mensonge par omission, et le dossier perdrait
sa valeur au premier contrôle. L'avertissement est imprimé **en tête** du PDF,
parce qu'un document circule sans la page qui l'a produit.

La liste des substances admises est volontairement courte, et comparée sur la
**substance active** plutôt que sur le nom commercial : un nom change, une
molécule non. Ce qui n'y figure pas est *signalé*, jamais déclaré interdit — une
spécialité peut être autorisée là où nous ne la connaissons pas, et c'est au
contrôleur de trancher.

Un point mérite d'être noté : la seule non-conformité que le système constate de
lui-même est la **récolte sous carence forcée**. Elle n'est visible que parce que
le SPRINT-22 a rendu le forçage traçable au lieu de l'interdire — un refus sans
issue aurait fait disparaître le traitement du registre, et cette vérification
n'existerait pas.

### Un défaut trouvé en chemin, et prouvé

En écrivant les clés étrangères de la `V29`, cinq clés posées en `V20` et `V27`
se sont révélées **fausses**. `ON DELETE SET NULL` sans liste de colonnes met à
`NULL` *toutes* les colonnes référençantes — `tenant_id` compris, qui est
`NOT NULL` partout dans ce schéma. Supprimer une ruche portant une dépense, une
division ou une capture d'essaim échouait donc en 500, sur un message parlant de
`tenant_id` : au dernier endroit où l'on aurait cherché.

Les migrations `V2`, `V4`, `V6`, `V7` et `V19` écrivaient la forme correcte —
`ON DELETE SET NULL (visite_id)`. Cinq clés l'avaient perdue. La `V29` les
répare, et `ElevageGenealogieIT.suppressionDUneRucheReferencee` le prouve : le
correctif retiré, ce test échoue sur *« null value in column "tenant_id" of
relation "depense" violates not-null constraint »*.

### Un verdict périmé de plus

« Photos de reine et de motif de ponte — `Photo` ne se rattache qu'à une
visite » : c'était vrai avant la `V20`, et faux depuis. `Photo.Cible.REINE`
existe depuis le SPRINT-21. La ligne passe à ✅ sans qu'une seule ligne de code
ait été écrite pour elle. Trois verdicts périmés avaient déjà été corrigés le
02/09/2026, et la rectification du réfractomètre au lot I en est une cinquième :
c'est la raison pour laquelle chaque ✅ doit citer le fichier qui le prouve, et
pourquoi ce document se relit à chaque lot au lieu de s'incrémenter.

---

## 26. Note de révision — 05/09/2026, lot G du plan de couverture

Neuvième lot du [plan de couverture](PLAN-COUVERTURE-ECARTS.md) : cinq lignes
visées, **six fermées** — la sixième étant le 🟡 des notes vocales, que la
transcription achève. Le compteur va de **124 à 130 sur 153**.

Ce lot était bloqué par la décision **D4**, la seule des quatre à porter sur une
question de principe plutôt que de coût. Elle est tranchée par
[ADR-013](../roadmap/operationnel/06_decisions/ADR-013-ou-tourne-l-ia.md).

### La décision, en une phrase

**Rien de ce qui est personnel ne quitte l'appareil, et rien n'est envoyé à un
tiers.** Trois conséquences, et elles doivent tenir ensemble — c'est ce qui rend
la décision autre chose qu'un slogan.

### 1. La transcription se fait sur l'appareil, ou pas du tout

La dictée n'utilise `SpeechRecognition` que si le navigateur expose un réglage de
traitement local. Là où il route l'audio vers un service de reconnaissance —
c'est le cas de Chrome de bureau —, l'interface **refuse et l'écrit**, plutôt que
d'envoyer la voix de l'apiculteur à un fournisseur sans le lui dire.

La détection est volontairement conservatrice : rien ne permet de vérifier qu'un
navigateur honore le réglage qu'il expose, et à défaut de preuve, la position
prudente est celle qui n'envoie rien.

C'est la généralisation d'une décision déjà prise. Au SPRINT-24, la note vocale
était restée sur l'appareil parce qu'encoder de l'audio en base64 dans un champ
texte aurait fabriqué un stockage de fichiers clandestin. Le raisonnement valait
aussi pour le **transport**, et il est maintenant écrit.

**Whisper WASM est écarté**, et ce n'est pas une paresse : quarante mégaoctets à
télécharger sur le téléphone qui monte au rucher contredisent la raison d'être de
la PWA. La porte reste ouverte — la transcription tient derrière une seule
fonction, et lui donner un second moteur ne touchera aucun écran.

Deux détails d'exécution méritent d'être notés. Le texte **s'ajoute** au champ et
ne le remplace pas : une dictée qui écrase la saisie au premier appui malheureux
est inutilisable avec des gants, et l'appui malheureux est la règle. Et seuls les
segments **finaux** sont transmis — les résultats intermédiaires changent à
chaque syllabe, et les écrire donnerait un texte qui se réécrit sous les doigts.

### 2. L'assistance ne passe par aucun modèle de langue

Le briefing quotidien lit quatre registres qui existent : alertes ouvertes,
tâches échues, carences qui se terminent dans la semaine, colonies non ouvertes
depuis trois semaines. Chaque ligne **cite ce qui la fonde**.

Un modèle qui rédigerait « votre colonie 12 semble affaiblie » produirait une
phrase plus agréable et moins vérifiable ; le jour où elle serait fausse,
personne ne saurait dire d'où elle vient. Et il faudrait lui envoyer l'historique
de l'exploitation, ce que le point 1 vient d'interdire. C'est le même arbitrage
qu'au SPRINT-22, où les règles sont du code plutôt qu'une table paramétrable.

Deux choix de présentation portent le reste. Les tâches en retard tiennent en
**une** ligne : une exploitation qui en a quarante n'a pas quarante choses à
savoir, elle en a une. Et une colonie **jamais** visitée n'y figure pas — elle
vient peut-être d'être enregistrée, et la signaler le jour de sa création ferait
passer le briefing pour un reproche.

### 3. Le mode local est une bascule, pas une promesse en prose

Le reproche du §8 était précis : il n'existait **aucune bascule explicite**. Il y
en a maintenant deux, parce qu'il y a deux trafics, et l'écran refuse de les
confondre.

- **Le serveur** : `zumm.reseau.sortant=false` coupe `api.open-meteo.com` et le
  microservice d'anomalie. La météo retombe sur la simulation déterministe — et
  la réponse **dit** que la valeur est simulée, ce qui est le point : une météo
  simulée présentée comme relevée serait pire qu'une météo absente. C'est
  exactement le grief que `vite.config.ts` formule contre le cache d'API depuis
  le SPRINT-13.
- **Le navigateur** : le mode local coupe les tuiles de carte, dont la seule
  séquence révèle où sont les ruchers — ce que `PolitiquePositions` protège côté
  serveur depuis le SPRINT-12. La carte garde ses marqueurs et ses rayons de
  butinage, et perd son fond ; les positions relatives et les distances se
  lisent encore, ce qui rend le mode acceptable au lieu d'en faire un
  interrupteur que personne n'actionne.

Un seul interrupteur pour les deux aurait fait croire à l'exploitant que son
poste est muet alors qu'il ne l'est qu'à moitié. **Promettre plus qu'on ne tient
est pire que ne rien promettre.**

### Deux implémentations d'une même formule

La ligne « IA embarquée sur l'appareil » se ferme en portant la détection EWMA
dans le navigateur. Ce n'est pas un modèle et il ne faut pas l'appeler ainsi :
c'est une moyenne mobile exponentielle, dont le mérite est justement de tenir
dans un navigateur sans rien télécharger.

**Le risque de ce portage n'est pas l'erreur, c'est la dérive.** Deux
implémentations de la même formule, dans deux langages, s'écartent en silence —
et l'écart ne se verrait que sur un écran : un point signalé côté serveur et pas
côté appareil, ou l'inverse. `ewma.test.ts` et `AnomalieEmbarqueeTest` fixent
donc les **mêmes trois nombres sur la même série** ; toucher l'un sans l'autre
fait échouer une des deux campagnes.

Le portage a d'ailleurs révélé une propriété du détecteur qui n'était écrite
nulle part : sur une série dont les premiers points sont presque identiques, la
variance connue est minuscule, et un écart de deux dixièmes dépasse trois
écarts-types. Le serveur se comporte ainsi depuis le SPRINT-06. Le noter dans le
test évite qu'on « corrige » un jour le portage pour un écart qui n'en est pas un.

### Ce que le lot ne ferme pas, et pourquoi

Le plan comptait huit lignes pour ce lot ; il en ferme six.

- **La réinitialisation de mot de passe**, laissée par le lot J, attend un
  serveur d'envoi — une pièce d'infrastructure, pas une ligne de code. Le realm
  livré n'en configure aucun, et afficher le lien quand même ferait attendre à
  l'utilisateur un message qui n'arrive jamais.
- **La logistique multi-sites**, reliquat du lot B, reste hors du produit : la
  charge d'équipe existe, la chaîne d'approvisionnement — le terrain de HiveOS —
  est un produit à elle seule.

### Le renoncement, écrit

Zümm n'aura pas de conversation en langage naturel tant que l'ADR-013 tient.
C'est un renoncement réel, et il vaut mieux l'écrire que le laisser découvrir :
le produit préfère une liste qui cite ses sources à une phrase qu'on ne peut pas
vérifier.

---

## 27. Note de révision — 05/09/2026, lot F₂ du plan de couverture

Dixième lot du [plan de couverture](PLAN-COUVERTURE-ECARTS.md) : quatre lignes
visées, **deux fermées, une passée à 🟡, une refusée**. Le compteur va de **130 à
132 sur 153**.

C'est le premier lot dont le résultat est inférieur à l'annonce, et il faut le
dire ainsi plutôt que de l'arrondir. La décision **D3** est tranchée par
[ADR-014](../roadmap/operationnel/06_decisions/ADR-014-capteurs-du-commerce.md) :
**on n'achète pas de matériel ; on livre ce qui se vérifie sans en avoir, et on
refuse le reste en le disant.**

Les quatre lignes ne se traitaient pas de la même façon, et les avoir groupées
était l'erreur du plan.

### L'anti-vol ne demandait aucun capteur — et c'était le plus cinglant

Le constat du §5 était le plus dur du document : « Zümm **cache** la position
pour protéger du vol ; Onibi **alerte** quand il survient. Les deux réponses sont
complémentaires, Zümm n'a que la première. »

Une ruche emportée ou renversée se voit dans la série de **poids** que l'API
ingère déjà. Ce qui manquait n'était pas une donnée, c'était une règle — et tout
le lot tient dans la phrase qui la rend utilisable :

> Une chute de vingt kilogrammes est une **récolte** si une récolte a été
> enregistrée ce jour-là sur cette ruche, et un **vol** sinon.

Sans cette vérification, la première miellée de l'année réveillerait l'alarme sur
tout le rucher. L'apiculteur la couperait — et une alarme qu'on coupe ne protège
plus de rien. C'est le même raisonnement qu'au SPRINT-22 sur le forçage de
carence : un mécanisme qu'on contourne vaut moins qu'un mécanisme qui prévoit le
cas normal.

Deux bornes accompagnent la règle, et elles sont honnêtes. La comparaison porte
sur **deux heures** : une passerelle qui pousse une fois par jour ne déclenchera
jamais cette alarme, parce qu'à cette cadence elle ne peut pas voir un vol. Et
l'alerte **ne se referme jamais toute seule** : une ruche volée ne revient pas, et
une alerte qui disparaîtrait parce que la balance repose sur le sol serait pire
que pas d'alerte du tout.

### Le Bluetooth : le profil standard, et rien d'inventé

Ce qui est vendeur, c'est « BroodMinder ». Ce qui est **vérifiable sans
matériel**, c'est le profil Bluetooth SIG : *Environmental Sensing* (`0x181A`) et
*Battery Service* (`0x180F`), dont les identifiants et les unités sont
normalisés. Tout capteur qui les implémente fonctionne, et l'écran le dit.

Le refus est aussi important que la livraison : écrire un décodeur pour la trame
propriétaire d'un fabricant dont personne n'a l'appareil reviendrait à **deviner
une structure de données**. Le résultat aurait l'apparence du support sans en
avoir la fiabilité — c'est le refus du réfractomètre au SPRINT-27, appliqué à un
autre sujet.

Ce qui se teste sans matériel, c'est justement ce qui peut se tromper sans se
voir : le **décodage**. Une température lue à l'envers donne des milliers de
degrés et se remarque ; une humidité inversée passerait pour plausible et
fausserait une série entière. D'où six tests sur les unités, le signe, la
caractéristique absente et la fermeture du GATT.

Web Bluetooth est **absent d'iOS Safari** et de Firefox. L'écran l'écrit au lieu
d'afficher un bouton inerte : la passerelle reste le chemin de tout le monde, le
Bluetooth direct est un raccourci pour ceux qui l'ont.

### Les intégrations nommées passent à 🟡, pas à ✅

Ce qui est livré sert **toutes** les intégrations sans en privilégier aucune :
`POST /api/mesures/lot`. Quarante ruches et quatre indicateurs relevés au quart
d'heure faisaient cent soixante requêtes, chacune avec sa poignée de main TLS et
sa clé d'idempotence ; elles en font une.

La ligne ne passe pas à ✅, et il ne faut pas qu'elle y passe : **personne n'a
vérifié une seule trame BroodMinder ici**. Le plan le recommandait déjà — un
partenariat, pas un développement — et le lot le confirme.

### L'analyse vidéo et acoustique est refusée

Elle demanderait un flux audio ou vidéo, donc un stockage binaire que le dépôt
n'a pas, et un modèle que l'[ADR-013](../roadmap/operationnel/06_decisions/ADR-013-ou-tourne-l-ia.md)
interdit de faire tourner ailleurs que sur l'appareil.

Le détail qui tranche : détecter une colonie orpheline au son suppose de savoir
ce qu'on écoute. Sortir un verdict d'un pic de fréquence sans donnée de
validation produirait un chiffre inventé sur une question que l'apiculteur ne
peut pas vérifier autrement qu'en ouvrant la ruche — c'est-à-dire exactement le
geste qu'on prétendait lui épargner.

### Deux défauts trouvés par le test, dont un antérieur au lot

Le test d'intégration écrit pour l'anti-vol en a révélé deux.

1. **`uq_alerte_ouverte` était à moitié corrigé** — le mien. La `V30` ajoutait
   une colonne `categorie` et un finder qui la lit, mais laissait l'index unique
   de la `V8` sur `(ruche, indicateur)`. L'insertion de l'alerte de vol échouait
   donc en 409 sur une ruche portant déjà une alerte de poids : précisément le
   cas qu'on venait d'ouvrir. Le finder et l'index disent désormais la même
   chose, ce qui est la règle du dépôt depuis l'ADR-001 — les deux couches
   doivent converger.
2. **`GET /api/mesures/alertes` ne pouvait réussir que sur une liste vide**, et
   ce depuis le SPRINT-06. La lecture vivait dans le contrôleur, hors de toute
   transaction ; `Alerte.ruche` étant `LAZY`, la sérialisation levait une
   `LazyInitializationException` dès qu'une alerte existait. **Aucun test n'avait
   jamais appelé cette route avec une alerte ouverte** — c'est le premier qui l'a
   fait qui l'a trouvée. La lecture est passée au service, comme partout ailleurs.

Le second est le plus instructif : une route couverte par un test qui ne la met
jamais dans l'état intéressant est une route non couverte.

---

## 28. Note de révision — 05/09/2026, lot H du plan de couverture

**Dernier lot** du [plan de couverture](PLAN-COUVERTURE-ECARTS.md) : neuf lignes
visées, **cinq fermées, deux passées à 🟡, une refusée** — la neuvième, le
millésime, était déjà ✅ et a été précisée. Le compteur va de **132 à 137 sur
153**, et le plan est clos.

Le §2 était le paragraphe le plus gênant du document, parce qu'il portait sur le
seul terrain que Zümm revendique par son nom : le SIG. BeeGIS y proposait des
couches d'occupation du sol, des surfaces par type de couvert et un historique de
rotation ; Zümm affichait **une tuile raster**. Six lignes ❌ d'affilée dans le
domaine annoncé comme le nôtre.

La décision **D1** est tranchée par
[ADR-015](../roadmap/operationnel/06_decisions/ADR-015-occupation-du-sol.md), et
elle tient en une phrase :

> **La donnée d'occupation du sol est accueillie, jamais interrogée.**

### Pourquoi le connecteur WMS était le mauvais réflexe

C'était la solution évidente, et elle était incompatible avec tout le reste du
produit. Demander en direct à un service tiers ce qu'il y a autour d'un rucher,
c'est lui envoyer les coordonnées du rucher — c'est-à-dire publier à un
prestataire exactement ce que `PolitiquePositions` masque depuis le SPRINT-12,
et ce que le mode local du SPRINT-30 vient tout juste de couper pour les tuiles.
Deux sprints à fermer une porte, un connecteur pour la rouvrir.

Le versement explicite coûte plus cher à l'utilisateur : rien n'arrive tout seul,
il faut aller chercher la couche et la déposer. L'écran l'écrit au lieu de faire
comme si la donnée manquante n'existait pas.

### La taxonomie est fermée, et c'est le vrai travail du lot

Dix classes, pas une de plus. Chaque source d'occupation du sol nomme les
siennes autrement — le registre parcellaire français, un cadastre communal et un
export CORINE ne parlent pas la même langue. Les laisser entrer telles quelles
aurait rendu deux exploitations incomparables et le mot « culture » ambigu dans
la même table.

L'ingesteur traduit donc vers le vocabulaire du produit, et une classe inconnue
fait échouer le versement **entier**. Accepter les polygones reconnus et jeter
silencieusement les autres aurait produit des surfaces fausses présentées comme
justes : le pire des deux mondes, puisque le total aurait quand même fait 100 %.

### Trois précautions de mesure, et elles changent le chiffre

1. **L'intersection est bornée au tampon avant d'être mesurée.** Une parcelle de
   colza qui déborde du rayon compterait sinon en entier, et « 60 % de cultures »
   finirait par désigner un département.
2. **Les surfaces sont géodésiques** (`geography`, pas `geometry`) : une
   projection plane dériverait de plusieurs pourcents, et le produit vise aussi
   le Maghreb.
3. **La réponse dit quelle part du cercle la couche décrit réellement.** Trente
   pour cent de couverture et soixante-dix pour cent de silence ne se lisent pas
   « 70 % de sol nu ». Sans ce champ, la table de surfaces serait une affirmation
   sur une zone dont on ne sait rien.

Le millésime obéit à la même exigence : `couvert_sol.millesime` est `NOT NULL`
dès la première ligne versée. Une occupation du sol de 2019 présentée comme
l'état du jour n'est pas une approximation, c'est une affirmation fausse — et
les parcelles tournent, c'est précisément l'objet du §13.

### La floraison observée ne remplace pas le calendrier déclaré

La `V21` portait déjà des périodes de floraison sur les ressources : du
déclaratif, qui prévoit. « Le colza fleurit en avril » se trompe trois années sur
dix, une gelée tardive décalant tout d'une quinzaine.

`floraison_observee` constate, et la réponse rend **les deux avec leur écart en
jours**. C'est la seule forme qui permette de dire « cette année, c'était en
avance de dix-sept jours » — un observé seul ne serait qu'une date de plus, et un
déclaratif seul reste ce qu'il était : une moyenne.

Une ressource ne fleurit qu'une fois par an, d'où `uq_floraison_annee`. Mais une
seconde saisie **complète** la première au lieu d'échouer : on note le début en
avril, le pic en mai, et refuser la seconde saisie obligerait à supprimer pour
corriger.

### Deux lignes restent partielles, et le disent à l'écran

- **Exposition aux zones traitées** — `distanceCultureM` rend la distance à la
  parcelle cultivée la plus proche. **Ce n'est pas une distance à une zone
  traitée** : aucune couche ouverte ne dit ce qui a été épandu ni quand.
  Présenter l'une pour l'autre serait une affirmation que rien ne fonde, et
  l'écran porte la phrase qui l'empêche.
- **Croisement santé × flore** — les deux moitiés se lisent désormais côte à
  côte, mais **aucun coefficient n'est calculé**. Sur la dizaine de ruchers d'une
  exploitation, une corrélation serait du bruit présenté comme un résultat. Même
  refus qu'au SPRINT-23 pour la note globale de comparaison d'emplacements.

### Et une refusée

Le **comptage de pollen** vient de réseaux d'aérobiologie nationaux, pas d'un
capteur de rucher. L'estimer à partir du couvert produirait un chiffre inventé
sur une donnée que l'apiculteur ne peut pas vérifier — le même refus que
l'acoustique au SPRINT-31 et que le réfractomètre au SPRINT-27.

Le *ground truthing* du §13 reste ❌ pour la raison inverse : il ne demande pas
un modèle mais un mécanisme de correction terrain — marquer une parcelle « à
confirmer » et engendrer la tâche de vérification. C'est du travail identifié,
pas un refus.

### Ce que le plan laisse derrière lui

Onze lots, du **A** au **J**, onze sprints applicatifs — du SPRINT-22 au
SPRINT-32 — et le compteur passé de **66 à 137 sur 153**. Restent **cinq 🟡**, **trois ❌** et **huit ⛔** — et sur les
trois ❌, deux sont des refus argumentés, un seul est du travail restant.

---

## 29. Note de révision — 09/09/2026, lot K du SPRINT-33

Le plan de couverture était **clos** au SPRINT-32 : onze lots, du **A** au **J**,
et un compteur passé de 66 à 137. Ce lot n'en est donc pas le douzième. Il prend
ce que la note précédente avait nommé en la fermant — sur les seize lignes
restantes, **une seule était du travail** ; les autres étaient des refus
argumentés ou du hors-périmètre. À la relecture, elles étaient trois : le *ground
truthing* du §13, resté ❌, et deux lignes 🟡 dont la moitié manquante était du
code et non une décision.

Le compteur va de **137 à 140 sur 153**. Restent **trois 🟡**, **deux ❌** et
**huit ⛔** — et pour la première fois depuis l'ouverture du document,
**aucune ligne de travail identifié**.

### 1. Ground truthing — le constat s'écrit à côté de la source, jamais dessus

BeeGIS conseille à ses utilisateurs de vérifier au printemps la culture
réellement semée. Le §13 en avait tiré une exigence en deux temps : toute donnée
environnementale porte son millésime, et peut être marquée « à confirmer », ce
qui engendre une tâche. Le millésime était acquis depuis la `V31` ; manquaient le
**doute** et le **constat**.

La tentation était d'écraser `classe` avec ce qui a été vu. Elle détruirait
exactement ce que le ground truthing établit — **que la couche se trompait**.
Écrasée, la parcelle raconte que le référentiel avait raison depuis le début, et
plus personne ne peut mesurer ce que vaut un millésime. Les deux colonnes
coexistent donc, comme la floraison déclarée de la `V21` et la floraison observée
de la `V31` coexistent : la source dit ce qu'elle croit, le terrain dit ce qui
est, et les lectures prennent le second dès qu'il existe
(`COALESCE(classe_constatee, classe)`).

De là, `GET /api/environnement/couvert/fiabilite` : combien de parcelles ont été
vérifiées, combien ont été **démenties**, combien attendent encore. C'est le
chiffre qui manquait pour répondre honnêtement à « peut-on se fier à cette
couche ? ». Une couche jamais vérifiée n'est pas une couche juste — c'est une
couche dont personne ne sait ce qu'elle vaut.

Deux détails de la règle valent d'être notés, parce qu'ils décident de son
utilité :

- **Une tâche par rucher, pas par parcelle.** « Vérifier la parcelle 17 843 »
  répété quarante fois rend la liste illisible en une matinée, et une liste qu'on
  n'ouvre plus ne rappelle rien.
- **Une proposition par saison.** La clé porte l'année : le ground truthing est un
  geste de printemps, on regarde ce qui a levé. Une clé fixe ne reproposerait
  jamais rien l'année suivante ; une clé au mois reproposerait la même tournée
  douze fois par an.

Le doute, lui, reste **un geste humain** : le déduire fabriquerait une tournée de
vérification que personne n'a demandée, sur des parcelles que personne ne
soupçonne.

### 2. Zones traitées — l'objection n'a pas été contournée, elle a été prise au mot

La ligne « évaluation de l'exposition aux zones traitées » était 🟡 avec un motif
explicite : `distanceCultureM` rend la distance à la parcelle *cultivée* la plus
proche, et **aucune couche ouverte ne dit ce qui a été épandu ni quand**.
Présenter l'une pour l'autre aurait été une affirmation que rien ne fonde.

Ce motif tient toujours, et c'est pourquoi la réponse n'est pas une couche mais
une **déclaration**. `zone_traitee` (`V32`) enregistre ce qu'un apiculteur peut
réellement obtenir : un voisin qui prévient, un épandage observé, un avis
officiel. La colonne `origine` **nomme la source** de chaque ligne, parce qu'une
zone « autre » ne vaut pas un avis de la protection des végétaux et que le
formulaire ne doit pas les présenter à égalité.

`GET /api/environnement/sites/{id}/exposition` en tire ce qu'on regarde avant de
partir travailler : les zones qui recoupent le rayon, la plus proche, et le
nombre encore **sous délai de rentrée**. La règle `RegleZoneTraiteeProche`
engendre une tâche sur une fenêtre de **quatorze jours** — la durée pendant
laquelle une mortalité devant la ruche peut encore se rattacher à l'événement.
Plus court laisserait passer une déclaration faite avec retard, le voisin ne
prévenant pas toujours le jour même ; plus long remplirait la liste de rappels
sans geste associé.

Et la tâche dit **d'aller regarder les planches d'envol**. Elle ne dit ni « vos
colonies sont exposées », ni « déplacez le rucher » : la déclaration ne porte ni
la dose, ni la dérive, ni le vent de ce jour-là. Affirmer l'exposition serait un
verdict inventé sur une question que l'apiculteur ne peut trancher qu'en
regardant ses ruches — le même refus qu'à l'analyse acoustique au SPRINT-31.

### 3. Feuille de chargement — la moitié logistique de la ligne « coordination »

La ligne 🟡 du §7 disait ce qui manquait sans ambiguïté : la charge par agent
existait depuis le SPRINT-23, « restent la logistique et la chaîne
d'approvisionnement, le terrain de HiveOS ».

`GET /api/plannings/chargement` répond à la question qu'on se pose sur le pas de
la porte : **qu'est-ce que je charge dans le véhicule ?** Deux lectures, et il
faut les deux — consolidée pour charger, par étape pour savoir où déposer.

**Le service n'invente rien, il assemble** : la tournée vient de
`PlanningService.tournee`, les tâches du registre, le stock de `consommable`. Le
seul ajout du lot est la colonne qui relie une tâche à ce qu'elle prélève — et
c'est elle qui manquait, pas un calcul. C'est le signe qu'une ligne 🟡 bien
formulée coûte peu à fermer : elle avait déjà identifié le verrou.

**Le manque est NOMMÉ, jamais corrigé.** Un consommable insuffisant s'affiche
comme tel ; répartir automatiquement le stock disponible reviendrait à décider
quelle ruche on saute, ce qui est une décision d'exploitation. Même refus que la
note globale de comparaison d'emplacements au SPRINT-23, à un autre étage.

### 4. Un quatrième constat, et il ne venait pas du plan : livré n'est pas atteignable

En-tête de ce document : *« Chaque ligne « couvert » cite le fichier qui le
prouve. »* Un audit du chemin complet — contrat OpenAPI → client TypeScript →
écran — a montré que la citation prouvait parfois moins qu'annoncé.

Le contrat porte **148 chemins et 211 opérations**. Toutes étaient joignables
depuis `api/client.ts` sauf une. Mais **vingt-cinq fonctions du client n'étaient
appelées par aucun écran** : la route existait, le type existait, la traduction
existait souvent — et rien, dans l'interface, n'y menait. Parmi elles, des
fonctions que ce document comptait déjà comme ✅ : les calculateurs de sirop et de
valorisation (§6), la conversion d'unités, les indices de colonie (§3), les
corrélations météo (§7), les statistiques du carnet (§3), la recherche de ruchers
proches d'un point (§1), l'historique de pesée par étage (§5), le versement d'une
couche et la saisie d'une floraison observée (§2).

Le verdict de ces lignes ne change pas — le code existait, et c'est ce que le
tableau affirme. Mais **la preuve était incomplète** : un service qu'aucun écran
n'atteint est livré pour un intégrateur, pas pour un apiculteur. Les
vingt-cinq sont branchées, plus la série brute des mesures qui n'avait même pas
de fonction client. Deux opérations restent volontairement hors de la PWA :
`/api/calendrier/{jeton}.ics` et `/api/flux/{jeton}` sont lues par un client de
calendrier et par un tiers — l'interface en **affiche l'URL**, elle ne les appelle
pas.

La leçon est à ranger à côté de celle du §10 sur les notifications : elle décrit
un invariant à protéger, pas un incident. **Une route sans écran ne se voit dans
aucun test** — ni le contrat, ni `parite.ts`, ni JaCoCo ne la signalent, puisque
tous trois vérifient que le code est juste, aucun que le code est atteint.

Et le défaut a un étage de plus, que ce même audit a d'abord manqué :
`photos/PanneauPhotos.tsx` — les photos attachées à un objet du parc, six cibles
depuis le SPRINT-28 — **n'est monté dans aucune fiche**. Ses trois fonctions de
client (`listerPhotosDe`, `attacherPhoto`, `detacherPhoto`) ont bien un appelant,
qui est ce composant ; le composant, lui, n'en a pas. Chercher les fonctions sans
appelant ne suffit donc pas : il faut remonter jusqu'à une route. La ligne du §1
reste ✅ — les photos de visite sont atteignables depuis `VisitesVue`, et le
`CHECK` qui impose exactement une cible est en base — mais **les cinq autres
cibles n'ont pas d'écran**. Où monter le panneau est une décision de fiche et de
rôle, pas une fermeture mécanique : elle est nommée ici plutôt que tranchée à la
sauvette.

### Ce qui reste, et pourquoi il reste

| | Ligne | Pourquoi |
|---|---|---|
| 🟡 | Intégrations nommées de capteurs (§5) | [ADR-014](../roadmap/operationnel/06_decisions/ADR-014-capteurs-du-commerce.md) : personne n'a vérifié une trame BroodMinder ici. C'est un partenariat, pas un développement |
| 🟡 | Croisement santé × flore (§2) | Les deux moitiés se lisent côte à côte ; sur la dizaine de ruchers d'une exploitation, un coefficient serait du bruit présenté comme un résultat |
| 🟡 | Réinitialisation de mot de passe (§7) | Le chemin est ouvert côté produit ; ce qui manque est un **serveur d'envoi**, que l'exploitant configure |
| ❌ | Comptage de pollen (§2) | Réseaux d'aérobiologie nationaux, pas un capteur de rucher — refusé au SPRINT-32 |
| ❌ | Analyse vidéo / acoustique (§5) | Refusée au SPRINT-31 : il faudrait un stockage binaire que le dépôt n'a pas, et un verdict que l'apiculteur ne pourrait vérifier qu'en ouvrant la ruche |

Aucune de ces cinq lignes n'attend du temps de développement : trois attendent
une décision ou une dépendance d'exploitation, deux ont été refusées avec leur
motif. Les **huit ⛔** du §9 sont inchangées.

Ce que le document devient à partir d'ici est donc différent de ce qu'il a été
pendant onze lots : il ne liste plus un reste à faire, il **documente un
périmètre** — y compris ses bords, qui sont la partie la plus utile à montrer.

---

## 30. Note de révision — 12/09/2026, deux des trois 🟡 laissés par le lot K

Le §29 listait trois lignes 🟡 restantes, dont il disait explicitement
qu'aucune « n'attend du temps de développement : trois attendent une décision
ou une dépendance d'exploitation ». Deux d'entre elles viennent de recevoir
cette décision. Le compteur va de **140 à 142 sur 153**. Ne reste 🟡 que
l'intégration nommée de capteurs du commerce (§5, ADR-014) — un partenariat,
toujours pas un développement.

**Croisement santé × flore (§2).** Le refus tenait en une phrase depuis le
SPRINT-32 : « sur la dizaine de ruchers d'une exploitation, une corrélation
serait du bruit présenté comme un résultat ». L'objection portait sur
l'interprétation, pas sur le calcul — et c'est exactement ce que `Coefficient`
sait déjà refuser pour la corrélation météo depuis le SPRINT-22. Extrait dans
sa propre classe pour être partagé entre les deux corrélations, il porte le
même seuil d'échantillon et la même règle : en dessous de douze RUCHERS (pas
colonies — apparier chaque colonie aurait fait entrer le même couvert
plusieurs fois dans le calcul), le coefficient sort nu. `CouvertSolRepository.partsParSite`
croise le couvert de tous les ruchers en une seule requête PostGIS plutôt
qu'une par rucher, prouvée contre une vraie base par `EnvironnementSigIT`
(la version simulée du service ne pouvait pas garantir que la requête groupée
elle-même était juste). Onglet dédié dans `TableauxVue.tsx`, avec
l'avertissement propre à cette corrélation : dix classes testées sur le même
échantillon, sans correction de comparaison multiple.

**Réinitialisation de mot de passe (§7).** Le code produit n'a pas changé
depuis le SPRINT-25 ; ce qui manquait était bien, comme annoncé, un serveur
d'envoi, pas un développement. Mailpit comble ce manque en développement, un
`smtpServer` paramétrable en production. La vérification a buté sur un défaut
qui n'était dans aucun fichier committé : Keycloak n'importe
`realm-zumm(.dev).json` qu'à la création du realm en base, jamais aux
démarrages suivants, et un volume Postgres antérieur au sprint continuait de
tourner sur l'ancien realm — sans `smtpServer`, sans `resetPasswordAllowed` —
après une reconstruction complète des images, sans qu'aucune erreur ne le
signale au démarrage. Documenté dans `infra/keycloak/README.md`, avec le geste
qui répare. Vérifié pour de vrai une fois le realm à jour : un envoi déclenché
arrive dans Mailpit, adressé correctement, et le lien qu'il contient ouvre une
vraie page Keycloak plutôt qu'une erreur.

Trace complète de la séquence — Docker débloqué après plusieurs tentatives,
diagnostic du realm, vérification — dans `docs/JOURNAL.md`, entrées du
12/09/2026.
