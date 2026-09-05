import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { definir, reinitialiserSession } from '../auth/session';
import { LangueProvider } from '../i18n/langue';
import { DialoguesProvider } from '../ui/dialogues';
import { ComptabiliteVue } from './ComptabiliteVue';
import { MaterielVue } from './MaterielVue';
import type { BilanExploitation, ComparaisonSaisons, Consommable, Materiel } from '../api/types';

/**
 * Matériel, stock et comptabilité côté interface (SPRINT-27, lot E).
 *
 * <p>Quatre comportements, et ce sont ceux par lesquels un module de gestion se
 * met à mentir :
 *
 * <ol>
 *   <li>un matériel sans périodicité affiche « sans entretien », pas une date :
 *       il ne s'entretient <strong>pas</strong>, ce qui n'est pas « quand on y
 *       pense » ;
 *   <li>un stock sous seuil le <strong>dit</strong> — sans quoi la table n'est
 *       qu'un inventaire ;
 *   <li>le bilan répète que les dépenses non affectées ne sont pas réparties :
 *       une assurance ne se divise pas par le nombre de ruches ;
 *   <li>un rendement de saison nul s'affiche « non évalué », jamais zéro.
 * </ol>
 */
vi.mock('../api/client', () => ({
  materiels: { lister: vi.fn(), creer: vi.fn(), mettreAJour: vi.fn(), supprimer: vi.fn() },
  consommables: { lister: vi.fn(), creer: vi.fn(), mettreAJour: vi.fn(), supprimer: vi.fn() },
  depenses: { lister: vi.fn(), creer: vi.fn(), mettreAJour: vi.fn(), supprimer: vi.fn() },
  sites: { lister: vi.fn() },
  ruches: { lister: vi.fn() },
  entretenirMateriel: vi.fn(),
  mouvementerStock: vi.fn(),
  chargerBilan: vi.fn(),
  chargerSaisons: vi.fn(),
  ressourcesExportables: vi.fn(),
  telechargerRessource: vi.fn(),
  telechargerBilanAnnuel: vi.fn(),
  ErreurApi: class ErreurApi extends Error {},
}));

const client = await import('../api/client');

const materiel = (over: Partial<Materiel> = {}): Materiel => ({
  id: 1,
  libelle: 'Extracteur 9 cadres',
  categorie: 'extracteur',
  quantite: 1,
  siteId: null,
  siteNom: null,
  etat: 'bon',
  periodiciteJours: 180,
  derniereMaintenance: '2026-01-01',
  prochaineMaintenance: '2026-06-30',
  enRetard: true,
  note: null,
  creeLe: '2026-01-01T00:00:00Z',
  majLe: '2026-01-01T00:00:00Z',
  ...over,
});

const consommable = (over: Partial<Consommable> = {}): Consommable => ({
  id: 2,
  libelle: 'Candi',
  categorie: 'candi',
  quantite: 2,
  unite: 'kg',
  seuilAlerte: 3,
  sousSeuil: true,
  note: null,
  creeLe: '2026-01-01T00:00:00Z',
  majLe: '2026-01-01T00:00:00Z',
  ...over,
});

