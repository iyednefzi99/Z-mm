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
  chargerMeteo: vi.fn(),
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
  observation: null,
  meteo: null,
  pathologies: [],
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
    // Les listes ne sont pas decoratives : sans l'option correspondante, le
    // `select` requis reste vide pour le navigateur, et la soumission est
    // bloquee avant meme d'atteindre le client d'API.
    vi.mocked(ruches.lister).mockResolvedValue([
      { id: 1, modele: 'Dadant 10', siteId: 2 } as never,
    ]);
    vi.mocked(agents.lister).mockResolvedValue([{ id: 3, nom: 'Awa Diop' } as never]);
    vi.mocked(plannings.lister).mockResolvedValue([PLANNING]);
  });

  it('transmet les six champs du rapport, au lieu de les figer à null', async () => {
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
      // Depuis le SPRINT-24, une modification porte la version lue : c'est ce
      // qui empeche le rejeu d'une saisie hors ligne d'ecraser un collegue.
      { 'X-Zumm-Version': expect.any(String) },
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

  it('distingue « non observé » de « non » (SPRINT-20)', async () => {
    monter();
    await screen.findByText('Dadant 10');
    await userEvent.click(screen.getAllByRole('button', { name: 'Modifier' })[0]);

    // Grille laissée telle quelle : rien n'a été observé, donc rien n'est
    // envoyé. Un objet plein de `false` ferait croire à une colonie sans œufs,
    // sans larves et sans reine — et fausserait toute statistique construite
    // dessus.
    await userEvent.click(screen.getByRole('button', { name: 'Enregistrer' }));

    expect(visites.mettreAJour).toHaveBeenCalledWith(
      42,
      expect.objectContaining({ observation: null, meteo: null, pathologies: [] }),
      { 'X-Zumm-Version': expect.any(String) },
    );
  });

  it('transmet la grille d’inspection et les pathologies constatées (SPRINT-20)', async () => {
    monter();
    await screen.findByText('Dadant 10');
    await userEvent.click(screen.getAllByRole('button', { name: 'Modifier' })[0]);

    await userEvent.selectOptions(await screen.findByLabelText('Œufs'), 'oui');
    await userEvent.selectOptions(screen.getByLabelText('Couvain operculé'), 'non');
    await userEvent.selectOptions(screen.getByLabelText('Motif de ponte'), 'compact');
    await userEvent.type(screen.getByLabelText('Cadres de couvain'), '5');
    await userEvent.selectOptions(screen.getByLabelText('Pathologie'), 'varroose');
    await userEvent.selectOptions(screen.getByLabelText('Gravité'), 'moderee');
    await userEvent.click(screen.getByRole('button', { name: '+ Ajouter' }));
    await userEvent.click(screen.getByRole('button', { name: 'Enregistrer' }));

    expect(visites.mettreAJour).toHaveBeenCalledWith(
      42,
      expect.objectContaining({
        observation: expect.objectContaining({
          couvainOeufs: true,
          // « Non » est une observation, pas une absence d'observation.
          couvainOpercule: false,
          couvainLarves: null,
          motifPonte: 'compact',
          cadresCouvain: 5,
        }),
        pathologies: [{ pathologie: 'varroose', gravite: 'moderee', note: null }],
      }),
      { 'X-Zumm-Version': expect.any(String) },
    );
  });

  it('n’envoie pas de cause de cellules royales sans cellule (SPRINT-20)', async () => {
    monter();
    await screen.findByText('Dadant 10');
    await userEvent.click(screen.getAllByRole('button', { name: 'Modifier' })[0]);

    // Le champ n'apparaît qu'à partir d'une cellule : « supersédure, zéro
    // cellule » est une contradiction que la base refuse, et qu'il vaut mieux
    // ne pas laisser saisir que faire rejeter après coup.
    expect(screen.queryByLabelText('Cause des cellules')).not.toBeInTheDocument();

    await userEvent.type(await screen.findByLabelText('Cellules royales'), '3');

    expect(screen.getByLabelText('Cause des cellules')).toBeInTheDocument();
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
