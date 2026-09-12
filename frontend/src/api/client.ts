/**
 * Client d'API.
 *
 * <p>Ce client reste ECRIT A LA MAIN, et c'est un choix (SPRINT-17). L'en-tete
 * precedent annonçait une generation complete depuis le contrat OpenAPI ; elle
 * aurait touche quarante fonctions et dix-neuf vues pour un gain limite au seul
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
  Abonnement,
  AbonnementCorps,
  Agent,
  AgentCorps,
  AlerteMesure,
  AlerteSanitaire,
  Anomalie,
  AuditEntree,
  BilanExploitation,
  Briefing,
  Brouillon,
  BrouillonCorps,
  CalendrierCellule,
  CaptureEssaim,
  CaptureEssaimCorps,
  ChargeAgent,
  CibleLot,
  CiblePhoto,
  ComparaisonSaisons,
  ComparaisonSite,
  CouvertRucher,
  ComptageVarroa,
  ComptageVarroaCorps,
  ConstatCouvertCorps,
  Consommable,
  ConsommableCorps,
  Conversion,
  CorrelationFlore,
  CorrelationMeteo,
  DemenagementCorps,
  ExpositionRucher,
  FeuilleChargement,
  FiabiliteCouvert,
  ParcelleCouvert,
  ZoneTraitee,
  ZoneTraiteeCorps,
  Depense,
  DepenseCorps,
  DossierConformite,
  Division,
  DivisionCorps,
  Emplacement,
  EmportRucher,
  EtatDemonstration,
  Genealogie,
  IndexGenetique,
  Ferme,
  FermeCorps,
  FloraisonCorps,
  FloraisonObservee,
  Fermier,
  FermierCorps,
  Gabarit,
  GabaritCorps,
  GrappeSites,
  IndiceColonie,
  Invitation,
  InvitationCorps,
  LigneProduction,
  Lot,
  LotCorps,
  Materiel,
  MaterielCorps,
  MentionOrigine,
  MesureCompartimentCorps,
  MesureCorps,
  MesureReponse,
  Meteo,
  Nourrissement,
  NourrissementCorps,
  Partage,
  PartageCorps,
  Photo,
  PhotoCibleCorps,
  PhotoCorps,
  Planning,
  PlanningCorps,
  PoidsCompartiment,
  PointJournalier,
  PointReferentiel,
  PrevisionRecolte,
  ProduitReferentiel,
  QuantiteMiel,
  RapportLot,
  Recolte,
  RecolteCorps,
  RecolteLotCorps,
  ReineElevage,
  ReineElevageCorps,
  Refractometre,
  Reine,
  ReineCorps,
  ResultatRecherche,
  Ruche,
  RucheCorps,
  Seuils,
  SerieCorps,
  SerieElevage,
  Site,
  SiteCorps,
  StatistiquePoint,
  Synthese,
  SyntheseRucher,
  Tache,
  TacheCorps,
  Tournee,
  Trace,
  Traitement,
  TraitementCorps,
  Transport,
  TransportCorps,
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
        // La garde de version (SPRINT-24) part avec la mutation : c'est au rejeu,
        // des heures plus tard, qu'elle sert — pas maintenant.
        entetes: enTetes.has('X-Zumm-Version')
          ? { 'X-Zumm-Version': enTetes.get('X-Zumm-Version') as string }
          : undefined,
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
    /**
     * Modifie une ressource. `entetes` porte les PRECONDITIONS (SPRINT-24).
     *
     * <p>Le seul usage aujourd'hui est `X-Zumm-Version` sur la visite : la
     * version que l'appelant avait sous les yeux. Elle voyage en en-tête et non
     * dans le corps parce que ce n'est pas une donnée métier — la mettre dans le
     * DTO l'aurait imposée à tous les appelants, y compris ceux qui modifient ce
     * qu'ils viennent de lire.
     */
    mettreAJour: (id: number, corps: C, entetes?: Record<string, string>) =>
      requete<E>(`${base}/${id}`, { method: 'PUT', headers: entetes, ...corpsJson(corps) }),
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

/**
 * US-058 : codes d'invitation (ADR-009).
 *
 * <p>Pas de `ressource()` générique : un code ne se MODIFIE pas. Le changer
 * après émission invaliderait celui déjà communiqué, sans que personne ne le
 * sache ; on en émet un autre et on révoque l'ancien.
 */
export const listerInvitations = () => requete<Invitation[]>('/api/invitations');

export const emettreInvitation = (corps: InvitationCorps) =>
  requete<Invitation>('/api/invitations', { method: 'POST', ...corpsJson(corps) });

export const revoquerInvitation = (id: number) =>
  requete<void>(`/api/invitations/${id}`, { method: 'DELETE' });

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

/**
 * Pousse plusieurs mesures en une requête (SPRINT-31, lot F₂).
 *
 * <p>Ce que toute passerelle demande, et ce dont se sert la lecture Bluetooth :
 * un capteur rend trois valeurs d'un coup, et trois requêtes pour un seul geste
 * feraient trois occasions d'échouer à moitié. Le serveur traite le lot en une
 * transaction — tout passe ou rien ne passe.
 */
export const ingererMesures = (corps: MesureCorps[]) =>
  requete<MesureReponse[]>('/api/mesures/lot', { method: 'POST', ...corpsJson(corps) });

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

