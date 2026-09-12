import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { definir, reinitialiserSession } from '../auth/session';
import { LangueProvider } from '../i18n/langue';
import { DialoguesProvider } from '../ui/dialogues';
import { RecoltesVue } from './RecoltesVue';
import { TachesVue } from './TachesVue';
import type { Recolte, Tache } from '../api/types';

/**
 * Tests du lot A côté interface (SPRINT-22).
 *
 * <p>Trois comportements y sont vérifiés, et ce sont les trois qui décident si
 * le travail serveur sert à quelque chose :
 *
 * <ol>
 *   <li>une tâche <strong>engendrée se justifie</strong> — sans sa règle
 *       affichée, elle apparaît sans qu'on sache pourquoi, et on l'ignore ;
 *   <li>« aucune proposition » se <strong>dit</strong> : un bouton qui ne
 *       répond rien passe pour cassé ;
 *   <li>un refus de carence (409) n'est pas affiché comme une erreur de saisie
 *       mais comme une <strong>décision à prendre</strong>, avec le champ qui
 *       permet de passer outre en s'expliquant.
 * </ol>
 */
vi.mock('../api/client', () => ({
  calculerValorisation: vi.fn(),
  convertirUnite: vi.fn(),
  taches: { lister: vi.fn(), creer: vi.fn(), mettreAJour: vi.fn(), supprimer: vi.fn() },
  recoltes: { lister: vi.fn(), creer: vi.fn(), mettreAJour: vi.fn(), supprimer: vi.fn() },
  ruches: { lister: vi.fn() },
  agents: { lister: vi.fn() },
  listerRappels: vi.fn(),
  executerRegles: vi.fn(),
  tracerLot: vi.fn(),
  // Même forme que la vraie : `statut` et `detail` sont lus par la vue pour
  // distinguer un refus métier d'une panne.
  ErreurApi: class ErreurApi extends Error {
    constructor(
      readonly statut: number,
      readonly detail: string,
    ) {
      super(detail);
    }
  },
}));

const { agents, executerRegles, listerRappels, recoltes, ruches, taches } =
  await import('../api/client');
const { ErreurApi } = await import('../api/client');

const tache = (over: Partial<Tache> = {}): Tache => ({
  id: 1,
  libelle: 'Commander des cadres gaufrés',
  rucheId: null,
  rucheModele: null,
  agentId: null,
  agentNom: null,
  echeance: '2026-09-10',
  faite: false,
  priorite: 'normale',
  categorie: null,
  origine: 'manuelle',
  regleCode: null,
  creeLe: '2026-09-01T08:00:00Z',
  majLe: '2026-09-01T08:00:00Z',
  ...over,
});

const recolte = (): Recolte => ({
  id: 1,
  rucheId: 5,
  rucheModele: 'Dadant',
  dateRecolte: '2026-07-15',
  quantiteKg: 18.5,
  typeMiel: 'Acacia',
  typeProduit: 'miel',
  unite: 'kg',
  humiditePct: null,
  lot: 'ZUMM-5-20260715-01',
  note: null,
  carenceForcee: false,
  motifForcage: null,
  qrPayload: 'zumm:tracabilite:ZUMM-5-20260715-01',
  creeLe: '2026-07-15T08:00:00Z',
  majLe: '2026-07-15T08:00:00Z',
});

beforeEach(() => {
  vi.mocked(ruches.lister).mockResolvedValue([]);
  vi.mocked(agents.lister).mockResolvedValue([]);
  vi.mocked(listerRappels).mockResolvedValue([]);
  vi.mocked(taches.lister).mockResolvedValue([]);
  vi.mocked(recoltes.lister).mockResolvedValue([]);
  definir({ utilisateur: 'lea', roles: ['responsable'], exploitation: 'demo' });
});

afterEach(() => reinitialiserSession());

const monterTaches = () =>
  render(
    <LangueProvider>
      <DialoguesProvider>
        <TachesVue />
      </DialoguesProvider>
    </LangueProvider>,
  );