beforeEach(() => {
  vi.mocked(client.materiels.lister).mockResolvedValue([]);
  vi.mocked(client.consommables.lister).mockResolvedValue([]);
  vi.mocked(client.depenses.lister).mockResolvedValue([]);
  vi.mocked(client.sites.lister).mockResolvedValue([]);
  vi.mocked(client.ruches.lister).mockResolvedValue([]);
  vi.mocked(client.chargerSaisons).mockResolvedValue([]);
  vi.mocked(client.ressourcesExportables).mockResolvedValue(['recoltes', 'depenses']);
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

describe('matériel et stock', () => {
  it('distingue « sans entretien » d’une échéance dépassée', async () => {
    vi.mocked(client.materiels.lister).mockResolvedValue([
      materiel(),
      materiel({ id: 2, libelle: 'Hausse vide', categorie: 'hausse', periodiciteJours: null,
        prochaineMaintenance: null, enRetard: false }),
    ]);

    monter(<MaterielVue />);

    expect(await screen.findByText('Extracteur 9 cadres')).toBeInTheDocument();
    // Une périodicité absente veut dire « ne s'entretient pas » — pas « quand on
    // y pense » : rien ne sera jamais proposé pour cette hausse.
    expect(screen.getByText('sans entretien')).toBeInTheDocument();
  });

  it('marque l’entretien fait sans rien demander d’autre', async () => {
    vi.mocked(client.materiels.lister).mockResolvedValue([materiel()]);
    vi.mocked(client.entretenirMateriel).mockResolvedValue(
      materiel({ enRetard: false, prochaineMaintenance: '2027-01-01' }),
    );

    monter(<MaterielVue />);
    await userEvent.click(await screen.findByRole('button', { name: 'Fait aujourd’hui' }));

    // Le geste réel — « je viens de réviser l'extracteur » — repousse l'échéance
    // sans qu'on ait à ressaisir la date.
    await waitFor(() => expect(client.entretenirMateriel).toHaveBeenCalledWith(1));
  });

  it('dit qu’un consommable est à racheter', async () => {
    vi.mocked(client.consommables.lister).mockResolvedValue([consommable()]);

    monter(<MaterielVue />);

    expect(await screen.findByText('Candi')).toBeInTheDocument();
    // C'est le seuil qui rend le stock utile : sans lui, la table dit ce qu'on a
    // et jamais ce qui manque.
    // La marque de la LIGNE, pas la phrase d'aide qui la mentionne aussi.
    expect(screen.getByText(/· à racheter/)).toBeInTheDocument();
  });

  it('mouvemente le stock plutôt que de poser un total', async () => {
    vi.mocked(client.consommables.lister).mockResolvedValue([consommable()]);
    vi.mocked(client.mouvementerStock).mockResolvedValue(consommable({ quantite: 1 }));

    monter(<MaterielVue />);
    await userEvent.click(await screen.findByRole('button', { name: '−1' }));

    // Deux personnes qui prélèvent du candi le même jour ne s'écrasent pas.
    await waitFor(() => expect(client.mouvementerStock).toHaveBeenCalledWith(2, -1));
  });
});

describe('comptabilité', () => {
  const bilan = (): BilanExploitation => ({
    debut: '2026-01-01',
    fin: '2026-12-31',
    productionMielKg: 30,
    recettesEur: 360,
    depensesEur: 345,
    resultatEur: 15,
    depensesNonAffectees: 300,
    parCategorie: { assurance: 300, cheptel: 45 },
    parRuche: [
      { rucheId: 1, rucheModele: 'Dadant', siteNom: 'Causse', productionKg: 20,
        recettesEur: 240, depensesEur: 45, resultatEur: 195 },
    ],
  });

  it('répète que les dépenses non affectées ne sont pas réparties', async () => {
    vi.mocked(client.chargerBilan).mockResolvedValue(bilan());

    monter(<ComptabiliteVue />);
    await userEvent.click(await screen.findByRole('button', { name: 'Afficher' }));

    // La phrase est dans l'interface, pas seulement dans le code : un chiffre
    // affiché survit à l'explication qui l'accompagnait.
    expect(await screen.findByText(/non affectés à une ruche/)).toBeInTheDocument();
    expect(screen.getByText(/une assurance ne se divise pas/)).toBeInTheDocument();
  });

  it('affiche « non évalué » pour une saison sans production', async () => {
    const saison: ComparaisonSaisons = {
      annee: 2025,
      productionMielKg: 0,
      ruchesProductives: 0,
      rendementKg: null,
      nombreRecoltes: 0,
      parProduit: [],
    };
    vi.mocked(client.chargerSaisons).mockResolvedValue([saison]);

    monter(<ComptabiliteVue />);

    // Zéro ferait croire à une saison catastrophique là où il n'y a rien eu de
    // saisi.
    expect(await screen.findByText('non évalué')).toBeInTheDocument();
  });

  it('télécharge une ressource dans le format choisi', async () => {
    // La vraie fonction rend une promesse, et la vue y accroche un `.catch`.
    // Sans cette resolution, le mock rend `undefined` : le gestionnaire de clic
    // leve alors une TypeError qui ne fait echouer AUCUNE assertion, mais qui
    // fait sortir Vitest en erreur — le test passait, la commande echouait.
    vi.mocked(client.telechargerRessource).mockResolvedValue(undefined);

    monter(<ComptabiliteVue />);

    await userEvent.selectOptions(await screen.findByLabelText('Ressource'), 'depenses');
    await userEvent.selectOptions(screen.getByLabelText('Format'), 'xlsx');
    await userEvent.click(screen.getByRole('button', { name: 'Télécharger' }));

    expect(client.telechargerRessource).toHaveBeenCalledWith('depenses', 'xlsx');
  });
});
