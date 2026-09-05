import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { definir, reinitialiserSession } from '../auth/session';
import { LangueProvider } from '../i18n/langue';
import { DialoguesProvider } from '../ui/dialogues';
import { RecoltesVue } from './RecoltesVue';
import { SitesVue } from './SitesVue';
import { TableauxVue } from './TableauxVue';
import type { ChargeAgent, ComparaisonSite, Ruche, Site, SyntheseRucher } from '../api/types';

/**
 * Tests du lot B côté interface (SPRINT-23).
 *
 * <p>Le serveur a ses propres tests ; ce qui se joue ici, c'est de savoir si ce
 * qu'il calcule reste vrai une fois affiché. Quatre points, et ce sont les
 * quatre par lesquels un tableau de bord se met à mentir :
 *
 * <ol>
 *   <li>un lot partiel <strong>nomme ses refus</strong> — sans eux, on reprend
 *       quarante ruches au lieu de trois ;
 *   <li>« non évalué » ne s'affiche pas comme un zéro : un rucher qu'on n'a pas
 *       encore visité n'est pas en mauvaise santé, il est inconnu ;
 *   <li>un emplacement sans colonie n'a pas un rendement de zéro, il n'en a
 *       pas — l'écrire le classerait dernier d'une comparaison à laquelle il
 *       n'a pas participé ;
 *   <li>la charge d'équipe s'affiche avec la phrase qui dit à quoi elle
 *       <strong>ne</strong> sert pas.
 * </ol>
 */
vi.mock('../api/client', () => ({
  chargerBriefing: vi.fn(() => Promise.resolve({ genereLe: '2026-09-05', lignes: [] })),
  recoltes: { lister: vi.fn(), creer: vi.fn(), mettreAJour: vi.fn(), supprimer: vi.fn() },
  sites: { lister: vi.fn(), creer: vi.fn(), mettreAJour: vi.fn(), supprimer: vi.fn() },
  ruches: { lister: vi.fn() },
  fermes: { lister: vi.fn() },
  agents: { lister: vi.fn() },
  recolterEnLot: vi.fn(),
  chargerCalendrier: vi.fn(),
  chargerPrevisions: vi.fn(),
  chargerProduction: vi.fn(),
  chargerSynthese: vi.fn(),
  chargerAlertesSanitaires: vi.fn(),
  telechargerExport: vi.fn(),
  comparerSites: vi.fn(),
  syntheseRuchers: vi.fn(),
  chargeEquipe: vi.fn(),
  tracerLot: vi.fn(),
  emplacementsSite: vi.fn(),
  transportsSite: vi.fn(),
  planifierTransport: vi.fn(),
  demenagerSite: vi.fn(),
  voisinsSite: vi.fn(),
  syntheseGlobale: vi.fn(),
  previsionsRecolte: vi.fn(),
  listerAlertes: vi.fn(),
  calendrierVisites: vi.fn(),
  productionParRuche: vi.fn(),
  visitesParRaison: vi.fn(),
  exporterCsv: vi.fn(),
  ErreurApi: class ErreurApi extends Error {
    constructor(
      readonly statut: number,
      readonly detail: string,
    ) {
      super(detail);
    }
  },
}));

const client = await import('../api/client');

const ruche = (over: Partial<Ruche> = {}): Ruche => ({
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
  ...over,
});

const site = (over: Partial<Site> = {}): Site => ({
  id: 1,
  nom: 'Plateau',
  fermeId: 1,
  fermeNom: 'Ferme',
  latitude: 44.8,
  longitude: 1.8,
  altitude: null,
  rayonButinageKm: null,
  dateMiseEnOeuvre: '2026-03-01',
  dateDemenagement: null,
  dateCloture: null,
  adresseRue: null,
  codePostal: null,
  ville: null,
  pays: null,
  typeSite: null,
  exposition: null,
  priorite: 'normale',
  couvertureReseau: null,
  ressources: [],
  creeLe: '2026-01-01T00:00:00Z',
  majLe: '2026-01-01T00:00:00Z',
  ...over,
});

