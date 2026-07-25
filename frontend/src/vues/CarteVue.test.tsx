import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { LangueProvider } from '../i18n/langue';
import { DialoguesProvider } from '../ui/dialogues';
import { CarteVue } from './CarteVue';
import type { GrappeSites, Ruche, Site } from '../api/types';

/**
 * Tests de la carte (US-030) et de sa vue « grappes » (US-045), SPRINT-10.
 *
 * Le point vérifié est que le regroupement n'est PAS recalculé dans le
 * navigateur : la vue appelle le serveur et se contente de placer ce qu'il rend.
 */
vi.mock('../api/client', () => ({
  sites: { lister: vi.fn() },
  ruches: { lister: vi.fn() },
  grappesSites: vi.fn(),
}));

const { grappesSites, ruches, sites } = await import('../api/client');

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

const CAHORS = site(1, 'Cahors nord', 44.467, 1.441);
const TOULOUSE = site(2, 'Toulouse est', 43.604, 1.444);

const GRAPPE: GrappeSites = {
  numero: 1,
  latitudeCentre: 44.45,
  longitudeCentre: 1.443,
  nombreSites: 2,
  nombreRuches: 7,
  sites: [CAHORS, TOULOUSE],
};

const monter = () =>
  render(
    <LangueProvider>
      <DialoguesProvider>
        <CarteVue />
      </DialoguesProvider>
    </LangueProvider>,
  );

describe('vue Carte', () => {
  beforeEach(() => {
    vi.mocked(sites.lister).mockResolvedValue([CAHORS, TOULOUSE]);
    vi.mocked(ruches.lister).mockResolvedValue([] as unknown as Ruche[]);
    vi.mocked(grappesSites).mockResolvedValue([GRAPPE]);
  });

  it('annonce l’absence de site géolocalisé plutôt qu’une carte vide', async () => {
    vi.mocked(sites.lister).mockResolvedValue([]);

    monter();

    expect(await screen.findByText('Aucun site géolocalisé.')).toBeInTheDocument();
  });

  it('rend les rayons de butinage par défaut, sans appeler le regroupement', async () => {
    monter();

    expect(await screen.findByText(/Cahors nord/)).toBeInTheDocument();
    expect(screen.getByRole('img', { name: 'Rayons de butinage' })).toBeInTheDocument();
    expect(grappesSites).not.toHaveBeenCalled();
  });

  it('demande le regroupement au serveur en basculant sur la vue grappes (US-045)', async () => {
    monter();
    await screen.findByText(/Cahors nord/);

    await userEvent.click(screen.getByRole('button', { name: 'Grappes' }));

    expect(grappesSites).toHaveBeenCalledWith(15000);
    expect(await screen.findByText(/Grappe 1 — 2 site\(s\) · 7 ruche\(s\)/)).toBeInTheDocument();
  });

  it('recalcule côté serveur quand on change le rayon de regroupement', async () => {
    monter();
    await screen.findByText(/Cahors nord/);
    await userEvent.click(screen.getByRole('button', { name: 'Grappes' }));

    await userEvent.selectOptions(screen.getByLabelText(/Rayon de regroupement/), '30');

    expect(grappesSites).toHaveBeenLastCalledWith(30000);
  });

  it('marque la vue active pour les lecteurs d’écran', async () => {
    monter();
    await screen.findByText(/Cahors nord/);

    expect(screen.getByRole('button', { name: 'Sites' })).toHaveAttribute('aria-pressed', 'true');

    await userEvent.click(screen.getByRole('button', { name: 'Grappes' }));

    expect(screen.getByRole('button', { name: 'Grappes' })).toHaveAttribute('aria-pressed', 'true');
  });

  it('revient aux sites sans conserver l’affichage des grappes', async () => {
    monter();
    await screen.findByText(/Cahors nord/);
    await userEvent.click(screen.getByRole('button', { name: 'Grappes' }));
    await screen.findByText(/Grappe 1/);

    await userEvent.click(screen.getByRole('button', { name: 'Sites' }));

    expect(screen.queryByText(/Grappe 1/)).not.toBeInTheDocument();
  });
});
