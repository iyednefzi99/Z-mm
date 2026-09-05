import { render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { LangueProvider } from '../i18n/langue';
import type { Briefing } from '../api/types';
import { BriefingPanneau } from './BriefingPanneau';

/**
 * Le point du jour (SPRINT-30, lot G).
 *
 * <p>Trois comportements, et ils portent tous la même idée : ce briefing se
 * VÉRIFIE. Chaque ligne cite ce qui la fonde, l'écran dit qu'aucun modèle de
 * langue n'intervient, et un échec de chargement ne montre rien plutôt qu'une
 * bannière d'erreur en tête du tableau de bord.
 */
vi.mock('../api/client', () => ({
  chargerBriefing: vi.fn(),
}));

const client = await import('../api/client');

const BRIEFING: Briefing = {
  genereLe: '2026-09-05',
  lignes: [
    {
      categorie: 'alerte',
      urgence: 1,
      titre: 'Alerte POIDS sur la ruche #12',
      detail: 'Declenchee a 1.20',
      rucheId: 12,
    },
    {
      categorie: 'carence',
      urgence: 2,
      titre: 'Fin de carence le 2026-09-09 sur la ruche #7',
      detail: 'Apivar, applique le 2026-07-01',
      rucheId: 7,
    },
  ],
};

beforeEach(() => {
  vi.mocked(client.chargerBriefing).mockResolvedValue(BRIEFING);
});

afterEach(() => vi.clearAllMocks());

const monter = () =>
  render(
    <LangueProvider>
      <BriefingPanneau />
    </LangueProvider>,
  );

describe('briefing du jour', () => {
  it('groupe les lignes par urgence et montre ce qui les fonde', async () => {
    monter();

    expect(await screen.findByText("Aujourd'hui")).toBeInTheDocument();
    expect(screen.getByText('Cette semaine')).toBeInTheDocument();
    // Le `detail` n'est pas décoratif : c'est lui qui rend la ligne vérifiable.
    expect(screen.getByText('Declenchee a 1.20')).toBeInTheDocument();
    expect(screen.getByText('Apivar, applique le 2026-07-01')).toBeInTheDocument();
  });

  it('écrit la catégorie en toutes lettres, sans la confier à la couleur', async () => {
    monter();

    // La moitié des lignes sont sanitaires : un daltonien doit les distinguer.
    expect(await screen.findByText('Alerte')).toBeInTheDocument();
    expect(screen.getByText('Carence')).toBeInTheDocument();
  });

  it('dit qu’aucun modèle de langue n’intervient', async () => {
    monter();

    expect(await screen.findByText(/Aucun modèle de langue n'intervient/))
      .toBeInTheDocument();
  });

  it('annonce une journée calme plutôt qu’un écran vide', async () => {
    vi.mocked(client.chargerBriefing).mockResolvedValue({
      genereLe: '2026-09-05',
      lignes: [],
    });

    monter();

    expect(await screen.findByText(/Rien ne réclame votre attention/)).toBeInTheDocument();
  });

  it('ne montre rien du tout si le chargement échoue', async () => {
    vi.mocked(client.chargerBriefing).mockRejectedValue(new Error('503'));

    const { container } = monter();

    // Le briefing est un confort : une bannière d'erreur en tête du tableau de
    // bord ferait croire à une panne du produit entier.
    await vi.waitFor(() => expect(container.querySelector('.z-briefing')).toBeNull());
  });
});
