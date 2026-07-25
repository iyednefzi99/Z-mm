import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  MARGE_MS,
  creerVerrou,
  delaiAvantRafraichissement,
  expiration,
  planifier,
} from './rafraichissement';

/**
 * Tests de la mécanique de rafraîchissement (US-050, SPRINT-11).
 *
 * Le module traverse le chemin critique de toutes les requêtes : il est vérifié
 * ici sans réseau, sans Keycloak et sans horloge réelle.
 */

/** Fabrique un JWT non signé — seule la charge utile compte pour ce module. */
function jeton(charge: Record<string, unknown>): string {
  const b64 = (o: unknown) =>
    btoa(JSON.stringify(o)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${b64({ alg: 'none' })}.${b64(charge)}.signature`;
}

describe('lecture de l’échéance du jeton', () => {
  it('lit le champ exp et le rend en millisecondes', () => {
    expect(expiration(jeton({ exp: 1_800_000_000 }))).toBe(1_800_000_000_000);
  });

  it('supporte des claims accentués (charge utile en UTF-8)', () => {
    const jwt = jeton({ exp: 1_800_000_000, nom: 'Aurélie Ménard' });

    expect(expiration(jwt)).toBe(1_800_000_000_000);
  });

  it('rend null sur un jeton sans échéance, illisible ou vide', () => {
    expect(expiration(jeton({ sub: 'sans-exp' }))).toBeNull();
    expect(expiration('pas-un-jwt')).toBeNull();
    expect(expiration('a.b.c')).toBeNull();
    expect(expiration('')).toBeNull();
  });
});

describe('délai avant rafraîchissement', () => {
  it('anticipe l’échéance de la marge', () => {
    const maintenant = 1_000_000;
    const jwt = jeton({ exp: (maintenant + 300_000) / 1000 });

    expect(delaiAvantRafraichissement(jwt, maintenant)).toBe(300_000 - MARGE_MS);
  });

  it('rend 0 — donc « tout de suite » — quand l’échéance est dépassée', () => {
    const maintenant = 2_000_000;
    const jwt = jeton({ exp: (maintenant - 60_000) / 1000 });

    expect(delaiAvantRafraichissement(jwt, maintenant)).toBe(0);
  });

  it('rend 0 et non un délai négatif dans la fenêtre de marge', () => {
    const maintenant = 3_000_000;
    const jwt = jeton({ exp: (maintenant + 10_000) / 1000 });

    expect(delaiAvantRafraichissement(jwt, maintenant)).toBe(0);
  });

  it('rend null quand il n’y a rien à planifier', () => {
    expect(delaiAvantRafraichissement('illisible')).toBeNull();
  });
});

describe('verrou de rafraîchissement', () => {
  it('ne lance qu’une exécution pour des appels concurrents', async () => {
    let appels = 0;
    let debloquer: (valeur: boolean) => void = () => undefined;
    const action = vi.fn(() => {
      appels += 1;
      return new Promise<boolean>((resoudre) => {
        debloquer = resoudre;
      });
    });
    const verrouille = creerVerrou(action);

    const trois = Promise.all([verrouille(), verrouille(), verrouille()]);
    debloquer(true);

    expect(await trois).toEqual([true, true, true]);
    expect(appels).toBe(1);
  });

  it('relâche le verrou : un appel ultérieur relance bien l’action', async () => {
    const action = vi.fn(() => Promise.resolve(true));
    const verrouille = creerVerrou(action);

    await verrouille();
    await verrouille();

    expect(action).toHaveBeenCalledTimes(2);
  });

  it('relâche le verrou même quand l’action échoue', async () => {
    const action = vi
      .fn<() => Promise<boolean>>()
      .mockRejectedValueOnce(new Error('réseau'))
      .mockResolvedValueOnce(true);
    const verrouille = creerVerrou(action);

    await expect(verrouille()).rejects.toThrow('réseau');

    // Sans relâchement, ce second appel rendrait la promesse déjà rejetée.
    await expect(verrouille()).resolves.toBe(true);
  });
});

describe('planification', () => {
  afterEach(() => vi.useRealTimers());

  it('déclenche l’action à l’échéance moins la marge', () => {
    vi.useFakeTimers();
    const maintenant = Date.now();
    const jwt = jeton({ exp: (maintenant + 300_000) / 1000 });
    const action = vi.fn();

    planifier(jwt, action);

    vi.advanceTimersByTime(300_000 - MARGE_MS - 1);
    expect(action).not.toHaveBeenCalled();
    vi.advanceTimersByTime(1);
    expect(action).toHaveBeenCalledTimes(1);
  });

  it('annule la planification quand la session change', () => {
    vi.useFakeTimers();
    const jwt = jeton({ exp: (Date.now() + 300_000) / 1000 });
    const action = vi.fn();

    planifier(jwt, action)();

    vi.advanceTimersByTime(600_000);
    expect(action).not.toHaveBeenCalled();
  });

  it('ne planifie rien sur un jeton sans échéance', () => {
    vi.useFakeTimers();
    const action = vi.fn();

    planifier('illisible', action);

    vi.advanceTimersByTime(600_000);
    expect(action).not.toHaveBeenCalled();
  });
});
