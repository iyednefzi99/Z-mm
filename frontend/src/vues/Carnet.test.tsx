import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { definir, reinitialiserSession } from '../auth/session';
import { LangueProvider } from '../i18n/langue';
import { DialoguesProvider } from '../ui/dialogues';
import type { Gabarit, PointReferentiel, Seuils } from '../api/types';
import { ConfigVue } from './ConfigVue';

/**
 * Le carnet paramétrable, côté écran (SPRINT-28, lot I).
 *
 * <p>Trois comportements décident si le paramétrage est inoffensif :
 *
 * <ol>
 *   <li>le référentiel est <strong>fermé</strong> : on coche des points
 *       existants, et l'écran n'offre aucun moyen d'en inventer un ;
 *   <li>la <strong>forme</strong> du carnet est réservée au pilotage — un
 *       apiculteur voit les gabarits, il ne les change pas ;
 *   <li>les points partent dans l'ordre où on les a choisis, et un gabarit se
 *       retire aussi facilement qu'il s'ajoute.
 * </ol>
 */
vi.mock('../api/client', () => ({
  recupererStatistiquesPoints: vi.fn(),
  chargerInfo: vi.fn(() => Promise.resolve({ reseauSortant: true })),
  recupererPoints: vi.fn(),
  gabarits: {
    lister: vi.fn(),
    creer: vi.fn(),
    mettreAJour: vi.fn(),
    supprimer: vi.fn(),
  },
  recupererSeuils: vi.fn(),
  etatDemonstration: vi.fn(),
  chargerDemonstration: vi.fn(),
  purgerDemonstration: vi.fn(),
  ErreurApi: class ErreurApi extends Error {},
}));

const client = await import('../api/client');

const SEUILS = {
  poidsRucheAlerteKg: 2,
  temperatureMinCelsius: 5,
  temperatureMaxCelsius: 38,
  humiditeMaxPourcent: 80,
  delaiAlerteJours: 3,
  arrondiDegresPublic: 2,
  prixMielKgEur: 12,
  coutVisiteEur: 8,
  languesActives: ['fr', 'en', 'ar'],
} as unknown as Seuils;

const POINTS: PointReferentiel[] = [
  {
    code: 'pop_forte',
    categorie: 'population',
    typeValeur: 'booleen',
    libelle: 'Population forte',
    ordre: 10,
  },
  {
    code: 'propolisation',
    categorie: 'batisse',
    typeValeur: 'echelle',
    libelle: 'Propolisation',
    ordre: 43,
  },
];

const GABARIT: Gabarit = {
  id: 4,
  nom: 'Printemps',
  description: null,
  noyauCouvain: true,
  noyauReine: true,
  noyauCadres: false,
  noyauTemperament: true,
  parDefaut: true,
  actif: true,
  points: ['pop_forte'],
  creeLe: '2026-09-04T08:00:00Z',
  majLe: '2026-09-04T08:00:00Z',
};

beforeEach(() => {
  vi.mocked(client.recupererSeuils).mockResolvedValue(SEUILS);
  vi.mocked(client.recupererPoints).mockResolvedValue(POINTS);
  vi.mocked(client.gabarits.lister).mockResolvedValue([GABARIT]);
  vi.mocked(client.etatDemonstration).mockRejectedValue(new Error('403'));
  definir({ utilisateur: 'lea', roles: ['responsable'], exploitation: 'demo' });
});

afterEach(() => {
  reinitialiserSession();
  vi.clearAllMocks();
});

const monter = () =>
  render(
    <LangueProvider>
      <DialoguesProvider>
        <ConfigVue />
      </DialoguesProvider>
    </LangueProvider>,
  );

describe('carnet paramétrable', () => {
  it('annonce le gabarit par défaut et le nombre de points retenus', async () => {
    monter();

    expect(await screen.findByText('Printemps')).toBeInTheDocument();
    expect(screen.getByText(/Proposé par défaut/)).toBeInTheDocument();
    expect(screen.getByText(/1 points retenus/)).toBeInTheDocument();
  });

  it('n’offre aucun moyen d’inventer un point', async () => {
    monter();
    await userEvent.click(await screen.findByRole('button', { name: 'Nouveau gabarit' }));

    // Le référentiel est FERMÉ : on active des cases existantes. Un champ libre
    // ici ferait inventer dix libellés pour la même observation, et plus rien
    // ne se compterait d'une exploitation à l'autre.
    expect(screen.getByLabelText('Population forte')).toBeInTheDocument();
    expect(screen.getByLabelText('Propolisation')).toBeInTheDocument();
    expect(screen.queryByLabelText(/Ajouter un point/)).not.toBeInTheDocument();
  });

  it('envoie les points cochés et l’état des sections du noyau', async () => {
    vi.mocked(client.gabarits.creer).mockResolvedValue(GABARIT);

    monter();
    await userEvent.click(await screen.findByRole('button', { name: 'Nouveau gabarit' }));
    await userEvent.type(screen.getByLabelText('Nom'), 'Hivernage');
    await userEvent.click(screen.getByLabelText('Propolisation'));
    // Éteindre une section la masque à la saisie ; la colonne reste en base.
    await userEvent.click(screen.getByLabelText('Comptage des cadres'));
    await userEvent.click(screen.getByRole('button', { name: 'Enregistrer' }));

    await waitFor(() =>
      expect(client.gabarits.creer).toHaveBeenCalledWith(
        expect.objectContaining({
          nom: 'Hivernage',
          points: ['propolisation'],
          noyauCadres: false,
          noyauCouvain: true,
        }),
      ),
    );
  });

  it('demande confirmation avant de supprimer un gabarit', async () => {
    monter();
    await userEvent.click(await screen.findByRole('button', { name: 'Supprimer' }));

    // Les visites gardent leurs relevés : le relevé pointe le RÉFÉRENTIEL, pas
    // le gabarit qui l'a proposé. La confirmation le dit, pour que personne ne
    // renonce à réorganiser son carnet de peur de perdre son historique.
    expect(
      await screen.findByText(/Les visites déjà saisies gardent leurs relevés/),
    ).toBeInTheDocument();
  });

  it('laisse un apiculteur lire le carnet sans le modifier', async () => {
    definir({ utilisateur: 'sam', roles: ['apiculteur'], exploitation: 'demo' });

    monter();

    // L'écran reste — il doit voir les cases qu'on lui demande de cocher — mais
    // les commandes d'écriture s'effacent : lui laisser le bouton lui promet un
    // formulaire qui finira en 403 après la saisie.
    expect(await screen.findByText('Printemps')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Nouveau gabarit' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Supprimer' })).not.toBeInTheDocument();
  });
});
