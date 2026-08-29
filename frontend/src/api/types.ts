/**
 * Types du domaine, en miroir des DTO du backend (US-001 a US-005, US-025).
 *
 * <p>Ces types sont ECRITS A LA MAIN, et le restent : c'est une decision, pas un
 * provisoire. L'en-tete annoncait jusqu'ici leur remplacement par des types
 * generes « des que le backend publiera son contrat OpenAPI », et la disparition
 * de ce fichier. Le contrat est publie (`openapi.json`), et le SPRINT-17 a tranche
 * autrement : la parite est VERIFIEE plutot que generee.
 *
 * <p>Voir `api/parite.ts` pour le detail de l'arbitrage — en resume, generer le
 * client aurait touche quarante fonctions et dix-neuf vues pour un gain limite au
 * seul typage, alors qu'une verification obtient la meme garantie (aucune derive
 * silencieuse) sans reecrire ce qui fonctionne. Une divergence casse `tsc`.
 */

export type RoleAgent = 'apiculteur' | 'superviseur' | 'responsable' | 'admin';

export const ROLES_AGENT: readonly RoleAgent[] = [
  'apiculteur',
  'superviseur',
  'responsable',
  'admin',
];

export interface Fermier {
  id: number;
  nom: string;
  contact: string | null;
  creeLe: string;
  majLe: string;
}

export interface FermierCorps {
  nom: string;
  contact: string | null;
}

export interface Ferme {
  id: number;
  nom: string;
  fermierId: number;
  fermierNom: string;
  creeLe: string;
  majLe: string;
}

export interface FermeCorps {
  nom: string;
  fermierId: number;
}

export interface Site {
  id: number;
  nom: string;
  fermeId: number;
  fermeNom: string;
  latitude: number;
  longitude: number;
  altitude: number | null;
  dateMiseEnOeuvre: string;
  dateDemenagement: string | null;
  dateCloture: string | null;
  creeLe: string;
  majLe: string;
}

export interface SiteCorps {
  nom: string;
  fermeId: number;
  latitude: number;
  longitude: number;
  altitude: number | null;
  dateMiseEnOeuvre: string;
  dateDemenagement: string | null;
  dateCloture: string | null;
}

export interface Agent {
  id: number;
  nom: string;
  role: RoleAgent;
  fermeId: number | null;
  fermeNom: string | null;
  email: string | null;
  creeLe: string;
  majLe: string;
}

export interface AgentCorps {
  nom: string;
  role: RoleAgent;
  fermeId: number | null;
  email?: string | null;
}

export type EtatRuche =
  | 'creee'
  | 'peuplee'
  | 'active'
  | 'en_division'
  | 'en_collecte'
  | 'cloturee';

export const ETATS_RUCHE: readonly EtatRuche[] = [
  'creee',
  'peuplee',
  'active',
  'en_division',
  'en_collecte',
  'cloturee',
];

export type TypeCompartiment = 'corps' | 'hausse';

export interface Compartiment {
  id: number;
  type: TypeCompartiment;
  nbCadres: number;
}

export interface CompartimentCorps {
  type: TypeCompartiment;
  nbCadres: number;
}

/**
 * Referentiel de type de ruche (SPRINT-20).
 *
 * <p>`modele` reste le texte libre — « Dadant 10 cadres, fond grillage Nicot ».
 * Ce type-ci est le referentiel AU-DESSUS : c'est lui qui rend possible une
 * statistique par type, qu'un texte libre interdisait.
 */
export type TypeRuche =
  | 'langstroth'
  | 'dadant'
  | 'warre'
  | 'voirnot'
  | 'top_bar'
  | 'kenyane'
  | 'autre';

export const TYPES_RUCHE: readonly TypeRuche[] = [
  'langstroth',
  'dadant',
  'warre',
  'voirnot',
  'top_bar',
  'kenyane',
  'autre',
];

