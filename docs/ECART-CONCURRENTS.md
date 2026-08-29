# Écart fonctionnel — Zümm face à douze outils apicoles du marché

> Analyse du 18/08/2026, **revérifiée contre le code le 25/08/2026** (périmètre
> SPRINT-19 inclus — voir la note de révision en fin de document).
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
| Adresse postale du rucher | ❌ | `Site` ne porte que des coordonnées — pas de rue/CP/ville/pays |
| Type de rucher, exposition, miellées, sources de nectar | ❌ | Aucun de ces champs sur `Site` ; ApiManager les demande dès la création |
| Historique d'emplacement / transhumance | 🟡 | `Site.dateMiseEnOeuvre`, `dateDemenagement`, `dateCloture` existent ; aucune vue d'historique ni planification de transport |
| Fiche ruche (type, cadres, hausses) | ✅ | `Ruche.modele` + `Compartiment` (`CORPS`/`HAUSSE`, `nbCadres`) |
| Référentiel de types de ruche (Langstroth, Warré, Dadant, Top-Bar) | ❌ | `Ruche.modele` est un **texte libre** : ni liste, ni statistique par type. Quatre concurrents le posent en référentiel |
| Cycle de vie de la ruche | ✅ | `EtatRuche` : créée → peuplée → active → en division → en collecte → clôturée |
| Archivage plutôt que suppression (morte, fusionnée, vendue) | 🟡 | `CLOTUREE` couvre l'archivage, mais **la cause n'est pas distinguée** — indiscernables en statistiques |
| Couleur de ruche (repérage visuel terrain) | ❌ | Aucun champ ; coût quasi nul, gain terrain réel |
| Origine de la colonie (essaim, division, nucléus) | ❌ | Pas de champ ; `RaisonVisite.DIVISION` trace l'acte, pas la filiation |
| **Enregistrement d'une division comme événement de plein droit** | 🟡 | `RaisonVisite.DIVISION` et `EtatRuche.EN_DIVISION` disent qu'une division a eu lieu ; ni la ruche fille, ni le nombre de cadres transférés ne sont saisis. HiveBook en fait une entité |
| **Capture d'essaim** | ❌ | Aucune notion. HiveBook l'enregistre au même rang qu'une division |
| Photos rattachées | 🟡 | `Photo` est liée à **une visite uniquement** (`Photo.visite`, `optional = false`). Ni ruche, ni reine, ni récolte, ni matériel |
| Recherche globale (ruche, site, agent…) | ❌ | La palette `Ctrl/⌘ + K` (`ui/palette.tsx`, filtrage par sous-séquence) cherche parmi les **dix-neuf écrans**, jamais parmi les objets métier. Chaque vue garde ses filtres ; aucune recherche transverse sur les données |
| Sync calendrier iCal (Google/Outlook/Apple) | ❌ | `CalendrierService` produit une matrice agents × ruches pour l'écran, pas un flux `.ics` |

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
| Comparaison de plusieurs emplacements candidats (transhumance) | 🟡 | `SiteController` compare les sites **entre eux dans l'espace** ; jamais sur leur environnement ou leur potentiel |
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

C'est le domaine où l'écart de **granularité** est le plus net. Zümm modélise
**la visite** ; les onze carnets concurrents modélisent **l'observation**.
HiveTracks pousse le raisonnement à son terme : une inspection y est une
cinquantaine de points cochables, donc analysables.

