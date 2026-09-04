import { describe, expect, it } from 'vitest';
import type { EmportRucher, Site } from '../api/types';
import {
  ageEnJours,
  emportDe,
  emports,
  JOURS_VALIDITE,
  perime,
  purger,
  purgerTout,
  ranger,
} from './emport';

/**
 * Tests de l'emport hors ligne (SPRINT-24, lot C).
 *
 * <p>Ce module met en œuvre l'ADR-012, qui précise le refus de cacher `/api`
 * sans le contredire. Ce qui se vérifie ici n'est pas le rangement — trivial —
 * mais les trois propriétés dont dépend la promesse :
 *
 * <ol>
 *   <li>l'instant du prélèvement vient du SERVEUR et ne se recalcule pas ;
 *   <li>un emport périmé <strong>ne se lit pas</strong> — il ne suffit pas de
 *       l'assortir d'un avertissement, ce serait parier que l'avertissement est
 *       lu ;
 *   <li>un rucher n'a jamais deux instantanés : réemporter remplace.
 * </ol>
 */

const SITE = { id: 7, nom: 'Rucher du causse' } as Site;

const emport = (preleveLe: string, site: Site = SITE): EmportRucher => ({
  preleveLe,
  site,
  ruches: [],
  dernieresVisites: [],
  tachesOuvertes: [],
  sousCarence: [],
});

const ilYA = (jours: number): string =>
  new Date(Date.now() - jours * 86_400_000).toISOString();

describe('emport hors ligne', () => {
  it('conserve l’instant du prélèvement rendu par le serveur', () => {
    const range = ranger(emport('2026-09-02T12:12:00Z'));

    // Recalculer la date à la réception donnerait l'heure du téléphone, pas
    // celle de la lecture : c'est précisément ce qui ferait passer une donnée
    // vieille de trois heures pour une donnée fraîche.
    expect(range.preleveLe).toBe('2026-09-02T12:12:00Z');
    expect(range.siteNom).toBe('Rucher du causse');
    expect(emports()).toHaveLength(1);
  });

  it('remplace l’instantané du même rucher au lieu d’en empiler un second', () => {
    ranger(emport(ilYA(3)));
    ranger(emport(ilYA(0)));

    expect(emports()).toHaveLength(1);
    expect(ageEnJours(emports()[0])).toBe(0);
  });

  it('range les ruchers du plus récemment prélevé au plus ancien', () => {
    ranger(emport(ilYA(5), { id: 1, nom: 'Ancien' } as Site));
    ranger(emport(ilYA(1), { id: 2, nom: 'Récent' } as Site));

    expect(emports().map((e) => e.siteNom)).toEqual(['Récent', 'Ancien']);
  });

  it('refuse de servir un emport périmé, au lieu de l’assortir d’un avertissement', () => {
    ranger(emport(ilYA(JOURS_VALIDITE)));

    // Le laisser passer avec un message reviendrait à parier que l'utilisateur
    // lit le message — exactement le pari que l'ADR refuse de faire.
    expect(emportDe(7)).toBeNull();
    // Il reste LISTÉ : l'écran doit pouvoir dire qu'il est trop vieux, ce qu'il
    // ne pourrait pas faire s'il disparaissait tout seul.
    expect(emports()).toHaveLength(1);
    expect(perime(emports()[0])).toBe(true);
  });

  it('sert un emport de la veille', () => {
    ranger(emport(ilYA(1)));

    expect(emportDe(7)).not.toBeNull();
    expect(ageEnJours(emportDe(7)!)).toBe(1);
  });

  it('purge un rucher, puis tous', () => {
    ranger(emport(ilYA(0), { id: 1, nom: 'Un' } as Site));
    ranger(emport(ilYA(0), { id: 2, nom: 'Deux' } as Site));

    purger(1);
    expect(emports().map((e) => e.siteId)).toEqual([2]);

    purgerTout();
    expect(emports()).toEqual([]);
  });

  it('ne se laisse pas casser par un localStorage corrompu', () => {
    localStorage.setItem('zumm.emports', 'ceci n’est pas du JSON');

    // Repartir sans emport est le pire qui doive arriver : l'application
    // s'ouvre, et l'écran dit qu'il n'y a rien d'emporté.
    expect(emports()).toEqual([]);
  });
});
