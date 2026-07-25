import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { LangueProvider } from '../i18n/langue';
import { DialoguesProvider } from '../ui/dialogues';
import { SitesVue } from './SitesVue';
import type { Site } from '../api/types';

/**
 * Tests de la vue Sites (US-003) et de ses voisins les plus proches (US-046),
 * SPRINT-10. Le client d'API est simulé : ce qui est vérifié ici est le
 * comportement de l'interface, pas celui du serveur — déjà couvert par les tests
 * d'intégration `CartographieTourneeIT`.
 */
vi.mock('../api/client', () => ({
  sites: {
    lister: vi.fn(),
    creer: vi.fn(),
    mettreAJour: vi.fn(),
    supprimer: vi.fn(),
  },
  fermes: { lister: vi.fn() },
  voisinsSite: vi.fn(),
  ErreurApi: class ErreurApi extends Error {},
}));

const { fermes, sites, voisinsSite } = await import('../api/client');

const site = (id: number, nom: string, latitude: number, longitude: number): Site => ({
  id,
  nom,
  fermeId: 1,
  fermeNom: 'Ferme du Causse',
  latitude,
  longitude,
  altitude: null,
  dateMiseEnOeuvre: '2026-04-01',
  dateDemenagement: null,
  dateCloture: null,
  creeLe: '2026-04-01T08:00:00Z',
  majLe: '2026-04-01T08:00:00Z',
});

const RUCHER = site(1, 'Rucher du Lot', 44.447, 1.441);
const VOISIN = site(2, 'Rucher du Causse', 44.45, 1.45);

const monter = () =>
  render(
    <LangueProvider>
      <DialoguesProvider>
        <SitesVue />
      </DialoguesProvider>
    </LangueProvider>,
  );

describe('vue Sites', () => {
  beforeEach(() => {
    vi.mocked(sites.lister).mockResolvedValue([RUCHER]);
    vi.mocked(fermes.lister).mockResolvedValue([]);
    vi.mocked(voisinsSite).mockResolvedValue([]);
  });

  it('affiche les sites avec leurs coordonnées à quatre décimales', async () => {
    monter();

    expect(await screen.findByText('Rucher du Lot')).toBeInTheDocument();
    expect(screen.getByText('44.4470')).toBeInTheDocument();
    expect(screen.getByText('1.4410')).toBeInTheDocument();
  });

  it('signale une liste vide plutôt qu’un tableau sans ligne', async () => {
    vi.mocked(sites.lister).mockResolvedValue([]);

    monter();

    expect(await screen.findByText('Aucun élément pour le moment.')).toBeInTheDocument();
  });

  it('affiche un message d’erreur exploitable quand l’API tombe', async () => {
    vi.mocked(sites.lister).mockRejectedValue(new Error('réseau'));

    monter();

    expect(await screen.findByRole('alert')).toHaveTextContent('Service indisponible');
  });

  it('demande les trois plus proches voisins et affiche leur distance en km (US-046)', async () => {
    vi.mocked(voisinsSite).mockResolvedValue([{ site: VOISIN, distanceMetres: 1573.4 }]);
    monter();
    await screen.findByText('Rucher du Lot');

    await userEvent.click(screen.getByRole('button', { name: 'Voir les voisins' }));

    expect(voisinsSite).toHaveBeenCalledWith(1, 3);
    expect(await screen.findByText('Rucher du Causse')).toBeInTheDocument();
    // 1573,4 m rendus par le formatage français (US-053) : virgule décimale.
    expect(screen.getByText(/1,57 km/)).toBeInTheDocument();
  });

  it('annonce l’absence de voisin plutôt qu’une liste vide silencieuse', async () => {
    monter();
    await screen.findByText('Rucher du Lot');

    await userEvent.click(screen.getByRole('button', { name: 'Voir les voisins' }));

    expect(await screen.findByText('Aucun autre site géolocalisé.')).toBeInTheDocument();
  });

  it('ouvre le formulaire de création avec des champs vides', async () => {
    monter();
    await screen.findByText('Rucher du Lot');

    await userEvent.click(screen.getByRole('button', { name: '+ Nouveau' }));

    await waitFor(() => expect(screen.getByLabelText(/Nom/)).toHaveValue(''));
    expect(screen.getByLabelText(/Latitude/)).toHaveValue(null);
  });

  it('pré-remplit le formulaire avec le site à modifier', async () => {
    monter();
    await screen.findByText('Rucher du Lot');

    await userEvent.click(screen.getByRole('button', { name: 'Modifier' }));

    expect(await screen.findByLabelText(/Nom/)).toHaveValue('Rucher du Lot');
    expect(screen.getByLabelText(/Latitude/)).toHaveValue(44.447);
  });
});