/**
 * Série BRUTE d'un indicateur : les mesures telles qu'elles sont arrivées.
 *
 * <p>Ce n'est pas la même chose que la série journalière, et c'est pour cela que
 * le serveur expose les deux : l'agrégée sert la courbe, la brute sert le
 * diagnostic du capteur. Un boîtier qui émet toutes les trente secondes et un
 * boîtier muet depuis deux jours rendent le MÊME point journalier — seule la
 * série brute les distingue.
 */
export const chargerSerieBrute = (rucheId: number, type: TypeIndicateur) =>
  requete<MesureReponse[]>(`/api/mesures?rucheId=${rucheId}&type=${type}`);

/** US-026 : service tierce getZummHoneyActualQuantity. */
export const getZummHoneyActualQuantity = (rucheId: number | null, unite: string) =>
  requete<QuantiteMiel>(
    `/api/services/getZummHoneyActualQuantity?${rucheId != null ? `rucheId=${rucheId}&` : ''}unite=${unite}`,
  );

/**
 * US-029 : contexte météo d'un site, prévisions comprises.
 *
 * `jours` est l'horizon de prévision (0 pour n'obtenir que l'instantané, 16 au
 * plus — au-delà, le serveur ramène la demande à son plafond plutôt que de la
 * rejeter). Un seul aller-retour rend les deux : le serveur n'interroge lui-même
 * le fournisseur qu'une fois.
 */
/** Identite de l'application, servie SANS jeton (`GET /api/info`). */
export interface InfoApplication {
  nom: string;
  version: string;
  accueil: string;
  langues: string[];
  /**
   * Parcours « mot de passe oublié » du fournisseur d'identité, ou chaîne VIDE
   * s'il n'est pas configuré (SPRINT-25).
   *
   * <p>Vide, l'application continue d'aiguiller vers le responsable
   * d'exploitation : afficher un lien qui mène à un formulaire dont le courriel
   * ne partira jamais est pire que ne rien afficher.
   */
  reinitialisationUrl: string;
  /**
   * Le SERVEUR s'autorise-t-il des appels sortants (SPRINT-30, lot G) ?
   *
   * <p>À ne pas confondre avec le mode local de CET appareil (`modeLocal()`) :
   * le premier coupe la météo et le microservice d'anomalie, le second les
   * tuiles de carte. Les deux moitiés se règlent séparément, et l'écran doit le
   * dire — sans quoi l'exploitant croirait son poste muet alors qu'il continue
   * de télécharger des fonds de carte chez un tiers.
   */
  reseauSortant: boolean;
}

/**
 * Lit l'identite de l'application — le seul endpoint metier ouvert a un
 * visiteur (`permitAll` dans `SecurityConfig`).
 *
 * <p>Volontairement HORS de `requete` : celle-ci efface la session sur un 401,
 * et une page publique ne doit pouvoir deconnecter personne. Aucun cookie n'est
 * envoye non plus — il n'y en a pas besoin, et ne pas l'envoyer est la seule
 * facon d'en etre sur.
 *
 * <p>La langue est passee en `Accept-Language` : le serveur traduit le message
 * d'accueil, et il n'a aucun moyen de connaitre la langue choisie dans
 * l'interface autrement.
 */
export const chargerInfo = async (langue: string): Promise<InfoApplication> => {
  const reponse = await fetch('/api/info', {
    credentials: 'omit',
    headers: { Accept: 'application/json', 'Accept-Language': langue },
  });
  if (!reponse.ok) {
    throw new ErreurApi(reponse.status, await detailErreur(reponse));
  }
  return (await reponse.json()) as InfoApplication;
};

export const chargerMeteo = (siteId: number, jours = 7) =>
  requete<Meteo>(`/api/meteo?siteId=${siteId}&jours=${jours}`);

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

/*
 * Registre sanitaire (SPRINT-20).
 *
 * <p>Pas de `ressource()` generique pour ces trois-la : un acte sanitaire ne se
 * MODIFIE pas. Corriger un traitement deja consigne reviendrait a reecrire un
 * registre d'elevage apres coup — exactement ce qu'un controle vient verifier.
 * On saisit, et on supprime une saisie fausse ; il n'y a pas de troisieme
 * geste. Les listes sont par ruche, comme le suivi de reine (US-032) : ces
 * registres se lisent colonne par colonne, jamais a plat.
 */
export const listerTraitements = (rucheId: number) =>
  requete<Traitement[]>(`/api/traitements?rucheId=${rucheId}`);

export const enregistrerTraitement = (corps: TraitementCorps) =>
  requete<Traitement>('/api/traitements', { method: 'POST', ...corpsJson(corps) });

export const supprimerTraitement = (id: number) =>
  requete<void>(`/api/traitements/${id}`, { method: 'DELETE' });

/**
 * Traitements dont la carence court encore, a l'echelle de l'exploitation.
 *
 * <p>La seule route sanitaire qui ne prend pas de ruche : elle repond a « que
 * puis-je recolter aujourd'hui », et c'est une question de cheptel, pas de
 * colonie.
 */
export const listerCarencesEnCours = () => requete<Traitement[]>('/api/traitements/carence');

export const listerNourrissements = (rucheId: number) =>
  requete<Nourrissement[]>(`/api/nourrissements?rucheId=${rucheId}`);

export const enregistrerNourrissement = (corps: NourrissementCorps) =>
  requete<Nourrissement>('/api/nourrissements', { method: 'POST', ...corpsJson(corps) });

export const supprimerNourrissement = (id: number) =>
  requete<void>(`/api/nourrissements/${id}`, { method: 'DELETE' });