beforeEach(() => {
  vi.mocked(client.recoltes.lister).mockResolvedValue([]);
  vi.mocked(client.sites.lister).mockResolvedValue([]);
  vi.mocked(client.ruches.lister).mockResolvedValue([]);
  vi.mocked(client.fermes.lister).mockResolvedValue([]);
  vi.mocked(client.agents.lister).mockResolvedValue([]);
  vi.mocked(client.syntheseRuchers).mockResolvedValue([]);
  vi.mocked(client.chargeEquipe).mockResolvedValue([]);
  vi.mocked(client.chargerCalendrier).mockResolvedValue([]);
  definir({ utilisateur: 'lea', roles: ['responsable'], exploitation: 'demo' });
});

afterEach(() => {
  reinitialiserSession();
  vi.clearAllMocks();
});

const monter = (vue: React.ReactElement) =>
  render(
    <LangueProvider>
      <DialoguesProvider>{vue}</DialoguesProvider>
    </LangueProvider>,
  );

describe('récolte de rucher entier (SPRINT-23)', () => {
  it('nomme les ruches refusées au lieu d’annoncer un lot réussi', async () => {
    vi.mocked(client.ruches.lister).mockResolvedValue([ruche()]);
    vi.mocked(client.recolterEnLot).mockResolvedValue({
      demandees: 3,
      reussites: [11, 12],
      echecs: [{ rucheId: 7, motif: 'Ruche sous carence jusqu’au 2026-09-14 (Apivar).' }],
    });

    monter(<RecoltesVue />);

    await userEvent.click(await screen.findByRole('button', { name: '+ Nouveau' }));
    await userEvent.selectOptions(screen.getByLabelText('Ruche'), '5');
    await userEvent.type(screen.getByLabelText('Date'), '2026-08-20');
    await userEvent.type(screen.getByLabelText('Quantité (kg)'), '12.5');

    await userEvent.click(screen.getByRole('button', { name: 'Récolter tout le rucher' }));

    // Le serveur reçoit le RUCHER de la ruche choisie, et une quantité qui vaut
    // pour CHAQUE ruche — jamais un total à répartir.
    expect(client.recolterEnLot).toHaveBeenCalledWith(
      expect.objectContaining({
        cible: { rucheIds: null, siteId: 1 },
        quantiteKgParRuche: 12.5,
        // Forcer une carence est une décision par colonie : le lot ne la propose
        // pas, sans quoi elle deviendrait une case à cocher.
        forcerCarence: false,
      }),
    );

    expect(await screen.findByText('2 sur 3 enregistrées.')).toBeInTheDocument();
    // Le motif est ce qui permet de reprendre trois ruches au lieu de quarante.
    expect(screen.getByText(/Ruche sous carence/)).toBeInTheDocument();
  });
});

describe('comparaison d’emplacements (SPRINT-23)', () => {
  it('n’écrit pas un rendement de zéro pour un rucher sans colonie', async () => {
    vi.mocked(client.sites.lister).mockResolvedValue([
      site({ id: 1, nom: 'Plateau' }),
      site({ id: 2, nom: 'Vallée' }),
    ]);
    const comparaison: ComparaisonSite[] = [
      {
        siteId: 1,
        siteNom: 'Plateau',
        ville: 'Gramat',
        typeSite: 'transhumance',
        exposition: 'sud',
        altitude: 620,
        nbRuches: 2,
        rendementKgParRuche: 10,
        ressourcesDeclarees: 2,
        ressourcesEnFleur: 1,
        ruchersA3km: 1,
      },
      {
        siteId: 2,
        siteNom: 'Vallée',
        ville: null,
        typeSite: null,
        exposition: null,
        altitude: null,
        nbRuches: 0,
        rendementKgParRuche: null,
        ressourcesDeclarees: 0,
        ressourcesEnFleur: 0,
        ruchersA3km: 1,
      },
    ];
    vi.mocked(client.comparerSites).mockResolvedValue(comparaison);

    monter(<SitesVue />);

    await userEvent.click(await screen.findByRole('button', { name: 'Comparer des emplacements' }));
    await userEvent.click(screen.getByLabelText(/Plateau/));
    await userEvent.click(screen.getByLabelText(/Vallée/));
    await userEvent.click(screen.getByRole('button', { name: 'Comparer' }));

    expect(client.comparerSites).toHaveBeenCalledWith([1, 2]);
    // Un rucher sans colonie n'a pas un rendement de zéro : il n'en a pas.
    expect(await screen.findByText('Aucune colonie')).toBeInTheDocument();
    // L'absence de note globale se dit, elle ne se devine pas.
    expect(screen.getByText(/Aucune note globale/)).toBeInTheDocument();
  });

  it('refuse de comparer un seul emplacement, et le dit avant le clic', async () => {
    vi.mocked(client.sites.lister).mockResolvedValue([site()]);

    monter(<SitesVue />);
    await userEvent.click(await screen.findByRole('button', { name: 'Comparer des emplacements' }));
    await userEvent.click(screen.getByLabelText(/Plateau/));

    expect(screen.getByText('Choisissez au moins deux emplacements.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Comparer' })).toBeDisabled();
    expect(client.comparerSites).not.toHaveBeenCalled();
  });
});