/** Couleur du corps, telle qu'on la repere au rucher — avant tout scan. */
export type CouleurRuche =
  | 'blanc'
  | 'jaune'
  | 'orange'
  | 'rouge'
  | 'vert'
  | 'bleu'
  | 'violet'
  | 'gris'
  | 'bois';

export const COULEURS_RUCHE: readonly CouleurRuche[] = [
  'blanc',
  'jaune',
  'orange',
  'rouge',
  'vert',
  'bleu',
  'violet',
  'gris',
  'bois',
];

/** D'ou vient la colonie : ce que le cheptel doit a lui-meme, et ce qu'il achete. */
export type OrigineRuche =
  | 'essaim_capture'
  | 'essaim_achete'
  | 'division'
  | 'nucleus'
  | 'paquet'
  | 'achat'
  | 'autre';

export const ORIGINES_RUCHE: readonly OrigineRuche[] = [
  'essaim_capture',
  'essaim_achete',
  'division',
  'nucleus',
  'paquet',
  'achat',
  'autre',
];

/**
 * Pourquoi une ruche est cloturee (SPRINT-20).
 *
 * <p>`EtatRuche.cloturee` confondait une ruche morte, une ruche vendue et une
 * ruche fusionnee : trois issues qui ne disent pas du tout la meme chose du
 * cheptel. Le serveur refuse cette cause sur une ruche encore active.
 */
export type CauseCloture =
  | 'morte'
  | 'fusionnee'
  | 'vendue'
  | 'volee'
  | 'reformee'
  | 'essaimee'
  | 'autre';

export const CAUSES_CLOTURE: readonly CauseCloture[] = [
  'morte',
  'fusionnee',
  'vendue',
  'volee',
  'reformee',
  'essaimee',
  'autre',
];

export interface Ruche {
  id: number;
  modele: string;
  siteId: number;
  siteNom: string;
  fermeId: number;
  fermeNom: string;
  agentResponsableId: number | null;
  agentResponsableNom: string | null;
  etat: EtatRuche;
  nbHausses: number;
  compartiments: Compartiment[];
  typeRuche: TypeRuche | null;
  couleur: CouleurRuche | null;
  origine: OrigineRuche | null;
  causeCloture: CauseCloture | null;
  creeLe: string;
  majLe: string;
}

export interface RucheCorps {
  modele: string;
  siteId: number;
  fermeId: number;
  agentResponsableId: number | null;
  etat: EtatRuche;
  compartiments: CompartimentCorps[];
  typeRuche: TypeRuche | null;
  couleur: CouleurRuche | null;
  origine: OrigineRuche | null;
  causeCloture: CauseCloture | null;
}

export type RaisonVisite =
  | 'controle'
  | 'recolte'
  | 'traitement'
  | 'nourrissage'
  | 'division'
  | 'autre';
export const RAISONS_VISITE: readonly RaisonVisite[] = [
  'controle',
  'recolte',
  'traitement',
  'nourrissage',
  'division',
  'autre',
];

export type StatutPlanning = 'propose' | 'approuve' | 'refuse';
export type EffectifQualitatif = 'faible' | 'moyen' | 'fort';
export type EtatSante = 'bon' | 'moyen' | 'mauvais';

export interface Planning {
  id: number;
  rucheId: number;
  rucheModele: string;
  agentId: number;
  agentNom: string;
  superviseurId: number | null;
  superviseurNom: string | null;
  datePrevue: string;
  heurePrevue: string | null;
  dureeMin: number | null;
  raison: RaisonVisite;
  statut: StatutPlanning;
  motifRefus: string | null;
  creeLe: string;
  majLe: string;
}

export interface PlanningCorps {
  rucheId: number;
  agentId: number;
  superviseurId: number | null;
  datePrevue: string;
  heurePrevue: string | null;
  dureeMin: number | null;
  raison: RaisonVisite;
}

export interface Photo {
  id: number;
  url: string;
  legende: string | null;
  creeLe: string;
}

export interface PhotoCorps {
  url: string;
  legende: string | null;
}