export const listerComptagesVarroa = (rucheId: number) =>
  requete<ComptageVarroa[]>(`/api/varroa?rucheId=${rucheId}`);

export const enregistrerComptageVarroa = (corps: ComptageVarroaCorps) =>
  requete<ComptageVarroa>('/api/varroa', { method: 'POST', ...corpsJson(corps) });

export const supprimerComptageVarroa = (id: number) =>
  requete<void>(`/api/varroa/${id}`, { method: 'DELETE' });

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

/**
 * Sites du tenant a moins de `rayonMetres` d'un point (US-019).
 *
 * <p>Le point est DONNE par l'appelant : le navigateur n'est jamais interroge
 * sur sa position. Un rucher est deja une position sensible (`PolitiquePositions`),
 * en croiser une seconde — celle de l'utilisateur — n'apporterait rien que la
 * saisie de deux coordonnees ne donne deja.
 */
export const sitesProches = (latitude: number, longitude: number, rayonMetres = 5000) =>
  requete<Site[]>(
    `/api/sites/proches?latitude=${latitude}&longitude=${longitude}`
      + `&rayonMetres=${rayonMetres}`,
  );

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
    // En-têtes propres à la mutation, tels qu'ils étaient au moment de la
    // saisie : aujourd'hui `X-Zumm-Version`, la garde de conflit.
    Object.assign(enTetes, m.entetes ?? {});
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
      // Refus du serveur (SPRINT-24). Avant, un 4xx était traité comme « traité »
      // et la saisie disparaissait sans un mot — y compris le 409 de conflit,
      // c'est-à-dire précisément le cas où deux agents avaient travaillé sur la
      // même visite. Le motif est désormais conservé, et l'arbitrage revient à
      // l'apiculteur.
      if (r.status >= 400 && r.status < 500) {
        const probleme = (await r.json().catch(() => ({}))) as {
          detail?: string;
          versionServeur?: string;
        };
        return {
          ok: false,
          reseau: false,
          refus: {
            statut: r.status,
            detail: probleme.detail ?? `Erreur ${r.status}`,
            versionServeur: probleme.versionServeur,
          },
        };
      }
      return { ok: r.ok, reseau: false };
    } catch {
      return { ok: false, reseau: true };
    }
  });

/**
 * Historique des emplacements d'un rucher (SPRINT-21, transhumance).
 *
 * <p>Les positions y arrivent masquees comme partout ailleurs : la suite des
 * emplacements est une carte plus riche que la position courante.
 */
export const emplacementsSite = (siteId: number) =>
  requete<Emplacement[]>(`/api/sites/${siteId}/emplacements`);

/**
 * Deplace un rucher : clot l'emplacement courant, en ouvre un nouveau.
 *
 * <p>A ne pas confondre avec `sites.mettreAJour`, qui CORRIGE une position mal
 * saisie sans rien inscrire dans l'historique.
 */
export const demenagerSite = (siteId: number, corps: DemenagementCorps) =>
  requete<Site>(`/api/sites/${siteId}/demenagement`, { method: 'POST', ...corpsJson(corps) });

/** Divisions issues d'une ruche mere (SPRINT-21). */
export const listerDivisions = (rucheMereId: number) =>
  requete<Division[]>(`/api/divisions?rucheMereId=${rucheMereId}`);

/** Filiation d'une ruche dans les deux sens : d'ou elle vient, ce qu'elle a donne. */
export const filiationRuche = (rucheId: number) =>
  requete<Division[]>(`/api/divisions/filiation?rucheId=${rucheId}`);

export const creerDivision = (corps: DivisionCorps) =>
  requete<Division>('/api/divisions', { method: 'POST', ...corpsJson(corps) });

export const supprimerDivision = (id: number) =>
  requete<void>(`/api/divisions/${id}`, { method: 'DELETE' });

/** Captures d'essaim (SPRINT-21). Sans filtre, toutes ; sinon la saison. */
export const listerCaptures = (debut?: string, fin?: string) => {
  const periode = debut && fin ? `?debut=${debut}&fin=${fin}` : '';
  return requete<CaptureEssaim[]>(`/api/captures${periode}`);
};

/** Captures encore en ruchette d'attente : ce qui reste a loger. */
export const listerCapturesEnAttente = () =>
  requete<CaptureEssaim[]>('/api/captures?enAttente=true');

export const creerCapture = (corps: CaptureEssaimCorps) =>
  requete<CaptureEssaim>('/api/captures', { method: 'POST', ...corpsJson(corps) });

/** Loge une capture dans une ruche : l'essaim devient une colonie du parc. */
export const logerCapture = (id: number, rucheId: number) =>
  requete<CaptureEssaim>(`/api/captures/${id}/loger?rucheId=${rucheId}`, { method: 'POST' });

export const supprimerCapture = (id: number) =>
  requete<void>(`/api/captures/${id}`, { method: 'DELETE' });

/** Photos d'un objet quelconque du parc (SPRINT-21). */
export const listerPhotosDe = (cible: CiblePhoto, cibleId: number) =>
  requete<Photo[]>(`/api/photos?cible=${cible}&cibleId=${cibleId}`);

export const attacherPhoto = (corps: PhotoCibleCorps) =>
  requete<Photo>('/api/photos', { method: 'POST', ...corpsJson(corps) });

