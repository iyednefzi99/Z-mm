import { render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { LangueProvider } from '../i18n/langue';
import type { CouvertRucher, FloraisonObservee } from '../api/types';
import { PanneauEnvironnement } from '../environnement/PanneauEnvironnement';

/**
 * Environnement d'un rucher (SPRINT-32, lot H).
 *
 * <p>Trois comportements, et ils portent tous la même idée : **ce panneau dit
 * d'où il parle, et ce qu'il ignore**. Une surface sans millésime ni source
 * n'engage personne ; une couche partielle ne doit pas se lire comme un
 * environnement vide ; et une distance à une culture n'est pas une distance à
 * une zone traitée.
 */
vi.mock('../api/client', () => ({
  chargerCouvert: vi.fn(),
  chargerRotation: vi.fn(),
  chargerFloraisons: vi.fn(),
}));

const client = await import('../api/client');

const COUVERT: CouvertRucher = {
  siteId: 1,
  siteNom: 'Rucher des tilleuls',
  rayonKm: 3,
  millesime: 2026,
  source: 'RPG 2026',
  surfaceCercleHa: 2827.43,
  couverte: 34.2,
  distanceCultureM: 420,
  surfaces: [
    { classe: 'culture', surfaceHa: 620.5, part: 21.9 },
    { classe: 'foret', surfaceHa: 346.1, part: 12.3 },
  ],
};

const FLORAISON: FloraisonObservee = {
  id: 7,
  ressourceId: 3,
  ressource: 'colza',
  siteId: 1,
  annee: 2026,
  dateDebut: '2026-04-18',
  datePic: '2026-05-02',
  dateFin: null,
  abondance: 2,
  moisDeclare: 4,
  ecartJours: 17,
  note: null,
};

beforeEach(() => {
  vi.mocked(client.chargerCouvert).mockResolvedValue(COUVERT);
  vi.mocked(client.chargerRotation).mockResolvedValue([]);
  vi.mocked(client.chargerFloraisons).mockResolvedValue([]);
});

afterEach(() => vi.clearAllMocks());

const monter = () =>
  render(
    <LangueProvider>
      <PanneauEnvironnement siteId={1} />
    </LangueProvider>,
  );

describe('environnement du rucher', () => {
  it('affiche la source et le millésime avec les surfaces', async () => {
    monter();

    // « 42 % de cultures » n'engage personne tant qu'on ne sait pas de quelle
    // année et de quel jeu de données cela vient (§13).
    expect(await screen.findByText(/RPG 2026/)).toBeInTheDocument();
    expect(screen.getByText(/2026/)).toBeInTheDocument();
    expect(screen.getByText('Cultures')).toBeInTheDocument();
    expect(screen.getByText('21.9 %')).toBeInTheDocument();
  });

  it('dit quelle part du cercle la couche décrit réellement', async () => {
    monter();

    expect(await screen.findByText(/34.2 %/)).toBeInTheDocument();
    // Trente pour cent de couverture et soixante-dix pour cent de silence ne
    // disent pas « 70 % de sol nu ».
    expect(screen.getByText(/ne veulent pas dire soixante-dix pour cent de sol nu/))
      .toBeInTheDocument();
  });

  it('refuse de présenter la distance à une culture comme une exposition', async () => {
    monter();

    expect(await screen.findByText(/pas à une zone traitée/)).toBeInTheDocument();
    expect(screen.getByText(/Aucune couche ouverte ne dit ce qui a été épandu/))
      .toBeInTheDocument();
  });

  it('écrit qu’aucune couche n’a été versée, au lieu d’afficher des zéros', async () => {
    vi.mocked(client.chargerCouvert).mockResolvedValue({
      ...COUVERT,
      millesime: null,
      source: null,
      couverte: null,
      distanceCultureM: null,
      surfaces: [],
    });

    monter();

    // Rien n'arrive tout seul : c'est le coût assumé de ne rien aller chercher
    // dehors, et la phrase dit pourquoi.
    expect(await screen.findByText(/Zümm ne va pas la chercher/)).toBeInTheDocument();
  });

  it('confronte la floraison observée au calendrier déclaré', async () => {
    vi.mocked(client.chargerFloraisons).mockResolvedValue([FLORAISON]);

    monter();

    expect(await screen.findByText(/colza · 2026/)).toBeInTheDocument();
    // Dix-sept jours après le 1er avril déclaré : c'est la seule chose qui
    // permette de dire « cette année, c'était en retard ».
    expect(screen.getByText(/17 jours en retard/)).toBeInTheDocument();
  });
});
