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
  'carte',
  'agents',
  'config',
  'audit',
] as const;

export type Onglet = (typeof ONGLETS)[number];

/** Écran servi à la racine. */
export const ONGLET_PAR_DEFAUT: Onglet = 'fermiers';

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
