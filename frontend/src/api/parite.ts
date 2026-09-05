/**
 * Parité entre les types écrits à la main et le contrat OpenAPI du serveur.
 *
 * <p>La dette que ce fichier solde : `api/types.ts` était écrit à la main alors
 * que le serveur publie un contrat. Rien ne garantissait qu'ils décrivent la même
 * chose — « la parité des types n'est garantie par rien d'autre que l'attention »,
 * comme le disait `client.ts` lui-même en en-tête. Une propriété renommée côté
 * serveur ne se voyait qu'à l'exécution, sous la forme d'un champ vide.
 *
 * <p><strong>Pourquoi vérifier plutôt que générer.</strong> Remplacer purement le
 * client écrit à la main par un client généré aurait touché quarante fonctions et
 * dix-neuf vues, pour un gain limité au seul typage : les fonctions elles-mêmes sont
 * lisibles et stables. Ce fichier obtient la garantie recherchée — **aucune dérive
 * silencieuse** — sans réécrire ce qui fonctionne. Une divergence casse `tsc`,
 * donc la chaîne, avec le nom du champ fautif.
 *
 * <p>Ce fichier n'est jamais importé à l'exécution : il ne contient que des types,
 * et disparaît à la compilation.
 *
 * <p>Régénérer le contrat après toute évolution de l'API :
 * <pre>
 *   cd backend && ./mvnw -B verify -Dit.test=ContratOpenApiIT   # écrit openapi.json
 *   cd frontend && npm run api:contrat                          # écrit contrat.ts
 * </pre>
 */

import type { components } from './contrat';
import type {
  Briefing,
  DossierConformite,
  Genealogie,
  IndexGenetique,
  ReineElevage,
  SerieElevage,
  Gabarit,
  PointReferentiel,
  PointReleve,
  ProduitReferentiel,
  Refractometre,
  StatistiquePoint,
  Materiel,
  Consommable,
  Depense,
  BilanExploitation,
  ComparaisonSaisons,
  PoidsCompartiment,
  Partage,
  EtatDemonstration,
  EmportRucher,
  Brouillon,
  RapportLot,
  SyntheseRucher,
  ComparaisonSite,
  ChargeAgent,
  IndiceColonie,
  CorrelationMeteo,
  Transport,
  Abonnement,
  CaptureEssaim,
  Division,
  Emplacement,
  RessourceFlorale,
  ResultatRecherche,
  Agent,
  AlerteMesure,
  AlerteSanitaire,
  Anomalie,
  AuditEntree,
  CalendrierCellule,
  Compartiment,
  ComptageVarroa,
  EtapeTournee,
  Ferme,
  Fermier,
  GrappeSites,
  LigneProduction,
  Lot,
  MentionOrigine,
  MesureReponse,
  Meteo,
  MeteoVisite,
  Nourrissement,
  ObservationVisite,
  PartLot,
  PathologieObservee,
  Photo,
  Planning,
  PointJournalier,
  PrevisionJour,
  PrevisionRecolte,
  QuantiteMiel,
  Recolte,
  Reine,
  Ruche,
  Seuils,
  Site,
  Synthese,
  Tache,
  Tournee,
  Trace,
  Traitement,
  Visite,
  VisiteBreve,
  VoisinSite,
} from './types';

type Schemas = components['schemas'];

/**
 * Échoue à la compilation si `Ecrit` n'est pas compatible avec `Publie`.
 *
 * <p>Le sens de la vérification est délibéré : on exige que le type écrit à la
 * main soit **assignable** au type publié. Une propriété manquante côté main
 * passe donc — le client a le droit de ne pas tout consommer — mais une propriété
 * de TYPE différent, elle, est rejetée. C'est exactement la dérive qu'on veut
 * attraper : le champ qui existe des deux côtés et ne dit plus la même chose.
 */
type Conforme<Ecrit extends Publie, Publie> = Ecrit;

