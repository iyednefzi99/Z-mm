import { describe, expect, it, vi } from 'vitest';
import { enfiler, rejouer, surFile, tailleFile, type MutationEnAttente } from './file';

/**
 * Tests de la file de synchronisation hors-ligne (US-011), SPRINT-10.
 *
 * C'est la pièce la plus délicate du front : elle persiste dans `localStorage`,
 * survit à un rechargement, et son rejeu doit distinguer une panne réseau (on
 * réessaiera) d'un refus serveur (inutile d'insister). Chaque cas est vérifié.
 */
describe('file de mutations hors-ligne', () => {
  it('démarre vide et compte les mutations enfilées', () => {
    expect(tailleFile()).toBe(0);

    enfiler({ methode: 'POST', url: '/api/sites', corps: '{"nom":"Rucher"}' });
    enfiler({ methode: 'DELETE', url: '/api/sites/1' });

    expect(tailleFile()).toBe(2);
  });

  it('persiste la file dans localStorage pour survivre au rechargement', () => {
    enfiler({ methode: 'POST', url: '/api/ruches', corps: '{"modele":"Dadant"}' });

    const brut = localStorage.getItem('zumm.file.mutations');
    expect(brut).not.toBeNull();
    const file = JSON.parse(brut as string) as MutationEnAttente[];
    expect(file).toHaveLength(1);
    expect(file[0]).toMatchObject({ methode: 'POST', url: '/api/ruches' });
    expect(file[0].id).toBeTruthy();
  });

  it('ne se laisse pas casser par un localStorage corrompu', () => {
    localStorage.setItem('zumm.file.mutations', 'ceci n’est pas du JSON');

    expect(tailleFile()).toBe(0);
  });

  it('rejoue les mutations dans l’ordre puis vide la file', async () => {
    enfiler({ methode: 'POST', url: '/api/sites/1' });
    enfiler({ methode: 'POST', url: '/api/sites/2' });
    enfiler({ methode: 'POST', url: '/api/sites/3' });
    const envoyees: string[] = [];

    await rejouer((m) => {
      envoyees.push(m.url);
      return Promise.resolve({ ok: true, reseau: false });
    });

    expect(envoyees).toEqual(['/api/sites/1', '/api/sites/2', '/api/sites/3']);
    expect(tailleFile()).toBe(0);
  });

  it('interrompt le rejeu et conserve la file quand le réseau est toujours absent', async () => {
    enfiler({ methode: 'POST', url: '/api/sites/1' });
    enfiler({ methode: 'POST', url: '/api/sites/2' });
    const tentatives: string[] = [];

    await rejouer((m) => {
      tentatives.push(m.url);
      return Promise.resolve({ ok: false, reseau: true });
    });

    // On s'arrête à la première panne : inutile d'épuiser la file hors-ligne.
    expect(tentatives).toEqual(['/api/sites/1']);
    expect(tailleFile()).toBe(2);
  });

  it('retire une mutation définitivement refusée par le serveur (4xx)', async () => {
    enfiler({ methode: 'POST', url: '/api/sites/invalide' });

    await rejouer(() => Promise.resolve({ ok: false, reseau: false }));

    // Un 4xx ne guérira pas au prochain essai : la garder bloquerait la file.
    expect(tailleFile()).toBe(0);
  });

  it('notifie les abonnés de la taille courante et sait les désabonner', () => {
    const vues: number[] = [];
    const desabonner = surFile((n) => vues.push(n));

    enfiler({ methode: 'POST', url: '/api/sites' });
    desabonner();
    enfiler({ methode: 'POST', url: '/api/sites' });

    // Abonnement immédiat (0), puis la première mutation (1) ; plus rien après.
    expect(vues).toEqual([0, 1]);
  });

  it('n’appelle pas l’émetteur lorsque la file est vide', async () => {
    const envoyer = vi.fn();

    await rejouer(envoyer);

    expect(envoyer).not.toHaveBeenCalled();
  });
});
