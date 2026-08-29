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
  'sanitaire',
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
  'permissions',
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
 * <p>Un emoji plutôt qu'un jeu d'icônes : dix-neuf destinations, aucune dépendance
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
  sanitaire: '💊',
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
  permissions: '🔐',
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
 * Répartition des dix-neuf écrans en cinq familles.
 *
 * <p><strong>Pourquoi grouper.</strong> Dix-neuf onglets alignés dans une barre qui
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
  cheptel: ['fermiers', 'fermes', 'sites', 'ruches', 'reines', 'sanitaire'],
  terrain: ['plannings', 'visites', 'taches', 'carte'],
  production: ['recoltes', 'lots'],
  administration: ['agents', 'invitations', 'config', 'permissions', 'audit'],
};

/**
 * Écrans dont la LECTURE est réservée à certains rôles (SPRINT-19).
 *
 * <p>Cette table n'invente rien : elle recopie ce que
 * `SecurityConfig.matriceRbac` refuse déjà côté serveur. Deux écrans seulement
 * y figurent, et c'est volontaire :
 *
 * <ul>
 *   <li>`audit` — `GET /api/audit` est réservé à `responsable` et `admin` ;
 *   <li>`invitations` — `/api/invitations` l'est en entier, lecture comprise :
 *       la liste des codes en cours est une liste de clefs valides.
 * </ul>
 *
 * <p><strong>Ce qui n'y est PAS, et pourquoi.</strong> Le référentiel
 * (`fermiers`, `fermes`, `sites`, `agents`, `ruches`) n'a que ses ÉCRITURES
 * restreintes : un apiculteur a le droit de lire la liste des agents. Masquer
 * ces écrans lui retirerait une consultation légitime ; c'est le bouton
 * « Nouveau » qui doit disparaître, pas l'onglet. Ce gardiennage-là se joue au
 * niveau de l'action : il est porté par {@link ROLES_ECRITURE}.
 *
 * <p><strong>Ce filtrage est un CONFORT, jamais une protection.</strong>
 * L'autorisation est posée par le serveur ; ce que le navigateur cache, il
 * pourrait le montrer. On masque pour ne pas proposer une porte fermée, pas
 * pour fermer la porte.
 */
export const ROLES_ONGLET: Partial<Record<Onglet, readonly string[]>> = {
  audit: ['responsable', 'admin'],
  invitations: ['responsable', 'admin'],
  // La matrice des permissions n'expose aucune donnée métier, mais elle décrit
  // qui peut quoi : c'est une carte des serrures. Elle suit donc le même
  // réglage que les deux écrans qu'elle documente.
  permissions: ['responsable', 'admin'],
};

/** L'écran est-il atteignable avec ces rôles ? */
export function ongletAutorise(onglet: Onglet, roles: readonly string[]): boolean {
  const requis = ROLES_ONGLET[onglet];
  return requis === undefined || requis.some((role) => roles.includes(role));
}

/**
 * Écrans dont l'ÉCRITURE est réservée à certains rôles.
 *
 * <p>Pendant de {@link ROLES_ONGLET}, pour le cas que celle-ci laissait ouvert.
 * Le référentiel se lit avec n'importe quel rôle métier et ne s'écrit qu'avec
 * `responsable` ou `admin` : `SecurityConfig.matriceRbac` refuse déjà les
 * `POST`, `PUT` et `DELETE` sur `/api/{fermiers,fermes,sites,agents,ruches}/**`.
 * Retirer l'onglet à un apiculteur lui ôterait une consultation légitime ; lui
 * laisser le bouton « Nouveau » lui promet un formulaire qui finira en 403 à
 * l'enregistrement, après la saisie. Ni l'un ni l'autre : l'écran reste, les
 * commandes d'écriture s'effacent.
 *
 * <p><strong>Même réserve que pour la navigation :</strong> c'est un confort, pas
 * une protection. Le refus est prononcé par le serveur, sur chacune des trois
 * méthodes — ce que le navigateur cache, il pourrait le montrer.
 *
 * <p>Un onglet absent de cette table s'écrit avec tout rôle métier : c'est le cas
 * des visites, mesures, tâches, récoltes et lots, qui sont le travail quotidien
 * de l'apiculteur.
 */
