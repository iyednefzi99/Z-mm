import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { LangueProvider } from '../i18n/langue';
import { ThemeProvider } from '../theme/theme';
import { DialoguesProvider } from '../ui/dialogues';
import { ToastsProvider } from '../ui/toasts';
import { PlanningsVue } from './PlanningsVue';
import type { Tournee } from '../api/types';

/**
 * Réordonnancement de la tournée proposée (SPRINT-22).
 *
 * <p>Trois propriétés, et ce sont celles qui se perdraient en silence :
 *
 * <ol>
 *   <li>tant que l'ordre du serveur n'a pas bougé, l'écran montre <strong>ses</strong>
 *       chiffres — pas une estimation locale qui divergerait de PostGIS ;
 *   <li>dès qu'on déplace une étape, l'écran le dit : ordre non enregistré,
 *       distances estimées. Une simulation muette se prendrait pour un plan ;
 *   <li>on peut revenir à la proposition, sinon la simulation serait un
 *       aller simple et l'apiculteur perdrait l'ordre calculé.
 * </ol>
 */
vi.mock('../api/client', () => ({
  plannings: { lister: vi.fn(), creer: vi.fn(), modifier: vi.fn(), supprimer: vi.fn() },
  agents: { lister: vi.fn() },
  ruches: { lister: vi.fn() },
  approuverPlanning: vi.fn(),
  refuserPlanning: vi.fn(),
  creerAbonnement: vi.fn(),
  listerAbonnements: vi.fn(),
  revoquerAbonnement: vi.fn(),
  telechargerAgendaIcs: vi.fn(),
  tourneeAgent: vi.fn(),
}));

const { agents, plannings, ruches, listerAbonnements, tourneeAgent } =
  await import('../api/client');

const TOURNEE: Tournee = {
  agentId: 7,
  agentNom: 'Amel',
  date: '2026-09-02',
  nombreSites: 3,
  nombreVisites: 3,
  etapes: [
    {
      ordre: 1,
      siteId: 1,
      siteNom: 'Bizerte',
      latitude: 37.2744,
      longitude: 9.8739,
      planningIds: [1],
      nombreVisites: 1,
      distanceDepuisPrecedenteMetres: 0,
    },
    {
      ordre: 2,
      siteId: 2,
      siteNom: 'Tunis',
      latitude: 36.8065,
      longitude: 10.1815,
      planningIds: [2],
      nombreVisites: 1,
      distanceDepuisPrecedenteMetres: 58756,
    },
    {
      ordre: 3,
      siteId: 3,
      siteNom: 'Sfax',
      latitude: 34.7406,
      longitude: 10.7603,
      planningIds: [3],
      nombreVisites: 1,
      distanceDepuisPrecedenteMetres: 235576,
    },
  ],
  // Un total volontairement « rond » et faux : s'il s'affiche, c'est qu'il vient
  // bien du serveur et non d'un recalcul local.
  distanceTotaleMetres: 300000,
};

const monter = () =>
  render(
    <LangueProvider>
      <ThemeProvider>
        <ToastsProvider>
          <DialoguesProvider>
            <PlanningsVue />
          </DialoguesProvider>
        </ToastsProvider>
      </ThemeProvider>
    </LangueProvider>,
  );

async function calculer(): Promise<void> {
  monter();
  await userEvent.selectOptions(await screen.findByLabelText('Agent'), '7');
  await userEvent.type(screen.getByLabelText('Date'), '2026-09-02');
  await userEvent.click(screen.getByRole('button', { name: 'Proposer un ordre' }));
  // Le budget par défaut d'un `findBy` est d'une seconde. Ici l'attente couvre un
  // `import()` : `ListeReordonnable` est un morceau à part, qui tire Motion. Sous
  // une campagne complète à cache froid, la seconde ne suffit pas — et le test
  // echouait alors une fois sur quelques-unes, sans rien signaler de réel.
  await screen.findByRole('list', { name: 'Ordre de tournée' }, { timeout: 5000 });
}

const ordre = (): string[] =>
  screen
    .getAllByRole('listitem')
    .map((li) => /^[⠿\s]*([^—]+)/.exec(li.textContent ?? '')?.[1].trim() ?? '');

beforeEach(() => {
  vi.mocked(plannings.lister).mockResolvedValue([]);
  vi.mocked(agents.lister).mockResolvedValue([
    { id: 7, nom: 'Amel', email: 'amel@example.test', role: 'apiculteur' },
  ] as never);
  vi.mocked(ruches.lister).mockResolvedValue([]);
  vi.mocked(listerAbonnements).mockResolvedValue([]);
  vi.mocked(tourneeAgent).mockResolvedValue(TOURNEE);
});

describe('tournée réordonnable', () => {
  it('affiche la proposition du serveur, avec les distances du serveur', async () => {
    await calculer();

    expect(ordre()).toEqual(['Bizerte', 'Tunis', 'Sfax']);
    expect(screen.getByText(/Distance totale : 300,00 km/)).toBeInTheDocument();
    expect(screen.queryByText(/Ordre modifié sur cet écran/)).not.toBeInTheDocument();
  });

  it("annonce la simulation dès qu'une étape bouge, et recalcule le total", async () => {
    await calculer();

    await userEvent.click(screen.getByRole('button', { name: 'Descendre d’un rang : Bizerte' }));

    expect(ordre()).toEqual(['Tunis', 'Bizerte', 'Sfax']);
    expect(screen.getByText(/Ordre modifié sur cet écran/)).toBeInTheDocument();
    // Le total du serveur a disparu au profit de l'estimation locale.
    expect(screen.queryByText(/300,00 km/)).not.toBeInTheDocument();
  });

  it('revient à la proposition', async () => {
    await calculer();
    await userEvent.click(screen.getByRole('button', { name: 'Descendre d’un rang : Bizerte' }));

    await userEvent.click(screen.getByRole('button', { name: 'Revenir à la proposition' }));

    await waitFor(() => expect(ordre()).toEqual(['Bizerte', 'Tunis', 'Sfax']));
    expect(screen.getByText(/Distance totale : 300,00 km/)).toBeInTheDocument();
  });
});
