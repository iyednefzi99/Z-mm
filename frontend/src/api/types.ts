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

/** Ressource du referentiel de sources de nectar (SPRINT-21). */
export type RessourceFloraleType =
  | 'colza'
  | 'tournesol'
  | 'acacia'
  | 'chataignier'
  | 'tilleul'
  | 'lavande'
  | 'bruyere'
  | 'luzerne'
  | 'sarrasin'
  | 'verger'
  | 'agrumes'
  | 'eucalyptus'
  | 'thym'
  | 'romarin'
  | 'jujubier'
  | 'palmier_dattier'
  | 'prairie'
  | 'foret'
  | 'garrigue'
  | 'autre';

export type TypeSite =
  | 'sedentaire'
  | 'transhumance'
  | 'fecondation'
  | 'elevage'
  | 'conservatoire'
  | 'autre';

export type Exposition =
  | 'nord'
  | 'nord_est'
  | 'est'
  | 'sud_est'
  | 'sud'
  | 'sud_ouest'
  | 'ouest'
  | 'nord_ouest';

export interface RessourceFlorale {
  id: number;
  ressource: RessourceFloraleType;
  distanceM: number | null;
  /**
   * Fenêtre de floraison en MOIS (1-12) — la « miellée » du §1.
   *
   * <p>Elle peut enjamber l'année : `moisFin` inférieur à `moisDebut` est
   * valide et se lit modulo douze (eucalyptus, novembre → février).
   */
  moisDebut: number | null;
  moisFin: number | null;
  note: string | null;
}

export interface RessourceFloraleCorps {
  ressource: RessourceFloraleType;
  distanceM: number | null;
  moisDebut: number | null;
  moisFin: number | null;
  note: string | null;
}

export interface Site {
  id: number;
  nom: string;
  fermeId: number;
  fermeNom: string;
  latitude: number;
  longitude: number;
  altitude: number | null;
  /**
   * Rayon de butinage de CE rucher, en kilomètres (SPRINT-32).
   *
   * <p>`null` = le défaut de `ConfigZumm.ini`. Le rayon réel dépend du terrain :
   * trois kilomètres en plaine, davantage en montagne, moins en ville. C'est un
   * RÉGLAGE et non un lieu — il traverse le masquage des positions.
   */
  rayonButinageKm: number | null;
  dateMiseEnOeuvre: string;
  dateDemenagement: string | null;
  dateCloture: string | null;
  /**
   * Adresse postale (SPRINT-21). NULLE pour les profils non proprietaires : le
   * serveur la retire avec l'altitude, parce qu'une rue situe un rucher au
   * portail la ou deux decimales le situent au kilometre. La commune et le pays,
   * eux, restent.
   */
  adresseRue: string | null;
  codePostal: string | null;
  ville: string | null;
  pays: string | null;
  typeSite: TypeSite | null;
  exposition: Exposition | null;
  /**
   * basse | normale | haute (SPRINT-23). Jamais nulle : un défaut nul
   * obligerait chaque lecture à traiter l'absence comme un cas particulier.
   */
  priorite: PrioriteTerrain;
  /**
   * Couverture mobile constatée sur place (SPRINT-24). NULLE = inconnue,
   * ce qui est la réponse honnête tant que personne n'y est allé avec un
   * téléphone. Ce n'est pas une position : elle n'est donc pas masquée.
   */
  couvertureReseau: CouvertureReseau | null;
  ressources: RessourceFlorale[];
  creeLe: string;
  majLe: string;
}

export interface SiteCorps {
  nom: string;
  fermeId: number;
  latitude: number;
  longitude: number;
  altitude: number | null;
  /**
   * Rayon de butinage de CE rucher, en kilomètres (SPRINT-32).
   *
   * <p>`null` = le défaut de `ConfigZumm.ini`. Deux ruchers d'une même
   * exploitation n'ont pas le même terrain, et c'est ce que le lot mesure.
   */
  rayonButinageKm: number | null;
  dateMiseEnOeuvre: string;
  dateDemenagement: string | null;
  dateCloture: string | null;
  adresseRue: string | null;
  codePostal: string | null;
  ville: string | null;
  pays: string | null;
  typeSite: TypeSite | null;
  exposition: Exposition | null;
  /**
   * basse | normale | haute (SPRINT-23). Jamais nulle : un défaut nul
   * obligerait chaque lecture à traiter l'absence comme un cas particulier.
   */
  priorite: PrioriteTerrain;
  /**
   * Couverture mobile constatée sur place (SPRINT-24). NULLE = inconnue,
   * ce qui est la réponse honnête tant que personne n'y est allé avec un
   * téléphone. Ce n'est pas une position : elle n'est donc pas masquée.
   */
  couvertureReseau: CouvertureReseau | null;
  ressources: RessourceFloraleCorps[];
}

/** Motif d'un changement d'emplacement (SPRINT-21). */
export type MotifEmplacement =
  | 'installation'
  | 'transhumance'
  | 'miellee'
  | 'securite'
  | 'reglementaire'
  | 'autre';

/**
 * Un emplacement occupe par un rucher (SPRINT-21).
 *
 * <p>`courant` distingue la ligne en cours : c'est la seule dont `dateFin` est
 * nulle, et l'index unique partiel de la base garantit qu'il n'y en a qu'une.
 */
export interface Emplacement {
  id: number;
  siteId: number;
  latitude: number;
  longitude: number;
  altitude: number | null;
  dateDebut: string;
  dateFin: string | null;
  motif: MotifEmplacement | null;
  note: string | null;
  courant: boolean;
}

export interface DemenagementCorps {
  latitude: number;
  longitude: number;
  altitude: number | null;
  dateDebut: string;
  motif: MotifEmplacement | null;
  note: string | null;
}

export interface Agent {
  id: number;
  nom: string;
  role: RoleAgent;
  fermeId: number | null;
  fermeNom: string | null;
  email: string | null;
  /**
   * Cet agent accepte-t-il les courriels d'alerte (SPRINT-25) ?
   *
   * <p>S'AJOUTE au réglage global du serveur, il ne le remplace pas :
   * les deux doivent être vrais pour qu'un message parte.
   */
  notificationsEmail: boolean;
  creeLe: string;
  majLe: string;
}

