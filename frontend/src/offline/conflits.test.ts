import { describe, expect, it } from 'vitest';
import { abandonner, enfiler, reappliquer, refus, rejouer, tailleFile } from './file';

/**
 * Conflits et refus au rejeu (SPRINT-24, lot C).
 *
 * <p>Ce que ces tests fixent est le comportement qui manquait, et qui était
 * silencieux : jusqu'ici, une mutation rejouée que le serveur refusait était
 * traitée comme « traitée » et <strong>disparaissait sans un mot</strong>. Le
 * cas le plus coûteux était le 409 — deux agents redescendus du même rucher —
 * c'est-à-dire précisément celui que le §11 nommait comme la limite du hors
 * ligne.
 */
describe('quarantaine des saisies refusées', () => {
  it('met en quarantaine une saisie refusée au lieu de la jeter', async () => {
    enfiler({ methode: 'PUT', url: '/api/visites/1', corps: '{"constatations":"cinq cadres"}' });

    await rejouer(() =>
      Promise.resolve({
        ok: false,
        reseau: false,
        refus: {
          statut: 409,
          detail: 'La visite 1 a ete modifiee depuis votre derniere lecture.',
          versionServeur: '2026-09-02T10:00:00Z',
        },
      }),
    );

    // Elle sort de la file — l'y garder la bloquerait — mais elle n'est pas
    // perdue : c'est une observation faite au rucher trois heures plus tôt.
    expect(tailleFile()).toBe(0);
    expect(refus()).toHaveLength(1);
    expect(refus()[0].statut).toBe(409);
    expect(refus()[0].versionServeur).toBe('2026-09-02T10:00:00Z');
    expect(refus()[0].mutation.corps).toBe('{"constatations":"cinq cadres"}');
  });

  it('réapplique une saisie SANS sa garde de version', async () => {
    enfiler({
      methode: 'PUT',
      url: '/api/visites/1',
      corps: '{}',
      entetes: { 'X-Zumm-Version': '2026-09-02T08:00:00Z', 'X-Autre': 'garde' },
    });
    await rejouer(() =>
      Promise.resolve({ ok: false, reseau: false, refus: { statut: 409, detail: 'conflit' } }),
    );

    reappliquer(refus()[0].mutation.id);

    // « Ma saisie l'emporte » : conserver la version la ferait refuser une
    // seconde fois, en boucle. Les autres en-têtes, eux, restent.
    const rejouees: Record<string, string>[] = [];
    await rejouer((m) => {
      rejouees.push(m.entetes ?? {});
      return Promise.resolve({ ok: true, reseau: false });
    });
    expect(rejouees).toEqual([{ 'X-Autre': 'garde' }]);
    expect(refus()).toEqual([]);
  });

  it('abandonne définitivement une saisie refusée', async () => {
    enfiler({ methode: 'POST', url: '/api/visites', corps: '{}' });
    await rejouer(() =>
      Promise.resolve({ ok: false, reseau: false, refus: { statut: 400, detail: 'invalide' } }),
    );

    abandonner(refus()[0].mutation.id);

    expect(refus()).toEqual([]);
    expect(tailleFile()).toBe(0);
  });

  it('rejoue les en-têtes propres à la mutation', async () => {
    enfiler({
      methode: 'PUT',
      url: '/api/visites/4',
      corps: '{}',
      entetes: { 'X-Zumm-Version': '2026-09-01T06:00:00Z' },
    });

    const vus: (Record<string, string> | undefined)[] = [];
    await rejouer((m) => {
      vus.push(m.entetes);
      return Promise.resolve({ ok: true, reseau: false });
    });

    // La garde est posée au moment de la SAISIE ; c'est des heures plus tard,
    // au rejeu, qu'elle sert. La régénérer alors ne protégerait de rien.
    expect(vus).toEqual([{ 'X-Zumm-Version': '2026-09-01T06:00:00Z' }]);
  });

  it('n’ouvre pas de quarantaine quand le rejeu réussit', async () => {
    enfiler({ methode: 'POST', url: '/api/visites', corps: '{}' });

    await rejouer(() => Promise.resolve({ ok: true, reseau: false }));

    expect(refus()).toEqual([]);
  });
});
