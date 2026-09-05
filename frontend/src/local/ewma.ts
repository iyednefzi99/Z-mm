/**
 * Détection d'anomalie EWMA, sur l'appareil (SPRINT-30, lot G).
 *
 * <p>Ferme la ligne « IA embarquée sur l'appareil » du §8 de
 * `docs/ECART-CONCURRENTS.md`, dont le constat était exact : « l'inférence est
 * serveur (`ia-service`) — c'est un choix d'architecture, pas un oubli, mais il
 * coûte le mode 100 % local ».
 *
 * <p><strong>Ce n'est pas un modèle, et il ne faut pas l'appeler ainsi.</strong>
 * C'est une moyenne mobile exponentielle et son écart-type : une trentaine de
 * lignes d'arithmétique, dont le mérite est justement de tenir dans un
 * navigateur sans rien télécharger. Le microservice `ia-service` fait exactement
 * le même calcul — voir `AnomalieService`, qui l'exécute déjà côté serveur
 * lorsque le microservice n'est pas joignable.
 *
 * <p><strong>Deux implémentations, un seul résultat attendu.</strong> C'est le
 * risque réel de ce fichier : deux langages qui divergent en silence. Il est
 * tenu par `ewma.test.ts`, qui rejoue une série de référence et fixe les
 * nombres. Toute modification ici doit être portée dans `AnomalieService`, et
 * réciproquement — l'écart se verrait sur un écran, jamais dans un journal.
 */

/** Poids du dernier point. Doit rester égal à `AnomalieService.ALPHA`. */
export const ALPHA = 0.3;

/** Au-delà de trois écarts-types, le point est signalé. Idem côté serveur. */
export const SEUIL_Z = 3.0;

/** Un point de la série : ce que porte une mesure de capteur. */
export interface PointSerie {
  instant: string;
  valeur: number;
}

/** Un point signalé, avec son écart à la ligne de base connue. */
export interface PointAnomalie {
  instant: string;
  valeur: number;
  z: number;
}

/** Résultat du balayage, dans la forme que l'API rend déjà. */
export interface ResultatEwma {
  alpha: number;
  seuilZ: number;
  moyenne: number | null;
  ecartType: number | null;
  points: number;
  anomalies: PointAnomalie[];
}

/** Arrondi au millième, comme le serveur : sans lui, les deux tests divergent. */
const arrondi = (valeur: number): number => Math.round(valeur * 1000) / 1000;

/**
 * Balaye une série et signale ce qui s'écarte de la ligne de base.
 *
 * <p>Le z-score se calcule **avant** la mise à jour, comme côté serveur : un
 * point aberrant qui aurait déjà déplacé la moyenne se comparerait à lui-même,
 * et ne se signalerait jamais.
 */
export function detecter(serie: readonly PointSerie[]): ResultatEwma {
  if (serie.length === 0) {
    return { alpha: ALPHA, seuilZ: SEUIL_Z, moyenne: null, ecartType: null, points: 0, anomalies: [] };
  }

  let moyenne = serie[0].valeur;
  let variance = 0;
  const anomalies: PointAnomalie[] = [];

  for (let i = 1; i < serie.length; i += 1) {
    const x = serie[i].valeur;
    const ecart = x - moyenne;
    const increment = ALPHA * ecart;
    const ecartType = Math.sqrt(variance);
    if (ecartType > 0) {
      const z = ecart / ecartType;
      if (Math.abs(z) > SEUIL_Z) {
        anomalies.push({ instant: serie[i].instant, valeur: x, z: arrondi(z) });
      }
    }
    moyenne += increment;
    variance = (1 - ALPHA) * (variance + ecart * increment);
  }

  return {
    alpha: ALPHA,
    seuilZ: SEUIL_Z,
    moyenne: arrondi(moyenne),
    ecartType: arrondi(Math.sqrt(variance)),
    points: serie.length,
    anomalies,
  };
}
