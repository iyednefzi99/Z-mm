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
