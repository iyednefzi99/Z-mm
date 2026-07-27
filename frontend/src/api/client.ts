/**
 * Client d'API.
 *
 * <p>Ce client reste ECRIT A LA MAIN, et c'est un choix (SPRINT-17). L'en-tete
 * precedent annonçait une generation complete depuis le contrat OpenAPI ; elle
 * aurait touche quarante fonctions et seize vues pour un gain limite au seul
 * typage — les fonctions elles-memes sont courtes, lisibles et stables.
 *
 * <p>Ce qui manquait vraiment, c'etait la GARANTIE : rien n'empechait les types
 * du client de deriver de ceux du serveur, et une propriete renommee ne se voyait
 * qu'a l'execution, sous la forme d'un champ vide. `api/parite.ts` la fournit —
 * il confronte chaque type ecrit ici au contrat publie, et fait echouer la
 * compilation en cas de divergence.
 *
 * <p>Ce dispositif a paye des sa premiere execution : il a revele que le contrat
 * publie decrivait `LocalTime` comme un objet alors que l'API serialise une
 * chaine. Le defaut etait dans le CONTRAT, pas dans ce fichier — et il aurait
 * casse le client de tout integrateur tiers (US-026).
 *
 * <p>Regenerer apres toute evolution de l'API :
 * `cd backend && ./mvnw verify -Dit.test=ContratOpenApiIT`, puis
 * `cd frontend && npm run api:contrat`. La CI verifie les deux.
 */

import { definir } from '../auth/session';
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
  GrappeSites,
  AlerteMesure,
  Anomalie,
  LigneProduction,
  Meteo,
  MesureCorps,
  MesureReponse,
  AuditEntree,
  Photo,
  PhotoCorps,
  Planning,
  PlanningCorps,
  PointJournalier,
  PrevisionRecolte,
  QuantiteMiel,
  Recolte,
  RecolteCorps,
  Reine,
  ReineCorps,
  Ruche,
  RucheCorps,
  Seuils,
  Site,
  SiteCorps,
  Synthese,
  Tache,
  TacheCorps,
  Tournee,
  Lot,
  LotCorps,
  MentionOrigine,
  Trace,
  TypeIndicateur,
  Visite,
  VisiteCorps,
  VoisinSite,
} from './types';

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

/**
 * Jeton anti-CSRF, déposé par le serveur dans un cookie LISIBLE (ADR-006).
 *
 * <p>Ce n'est pas un secret : c'est une preuve que la requête part bien de notre
 * page. Un site tiers peut faire envoyer le cookie de session par le navigateur,
 * mais la politique d'origine l'empêche de LIRE ce cookie-ci, donc de fabriquer
 * l'en-tête correspondant.
 */
export function jetonCsrf(): string | null {
  const trouve = document.cookie
    .split('; ')
    .find((morceau) => morceau.startsWith('XSRF-TOKEN='));
  return trouve ? decodeURIComponent(trouve.slice('XSRF-TOKEN='.length)) : null;
}

/**
 * En-têtes communs à tout appel d'API.
 *
 * <p>Plus d'en-tête `Authorization` : le navigateur ne détient aucun jeton. Le
 * cookie de session part tout seul grâce à `credentials: 'include'`, et c'est
 * précisément parce qu'il part tout seul que le jeton CSRF accompagne les
 * mutations.
 */
function enTetesCommuns(options: RequestInit, methode: string): Headers {
  const enTetes = new Headers(options.headers);
  enTetes.set('Accept', 'application/json');
  if (options.body) {
    enTetes.set('Content-Type', 'application/json');
  }
  if (MUTATIONS.has(methode)) {
    const jeton = jetonCsrf();
    if (jeton) {
      enTetes.set('X-XSRF-TOKEN', jeton);
    }
  }
  return enTetes;
}

