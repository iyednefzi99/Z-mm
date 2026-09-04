import { describe, expect, it } from 'vitest';
import {
  definirEtendue,
  ECRANS_AVANCES,
  etendueCourante,
  ongletMontre,
} from './interface';
import { ONGLETS, ongletAutorise } from '../routage/routes';

/**
 * Étendue de l'interface (SPRINT-25, lot J).
 *
 * <p>Ce qui se vérifie ici tient en une phrase : **masquer n'est pas
 * interdire**. Les rôles décident de ce qui est permis, ce réglage de ce qui est
 * montré, et rien ne doit rapprocher les deux — une interface qui aurait l'air
 * de retirer des droits ferait appeler le responsable pour un réglage que
 * l'utilisateur pouvait changer lui-même.
 */
describe('étendue de l’interface', () => {
  it('montre tous les écrans en interface complète', () => {
    expect(ONGLETS.every((onglet) => ongletMontre(onglet, 'complete'))).toBe(true);
  });

  it('masque les écrans avancés en interface essentielle, et eux seuls', () => {
    const masques = ONGLETS.filter((onglet) => !ongletMontre(onglet, 'essentielle'));

    expect([...masques].sort()).toEqual([...ECRANS_AVANCES].sort());
  });

  it('garde ce qui sert à tenir un rucher', () => {
    // Le point du mode : l'apiculteur de trois ruches ouvre le produit pour
    // noter une visite, pas pour lire un journal d'audit.
    for (const essentiel of ['ruches', 'sites', 'visites', 'taches', 'recoltes'] as const) {
      expect(ongletMontre(essentiel, 'essentielle')).toBe(true);
    }
  });

  it('ne touche à aucun droit : un écran masqué reste autorisé', () => {
    // C'est l'invariant du lot. `ongletAutorise` lit les rôles, `ongletMontre`
    // lit une préférence de confort : les deux ne doivent jamais se rejoindre.
    for (const avance of ECRANS_AVANCES) {
      expect(ongletMontre(avance, 'essentielle')).toBe(false);
      expect(ongletAutorise(avance, ['responsable'])).toBe(true);
    }
  });

  it('mémorise le réglage, et repart en complète quand rien n’est enregistré', () => {
    expect(etendueCourante()).toBe('complete');

    definirEtendue('essentielle');
    expect(localStorage.getItem('zumm.interface')).toBe('essentielle');
    expect(etendueCourante()).toBe('essentielle');
  });

  it('ignore une valeur enregistrée qui n’existe plus', () => {
    localStorage.setItem('zumm.interface', 'minimale');

    // L'interface complète est le repli juste : elle ne cache rien, donc ne
    // surprend personne.
    expect(etendueCourante()).toBe('complete');
  });
});