/**
 * Detache une photo, quelle que soit sa cible.
 *
 * <p>Nom distinct de `supprimerPhoto`, qui reste la suppression d'une photo DE
 * VISITE par la route historique : deux chemins, deux fonctions, aucune ambiguite
 * a l'appel.
 */
export const detacherPhoto = (id: number) =>
  requete<void>(`/api/photos/${id}`, { method: 'DELETE' });

/**
 * Recherche transverse (SPRINT-21).
 *
 * <p>Le serveur refuse en dessous de deux caracteres : l'appelant filtre donc en
 * amont plutot que d'afficher une erreur a chaque frappe.
 */
export const rechercher = (motif: string, limite?: number) => {
  const plafond = limite === undefined ? '' : `&limite=${limite}`;
  return requete<ResultatRecherche[]>(
    `/api/recherche?q=${encodeURIComponent(motif)}${plafond}`,
  );
};

/**
 * Telecharge les visites planifiees au format iCalendar (SPRINT-21).
 *
 * <p>Un telechargement et non une URL d'abonnement : un abonnement supposerait
 * un jeton permanent dans l'URL, recopie dans les reglages de trois appareils et
 * transmis en clair a chaque intermediaire. Voir `AgendaIcsService` cote serveur
 * pour l'arbitrage complet.
 *
 * <p>Meme mecanique que les autres exports : la route exige la session, donc
 * fetch + blob plutot qu'un simple lien.
 */
export const telechargerAgendaIcs = async (debut?: string, fin?: string): Promise<void> => {
  const bornes = debut && fin ? `?debut=${debut}&fin=${fin}` : '';
  const reponse = await fetch(`/api/plannings/agenda.ics${bornes}`, {
    headers: { Accept: 'text/calendar' },
    credentials: 'include',
  });
  if (!reponse.ok) {
    throw new ErreurApi(reponse.status, await detailErreur(reponse));
  }
  const blob = await reponse.blob();
  const url = URL.createObjectURL(blob);
  const lien = document.createElement('a');
  lien.href = url;
  lien.download = 'zumm-visites.ics';
  document.body.appendChild(lien);
  lien.click();
  lien.remove();
  URL.revokeObjectURL(url);
};

/**
 * Plans de transhumance (SPRINT-21). Sans `siteId`, ce qui reste a deplacer
 * dans l'exploitation ; avec, l'historique complet d'un rucher.
 */
export const listerTransports = (siteId?: number) =>
  requete<Transport[]>(`/api/transports${siteId === undefined ? '' : `?siteId=${siteId}`}`);

export const planifierTransport = (corps: TransportCorps) =>
  requete<Transport>('/api/transports', { method: 'POST', ...corpsJson(corps) });

/** Realise le plan : le rucher demenage, et l'historique d'emplacement s'ecrit. */
export const realiserTransport = (id: number) =>
  requete<Transport>(`/api/transports/${id}/realiser`, { method: 'POST' });

export const annulerTransport = (id: number) =>
  requete<Transport>(`/api/transports/${id}/annuler`, { method: 'POST' });

export const supprimerTransport = (id: number) =>
  requete<void>(`/api/transports/${id}`, { method: 'DELETE' });

/** Abonnements iCalendar d'un agent (SPRINT-21). */
export const listerAbonnements = (agentId: number) =>
  requete<Abonnement[]>(`/api/abonnements-calendrier?agentId=${agentId}`);

/**
 * Emet un abonnement. La reponse porte l'URL complete — la seule fois ou elle
 * existe, le serveur ne gardant que l'empreinte du jeton.
 */
export const creerAbonnement = (corps: AbonnementCorps) =>
  requete<Abonnement>('/api/abonnements-calendrier', { method: 'POST', ...corpsJson(corps) });

/** Revoque : la ligne demeure et documente la coupure, l'URL cesse de repondre. */
export const revoquerAbonnement = (id: number) =>
  requete<Abonnement>(`/api/abonnements-calendrier/${id}`, { method: 'DELETE' });

/**
 * Indices de colonie : sante et risque d'essaimage (SPRINT-22).
 *
 * <p>Sans `rucheId`, tout le parc, du plus preoccupant au plus sain.
 */
export const listerIndices = (rucheId?: number) =>
  requete<IndiceColonie[]>(`/api/indices${rucheId === undefined ? '' : `?rucheId=${rucheId}`}`);

/** Correlations meteo / production sur les douze derniers mois (SPRINT-22). */
export const correlationsMeteo = () => requete<CorrelationMeteo[]>('/api/correlations/meteo');

/**
 * Correlation couvert du sol / sante des colonies, par classe (SPRINT-33).
 *
 * <p>Sans millesime, le plus recent verse : comparer la sante d'aujourd'hui a
 * l'occupation du sol de 2019 croiserait deux etats qui n'ont jamais coexiste.
 */
export const correlationsFlore = (millesime?: number) =>
  requete<CorrelationFlore[]>(
    `/api/correlations/flore${millesime === undefined ? '' : `?millesime=${millesime}`}`,
  );

/**
 * Execute le moteur de regles et rend les taches CREEES (SPRINT-22).
 *
 * <p>Idempotent : deux appels dans la journee ne produisent rien la seconde
 * fois. Une reponse vide est donc un resultat normal, pas un echec.
 */
export const executerRegles = () => requete<Tache[]>('/api/regles/executer', { method: 'POST' });

/**
 * Le meme traitement sur plusieurs ruches (SPRINT-23, lot B).
 *
 * <p>Repond 200 et un RAPPORT, jamais 201 : le lot cree plusieurs ressources et
 * peut en refuser une partie. L'appelant doit lire `echecs` — c'est la seule
 * facon de savoir quoi reprendre.
 */