export const ROLES_ECRITURE: Partial<Record<Onglet, readonly string[]>> = {
  fermiers: ['responsable', 'admin'],
  fermes: ['responsable', 'admin'],
  sites: ['responsable', 'admin'],
  ruches: ['responsable', 'admin'],
  agents: ['responsable', 'admin'],
};

/** L'écran est-il modifiable avec ces rôles ? */
export function peutEcrire(onglet: Onglet, roles: readonly string[]): boolean {
  const requis = ROLES_ECRITURE[onglet];
  return requis === undefined || requis.some((role) => roles.includes(role));
}

/**
 * Écrans d'une famille visibles avec ces rôles, dans l'ordre de la famille.
 *
 * <p>Une famille peut se retrouver vide — `administration` pour un apiculteur
 * n'a plus que `agents` et `config`. Le rail doit alors ne rien peindre du tout
 * plutôt qu'un intertitre sans écrans dessous.
 */
export function ongletsVisibles(
  groupe: Groupe,
  roles: readonly string[],
): readonly Onglet[] {
  return GROUPES[groupe].filter((onglet) => ongletAutorise(onglet, roles));
}

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

/**
 * Routes servies <strong>hors console</strong>, et accessibles sans session
 * (SPRINT-19).
 *
 * <p>Jusqu'ici l'application n'avait qu'une porte : sans session, l'écran de
 * connexion occupait tout l'espace, quelle que soit l'URL. Un visiteur ne
 * pouvait donc rien apprendre du produit avant d'avoir un compte — ce qui
 * suppose déjà d'avoir un code d'exploitation. La page d'accueil comble ce
 * trou : elle présente les fonctionnalités à <strong>tout le monde</strong>,
 * visiteur compris, et reste consultable une fois connecté.
 *
 * <p>Ces chemins ne sont volontairement PAS des onglets : ils n'apparaissent ni
 * dans le rail, ni dans la palette, et ne vivent pas dans la coquille de la
 * console.
 */
export const ROUTES_PUBLIQUES = {
  accueil: '/accueil',
  connexion: '/connexion',
  apropos: '/a-propos',
  fonctionnalites: '/fonctionnalites',
  ressources: '/ressources',
  editions: '/editions',
  contact: '/contact',
  recuperation: '/recuperation',
  cgu: '/cgu',
  confidentialite: '/confidentialite',
} as const;

export type RoutePublique = keyof typeof ROUTES_PUBLIQUES;

const CHEMINS_PUBLICS = Object.entries(ROUTES_PUBLIQUES) as [RoutePublique, string][];

/** Route publique correspondant à un chemin, ou {@code null} si ce n'en est pas une. */
export function routePubliqueDepuisChemin(chemin: string): RoutePublique | null {
  const segment = `/${chemin.replace(/^\/+/, '').replace(/\/+$/, '')}`;
  return CHEMINS_PUBLICS.find(([, valeur]) => valeur === segment)?.[0] ?? null;
}

/**
 * Routes qui exigent une session sans appartenir à la console (SPRINT-19).
 *
 * <p>« Mon compte » est personnel, pas métier : il n'a rien à faire dans un rail
 * qui range des ressources d'exploitation, et il se cherche là où le web l'a mis
 * depuis toujours — sous son propre nom, dans le menu de profil. Il garde
 * pourtant la coquille de la console : on y va depuis un écran de travail, et on
 * y revient.
 *
 * <p>Cette table est le pendant de {@link ROUTES_PUBLIQUES}. Les trois
 * ensembles — onglets, routes publiques, routes de session — sont disjoints ;
 * `routage.test.ts` le vérifie, faute de quoi un chemin serait servi par deux
 * branches et la seconde ne s'exécuterait jamais.
 */
export const ROUTES_SESSION = {
  compte: '/compte',
} as const;

export type RouteSession = keyof typeof ROUTES_SESSION;

const CHEMINS_SESSION = Object.entries(ROUTES_SESSION) as [RouteSession, string][];

/** Route de session correspondant à un chemin, ou {@code null}. */
export function routeSessionDepuisChemin(chemin: string): RouteSession | null {
  const segment = `/${chemin.replace(/^\/+/, '').replace(/\/+$/, '')}`;
  return CHEMINS_SESSION.find(([, valeur]) => valeur === segment)?.[0] ?? null;
}
