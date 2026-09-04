import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { definir, reinitialiserSession } from '../auth/session';
import { LangueProvider } from '../i18n/langue';
import { DialoguesProvider } from '../ui/dialogues';
import { VisitesVue } from './VisitesVue';
import type { Brouillon, Ruche, Visite } from '../api/types';

/**
 * Brouillon de visite et garde de version (SPRINT-24, lot C).
 *
 * <p>Trois comportements, et ce sont ceux qui décident si le travail serveur
 * sert à quelque chose :
 *
 * <ol>
 *   <li>un brouillon laissé sur un autre appareil <strong>se voit</strong>, avec
 *       l'appareil et l'heure : « commencé il y a deux heures sur le téléphone »
 *       se reprend, « commencé il y a trois semaines » s'efface ;
 *   <li>le reprendre <strong>repose le formulaire</strong> — un brouillon qu'on
 *       ne peut pas rouvrir ne sert à rien ;
 *   <li>modifier une visite envoie la <strong>version lue</strong>, sans quoi le
 *       rejeu de la file écraserait en silence le travail d'un autre agent.
 * </ol>
 */
vi.mock('../api/client', () => ({
  visites: { lister: vi.fn(), creer: vi.fn(), mettreAJour: vi.fn(), supprimer: vi.fn() },
  ruches: { lister: vi.fn() },
  agents: { lister: vi.fn() },
  plannings: { lister: vi.fn() },
  deposerBrouillon: vi.fn(),
  effacerBrouillon: vi.fn(),
  listerBrouillons: vi.fn(),
  listerPhotos: vi.fn(),
  ajouterPhoto: vi.fn(),
  supprimerPhoto: vi.fn(),
  chargerMeteo: vi.fn(),
  telechargerRapportVisite: vi.fn(),
  ErreurApi: class ErreurApi extends Error {},
}));

const client = await import('../api/client');

const RUCHE = {
  id: 5,
  modele: 'Dadant',
  siteId: 1,
  siteNom: 'Rucher du causse',
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
} as Ruche;

const brouillon = (over: Partial<Brouillon> = {}): Brouillon => ({
  id: 3,
  agentId: 2,
  agentNom: 'Amine Trabelsi',
  rucheId: 5,
  rucheModele: 'Dadant',
  siteNom: 'Rucher du causse',
  contenu: JSON.stringify({
    rucheId: 5,
    agentId: 2,
    dateVisite: '2026-08-20',
    raison: 'controle',
    constatations: 'trois cadres de couvain, reine vue',
  }),
  appareil: 'téléphone',
  creeLe: '2026-09-02T08:00:00Z',
  majLe: '2026-09-02T10:30:00Z',
  ...over,
});

