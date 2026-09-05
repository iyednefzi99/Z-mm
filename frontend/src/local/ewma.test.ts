import { describe, expect, it } from 'vitest';
import { detecter, type PointSerie } from './ewma';

/**
 * Détection d'anomalie sur l'appareil (SPRINT-30, lot G).
 *
 * <p><strong>Le risque de ce fichier n'est pas l'erreur, c'est la DÉRIVE.</strong>
 * Le même calcul existe en Java (`AnomalieService`) et en TypeScript ; deux
 * implémentations d'une même formule s'écartent en silence, et l'écart ne se
 * verrait que sur un écran — un point signalé côté serveur et pas côté
 * appareil, ou l'inverse.
 *
 * <p>La série de référence est celle d'`AnomalieServiceTest.repereUnePointe`,
 * et le test miroir côté Java fixe les mêmes nombres. Toucher l'un sans l'autre
 * fait échouer une des deux campagnes, ce qui est exactement le but.
 */

/** La série d'`AnomalieServiceTest` : sept mesures stables, puis une pointe. */
const REFERENCE: PointSerie[] = [30.0, 30.2, 29.9, 30.1, 29.8, 30.3, 29.9, 50.0].map(
  (valeur, i) => ({ instant: `2026-09-0${i + 1}T08:00:00Z`, valeur }),
);

describe('EWMA sur l’appareil', () => {
  it('repère la pointe isolée, et elle seule', () => {
    const resultat = detecter(REFERENCE);

    expect(resultat.points).toBe(8);
    expect(resultat.anomalies).toHaveLength(1);
    expect(resultat.anomalies[0].valeur).toBe(50);
  });

  it('rend exactement les nombres du serveur sur la série de référence', () => {
    const resultat = detecter(REFERENCE);

    // Ces trois nombres sont le contrat entre les deux implémentations. S'ils
    // changent ici, `AnomalieEmbarqueeTest` doit changer de la même façon —
    // sinon le mode local et le mode serveur ne disent plus la même chose de la
    // même ruche.
    expect(resultat.moyenne).toBe(36.012);
    expect(resultat.ecartType).toBe(9.159);
    expect(resultat.anomalies[0].z).toBe(113.015);
  });

  it('ne signale rien sur une série qui varie normalement', () => {
    const stable = [20, 21, 19, 22, 18, 20].map((valeur, i) => ({
      instant: `2026-09-0${i + 1}T08:00:00Z`,
      valeur,
    }));

    expect(detecter(stable).anomalies).toHaveLength(0);
  });

  it('signale beaucoup au début d’une série TROP régulière, et c’est attendu', () => {
    // Propriété réelle de l'EWMA, et non un défaut de ce portage : sur une série
    // dont les premiers points sont presque identiques, la variance connue est
    // minuscule, et un écart de deux dixièmes dépasse alors trois écarts-types.
    // Le serveur se comporte exactement pareil depuis le SPRINT-06 — le noter
    // ici évite qu'on « corrige » un jour le portage pour un écart qui n'en est
    // pas un.
    const tropReguliere = [20, 20.1, 19.9, 20, 20.2, 19.8].map((valeur, i) => ({
      instant: `2026-09-0${i + 1}T08:00:00Z`,
      valeur,
    }));

    expect(detecter(tropReguliere).anomalies.length).toBeGreaterThan(0);
  });

  it('rend une base de calcul vide plutôt que zéro sur une série vide', () => {
    const resultat = detecter([]);

    // Zéro se lirait comme une mesure ; `null` dit qu'il n'y en a aucune. La
    // même distinction que partout ailleurs dans ce produit.
    expect(resultat.moyenne).toBeNull();
    expect(resultat.ecartType).toBeNull();
    expect(resultat.points).toBe(0);
  });

  it('ne signale jamais le premier point, qui EST la ligne de base', () => {
    const chute = [40, 10, 10, 10].map((valeur, i) => ({
      instant: `2026-09-0${i + 1}T08:00:00Z`,
      valeur,
    }));

    // Le premier point n'a rien à quoi se comparer : le signaler ferait de
    // chaque nouvelle ruche une anomalie le jour de sa pose.
    expect(chute.length).toBe(4);
    expect(detecter(chute).anomalies.every((a) => a.instant !== chute[0].instant)).toBe(true);
  });
});