export interface Visite {
  id: number;
  rucheId: number;
  rucheModele: string;
  agentId: number;
  agentNom: string;
  planningId: number | null;
  dateVisite: string;
  heureVisite: string | null;
  dureeMin: number | null;
  raison: RaisonVisite;
  constatations: string | null;
  actionsPrevues: string | null;
  actionsEffectuees: string | null;
  recommandations: string | null;
  effectifQualitatif: EffectifQualitatif | null;
  etatSante: EtatSante | null;
  productivite: number | null;
  observation: ObservationVisite | null;
  meteo: MeteoVisite | null;
  pathologies: PathologieObservee[];
  photos: Photo[];
  creeLe: string;
  majLe: string;
}

export interface VisiteCorps {
  rucheId: number;
  agentId: number;
  planningId: number | null;
  dateVisite: string;
  heureVisite: string | null;
  dureeMin: number | null;
  raison: RaisonVisite;
  constatations: string | null;
  actionsPrevues: string | null;
  actionsEffectuees: string | null;
  recommandations: string | null;
  effectifQualitatif: EffectifQualitatif | null;
  etatSante: EtatSante | null;
  productivite: number | null;
  observation: ObservationVisite | null;
  meteo: MeteoVisite | null;
  pathologies: PathologieCorps[];
}

/** Tâche ou rappel de l'apiculteur (US-031). */
export interface Tache {
  id: number;
  libelle: string;
  rucheId: number | null;
  rucheModele: string | null;
  agentId: number | null;
  agentNom: string | null;
  echeance: string | null;
  faite: boolean;
  creeLe: string;
  majLe: string;
}

export interface TacheCorps {
  libelle: string;
  rucheId: number | null;
  agentId: number | null;
  echeance: string | null;
  faite: boolean;
}

/**
 * Code d'invitation à rejoindre l'exploitation (US-058, ADR-009).
 *
 * <p>`epuise` est calculé par le serveur et non ici : « périmé » dépend de
 * l'heure, et l'horloge du navigateur d'un téléphone au rucher n'est pas une
 * référence — un poste en avance masquerait des codes encore valides.
 */
export interface Invitation {
  id: number;
  code: string;
  role: RoleAgent;
  utilisations: number;
  utilisationsMax: number;
  expireLe: string;
  creePar: string | null;
  epuise: boolean;
}

export interface InvitationCorps {
  role: RoleAgent;
  utilisationsMax: number;
  joursValidite: number;
}

/** Résumé d'une visite dans une cellule du calendrier (US-012). */
export interface VisiteBreve {
  id: number;
  date: string;
  raison: RaisonVisite;
  etatSante: EtatSante | null;
}

/** Cellule du calendrier matriciel agents × ruches (US-012). */
export interface CalendrierCellule {
  agentId: number;
  agentNom: string;
  rucheId: number;
  rucheModele: string;
  nombreVisites: number;
  visites: VisiteBreve[];
}

/** Ligne du tableau de bord production (US-013). */
export interface LigneProduction {
  rucheId: number;
  rucheModele: string;
  poidsActuelKg: number | null;
  poidsMinKg: number | null;
  poidsMaxKg: number | null;
  nombreMesures: number;
  sousSeuil: boolean;
  productiviteMoyenne: number | null;
}

/** Prévision de récolte d'une ruche (US-042, SPRINT-09). */
export type TendanceRecolte = 'hausse' | 'stable' | 'baisse' | 'inconnue';

export interface PrevisionRecolte {
  rucheId: number;
  rucheModele: string;
  poidsActuelKg: number | null;
  tendanceKgParJour: number | null;
  projection7jKg: number | null;
  tendance: TendanceRecolte;
  nombreMesures: number;
}

/** Entrée du journal d'audit (US-043, SPRINT-09). */
export type ActionAudit = 'creation' | 'modification' | 'suppression';

export interface AuditEntree {
  id: number;
  instant: string;
  acteur: string;
  action: ActionAudit;
  entite: string;
  entiteId: number | null;
  resume: string | null;
}

