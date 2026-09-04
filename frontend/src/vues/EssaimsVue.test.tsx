import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { LangueProvider } from '../i18n/langue';
import { EssaimsVue } from './EssaimsVue';
import type { CaptureEssaim, Division } from '../api/types';

/**
 * Tests des entrées et divisions de colonies (SPRINT-21, §1 de
 * `docs/ECART-CONCURRENTS.md`).
 *
 * <p>Trois propriétés y sont vérifiées, et ce sont celles qui échouent en
 * silence :
 *
 * <ol>
 *   <li>une capture non logée reste distinguable d'une capture logée — c'est
 *       toute la valeur du registre : savoir ce qui attend encore une ruche ;
 *   <li>la filiation se lit dans les deux sens sur la ruche choisie, et la
 *       fille absente s'affiche comme telle plutôt qu'en case vide ;
 *   <li>une division part sans fille quand aucune n'est choisie — l'exiger
 *       ferait renoncer à saisir la division, donc perdre la filiation.
 * </ol>
 */
vi.mock('../api/client', () => ({
  ruches: { lister: vi.fn() },
  agents: { lister: vi.fn() },
  listerCaptures: vi.fn(),
  listerCapturesEnAttente: vi.fn(),
  creerCapture: vi.fn(),
  logerCapture: vi.fn(),
  supprimerCapture: vi.fn(),
  filiationRuche: vi.fn(),
  creerDivision: vi.fn(),
  supprimerDivision: vi.fn(),
  ErreurApi: class ErreurApi extends Error {
    constructor(
      readonly statut: number,
      readonly detail: string,
    ) {
      super(detail);
    }
  },
}));

const {
  agents,
  creerDivision,
  filiationRuche,
  listerCaptures,
  listerCapturesEnAttente,
  logerCapture,
  ruches,
} = await import('../api/client');

const capture = (id: number, logee: boolean): CaptureEssaim => ({
  id,
  agentId: 1,
  agentNom: 'Amel',
  rucheId: logee ? 12 : null,
  siteId: null,
  siteNom: null,
  dateCapture: '2026-05-04',
  origine: 'essaim_naturel',
  lieu: 'Haie du voisin',
  poidsKg: 1.8,
  hauteurM: 3,
  note: null,
  logee,
  creeLe: '2026-05-04T08:00:00Z',
});

const division = (id: number, filleId: number | null): Division => ({
  id,
  rucheMereId: 42,
  rucheMereModele: 'Dadant 10 cadres',
  rucheFilleId: filleId,
  rucheFilleModele: filleId == null ? null : 'Nucléus 6 cadres',
  agentId: 1,
  agentNom: 'Amel',
  visiteId: null,
  dateDivision: '2026-05-12',
  methode: 'essaim_artificiel',
  cadresCouvain: 3,
  cadresProvisions: 2,
  origineReine: 'cellule_royale',
  note: null,
  creeLe: '2026-05-12T09:00:00Z',
});

const monter = () =>
  render(
    <LangueProvider>
      <EssaimsVue />
    </LangueProvider>,
  );

beforeEach(() => {
  vi.mocked(ruches.lister).mockResolvedValue([]);
  vi.mocked(agents.lister).mockResolvedValue([]);
  vi.mocked(listerCaptures).mockResolvedValue([]);
  vi.mocked(listerCapturesEnAttente).mockResolvedValue([]);
  vi.mocked(filiationRuche).mockResolvedValue([]);
});

