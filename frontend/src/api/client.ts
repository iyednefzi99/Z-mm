/**
 * Client d'API — implementation provisoire du SPRINT-00/01.
 *
 * ATTENTION : des que le backend publiera son contrat OpenAPI 3, ce client devra
 * etre GENERE depuis ce contrat, et non plus ecrit a la main, afin de garantir la
 * parite des types client/serveur.
 */

import { jetonCourant, fermerSession } from '../auth/session';
import type {
  Agent,
  AgentCorps,
  Ferme,
  FermeCorps,
  Fermier,
  FermierCorps,
  Seuils,
  Site,
  SiteCorps,
} from './types';

export interface Info {
  nom: string;
  version: string;
  accueil: string;
  langues: string[];
}

/** Erreur d'API portant le statut HTTP et le detail (ProblemDetail cote serveur). */
export class ErreurApi extends Error {
  constructor(
    readonly statut: number,
    readonly detail: string,
  ) {
    super(detail);
    this.name = 'ErreurApi';
  }
}

async function requete<T>(url: string, options: RequestInit = {}): Promise<T> {
  const jeton = jetonCourant();
  const enTetes = new Headers(options.headers);
  enTetes.set('Accept', 'application/json');
  if (options.body) {
    enTetes.set('Content-Type', 'application/json');
  }
  if (jeton) {
    enTetes.set('Authorization', `Bearer ${jeton}`);
  }

  const reponse = await fetch(url, { ...options, headers: enTetes });

  // Un jeton expire ou absent : on ferme la session pour ramener a l'ecran de
  // connexion, plutot que de laisser l'utilisateur devant des erreurs muettes.
  if (reponse.status === 401) {
    fermerSession();
    throw new ErreurApi(401, 'Session expirée ou absente.');
  }

  if (!reponse.ok) {
    throw new ErreurApi(reponse.status, await detailErreur(reponse));
  }

  if (reponse.status === 204) {
    return undefined as T;
  }
  return (await reponse.json()) as T;
}

async function detailErreur(reponse: Response): Promise<string> {
  try {
    const corps = (await reponse.json()) as { detail?: string; title?: string };
    return corps.detail ?? corps.title ?? `Erreur ${reponse.status}`;
  } catch {
    return `Erreur ${reponse.status}`;
  }
}

const corpsJson = (donnees: unknown): RequestInit => ({ body: JSON.stringify(donnees) });

/** Fabrique les operations CRUD d'une ressource, pour eviter la repetition. */
function ressource<E, C>(base: string) {
  return {
    lister: () => requete<E[]>(base),
    obtenir: (id: number) => requete<E>(`${base}/${id}`),
    creer: (corps: C) => requete<E>(base, { method: 'POST', ...corpsJson(corps) }),
    mettreAJour: (id: number, corps: C) =>
      requete<E>(`${base}/${id}`, { method: 'PUT', ...corpsJson(corps) }),
    supprimer: (id: number) => requete<void>(`${base}/${id}`, { method: 'DELETE' }),
  };
}

export const fermiers = ressource<Fermier, FermierCorps>('/api/fermiers');
export const fermes = ressource<Ferme, FermeCorps>('/api/fermes');
export const sites = ressource<Site, SiteCorps>('/api/sites');
export const agents = ressource<Agent, AgentCorps>('/api/agents');

/** Sites du tenant a proximite d'un point (US-003, PostGIS). */
export const sitesProches = (latitude: number, longitude: number, rayonMetres: number) =>
  requete<Site[]>(
    `/api/sites/proches?latitude=${latitude}&longitude=${longitude}&rayonMetres=${rayonMetres}`,
  );

/** Seuils metier lus depuis ConfigZumm.ini (US-025). */
export const recupererSeuils = () => requete<Seuils>('/api/configuration/seuils');

export const recupererInfo = async (langue: string, signal?: AbortSignal): Promise<Info> => {
  const reponse = await fetch('/api/info', { headers: { 'Accept-Language': langue }, signal });
  if (!reponse.ok) {
    throw new Error(`Réponse ${reponse.status} de /api/info`);
  }
  return (await reponse.json()) as Info;
};
