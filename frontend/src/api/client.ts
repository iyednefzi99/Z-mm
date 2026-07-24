/**
 * Client d'API — implementation provisoire du SPRINT-00/01.
 *
 * ATTENTION : des que le backend publiera son contrat OpenAPI 3, ce client devra
 * etre GENERE depuis ce contrat, et non plus ecrit a la main, afin de garantir la
 * parite des types client/serveur.
 */

import { jetonCourant, fermerSession } from '../auth/session';
import { enfiler, rejouer, type MutationEnAttente } from '../offline/file';
import type {
  Agent,
  AgentCorps,
  AlerteSanitaire,
  CalendrierCellule,
  Ferme,
  FermeCorps,
  Fermier,
  FermierCorps,
  LigneProduction,
  Photo,
  PhotoCorps,
  Planning,
  PlanningCorps,
  Ruche,
  RucheCorps,
  Seuils,
  Site,
  SiteCorps,
  Tache,
  TacheCorps,
  Visite,
  VisiteCorps,
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

/** Mutation mise en file faute de réseau (US-011) : sera synchronisée plus tard. */
export class ErreurHorsLigne extends Error {
  constructor() {
    super('Hors ligne : opération mise en file pour synchronisation.');
    this.name = 'ErreurHorsLigne';
  }
}

const MUTATIONS = new Set(['POST', 'PUT', 'DELETE']);

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

  const methode = (options.method ?? 'GET').toUpperCase();
  let reponse: Response;
  try {
    reponse = await fetch(url, { ...options, headers: enTetes });
  } catch (cause) {
    // Panne réseau : une mutation est mise en file (US-011) ; une lecture échoue.
    if (MUTATIONS.has(methode)) {
      enfiler({
        methode: methode as MutationEnAttente['methode'],
        url,
        corps: typeof options.body === 'string' ? options.body : undefined,
      });
      throw new ErreurHorsLigne();
    }
    throw cause;
  }

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
export const ruches = ressource<Ruche, RucheCorps>('/api/ruches');
export const plannings = ressource<Planning, PlanningCorps>('/api/plannings');
export const visites = ressource<Visite, VisiteCorps>('/api/visites');
export const taches = ressource<Tache, TacheCorps>('/api/taches');

/** US-031 : rappels en cours (tâches non faites déjà échues). */
export const listerRappels = () => requete<Tache[]>('/api/taches/rappels');

/** US-012 : calendrier matriciel agents × ruches sur une période. */
export const chargerCalendrier = (debut: string, fin: string) =>
  requete<CalendrierCellule[]>(`/api/tableaux/calendrier?debut=${debut}&fin=${fin}`);

/** US-013 : tableau de bord production (poids par ruche). */
export const chargerProduction = () => requete<LigneProduction[]>('/api/tableaux/production');

/** US-014 : alertes sanitaires par ruche. */
export const chargerAlertesSanitaires = () =>
  requete<AlerteSanitaire[]>('/api/tableaux/alertes-sanitaires');

/**
 * US-027 : export CSV/TXT. L'API exige le jeton en en-tête, donc on télécharge via
 * fetch + blob plutôt qu'un simple lien, puis on déclenche l'enregistrement.
 */
export const telechargerExport = async (
  ressourceExport: 'visites' | 'ruches',
  format: 'csv' | 'txt',
): Promise<void> => {
  const jeton = jetonCourant();
  const reponse = await fetch(`/api/export/${ressourceExport}?format=${format}`, {
    headers: jeton ? { Authorization: `Bearer ${jeton}` } : {},
  });
  if (!reponse.ok) {
    throw new ErreurApi(reponse.status, await detailErreur(reponse));
  }
  const blob = await reponse.blob();
  const url = URL.createObjectURL(blob);
  const lien = document.createElement('a');
  lien.href = url;
  lien.download = `zumm-${ressourceExport}.${format}`;
  document.body.appendChild(lien);
  lien.click();
  lien.remove();
  URL.revokeObjectURL(url);
};

/** US-008 : décision du superviseur sur un planning. */
export const approuverPlanning = (id: number) =>
  requete<Planning>(`/api/plannings/${id}/approuver`, { method: 'POST' });
export const refuserPlanning = (id: number, motif: string) =>
  requete<Planning>(`/api/plannings/${id}/refuser`, { method: 'POST', ...corpsJson({ motif }) });

/** US-010/028 : photos d'une visite. */
export const listerPhotos = (visiteId: number) =>
  requete<Photo[]>(`/api/visites/${visiteId}/photos`);
export const ajouterPhoto = (visiteId: number, corps: PhotoCorps) =>
  requete<Photo>(`/api/visites/${visiteId}/photos`, { method: 'POST', ...corpsJson(corps) });
export const supprimerPhoto = (visiteId: number, photoId: number) =>
  requete<void>(`/api/visites/${visiteId}/photos/${photoId}`, { method: 'DELETE' });

/** Sites du tenant a proximite d'un point (US-003, PostGIS). */
export const sitesProches = (latitude: number, longitude: number, rayonMetres: number) =>
  requete<Site[]>(
    `/api/sites/proches?latitude=${latitude}&longitude=${longitude}&rayonMetres=${rayonMetres}`,
  );

/** Seuils metier lus depuis ConfigZumm.ini (US-025). */
export const recupererSeuils = () => requete<Seuils>('/api/configuration/seuils');

/**
 * Rejoue les mutations mises en file hors-ligne (US-011). À brancher sur
 * l'événement `online`. Chaque mutation est renvoyée avec le jeton courant.
 */
export const synchroniser = (): Promise<void> =>
  rejouer(async (m) => {
    const enTetes: Record<string, string> = { Accept: 'application/json' };
    if (m.corps) {
      enTetes['Content-Type'] = 'application/json';
    }
    const jeton = jetonCourant();
    if (jeton) {
      enTetes.Authorization = `Bearer ${jeton}`;
    }
    try {
      const r = await fetch(m.url, { method: m.methode, headers: enTetes, body: m.corps });
      return { ok: r.ok || (r.status >= 400 && r.status < 500), reseau: false };
    } catch {
      return { ok: false, reseau: true };
    }
  });

export const recupererInfo = async (langue: string, signal?: AbortSignal): Promise<Info> => {
  const reponse = await fetch('/api/info', { headers: { 'Accept-Language': langue }, signal });
  if (!reponse.ok) {
    throw new Error(`Réponse ${reponse.status} de /api/info`);
  }
  return (await reponse.json()) as Info;
};