describe('captures d’essaim', () => {
  it('distingue une capture logée d’une capture encore en attente', async () => {
    vi.mocked(listerCaptures).mockResolvedValue([capture(1, false), capture(2, true)]);
    monter();

    expect(await screen.findByText('En ruchette d’attente')).toBeInTheDocument();
    expect(screen.getByText('Logée')).toBeInTheDocument();
  });

  it('n’interroge que les captures en attente quand le filtre le demande', async () => {
    vi.mocked(listerCaptures).mockResolvedValue([capture(1, false)]);
    monter();
    await screen.findByText('Haie du voisin');

    await userEvent.selectOptions(
      screen.getByLabelText('Afficher'),
      'enAttente',
    );

    await waitFor(() => expect(listerCapturesEnAttente).toHaveBeenCalled());
  });

  it('propose de loger une capture, jamais d’en modifier une déjà logée', async () => {
    vi.mocked(ruches.lister).mockResolvedValue([
      {
        id: 12,
        modele: 'Dadant 10 cadres',
        siteId: 1,
        siteNom: 'Rucher',
        fermeId: 1,
        fermeNom: 'Ferme',
        agentResponsableId: null,
        agentResponsableNom: null,
        etat: 'active',
        nbHausses: 0,
        compartiments: [],
        typeRuche: null,
        couleur: null,
        origine: null,
        causeCloture: null,
        priorite: 'normale',
        creeLe: '2026-01-01T00:00:00Z',
        majLe: '2026-01-01T00:00:00Z',
      },
    ]);
    vi.mocked(listerCaptures).mockResolvedValue([capture(1, false)]);
    vi.mocked(logerCapture).mockResolvedValue(capture(1, true));
    monter();
    await screen.findByText('Haie du voisin');

    await userEvent.selectOptions(screen.getByLabelText('Loger dans une ruche'), '12');

    await waitFor(() => expect(logerCapture).toHaveBeenCalledWith(1, 12));
  });
});

describe('divisions', () => {
  const choisirMere = async () => {
    vi.mocked(agents.lister).mockResolvedValue([
      {
        id: 1,
        nom: 'Amel',
        role: 'apiculteur',
        fermeId: null,
        fermeNom: null,
        email: null,
        notificationsEmail: true,
        creeLe: '2026-01-01T00:00:00Z',
        majLe: '2026-01-01T00:00:00Z',
      },
    ]);
    vi.mocked(ruches.lister).mockResolvedValue([
      {
        id: 42,
        modele: 'Dadant 10 cadres',
        siteId: 1,
        siteNom: 'Rucher',
        fermeId: 1,
        fermeNom: 'Ferme',
        agentResponsableId: null,
        agentResponsableNom: null,
        etat: 'active',
        nbHausses: 0,
        compartiments: [],
        typeRuche: null,
        couleur: null,
        origine: null,
        causeCloture: null,
        priorite: 'normale',
        creeLe: '2026-01-01T00:00:00Z',
        majLe: '2026-01-01T00:00:00Z',
      },
    ]);
    monter();
    await userEvent.click(screen.getByRole('tab', { name: 'Divisions' }));
    await userEvent.selectOptions(await screen.findByLabelText('Ruche mère'), '42');
  };

  it('affiche la filiation, fille absente comprise', async () => {
    vi.mocked(filiationRuche).mockResolvedValue([division(1, 43), division(2, null)]);
    await choisirMere();

    expect(await screen.findByText('43 — Nucléus 6 cadres')).toBeInTheDocument();
    // Une case vide laisserait croire à une donnée perdue ; c'est une division
    // vers un nucléus pas encore enregistré comme ruche.
    expect(screen.getByText('Fille non enregistrée')).toBeInTheDocument();
  });

  it('enregistre une division sans fille quand aucune n’est choisie', async () => {
    vi.mocked(creerDivision).mockResolvedValue(division(3, null));
    await choisirMere();

    await userEvent.type(screen.getByLabelText('Date de division'), '2026-05-12');
    await userEvent.selectOptions(screen.getByLabelText('Agent responsable'), '1');
    await userEvent.click(screen.getByRole('button', { name: 'Enregistrer' }));

    await waitFor(() => expect(creerDivision).toHaveBeenCalled());
    expect(vi.mocked(creerDivision).mock.calls[0][0]).toMatchObject({
      rucheMereId: 42,
      rucheFilleId: null,
    });
  });
});