/**
 * Rend chaque propriété du contrat tolérante au `null`.
 *
 * <p>Ce décalage est réel et mérite d'être expliqué plutôt que contourné en
 * silence. Jackson sérialise un champ Java nul en `"champ": null` — la clé est
 * **présente**, sa valeur est nulle. springdoc, lui, décrit ce champ comme
 * simplement *non requis*, ce qu'openapi-typescript rend par `champ?: T`, donc
 * `T | undefined`.
 *
 * <p>Les types écrits à la main disent `T | null`, ce qui décrit **mieux** la
 * réalité du fil que le contrat généré. On normalise donc du côté publié plutôt
 * que de dégrader le côté écrit : la vérification porte alors sur ce qui compte —
 * le NOM et le TYPE de base de chaque champ — sans buter sur une convention de
 * description.
 *
 * <p>Corriger la source demanderait d'annoter chaque champ nullable côté Java
 * (`@Schema(nullable = true)`), soit plusieurs centaines d'annotations pour un
 * gain nul à l'exécution. C'est un arbitrage, pas un oubli.
 */
type TolerantAuNull<T> = T extends (infer Element)[]
  ? TolerantAuNull<Element>[]
  : T extends object
    ? { [K in keyof T]: TolerantAuNull<T[K]> | null }
    : T;

export type _Fermier = Conforme<Fermier, TolerantAuNull<Schemas['FermierReponse']>>;
export type _Ferme = Conforme<Ferme, TolerantAuNull<Schemas['FermeReponse']>>;
export type _Site = Conforme<Site, TolerantAuNull<Schemas['SiteReponse']>>;
export type _Agent = Conforme<Agent, TolerantAuNull<Schemas['AgentReponse']>>;
export type _Ruche = Conforme<Ruche, TolerantAuNull<Schemas['RucheReponse']>>;
export type _Visite = Conforme<Visite, TolerantAuNull<Schemas['VisiteReponse']>>;
export type _Planning = Conforme<Planning, TolerantAuNull<Schemas['PlanningReponse']>>;
export type _Tache = Conforme<Tache, TolerantAuNull<Schemas['TacheReponse']>>;
export type _Recolte = Conforme<Recolte, TolerantAuNull<Schemas['RecolteReponse']>>;
export type _Lot = Conforme<Lot, TolerantAuNull<Schemas['LotReponse']>>;
export type _Mesure = Conforme<MesureReponse, TolerantAuNull<Schemas['MesureReponse']>>;

/*
 * Couverture etendue.
 *
 * <p>La verification ne portait que sur onze types — les entites CRUD — alors que
 * `types.ts` en exporte une soixantaine. Tout ce qui n'etait pas couvert pouvait
 * deriver en silence : precisement les reponses analytiques (synthese, previsions,
 * carte, anomalies), c'est-a-dire celles qu'aucun formulaire ne fait echouer
 * visiblement quand un champ arrive vide.
 */
export type _Photo = Conforme<Photo, TolerantAuNull<Schemas['PhotoReponse']>>;
export type _Compartiment = Conforme<Compartiment, TolerantAuNull<Schemas['CompartimentReponse']>>;
export type _Reine = Conforme<Reine, TolerantAuNull<Schemas['ReineReponse']>>;
export type _AuditEntree = Conforme<AuditEntree, TolerantAuNull<Schemas['AuditEntreeReponse']>>;
export type _AlerteMesure = Conforme<AlerteMesure, TolerantAuNull<Schemas['AlerteReponse']>>;
export type _AlerteSanitaire = Conforme<AlerteSanitaire, TolerantAuNull<Schemas['AlerteSanitaire']>>;
export type _Synthese = Conforme<Synthese, TolerantAuNull<Schemas['SyntheseReponse']>>;
export type _LigneProduction = Conforme<LigneProduction, TolerantAuNull<Schemas['LigneProduction']>>;
export type _PrevisionRecolte = Conforme<PrevisionRecolte, TolerantAuNull<Schemas['PrevisionRecolte']>>;
export type _CalendrierCellule = Conforme<CalendrierCellule, TolerantAuNull<Schemas['CalendrierCellule']>>;
export type _VisiteBreve = Conforme<VisiteBreve, TolerantAuNull<Schemas['VisiteBreve']>>;
export type _PointJournalier = Conforme<PointJournalier, TolerantAuNull<Schemas['PointJournalier']>>;
export type _Meteo = Conforme<Meteo, TolerantAuNull<Schemas['MeteoReponse']>>;
export type _PrevisionJour = Conforme<PrevisionJour, TolerantAuNull<Schemas['PrevisionJour']>>;
export type _Anomalie = Conforme<Anomalie, TolerantAuNull<Schemas['AnomalieReponse']>>;
export type _Trace = Conforme<Trace, TolerantAuNull<Schemas['TraceReponse']>>;
export type _GrappeSites = Conforme<GrappeSites, TolerantAuNull<Schemas['GrappeSites']>>;
export type _VoisinSite = Conforme<VoisinSite, TolerantAuNull<Schemas['VoisinSite']>>;
export type _EtapeTournee = Conforme<EtapeTournee, TolerantAuNull<Schemas['EtapeTournee']>>;
export type _Tournee = Conforme<Tournee, TolerantAuNull<Schemas['TourneeReponse']>>;
export type _QuantiteMiel = Conforme<QuantiteMiel, TolerantAuNull<Schemas['QuantiteMiel']>>;
export type _MentionOrigine = Conforme<MentionOrigine, TolerantAuNull<Schemas['MentionOrigine']>>;
export type _PartLot = Conforme<PartLot, TolerantAuNull<Schemas['PartReponse']>>;
export type _Seuils = Conforme<Seuils, TolerantAuNull<Schemas['SeuilsMetier']>>;