export const traiterEnLot = (corps: { cible: CibleLot; traitement: TraitementCorps }) =>
  requete<RapportLot>('/api/traitements/lot', { method: 'POST', ...corpsJson(corps) });

/** Le meme nourrissement sur plusieurs ruches (SPRINT-23, lot B). */
export const nourrirEnLot = (corps: { cible: CibleLot; nourrissement: NourrissementCorps }) =>
  requete<RapportLot>('/api/nourrissements/lot', { method: 'POST', ...corpsJson(corps) });

/**
 * Recolte tout un rucher en une saisie (SPRINT-23, lot B).
 *
 * <p>`quantiteKgParRuche` vaut pour CHAQUE ruche : le serveur ne repartit jamais
 * un total, il enregistrerait sinon une masse fausse par colonie.
 */
export const recolterEnLot = (corps: RecolteLotCorps) =>
  requete<RapportLot>('/api/recoltes/lot', { method: 'POST', ...corpsJson(corps) });

/** Synthese par rucher (SPRINT-23). Sans `siteId`, tous, du plus preoccupant au plus calme. */
export const syntheseRuchers = (siteId?: number) =>
  requete<SyntheseRucher[]>(
    `/api/ruchers/synthese${siteId === undefined ? '' : `?siteId=${siteId}`}`,
  );

/** Compare des emplacements candidats sur leur potentiel (SPRINT-23). */
export const comparerSites = (ids: number[]) =>
  requete<ComparaisonSite[]>(`/api/sites/comparaison?ids=${ids.join(',')}`);

/** Charge de l'equipe, du plus charge au moins charge (SPRINT-23). */
export const chargeEquipe = () => requete<ChargeAgent[]>('/api/equipe/charge');

// ─── Le terrain sans réseau (SPRINT-24, lot C) ─────────────────────────────

/**
 * Instantané complet d'un rucher, en UN appel (ADR-012).
 *
 * <p>Un seul aller-retour, et c'est le point : sur un réseau qui s'effondre —
 * le contexte même de la fonction — six appels enchaînés donneraient un emport à
 * moitié fait, c'est-à-dire pire qu'aucun, parce qu'il aurait l'air complet.
 */
export const emporterRucher = (siteId: number) =>
  requete<EmportRucher>(`/api/ruchers/${siteId}/emport`);

/**
 * Fiche d'inspection VIERGE d'un rucher, à imprimer avant de partir.
 *
 * <p>Ouverte dans un onglet plutôt que téléchargée : on la relit à l'écran avant
 * de l'imprimer, et forcer un enregistrement ajouterait un geste à chaque fois.
 */
export const ouvrirFicheInspection = async (siteId: number): Promise<void> => {
  const reponse = await fetch(`/api/ruchers/${siteId}/fiche-inspection.pdf`, {
    credentials: 'include',
  });
  if (!reponse.ok) {
    throw new ErreurApi(reponse.status, await detailErreur(reponse));
  }
  const url = URL.createObjectURL(await reponse.blob());
  window.open(url, '_blank', 'noopener');
  // Révocation différée : révoquer tout de suite fermerait l'onglet qu'on vient
  // d'ouvrir sur certains navigateurs.
  setTimeout(() => URL.revokeObjectURL(url), 60_000);
};

/** Dépose ou remplace le brouillon de visite d'un agent sur une ruche. */
export const deposerBrouillon = (corps: BrouillonCorps) =>
  requete<Brouillon>('/api/brouillons', { method: 'PUT', ...corpsJson(corps) });

/** Mes brouillons, du plus récent au plus ancien. */
export const listerBrouillons = (agentId: number) =>
  requete<Brouillon[]>(`/api/brouillons?agentId=${agentId}`);

export const effacerBrouillon = (id: number) =>
  requete<void>(`/api/brouillons/${id}`, { method: 'DELETE' });

// ─── Jeu de démonstration (SPRINT-25, lot J) ───────────────────────────────

/** Un jeu est-il chargé, et la fonction est-elle seulement ouverte ici ? */
export const etatDemonstration = () => requete<EtatDemonstration>('/api/demonstration');

export const chargerDemonstration = () =>
  requete<EtatDemonstration>('/api/demonstration', { method: 'POST' });

/** Retire le jeu — et rien d'autre : la purge suit la trace, jamais les noms. */
export const purgerDemonstration = () =>
  requete<EtatDemonstration>('/api/demonstration', { method: 'DELETE' });

// ─── Capteurs : hausse et partage (SPRINT-26, lot F₁) ──────────────────────

/** Enregistre le poids d'un corps ou d'une hausse. */
export const peserCompartiment = (corps: MesureCompartimentCorps) =>
  requete<PoidsCompartiment>('/api/compartiments/mesures', {
    method: 'POST',
    ...corpsJson(corps),
  });

/**
 * Répartition du poids d'une ruche entre ses compartiments.
 *
 * <p>Distincte de `chargerSerie` : celle-là rend ce qu'une balance pèse sous la
 * ruche entière, celle-ci ce qu'on attribue à chaque étage. Les additionner
 * ferait compter deux fois le même miel.
 */
export const repartitionCompartiments = (rucheId: number) =>
  requete<PoidsCompartiment[]>(`/api/compartiments/repartition?rucheId=${rucheId}`);

