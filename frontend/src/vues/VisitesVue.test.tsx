import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { LangueProvider } from '../i18n/langue';
import { DialoguesProvider } from '../ui/dialogues';
import { VisitesVue } from './VisitesVue';
import type { Planning, Visite } from '../api/types';

/**
 * Tests de la vue Visites (US-009), et surtout de la partie du rapport que le
 * formulaire ne savait pas remplir.
 *
 * <p>Six champs du corps de requête étaient écrits en dur à {@code null} :
 * `planningId`, `heureVisite`, `dureeMin`, `actionsPrevues`, `actionsEffectuees`
 * et `recommandations`. Le backend les acceptait, la migration les stockait, le
 * PDF les imprimait et les trois locales les traduisaient — mais aucun champ ne
 * les rendait, donc le rapport sortait toujours amputé et le lien
 * planning → visite (US-008) n'était jamais posé. Aucun `TODO` ne le signalait :
 * seul un test de vue pouvait l'attraper, et il n'y en avait pas.
 */
vi.mock('../api/client', () => ({
  visites: {
    lister: vi.fn(),
    creer: vi.fn(),
    mettreAJour: vi.fn(),
    supprimer: vi.fn(),
  },
  ruches: { lister: vi.fn() },
  agents: { lister: vi.fn() },
  plannings: { lister: vi.fn() },
  listerPhotos: vi.fn(),
  ajouterPhoto: vi.fn(),
  supprimerPhoto: vi.fn(),
  telechargerRapportVisite: vi.fn(),
  ErreurApi: class ErreurApi extends Error {},
}));

const { agents, plannings, ruches, visites } = await import('../api/client');

const PLANNING: Planning = {
  id: 7,
  rucheId: 1,
  rucheModele: 'Dadant 10',
  agentId: 3,
  agentNom: 'Awa Diop',
  superviseurId: null,
  superviseurNom: null,
  datePrevue: '2026-05-12',
  heurePrevue: null,
  dureeMin: null,
  raison: 'controle',
  statut: 'approuve',
  motifRefus: null,
  creeLe: '2026-05-01T08:00:00Z',
  majLe: '2026-05-01T08:00:00Z',
};

const VISITE: Visite = {
  id: 42,
  rucheId: 1,
  rucheModele: 'Dadant 10',
  agentId: 3,
  agentNom: 'Awa Diop',
  planningId: 7,
  dateVisite: '2026-05-12',
  heureVisite: '14:30:00',
  dureeMin: 45,
  raison: 'controle',
  constatations: 'Colonie vive.',
  actionsPrevues: 'Poser une hausse.',
  actionsEffectuees: 'Hausse posée.',
  recommandations: 'Repasser sous quinze jours.',
  effectifQualitatif: 'fort',
  etatSante: 'bon',
  productivite: 3,
  photos: [],
  creeLe: '2026-05-12T14:30:00Z',
  majLe: '2026-05-12T14:30:00Z',
};

const monter = () =>
  render(
    <LangueProvider>
      <DialoguesProvider>
        <VisitesVue />
      </DialoguesProvider>
    </LangueProvider>,
  );

describe('vue Visites', () => {
  beforeEach(() => {
    vi.mocked(visites.lister).mockResolvedValue([VISITE]);
    vi.mocked(ruches.lister).mockResolvedValue([]);
    vi.mocked(agents.lister).mockResolvedValue([]);
    vi.mocked(plannings.lister).mockResolvedValue([PLANNING]);
  });

  it('transmet les six champs du rapport, au lieu de les figer à null', async () => {
    vi.mocked(ruches.lister).mockResolvedValue([
      { id: 1, modele: 'Dadant 10' } as never,
    ]);
    vi.mocked(agents.lister).mockResolvedValue([{ id: 3, nom: 'Awa Diop' } as never]);
    monter();
    await screen.findByText('Dadant 10');

    await userEvent.click(screen.getAllByRole('button', { name: 'Modifier' })[0]);

    await userEvent.selectOptions(await screen.findByLabelText('Planning'), '7');
    await userEvent.clear(screen.getByLabelText('Durée (min)'));
    await userEvent.type(screen.getByLabelText('Durée (min)'), '30');
    await userEvent.clear(screen.getByLabelText('Actions prévues'));
    await userEvent.type(screen.getByLabelText('Actions prévues'), 'Nourrir');
    await userEvent.clear(screen.getByLabelText('Actions effectuées'));
    await userEvent.type(screen.getByLabelText('Actions effectuées'), 'Nourri');
    await userEvent.clear(screen.getByLabelText('Recommandations'));
    await userEvent.type(screen.getByLabelText('Recommandations'), 'Surveiller');
    await userEvent.click(screen.getByRole('button', { name: 'Enregistrer' }));

    expect(visites.mettreAJour).toHaveBeenCalledWith(
      42,
      expect.objectContaining({
        planningId: 7,
        heureVisite: '14:30',
        dureeMin: 30,
        actionsPrevues: 'Nourrir',
        actionsEffectuees: 'Nourri',
        recommandations: 'Surveiller',
      }),
    );
  });

  it('pré-remplit les champs du rapport à la modification', async () => {
    monter();
    await screen.findByText('Dadant 10');

    await userEvent.click(screen.getAllByRole('button', { name: 'Modifier' })[0]);

    // « 14:30:00 » côté contrat, « 14:30 » côté input type="time".
    expect(await screen.findByLabelText('Heure')).toHaveValue('14:30');
    expect(screen.getByLabelText('Durée (min)')).toHaveValue(45);
    expect(screen.getByLabelText('Actions prévues')).toHaveValue('Poser une hausse.');
    expect(screen.getByLabelText('Recommandations')).toHaveValue(
      'Repasser sous quinze jours.',
    );
  });

  it('ne propose que les plannings approuvés de la ruche visitée (US-008)', async () => {
    vi.mocked(plannings.lister).mockResolvedValue([
      PLANNING,
      { ...PLANNING, id: 8, statut: 'propose', datePrevue: '2026-06-01' },
      { ...PLANNING, id: 9, rucheId: 2, datePrevue: '2026-06-02' },
    ]);
    monter();
    await screen.findByText('Dadant 10');

    await userEvent.click(screen.getAllByRole('button', { name: 'Modifier' })[0]);

    const select = await screen.findByLabelText('Planning');
    // Le planning proposé et celui d'une autre ruche sont écartés ; restent
    // l'option vide et le seul planning approuvé de la ruche 1.
    const valeurs = [...select.querySelectorAll('option')].map((o) => o.value);
    expect(valeurs).toEqual(['', '7']);
  });
});