/*
 * Registre sanitaire (SPRINT-20).
 *
 * <p>Ces six types-la ne sont pas seulement nouveaux : ce sont ceux dont la
 * derive se verrait le moins. Un `tauxUnite` renomme ou une `gravite` devenue
 * `severite` n'aurait fait echouer aucun formulaire — le champ serait
 * simplement arrive vide, et un taux de varroa affiche sans son unite est pire
 * qu'un taux absent.
 */
export type _Traitement = Conforme<Traitement, TolerantAuNull<Schemas['TraitementReponse']>>;
export type _Nourrissement =
  Conforme<Nourrissement, TolerantAuNull<Schemas['NourrissementReponse']>>;
export type _ComptageVarroa =
  Conforme<ComptageVarroa, TolerantAuNull<Schemas['ComptageVarroaReponse']>>;
export type _PathologieObservee =
  Conforme<PathologieObservee, TolerantAuNull<Schemas['PathologieReponse']>>;
export type _ObservationVisite =
  Conforme<ObservationVisite, TolerantAuNull<Schemas['ObservationVisite']>>;
export type _MeteoVisite = Conforme<MeteoVisite, TolerantAuNull<Schemas['MeteoVisite']>>;

/*
 * Terrain, ruchers et ruches (SPRINT-21).
 *
 * <p>Cinq types de plus, et deux d'entre eux meritent d'etre cites : `Site`
 * gagne six champs dont l'adresse — qui arrive NULLE pour un profil non
 * proprietaire, ce que seule la lecture du contrat permet de distinguer d'un
 * champ disparu — et `Photo` gagne `cible`/`cibleId`, sans quoi une galerie ne
 * saurait plus d'ou vient une image des lors qu'elle n'est plus forcement une
 * photo de visite.
 */
export type _RessourceFlorale =
  Conforme<RessourceFlorale, TolerantAuNull<Schemas['RessourceFloraleReponse']>>;
export type _Emplacement = Conforme<Emplacement, TolerantAuNull<Schemas['EmplacementReponse']>>;
export type _Division = Conforme<Division, TolerantAuNull<Schemas['DivisionReponse']>>;
export type _CaptureEssaim =
  Conforme<CaptureEssaim, TolerantAuNull<Schemas['CaptureEssaimReponse']>>;
export type _ResultatRecherche =
  Conforme<ResultatRecherche, TolerantAuNull<Schemas['ResultatRecherche']>>;

/*
 * Terrain, second lot (SPRINT-21, migration V21).
 *
 * <p>Deux types dont la derive se verrait tard : `voyages` est CALCULE par le
 * serveur — le renommer laisserait l'ecran afficher un blanc la ou se lit le
 * nombre de trajets — et `url` n'est renseignee qu'a la creation d'un
 * abonnement, si bien qu'un changement de nom la ferait disparaitre au moment
 * precis ou elle est irrecuperable.
 */
export type _Transport = Conforme<Transport, TolerantAuNull<Schemas['TransportReponse']>>;
export type _Abonnement = Conforme<Abonnement, TolerantAuNull<Schemas['AbonnementReponse']>>;

/*
 * Regles, indices et correlations (SPRINT-22, lot A).
 *
 * <p>Deux types dont la derive serait invisible a l'usage. `IndiceColonie`
 * porte `composantes` : le renommer ferait afficher une jauge sur une colonie
 * non evaluee, c'est-a-dire exactement ce que le service refuse de faire. Et
 * `CorrelationMeteo.coefficient` est NULLABLE par construction — un contrat qui
 * le rendrait obligatoire masquerait la distinction entre « aucun lien » et
 * « pas de coefficient calculable ».
 */
