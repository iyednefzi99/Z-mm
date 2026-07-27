import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { LangueProvider } from '../i18n/langue';
import { ToastsProvider } from '../ui/toasts';
import { LotsVue } from './LotsVue';
import type { Lot } from '../api/types';

/**
 * Tests de la vue Lots (US-056), et de la suppression annulable.
 *
 * <p>Cette vue est la seule à ne pas passer par `useRessource` : elle tient sa
 * propre liste. Elle était donc restée sur « confirmer d'abord, supprimer
 * ensuite », alors que les neuf autres avaient hérité de l'annulation différée du
 * hook partagé. Retirer sa confirmation sans lui donner l'annulation aurait
 * supprimé toute protection sur un objet réglementaire — un lot recréé changerait
 * de référence et perdrait ses origines déclarées.
 */
vi.mock('../api/client', () => ({
  lots: {
    lister: vi.fn(),
    creer: vi.fn(),
    supprimer: vi.fn(),
  },
  recoltes: { lister: vi.fn() },
  chargerMentionOrigine: vi.fn(),
  ErreurApi: class ErreurApi extends Error {},
}));

const { lots, recoltes } = await import('../api/client');

const LOT: Lot = {
  id: 3,
  reference: 'LOT-2026-014',
  dateConditionnement: '2026-07-01',
  quantiteKg: 120,
  typeMiel: 'Toutes fleurs',
  note: null,
  composition: [],
  creeLe: '2026-07-01T09:00:00Z',
  majLe: '2026-07-01T09:00:00Z',
};

const monter = () =>
  render(
    <LangueProvider>
      <ToastsProvider>
        <LotsVue />
      </ToastsProvider>
    </LangueProvider>,
  );

describe('vue Lots — suppression annulable', () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    vi.mocked(lots.lister).mockResolvedValue([LOT]);
    vi.mocked(recoltes.lister).mockResolvedValue([]);
    vi.mocked(lots.supprimer).mockResolvedValue(undefined);
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.clearAllMocks();
  });

  it('supprime sans confirmation préalable, et diffère l’appel réseau', async () => {
    const utilisateur = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    monter();
    expect(await screen.findByText('LOT-2026-014')).toBeInTheDocument();

    await utilisateur.click(screen.getByRole('button', { name: 'Supprimer' }));

    // Aucune boîte de confirmation ne s'interpose : la ligne part tout de suite.
    expect(screen.queryByRole('button', { name: 'Confirmer' })).not.toBeInTheDocument();
    expect(screen.queryByText('LOT-2026-014')).not.toBeInTheDocument();
    // Mais rien n'est encore envoyé : c'est ce qui distingue annuler de recréer.
    expect(lots.supprimer).not.toHaveBeenCalled();

    await act(async () => {
      vi.runAllTimers();
    });
    expect(lots.supprimer).toHaveBeenCalledWith(3);
  });

  it('n’envoie rien du tout si l’utilisateur annule', async () => {
    const utilisateur = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    monter();
    await screen.findByText('LOT-2026-014');

    await utilisateur.click(screen.getByRole('button', { name: 'Supprimer' }));
    await utilisateur.click(await screen.findByRole('button', { name: 'Annuler' }));

    await act(async () => {
      vi.runAllTimers();
    });
    expect(lots.supprimer).not.toHaveBeenCalled();
    // La liste est relue depuis le serveur, source de vérité.
    expect(lots.lister).toHaveBeenCalledTimes(2);
  });
});