/**
 * Pesees successives d'UN compartiment sur une fenetre.
 *
 * <p>Les deux bornes sont exigees par le serveur, et c'est la meme raison qu'aux
 * statistiques du carnet : sans elles, chaque ouverture d'ecran balaierait toute
 * l'histoire de l'exploitation. Elles voyagent en `Instant` ISO — la pesee d'une
 * hausse a une heure, pas seulement un jour.
 */
export const serieCompartiment = (id: number, debut: string, fin: string) =>
  requete<PoidsCompartiment[]>(
    `/api/compartiments/${id}/serie?debut=${encodeURIComponent(debut)}`
      + `&fin=${encodeURIComponent(fin)}`,
  );

/** Ouvre un partage. L'URL n'est rendue qu'ici, et une seule fois. */
export const ouvrirPartage = (corps: PartageCorps) =>
  requete<Partage>('/api/partages', { method: 'POST', ...corpsJson(corps) });

export const listerPartages = (rucheId: number) =>
  requete<Partage[]>(`/api/partages?rucheId=${rucheId}`);

/** Révoque : la ligne demeure, l'URL cesse de répondre. */
export const revoquerPartage = (id: number) =>
  requete<Partage>(`/api/partages/${id}`, { method: 'DELETE' });

// ─── Production, stock, matériel (SPRINT-27, lot E) ────────────────────────

export const materiels = ressource<Materiel, MaterielCorps>('/api/materiels');
export const consommables = ressource<Consommable, ConsommableCorps>('/api/consommables');
export const depenses = ressource<Depense, DepenseCorps>('/api/depenses');

/**
 * Marque l'entretien fait, au jour donné.
 *
 * <p>Un geste dédié plutôt qu'une modification du champ : c'est l'action réelle,
 * et elle repousse l'échéance sans rien d'autre à ressaisir.
 */
export const entretenirMateriel = (id: number, jour?: string) =>
  requete<Materiel>(
    `/api/materiels/${id}/entretien${jour === undefined ? '' : `?jour=${jour}`}`,
    { method: 'POST' },
  );

/**
 * Entrée (positif) ou sortie (négatif) de stock.
 *
 * <p>Un mouvement plutôt qu'un total : deux personnes qui prélèvent du candi le
 * même jour ne s'écrasent pas l'une l'autre.
 */
export const mouvementerStock = (id: number, delta: number) =>
  requete<Consommable>(`/api/consommables/${id}/mouvement?delta=${delta}`, { method: 'POST' });

/** Bilan d'une période. La période est demandée, jamais devinée. */
export const chargerBilan = (debut: string, fin: string) =>
  requete<BilanExploitation>(`/api/depenses/bilan?debut=${debut}&fin=${fin}`);

/** Les saisons enregistrées, de la plus récente à la plus ancienne. */
export const chargerSaisons = () => requete<ComparaisonSaisons[]>('/api/saisons');

/** Les ressources exportables, pour que l'écran n'ait pas à les deviner. */
export const ressourcesExportables = () => requete<string[]>('/api/export/ressources');

/**
 * Télécharge une ressource dans l'un des trois formats.
 *
 * <p>Comme les autres exports : fetch + blob, parce que la session voyage en
 * cookie et qu'un simple lien ne la porterait pas de la même façon.
 */
export const telechargerRessource = async (
  ressource: string,
  format: 'csv' | 'txt' | 'xlsx',
): Promise<void> => {
  const reponse = await fetch(`/api/export/${ressource}?format=${format}`, {
    credentials: 'include',
  });
  if (!reponse.ok) {
    throw new ErreurApi(reponse.status, await detailErreur(reponse));
  }
  const url = URL.createObjectURL(await reponse.blob());
  const lien = document.createElement('a');
  lien.href = url;
  lien.download = `zumm-${ressource}.${format}`;
  document.body.appendChild(lien);
  lien.click();
  lien.remove();
  URL.revokeObjectURL(url);
};

/** Bilan annuel en PDF : un document qu'on archive, pas un tableau de bord. */
export const telechargerBilanAnnuel = async (annee: number): Promise<void> => {
  const reponse = await fetch(`/api/saisons/${annee}/bilan.pdf`, { credentials: 'include' });
  if (!reponse.ok) {
    throw new ErreurApi(reponse.status, await detailErreur(reponse));
  }
  const url = URL.createObjectURL(await reponse.blob());
  const lien = document.createElement('a');
  lien.href = url;
  lien.download = `zumm-bilan-${annee}.pdf`;
  document.body.appendChild(lien);
  lien.click();
  lien.remove();
  URL.revokeObjectURL(url);
};

/** Sucre et eau pour un volume de sirop. Fonction pure, côté serveur. */
export const calculerSirop = (proportion: '1:1' | '2:1', litres: number) =>
  requete<{
    proportion: string;
    litres: number;
    sucreKg: number;
    eauL: number;
    usage: string;
  }>(`/api/calculateurs/sirop?proportion=${encodeURIComponent(proportion)}&litres=${litres}`);

/** Valorisation d'une production. Une valorisation, pas un chiffre d'affaires. */
export const calculerValorisation = (kilos: number, prixKgEur?: number) =>
  requete<{ kilos: number; prixKgEur: number; totalEur: number; pots500g: number }>(
    `/api/calculateurs/valorisation?kilos=${kilos}`
      + (prixKgEur === undefined ? '' : `&prixKgEur=${prixKgEur}`),
  );

