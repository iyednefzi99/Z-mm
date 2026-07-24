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
  creeLe: string;
  majLe: string;
}

export interface AgentCorps {
  nom: string;
  role: RoleAgent;
  fermeId: number | null;
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
