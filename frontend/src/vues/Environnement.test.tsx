import { render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { LangueProvider } from '../i18n/langue';
import { DialoguesProvider } from '../ui/dialogues';
import { ToastsProvider } from '../ui/toasts';
import type { CouvertRucher, FloraisonObservee, Site } from '../api/types';
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
  // SPRINT-33 : le panneau atteint désormais la vérification terrain, les zones
  // traitées et le versement de couche. Un nom absent de ce mock vaudrait
  // `undefined` à l'appel, et le panneau tomberait au montage.
  chargerParcelles: vi.fn(),
  chargerFiabilite: vi.fn(),
  chargerExposition: vi.fn(),
  chargerZonesTraitees: vi.fn(),
  chargerMillesimes: vi.fn(),
  constaterParcelle: vi.fn(),
  marquerParcelle: vi.fn(),
  declarerZoneTraitee: vi.fn(),
  supprimerZoneTraitee: vi.fn(),
  enregistrerFloraison: vi.fn(),
  supprimerFloraison: vi.fn(),
  purgerCouvert: vi.fn(),
  verserCouvert: vi.fn(),
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

/** Le site n'est chargé que pour ses ressources déclarées : le reste est du décor. */
const SITE: Site = {
  id: 1,
  nom: 'Rucher des tilleuls',
  fermeId: 1,
  fermeNom: 'Ferme du nord',
  latitude: 36.8,
  longitude: 10.18,
  altitude: null,
  rayonButinageKm: 3,
  dateMiseEnOeuvre: '2026-01-10',
  dateDemenagement: null,
  dateCloture: null,
  adresseRue: null,
  codePostal: null,
  ville: null,
  pays: null,
  typeSite: 'sedentaire',
  exposition: null,
  priorite: 'normale',
  couvertureReseau: null,
  ressources: [
    { id: 3, ressource: 'colza', distanceM: 800, moisDebut: 4, moisFin: 5, note: null },
  ],
  creeLe: '2026-01-10T08:00:00Z',
  majLe: '2026-01-10T08:00:00Z',
};

beforeEach(() => {
  vi.mocked(client.chargerCouvert).mockResolvedValue(COUVERT);
  vi.mocked(client.chargerRotation).mockResolvedValue([]);
  vi.mocked(client.chargerFloraisons).mockResolvedValue([]);
  vi.mocked(client.chargerParcelles).mockResolvedValue([]);
  vi.mocked(client.chargerFiabilite).mockResolvedValue({
    millesime: 2026,
    parcelles: 0,
    verifiees: 0,
    dementies: 0,
    enAttente: 0,
  });
  vi.mocked(client.chargerExposition).mockResolvedValue({
    siteId: 1,
    siteNom: 'Rucher des tilleuls',
    rayonKm: 3,
    declarations: 0,
    derniereDeclaration: null,
    distanceMinM: null,
    sousDelaiRentree: 0,
    zones: [],
  });
  vi.mocked(client.chargerZonesTraitees).mockResolvedValue([]);
  vi.mocked(client.chargerMillesimes).mockResolvedValue([]);
});

afterEach(() => vi.clearAllMocks());

const monter = () =>
  render(
    <LangueProvider>
      <ToastsProvider>
        <DialoguesProvider>
          <PanneauEnvironnement siteId={1} ressources={SITE.ressources} />
        </DialoguesProvider>
      </ToastsProvider>
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
