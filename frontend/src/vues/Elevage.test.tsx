import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { LangueProvider } from '../i18n/langue';
import type {
  DossierConformite,
  Genealogie,
  IndexGenetique,
  ReineElevage,
} from '../api/types';
import { PanneauElevage } from '../elevage/PanneauElevage';

/**
 * Élevage, côté interface (SPRINT-29, lot D).
 *
 * <p>Quatre comportements, et trois tiennent à des refus :
 *
 * <ol>
 *   <li>l'arbre de lignée est <strong>doublé d'une liste</strong> — un SVG de
 *       rectangles ne s'annonce pas à un lecteur d'écran ;
 *   <li>un critère sans assez d'observations le <strong>dit</strong>, au lieu
 *       d'afficher un tiret qui passerait pour un zéro ;
 *   <li>l'écran ne fabrique <strong>aucune note globale</strong>, et affiche la
 *       phrase qui explique pourquoi ;
 *   <li>le dossier de contrôle imprime son avertissement : Zümm ne certifie rien.
 * </ol>
 */
vi.mock('../api/client', () => ({
  reinesElevage: {
    lister: vi.fn(),
    creer: vi.fn(),
    supprimer: vi.fn(),
    mettreAJour: vi.fn(),
    obtenir: vi.fn(),
  },
  series: { lister: vi.fn(), creer: vi.fn(), supprimer: vi.fn(), mettreAJour: vi.fn() },
  ruches: { lister: vi.fn() },
  chargerGenealogie: vi.fn(),
  chargerIndexGenetique: vi.fn(),
  chargerConformite: vi.fn(),
  ouvrirDocumentElevage: vi.fn(),
  ErreurApi: class ErreurApi extends Error {},
}));

const client = await import('../api/client');

const REINE: ReineElevage = {
  id: 5,
  code: 'R-2026-12',
  mereId: 2,
  mereCode: 'R-2025-07',
  rucheMereId: null,
  serieId: null,
  serieNom: null,
  rucheId: 3,
  rucheModele: 'Dadant 10',
  origine: 'elevage',
  fournisseur: null,
  race: 'Buckfast',
  anneeNaissance: 2026,
  couleurMarquage: null,
  ailesClippees: null,
  dateGreffage: null,
  dateNaissance: null,
  dateFecondation: null,
  dateIntroduction: '2026-06-01',
  dateFin: null,
  statut: 'en_service',
  note: null,
  creeLe: '2026-09-05T08:00:00Z',
  majLe: '2026-09-05T08:00:00Z',
};

const GENEALOGIE: Genealogie = {
  reine: REINE,
  ascendants: [
    {
      id: 2,
      code: 'R-2025-07',
      profondeur: 1,
      parentId: null,
      race: 'Buckfast',
      anneeNaissance: 2025,
      statut: 'remplacee',
    },
  ],
  descendants: [
    {
      id: 8,
      code: 'R-2027-01',
      profondeur: 1,
      parentId: 5,
      race: null,
      anneeNaissance: 2027,
      statut: 'en_service',
    },
  ],
};

const INDEX: IndexGenetique = {
  reineId: 5,
  code: 'R-2026-12',
  rucheId: 3,
  debut: '2026-06-01',
  fin: '2026-09-05',
  criteres: [
    { code: 'douceur', valeur: 2.7, unite: 'sur 3', observations: 3, suffisant: true },
    { code: 'hygiene', valeur: null, unite: 'sur 3', observations: 0, suffisant: false },
  ],
};

const DOSSIER: DossierConformite = {
  debut: '2026-01-01',
  fin: '2026-12-31',
  avertissement: 'Zümm ne certifie pas. Ce dossier rassemble les pièces d’un contrôle.',
  points: [
    {
      code: 'nourrissement',
      statut: 'a_justifier',
      detail: 'L’origine biologique des sucres ne figure pas dans le système.',
      nombre: 4,
    },
  ],
};

beforeEach(() => {
  vi.mocked(client.reinesElevage.lister).mockResolvedValue([REINE]);
  vi.mocked(client.series.lister).mockResolvedValue([]);
  vi.mocked(client.ruches.lister).mockResolvedValue([]);
  vi.mocked(client.chargerGenealogie).mockResolvedValue(GENEALOGIE);
  vi.mocked(client.chargerIndexGenetique).mockResolvedValue(INDEX);
  vi.mocked(client.chargerConformite).mockResolvedValue(DOSSIER);
});

afterEach(() => {
  vi.clearAllMocks();
});

const monter = () =>
  render(
    <LangueProvider>
      <PanneauElevage />
    </LangueProvider>,
  );

describe('élevage', () => {
  it('double l’arbre de lignée d’une liste, pour les lecteurs d’écran', async () => {
    monter();
    await userEvent.click(await screen.findByRole('button', { name: 'Modifier' }));

    // Le SVG porte son étiquette…
    expect(await screen.findByRole('img', { name: /Arbre de lignée/ })).toBeInTheDocument();
    // …et la lignée est aussi lisible en texte : un SVG de rectangles ne
    // s'annonce pas, et une généalogie ne peut pas se contenter d'être montrée.
    expect(screen.getByText(/Mère \(1\) : R-2025-07/)).toBeInTheDocument();
    expect(screen.getByText(/Fille \(1\) : R-2027-01/)).toBeInTheDocument();
  });

  it('dit qu’un critère n’est pas assez observé au lieu d’afficher un tiret', async () => {
    monter();
    await userEvent.click(await screen.findByRole('button', { name: 'Modifier' }));

    expect(await screen.findByText('2.7 sur 3')).toBeInTheDocument();
    // Le test hygiénique n'a jamais été relevé : « 0 » se lirait comme un
    // mauvais résultat, alors que personne n'a regardé.
    expect(screen.getByText('pas assez observé')).toBeInTheDocument();
    expect(screen.getByText('0 observations')).toBeInTheDocument();
  });

  it('n’affiche aucune note globale, et dit pourquoi', async () => {
    monter();
    await userEvent.click(await screen.findByRole('button', { name: 'Modifier' }));

    expect(await screen.findByText(/Aucune note globale n'est calculée/))
      .toBeInTheDocument();
  });

  it('affiche l’avertissement du dossier de contrôle avant ses points', async () => {
    monter();
    await userEvent.click(await screen.findByRole('button', { name: 'Points de contrôle' }));

    await waitFor(() => expect(client.chargerConformite).toHaveBeenCalled());
    // Zümm ne certifie rien : la phrase accompagne le dossier partout.
    expect(await screen.findByText(/ne certifie pas/)).toBeInTheDocument();
    expect(screen.getByText('À justifier', { exact: false })).toBeInTheDocument();
  });

  it('ne demande le fournisseur que pour une reine achetée', async () => {
    monter();
    await userEvent.click(await screen.findByRole('button', { name: 'Nouvelle reine' }));

    // Origine « élevage maison » par défaut : le champ n'a pas lieu d'être, et
    // la base le refuserait.
    expect(screen.queryByLabelText('Fournisseur')).not.toBeInTheDocument();

    await userEvent.selectOptions(screen.getByLabelText('Origine'), 'achat');
    expect(screen.getByLabelText('Fournisseur')).toBeInTheDocument();
  });
});
