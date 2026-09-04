import { describe, expect, it } from 'vitest';
import type { EtapeTournee, Tournee } from '../api/types';
import { afficher, distanceApproximative } from './tournee';

const TUNIS = { latitude: 36.8065, longitude: 10.1815 };
const SFAX = { latitude: 34.7406, longitude: 10.7603 };
const BIZERTE = { latitude: 37.2744, longitude: 9.8739 };

function etape(
  nom: string,
  point: { latitude: number; longitude: number },
  id: number,
): EtapeTournee {
  return {
    ordre: id,
    siteId: id,
    siteNom: nom,
    latitude: point.latitude,
    longitude: point.longitude,
    planningIds: [id],
    nombreVisites: 1,
    distanceDepuisPrecedenteMetres: 0,
  };
}

const PROPOSITION: Tournee = {
  agentId: 1,
  agentNom: 'Amel',
  date: '2026-09-01',
  nombreSites: 3,
  nombreVisites: 3,
  etapes: [etape('Bizerte', BIZERTE, 1), etape('Tunis', TUNIS, 2), etape('Sfax', SFAX, 3)],
  // Chiffres « du serveur » : volontairement différents de ce que la formule
  // locale rendrait, pour que le test distingue les deux chemins.
  distanceTotaleMetres: 294000,
};

describe('distanceApproximative', () => {
  it('mesure une distance connue', () => {
    // Tunis — Sfax, ~235 km à vol d'oiseau.
    expect(distanceApproximative(TUNIS, SFAX)).toBeCloseTo(235576, -3);
  });

  it('est symétrique et nulle sur place', () => {
    expect(distanceApproximative(TUNIS, SFAX)).toBeCloseTo(distanceApproximative(SFAX, TUNIS), 6);
    expect(distanceApproximative(TUNIS, TUNIS)).toBe(0);
  });
});

describe('afficher', () => {
  it("laisse passer les chiffres du serveur tant que l'ordre n'a pas bougé", () => {
    const vue = afficher(PROPOSITION, null);
    expect(vue.etapes).toBe(PROPOSITION.etapes);
    expect(vue.distanceTotaleMetres).toBe(294000);
    // Le point de ce test : rien n'est recalculé sur le chemin nominal, donc rien
    // ne peut diverger de PostGIS.
    expect(vue.estimee).toBe(false);
  });

  it("recalcule et s'annonce comme estimation dès que l'ordre change", () => {
    const inverse = [...PROPOSITION.etapes].reverse();
    const vue = afficher(PROPOSITION, inverse);

    expect(vue.estimee).toBe(true);
    expect(vue.etapes.map((e) => e.siteNom)).toEqual(['Sfax', 'Tunis', 'Bizerte']);
    expect(vue.etapes.map((e) => e.ordre)).toEqual([1, 2, 3]);
    // Chemin ouvert : la première étape n'a pas de tronçon entrant.
    expect(vue.etapes[0].distanceDepuisPrecedenteMetres).toBe(0);
    expect(vue.distanceTotaleMetres).toBeCloseTo(
      distanceApproximative(SFAX, TUNIS) + distanceApproximative(TUNIS, BIZERTE),
      6,
    );
  });

  it('rend un ordre plus long plus long', () => {
    // Bizerte → Tunis → Sfax descend la côte ; Tunis → Bizerte → Sfax remonte
    // puis redescend. La simulation doit voir la différence.
    const bon = afficher(PROPOSITION, PROPOSITION.etapes).distanceTotaleMetres;
    const mauvais = afficher(PROPOSITION, [
      PROPOSITION.etapes[1],
      PROPOSITION.etapes[0],
      PROPOSITION.etapes[2],
    ]).distanceTotaleMetres;
    expect(mauvais).toBeGreaterThan(bon);
  });
});
