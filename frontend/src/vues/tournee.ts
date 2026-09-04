import type { EtapeTournee, Tournee } from '../api/types';

/**
 * Simulation locale d'un autre ordre de tournée (SPRINT-22).
 *
 * <p><strong>Ce que ce module fait, et surtout ce qu'il ne fait pas.</strong>
 * `/api/plannings/tournee` rend un ordre <em>proposé</em> : `OptimiseurTournee`
 * applique un plus-proche-voisin puis un 2-opt sur une matrice de distances
 * géodésiques calculées par PostGIS. C'est un bon ordre, pas l'ordre optimal — le
 * service le dit lui-même — et surtout il ignore ce que seul l'apiculteur sait :
 * une route coupée, un rucher qu'il faut voir tôt, un propriétaire à prévenir.
 *
 * <p>Réordonner la proposition est donc une <strong>simulation</strong>, et le reste :
 * aucun ordre n'est persisté, aucune route n'existe côté serveur pour l'enregistrer.
 * L'écran le dit à l'utilisateur, et ce module n'invente pas le contraire.
 *
 * <p><strong>Pourquoi les distances re-simulées sont annoncées comme estimées.</strong>
 * PostGIS mesure sur l'ellipsoïde WGS84 ; la formule ci-dessous mesure sur une
 * sphère. L'écart va jusqu'à ~0,5 % sur ces longueurs. Tant que l'ordre n'a pas
 * bougé, l'écran affiche donc les chiffres du serveur <em>tels quels</em> ; ce
 * n'est qu'une fois l'ordre modifié que l'estimation prend le relais, et elle est
 * étiquetée comme telle. Deux sources de vérité pour une même distance seraient
 * une dérive ; une source de vérité et une estimation nommée, non.
 */

/** Rayon moyen de la Terre (IUGG), en mètres. */
const RAYON_TERRE_M = 6371008.8;

const enRadians = (degres: number): number => (degres * Math.PI) / 180;

/** Distance à vol d'oiseau entre deux points, en mètres (haversine, sphérique). */
export function distanceApproximative(
  a: { latitude: number; longitude: number },
  b: { latitude: number; longitude: number },
): number {
  const dLat = enRadians(b.latitude - a.latitude);
  const dLon = enRadians(b.longitude - a.longitude);
  const lat1 = enRadians(a.latitude);
  const lat2 = enRadians(b.latitude);
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) ** 2;
  return 2 * RAYON_TERRE_M * Math.asin(Math.min(1, Math.sqrt(h)));
}

/** Ce que l'écran affiche : des étapes, un total, et l'aveu de sa provenance. */
export interface TourneeAffichee {
  etapes: EtapeTournee[];
  distanceTotaleMetres: number;
  /** `true` si les distances viennent d'ici et non de PostGIS. */
  estimee: boolean;
}

/**
 * Compose ce qu'il faut afficher.
 *
 * @param tournee la proposition du serveur, source de vérité.
 * @param ordre l'ordre choisi par l'utilisateur, ou `null` tant qu'il n'a rien
 *        touché — auquel cas les chiffres du serveur passent sans être recalculés.
 */
export function afficher(tournee: Tournee, ordre: EtapeTournee[] | null): TourneeAffichee {
  if (ordre === null) {
    return {
      etapes: tournee.etapes,
      distanceTotaleMetres: tournee.distanceTotaleMetres,
      estimee: false,
    };
  }

  // Le premier tronçon n'existe pas : la tournée est un chemin ouvert, sans dépôt
  // de départ — c'est aussi ce que fait `OptimiseurTournee.longueur` côté serveur.
  const etapes = ordre.map((etape, rang) => ({
    ...etape,
    ordre: rang + 1,
    distanceDepuisPrecedenteMetres: rang === 0 ? 0 : distanceApproximative(ordre[rang - 1], etape),
  }));

  return {
    etapes,
    distanceTotaleMetres: etapes.reduce((somme, e) => somme + e.distanceDepuisPrecedenteMetres, 0),
    estimee: true,
  };
}