describe('tâches — priorité et origine (SPRINT-22)', () => {
  it('affiche la priorité, et justifie une tâche engendrée par sa règle', async () => {
    vi.mocked(taches.lister).mockResolvedValue([
      tache({
        libelle: 'Fin de carence le 2026-09-03 — Apivar (ruche 42)',
        priorite: 'haute',
        categorie: 'traitement',
        origine: 'regle',
        regleCode: 'carence-retrait',
      }),
    ]);

    monterTaches();

    expect(await screen.findByText('Haute')).toBeInTheDocument();
    expect(screen.getByText('Traitement')).toBeInTheDocument();
    // Une tâche qu'on ne s'explique pas est une tâche qu'on ignore.
    expect(screen.getByText(/Proposée automatiquement/)).toBeInTheDocument();
    expect(screen.getByText(/carence-retrait/)).toBeInTheDocument();
  });

  it('dit « rien à proposer » plutôt que de rester muet', async () => {
    vi.mocked(executerRegles).mockResolvedValue([]);
    monterTaches();

    await userEvent.click(screen.getByRole('button', { name: 'Proposer des tâches' }));

    // Un bouton qui ne répond rien passe pour cassé, et l'utilisateur reclique.
    expect(await screen.findByText(/Aucune nouvelle tâche à proposer/)).toBeInTheDocument();
  });

  it('annonce le nombre de tâches proposées', async () => {
    vi.mocked(executerRegles).mockResolvedValue([tache({ origine: 'regle' }), tache({ id: 2 })]);
    monterTaches();

    await userEvent.click(screen.getByRole('button', { name: 'Proposer des tâches' }));

    expect(await screen.findByText(/2 tâche\(s\) proposée\(s\)/)).toBeInTheDocument();
  });
});

describe('récolte — carence opposable (SPRINT-22)', () => {
  const monterRecoltes = () =>
    render(
      <LangueProvider>
        <DialoguesProvider>
          <RecoltesVue />
        </DialoguesProvider>
      </LangueProvider>,
    );

  const saisirRecolte = async () => {
    vi.mocked(ruches.lister).mockResolvedValue([
      {
        id: 5,
        modele: 'Dadant',
        siteId: 1,
        siteNom: 'Rucher',
        fermeId: 1,
        fermeNom: 'Ferme',
        agentResponsableId: null,
        agentResponsableNom: null,
        etat: 'active',
        nbHausses: 1,
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
    monterRecoltes();
    await userEvent.click(await screen.findByRole('button', { name: '+ Nouveau' }));
    await userEvent.selectOptions(await screen.findByLabelText(/Ruche/), '5');
    // La date est `requis` : sans elle, jsdom applique la validation HTML et le
    // formulaire ne se soumet pas — le test echouerait sans rien apprendre.
    await userEvent.type(screen.getByLabelText(/Date/), '2026-07-15');
    await userEvent.type(screen.getByLabelText('Quantité (kg)'), '18.5');
  };

  it('transforme un refus 409 en décision, pas en erreur de saisie', async () => {
    vi.mocked(recoltes.creer).mockRejectedValue(
      new ErreurApi(409, 'Ruche sous carence jusqu’au 2026-08-14 (Apivar).'),
    );
    await saisirRecolte();

    await userEvent.click(screen.getByRole('button', { name: 'Enregistrer' }));

    // Le message du serveur porte le produit et la date : c'est ce qui permet de
    // reporter plutôt que de contourner.
    expect(await screen.findByText(/2026-08-14/)).toBeInTheDocument();
    // Et la porte de sortie s'ouvre : le champ de motif apparaît.
    expect(screen.getByLabelText(/Motif du forçage/)).toBeInTheDocument();
  });

  it('renvoie le motif au serveur quand on force', async () => {
    vi.mocked(recoltes.creer)
      .mockRejectedValueOnce(new ErreurApi(409, 'Ruche sous carence jusqu’au 2026-08-14.'))
      .mockResolvedValueOnce(recolte());
    await saisirRecolte();

    await userEvent.click(screen.getByRole('button', { name: 'Enregistrer' }));
    await userEvent.type(
      await screen.findByLabelText(/Motif du forçage/),
      'Hausse posée après la fin du traitement.',
    );
    // Le bouton a changé de nom : on ne force pas par inadvertance.
    await userEvent.click(screen.getByRole('button', { name: 'Forcer et enregistrer' }));

    await waitFor(() => expect(recoltes.creer).toHaveBeenCalledTimes(2));
    const second = vi.mocked(recoltes.creer).mock.calls[1][0];
    // Un forçage muet n'a aucune valeur devant un contrôle : le serveur le
    // refuserait, et l'interface ne doit pas le laisser partir.
    expect(second).toMatchObject({
      forcerCarence: true,
      motifForcage: 'Hausse posée après la fin du traitement.',
    });
  });
});
