import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { definir, reinitialiserSession } from '../auth/session';
import { LangueProvider } from '../i18n/langue';
import { DialoguesProvider } from '../ui/dialogues';
import { CapteursVue } from './CapteursVue';
import type { Partage, PoidsCompartiment, Ruche } from '../api/types';

/**
 * Poids par étage et partage de flux (SPRINT-26, lot F₁).
 *
 * <p>Trois comportements, et ce sont les trois par lesquels ces deux fonctions
 * trahiraient leur utilisateur :
 *
 * <ol>
 *   <li>une hausse jamais pesée affiche « jamais pesé », <strong>pas 0 kg</strong> :
 *       zéro ferait croire à une colonie qui a perdu ses réserves ;
 *   <li>l'URL d'un partage s'affiche <strong>une seule fois</strong>, et l'écran
 *       le dit — la base n'en garde que l'empreinte, la relire est impossible ;
 *   <li>un partage révoqué le montre, au lieu de disparaître : savoir qu'un
 *       partage a été coupé vaut mieux que de ne plus rien savoir.
 * </ol>
 */
vi.mock('../api/client', () => ({
  serieCompartiment: vi.fn(),
  chargerSerieBrute: vi.fn(),
  ruches: { lister: vi.fn() },
  sites: { lister: vi.fn() },
  chargerAlertesOuvertes: vi.fn(),
  ingererMesure: vi.fn(),
  chargerMeteo: vi.fn(),
  chargerQuantiteMiel: vi.fn(),
  detecterAnomalie: vi.fn(),
  chargerSerieJournaliere: vi.fn(),
  peserCompartiment: vi.fn(),
  repartitionCompartiments: vi.fn(),
  ouvrirPartage: vi.fn(),
  listerPartages: vi.fn(),
  revoquerPartage: vi.fn(),
  ErreurApi: class ErreurApi extends Error {},
}));

const client = await import('../api/client');

const RUCHE = { id: 5, modele: 'Dadant' } as Ruche;

const etages: PoidsCompartiment[] = [
  { compartimentId: 1, type: 'corps', nbCadres: 10, valeur: 22.4, instant: '2026-09-01T09:00:00Z' },
  { compartimentId: 2, type: 'hausse', nbCadres: 9, valeur: null, instant: null },
];

const partage = (over: Partial<Partage> = {}): Partage => ({
  id: 3,
  rucheId: 5,
  libelle: 'Technicien sanitaire',
  creeLe: '2026-09-01T09:00:00Z',
  expireLe: '2026-10-01T09:00:00Z',
  revoqueLe: null,
  derniereUtilisation: null,
  actif: true,
  url: null,
  ...over,
});

beforeEach(() => {
  vi.mocked(client.ruches.lister).mockResolvedValue([RUCHE]);
  vi.mocked(client.sites.lister).mockResolvedValue([]);
  vi.mocked(client.chargerAlertesOuvertes).mockResolvedValue([]);
  vi.mocked(client.listerPartages).mockResolvedValue([]);
  // Choisir un étage appelle serieCompartiment (CapteursVue.tsx) : sans valeur
  // par défaut ici, le mock nu rend `undefined` et `.then(...)` explose des
  // qu'un test sélectionne un étage.
  vi.mocked(client.serieCompartiment).mockResolvedValue([]);
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
        <CapteursVue />
      </DialoguesProvider>
    </LangueProvider>,
  );

/**
 * Le volet portant cette légende.
 *
 * <p>L'écran des capteurs compte cinq sélecteurs « Ruche » — ingestion, miel,
 * étages, partage, anomalie. Les distinguer par un libellé différent aurait
 * abîmé l'interface pour arranger le test ; c'est au test de viser le bon
 * volet.
 */
const attendreRuches = async (): Promise<void> => {
  // Les options arrivent d'un appel réseau simulé : viser le sélecteur avant
  // qu'elles soient là donne « Value "5" not found in options ».
  await screen.findAllByRole('option', { name: 'Dadant' });
};

const volet = (legende: string): HTMLElement => {
  const titre = screen.getByText(legende);
  const conteneur = titre.closest('fieldset');
  if (!conteneur) {
    throw new Error(`Volet introuvable : ${legende}`);
  }
  return conteneur;
};

describe('poids par étage', () => {
  it('affiche « jamais pesé » plutôt que zéro pour une hausse sans pesée', async () => {
    vi.mocked(client.repartitionCompartiments).mockResolvedValue(etages);
    monter();
    await attendreRuches();

    await userEvent.selectOptions(within(volet('Poids par étage')).getByLabelText('Ruche'), '5');

    expect(await screen.findByText('jamais pesé')).toBeInTheDocument();
    expect(screen.getByText(/22,4 kg/)).toBeInTheDocument();
  });

  it('enregistre une pesée sur le compartiment choisi, puis recharge', async () => {
    vi.mocked(client.repartitionCompartiments).mockResolvedValue(etages);
    vi.mocked(client.peserCompartiment).mockResolvedValue(etages[1]);
    monter();
    await attendreRuches();

    const etage = volet('Poids par étage');
    await userEvent.selectOptions(within(etage).getByLabelText('Ruche'), '5');
    await userEvent.selectOptions(await within(etage).findByLabelText('Étage'), '2');
    await userEvent.type(within(etage).getByLabelText('Poids (kg)'), '14.5');
    await userEvent.click(within(etage).getByRole('button', { name: 'Enregistrer la pesée' }));

    await waitFor(() =>
      expect(client.peserCompartiment).toHaveBeenCalledWith({
        compartimentId: 2,
        valeur: 14.5,
      }),
    );
  });
});

describe('partage de flux', () => {
  it('affiche l’URL une seule fois, et le dit', async () => {
    vi.mocked(client.ouvrirPartage).mockResolvedValue(
      partage({ url: 'https://zumm.test/api/flux/AbCd' }),
    );
    monter();
    await attendreRuches();

    const partages = volet('Partager un flux');
    await userEvent.selectOptions(within(partages).getByLabelText('Ruche'), '5');
    await userEvent.type(within(partages).getByLabelText('À qui, et pourquoi'), 'Technicien');
    await userEvent.click(within(partages).getByRole('button', { name: 'Ouvrir un partage' }));

    // La base ne garde que l'empreinte : impossible de relire ce lien plus tard.
    expect(await screen.findByText(/il ne sera plus jamais affiché/)).toBeInTheDocument();
    expect(screen.getByText('https://zumm.test/api/flux/AbCd')).toBeInTheDocument();
  });

  it('montre qu’un partage est révoqué au lieu de le faire disparaître', async () => {
    vi.mocked(client.listerPartages).mockResolvedValue([
      partage({ actif: false, revoqueLe: '2026-09-02T10:00:00Z' }),
    ]);
    monter();
    await attendreRuches();

    await userEvent.selectOptions(within(volet('Partager un flux')).getByLabelText('Ruche'), '5');

    // Savoir qu'un partage a été coupé vaut mieux que de ne plus rien savoir.
    expect(await screen.findByText(/révoqué/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Révoquer' })).toBeNull();
  });

  it('dit qu’un partage n’a jamais été consulté', async () => {
    vi.mocked(client.listerPartages).mockResolvedValue([partage()]);
    monter();
    await attendreRuches();

    await userEvent.selectOptions(within(volet('Partager un flux')).getByLabelText('Ruche'), '5');

    // C'est ce qui rend la révocation décidable : un partage jamais ouvert se
    // coupe sans hésiter.
    expect(await screen.findByText(/jamais consulté/)).toBeInTheDocument();
  });
});
