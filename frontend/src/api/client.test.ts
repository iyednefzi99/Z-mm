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
import { jetonCourant, ouvrirSession } from '../auth/session';

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
    vi.stubGlobal('fetch', vi.fn());
  });

  const dernierAppel = () => vi.mocked(fetch).mock.calls[0];

  it('joint le jeton de session en en-tête Authorization', async () => {
    ouvrirSession('jeton-de-test');
    vi.mocked(fetch).mockResolvedValue(reponse([]));

    await sites.lister();

    const [, options] = dernierAppel();
    const entetes = new Headers(options?.headers);
    expect(entetes.get('Authorization')).toBe('Bearer jeton-de-test');
    expect(entetes.get('Accept')).toBe('application/json');
  });

  it('n’envoie pas d’en-tête Authorization sans session ouverte', async () => {
    vi.mocked(fetch).mockResolvedValue(reponse([]));

    await sites.lister();

    const [, options] = dernierAppel();
    expect(new Headers(options?.headers).get('Authorization')).toBeNull();
  });

  it('transforme une réponse d’erreur en ErreurApi portant statut et détail', async () => {
    vi.mocked(fetch).mockResolvedValue(reponse({ detail: 'Site introuvable' }, 404));

    await expect(sites.obtenir(42)).rejects.toMatchObject({
      name: 'ErreurApi',
      statut: 404,
      detail: 'Site introuvable',
    });
    await expect(sites.obtenir(42)).rejects.toBeInstanceOf(ErreurApi);
  });

  it('ferme la session sur 401 pour ramener à l’écran de connexion', async () => {
    ouvrirSession('jeton-expire');
    vi.mocked(fetch).mockResolvedValue(reponse({}, 401));

    await expect(sites.lister()).rejects.toBeInstanceOf(ErreurApi);
    expect(jetonCourant()).toBeNull();
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