export type NiveauAlerte = 'ok' | 'attention' | 'critique';

/** Alerte du tableau de bord sanitaire (US-014). */
export interface AlerteSanitaire {
  rucheId: number;
  rucheModele: string;
  dernierEtatSante: EtatSante | null;
  derniereVisite: string | null;
  joursDepuisVisite: number | null;
  niveau: NiveauAlerte;
  motif: string;
}

export type TypeIndicateur = 'poids' | 'temperature' | 'humidite' | 'activite';
export const TYPES_INDICATEUR: readonly TypeIndicateur[] = [
  'poids',
  'temperature',
  'humidite',
  'activite',
];

/** Alerte de seuil déclenchée par une mesure (US-018). */
export interface AlerteMesure {
  id: number;
  rucheId: number;
  rucheModele: string;
  typeIndicateur: TypeIndicateur;
  niveau: 'attention' | 'critique';
  message: string;
  valeurDeclenchement: number;
  ouverte: boolean;
  ouverteLe: string;
  fermeeLe: string | null;
}

/** Mesure ingérée et alertes déclenchées (US-017/018). */
export interface MesureReponse {
  rucheId: number;
  typeIndicateur: TypeIndicateur;
  instant: string;
  valeur: number;
  alertes: AlerteMesure[];
}

export interface MesureCorps {
  rucheId: number;
  typeIndicateur: TypeIndicateur;
  valeur: number;
  instant: string | null;
}

/** Synthèse de pilotage et ROI (US-015). */
export interface Synthese {
  nombreRuches: number;
  nombreVisites: number;
  visitesParRaison: Record<string, number>;
  productiviteMoyenne: number | null;
  poidsTotalActuelKg: number;
  alertesOuvertes: number;
  roi: {
    valeurProductionEur: number;
    coutInterventionsEur: number;
    roiPourcent: number | null;
  };
}

/** Réponse du service getZummHoneyActualQuantity (US-026). */
export interface QuantiteMiel {
  rucheId: number | null;
  quantite: number;
  unite: string;
}

/**
 * Prévision météo d'une journée (US-029).
 *
 * Minimale et maximale plutôt qu'une moyenne : ce sont elles qui décident d'une
 * visite. Tous les champs sont nullables — une série météo peut arriver
 * incomplète, et `null` dit « inconnu » là où `0` dirait « pas de pluie ».
 */
export interface PrevisionJour {
  date: string;
  temperatureMinCelsius: number | null;
  temperatureMaxCelsius: number | null;
  precipitationsMm: number | null;
  ventMaxKmh: number | null;
}

/** Contexte météo local d'un site (US-029) : instantané + prévisions. */
export interface Meteo {
  siteId: number;
  latitude: number;
  longitude: number;
  temperatureCelsius: number;
  humiditePourcent: number | null;
  ventKmh: number | null;
  source: string;
  instant: string;
  previsions: PrevisionJour[];
}

export type StatutReine = 'introduite' | 'en_ponte' | 'remplacee' | 'disparue' | 'essaimee';
export const STATUTS_REINE: readonly StatutReine[] = [
  'introduite',
  'en_ponte',
  'remplacee',
  'disparue',
  'essaimee',
];
export type CouleurReine = 'blanc' | 'jaune' | 'rouge' | 'vert' | 'bleu';
export const COULEURS_REINE: readonly CouleurReine[] = ['blanc', 'jaune', 'rouge', 'vert', 'bleu'];

/** Événement du journal de la reine (US-032). */
export interface Reine {
  id: number;
  rucheId: number;
  rucheModele: string;
  dateEvenement: string;
  statut: StatutReine;
  couleurMarquage: CouleurReine | null;
  anneeNaissance: number | null;
  race: string | null;
  note: string | null;
  creeLe: string;
  majLe: string;
}

export interface ReineCorps {
  rucheId: number;
  dateEvenement: string;
  statut: StatutReine;
  couleurMarquage: CouleurReine | null;
  anneeNaissance: number | null;
  race: string | null;
  note: string | null;
}

