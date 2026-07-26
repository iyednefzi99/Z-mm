import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  ErreurApi,
  ErreurHorsLigne,
  grappesSites,
  sites,
  tourneeAgent,
  voisinsSite,
} from './client';
import { tailleFile } from '../offline/file';
import { definir, reinitialiserSession, sessionCourante } from '../auth/session';

/**
 * Tests du client d'API (US-049, SPRINT-10) : gestion des erreurs, bascule
 * hors-ligne (US-011) et construction des URL des endpoints spatiaux du
 * SPRINT-10 (US-045 à US-047).
 */

/** Réponse `fetch` minimale, suffisante pour ce que le client en lit. */
function reponse(corps: unknown, statut = 200): Response {
  return {
    ok: statut >= 200 && statut < 300,
    status: statut,
    json: () => Promise.resolve(corps),
  } as Response;
}

describe('client d’API', () => {
  beforeEach(() => {
    reinitialiserSession();
    document.cookie = 'XSRF-TOKEN=; max-age=0';
    vi.stubGlobal('fetch', vi.fn());
  });

  const dernierAppel = () => vi.mocked(fetch).mock.calls[0];

  it('n’envoie AUCUN en-tête Authorization : le navigateur ne détient plus de jeton', async () => {
    // Cœur de l'ADR-006. Ce test échoue si quelqu'un réintroduit un jeton côté
    // client, ce qui est exactement le retour en arrière à empêcher.
    vi.mocked(fetch).mockResolvedValue(reponse([]));

    await sites.lister();

    const [, options] = vi.mocked(fetch).mock.calls[0];
    expect(new Headers(options?.headers).get('Authorization')).toBeNull();
  });

  it('envoie le cookie de session sur chaque appel', async () => {
    // Sans `credentials: 'include'`, le cookie HttpOnly ne partirait pas et
    // toute l'authentification tomberait — silencieusement, en 401.
    vi.mocked(fetch).mockResolvedValue(reponse([]));

    await sites.lister();

    expect(vi.mocked(fetch).mock.calls[0][1]?.credentials).toBe('include');
  });

  it('joint le jeton CSRF aux mutations, et à elles seules', async () => {
    document.cookie = 'XSRF-TOKEN=jeton-csrf-de-test';
    vi.mocked(fetch).mockResolvedValue(reponse({ id: 1 }));

    await sites.creer({ nom: 'Rucher' } as never);
    const enTetesMutation = new Headers(vi.mocked(fetch).mock.calls[0][1]?.headers);
    expect(enTetesMutation.get('X-XSRF-TOKEN')).toBe('jeton-csrf-de-test');

    vi.mocked(fetch).mockClear();
    await sites.lister();
    const enTetesLecture = new Headers(vi.mocked(fetch).mock.calls[0][1]?.headers);
    // Une lecture ne change pas d'état : CSRF n'y ajouterait rien.
    expect(enTetesLecture.get('X-XSRF-TOKEN')).toBeNull();
  });

  it('efface la session connue sur un 401', async () => {
    definir({ utilisateur: 'agent', roles: ['apiculteur'], exploitation: 'demo' });
    vi.mocked(fetch).mockResolvedValue(reponse({ detail: 'expirée' }, 401));

    await expect(sites.lister()).rejects.toBeInstanceOf(ErreurApi);
    expect(sessionCourante()).toBeNull();
  });

  it('met une mutation en file quand le réseau est absent (US-011)', async () => {
    vi.mocked(fetch).mockRejectedValue(new TypeError('Failed to fetch'));

    await expect(
      sites.creer({
        nom: 'Rucher du Lot',
        fermeId: 1,
        latitude: 44.447,
        longitude: 1.441,
        altitude: null,
        dateMiseEnOeuvre: '2026-04-01',
        dateDemenagement: null,
        dateCloture: null,
      }),
    ).rejects.toBeInstanceOf(ErreurHorsLigne);

    expect(tailleFile()).toBe(1);
  });

  it('ne met pas une lecture en file : elle échoue franchement', async () => {
    vi.mocked(fetch).mockRejectedValue(new TypeError('Failed to fetch'));

    await expect(sites.lister()).rejects.toBeInstanceOf(TypeError);
    expect(tailleFile()).toBe(0);
  });

  it('construit l’URL du regroupement spatial avec ses deux paramètres (US-045)', async () => {
    vi.mocked(fetch).mockResolvedValue(reponse([]));

    await grappesSites(20000, 3);

    expect(dernierAppel()[0]).toBe('/api/sites/grappes?distanceMetres=20000&minimumSites=3');
  });

  it('applique les valeurs par défaut du regroupement (15 km, 2 sites)', async () => {
    vi.mocked(fetch).mockResolvedValue(reponse([]));

    await grappesSites();

    expect(dernierAppel()[0]).toBe('/api/sites/grappes?distanceMetres=15000&minimumSites=2');
  });

  it('construit l’URL des voisins d’un site (US-046)', async () => {
    vi.mocked(fetch).mockResolvedValue(reponse([]));

    await voisinsSite(12, 5);

    expect(dernierAppel()[0]).toBe('/api/sites/12/voisins?limite=5');
  });

  it('omet le site de départ de la tournée quand il n’est pas imposé (US-047)', async () => {
    vi.mocked(fetch).mockResolvedValue(reponse({}));

    await tourneeAgent(3, '2026-12-04');

    expect(dernierAppel()[0]).toBe('/api/plannings/tournee?agentId=3&date=2026-12-04');
  });

  it('ajoute le site de départ de la tournée lorsqu’il est imposé (US-047)', async () => {
    vi.mocked(fetch).mockResolvedValue(reponse({}));

    await tourneeAgent(3, '2026-12-04', 7);

    expect(dernierAppel()[0]).toBe(
      '/api/plannings/tournee?agentId=3&date=2026-12-04&departSiteId=7',
    );
  });
});
