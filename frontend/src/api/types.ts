/**
 * Types du domaine, en miroir des DTO du backend (US-001 a US-005, US-025).
 *
 * ATTENTION : provisoire, comme le client. Des que le backend publiera son
 * contrat OpenAPI 3, ces types seront GENERES depuis le contrat pour garantir la
 * parite client/serveur, et ce fichier disparaitra.
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

/** Contexte météo local d'un site (US-029). */
export interface Meteo {
  siteId: number;
  latitude: number;
  longitude: number;
  temperatureCelsius: number;
  humiditePourcent: number | null;
  ventKmh: number | null;
  source: string;
  instant: string;
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