/** Récolte avec lot et payload QR (US-033). */
export interface Recolte {
  id: number;
  rucheId: number;
  rucheModele: string;
  dateRecolte: string;
  quantiteKg: number;
  typeMiel: string | null;
  lot: string;
  note: string | null;
  qrPayload: string;
  creeLe: string;
  majLe: string;
}

export interface RecolteCorps {
  rucheId: number;
  dateRecolte: string;
  quantiteKg: number;
  typeMiel: string | null;
  note: string | null;
}

/** Fiche de traçabilité d'un lot (US-033). */
export interface Trace {
  lot: string;
  rucheId: number;
  rucheModele: string;
  siteNom: string;
  fermeNom: string;
  dateRecolte: string;
  quantiteKg: number;
  typeMiel: string | null;
}

/** Détection d'anomalie EWMA (US-034). */
export interface Anomalie {
  rucheId: number;
  typeIndicateur: TypeIndicateur;
  alpha: number;
  seuilZ: number;
  baseline: number | null;
  ecartType: number | null;
  nombrePoints: number;
  anomalies: { instant: string; valeur: number; zScore: number }[];
}

/** Grappe de sites proches, calculée par PostGIS (US-045). */
export interface GrappeSites {
  numero: number;
  latitudeCentre: number;
  longitudeCentre: number;
  nombreSites: number;
  nombreRuches: number;
  sites: Site[];
}

/** Site voisin d'un site de référence, distance géodésique en mètres (US-046). */
export interface VoisinSite {
  site: Site;
  distanceMetres: number;
}

/** Étape d'une tournée : un site et les plannings à y honorer (US-047). */
export interface EtapeTournee {
  ordre: number;
  siteId: number;
  siteNom: string;
  latitude: number;
  longitude: number;
  planningIds: number[];
  nombreVisites: number;
  distanceDepuisPrecedenteMetres: number;
}

/** Tournée proposée à un agent pour une journée (US-047). */
export interface Tournee {
  agentId: number;
  agentNom: string;
  date: string;
  nombreSites: number;
  nombreVisites: number;
  distanceTotaleMetres: number;
  etapes: EtapeTournee[];
}

export interface Seuils {
  langueParDefaut: string;
  languesActives: string[];
  poidsRucheAlerteKg: number;
  temperatureMinCelsius: number;
  temperatureMaxCelsius: number;
  humiditeMaxPourcent: number;
  delaiAlerteJours: number;
  arrondiDegresPublic: number;
  /** Hypothèses de valorisation du ROI (section `[economie]` de ConfigZumm.ini). */
  prixMielKgEur: number;
  coutVisiteEur: number;
}

/**
 * Lot de conditionnement et mention d'origine (US-056, SPRINT-14).
 *
 * Conformité à la directive (UE) 2024/1438, applicable au 14 juin 2026 : le pot
 * porte le ou les pays d'origine, par ordre décroissant, en pourcentages. La
 * maille est le LOT MIS EN POT — un mélange — et non la récolte.
 */
export interface OrigineDeclaree {
  /** Récolte d'origine, ou `null` pour du miel acquis à un tiers. */
  recolteId: number | null;
  /** Code pays ISO 3166-1 alpha-2 : « FR », « ES »… */
  paysOrigine: string;
  pourcentage: number;
}

export interface LotCorps {
  reference: string;
  dateConditionnement: string;
  quantiteKg: number;
  typeMiel?: string | null;
  note?: string | null;
  origines: OrigineDeclaree[];
}

export interface PartLot {
  id: number;
  recolteId: number | null;
  recolteLot: string | null;
  paysOrigine: string;
  pourcentage: number;
}

export interface Lot {
  id: number;
  reference: string;
  dateConditionnement: string;
  quantiteKg: number;
  typeMiel: string | null;
  note: string | null;
  composition: PartLot[];
  creeLe: string;
  majLe: string;
}