beforeEach(() => {
  vi.mocked(client.visites.lister).mockResolvedValue([]);
  vi.mocked(client.ruches.lister).mockResolvedValue([RUCHE]);
  vi.mocked(client.agents.lister).mockResolvedValue([
    { id: 2, nom: 'Amine Trabelsi' } as never,
  ]);
  vi.mocked(client.plannings.lister).mockResolvedValue([]);
  vi.mocked(client.listerBrouillons).mockResolvedValue([]);
  vi.mocked(client.listerPhotos).mockResolvedValue([]);
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
        <VisitesVue />
      </DialoguesProvider>
    </LangueProvider>,
  );

/** Ouvre la modale et choisit la ruche puis l'agent : c'est ce couple qui porte le brouillon. */
const ouvrirEtChoisir = async () => {
  await userEvent.click(await screen.findByRole('button', { name: '+ Nouveau' }));
  await userEvent.selectOptions(screen.getByLabelText('Modèle'), '5');
  await userEvent.selectOptions(screen.getByLabelText('Agent'), '2');
};

describe('brouillon de visite (SPRINT-24)', () => {
  it('signale un brouillon laissé sur un autre appareil, et dit lequel', async () => {
    vi.mocked(client.listerBrouillons).mockResolvedValue([brouillon()]);

    monter();
    await ouvrirEtChoisir();

    // L'appareil et l'heure sont ce qui rend la reprise décidable.
    expect(await screen.findByText(/depuis téléphone/)).toBeInTheDocument();
  });

  it('repose le formulaire tel qu’il avait été laissé', async () => {
    vi.mocked(client.listerBrouillons).mockResolvedValue([brouillon()]);

    monter();
    await ouvrirEtChoisir();
    await userEvent.click(await screen.findByRole('button', { name: 'Reprendre le brouillon' }));

    // Un brouillon qu'on ne peut pas rouvrir ne sert à rien.
    expect(screen.getByLabelText('Constatations')).toHaveValue(
      'trois cadres de couvain, reine vue',
    );
  });

  it('dépose la saisie en cours sans exiger qu’elle soit complète', async () => {
    vi.mocked(client.deposerBrouillon).mockResolvedValue(brouillon());

    monter();
    await ouvrirEtChoisir();
    await userEvent.click(screen.getByRole('button', { name: 'Enregistrer le brouillon' }));

    // Ni date, ni observation : une saisie en cours a le droit d'être
    // incomplète, et la refuser ferait perdre ce qu'on cherche à sauver.
    await waitFor(() => expect(client.deposerBrouillon).toHaveBeenCalled());
    const corps = vi.mocked(client.deposerBrouillon).mock.calls[0][0];
    expect(corps.rucheId).toBe(5);
    expect(corps.agentId).toBe(2);
    expect(JSON.parse(corps.contenu)).toMatchObject({ rucheId: 5, agentId: 2 });
  });

  it('ne propose pas de reprendre un brouillon par-dessus une visite existante', async () => {
    vi.mocked(client.listerBrouillons).mockResolvedValue([brouillon()]);
    vi.mocked(client.visites.lister).mockResolvedValue([
      {
        id: 11,
        rucheId: 5,
        rucheModele: 'Dadant',
        agentId: 2,
        agentNom: 'Amine Trabelsi',
        planningId: null,
        dateVisite: '2026-08-20',
        heureVisite: null,
        dureeMin: null,
        raison: 'controle',
        constatations: 'colonie calme',
        actionsPrevues: null,
        actionsEffectuees: null,
        recommandations: null,
        effectifQualitatif: null,
        etatSante: null,
        productivite: null,
        observation: null,
        meteo: null,
        pathologies: [],
        photos: [],
        creeLe: '2026-08-20T08:00:00Z',
        majLe: '2026-08-20T08:00:00Z',
      } as Visite,
    ]);

    monter();
    await userEvent.click(await screen.findByRole('button', { name: 'Modifier' }));

    // Reprendre un brouillon par-dessus une visite enregistrée écraserait le
    // registre avec une saisie abandonnée.
    expect(screen.queryByRole('button', { name: 'Reprendre le brouillon' })).toBeNull();
  });

  it('envoie la version lue quand on modifie une visite', async () => {
    vi.mocked(client.visites.lister).mockResolvedValue([
      {
        id: 11,
        rucheId: 5,
        rucheModele: 'Dadant',
        agentId: 2,
        agentNom: 'Amine Trabelsi',
        planningId: null,
        dateVisite: '2026-08-20',
        heureVisite: null,
        dureeMin: null,
        raison: 'controle',
        constatations: 'colonie calme',
        actionsPrevues: null,
        actionsEffectuees: null,
        recommandations: null,
        effectifQualitatif: null,
        etatSante: null,
        productivite: null,
        observation: null,
        meteo: null,
        pathologies: [],
        photos: [],
        creeLe: '2026-08-20T08:00:00Z',
        majLe: '2026-08-20T09:15:00Z',
      } as Visite,
    ]);
    vi.mocked(client.visites.mettreAJour).mockResolvedValue({} as Visite);

    monter();
    await userEvent.click(await screen.findByRole('button', { name: 'Modifier' }));
    await userEvent.click(screen.getByRole('button', { name: 'Enregistrer' }));

    // Sans cet en-tête, le rejeu d'une saisie faite au rucher trois heures plus
    // tôt écraserait en silence celle d'un collègue rentré avant.
    await waitFor(() => expect(client.visites.mettreAJour).toHaveBeenCalled());
    expect(vi.mocked(client.visites.mettreAJour).mock.calls[0][2]).toEqual({
      'X-Zumm-Version': '2026-08-20T09:15:00Z',
    });
  });
});