export type _IndiceColonie = Conforme<IndiceColonie, TolerantAuNull<Schemas['IndiceColonie']>>;
export type _CorrelationMeteo =
  Conforme<CorrelationMeteo, TolerantAuNull<Schemas['CorrelationMeteo']>>;

/*
 * Actes de lot et agregats (SPRINT-23, lot B).
 *
 * <p>Quatre types dont la derive serait particulierement couteuse a
 * l'affichage. `RapportLot.echecs` porte le motif ruche par ruche : le
 * renommer ferait afficher un lot « reussi » la ou trois colonies ont ete
 * refusees. `SyntheseRucher.santeMoyenne` et `ComparaisonSite.rendementKgParRuche`
 * sont NULLABLES par construction — un contrat qui les rendrait obligatoires
 * effacerait la difference entre « zero » et « pas encore evalue », qui est
 * tout l'objet de ces deux ecrans. Et `ChargeAgent.ruchersConcernes` n'est pas
 * `ruchesResponsable` : les confondre ferait compter trois deplacements la ou
 * il y en a deux.
 */
export type _RapportLot = Conforme<RapportLot, TolerantAuNull<Schemas['RapportLot']>>;
export type _SyntheseRucher =
  Conforme<SyntheseRucher, TolerantAuNull<Schemas['SyntheseRucher']>>;
export type _ComparaisonSite =
  Conforme<ComparaisonSite, TolerantAuNull<Schemas['ComparaisonSite']>>;
export type _ChargeAgent = Conforme<ChargeAgent, TolerantAuNull<Schemas['ChargeAgent']>>;

/*
 * Le terrain sans reseau (SPRINT-24, lot C).
 *
 * <p>Deux types dont la derive serait particulierement traitre. `preleveLe` est
 * ce qui empeche une donnee du disque de passer pour fraiche : le renommer
 * rendrait l'ecran muet sur l'age de l'instantane, c'est-a-dire exactement
 * l'objection que l'ADR-012 s'engage a lever. Et `Brouillon.contenu` est du JSON
 * OPAQUE : un changement de nom ferait rouvrir un formulaire vide, sans erreur,
 * la ou l'apiculteur attend sa saisie de la veille.
 */
export type _EmportRucher = Conforme<EmportRucher, TolerantAuNull<Schemas['EmportRucher']>>;
export type _Brouillon = Conforme<Brouillon, TolerantAuNull<Schemas['BrouillonReponse']>>;

/*
 * Identification et confort (SPRINT-25, lot J).
 *
 * <p>`EtatDemonstration.objets` est le chiffre qui rend le bouton « Retirer »
 * decidable plutot qu'inquietant : le renommer laisserait l'ecran proposer une
 * suppression sans dire combien de lignes partent. `Agent.notificationsEmail`
 * est verifie par le meme mecanisme via `_Agent`.
 */
export type _EtatDemonstration =
  Conforme<EtatDemonstration, TolerantAuNull<Schemas['EtatDemonstration']>>;

/*
 * Capteurs : hausse et partage (SPRINT-26, lot F1).
 *
 * <p>`PoidsCompartiment.valeur` est NULLABLE par construction — une hausse
 * jamais pesee n'est pas une hausse vide. Un contrat qui la rendrait
 * obligatoire ferait afficher 0 kg, c'est-a-dire une colonie qui aurait perdu
 * ses reserves. Et `Partage.url` n'existe qu'a la creation : la renommer
 * ferait disparaitre le lien au moment PRECIS ou il est irrecuperable, la base
 * n'en gardant que l'empreinte.
 */
export type _PoidsCompartiment =
  Conforme<PoidsCompartiment, TolerantAuNull<Schemas['PoidsCompartiment']>>;
export type _Partage = Conforme<Partage, TolerantAuNull<Schemas['PartageReponse']>>;

/*
 * Production, stock, materiel (SPRINT-27, lot E).
 *
 * <p>Cinq types dont la derive serait couteuse a l'ecran. `Materiel.enRetard` et
 * `prochaineMaintenance` sont CALCULES par le serveur : les renommer ferait
 * disparaitre le seul signal de l'ecran des equipements. `Consommable.sousSeuil`
 * de meme. `BilanExploitation.depensesNonAffectees` est ce que l'interface
 * s'engage a montrer separement — le perdre reviendrait a repartir en silence ce
 * que le service refuse de repartir. Et `ComparaisonSaisons.rendementKg` est
 * NULLABLE par construction : un contrat qui le rendrait obligatoire ferait
 * afficher zero la ou aucune ruche n'a produit.
 */