export interface MentionOrigine {
  /** Mention prête à imprimer, dans la langue demandée. */
  texte: string;
  origines: { paysOrigine: string; libelle: string; pourcentage: number }[];
  /** Vrai dès que plus d'un pays entre dans le lot. */
  melange: boolean;
}

/**
 * Point de courbe journalière (SPRINT-18).
 *
 * <p>La courbe ne lit plus la série brute : à un relevé par quart d'heure, trois
 * ans d'historique font ~105 000 points pour une seule ruche, dont un graphique de
 * 640 pixels n'en montrera jamais plus que sa largeur. Le serveur agrège par jour.
 *
 * <p>Le minimum et le maximum voyagent avec la moyenne : sur une ruche,
 * l'amplitude d'une journée est une information à part entière — une chute
 * nocturne de poids ne se lit pas sur une moyenne.
 */
export interface PointJournalier {
  jour: string;
  moyenne: number;
  minimum: number;
  maximum: number;
  nombre: number;
}

/* =========================================================================
 * Registre sanitaire et observations structurees (SPRINT-20)
 *
 * Trois actes qui n'etaient jusqu'ici qu'une valeur de `RaisonVisite` : on
 * savait QU'ON avait traite, jamais avec quoi, a quelle dose, ni sous quel
 * delai de carence. Et « varroa » n'existait nulle part dans le depot hors de
 * la prose du jeu de demonstration.
 *
 * Les trois ressources partagent leur maille — ruche, date, agent — sans
 * partager leur forme : la dose et la carence n'ont de sens que pour un
 * traitement, le motif que pour un nourrissement, la methode de comptage que
 * pour le varroa. Les fondre rendrait facultatif tout ce qui fait la valeur de
 * chaque acte (voir le commentaire de tete de `V19`).
 * ========================================================================= */

/** Ce contre quoi on traite. */
export type CibleTraitement =
  | 'varroa'
  | 'loque_americaine'
  | 'loque_europeenne'
  | 'nosema'
  | 'petit_coleoptere'
  | 'fausse_teigne'
  | 'frelon'
  | 'autre';

export const CIBLES_TRAITEMENT: readonly CibleTraitement[] = [
  'varroa',
  'loque_americaine',
  'loque_europeenne',
  'nosema',
  'petit_coleoptere',
  'fausse_teigne',
  'frelon',
  'autre',
];

/** Unite de dose. Une dose sans unite ne se relit pas : « 2 » ne dit ni 2 ml ni 2 lanieres. */
export type UniteDose = 'mg' | 'g' | 'ml' | 'l' | 'laniere' | 'plaquette';

export const UNITES_DOSE: readonly UniteDose[] = ['mg', 'g', 'ml', 'l', 'laniere', 'plaquette'];

/**
 * Traitement sanitaire applique a une ruche.
 *
 * @property dateRetrait fin de carence, calculee par la base
 *   (`dateFin + delaiCarenceJours`) : avant elle, le miel ne part pas en recolte
 * @property sousCarence verdict du jour, calcule par le serveur. Le client ne
 *   refait pas ce calcul de dates — et surtout pas differemment
 */
export interface Traitement {
  id: number;
  rucheId: number;
  rucheModele: string;
  agentId: number;
  agentNom: string;
  visiteId: number | null;
  produit: string;
  substanceActive: string | null;
  cible: CibleTraitement;
  dose: number | null;
  doseUnite: UniteDose | null;
  dateDebut: string;
  dateFin: string | null;
  delaiCarenceJours: number | null;
  dateRetrait: string | null;
  sousCarence: boolean;
  ordonnance: string | null;
  note: string | null;
  creeLe: string;
  majLe: string;
}

export interface TraitementCorps {
  rucheId: number;
  agentId: number;
  visiteId: number | null;
  produit: string;
  substanceActive: string | null;
  cible: CibleTraitement;
  dose: number | null;
  doseUnite: UniteDose | null;
  dateDebut: string;
  dateFin: string | null;
  delaiCarenceJours: number | null;
  ordonnance: string | null;
  note: string | null;
}