export interface AgentCorps {
  nom: string;
  role: RoleAgent;
  fermeId: number | null;
  email?: string | null;
  /** NUL = on ne touche pas au réglage existant (SPRINT-25). */
  notificationsEmail?: boolean | null;
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

/** Priorité d'une ruche ou d'un rucher (SPRINT-23). Trois niveaux, pas quatre. */
export type PrioriteTerrain = 'basse' | 'normale' | 'haute';

export const PRIORITES_TERRAIN: readonly PrioriteTerrain[] = ['basse', 'normale', 'haute'];

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
  /**
   * basse | normale | haute (SPRINT-23). Jamais nulle : un défaut nul
   * obligerait chaque lecture à traiter l'absence comme un cas particulier.
   */
  priorite: PrioriteTerrain;
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
  /**
   * basse | normale | haute (SPRINT-23). Jamais nulle : un défaut nul
   * obligerait chaque lecture à traiter l'absence comme un cas particulier.
   */
  priorite: PrioriteTerrain;
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

/**
 * Objet auquel une photo est attachee (SPRINT-21, sixieme cible au SPRINT-28).
 *
 * <p>`TRAITEMENT` porte le scan de l'ordonnance veterinaire : un registre
 * d'elevage devient verifiable quand la piece y est attachee, et non seulement
 * sa reference recopiee a la main.
 */
export type CiblePhoto =
  | 'VISITE'
  | 'RUCHE'
  | 'SITE'
  | 'REINE'
  | 'RECOLTE'
  | 'TRAITEMENT';

export interface Photo {
  id: number;
  cible: CiblePhoto;
  cibleId: number;
  url: string;
  legende: string | null;
  creeLe: string;
}

export interface PhotoCorps {
  url: string;
  legende: string | null;
}

/** Corps de la route generique `/api/photos`, ou la cible est dans le corps. */
export interface PhotoCibleCorps {
  cible: CiblePhoto;
  cibleId: number;
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
  /** Points du carnet paramétrable RELEVÉS (SPRINT-28) — pas tous les points. */
  points: PointReleve[];
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
  /**
   * Points du carnet relevés pendant cette visite (SPRINT-28).
   *
   * <p>N'envoyer que ce qui a été REGARDÉ. Omettre un point n'est pas l'envoyer
   * à « non » : l'absence dit qu'on n'a pas regardé, `coche: false` dit qu'on a
   * regardé et que ce n'était pas là. Les statistiques reposent sur cette
   * différence.
   */
  points: PointReleve[];
}

// ─── Le carnet paramétrable (SPRINT-28, lot I) ──────────────────────────────

/** Ce qu'un point du référentiel attend comme valeur. */
export type TypePointObservation = 'booleen' | 'echelle';

/** Familles du référentiel, dans l'ordre où l'inspection les rencontre. */
export type CategoriePoint =
  | 'population'
  | 'reine'
  | 'reserves'
  | 'batisse'
  | 'sanitaire'
  | 'materiel'
  | 'environnement'
  | 'geste';

/**
 * Un point du référentiel **fermé** d'observation.
 *
 * <p>Le référentiel ne s'écrit que par migration : un gabarit y choisit un
 * sous-ensemble, il n'y ajoute jamais rien. C'est ce qui garde les observations
 * comparables d'une exploitation à l'autre.
 *
 * <p>`libelle` est le libellé FRANÇAIS du serveur. L'interface traduit par
 * `code` et ne s'en sert qu'en repli — pour un point ajouté côté serveur que
 * cette version du front ne connaît pas encore.
 */
export interface PointReferentiel {
  code: string;
  categorie: CategoriePoint;
  typeValeur: TypePointObservation;
  libelle: string;
  ordre: number;
}

/**
 * Valeur relevée pour un point, sur une visite.
 *
 * <p>Exactement un des deux champs est renseigné, selon le type du point. Le
 * serveur refuse l'autre plutôt que de l'ignorer.
 */
export interface PointReleve {
  code: string;
  coche: boolean | null;
  niveau: number | null;
}

/** Gabarit d'inspection : le carnet tel que l'exploitation le veut. */
export interface Gabarit {
  id: number;
  nom: string;
  description: string | null;
  /** Sections du noyau (SPRINT-20) allumées. Éteintes, elles sont masquées — pas effacées. */
  noyauCouvain: boolean;
  noyauReine: boolean;
  noyauCadres: boolean;
  noyauTemperament: boolean;
  parDefaut: boolean;
  actif: boolean;
  /** Codes du référentiel, dans l'ordre d'affichage. */
  points: string[];
  creeLe: string;
  majLe: string;
}

export interface GabaritCorps {
  nom: string;
  description: string | null;
  noyauCouvain: boolean;
  noyauReine: boolean;
  noyauCadres: boolean;
  noyauTemperament: boolean;
  parDefaut: boolean;
  actif: boolean;
  points: string[];
}

/**
 * Produit du référentiel de traitement, **indicatif**.
 *
 * <p>Il pré-remplit la saisie ; la notice fait foi, et `mention` doit rester
 * affichée. `haussesRetirees` porte la contrainte réelle de la plupart des
 * varroacides : un délai de carence de zéro jour, lu seul, se comprend comme
 * « on peut récolter ».
 */
export interface ProduitReferentiel {
  code: string;
  nom: string;
  substanceActive: string;
  cible: CibleTraitement;
  forme: string;
  delaiCarenceJours: number;
  haussesRetirees: boolean;
  ordonnanceRequise: boolean;
  mention: string | null;
}

/**
 * Ce qu'un point a donné sur une période.
 *
 * <p>`releves` compte les fois où le point a été REGARDÉ, jamais les visites de
 * la période : rapporter les présences à toutes les visites donnerait un taux
 * systématiquement sous-estimé, et rassurant à tort.
 */
export interface StatistiquePoint {
  code: string;
  libelle: string;
  categorie: CategoriePoint;
  typeValeur: TypePointObservation;
  releves: number;
  presents: number;
  moyenneEchelle: number | null;
}

/**
 * Taux d'eau d'un miel, lu au réfractomètre (SPRINT-28).
 *
 * <p>Au-delà de 18 % le miel fermente en pot ; au-delà de 20 % il sort de la
 * norme de commercialisation. D'où un verdict, et pas seulement un nombre.
 */
export interface Refractometre {
  indice: number;
  temperatureC: number;
  /** Indice ramené à 20 °C — rendu pour que le calcul soit vérifiable. */
  indiceCorrige: number;
  humiditePct: number;
  verdict: 'stable' | 'risque_fermentation' | 'hors_norme';
  conformeNorme: boolean;
}

/** Tâche ou rappel de l'apiculteur (US-031). */
/** Priorite d'une tache (SPRINT-22). Absente a la saisie, elle vaut `normale`. */
export type PrioriteTache = 'basse' | 'normale' | 'haute' | 'critique';

export const PRIORITES_TACHE: readonly PrioriteTache[] = [
  'basse',
  'normale',
  'haute',
  'critique',
];

export type CategorieTache =
  | 'controle'
  | 'traitement'
  | 'nourrissement'
  | 'recolte'
  | 'materiel'
  | 'elevage'
  | 'administratif'
  | 'autre';

export const CATEGORIES_TACHE: readonly CategorieTache[] = [
  'controle',
  'traitement',
  'nourrissement',
  'recolte',
  'materiel',
  'elevage',
  'administratif',
  'autre',
];

export interface Tache {
  id: number;
  libelle: string;
  rucheId: number | null;
  rucheModele: string | null;
  agentId: number | null;
  agentNom: string | null;
  echeance: string | null;
  faite: boolean;
  priorite: PrioriteTache;
  categorie: CategorieTache | null;
  /**
   * `manuelle` ou `regle`.
   *
   * <p>Une tâche engendrée doit pouvoir se justifier à l'écran — « proposée par
   * la règle des délais de carence ». Une liste où les deux se confondent finit
   * par ne plus être lue.
   */
  origine: 'manuelle' | 'regle';
  regleCode: string | null;
  creeLe: string;
  majLe: string;
}

export interface TacheCorps {
  libelle: string;
  rucheId: number | null;
  agentId: number | null;
  echeance: string | null;
  faite: boolean;
  priorite: PrioriteTache | null;
  categorie: CategorieTache | null;
}

/**
 * Indices calculés d'une colonie (SPRINT-22).
 *
 * <p>`composantes` à zéro signifie que **rien n'a pu être évalué** : `sante` et
 * `risqueEssaimage` valent alors zéro et ne veulent rien dire. L'écran doit
 * afficher « non évalué », jamais une jauge — une jauge sur du vide fait passer
 * l'ignorance pour un diagnostic.
 */
export interface IndiceColonie {
  rucheId: number;
  rucheModele: string;
  sante: number;
  risqueEssaimage: number;
  composantes: number;
  derniereVisite: string | null;
  motifs: string[];
}

/** Corrélation entre un indicateur météo figé et la production (SPRINT-22). */
export interface CorrelationMeteo {
  indicateur: 'temperature' | 'humidite' | 'vent';
  /** `null` quand le coefficient n'existe pas : série constante ou une seule paire. */
  coefficient: number | null;
  echantillon: number;
  interpretation: string;
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

export type TypeIndicateur =
  | 'poids'
  | 'temperature'
  | 'humidite'
  | 'activite'
  /**
   * Niveau de batterie du capteur, en pourcent (SPRINT-26).
   *
   * <p>Ne dit rien de la colonie : c'est l'état du MATÉRIEL qui l'observe. Le
   * reproche fait à BeeLog et Onibi n'est pas l'absence de mesure, c'est la
   * panne silencieuse — d'où une valeur de plus, et le même mécanisme d'alerte.
   */
  | 'alimentation'
  /**
   * Inclinaison de la ruche, en degrés (SPRINT-31).
   *
   * <p>Ne dit rien de la colonie non plus : c'est la POSITION de la caisse. Une
   * ruche renversée par le vent, un sanglier ou un voleur sort de la verticale.
   */
  | 'inclinaison';
export const TYPES_INDICATEUR: readonly TypeIndicateur[] = [
  'poids',
  'temperature',
  'humidite',
  'activite',
  'alimentation',
  'inclinaison',
];

/** Alerte de seuil déclenchée par une mesure (US-018). */
/**
 * Famille d'une alerte (SPRINT-31).
 *
 * <p>Un dépassement de seuil se surveille ; une chute brutale sans récolte fait
 * prendre la voiture. L'écran doit pouvoir les distinguer sans lire le message.
 */
export type CategorieAlerte = 'seuil' | 'antivol';

export interface AlerteMesure {
  id: number;
  rucheId: number;
  rucheModele: string;
  typeIndicateur: TypeIndicateur;
  categorie: CategorieAlerte;
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
  typeProduit: TypeProduit;
  unite: UniteProduit;
  /** Taux d'eau du miel, au réfractomètre (SPRINT-28). Réservé au miel. */
  humiditePct: number | null;
  lot: string;
  note: string | null;
  /** Récolte enregistrée malgré une carence, avec son motif (SPRINT-22). */
  carenceForcee: boolean;
  motifForcage: string | null;
  qrPayload: string;
  creeLe: string;
  majLe: string;
}

export interface RecolteCorps {
  rucheId: number;
  dateRecolte: string;
  quantiteKg: number;
  typeMiel: string | null;
  /** Absents, c'est du MIEL en kilogrammes : le défaut d'avant le SPRINT-27. */
  typeProduit?: TypeProduit | null;
  unite?: UniteProduit | null;
  /** Taux d'eau, entre 10 et 30 %. Réservé au miel : la base refuse le reste. */
  humiditePct?: number | null;
  note: string | null;
  /**
   * Enregistrer malgré une carence en cours (SPRINT-22).
   *
   * <p>Le serveur répond **409** tant que ce drapeau est faux : la requête est
   * valide, c'est l'état de la ruche qui s'y oppose. Forcer exige un motif, et
   * la décision est consignée au journal d'audit.
   */
  forcerCarence: boolean;
  motifForcage: string | null;
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
  /** Vétérinaire signataire et date (SPRINT-28) : sans eux, rien n'est vérifiable. */
  ordonnanceVeterinaire: string | null;
  ordonnanceDate: string | null;
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
  ordonnanceVeterinaire: string | null;
  ordonnanceDate: string | null;
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

/** Methode employee pour diviser une colonie (SPRINT-21). */
export type MethodeDivision =
  | 'essaim_artificiel'
  | 'nucleus'
  | 'partage_egal'
  | 'prelevement_cadres'
  | 'autre';

export type OrigineReineDivision =
  | 'cellule_royale'
  | 'reine_introduite'
  | 'orpheline'
  | 'reine_mere'
  | 'autre';

/**
 * Division d'une colonie (SPRINT-21).
 *
 * <p>`rucheFilleId` peut manquer : on divise souvent vers un nucleus qui ne sera
 * enregistre comme ruche que s'il prend.
 */
export interface Division {
  id: number;
  rucheMereId: number;
  rucheMereModele: string;
  rucheFilleId: number | null;
  rucheFilleModele: string | null;
  agentId: number;
  agentNom: string;
  visiteId: number | null;
  dateDivision: string;
  methode: MethodeDivision | null;
  cadresCouvain: number | null;
  cadresProvisions: number | null;
  origineReine: OrigineReineDivision | null;
  note: string | null;
  creeLe: string;
}

export interface DivisionCorps {
  rucheMereId: number;
  rucheFilleId: number | null;
  agentId: number;
  visiteId: number | null;
  dateDivision: string;
  methode: MethodeDivision | null;
  cadresCouvain: number | null;
  cadresProvisions: number | null;
  origineReine: OrigineReineDivision | null;
  note: string | null;
}

export type OrigineCapture =
  | 'essaim_naturel'
  | 'piege'
  | 'recuperation'
  | 'signalement'
  | 'autre';

/**
 * Capture d'essaim (SPRINT-21).
 *
 * <p>Aucune coordonnee, deliberement : `lieu` est un repere humain, pas un point
 * sur une carte. `logee` dit si l'essaim a rejoint une ruche du parc.
 */
export interface CaptureEssaim {
  id: number;
  agentId: number;
  agentNom: string;
  rucheId: number | null;
  siteId: number | null;
  siteNom: string | null;
  dateCapture: string;
  origine: OrigineCapture;
  lieu: string | null;
  poidsKg: number | null;
  hauteurM: number | null;
  note: string | null;
  logee: boolean;
  creeLe: string;
}

export interface CaptureEssaimCorps {
  agentId: number;
  rucheId: number | null;
  siteId: number | null;
  dateCapture: string;
  origine: OrigineCapture;
  lieu: string | null;
  poidsKg: number | null;
  hauteurM: number | null;
  note: string | null;
}

/** Famille d'objet rendue par la recherche globale (SPRINT-21). */
export type TypeResultat =
  | 'ruche'
  | 'site'
  | 'ferme'
  | 'fermier'
  | 'agent'
  | 'recolte'
  | 'lot';

/**
 * Une reponse de la recherche globale (SPRINT-21).
 *
 * <p>Volontairement pauvre — ni position, ni adresse, ni courriel : c'est le seul
 * endroit du produit ou un appel rend d'un coup un echantillon de toutes les
 * tables.
 */
export interface ResultatRecherche {
  type: TypeResultat;
  id: number;
  libelle: string;
  precision: string | null;
  route: string;
}

/** Referentiel des methodes de division (SPRINT-21). */
export const METHODES_DIVISION: readonly MethodeDivision[] = [
  'essaim_artificiel',
  'nucleus',
  'partage_egal',
  'prelevement_cadres',
  'autre',
];

export const ORIGINES_REINE: readonly OrigineReineDivision[] = [
  'cellule_royale',
  'reine_introduite',
  'orpheline',
  'reine_mere',
  'autre',
];

export const ORIGINES_CAPTURE: readonly OrigineCapture[] = [
  'essaim_naturel',
  'piege',
  'recuperation',
  'signalement',
  'autre',
];

/** Referentiel des sources de nectar declarables sur un rucher (SPRINT-21). */
export const RESSOURCES_FLORALES: readonly RessourceFloraleType[] = [
  'colza',
  'tournesol',
  'acacia',
  'chataignier',
  'tilleul',
  'lavande',
  'bruyere',
  'luzerne',
  'sarrasin',
  'verger',
  'agrumes',
  'eucalyptus',
  'thym',
  'romarin',
  'jujubier',
  'palmier_dattier',
  'prairie',
  'foret',
  'garrigue',
  'autre',
];

export const TYPES_SITE: readonly TypeSite[] = [
  'sedentaire',
  'transhumance',
  'fecondation',
  'elevage',
  'conservatoire',
  'autre',
];

export const EXPOSITIONS: readonly Exposition[] = [
  'nord',
  'nord_est',
  'est',
  'sud_est',
  'sud',
  'sud_ouest',
  'ouest',
  'nord_ouest',
];

export const MOTIFS_EMPLACEMENT: readonly MotifEmplacement[] = [
  'installation',
  'transhumance',
  'miellee',
  'securite',
  'reglementaire',
  'autre',
];

/** Statut d'un plan de transhumance (SPRINT-21). */
export type StatutTransport = 'prevu' | 'realise' | 'annule';

export const STATUTS_TRANSPORT: readonly StatutTransport[] = ['prevu', 'realise', 'annule'];

/**
 * Plan de deplacement d'un rucher (SPRINT-21).
 *
 * <p>`voyages` est calcule par le serveur a partir de la capacite et du nombre
 * de ruches : il n'est stocke nulle part, pour que les trois valeurs ne puissent
 * pas diverger.
 *
 * <p>La destination arrive MASQUEE pour les profils non proprietaires, comme
 * toute position : un plan de transport est une carte des ruchers a venir.
 */
export interface Transport {
  id: number;
  siteId: number;
  siteNom: string;
  agentId: number;
  agentNom: string;
  datePrevue: string;
  heurePrevue: string | null;
  vehicule: string | null;
  capaciteRuches: number | null;
  nbRuches: number | null;
  voyages: number | null;
  destinationLibelle: string;
  destinationLatitude: number | null;
  destinationLongitude: number | null;
  statut: StatutTransport;
  note: string | null;
  creeLe: string;
}

export interface TransportCorps {
  siteId: number;
  agentId: number;
  datePrevue: string;
  heurePrevue: string | null;
  vehicule: string | null;
  capaciteRuches: number | null;
  nbRuches: number | null;
  destinationLibelle: string;
  destinationLatitude: number | null;
  destinationLongitude: number | null;
  note: string | null;
}

/**
 * Abonnement iCalendar (SPRINT-21).
 *
 * <p>`url` n'est renseignee qu'a la CREATION : elle porte le jeton en clair, qui
 * n'existe nulle part ailleurs — la base n'en garde que l'empreinte. Perdue,
 * elle se remplace, elle ne se retrouve pas.
 */
export interface Abonnement {
  id: number;
  agentId: number;
  libelle: string;
  creeLe: string;
  expireLe: string;
  revoqueLe: string | null;
  derniereUtilisation: string | null;
  actif: boolean;
  url: string | null;
}

export interface AbonnementCorps {
  agentId: number;
  libelle: string;
  dureeJours: number;
}

/**
 * Sur quelles ruches porte une opération de lot (SPRINT-23).
 *
 * <p>Les deux champs sont cumulables et le serveur déduplique : scanner trente
 * ruches d'un rucher qui en compte quarante, puis viser le rucher entier, donne
 * quarante lignes — jamais soixante-dix.
 */
export interface CibleLot {
  rucheIds: number[] | null;
  siteId: number | null;
}

/**
 * Ce qu'une opération de lot a réellement fait (SPRINT-23).
 *
 * <p><strong>Un lot réussit rarement en entier, et ce n'est pas une anomalie.</strong>
 * Sur quarante ruches, deux clôturées et une sous carence donnent trente-sept
 * succès et trois refus : c'est le résultat normal. L'écran doit donc afficher
 * les deux, et nommer les refus — c'est ce qui permet de reprendre trois ruches
 * au lieu de quarante.
 */
export interface RapportLot {
  demandees: number;
  reussites: number[];
  echecs: { rucheId: number; motif: string }[];
}

/** Le rucher vu d'un coup : le niveau auquel on travaille (SPRINT-23). */
export interface SyntheseRucher {
  siteId: number;
  siteNom: string;
  ville: string | null;
  priorite: PrioriteTerrain;
  nbRuches: number;
  nbActives: number;
  /** `null` si aucune colonie n'a pu être évaluée — jamais 0, qui serait un jugement. */
  santeMoyenne: number | null;
  coloniesEvaluees: number;
  risqueEssaimageMax: number | null;
  ruchesSousCarence: number;
  alertesOuvertes: number;
  tachesOuvertes: number;
  productionKg: number;
}

/** Un emplacement aligné pour la comparaison (SPRINT-23). Aucune note globale. */
export interface ComparaisonSite {
  siteId: number;
  siteNom: string;
  ville: string | null;
  typeSite: TypeSite | null;
  exposition: Exposition | null;
  altitude: number | null;
  nbRuches: number;
  rendementKgParRuche: number | null;
  ressourcesDeclarees: number;
  ressourcesEnFleur: number;
  ruchersA3km: number;
}

/**
 * Charge d'un agent (SPRINT-23).
 *
 * <p>Ces chiffres servent à répartir, jamais à comparer des personnes : dix
 * tâches en retard, c'est le plus souvent trois jours de pluie.
 */
export interface ChargeAgent {
  agentId: number;
  agentNom: string;
  role: string | null;
  ruchesResponsable: number;
  ruchersConcernes: number;
  tachesOuvertes: number;
  tachesEnRetard: number;
  tachesCritiques: number;
  visites7Jours: number;
}

/** Corps d'une récolte de rucher entier (SPRINT-23). */
export interface RecolteLotCorps {
  cible: CibleLot;
  dateRecolte: string;
  /** Masse récoltée sur CHAQUE ruche visée, jamais un total à répartir. */
  quantiteKgParRuche: number;
  typeMiel: string | null;
  note: string | null;
  forcerCarence: boolean;
  motifForcage: string | null;
}


// ─── Le terrain sans réseau (SPRINT-24, lot C) ─────────────────────────────

export type CouvertureReseau = 'aucune' | 'faible' | 'correcte' | 'bonne';
export const COUVERTURES_RESEAU: readonly CouvertureReseau[] = [
  'aucune',
  'faible',
  'correcte',
  'bonne',
];

/**
 * Instantané d'un rucher, à emporter hors ligne (ADR-012).
 *
 * <p>`preleveLe` est la raison d'être de ce type : une donnée servie depuis le
 * disque ne doit jamais passer pour fraîche. Il s'affiche partout où
 * l'instantané sert, pas seulement là où on l'a déclenché.
 *
 * <p>Ni mesures de capteurs, ni météo, ni position exacte : voir `EmportRucher`
 * côté serveur, qui dit pourquoi chacune est dehors.
 */
export interface EmportRucher {
  preleveLe: string;
  site: Site;
  ruches: Ruche[];
  dernieresVisites: Visite[];
  tachesOuvertes: Tache[];
  sousCarence: Traitement[];
}

/**
 * Saisie de visite en cours, reprenable sur un autre appareil (SPRINT-24).
 *
 * <p>`contenu` est du JSON opaque au serveur — le formulaire tel que le front
 * l'a laissé. `appareil` et `majLe` sont ce qui rend la reprise décidable :
 * « commencé il y a deux heures sur le téléphone » se reprend, « commencé il y a
 * trois semaines » s'efface.
 */
export interface Brouillon {
  id: number;
  agentId: number;
  agentNom: string;
  rucheId: number;
  rucheModele: string;
  siteNom: string | null;
  contenu: string;
  appareil: string | null;
  creeLe: string;
  majLe: string;
}

export interface BrouillonCorps {
  agentId: number;
  rucheId: number;
  contenu: string;
  appareil: string | null;
}

// ─── Identification et confort (SPRINT-25, lot J) ──────────────────────────

/**
 * État du jeu de démonstration.
 *
 * <p>`objets` est ce qui rend le bouton « Retirer » décidable plutôt
 * qu'inquiétant : on sait combien de lignes disparaîtront.
 */
export interface EtatDemonstration {
  disponible: boolean;
  charge: boolean;
  objets: number;
}

// ─── Capteurs : hausse et partage (SPRINT-26, lot F₁) ──────────────────────

/**
 * Poids attribué à un compartiment.
 *
 * <p>`valeur` et `instant` sont NULS tant qu'aucune pesée n'a eu lieu, et le
 * restent plutôt que de valoir zéro : une hausse jamais pesée n'est pas une
 * hausse vide, c'est une hausse inconnue. Afficher 0 kg ferait croire à une
 * colonie qui a perdu ses réserves.
 */
export interface PoidsCompartiment {
  compartimentId: number;
  type: 'corps' | 'hausse';
  nbCadres: number;
  valeur: number | null;
  instant: string | null;
}

export interface MesureCompartimentCorps {
  compartimentId: number;
  valeur: number;
  instant?: string | null;
}

/**
 * Partage d'un flux de télémétrie hors de l'exploitation.
 *
 * <p>`url` n'est renseignée QU'À LA CRÉATION : le jeton n'existe en clair
 * qu'une fois, la base n'en gardant que l'empreinte. La relire plus tard est
 * impossible — c'est le prix, assumé, de ne rien stocker de réutilisable.
 */
export interface Partage {
  id: number;
  rucheId: number;
  libelle: string;
  creeLe: string;
  expireLe: string;
  revoqueLe: string | null;
  derniereUtilisation: string | null;
  actif: boolean;
  url: string | null;
}

export interface PartageCorps {
  rucheId: number;
  libelle: string;
  dureeJours: number;
}

// ─── Production, stock, matériel (SPRINT-27, lot E) ────────────────────────

export type TypeProduit =
  | 'miel'
  | 'cire'
  | 'pollen'
  | 'propolis'
  | 'gelee_royale'
  | 'essaim'
  | 'reine';
export const TYPES_PRODUIT: readonly TypeProduit[] = [
  'miel',
  'cire',
  'pollen',
  'propolis',
  'gelee_royale',
  'essaim',
  'reine',
];

/** kg pour ce qui se pèse, unite pour ce qui se compte. */
export type UniteProduit = 'kg' | 'unite';

export type CategorieMateriel =
  | 'ruche'
  | 'hausse'
  | 'cadre'
  | 'extracteur'
  | 'maturateur'
  | 'enfumoir'
  | 'protection'
  | 'vehicule'
  | 'balance'
  | 'autre';
export const CATEGORIES_MATERIEL: readonly CategorieMateriel[] = [
  'ruche',
  'hausse',
  'cadre',
  'extracteur',
  'maturateur',
  'enfumoir',
  'protection',
  'vehicule',
  'balance',
  'autre',
];

export type EtatMateriel = 'neuf' | 'bon' | 'a_reviser' | 'hors_service';
export const ETATS_MATERIEL: readonly EtatMateriel[] = [
  'neuf',
  'bon',
  'a_reviser',
  'hors_service',
];

/**
 * Un équipement, avec son échéance d'entretien CALCULÉE.
 *
 * <p>`prochaineMaintenance` et `enRetard` ne sont pas stockés : les ranger en
 * base créerait une valeur à maintenir en cohérence avec la dernière
 * maintenance.
 */
export interface Materiel {
  id: number;
  libelle: string;
  categorie: CategorieMateriel;
  quantite: number;
  siteId: number | null;
  siteNom: string | null;
  etat: EtatMateriel;
  periodiciteJours: number | null;
  derniereMaintenance: string | null;
  prochaineMaintenance: string | null;
  enRetard: boolean;
  note: string | null;
  creeLe: string;
  majLe: string;
}

export interface MaterielCorps {
  libelle: string;
  categorie: CategorieMateriel;
  quantite: number;
  siteId: number | null;
  etat: EtatMateriel;
  periodiciteJours: number | null;
  derniereMaintenance: string | null;
  note: string | null;
}

export type CategorieConsommable =
  | 'sirop'
  | 'candi'
  | 'traitement'
  | 'cire_gaufree'
  | 'pot'
  | 'etiquette'
  | 'cadre'
  | 'protection'
  | 'autre';
export const CATEGORIES_CONSOMMABLE: readonly CategorieConsommable[] = [
  'sirop',
  'candi',
  'traitement',
  'cire_gaufree',
  'pot',
  'etiquette',
  'cadre',
  'protection',
  'autre',
];

/** `sousSeuil` est calculé, et la comparaison est INCLUSIVE. */
export interface Consommable {
  id: number;
  libelle: string;
  categorie: CategorieConsommable;
  quantite: number;
  unite: 'kg' | 'l' | 'unite';
  seuilAlerte: number;
  sousSeuil: boolean;
  note: string | null;
  creeLe: string;
  majLe: string;
}

export interface ConsommableCorps {
  libelle: string;
  categorie: CategorieConsommable;
  quantite: number;
  unite: 'kg' | 'l' | 'unite';
  seuilAlerte: number;
  note: string | null;
}

export type CategorieDepense =
  | 'materiel'
  | 'consommable'
  | 'traitement'
  | 'nourrissement'
  | 'cheptel'
  | 'transport'
  | 'analyse'
  | 'assurance'
  | 'formation'
  | 'autre';
export const CATEGORIES_DEPENSE: readonly CategorieDepense[] = [
  'materiel',
  'consommable',
  'traitement',
  'nourrissement',
  'cheptel',
  'transport',
  'analyse',
  'assurance',
  'formation',
  'autre',
];

export interface Depense {
  id: number;
  libelle: string;
  categorie: CategorieDepense;
  montantEur: number;
  dateDepense: string;
  rucheId: number | null;
  rucheModele: string | null;
  siteId: number | null;
  siteNom: string | null;
  note: string | null;
  creeLe: string;
  majLe: string;
}

export interface DepenseCorps {
  libelle: string;
  categorie: CategorieDepense;
  montantEur: number;
  dateDepense: string;
  rucheId: number | null;
  siteId: number | null;
  note: string | null;
}

/**
 * Rentabilité d'une ruche.
 *
 * <p>`recettesEur` est une VALORISATION au prix du kilo paramétré, pas un
 * chiffre d'affaires : Zümm ne connaît pas les prix de vente.
 */
export interface RentabiliteRuche {
  rucheId: number;
  rucheModele: string;
  siteNom: string | null;
  productionKg: number;
  recettesEur: number;
  depensesEur: number;
  resultatEur: number;
}

/**
 * Bilan d'une période.
 *
 * <p>`depensesNonAffectees` figure À PART et n'est pas répartie : une assurance
 * ne se divise pas par le nombre de ruches.
 */
export interface BilanExploitation {
  debut: string;
  fin: string;
  productionMielKg: number;
  recettesEur: number;
  depensesEur: number;
  resultatEur: number;
  depensesNonAffectees: number;
  parCategorie: Record<string, number>;
  parRuche: RentabiliteRuche[];
}

/** Une saison, en année civile. `rendementKg` est NUL si rien n'a produit. */
export interface ComparaisonSaisons {
  annee: number;
  productionMielKg: number;
  ruchesProductives: number;
  rendementKg: number | null;
  nombreRecoltes: number;
  parProduit: { typeProduit: string; unite: string; quantite: number }[];
}

// ─── Élevage, reines et généalogie (SPRINT-29, lot D) ───────────────────────

/** D'où vient une reine. `inconnue` est le défaut : c'est le cas le plus fréquent. */
export type OrigineReine = 'elevage' | 'achat' | 'essaimage' | 'supersedure' | 'inconnue';

/**
 * Nommee `ORIGINES_ELEVAGE` et non `ORIGINES_REINE` : ce nom-la est pris depuis
 * le SPRINT-21 par l'origine de la reine d'une DIVISION, qui est une autre
 * question — d'ou vient la reine de la ruche fille, pas d'ou vient la reine.
 */
export const ORIGINES_ELEVAGE: readonly OrigineReine[] = [
  'elevage',
  'achat',
  'essaimage',
  'supersedure',
  'inconnue',
];

/** Où en est une reine. À ne pas confondre avec `StatutReine`, qui est un ÉVÉNEMENT. */
export type EtatReine =
  | 'en_service'
  | 'reserve'
  | 'remplacee'
  | 'disparue'
  | 'morte'
  | 'vendue';

export const ETATS_REINE: readonly EtatReine[] = [
  'en_service',
  'reserve',
  'remplacee',
  'disparue',
  'morte',
  'vendue',
];

export type MethodeElevage =
  | 'greffage'
  | 'picking'
  | 'cupularve'
  | 'essaim_artificiel'
  | 'autre';

export const METHODES_ELEVAGE: readonly MethodeElevage[] = [
  'greffage',
  'picking',
  'cupularve',
  'essaim_artificiel',
  'autre',
];

/**
 * Une reine, avec sa filiation.
 *
 * <p>**À ne pas confondre avec `Reine`**, qui est un ÉVÉNEMENT du journal d'une
 * ruche depuis le SPRINT-07. Les deux coexistent : l'une porte les individus,
 * l'autre ce qui leur arrive.
 */
export interface ReineElevage {
  id: number;
  code: string | null;
  mereId: number | null;
  mereCode: string | null;
  rucheMereId: number | null;
  serieId: number | null;
  serieNom: string | null;
  rucheId: number | null;
  rucheModele: string | null;
  origine: OrigineReine;
  fournisseur: string | null;
  race: string | null;
  anneeNaissance: number | null;
  couleurMarquage: CouleurReine | null;
  /** `null` = on ne sait pas, ce qui n'est pas « non clippée ». */
  ailesClippees: boolean | null;
  dateGreffage: string | null;
  dateNaissance: string | null;
  dateFecondation: string | null;
  dateIntroduction: string | null;
  /** Fin de règne : elle borne l'index génétique. */
  dateFin: string | null;
  statut: EtatReine;
  note: string | null;
  creeLe: string;
  majLe: string;
}

export interface ReineElevageCorps {
  code: string | null;
  mereId: number | null;
  rucheMereId: number | null;
  serieId: number | null;
  rucheId: number | null;
  origine: OrigineReine;
  fournisseur: string | null;
  race: string | null;
  anneeNaissance: number | null;
  couleurMarquage: CouleurReine | null;
  ailesClippees: boolean | null;
  dateGreffage: string | null;
  dateNaissance: string | null;
  dateFecondation: string | null;
  dateIntroduction: string | null;
  dateFin: string | null;
  statut: EtatReine;
  note: string | null;
}

/** Un maillon de la lignée. `profondeur` : 1 pour une mère ou une fille directe. */
export interface NoeudLignee {
  id: number;
  code: string;
  profondeur: number;
  parentId: number | null;
  race: string | null;
  anneeNaissance: number | null;
  statut: EtatReine;
}

/**
 * Arbre de lignée.
 *
 * <p>Deux listes plutôt qu'un arbre unique : les mères forment une chaîne, les
 * filles un arbre. Les fondre aurait forcé l'interface à deviner de quel côté
 * elle se trouve.
 */
export interface Genealogie {
  reine: ReineElevage;
  ascendants: NoeudLignee[];
  descendants: NoeudLignee[];
}

/**
 * Un critère observé pendant le règne d'une reine.
 *
 * <p>`valeur` est dans son unité propre, jamais ramenée à une échelle commune :
 * les critères ne se comparent pas entre eux, et une échelle unique le ferait
 * croire.
 */
export interface CritereGenetique {
  code: string;
  valeur: number | null;
  unite: string | null;
  observations: number;
  /** Faux quand les observations manquent — la valeur est alors `null`. */
  suffisant: boolean;
}

/**
 * Index génétique multicritère.
 *
 * <p>**Il n'y a aucune note globale, et il ne doit jamais y en avoir.** La
 * douceur, un taux d'infestation et des kilogrammes ne s'additionnent pas.
 */
export interface IndexGenetique {
  reineId: number;
  code: string;
  rucheId: number | null;
  debut: string;
  fin: string;
  criteres: CritereGenetique[];
}

export interface SerieElevage {
  id: number;
  nom: string;
  dateGreffage: string;
  soucheId: number | null;
  soucheCode: string | null;
  rucheEleveuseId: number | null;
  methode: MethodeElevage | null;
  nbGreffees: number;
  nbAcceptees: number | null;
  nbNees: number | null;
  nbFecondees: number | null;
  /** Calculés côté serveur, jamais stockés. */
  tauxAcceptation: number | null;
  tauxReussite: number | null;
  note: string | null;
  creeLe: string;
  majLe: string;
}

export interface SerieCorps {
  nom: string;
  dateGreffage: string;
  soucheId: number | null;
  rucheEleveuseId: number | null;
  methode: MethodeElevage | null;
  nbGreffees: number;
  nbAcceptees: number | null;
  nbNees: number | null;
  nbFecondees: number | null;
  note: string | null;
}

/** Ce que le système peut vérifier d'un dossier de contrôle — et pas au-delà. */
export type StatutControle = 'verifie' | 'signale' | 'a_justifier';

export interface PointControle {
  code: string;
  statut: StatutControle;
  detail: string;
  nombre: number;
}

/**
 * Dossier de contrôle.
 *
 * <p>Zümm ne certifie rien : `avertissement` doit être affiché ET imprimé, sans
 * exception. « À justifier » ne veut pas dire « non conforme » : cela veut dire
 * que la pièce se trouve ailleurs que dans ce logiciel.
 */
export interface DossierConformite {
  debut: string;
  fin: string;
  avertissement: string;
  points: PointControle[];
}

// ─── Briefing du jour (SPRINT-30, lot G) ────────────────────────────────────

/**
 * Une ligne du briefing.
 *
 * <p>`urgence` : 1 à faire aujourd'hui, 3 à savoir. `detail` porte ce qui fonde
 * la ligne — un compte, une date, un nom de ruche : c'est ce qui rend le
 * briefing vérifiable, et c'est pourquoi il n'y a pas de modèle de langue
 * derrière (voir ADR-013).
 */
export interface LigneBriefing {
  categorie: 'alerte' | 'tache' | 'carence' | 'visite' | 'meteo';
  urgence: number;
  titre: string;
  detail: string;
  rucheId: number | null;
}

/** Ce qui mérite l'attention aujourd'hui. Recalculé, jamais stocké. */
export interface Briefing {
  genereLe: string;
  lignes: LigneBriefing[];
}

// ─── Environnement : couvert du sol et floraison (SPRINT-32, lot H) ─────────

/**
 * Taxonomie **fermée** du couvert du sol.
 *
 * <p>Chaque source nomme ses classes autrement — « prairie permanente »,
 * *grassland*, `landuse=meadow`. Les laisser entrer telles quelles rendrait deux
 * exploitations incomparables : l'ingesteur traduit vers ces dix classes, et
 * refuse ce qu'il ne sait pas traduire.
 */
export type ClasseCouvert =
  | 'culture'
  | 'prairie'
  | 'foret'
  | 'lande'
  | 'verger'
  | 'vigne'
  | 'eau'
  | 'urbain'
  | 'sol_nu'
  | 'autre';

export interface SurfaceCouvert {
  classe: ClasseCouvert;
  surfaceHa: number;
  /** Part du cercle de butinage, en pourcent. */
  part: number | null;
}

/**
 * Ce qu'il y a autour d'un rucher.
 *
 * <p>`source` et `millesime` accompagnent les surfaces **sans exception** :
 * « 42 % de cultures » n'engage personne tant qu'on ne sait pas de quelle année
 * et de quel jeu de données cela vient (§13). `millesime` à `null` signifie
 * qu'aucune couche n'a été versée — ce n'est pas un environnement vide.
 */
export interface CouvertRucher {
  siteId: number;
  siteNom: string;
  rayonKm: number;
  millesime: number | null;
  source: string | null;
  surfaceCercleHa: number;
  /**
   * Part du cercle effectivement DÉCRITE par la couche.
   *
   * <p>Trente pour cent de couverture et soixante-dix pour cent de silence ne
   * disent pas « 70 % de sol nu ».
   */
  couverte: number | null;
  /**
   * Distance à la parcelle cultivée la plus proche, en mètres.
   *
   * <p>Ce n'est **pas** une distance à une zone traitée : aucune couche ouverte
   * ne dit ce qui a été épandu ni quand.
   */
  distanceCultureM: number | null;
  surfaces: SurfaceCouvert[];
}

/**
 * Floraison OBSERVÉE, à ne pas confondre avec la floraison déclarée de la `V21`.
 *
 * <p>Le déclaratif prévoit — « le colza fleurit en avril » —, l'observé
 * constate. `ecartJours` est ce que la confrontation des deux apprend : positif,
 * la floraison est en retard sur la prévision.
 */
export interface FloraisonObservee {
  id: number;
  ressourceId: number;
  ressource: string;
  siteId: number | null;
  annee: number;
  dateDebut: string;
  datePic: string | null;
  dateFin: string | null;
  /** 0 (nulle) à 3 (exceptionnelle). Une échelle perçue, jamais une mesure. */
  abondance: number | null;
  moisDeclare: number | null;
  ecartJours: number | null;
  note: string | null;
}

export interface FloraisonCorps {
  ressourceId: number;
  annee: number;
  dateDebut: string;
  datePic: string | null;
  dateFin: string | null;
  abondance: number | null;
  note: string | null;
}
