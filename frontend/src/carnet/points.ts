/**
 * Le carnet paramétrable, côté logique (SPRINT-28, lot I).
 *
 * <p>Des fonctions pures, sans React et sans réseau : elles portent la seule
 * règle qui compte ici — **ne pas confondre « pas regardé » avec « non »**. Une
 * case décochée dit qu'on a ouvert la ruche et que ce n'était pas là ; une case
 * jamais touchée ne dit rien du tout. Les fondre ferait descendre tous les taux
 * du parc dans le sens rassurant, qui est le pire.
 */

import type { Gabarit, PointReferentiel, PointReleve } from '../api/types';

/**
 * État d'une case du carnet.
 *
 * <p>Trois états, et non deux, pour la raison ci-dessus. `inconnu` est l'état
 * initial de chaque case : il ne part pas au serveur.
 */
export type EtatCase = 'inconnu' | 'oui' | 'non';

/**
 * Ordre du cycle au clic : inconnu → oui → non → inconnu.
 *
 * <p>« Oui » vient en premier parce que c'est le geste courant : on coche ce
 * qu'on a vu. Noter une absence est plus rare, et mérite le second clic.
 */
export const SUIVANT: Record<EtatCase, EtatCase> = {
  inconnu: 'oui',
  oui: 'non',
  non: 'inconnu',
};

/** Valeur `aria-checked` correspondante — `mixed` est la case indéterminée. */
export const ARIA_ETAT: Record<EtatCase, 'true' | 'false' | 'mixed'> = {
  inconnu: 'mixed',
  oui: 'true',
  non: 'false',
};

/** Saisie en cours : ce que l'écran tient pour les points du gabarit. */
export interface SaisieCarnet {
  /** Points booléens, par code. */
  cases: Record<string, EtatCase>;
  /** Points d'échelle, par code : `''` (non regardé) ou `'0'` à `'3'`. */
  niveaux: Record<string, string>;
}

/** Saisie vierge : aucun point regardé. */
export const SAISIE_VIDE: SaisieCarnet = { cases: {}, niveaux: {} };

/**
 * Points à présenter, dans l'ordre du gabarit.
 *
 * <p>Sans gabarit, aucun point supplémentaire : une exploitation qui n'a jamais
 * ouvert l'écran de configuration garde exactement la grille du SPRINT-20, et
 * ne doit rien y perdre.
 *
 * <p>Un code que le référentiel ne connaît pas est ignoré plutôt que rendu tel
 * quel : c'est le cas d'un front plus ancien que le serveur, et afficher un
 * identifiant technique au rucher ne rend service à personne.
 */
export function pointsDuGabarit(
  gabarit: Gabarit | null,
  referentiel: readonly PointReferentiel[],
): PointReferentiel[] {
  if (gabarit === null) {
    return [];
  }
  const parCode = new Map(referentiel.map((point) => [point.code, point]));
  return gabarit.points
    .map((code) => parCode.get(code))
    .filter((point): point is PointReferentiel => point !== undefined);
}

/**
 * Convertit la saisie en relevés à envoyer.
 *
 * <p>**Seuls les points regardés partent.** Compléter la liste avec des « non »
 * pour les cases non touchées ferait dire à l'inspection ce qu'elle n'a pas dit.
 */
export function versReleves(
  points: readonly PointReferentiel[],
  saisie: SaisieCarnet,
): PointReleve[] {
  const releves: PointReleve[] = [];
  for (const point of points) {
    if (point.typeValeur === 'echelle') {
      const niveau = saisie.niveaux[point.code];
      if (niveau !== undefined && niveau !== '') {
        releves.push({ code: point.code, coche: null, niveau: Number(niveau) });
      }
    } else {
      const etat = saisie.cases[point.code];
      if (etat !== undefined && etat !== 'inconnu') {
        releves.push({ code: point.code, coche: etat === 'oui', niveau: null });
      }
    }
  }
  return releves;
}

/** Reconstruit la saisie à partir des relevés d'une visite existante. */
export function depuisReleves(releves: readonly PointReleve[]): SaisieCarnet {
  const saisie: SaisieCarnet = { cases: {}, niveaux: {} };
  for (const releve of releves) {
    if (releve.niveau !== null) {
      saisie.niveaux[releve.code] = String(releve.niveau);
    } else if (releve.coche !== null) {
      saisie.cases[releve.code] = releve.coche ? 'oui' : 'non';
    }
  }
  return saisie;
}

/**
 * Libellé d'un point dans la langue courante.
 *
 * <p>Le catalogue de traduction est indexé par code. Le libellé français rendu
 * par le serveur ne sert que de **repli** — pour un point ajouté côté serveur
 * que cette version de l'interface ne connaît pas encore. Sans lui, une mise à
 * jour de base afficherait des identifiants au rucher.
 */
export function libellePoint(
  catalogue: Record<string, string>,
  point: { code: string; libelle: string },
): string {
  return catalogue[point.code] ?? point.libelle;
}

/** Répartit des points par famille, en conservant l'ordre du référentiel. */
export function parCategorie<T extends { categorie: string }>(
  points: readonly T[],
): [string, T[]][] {
  const familles = new Map<string, T[]>();
  for (const point of points) {
    const liste = familles.get(point.categorie);
    if (liste === undefined) {
      familles.set(point.categorie, [point]);
    } else {
      liste.push(point);
    }
  }
  return [...familles.entries()];
}

/**
 * Taux de présence d'un point, ou `null` s'il n'a jamais été regardé.
 *
 * <p>Le dénominateur est le nombre de **relevés**, jamais le nombre de visites :
 * rapporter les présences à toutes les visites de la période donnerait un taux
 * systématiquement sous-estimé.
 */
export function tauxPresence(statistique: {
  releves: number;
  presents: number;
}): number | null {
  return statistique.releves === 0
    ? null
    : Math.round((statistique.presents / statistique.releves) * 100);
}