/**
 * Type d'aliment apporte.
 *
 * <p>Les deux sirops sont distingues parce qu'ils ne servent pas a la meme
 * chose : le 1:1 stimule la ponte au printemps, le 2:1 constitue les reserves
 * d'hiver. Les confondre rendrait le motif illisible.
 */
export type TypeAliment =
  | 'sirop_1_1'
  | 'sirop_2_1'
  | 'candi'
  | 'pollen'
  | 'substitut_pollen'
  | 'miel'
  | 'eau';

export const TYPES_ALIMENT: readonly TypeAliment[] = [
  'sirop_1_1',
  'sirop_2_1',
  'candi',
  'pollen',
  'substitut_pollen',
  'miel',
  'eau',
];

export type UniteQuantite = 'kg' | 'g' | 'l' | 'ml';

export const UNITES_QUANTITE: readonly UniteQuantite[] = ['kg', 'g', 'l', 'ml'];

export type MotifNourrissement =
  | 'stimulation'
  | 'hivernage'
  | 'disette'
  | 'secours'
  | 'transhumance'
  | 'autre';

export const MOTIFS_NOURRISSEMENT: readonly MotifNourrissement[] = [
  'stimulation',
  'hivernage',
  'disette',
  'secours',
  'transhumance',
  'autre',
];

/** Apport nourricier a une ruche. */
export interface Nourrissement {
  id: number;
  rucheId: number;
  rucheModele: string;
  agentId: number;
  agentNom: string;
  visiteId: number | null;
  dateApport: string;
  typeAliment: TypeAliment;
  quantite: number;
  quantiteUnite: UniteQuantite;
  motif: MotifNourrissement | null;
  note: string | null;
  creeLe: string;
  majLe: string;
}

export interface NourrissementCorps {
  rucheId: number;
  agentId: number;
  visiteId: number | null;
  dateApport: string;
  typeAliment: TypeAliment;
  quantite: number;
  quantiteUnite: UniteQuantite;
  motif: MotifNourrissement | null;
  note: string | null;
}

/**
 * Methode de comptage du varroa.
 *
 * <p>Elle decide du denominateur : le lange se rapporte a une DUREE de pose,
 * toute autre methode a un NOMBRE D'ABEILLES. C'est aussi ce qui interdit un
 * taux unique — « 3,5 » ne veut rien dire sans son unite.
 */
export type MethodeVarroa = 'lange' | 'sucre_glace' | 'alcool' | 'co2' | 'desoperculation';

export const METHODES_VARROA: readonly MethodeVarroa[] = [
  'lange',
  'sucre_glace',
  'alcool',
  'co2',
  'desoperculation',
];

/** Le lange compte une chute naturelle ; les autres methodes echantillonnent. */
export const parLange = (methode: MethodeVarroa): boolean => methode === 'lange';

export type UniteTauxVarroa = 'varroas_par_jour' | 'pour_cent_abeilles';

/** `inconnu` quand le denominateur manque : rassurer sans savoir serait pire. */
export type VerdictVarroa = 'faible' | 'surveiller' | 'traiter' | 'inconnu';

export interface ComptageVarroa {
  id: number;
  rucheId: number;
  rucheModele: string;
  agentId: number;
  agentNom: string;
  visiteId: number | null;
  dateComptage: string;
  methode: MethodeVarroa;
  varroasComptes: number;
  abeillesEchantillon: number | null;
  joursExposition: number | null;
  taux: number | null;
  tauxUnite: UniteTauxVarroa;
  verdict: VerdictVarroa;
  note: string | null;
  creeLe: string;
  majLe: string;
}

export interface ComptageVarroaCorps {
  rucheId: number;
  agentId: number;
  visiteId: number | null;
  dateComptage: string;
  methode: MethodeVarroa;
  varroasComptes: number;
  abeillesEchantillon: number | null;
  joursExposition: number | null;
  note: string | null;
}

