import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { LangueProvider } from '../i18n/langue';
import type { Reine } from '../api/types';
import { ReinesVue } from './ReinesVue';

/**
 * Journal des reines (US-032) et son panneau photos (SPRINT-34, lot L).
 *
 * <p>`Reine` ici est un ÉVÉNEMENT du journal — l'équivalent frontend de
 * `SuiviReine` côté serveur — et non l'individu élevé (`ReineElevage`, testé
 * dans `Elevage.test.tsx`). C'est donc l'id de CET événement, pas celui de la
 * ruche, que la cible `REINE` de `/api/photos` attend : un test qui les
 * confondrait ne le remarquerait qu'au premier essai réel contre le serveur.
 */
vi.mock('../api/client', () => ({
  ruches: { lister: vi.fn() },
  listerReines: vi.fn(),
  enregistrerReine: vi.fn(),
  supprimerReine: vi.fn(),
  listerPhotosDe: vi.fn(),
  reinesElevage: {
    lister: vi.fn(),
    creer: vi.fn(),
    supprimer: vi.fn(),
    mettreAJour: vi.fn(),
    obtenir: vi.fn(),
  },
  series: { lister: vi.fn(), creer: vi.fn(), supprimer: vi.fn(), mettreAJour: vi.fn() },
  chargerGenealogie: vi.fn(),
  chargerIndexGenetique: vi.fn(),
  chargerConformite: vi.fn(),
  ouvrirDocumentElevage: vi.fn(),
  ErreurApi: class ErreurApi extends Error {},
}));

const client = await import('../api/client');

const EVENEMENT: Reine = {
  id: 42,
  rucheId: 3,
  rucheModele: 'Dadant 10',
  dateEvenement: '2026-06-01',
  statut: 'introduite',
  couleurMarquage: null,
  anneeNaissance: null,
  race: null,
  note: null,
  creeLe: '2026-06-01T08:00:00Z',
  majLe: '2026-06-01T08:00:00Z',
};

beforeEach(() => {
  vi.mocked(client.ruches.lister).mockResolvedValue([
    { id: 3, modele: 'Dadant 10' } as never,
  ]);
  vi.mocked(client.listerReines).mockResolvedValue([EVENEMENT]);
  vi.mocked(client.listerPhotosDe).mockResolvedValue([]);
  vi.mocked(client.reinesElevage.lister).mockResolvedValue([]);
  vi.mocked(client.series.lister).mockResolvedValue([]);
});

afterEach(() => {
  vi.clearAllMocks();
});

const monter = () =>
  render(
    <LangueProvider>
      <ReinesVue />
    </LangueProvider>,
  );

describe('journal des reines', () => {
  it('ouvre le panneau photos d’un événement avec la cible REINE et son id à lui', async () => {
    monter();

    await userEvent.selectOptions(
      await screen.findByLabelText('Choisir une ruche'),
      'Dadant 10',
    );
    // Le statut « Introduite » figure aussi comme option du sélecteur d'ajout :
    // seule la cellule du tableau confirme que l'historique a bien chargé.
    await screen.findByRole('cell', { name: 'Introduite' });

    await userEvent.click(screen.getByRole('button', { name: 'Photos' }));

    expect(client.listerPhotosDe).toHaveBeenCalledWith('REINE', EVENEMENT.id);
  });
});
