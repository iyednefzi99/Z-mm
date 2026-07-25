import { beforeEach, describe, expect, it, vi } from 'vitest';

/**
 * Tests du rejeu de requête après rafraîchissement du jeton (US-050, SPRINT-11).
 *
 * L'échange avec Keycloak est simulé : ce qui est vérifié ici est la logique du
 * client d'API — une seule tentative, rejeu de la requête d'origine, un seul
 * rafraîchissement pour plusieurs 401 simultanés, et fermeture de session en
 * dernier recours.
 */
vi.mock('../auth/oidc', () => ({
  echangerRafraichissement: vi.fn(),
  oidcConfigure: () => true,
  deconnexionOidc: vi.fn(),
  demarrerConnexion: vi.fn(),
  terminerConnexion: vi.fn(),
}));

const { echangerRafraichissement } = await import('../auth/oidc');
const { sites, ruches } = await import('./client');
const { jetonCourant, ouvrirSession } = await import('../auth/session');

function reponse(corps: unknown, statut = 200): Response {
  return {
    ok: statut >= 200 && statut < 300,
    status: statut,
    json: () => Promise.resolve(corps),
  } as Response;
}

describe('rafraîchissement et rejeu', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
    ouvrirSession('jeton-expire', 'jeton-de-rafraichissement');
  });

  it('rejoue la requête après un rafraîchissement réussi', async () => {
    vi.mocked(echangerRafraichissement).mockResolvedValue(true);
    vi.mocked(fetch)
      .mockResolvedValueOnce(reponse({}, 401))
      .mockResolvedValueOnce(reponse([{ id: 1, nom: 'Rucher du Lot' }]));

    const resultat = await sites.lister();

    expect(echangerRafraichissement).toHaveBeenCalledTimes(1);
    expect(fetch).toHaveBeenCalledTimes(2);
    expect(resultat).toHaveLength(1);
    // La session survit : c'est tout l'objet de la story.
    expect(jetonCourant()).not.toBeNull();
  });

  it('rejoue avec la même URL et la même méthode', async () => {
    vi.mocked(echangerRafraichissement).mockResolvedValue(true);
    vi.mocked(fetch)
      .mockResolvedValueOnce(reponse({}, 401))
      .mockResolvedValueOnce(reponse({ id: 7 }));

    await ruches.supprimer(7);

    const [premier, second] = vi.mocked(fetch).mock.calls;
    expect(premier[0]).toBe('/api/ruches/7');
    expect(second[0]).toBe('/api/ruches/7');
    expect(second[1]?.method).toBe('DELETE');
  });

  it('n’essaie qu’une fois : un second 401 ferme la session', async () => {
    vi.mocked(echangerRafraichissement).mockResolvedValue(true);
    vi.mocked(fetch).mockResolvedValue(reponse({}, 401));

    await expect(sites.lister()).rejects.toMatchObject({ statut: 401 });

    // Deux appels réseau (l'original et le rejeu), pas une boucle.
    expect(fetch).toHaveBeenCalledTimes(2);
    expect(echangerRafraichissement).toHaveBeenCalledTimes(1);
    expect(jetonCourant()).toBeNull();
  });

  it('ferme la session quand le rafraîchissement échoue', async () => {
    vi.mocked(echangerRafraichissement).mockResolvedValue(false);
    vi.mocked(fetch).mockResolvedValue(reponse({}, 401));

    await expect(sites.lister()).rejects.toMatchObject({ statut: 401 });

    expect(fetch).toHaveBeenCalledTimes(1);
    expect(jetonCourant()).toBeNull();
  });

  it('ne déclenche qu’un rafraîchissement pour plusieurs 401 simultanés', async () => {
    let debloquer: (valeur: boolean) => void = () => undefined;
    vi.mocked(echangerRafraichissement).mockReturnValue(
      new Promise<boolean>((resoudre) => {
        debloquer = resoudre;
      }),
    );
    vi.mocked(fetch)
      .mockResolvedValueOnce(reponse({}, 401))
      .mockResolvedValueOnce(reponse({}, 401))
      .mockResolvedValueOnce(reponse({}, 401))
      .mockResolvedValue(reponse([]));

    const trois = Promise.all([sites.lister(), sites.lister(), sites.lister()]);
    debloquer(true);
    await trois;

    // Le verrou du client : trois 401, un seul échange de jeton.
    expect(echangerRafraichissement).toHaveBeenCalledTimes(1);
  });

  it('laisse passer les erreurs qui ne sont pas des 401', async () => {
    vi.mocked(fetch).mockResolvedValue(reponse({ detail: 'Site introuvable' }, 404));

    await expect(sites.obtenir(1)).rejects.toMatchObject({ statut: 404 });

    expect(echangerRafraichissement).not.toHaveBeenCalled();
    expect(jetonCourant()).not.toBeNull();
  });
});