/** Maladies et ravageurs NOMMES, constates pendant une visite. */
export type Pathologie =
  | 'varroose'
  | 'loque_americaine'
  | 'loque_europeenne'
  | 'nosemose'
  | 'petit_coleoptere'
  | 'fausse_teigne'
  | 'frelon_asiatique'
  | 'couvain_sacciforme'
  | 'mycose'
  | 'pesticide'
  | 'autre';

export const PATHOLOGIES: readonly Pathologie[] = [
  'varroose',
  'loque_americaine',
  'loque_europeenne',
  'nosemose',
  'petit_coleoptere',
  'fausse_teigne',
  'frelon_asiatique',
  'couvain_sacciforme',
  'mycose',
  'pesticide',
  'autre',
];

/** « suspectee » par defaut : au rucher on constate un symptome, on ne diagnostique pas. */
export type GravitePathologie = 'suspectee' | 'legere' | 'moderee' | 'severe';

export const GRAVITES_PATHOLOGIE: readonly GravitePathologie[] = [
  'suspectee',
  'legere',
  'moderee',
  'severe',
];

export interface PathologieObservee {
  id: number;
  pathologie: Pathologie;
  gravite: GravitePathologie;
  note: string | null;
}

export interface PathologieCorps {
  pathologie: Pathologie;
  gravite: GravitePathologie | null;
  note: string | null;
}

export type MotifPonte = 'compact' | 'lacunaire' | 'irregulier' | 'absent';

export const MOTIFS_PONTE: readonly MotifPonte[] = [
  'compact',
  'lacunaire',
  'irregulier',
  'absent',
];

/** Pourquoi la colonie eleve : la donnee actionnable derriere un nombre de cellules. */
export type CauseCellules = 'essaimage' | 'supersedure' | 'urgence';

export const CAUSES_CELLULES: readonly CauseCellules[] = ['essaimage', 'supersedure', 'urgence'];

export type Temperament = 'doux' | 'normal' | 'agressif';

export const TEMPERAMENTS: readonly Temperament[] = ['doux', 'normal', 'agressif'];

/**
 * Grille d'inspection structuree d'une visite (SPRINT-20).
 *
 * <p>Ce qui vivait en texte libre dans `constatations` et n'etait donc
 * analysable par rien. Le texte libre reste — il porte ce qu'aucune case ne
 * prevoit — mais il cesse d'etre la SEULE trace du couvain, des reserves et des
 * cellules royales.
 *
 * <p><strong>Chaque champ vaut `null` tant qu'il n'a pas ete observe</strong>, et
 * c'est la distinction a tenir jusque dans les formulaires : « non observe » et
 * « non » ne disent pas la meme chose, et une statistique construite sur leur
 * confusion serait fausse. La visite entiere rend `null` quand rien n'a ete
 * coche, plutot qu'un objet plein de `null`.
 */
export interface ObservationVisite {
  couvainOeufs: boolean | null;
  couvainLarves: boolean | null;
  couvainOpercule: boolean | null;
  motifPonte: MotifPonte | null;
  reineVue: boolean | null;
  cellulesRoyales: number | null;
  cellulesRoyalesCause: CauseCellules | null;
  cadresCouvain: number | null;
  cadresMiel: number | null;
  cadresPollen: number | null;
  temperament: Temperament | null;
}

/** D'ou vient le releve : une estimation ne se traite pas comme une mesure. */
export type SourceMeteo = 'open-meteo' | 'simulation' | 'saisie';

/**
 * Meteo FIGEE au moment de la visite (SPRINT-20).
 *
 * <p>Recopiee du fournisseur a la saisie, jamais rappelee ensuite : une
 * prevision se revise, un releve non. Aller rechercher la meteo du 12 mars six
 * mois plus tard donnerait la valeur reconstituee d'aujourd'hui, et rendrait
 * fausse la correlation meteo x production qu'elle sert precisement a etablir.
 */
export interface MeteoVisite {
  temperatureCelsius: number | null;
  humiditePourcent: number | null;
  ventKmh: number | null;
  source: SourceMeteo | null;
}