describe('synthèse par rucher et charge d’équipe (SPRINT-23)', () => {
  const rucher = (over: Partial<SyntheseRucher> = {}): SyntheseRucher => ({
    siteId: 1,
    siteNom: 'Rucher neuf',
    ville: null,
    priorite: 'haute',
    nbRuches: 4,
    nbActives: 4,
    santeMoyenne: null,
    coloniesEvaluees: 0,
    risqueEssaimageMax: null,
    ruchesSousCarence: 0,
    alertesOuvertes: 0,
    tachesOuvertes: 0,
    productionKg: 0,
    ...over,
  });

  it('affiche « non évalué », jamais un zéro, pour un rucher jamais visité', async () => {
    vi.mocked(client.syntheseRuchers).mockResolvedValue([rucher()]);

    monter(<TableauxVue />);
    await userEvent.click(await screen.findByRole('button', { name: 'Par rucher' }));

    expect(await screen.findByText('Rucher neuf')).toBeInTheDocument();
    // Un rucher qu'on n'a pas encore vu n'est pas en mauvaise santé.
    expect(screen.getByText('Non évalué')).toBeInTheDocument();
    expect(screen.getByText('Haute')).toBeInTheDocument();
  });

  it('accompagne la moyenne du nombre de colonies réellement évaluées', async () => {
    vi.mocked(client.syntheseRuchers).mockResolvedValue([
      rucher({ santeMoyenne: 72, coloniesEvaluees: 3, risqueEssaimageMax: 40 }),
    ]);

    monter(<TableauxVue />);
    await userEvent.click(await screen.findByRole('button', { name: 'Par rucher' }));

    // 72 sur trois colonies observées et 72 sur quarante ne se lisent pas de la
    // même façon : le dénominateur fait partie du chiffre.
    expect(await screen.findByText(/3 colonie\(s\) évaluée\(s\)/)).toBeInTheDocument();
  });

  it('dit à quoi les chiffres de l’équipe ne servent pas', async () => {
    const agent: ChargeAgent = {
      agentId: 1,
      agentNom: 'Hedi Mansour',
      role: 'apiculteur',
      ruchesResponsable: 3,
      ruchersConcernes: 2,
      tachesOuvertes: 2,
      tachesEnRetard: 1,
      tachesCritiques: 1,
      visites7Jours: 0,
    };
    vi.mocked(client.chargeEquipe).mockResolvedValue([agent]);

    monter(<TableauxVue />);
    await userEvent.click(await screen.findByRole('button', { name: 'Équipe' }));

    expect(await screen.findByText('Hedi Mansour')).toBeInTheDocument();
    // Trois ruches sur deux ruchers font deux déplacements, pas trois : la
    // colonne se lit dans l'ordre des en-têtes, pas au premier « 2 » venu.
    const cellules = screen.getAllByRole('cell').map((c) => c.textContent);
    expect(cellules).toEqual(['Hedi Mansour', '3', '2', '2', '1', '1', '0']);
    expect(screen.getByText(/jamais à comparer des personnes/)).toBeInTheDocument();
  });
});
