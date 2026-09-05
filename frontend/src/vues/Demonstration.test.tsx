import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { definir, reinitialiserSession } from '../auth/session';
import { LangueProvider } from '../i18n/langue';
import { DialoguesProvider } from '../ui/dialogues';
import { ConfigVue } from './ConfigVue';
import type { Seuils } from '../api/types';

/**
 * Jeu de démonstration, côté interface (SPRINT-25, lot J).
 *
 * <p>Quatre comportements, et ils tiennent tous au même mot du §13 :
 * <strong>réversible</strong>.
 *
 * <ol>
 *   <li>le nombre d'objets s'affiche — c'est ce qui rend le bouton « Retirer »
 *       décidable plutôt qu'inquiétant ;
 *   <li>le retrait <strong>demande confirmation</strong> : c'est la seule
 *       commande du produit qui supprime des dizaines de lignes d'un coup ;
 *   <li>quand la fonction est éteinte sur le déploiement, l'écran le dit au lieu
 *       de proposer un bouton qui échouera ;
 *   <li>un rôle sans droit ne voit pas la section du tout.
 * </ol>
 */
vi.mock('../api/client', () => ({
  recupererPoints: vi.fn(() => Promise.resolve([])),
  gabarits: {
    lister: vi.fn(() => Promise.resolve([])),
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

beforeEach(() => {
  vi.mocked(client.recupererSeuils).mockResolvedValue(SEUILS);
  definir({ utilisateur: 'lea', roles: ['admin'], exploitation: 'demo' });
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

describe('jeu de démonstration', () => {
  it('propose le chargement quand rien n’est en place', async () => {
    vi.mocked(client.etatDemonstration).mockResolvedValue({
      disponible: true,
      charge: false,
      objets: 0,
    });
    vi.mocked(client.chargerDemonstration).mockResolvedValue({
      disponible: true,
      charge: true,
      objets: 10,
    });

    monter();
    await userEvent.click(await screen.findByRole('button', { name: 'Charger la démonstration' }));

    expect(client.chargerDemonstration).toHaveBeenCalled();
    // Le compte s'affiche : c'est lui qui rendra le retrait décidable.
    expect(await screen.findByText(/10 objets de démonstration/)).toBeInTheDocument();
  });

  it('demande confirmation avant de retirer, en disant combien d’objets partent', async () => {
    vi.mocked(client.etatDemonstration).mockResolvedValue({
      disponible: true,
      charge: true,
      objets: 10,
    });
    vi.mocked(client.purgerDemonstration).mockResolvedValue({
      disponible: true,
      charge: false,
      objets: 0,
    });

    monter();
    await userEvent.click(await screen.findByRole('button', { name: 'Retirer la démonstration' }));

    // La seule commande du produit qui supprime des dizaines de lignes d'un
    // coup : elle ne part pas sur un clic.
    expect(await screen.findByText(/Retirer le jeu de démonstration et les 10 objets/))
      .toBeInTheDocument();
    expect(client.purgerDemonstration).not.toHaveBeenCalled();

    await userEvent.click(screen.getByRole('button', { name: 'Confirmer' }));
    await waitFor(() => expect(client.purgerDemonstration).toHaveBeenCalled());
    expect(await screen.findByText('Aucun jeu de démonstration chargé.')).toBeInTheDocument();
  });

  it('dit que la fonction est éteinte plutôt que de proposer un bouton qui échoue', async () => {
    vi.mocked(client.etatDemonstration).mockResolvedValue({
      disponible: false,
      charge: false,
      objets: 0,
    });

    monter();

    expect(await screen.findByText(/désactivé sur ce déploiement/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Charger la démonstration' })).toBeNull();
  });

  it('ne montre rien du tout à un rôle qui n’y a pas droit', async () => {
    // Un 403 est la réponse NORMALE hors administration : la section disparaît,
    // plutôt que d'afficher une erreur pour une fonction qui ne concerne pas
    // l'appelant.
    vi.mocked(client.etatDemonstration).mockRejectedValue(new Error('403'));

    monter();
    await screen.findByText('12');

    expect(screen.queryByText('Jeu de démonstration')).toBeNull();
  });
});
