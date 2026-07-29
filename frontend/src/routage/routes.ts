/**
 * Table des routes de la console (US-051, SPRINT-11).
 *
 * <p>Le projet n'embarque volontairement aucune bibliothèque de routage. Les
 * routes sont plates — un segment, aucun paramètre, aucune imbrication — et un
 * routeur générique n'apporterait ici qu'une dépendance de plus à maintenir. La
 * décision est consignée dans l'ADR du SPRINT-11.
 *
 * <p>La correspondance est directe : l'onglet {@code sites} vit sous
 * {@code /sites}. Ce module reste la seule source de vérité de cette table.
 */

export const ONGLETS = [
  'fermiers',
  'fermes',
  'sites',
  'ruches',
  'plannings',
  'visites',
  'taches',
  'tableaux',
  'capteurs',
  'reines',
  'recoltes',
  'lots',
  'carte',
  'agents',
  'invitations',
  'config',
  'audit',
] as const;

export type Onglet = (typeof ONGLETS)[number];

/**
 * Écran servi à la racine.
 *
 * <p>C'était `fermiers` — la première ligne du tableau, choisie par défaut plutôt
 * que décidée. Ouvrir sur un écran d'administration oblige l'apiculteur à
 * naviguer avant de savoir si son cheptel va bien. Les consoles apicoles du
 * marché ouvrent toutes sur l'état des colonies ; la nôtre ouvre donc sur les
 * tableaux de bord, où la synthèse, les alertes sanitaires et la production se
 * lisent d'un coup d'œil.
 */
export const ONGLET_PAR_DEFAUT: Onglet = 'tableaux';

/**
 * Pictogramme de chaque écran.
 *
 * <p>Un emoji plutôt qu'un jeu d'icônes : seize destinations, aucune dépendance
 * de plus, et un rendu identique en clair comme en sombre. Ils sont
 * <strong>décoratifs</strong> — toujours posés en `aria-hidden`, le libellé
 * traduit porte seul le sens. Un lecteur d'écran ne doit pas annoncer « abeille
 * Ruches ».
 */
export const ICONES: Record<Onglet, string> = {
  fermiers: '🧑‍🌾',
  fermes: '🏡',
  sites: '📍',
  ruches: '🐝',
  plannings: '🗓️',
  visites: '🔎',
  taches: '✅',
  tableaux: '📊',
  capteurs: '📡',
  reines: '👑',
  recoltes: '🍯',
  lots: '🏷️',
  carte: '🗺️',
  agents: '👥',
  invitations: '🎟️',
  config: '⚙️',
  audit: '📜',
};

/** Familles de la navigation. */
export const GROUPES_CLES = [
  'pilotage',
  'cheptel',
  'terrain',
  'production',
  'administration',
] as const;

export type Groupe = (typeof GROUPES_CLES)[number];

/**
 * Répartition des seize écrans en cinq familles.
 *
 * <p><strong>Pourquoi grouper.</strong> Seize onglets alignés dans une barre qui
 * défile horizontalement ne forment pas une navigation : au-delà du septième,
 * l'utilisateur ne balaye plus, il cherche. Les familles suivent le déroulé du
 * métier — on pilote, on gère un cheptel, on va au rucher, on récolte, on
 * administre — et non l'ordre dans lequel les écrans ont été livrés.
 *
 * <p>`pilotage` vient en tête : c'est l'écran d'accueil, et la première position
 * d'une liste est celle dont on se souvient.
 *
 * <p>L'invariant à tenir : chaque onglet apparaît dans <strong>exactement
 * une</strong> famille. `routage.test.ts` le vérifie — un écran ajouté sans
 * famille sortirait sinon de la navigation sans que rien ne le signale.
 */
export const GROUPES: Record<Groupe, readonly Onglet[]> = {
  pilotage: ['tableaux', 'capteurs'],
  cheptel: ['fermiers', 'fermes', 'sites', 'ruches', 'reines'],
  terrain: ['plannings', 'visites', 'taches', 'carte'],
  production: ['recoltes', 'lots'],
  administration: ['agents', 'invitations', 'config', 'audit'],
};

/** Chemin canonique d'un onglet. */
export function cheminDepuisOnglet(onglet: Onglet): string {
  return `/${onglet}`;
}

/**
 * Onglet correspondant à un chemin, ou {@code null} si le chemin est inconnu —
 * auquel cas l'application doit afficher un écran « page introuvable », et non
 * retomber silencieusement sur le premier onglet : une URL fausse dans un lien
 * partagé doit se voir.
 */
export function ongletDepuisChemin(chemin: string): Onglet | null {
  const segment = chemin.replace(/^\/+/, '').replace(/\/+$/, '');
  if (segment === '') {
    return ONGLET_PAR_DEFAUT;
  }
  return (ONGLETS as readonly string[]).includes(segment) ? (segment as Onglet) : null;
}