export type _Materiel = Conforme<Materiel, TolerantAuNull<Schemas['MaterielReponse']>>;
export type _Consommable =
  Conforme<Consommable, TolerantAuNull<Schemas['ConsommableReponse']>>;
export type _Depense = Conforme<Depense, TolerantAuNull<Schemas['DepenseReponse']>>;
export type _BilanExploitation =
  Conforme<BilanExploitation, TolerantAuNull<Schemas['BilanExploitation']>>;
export type _ComparaisonSaisons =
  Conforme<ComparaisonSaisons, TolerantAuNull<Schemas['ComparaisonSaisons']>>;

/*
 * Le carnet parametrable (SPRINT-28, lot I).
 *
 * <p>Six types dont la derive serait invisible a l'ecran et couteuse ailleurs.
 * `PointReferentiel.typeValeur` decide de la case a cocher OU du selecteur
 * d'intensite : le renommer ferait rendre le mauvais champ, et le serveur
 * refuserait la saisie. `PointReleve` porte la distinction qui fonde tout le
 * lot — `coche: null` et `niveau: null` ne sont pas des oublis, ils disent quel
 * type de valeur a ete releve. `StatistiquePoint.moyenneEchelle` est NULLABLE
 * par construction : un contrat qui le rendrait obligatoire ferait afficher une
 * moyenne de cases cochees, c'est-a-dire un taux deguise en note. Et
 * `Refractometre.conformeNorme` est le seul signal qui distingue un miel
 * commercialisable d'un miel qui ne l'est pas.
 */
export type _PointReferentiel =
  Conforme<PointReferentiel, TolerantAuNull<Schemas['PointReferentiel']>>;
export type _PointReleve = Conforme<PointReleve, TolerantAuNull<Schemas['PointReleve']>>;
export type _Gabarit = Conforme<Gabarit, TolerantAuNull<Schemas['GabaritReponse']>>;
export type _ProduitReferentiel =
  Conforme<ProduitReferentiel, TolerantAuNull<Schemas['ProduitReferentiel']>>;
export type _StatistiquePoint =
  Conforme<StatistiquePoint, TolerantAuNull<Schemas['StatistiquePoint']>>;
export type _Refractometre = Conforme<Refractometre, TolerantAuNull<Schemas['Refractometre']>>;

/*
 * Elevage, reines et genealogie (SPRINT-29, lot D).
 *
 * <p>Cinq types dont la derive serait grave et invisible. `ReineElevage.mereId`
 * porte la filiation : renomme, l'arbre se viderait sans erreur. `Genealogie`
 * distingue ascendants et descendants — les fondre cote serveur ferait dessiner
 * les meres a la place des filles. `IndexGenetique.criteres[].suffisant` decide
 * si une valeur s'affiche ou si l'ecran dit « pas assez observe » : le perdre
 * ferait afficher `null` comme un zero, c'est-a-dire comme un mauvais resultat.
 * `SerieElevage.tauxAcceptation` est NULLABLE tant que le compte n'est pas
 * releve. Et `DossierConformite.avertissement` est la phrase qui empeche de lire
 * ce document comme une certification : elle doit arriver, toujours.
 */
export type _ReineElevage = Conforme<ReineElevage, TolerantAuNull<Schemas['ReineElevage']>>;
export type _Genealogie = Conforme<Genealogie, TolerantAuNull<Schemas['Genealogie']>>;
export type _IndexGenetique =
  Conforme<IndexGenetique, TolerantAuNull<Schemas['IndexGenetique']>>;
export type _SerieElevage = Conforme<SerieElevage, TolerantAuNull<Schemas['SerieReponse']>>;
export type _DossierConformite =
  Conforme<DossierConformite, TolerantAuNull<Schemas['DossierConformite']>>;

/*
 * Briefing du jour (SPRINT-30, lot G).
 *
 * <p>Un seul type, et une seule chose a proteger : `LigneBriefing.detail`. C'est
 * lui qui rend la ligne verifiable, et le perdre transformerait le briefing en
 * suite d'affirmations — exactement ce que l'ADR-013 refuse en ecartant un
 * modele de langue.
 */
export type _Briefing = Conforme<Briefing, TolerantAuNull<Schemas['Briefing']>>;