async function requete<T>(url: string, options: RequestInit = {}, cle?: string): Promise<T> {
  const methode = (options.method ?? 'GET').toUpperCase();
  const enTetes = enTetesCommuns(options, methode);
  // Clé d'idempotence (US-055) : générée UNE fois par mutation logique, puis
  // conservée à travers le rejeu après rafraîchissement du jeton et jusqu'à la
  // synchronisation hors-ligne. Une clé regénérée à chaque tentative ne
  // protégerait de rien — c'est justement la répétition qu'elle doit désigner.
  const cleIdempotence = MUTATIONS.has(methode) ? (cle ?? crypto.randomUUID()) : undefined;
  if (cleIdempotence) {
    enTetes.set('Idempotency-Key', cleIdempotence);
  }

  let reponse: Response;
  try {
    reponse = await fetch(url, { ...options, headers: enTetes, credentials: 'include' });
  } catch (cause) {
    // Panne réseau : une mutation est mise en file (US-011) ; une lecture échoue.
    // La clé part avec elle : `fetch` échoue aussi quand la requête est arrivée
    // et que seule la réponse s'est perdue.
    if (MUTATIONS.has(methode)) {
      enfiler({
        methode: methode as MutationEnAttente['methode'],
        url,
        corps: typeof options.body === 'string' ? options.body : undefined,
        cle: cleIdempotence,
      });
      throw new ErreurHorsLigne();
    }
    throw cause;
  }

  // Session expirée. Le RAFRAÎCHISSEMENT n'est plus l'affaire du navigateur : le
  // serveur détient les jetons et les renouvelle lui-même (ADR-006). Un 401 ici
  // signifie donc que même le serveur n'a plus de session valide — il faut se
  // reconnecter, et l'interface doit le montrer plutôt que laisser l'utilisateur
  // devant des erreurs muettes.
  if (reponse.status === 401) {
    definir(null);
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

/** Page de resultats : le contenu, et le total porte par l'en-tete (US-052). */
export interface PageResultat<E> {
  elements: E[];
  total: number;
  page: number;
  taille: number;
}

/**
 * Variante de {@link requete} qui lit aussi les en-tetes de pagination (US-052).
 *
 * <p>Le corps reste un tableau JSON : c'est `X-Total-Count` qui porte le total.
 * Cote serveur, ce choix evite de changer la forme de la reponse selon la presence
 * d'un parametre.
 */
async function requetePaginee<E>(url: string, page: number, taille: number): Promise<PageResultat<E>> {
  const reponse = await fetch(`${url}?page=${page}&taille=${taille}`, {
    headers: { Accept: 'application/json' },
    credentials: 'include',
  });

  if (reponse.status === 401) {
    definir(null);
    throw new ErreurApi(401, 'Session expirée ou absente.');
  }
  if (!reponse.ok) {
    throw new ErreurApi(reponse.status, await detailErreur(reponse));
  }

  const elements = (await reponse.json()) as E[];
  // En-tete absent (serveur ancien) : le total se rabat sur ce qui est arrive.
  const total = Number(reponse.headers.get('X-Total-Count') ?? elements.length);
  return { elements, total, page, taille };
}

/** Fabrique les operations CRUD d'une ressource, pour eviter la repetition. */
function ressource<E, C>(base: string) {
  return {
    lister: () => requete<E[]>(base),
    listerPage: (page: number, taille: number) => requetePaginee<E>(base, page, taille),
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

/** US-015 : synthèse de pilotage et ROI. */
export const chargerSynthese = () => requete<Synthese>('/api/tableaux/synthese');

/** US-042 (SPRINT-09) : prévisions de récolte (tendance du poids par ruche). */
export const chargerPrevisions = () =>
  requete<PrevisionRecolte[]>('/api/tableaux/previsions');

/** US-043 (SPRINT-09) : journal d'audit (responsable/admin). */
export const chargerAudit = () => requete<AuditEntree[]>('/api/audit');

/** US-017 : ingestion d'une mesure de capteur. */
export const ingererMesure = (corps: MesureCorps) =>
  requete<MesureReponse>('/api/mesures', { method: 'POST', ...corpsJson(corps) });

/** US-018 : alertes de seuils actuellement ouvertes. */
export const chargerAlertesOuvertes = () => requete<AlerteMesure[]>('/api/mesures/alertes');

/**
 * Série JOURNALIÈRE d'un indicateur (SPRINT-18) : un point par jour.
 *
 * <p>C'est ce que consomme la courbe. L'agrégation se fait en base, là où sont
 * les données : environ cent fois moins d'octets transportés pour un graphique
 * identique à l'œil.
 */
export const chargerSerieJournaliere = (rucheId: number, type: TypeIndicateur) =>
  requete<PointJournalier[]>(`/api/mesures/journalier?rucheId=${rucheId}&type=${type}`);

/** US-026 : service tierce getZummHoneyActualQuantity. */
export const getZummHoneyActualQuantity = (rucheId: number | null, unite: string) =>
  requete<QuantiteMiel>(
    `/api/services/getZummHoneyActualQuantity?${rucheId != null ? `rucheId=${rucheId}&` : ''}unite=${unite}`,
  );

/** US-029 : contexte météo d'un site. */
export const chargerMeteo = (siteId: number) => requete<Meteo>(`/api/meteo?siteId=${siteId}`);

/** US-032 : suivi de la reine. */
export const listerReines = (rucheId: number) =>
  requete<Reine[]>(`/api/reines?rucheId=${rucheId}`);
export const enregistrerReine = (corps: ReineCorps) =>
  requete<Reine>('/api/reines', { method: 'POST', ...corpsJson(corps) });
export const supprimerReine = (id: number) =>
  requete<void>(`/api/reines/${id}`, { method: 'DELETE' });

/** US-033 : récoltes et traçabilité. */
export const recoltes = ressource<Recolte, RecolteCorps>('/api/recoltes');
export const tracerLot = (lot: string) =>
  requete<Trace>(`/api/recoltes/tracabilite/${encodeURIComponent(lot)}`);

/**
 * US-056 : lots de conditionnement et mention d'origine (directive (UE) 2024/1438).
 */
export const lots = ressource<Lot, LotCorps>('/api/lots');

/**
 * Mention d'origine prête à imprimer. La langue est négociée par l'en-tête :
 * un miel exporté s'étiquette dans la langue du marché, pas dans celle du
 * producteur.
 */
export const chargerMentionOrigine = (id: number, langue: string) =>
  requete<MentionOrigine>(`/api/lots/${id}/mention`, {
    headers: { 'Accept-Language': langue },
  });

/** US-034 : détection d'anomalie EWMA. */
export const detecterAnomalie = (rucheId: number, type: TypeIndicateur) =>
  requete<Anomalie>(`/api/anomalies?rucheId=${rucheId}&type=${type}`);

/**
 * US-027 : export CSV/TXT. L'API exige le jeton en en-tête, donc on télécharge via
 * fetch + blob plutôt qu'un simple lien, puis on déclenche l'enregistrement.
 */
export const telechargerExport = async (
  ressourceExport: 'visites' | 'ruches',
  format: 'csv' | 'txt',
): Promise<void> => {
  const reponse = await fetch(`/api/export/${ressourceExport}?format=${format}`, { credentials: 'include' });
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

/**
 * US-044 (SPRINT-09) : télécharge le rapport de visite en PDF. Comme l'export, le
 * jeton doit voyager en en-tête, d'où le fetch + blob plutôt qu'un simple lien.
 */
export const telechargerRapportVisite = async (visiteId: number): Promise<void> => {
  const reponse = await fetch(`/api/visites/${visiteId}/rapport.pdf`, { credentials: 'include' });
  if (!reponse.ok) {
    throw new ErreurApi(reponse.status, await detailErreur(reponse));
  }
  const blob = await reponse.blob();
  const url = URL.createObjectURL(blob);
  const lien = document.createElement('a');
  lien.href = url;
  lien.download = `rapport-visite-${visiteId}.pdf`;
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

/** Regroupement des sites par proximite, calcule par PostGIS (US-045). */
export const grappesSites = (distanceMetres = 15000, minimumSites = 2) =>
  requete<GrappeSites[]>(
    `/api/sites/grappes?distanceMetres=${distanceMetres}&minimumSites=${minimumSites}`,
  );

/** Sites les plus proches d'un site donne, distance a l'appui (US-046). */
export const voisinsSite = (siteId: number, limite = 3) =>
  requete<VoisinSite[]>(`/api/sites/${siteId}/voisins?limite=${limite}`);

/** Ordre de tournee propose a un agent pour une journee (US-047). */
export const tourneeAgent = (agentId: number, date: string, departSiteId?: number) => {
  const depart = departSiteId === undefined ? '' : `&departSiteId=${departSiteId}`;
  return requete<Tournee>(`/api/plannings/tournee?agentId=${agentId}&date=${date}${depart}`);
};

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
    // Même clé qu'à la première tentative : si le serveur avait déjà traité la
    // mutation, il rejoue sa réponse au lieu de créer un doublon.
    enTetes['Idempotency-Key'] = m.cle;
    const csrf = jetonCsrf();
    if (csrf) {
      enTetes['X-XSRF-TOKEN'] = csrf;
    }
    try {
      const r = await fetch(m.url, {
        method: m.methode,
        headers: enTetes,
        body: m.corps,
        credentials: 'include',
      });
      if (r.status === 401 || r.status === 403) {
        // Session expirée pendant la coupure — le cas le plus courant après une
        // journée sur le terrain. Ce n'est PAS un refus métier : on garde la
        // saisie en file plutôt que de la détruire.
        return { ok: false, reseau: false, session: true };
      }
      return { ok: r.ok || (r.status >= 400 && r.status < 500), reseau: false };
    } catch {
      return { ok: false, reseau: true };
    }
  });