/**
 * Convertit une valeur d'une unité vers une autre (US-019).
 *
 * <p>Le calcul est **côté serveur** alors qu'il tiendrait en trois lignes ici, et
 * c'est délibéré : les facteurs sont ceux que le reste de l'application applique
 * déjà à ses mesures. Les recopier dans le navigateur créerait une seconde table
 * de conversion, et le jour où l'une des deux change, rien ne dirait laquelle
 * fait foi.
 *
 * <p>Deux familles seulement — masse et température — et elles ne se croisent
 * pas : convertir des grammes en degrés est refusé en 400, jamais approximé.
 */
export const convertirUnite = (valeur: number, de: string, vers: string) =>
  requete<Conversion>(
    `/api/conversions?valeur=${valeur}&de=${encodeURIComponent(de)}`
      + `&vers=${encodeURIComponent(vers)}`,
  );

// ─── Le carnet paramétrable (SPRINT-28, lot I) ──────────────────────────────

/**
 * Référentiel **fermé** des points d'observation.
 *
 * <p>Il ne change qu'au déploiement d'une migration : le charger une fois par
 * session suffit, et les vues qui l'utilisent le partagent.
 */
export const recupererPoints = () => requete<PointReferentiel[]>('/api/carnet/points');

/** Référentiel indicatif des produits de traitement. La notice fait foi. */
export const recupererProduitsTraitement = () =>
  requete<ProduitReferentiel[]>('/api/carnet/produits');

/**
 * Gabarits d'inspection.
 *
 * <p>Le patron `ressource` s'applique tel quel : le carnet est une ressource
 * ordinaire, seule sa forme est particuliere.
 */
export const gabarits = ressource<Gabarit, GabaritCorps>('/api/carnet/gabarits');

/**
 * Ce que chaque point a donné sur une période.
 *
 * <p>Les bornes sont obligatoires côté serveur : sans elles, la requête
 * balaierait toute l'histoire de l'exploitation à chaque ouverture d'écran.
 */
export const recupererStatistiquesPoints = (depuis: string, jusqu: string) =>
  requete<StatistiquePoint[]>(
    `/api/carnet/statistiques?depuis=${depuis}&jusqu=${jusqu}`,
  );

/**
 * Taux d'eau d'un miel, lu au réfractomètre.
 *
 * <p>Hors de la table publiée, le serveur répond 400 plutôt que d'extrapoler :
 * un chiffre faux sur la mesure qui décide de la conservation serait cru.
 */
export const calculerRefractometre = (indice: number, temperatureC?: number) =>
  requete<Refractometre>(
    `/api/calculateurs/refractometre?indice=${indice}`
      + (temperatureC === undefined ? '' : `&temperatureC=${temperatureC}`),
  );

// ─── Élevage, reines et généalogie (SPRINT-29, lot D) ───────────────────────

/**
 * Les reines comme individus.
 *
 * <p>Distinct de `listerReines`, qui sert le JOURNAL d'une ruche depuis le
 * SPRINT-07. Les deux ressources coexistent parce qu'elles ne parlent pas de la
 * même chose.
 */
export const reinesElevage =
  ressource<ReineElevage, ReineElevageCorps>('/api/elevage/reines');

export const series = ressource<SerieElevage, SerieCorps>('/api/elevage/series');

/** Ascendance et descendance d'une reine. */
export const chargerGenealogie = (id: number) =>
  requete<Genealogie>(`/api/elevage/reines/${id}/genealogie`);

/**
 * Critères observés pendant le règne d'une reine.
 *
 * <p>Aucune note globale n'est rendue, et l'écran ne doit pas en fabriquer une.
 */
export const chargerIndexGenetique = (id: number) =>
  requete<IndexGenetique>(`/api/elevage/reines/${id}/index`);

/** Points de contrôle vérifiables. Ce dossier ne certifie rien. */
export const chargerConformite = (depuis: string, jusqu: string) =>
  requete<DossierConformite>(
    `/api/elevage/conformite?depuis=${depuis}&jusqu=${jusqu}`,
  );

/** Ouvre un document d'élevage dans un onglet : il se relit avant de s'imprimer. */
export const ouvrirDocumentElevage = (
  document: 'registre' | 'conformite',
  depuis: string,
  jusqu: string,
): void => {
  window.open(
    `/api/elevage/${document}.pdf?depuis=${depuis}&jusqu=${jusqu}`,
    '_blank',
    'noopener',
  );
};

/**
 * Ce qui mérite l'attention aujourd'hui (SPRINT-30, lot G).
 *
 * <p>Aucun modèle de langue derrière : quatre registres relus, et chaque ligne
 * cite ce qui la fonde (ADR-013).
 */
export const chargerBriefing = () => requete<Briefing>('/api/briefing');

// ─── Environnement : couvert du sol et floraison (SPRINT-32, lot H) ─────────

/**
 * Ce qu'il y a autour d'un rucher, au millésime demandé.
 *
 * <p>Rien n'est allé chercher la donnée dehors : l'exploitation l'a versée, et
 * tout le calcul s'est fait chez elle (ADR-015). Interroger un service tiers
 * avec les coordonnées d'un rucher lui apprendrait où sont les ruches.
 */
export const chargerCouvert = (siteId: number, millesime?: number) =>
  requete<CouvertRucher>(
    `/api/environnement/sites/${siteId}/couvert`
      + (millesime === undefined ? '' : `?millesime=${millesime}`),
  );