| Fonctionnalité concurrente | Zümm | Preuve / manque |
|---|---|---|
| Inspection datée, horodatée, par ruche | ✅ | `Visite` (date, heure, durée, agent, raison) |
| Planification et **approbation** des visites | ✅ | `Planning` + `StatutPlanning` (proposé/approuvé/refusé) — **aucun des douze ne l'a** |
| Rapport de visite PDF | ✅ | `RapportVisitePdfService`, `GET /api/visites/{id}/rapport.pdf` |
| Saisie par cases à cocher (~50 points analysables) | ❌ | Formulaire figé dans `VisitesVue.tsx`, dominé par du texte libre |
| Force de la colonie | 🟡 | `EffectifQualitatif` (faible/moyen/fort) — les concurrents utilisent une échelle exploitable en courbe |
| Tempérament / agressivité | ❌ | Aucun champ. Six catalogues le demandent |
| État du couvain (œufs, operculé, motif de ponte) | ❌ | Noyé dans `Visite.constatations` (texte libre) — **non analysable** |
| Réserves miel / pollen, contenu des cadres | ❌ | Idem, texte libre |
| Cellules royales et cause (essaimage / supersédure / urgence) | ❌ | Absent — c'est pourtant le signal d'essaimage le plus actionnable |
| Reine vue / statut à chaque visite | 🟡 | `SuiviReine` est un événement **séparé** de la visite, pas une case du formulaire |
| État sanitaire | 🟡 | `EtatSante` (bon/moyen/mauvais) — un seul curseur, sans maladie ni ravageur nommé |
| Maladies et ravageurs nommés (loque, petit coléoptère…) | ❌ | HiveTracks les référence ; Zümm n'a qu'un curseur à trois positions |
| **Suivi du varroa** (méthode, comptage, taux d'infestation calculé) | ❌ | `grep -ri varroa` ne renvoie que **deux lignes de prose**, toutes deux dans `infra/seed-demo.sql` : une `constatation` de visite en texte libre et un libellé de tâche. Aucune table, aucune colonne, aucune énumération — le jeu de démonstration montre le besoin que le modèle ne porte pas. **Huit des douze catalogues le nomment**, plusieurs en font un module entier avec calculateur |
| **Traitements sanitaires** (produit, dose, cible, durée, délai de carence) | ❌ | Seulement `RaisonVisite.TRAITEMENT` — on sait *qu'on* a traité, jamais *avec quoi* ni *combien*. **Bloquant pour un registre sanitaire réglementaire** |
| Référentiel de traitements pré-renseigné | ❌ | HiveTracks en embarque plus de vingt ; c'est ce qui rend la saisie tenable au rucher |
| Délai de carence / date de retrait avant récolte | ❌ | Absent, et c'est un point **réglementaire**, pas un confort |
| **Nourrissements** (type, quantité, motif) | ❌ | Idem : `RaisonVisite.NOURRISSAGE` seulement |
| Ordonnances vétérinaires | ❌ | Absent |
| **Score de santé calculé par colonie** | ❌ | APIGO, BuzzWise, HiveTracks et HiveBook le calculent. Zümm n'agrège aucun indice par ruche |
| **Score de risque d'essaimage** | ❌ | `SuiviReine` accepte le statut `essaimee` — **constat a posteriori**, pas prédiction |
| **Recommandations automatiques / tâches générées** | ❌ | `TacheService` est un CRUD pur (`creer`, `lister`, `mettreAJour`, `supprimer`). Aucun événement métier ne crée de tâche. **Six catalogues sur douze** génèrent des conseils ou un plan de tâches |
| Rappels programmés (retrait de traitement, contrôle de ponte à J+7) | ❌ | `GET /api/taches/rappels` liste les échéances **saisies à la main** ; rien n'en pose |
| Modèles / gabarits d'inspection réutilisables, champs activables | ❌ | Aucun paramétrage |
| Météo attachée à l'observation | 🟡 | `MeteoController` + `OpenMeteoFournisseur` existent, mais la météo n'est **pas figée sur la visite** — donc pas de corrélation historique |

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
| Notes vocales simplement enregistrées, sans transcription | ❌ | ApiManager et BeeKeepPal se contentent de stocker l'audio ; même ce repli minimal n'existe pas |
| **Fiches d'inspection imprimables** (saisie au stylo, saisie différée) | ❌ | `RapportVisitePdfService` produit un compte rendu **après** la visite. La fiche vierge à emporter est l'inverse — et c'est la réponse la plus économique au problème des gants |
| Identification par **QR code** sur la ruche | 🟡 | `RecoltesVue.tsx` génère un QR de **lot de récolte** ; pas de QR par ruche, pas de planche d'étiquettes |
| Identification par **NFC** | ❌ | Aucun appel Web NFC (indisponible sur iOS Safari : le QR reste le socle, le NFC un confort) |
| **Interventions groupées / scan en masse** sur tout un rucher | ❌ | Toutes les mutations sont unitaires (`POST /api/visites`, `/api/recoltes`…). Traiter 40 ruches = 40 saisies. **Écart terrain le plus coûteux** |
| Saisie hors ligne au rucher | ✅ | `frontend/src/offline/file.ts` — file de mutations persistée, rejeu à l'événement `online`, **clé d'idempotence stable** (`FiltreIdempotence`) |
| **Consultation** hors ligne des données | ❌ | Choix explicite : `vite.config.ts` met `/api` en `navigateFallbackDenylist`. La coquille et les tuiles sont hors ligne, **les données non**. Six catalogues en font un argument de vente ; c'est le reproche n°1 fait à HiveTracks, BeeKube et BuzzWise |
| Résolution de conflits multi-agents hors ligne | ❌ | Limite documentée dans `offline/file.ts` |
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
| Poids **par hausse** | ❌ | `MesureId` = (`rucheId`, `typeIndicateur`, `instant`) : la granularité s'arrête à la ruche, alors que `Compartiment` distingue déjà corps et hausses |
| Alertes à seuils sur capteurs | ✅ | `SeuilAlerteService` (hystérésis anti-rebond), `Alerte`, `NotificationAlerteService` |
| Notification e-mail à l'ouverture d'une alerte | ✅ | `NotificationAlerteService` — **un seul destinataire par message** (`setTo`), jamais de liste. Voir la leçon n°1 du §10 |
| Prévision de récolte | ✅ | `PrevisionRecolteService` — régression linéaire sur la série de poids, projection 7 j |
| Détection d'anomalie par IA | ✅ | `MoteurAnomalie` (port) + `ClientAnomalieIA` → microservice Python. HiveBook fait la même chose sur l'appareil |
| Partage d'un flux de télémétrie entre utilisateurs | 🟡 | Le partage passe par l'appartenance au même tenant (`Agent`, RBAC) ; aucun partage inter-exploitations comme chez BeeLog Digital |
| **Analyse vidéo / acoustique à l'entrée** (comptage de trafic, perte de reine) | ❌ | Onibi seul. Le port `MoteurAnomalie` est prêt à recevoir un second moteur — l'architecture ne s'y oppose pas, la donnée manque |
| **Actionneurs à distance** (portes robotisées, protection anti-frelon) | ⛔ | Zümm observe, il ne commande pas. Piloter un actionneur engage la sécurité de la colonie et suppose du matériel propriétaire |
| **Alarme anti-vol / détection de basculement** | ❌ | Le vol de ruches est pourtant la menace qui justifie `PolitiquePositions` (voir sa javadoc). Zümm **cache** la position pour protéger du vol ; Onibi **alerte** quand il survient. Les deux réponses sont complémentaires, Zümm n'a que la première |
| Supervision de l'état des batteries des capteurs | ❌ | Aucun indicateur d'alimentation. BeeLog et Onibi se font tous deux reprocher les pannes de batterie silencieuses |

---

## 6. Production, stock, matériel et commerce

| Fonctionnalité concurrente | Zümm | Preuve / manque |
|---|---|---|
| Récolte par ruche, quantité, type de miel, lot | ✅ | `Recolte` + `RecolteController` |
| Traçabilité du lot | ✅ | `GET /api/recoltes/tracabilite/{lot}` |
| Conditionnement et mention d'origine réglementaire | ✅ | `LotConditionnement` + `LotConditionnementService` — directive (UE) 2024/1438, consolidation par pays, somme à 100 %. **Aucun des douze ne l'implémente à ce niveau** |
| Chaîne récolte → maturation → mise en pot, DLUO/DDM | 🟡 | Récolte et conditionnement existent ; l'étape de maturation et les dates de péremption non |
| **Produits autres que le miel** (cire, pollen, propolis, gelée royale, essaims, reines) | ❌ | `Recolte.quantiteKg` + `typeMiel` : le modèle **présuppose du miel**. Quatre concurrents ventilent par produit |
| Comparaison des récoltes année par année | ❌ | Les agrégats sont sur période glissante, jamais saison contre saison |
| Récolte sur tout un rucher en une saisie | ❌ | Unitaire par ruche |
| **Inventaire du matériel** (hausses, cadres, extracteurs) et état d'entretien | ❌ | `LotConditionnement` conditionne, il ne tient pas d'inventaire. BeeKeepPal, HiveBook et APIGO l'ont |
| Stock de consommables avec seuils de réapprovisionnement | ❌ | Absent |
| Comptabilité : dépenses, recettes, rentabilité par ruche | 🟡 | `SyntheseService` calcule un **ROI global** depuis `ConfigZumm.ini` (`[economie]`) ; pas de coûts réels, pas de ventilation par ruche ou par rucher |
| Scan de reçus, rapports fiscaux | ⛔ | HiveBook le fait par IA embarquée ; hors périmètre (voir §9) |
| Gestion clients, fournisseurs, ventes | ⛔ | BeeKeepPal, APIGO, ApiManager ; hors périmètre |
| **Calculateurs apicoles** (sirop 1:1 et 2:1, infestation varroa, prix du miel, réfractomètre) | 🟡 | `ConversionController` couvre les **unités de masse**. APIGO et BuzzWise en font une batterie entière — c'est peu coûteux et très visible |

---

## 7. Élevage, génétique, pilotage et données

| Fonctionnalité concurrente | Zümm | Preuve / manque |
|---|---|---|
| Fiche reine (race, année, marquage, statut, origine) | ✅ | `SuiviReine` (statut, couleurMarquage, anneeNaissance, race), `ReineController`, `ReinesVue.tsx` |
| Historique des reines d'une ruche | ✅ | `SuiviReine` est événementiel — l'historique est natif |
| Ailes clippées, fournisseur, ruche-mère | ❌ | Champs absents |
| **Généalogie / arbre de lignées** | ❌ | Pas de lien reine → reine mère. BeeKube, APiLOG et HiveBook en font un argument fort |
| Dates de greffage, suivi d'élevage | ❌ | Absent |
| **Index génétique multicritère** (hygiène, résistance varroa, douceur) | ❌ | Dépend d'abord des critères d'inspection manquants (§3) |
| Photos de reine et de motif de ponte | ❌ | `Photo` ne se rattache qu'à une visite |
| **Registre d'élevage réglementaire** (PDF / Excel) | ❌ | Seul le rapport de visite est en PDF. Dépend des traitements et nourrissements structurés (§3). **Cinq concurrents le génèrent automatiquement** |
| Rapports de conformité / certification bio | ❌ | HiveBook et APIGO les produisent |
| Déclaration NAPI, déclaration annuelle des ruches | ⛔ | Dispositifs nationaux français |
| Multi-utilisateurs et rôles | ✅ | `Agent` + `RoleAgent` (apiculteur/superviseur/responsable/admin), `InvitationController`, RBAC Keycloak (`SecurityConfig.matriceRbac`). **Plus fin** que les rôles d'ApiManager et sans plafond d'accès, là où BeeKeepPal limite à trois |
| Lisibilité des droits pour l'utilisateur | ✅ | SPRINT-19 : la navigation masque les écrans fermés au rôle (`routage/routes.ts`, `ROLES_ONGLET`), `InterditVue` explique le refus au lieu d'un 403 nu, et `PermissionsVue` publie la matrice complète — *voici les serrures, voici qui a les clés*. **Aucun des douze ne montre ses règles d'accès à ses utilisateurs** |
| Compte en libre-service (création, consultation) | ✅ | `ConnexionVue` (connexion **et** inscription depuis l'application), `CompteVue` : utilisateur, exploitation et rôles lus dans la session serveur, aucun jeton en mémoire du navigateur (ADR-006) |
| **Réinitialisation de mot de passe en libre-service** | ❌ | `RecuperationVue` **aiguille vers le responsable d'exploitation** : aucun serveur d'envoi n'est configuré dans `infra/keycloak/realm-zumm{,.dev}.json`, donc aucun courriel ne partirait. Onze des douze étant des services en ligne (§8), ils portent tous ce chemin ; c'est le maillon manquant du parcours de compte |
| Cloisonnement des données entre exploitations | ✅ | Multi-tenant + RLS PostgreSQL, `TenantFilter`, `tenant_id` obligatoire dans le JWT. **Aucun des douze ne le documente** |
| Piste d'audit | ✅ | `AuditEntree`, `AuditAspect`, `AuditController`, `AuditVue.tsx` |
| Tâches avec échéance | ✅ | `Tache` + `GET /api/taches/rappels` |
| Priorité, type et notification de tâche | 🟡 | `Tache` n'a que `libelle`, `ruche`, `agent`, `echeance`, `faite` |
| Tableau de bord de synthèse | ✅ | `TableauDeBordController` : calendrier, production, alertes sanitaires, synthèse, prévisions |
| **Vision à trois niveaux** (ruche → rucher → exploitation) | 🟡 | Le niveau ruche et le niveau exploitation existent ; **le niveau rucher est le maillon faible** — pas d'agrégat par site. C'est l'ossature revendiquée par BeeKeepPal |
| Tournée optimisée du jour | ✅ | `OptimiseurTournee` (plus proche voisin + 2-opt), `GET /api/plannings/tournee` — **APIGO l'annonce, Zümm l'a** |
| Coordination d'équipes terrain, logistique multi-sites | 🟡 | `Planning` + `OptimiseurTournee` + portée par affectation couvrent la coordination ; ni logistique, ni chaîne d'approvisionnement (le terrain de HiveOS) |
| Assistant / mentor IA, briefing quotidien | ❌ | L'IA de Zümm surveille des séries de capteurs ; elle ne lit pas l'historique d'une colonie et ne propose rien |
| **Météo prévisionnelle** | ✅ | `OpenMeteoFournisseur` : `current=` + `daily=` dans **un seul appel**, `timezone=auto`, horizon borné à 16 j, `GET /api/meteo?siteId=&jours=`. Repli simulation déterministe hors ligne |
| Tâches programmées selon la météo | ❌ | La prévision est là, le déclenchement non (HiveBook le fait) |
| Graphiques | ✅ | `ui/graphiques.tsx` (SVG maison, ADR-007) |
| Corrélation météo ↔ production | ❌ | Les deux sources existent, le croisement non (§3, météo non figée sur la visite) |
| Export CSV | ✅ | `ExportService` (CSV RFC 4180 + TXT), `GET /api/export/{visites,ruches}` |
| Export XLSX | ❌ | Formats CSV/TXT uniquement |
| Export du reste (récoltes, mesures, lots, tâches…) | ❌ | Deux entités exportables sur dix-neuf. **Les douze catalogues promettent tous l'export intégral** |
| Bilan annuel PDF | ❌ | Absent |
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

**Les écarts qui comptent, par coût d'opportunité décroissant :**

1. **Traitements, nourrissements et varroa comme entités de plein droit.** Ces
   trois actes ne sont qu'une valeur de `RaisonVisite` : ni produit, ni dose, ni
   délai de carence. Socle de tout le reste — registre d'élevage, corrélations,
   index génétique, score de santé. **Huit des douze catalogues nomment le
   varroa ; le dépôt ne le nomme que dans la prose du jeu de démonstration
   (`infra/seed-demo.sql`) — jamais dans une table, une colonne ou une
   énumération.**
2. **Interventions groupées et scan en masse.** Toutes les mutations sont
   unitaires. Sur un rucher de quarante ruches, un traitement se saisit quarante
   fois — le produit devient inutilisable à l'échelle qu'il prétend viser.
3. **Observations d'inspection structurées** (couvain, réserves, cellules
   royales, tempérament, maladies nommées). Aujourd'hui en texte libre dans
   `Visite.constatations` : rien n'en est analysable, ce qui plafonne tout le
   module analytique. HiveTracks montre la cible : des cases, pas de la prose.
4. **Tâches et rappels engendrés par les événements.** Six catalogues
   recommandent ou programment ; `TacheService` ne fait qu'enregistrer. Le
   retrait d'un traitement après son délai de carence est le cas d'école : la
   règle est mécanique, la valeur immédiate.
5. **Couche d'occupation du sol et calendrier de floraison.** Le seul axe où un
   concurrent (BeeGIS) joue sur le terrain revendiqué par Zümm — le SIG. PostGIS
   est déjà là ; il manque la donnée d'entrée et le choix d'un référentiel
   portable (§2).
6. **Saisie vocale**, précédée de la **fiche imprimable** — même problème, deux
   ordres de grandeur d'écart en coût.
7. **Généalogie des reines.** Trois concurrents en font leur argument central ;
   c'est une clé étrangère réflexive sur `SuiviReine` et une vue d'arbre.

**Écarts à faible coût, à prendre en même temps :**

| Écart | Coût |
|---|---|
| Fiches d'inspection vierges imprimables | Le moteur PDF existe (`RapportVisitePdfService`) |
| Calculateurs (sirop 1:1 et 2:1, infestation varroa, prix du miel) | Fonctions pures, testables sans base ; `ConversionController` donne le patron |
| Agrégats au niveau **rucher** | Le maillon manquant de la vision à trois niveaux ; les requêtes par site existent déjà |
| Rayons de butinage réglables | `rayonsKm` est déjà une propriété de `CarteFond` ; il manque le contrôle d'interface |
| Photos rattachées à une ruche, une reine, une récolte | `Photo.visite` en `optional = false` à relâcher |
| QR code par ruche et planche d'étiquettes imprimable | La bibliothèque QR est déjà là (`RecoltesVue.tsx`) |
| Référentiel de types de ruche, couleur, cause de clôture | Trois colonnes et une énumération |
| Priorité et catégorie sur les tâches | Deux colonnes |
| Export CSV étendu aux autres entités, puis XLSX | `ExportService` existe et n'expose que deux entités sur dix-neuf |
| Météo figée sur la visite | La prévision est livrée ; débloque la corrélation météo ↔ production |
| Indicateur d'alimentation des capteurs | Un `TypeIndicateur` de plus ; évite la panne silencieuse reprochée à BeeLog et Onibi |

**Un écart à trancher explicitement : la consultation hors ligne.** Le
commentaire de `vite.config.ts` refuse de mettre `/api` en cache — une mesure de
capteur périmée induirait l'apiculteur en erreur. L'argument est juste pour les
mesures ; il l'est beaucoup moins pour la liste des ruches d'un rucher, qui ne
change pas dans la journée. **Six des douze concurrents mettent la consultation
hors ligne en tête de leur argumentaire**, et c'est le reproche n°1 fait à
HiveTracks, BeeKube et BuzzWise. Un cache sélectif par entité, avec date de
fraîcheur affichée, réconcilierait les deux exigences — et rejoint la leçon n°3
du §10. **Le §13 tranche ce point** : trois concurrents font aujourd'hui exécuter
ce préchargement à la main par leurs utilisateurs, ce qui désigne la forme juste
— un « emporter ce rucher hors ligne » déclenché par l'apiculteur, borné et daté,
plutôt qu'un cache automatique.

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
| Traitements, nourrissements, varroa | ● 3 tables | ● 3 tranches | ● | ● 3 vues | ● | — |
| Observations d'inspection structurées | ● colonnes ou table fille | ● `Visite` + DTO | ● | ● formulaire à cases | ● nombreux libellés | — |
| Interventions groupées / scan en masse | — | ● routes de lot, transaction, idempotence | ● | ● sélection multiple | ◐ | — |
| Tâches et rappels engendrés par événement | ◐ colonnes `priorite`, `type`, `origine` | ● règles + événements applicatifs | ● | ◐ affichage existant | ◐ | — |
| Score de santé / risque d'essaimage | — dérivé | ● service d'agrégation (ou `ia-service`) | ● | ● | ◐ | ◐ si l'IA s'en mêle |
| Registre d'élevage réglementaire | — | ● service PDF | ● | ◐ un bouton | ◐ | — |
| Couche d'occupation du sol | ● table + index GiST | ● intersection PostGIS | ● | ● couche MapLibre + légende | ◐ | ● ingestion et volume |
| Calendrier de floraison / miellées | ● | ● | ● | ● | ● | — |
| Généalogie des reines | ● FK réflexive sur `suivi_reine` | ● | ● | ● vue d'arbre SVG | ◐ | — |
| Saisie vocale | — | — si transcription locale, ● si serveur | ◐ | ● Web Speech / `MediaRecorder`, permissions PWA | ● 3 langues de reconnaissance | ◐ |
| Fiches d'inspection imprimables | — | ● un service PDF de plus | ◐ | ◐ un bouton | ◐ | — |
| Consultation hors ligne | — | ◐ en-têtes de fraîcheur | — | ● service worker, `vite.config.ts`, cache par entité | ◐ | — |
| Bluetooth direct vers les capteurs | — | — l'API d'ingestion existe | — | ● Web Bluetooth (absent d'iOS Safari) | ◐ | — |
| Indicateur d'alimentation des capteurs | ◐ valeur d'énumération | ◐ seuils | ◐ | ◐ | ◐ | — |
| Agrégats au niveau **rucher** | — | ● requêtes + DTO | ● | ● | ◐ | — |
| Calculateurs apicoles | — | ● fonctions pures + contrôleur | ● | ● | ● | — |
| Export étendu, puis XLSX | — | ● `ExportService` (+ dépendance `pom.xml`) | ◐ | ◐ boutons | ◐ | — |
| Photos hors visite | ● colonnes + FK composites | ● | ● | ● | — | — |
| Type de ruche, couleur, cause de clôture | ● colonnes + énumération | ● | ● | ● | ● | — |
| Rayons de butinage réglables | — | — | — | ● un contrôle d'interface | ◐ | — |
| QR par ruche, planche d'étiquettes | — | ◐ endpoint PDF | ◐ | ● | ◐ | — |
| Météo figée sur la visite | ● colonnes sur `visite` | ● | ● | — | — | — |
| Actionneurs, anti-vol, vidéo à l'entrée | ⛔ | ⛔ | ⛔ | ⛔ | ⛔ | ⛔ matériel |

### Lecture par couche

**Base de données (`db/migration`, prochaine version `V19`).** Neuf écarts
exigent une migration, et **c'est la couche qui commande le calendrier** : rien
ne se code au-dessus d'une table qui n'existe pas. Les trois tables sanitaires
(traitement, nourrissement, comptage varroa) sont à poser d'un bloc — même forme
(ruche, date, agent, produit, quantité), même politique RLS ; les séparer
multiplierait les revues de sécurité pour rien. Un piège propre au dépôt : la
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

1. **Une seule migration `V19`** : les trois tables sanitaires, les colonnes
   d'observation, la météo figée sur la visite et les colonnes bon marché
   (couleur, cause de clôture, type de ruche). Une migration, une revue RLS.
2. **Les tranches back correspondantes**, puis **une seule régénération de
   contrat** pour l'ensemble.
3. **Le front en second temps** : formulaire d'inspection à cases, vues
   sanitaires, interventions groupées.
4. **En parallèle et sans dépendance** — donc parallélisables dès maintenant :
   les rayons réglables, la fiche imprimable, les calculateurs, la consultation
   hors ligne. Aucun des quatre n'attend une migration.
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
| « Ouvrez les fiches de vos ruchers **avant** d'entrer en zone blanche » | BeeKube, BuzzWise, HiveTracks | **Emporter un rucher hors ligne** : préchargement explicite, déclenché par l'utilisateur, avec date de fraîcheur affichée et purge à la synchronisation | ❌ | front |
| « Notez au stylo ou en mémo vocal, saisissez au retour sur ordinateur » | ApiManager, BuzzWise, HiveTracks | **Brouillon de visite reprenable** sur un autre appareil : la saisie commencée au rucher se termine à la maison | ❌ | back + front |
| « Exportez en CSV/PDF chaque mois, ou en fin de saison » | ApiManager, HiveSense, BuzzWise, HiveTracks, HiveBook | **Export intégral** de toutes les entités + **rappel d'archivage saisonnier** engendré automatiquement | ❌ | back + front |
| « Emportez une batterie externe, fermez les applications gourmandes » | BeeKube, HiveSense, HiveBook | **Mode économie** assumé : rendu léger, pas de WebGL, pas de rafraîchissement de fond | 🟡 | front |
| « Collez des QR codes / posez des puces NFC plutôt que des autocollants » | APiLOG, ApiManager, APIGO | **Étiquetage durable** : QR par ruche, planche imprimable, identifiant court lisible à l'œil nu quand le scan échoue | 🟡 | back + front |
| « Équipez d'abord vos ruches souches ou vos ruchers stratégiques » | BeeLog Digital, Onibi | **Marquage de priorité** sur la ruche et le rucher, repris dans le tableau de bord et la tournée | ❌ | DB + back + front |

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
  voient exactement le même produit. Et le gardiennage s'arrête à l'onglet ;
  `routes.ts` note lui-même que le masquage des actions (« le bouton *Nouveau*
  qui doit disparaître, pas l'onglet ») **reste à faire**.

### Contournements propres à un outil, mais transposables

| Contournement conseillé | Qui | Exigence pour Zümm | Verdict | Couche |
|---|---|---|:--:|---|
| « Vérifiez sur le terrain au printemps la culture réellement semée » (*ground truthing*) | BeeGIS | Toute donnée environnementale porte son **millésime** et peut être marquée « à confirmer », ce qui engendre une tâche de vérification | ❌ | DB + back + front |
| « Servez-vous de l'historique de rotation sur 3 à 5 ans » | BeeGIS | **Comparaison saison contre saison**, et non agrégats sur période glissante | ❌ | back + front |
| « Vérifiez la couverture réseau du site **avant** d'installer » | Onibi | Champ **couverture réseau** sur le `Site`, au même titre que l'exposition — il conditionne ce qu'on peut y déployer | ❌ | DB + back + front |
| « Vérifiez le niveau de charge des capteurs via le tableau de bord » | BeeLog Digital, Onibi | `TypeIndicateur.ALIMENTATION` + seuil d'alerte : `SeuilAlerteService` sait déjà faire le reste | ❌ | DB + back + front |
| « Nettoyez les optiques, grattez la propolis sur les glissières » | Onibi | **Plan de maintenance du matériel** : tâches récurrentes attachées à un équipement, pas à une ruche | ❌ | DB + back + front |
| « Configurez une seule ruche *test* avant de basculer l'exploitation » | BeeLog Digital | **Jeu de démonstration réversible** — `infra/seed-demo.sh` existe côté exploitant, rien côté utilisateur | 🟡 | infra + front |
| « N'activez les modules avancés qu'au moment où vous en avez besoin » | APIGO, HiveTracks | **Interface progressive** : les modules avancés ne s'imposent pas à l'apiculteur de trois ruches | 🟡 | front |
| « Ajoutez l'application à l'écran d'accueil avant de partir au rucher » | APIGO | **Invite d'installation contextuelle** : `pwa.ts` gère la mise à jour (stratégie *prompt*), jamais l'installation (`beforeinstallprompt` absent) | ❌ | front |
| « Utilisez le champ Notes libre pour préciser l'action à mener » | HiveTracks | **Action attachée à l'observation** : une case cochée engendre la tâche correspondante. Le champ libre ne doit pas être la soupape du modèle | ❌ | DB + back + front |
| « Désactivez les notifications e-mail pour éviter la fuite d'adresses » | BeeKeepPal | **Réglage par utilisateur** : `zumm.notifications.email.enabled` est une propriété **globale**, pas une préférence d'agent | ❌ | DB + back + front |
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
  faute de champ pour le dire ;
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
l'écran. Cette note dit **ce que ce document doit cesser d'affirmer**, et ce
qu'il continue d'affirmer malgré la livraison — la seconde liste est la plus
utile des deux.

### Ce qui n'est plus vrai

| Affirmation des §1 à §13 | État au 29/08/2026 |
|---|---|
| « Traitements, nourrissements et varroa ne sont qu'une valeur de `RaisonVisite` » (§3, §11 écart 1) | **Faux.** Trois tables de plein droit : `traitement` (produit, substance active, dose **et son unité**, cible, période, délai de carence, fin de carence générée et indexée), `nourrissement` (type, quantité, unité, motif), `comptage_varroa` (méthode et comptages **bruts**) |
| « Le dépôt ne nomme le varroa que dans la prose de `seed-demo.sql` » (§3) | **Faux.** Il le nomme dans une table, une route (`/api/varroa`), un service qui calcule son taux et son verdict, et un écran |
| « Les observations d'inspection vivent en texte libre » (§3, §11 écart 3) | **Faux.** Quinze colonnes sur `visite` — couvain, motif de ponte, cellules royales et leur cause, cadres, tempérament — plus une table fille `observation_pathologie` pour les maladies **nommées**. Le texte libre subsiste, il n'est plus la seule trace |
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
