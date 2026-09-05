import { describe, expect, it } from 'vitest';
import type { Gabarit, PointReferentiel, PointReleve } from '../api/types';
import {
  SUIVANT,
  depuisReleves,
  libellePoint,
  parCategorie,
  pointsDuGabarit,
  tauxPresence,
  versReleves,
} from './points';

/**
 * Le carnet paramétrable, côté logique (SPRINT-28, lot I).
 *
 * <p>Tout ce fichier tourne autour d'une seule distinction : **« pas regardé »
 * n'est pas « non »**. C'est elle qui décide si un taux de parc veut dire
 * quelque chose, et c'est elle qu'une case à cocher ordinaire perd.
 */

const point = (
  code: string,
  typeValeur: 'booleen' | 'echelle',
  categorie = 'population',
): PointReferentiel => ({
  code,
  categorie: categorie as PointReferentiel['categorie'],
  typeValeur,
  libelle: 'Libellé serveur de ' + code,
  ordre: 10,
});

const REFERENTIEL = [
  point('pop_forte', 'booleen'),
  point('pop_faible', 'booleen'),
  point('propolisation', 'echelle', 'batisse'),
];

const gabaritDe = (points: string[]): Gabarit => ({
  id: 1,
  nom: 'Printemps',
  description: null,
  noyauCouvain: true,
  noyauReine: true,
  noyauCadres: true,
  noyauTemperament: true,
  parDefaut: true,
  actif: true,
  points,
  creeLe: '2026-09-04T08:00:00Z',
  majLe: '2026-09-04T08:00:00Z',
});

describe('relevés du carnet', () => {
  it('n’envoie que les points regardés', () => {
    const releves = versReleves(REFERENTIEL, {
      cases: { pop_forte: 'oui' },
      niveaux: {},
    });

    // `pop_faible` et `propolisation` figuraient au gabarit et n'ont pas été
    // touchés : les envoyer à « non » ferait dire à l'inspection ce qu'elle n'a
    // pas dit.
    expect(releves).toEqual([{ code: 'pop_forte', coche: true, niveau: null }]);
  });

  it('distingue « regardé, absent » de « pas regardé »', () => {
    const releves = versReleves(REFERENTIEL, {
      cases: { pop_forte: 'non', pop_faible: 'inconnu' },
      niveaux: {},
    });

    // Un seul relevé, et il vaut `false` : c'est un CONSTAT négatif, qui doit
    // compter dans le dénominateur d'un taux. L'autre n'existe pas.
    expect(releves).toEqual([{ code: 'pop_forte', coche: false, niveau: null }]);
  });

  it('envoie une intensité pour un point d’échelle, jamais une case', () => {
    const releves = versReleves(REFERENTIEL, {
      cases: {},
      niveaux: { propolisation: '2' },
    });

    expect(releves).toEqual([{ code: 'propolisation', coche: null, niveau: 2 }]);
  });

  it('reconstruit la saisie d’une visite déjà enregistrée', () => {
    const existants: PointReleve[] = [
      { code: 'pop_forte', coche: true, niveau: null },
      { code: 'pop_faible', coche: false, niveau: null },
      { code: 'propolisation', coche: null, niveau: 3 },
    ];

    const saisie = depuisReleves(existants);

    expect(saisie.cases).toEqual({ pop_forte: 'oui', pop_faible: 'non' });
    expect(saisie.niveaux).toEqual({ propolisation: '3' });
    // Aller-retour sans perte : rouvrir une visite pour corriger une ligne ne
    // doit pas effacer les autres.
    expect(versReleves(REFERENTIEL, saisie)).toEqual(existants);
  });

  it('fait tourner la case sur trois états, et revient à son point de départ', () => {
    expect(SUIVANT.inconnu).toBe('oui');
    expect(SUIVANT[SUIVANT[SUIVANT.inconnu]]).toBe('inconnu');
  });
});

describe('points d’un gabarit', () => {
  it('suit l’ordre du gabarit, pas celui du référentiel', () => {
    const points = pointsDuGabarit(gabaritDe(['propolisation', 'pop_forte']), REFERENTIEL);

    expect(points.map((p) => p.code)).toEqual(['propolisation', 'pop_forte']);
  });

  it('ignore un code que le référentiel ne connaît pas', () => {
    // Cas d'un front plus ancien que le serveur : afficher un identifiant
    // technique au rucher ne rend service à personne.
    const points = pointsDuGabarit(gabaritDe(['pop_forte', 'point_futur']), REFERENTIEL);

    expect(points.map((p) => p.code)).toEqual(['pop_forte']);
  });

  it('sans gabarit, aucun point supplémentaire', () => {
    // Une exploitation qui n'a jamais ouvert l'écran de configuration garde la
    // grille du SPRINT-20, et ne doit rien y perdre.
    expect(pointsDuGabarit(null, REFERENTIEL)).toEqual([]);
  });
});

describe('affichage', () => {
  it('traduit par code, et se rabat sur le libellé du serveur', () => {
    const catalogue = { pop_forte: 'Strong population' };

    expect(libellePoint(catalogue, REFERENTIEL[0])).toBe('Strong population');
    expect(libellePoint(catalogue, REFERENTIEL[2])).toBe('Libellé serveur de propolisation');
  });

  it('groupe par famille en gardant l’ordre reçu', () => {
    expect(parCategorie(REFERENTIEL).map(([famille]) => famille)).toEqual([
      'population',
      'batisse',
    ]);
  });

  it('ne calcule aucun taux sans relevé', () => {
    // Zéro sur zéro n'est pas zéro pour cent : afficher 0 % ferait lire « jamais
    // constaté » là où personne n'a regardé.
    expect(tauxPresence({ releves: 0, presents: 0 })).toBeNull();
    expect(tauxPresence({ releves: 4, presents: 1 })).toBe(25);
  });
});