/** Les mêmes surfaces, millésime par millésime : la rotation se LIT. */
export const chargerRotation = (siteId: number) =>
  requete<CouvertRucher[]>(`/api/environnement/sites/${siteId}/rotation`);

export const chargerMillesimes = () =>
  requete<number[]>('/api/environnement/couvert/millesimes');

/**
 * Verse une couche d'occupation du sol.
 *
 * <p>Une `FeatureCollection` GeoJSON dont chaque entité porte une propriété
 * `classe` de la taxonomie fermée. Le versement **remplace** son millésime.
 */
export const verserCouvert = (source: string, millesime: number, collection: unknown) =>
  requete<{ polygones: number; millesime: number; source: string }>(
    `/api/environnement/couvert?source=${encodeURIComponent(source)}&millesime=${millesime}`,
    { method: 'POST', ...corpsJson(collection) },
  );

export const purgerCouvert = (millesime: number) =>
  requete<{ supprimes: number }>(`/api/environnement/couvert?millesime=${millesime}`, {
    method: 'DELETE',
  });

export const chargerFloraisons = (siteId?: number) =>
  requete<FloraisonObservee[]>(
    '/api/environnement/floraisons' + (siteId === undefined ? '' : `?siteId=${siteId}`),
  );

/** Enregistre ou COMPLÈTE l'observation de l'année : une ressource fleurit une fois. */
export const enregistrerFloraison = (corps: FloraisonCorps) =>
  requete<FloraisonObservee>('/api/environnement/floraisons', {
    method: 'POST',
    ...corpsJson(corps),
  });

/**
 * Retire une observation de floraison.
 *
 * <p>Le pendant nécessaire d'un enregistrement qui **complète** : puisque la
 * seconde saisie de l'année enrichit la première au lieu d'en créer une seconde,
 * une date entrée de travers ne se corrige pas en ressaisissant — elle se retire
 * et se refait.
 */
export const supprimerFloraison = (id: number) =>
  requete<void>(`/api/environnement/floraisons/${id}`, { method: 'DELETE' });

// ─── Verification terrain et zones traitees (SPRINT-33, lot K) ──────────────

/**
 * Parcelles de la couche, ou celles d'un rucher.
 *
 * <p>`enAttente` a `true` — le defaut — ne rend que ce que le terrain doit
 * trancher. Sans cette borne, l'ecran chargerait la couche entiere, soit des
 * milliers de polygones dont personne ne doute.
 */
export const chargerParcelles = (siteId?: number, enAttente = true) =>
  requete<ParcelleCouvert[]>(
    `/api/environnement/couvert/parcelles?enAttente=${enAttente}`
      + (siteId === undefined ? '' : `&siteId=${siteId}`),
  );

/**
 * Pose ou leve le doute sur une parcelle.
 *
 * <p>Un geste humain, et il le reste : deduire le doute fabriquerait une tournee
 * de verification que personne n'a demandee.
 */
export const marquerParcelle = (id: number, valeur = true) =>
  requete<void>(
    `/api/environnement/couvert/parcelles/${id}/a-confirmer?valeur=${valeur}`,
    { method: 'POST' },
  );

/**
 * Enregistre ce que le terrain a montre — le *ground truthing*.
 *
 * <p>La classe de la SOURCE n'est jamais ecrasee : le constat s'ecrit a cote, et
 * les surfaces le prennent des qu'il existe.
 */
export const constaterParcelle = (id: number, corps: ConstatCouvertCorps) =>
  requete<void>(`/api/environnement/couvert/parcelles/${id}/constat`, {
    method: 'POST',
    ...corpsJson(corps),
  });

/** Ce que le terrain a appris sur un millesime : trois nombres, aucun taux. */
export const chargerFiabilite = (millesime: number) =>
  requete<FiabiliteCouvert>(
    `/api/environnement/couvert/fiabilite?millesime=${millesime}`,
  );

/** Zones traitees declarees par l'exploitation. */
export const chargerZonesTraitees = () =>
  requete<ZoneTraitee[]>('/api/environnement/zones-traitees');

/** Declare une zone traitee. Aucune identite de tiers n'est demandee. */
export const declarerZoneTraitee = (corps: ZoneTraiteeCorps) =>
  requete<ZoneTraitee>('/api/environnement/zones-traitees', {
    method: 'POST',
    ...corpsJson(corps),
  });

export const supprimerZoneTraitee = (id: number) =>
  requete<void>(`/api/environnement/zones-traitees/${id}`, { method: 'DELETE' });

/**
 * Exposition d'un rucher aux zones DECLAREES.
 *
 * <p>La reponse porte le nombre de declarations et la date de la plus recente :
 * « aucune zone a proximite » se lit « rien ne m'a ete declare », jamais « rien
 * n'a ete epandu ».
 */
export const chargerExposition = (siteId: number) =>
  requete<ExpositionRucher>(`/api/environnement/sites/${siteId}/exposition`);

/**
 * Feuille de chargement de la tournee d'un agent.
 *
 * <p>Ce qu'il faut mettre dans le vehicule avant de partir, dans l'ordre des
 * etapes, plus le total par consommable et ce que le stock n'en couvre pas.
 */
export const chargerFeuilleChargement = (
  agentId: number,
  date: string,
  departSiteId?: number,
) =>
  requete<FeuilleChargement>(
    `/api/plannings/chargement?agentId=${agentId}&date=${date}`
      + (departSiteId === undefined ? '' : `&departSiteId=${departSiteId}`),
  );
