import { describe, expect, it } from 'vitest';
import { VIDE, formatsDe } from './formats';

/**
 * Tests du formatage localisé (US-053, SPRINT-11).
 *
 * Les assertions évitent de figer la ponctuation exacte d'ICU, qui varie d'une
 * version de Node à l'autre : ce qui est vérifié est ce qui compte
 * fonctionnellement — l'ISO brut a disparu, la locale est respectée, et l'absence
 * de valeur ne produit ni « Invalid Date » ni « NaN ».
 */

const fr = formatsDe('fr');
const en = formatsDe('en');
const ar = formatsDe('ar');

describe('dates', () => {
  it('ne rend plus la date en ISO brut', () => {
    expect(fr.date('2026-12-04')).not.toBe('2026-12-04');
    expect(en.date('2026-12-04')).not.toBe('2026-12-04');
    expect(ar.date('2026-12-04')).not.toBe('2026-12-04');
  });

  it('rend le jour, le mois et l’année dans chaque langue', () => {
    for (const format of [fr, en, ar]) {
      const rendu = format.date('2026-12-04');
      expect(rendu).toMatch(/4/);
      expect(rendu).toMatch(/2026/);
    }
  });

  it('donne des rendus distincts en français et en anglais', () => {
    expect(fr.date('2026-12-04')).not.toBe(en.date('2026-12-04'));
  });

  it('garde les chiffres latins en arabe, comme le reste de l’interface', () => {
    // Un chiffre indo-arabe (٠-٩) trahirait une numération incohérente avec les
    // traductions existantes (« 1 و2 و3 كم »).
    expect(ar.date('2026-12-04')).not.toMatch(/[٠-٩]/);
    expect(ar.nombre(1234.5)).not.toMatch(/[٠-٩]/);
  });

  it('rend le tiret cadratin pour une date absente ou invalide', () => {
    expect(fr.date(null)).toBe(VIDE);
    expect(fr.date(undefined)).toBe(VIDE);
    expect(fr.date('')).toBe(VIDE);
    expect(fr.date('pas-une-date')).toBe(VIDE);
  });

  it('ajoute l’heure sur un horodatage', () => {
    const rendu = fr.dateHeure('2026-12-04T14:35:00Z');

    expect(rendu).toMatch(/2026/);
    expect(rendu).toMatch(/\d{1,2}[:h]\d{2}/);
  });

  it('rend le tiret cadratin pour un horodatage absent', () => {
    expect(fr.dateHeure(null)).toBe(VIDE);
  });
});

describe('nombres', () => {
  it('utilise la virgule décimale en français et le point en anglais', () => {
    expect(fr.nombre(1.57)).toBe('1,57');
    expect(en.nombre(1.57)).toBe('1.57');
  });

  it('respecte le nombre de décimales demandé', () => {
    expect(fr.nombre(3, 0)).toBe('3');
    expect(fr.nombre(3, 2)).toBe('3,00');
  });

  it('rend le tiret cadratin pour une valeur absente ou NaN', () => {
    expect(fr.nombre(null)).toBe(VIDE);
    expect(fr.nombre(undefined)).toBe(VIDE);
    expect(fr.nombre(Number.NaN)).toBe(VIDE);
  });

  it('n’avale pas le zéro', () => {
    expect(fr.nombre(0, 0)).toBe('0');
  });
});

describe('distances', () => {
  it('rend les mètres en deçà du kilomètre', () => {
    expect(fr.distance(350)).toBe('350 m');
    expect(fr.distance(999)).toBe('999 m');
  });

  it('bascule en kilomètres à partir de 1 000 m', () => {
    expect(fr.distance(1000)).toBe('1,00 km');
    expect(fr.distance(1573.4)).toBe('1,57 km');
    expect(en.distance(1573.4)).toBe('1.57 km');
  });

  it('rend le tiret cadratin pour une distance absente', () => {
    expect(fr.distance(null)).toBe(VIDE);
  });
});
