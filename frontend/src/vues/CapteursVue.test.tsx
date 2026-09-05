import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { LangueProvider } from '../i18n/langue';
import { CapteursVue } from './CapteursVue';
import type { Meteo } from '../api/types';

/**
 * Prévisions météo de la vue Capteurs (US-029).
 *
 * <p>Ce qui est vérifié ici n'est pas « la météo s'affiche » mais deux décisions :
 * l'horizon saisi part bien vers le serveur (une prévision à 7 jours qui en
 * demanderait 1 ne se verrait nulle part ailleurs), et une réponse sans prévision
 * dit qu'il n'y en a pas au lieu de laisser un tableau vide — le serveur rend une
 * liste vide, jamais `null`, et l'écran doit traduire ce cas.
 */
vi.mock('../api/client', () => ({
  chargerAlertesOuvertes: vi.fn(),
  chargerMeteo: vi.fn(),
  chargerSerieJournaliere: vi.fn(),
  detecterAnomalie: vi.fn(),
  getZummHoneyActualQuantity: vi.fn(),
  ingererMesure: vi.fn(),
  ruches: { lister: vi.fn() },
  sites: { lister: vi.fn() },
  ErreurApi: class ErreurApi extends Error {},
}));

const { chargerAlertesOuvertes, chargerMeteo, ruches, sites } = await import('../api/client');

const METEO: Meteo = {
  siteId: 1,
  latitude: 36.8,
  longitude: 10.2,
  temperatureCelsius: 21.5,
  humiditePourcent: 60,
  ventKmh: 12,
  source: 'simulation',
  instant: '2026-08-18T10:00:00Z',
  previsions: [
    {
      date: '2026-08-18',
      temperatureMinCelsius: 14,
      temperatureMaxCelsius: 29,
      precipitationsMm: 0,
      ventMaxKmh: 18.5,
    },
    {
      date: '2026-08-19',
      temperatureMinCelsius: 15.2,
      temperatureMaxCelsius: 30.1,
      precipitationsMm: 3.2,
      ventMaxKmh: 19,
    },
  ],
};

const monter = () =>
  render(
    <LangueProvider>
      <CapteursVue />
    </LangueProvider>,
  );

describe('vue Capteurs — prévisions météo', () => {
  beforeEach(() => {
    vi.mocked(ruches.lister).mockResolvedValue([]);
    vi.mocked(sites.lister).mockResolvedValue([
      {
        id: 1,
        nom: 'Rucher de Sidi Thabet',
        fermeId: 1,
        fermeNom: 'Ferme',
        latitude: 36.8,
        longitude: 10.2,
        altitude: null,
        rayonButinageKm: null,
        dateMiseEnOeuvre: '2026-04-01',
        dateDemenagement: null,
        dateCloture: null,
        adresseRue: null,
        codePostal: null,
        ville: null,
        pays: null,
        typeSite: null,
        exposition: null,
        priorite: 'normale',
        couvertureReseau: null,
        ressources: [],
        creeLe: '2026-04-01T08:00:00Z',
        majLe: '2026-04-01T08:00:00Z',
      },
    ]);
    vi.mocked(chargerAlertesOuvertes).mockResolvedValue([]);
    vi.mocked(chargerMeteo).mockResolvedValue(METEO);
  });

  it("transmet l'horizon saisi au serveur et rend une ligne par jour", async () => {
    const utilisateur = userEvent.setup();
    monter();

    const horizon = await screen.findByLabelText('Horizon (jours)');
    await utilisateur.clear(horizon);
    await utilisateur.type(horizon, '2');
    await utilisateur.click(screen.getByRole('button', { name: 'Afficher' }));

    expect(chargerMeteo).toHaveBeenCalledWith(1, 2);
    // Deux jours rendus, plus la ligne d'en-tête.
    expect(await screen.findByText('Prévisions')).toBeInTheDocument();
    expect(screen.getAllByRole('row')).toHaveLength(3);
    expect(screen.getByText('18,5')).toBeInTheDocument();
  });

  it('annonce explicitement une réponse sans prévision', async () => {
    vi.mocked(chargerMeteo).mockResolvedValue({ ...METEO, previsions: [] });
    const utilisateur = userEvent.setup();
    monter();

    await screen.findByLabelText('Horizon (jours)');
    await utilisateur.click(screen.getByRole('button', { name: 'Afficher' }));

    expect(await screen.findByText('Aucune prévision disponible.')).toBeInTheDocument();
    expect(screen.queryByRole('row')).not.toBeInTheDocument();
  });
});
