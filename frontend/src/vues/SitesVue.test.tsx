import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { definir, reinitialiserSession } from '../auth/session';
import { LangueProvider } from '../i18n/langue';
import { DialoguesProvider } from '../ui/dialogues';
import { SitesVue } from './SitesVue';
import type { Site } from '../api/types';

/**
 * Tests de la vue Sites (US-003) et de ses voisins les plus proches (US-046),
 * SPRINT-10. Le client d'API est simulé : ce qui est vérifié ici est le
 * comportement de l'interface, pas celui du serveur — déjà couvert par les tests
 * d'intégration `CartographieTourneeIT`.
 */
vi.mock('../api/client', () => ({
  sites: {
    lister: vi.fn(),
    creer: vi.fn(),
    mettreAJour: vi.fn(),
    supprimer: vi.fn(),
  },
  fermes: { lister: vi.fn() },
  agents: { lister: vi.fn() },
  voisinsSite: vi.fn(),
  emplacementsSite: vi.fn(),
  demenagerSite: vi.fn(),
  listerTransports: vi.fn(),
  planifierTransport: vi.fn(),
  realiserTransport: vi.fn(),
  annulerTransport: vi.fn(),
  comparerSites: vi.fn(),
  emporterRucher: vi.fn(),
  ErreurApi: class ErreurApi extends Error {},
}));

const {
  agents,
  demenagerSite,
  emplacementsSite,
  emporterRucher,
  fermes,
  listerTransports,
  sites,
  voisinsSite,
} = await import('../api/client');

const site = (id: number, nom: string, latitude: number, longitude: number): Site => ({
  id,
  nom,
  fermeId: 1,
  fermeNom: 'Ferme du Causse',
  latitude,
  longitude,
  altitude: null,
  dateMiseEnOeuvre: '2026-04-01',
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
  creeLe: '2026-04-01T08:00:00Z',
  majLe: '2026-04-01T08:00:00Z',
});

const RUCHER = site(1, 'Rucher du Lot', 44.447, 1.441);
const VOISIN = site(2, 'Rucher du Causse', 44.45, 1.45);

const monter = () =>
  render(
    <LangueProvider>
      <DialoguesProvider>
        <SitesVue />
      </DialoguesProvider>
    </LangueProvider>,
  );

describe('vue Sites', () => {
  beforeEach(() => {
    vi.mocked(sites.lister).mockResolvedValue([RUCHER]);
    vi.mocked(fermes.lister).mockResolvedValue([]);
    vi.mocked(voisinsSite).mockResolvedValue([]);
    vi.mocked(emplacementsSite).mockResolvedValue([]);
    vi.mocked(agents.lister).mockResolvedValue([]);
    vi.mocked(listerTransports).mockResolvedValue([]);
    // Le referentiel ne s'ecrit qu'avec `responsable` ou `admin` : sans session,
    // les commandes d'ecriture ne sont plus rendues du tout (SPRINT-19).
    definir({ utilisateur: 'lea', roles: ['responsable'], exploitation: 'demo' });
  });

  afterEach(() => reinitialiserSession());

  it('affiche les sites avec leurs coordonnées à quatre décimales', async () => {
    monter();

    expect(await screen.findByText('Rucher du Lot')).toBeInTheDocument();
    expect(screen.getByText('44.4470')).toBeInTheDocument();
    expect(screen.getByText('1.4410')).toBeInTheDocument();
  });

  it('offre une action sur liste vide, plutôt qu’un constat', async () => {
    vi.mocked(sites.lister).mockResolvedValue([]);

    monter();

    // Une liste vide n'est pas une erreur : pour un nouvel utilisateur, c'est le
    // PREMIER écran. Il doit donc porter le moyen d'en sortir, et pas seulement
    // annoncer qu'il n'y a rien — l'ancienne phrase « Aucun élément pour le
    // moment » laissait devant un cul-de-sac.
    expect(await screen.findByText('Rien à afficher pour l’instant')).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: '+ Nouveau' }).length).toBeGreaterThan(0);
  });

  it('affiche un message d’erreur exploitable quand l’API tombe', async () => {
    vi.mocked(sites.lister).mockRejectedValue(new Error('réseau'));

    monter();

    expect(await screen.findByRole('alert')).toHaveTextContent('Service indisponible');
  });

  it('demande les trois plus proches voisins et affiche leur distance en km (US-046)', async () => {
    vi.mocked(voisinsSite).mockResolvedValue([{ site: VOISIN, distanceMetres: 1573.4 }]);
    monter();
    await screen.findByText('Rucher du Lot');

    await userEvent.click(screen.getByRole('button', { name: 'Voir les voisins' }));

    expect(voisinsSite).toHaveBeenCalledWith(1, 3);
    expect(await screen.findByText('Rucher du Causse')).toBeInTheDocument();
    // 1573,4 m rendus par le formatage français (US-053) : virgule décimale.
    expect(screen.getByText(/1,57 km/)).toBeInTheDocument();
  });

  it('annonce l’absence de voisin plutôt qu’une liste vide silencieuse', async () => {
    monter();
    await screen.findByText('Rucher du Lot');

    await userEvent.click(screen.getByRole('button', { name: 'Voir les voisins' }));

    expect(await screen.findByText('Aucun autre site géolocalisé.')).toBeInTheDocument();
  });

  it('ouvre le formulaire de création avec des champs vides', async () => {
    monter();
    await screen.findByText('Rucher du Lot');

    await userEvent.click(screen.getByRole('button', { name: '+ Nouveau' }));

    await waitFor(() => expect(screen.getByLabelText(/Nom/)).toHaveValue(''));
    expect(screen.getByLabelText(/Latitude/)).toHaveValue(null);
  });

  it('pré-remplit le formulaire avec le site à modifier', async () => {
    monter();
    await screen.findByText('Rucher du Lot');

    await userEvent.click(screen.getByRole('button', { name: 'Modifier' }));

    expect(await screen.findByLabelText(/Nom/)).toHaveValue('Rucher du Lot');
    expect(screen.getByLabelText(/Latitude/)).toHaveValue(44.447);
  });
});

describe('vue Sites — terrain (SPRINT-21)', () => {
  const RUCHER_SITUE: Site = {
    ...RUCHER,
    adresseRue: '12 chemin des Vignes',
    codePostal: '46100',
    ville: 'Figeac',
    pays: 'FR',
    typeSite: 'transhumance',
    exposition: 'sud_est',
    ressources: [
      { id: 5, ressource: 'tilleul', distanceM: 800, moisDebut: 6, moisFin: 7, note: null },
    ],
  };

  beforeEach(() => {
    vi.mocked(sites.lister).mockResolvedValue([RUCHER_SITUE]);
    vi.mocked(fermes.lister).mockResolvedValue([]);
    vi.mocked(voisinsSite).mockResolvedValue([]);
    vi.mocked(emplacementsSite).mockResolvedValue([]);
    vi.mocked(agents.lister).mockResolvedValue([]);
    vi.mocked(listerTransports).mockResolvedValue([]);
    definir({ utilisateur: 'lea', roles: ['responsable'], exploitation: 'demo' });
  });

  afterEach(() => reinitialiserSession());

  it('montre la commune et le type de rucher dans la liste', async () => {
    monter();

    expect(await screen.findByText('Figeac')).toBeInTheDocument();
    expect(screen.getByText('Transhumance')).toBeInTheDocument();
  });

  it('pré-remplit l’adresse et les ressources déjà déclarées', async () => {
    monter();
    await screen.findByText('Rucher du Lot');

    await userEvent.click(screen.getByRole('button', { name: 'Modifier' }));

    expect(await screen.findByLabelText(/Adresse/)).toHaveValue('12 chemin des Vignes');
    expect(screen.getByLabelText(/Commune/)).toHaveValue('Figeac');
    // « Tilleul » figure deux fois dans le formulaire — une fois comme ressource
    // DÉCLARÉE, une fois comme option offerte à l'ajout. C'est la première qu'on
    // vérifie : la liste des déclarées est la seule qui parte au serveur.
    const declarees = within(screen.getByRole('list')).getByText('Tilleul');
    expect(declarees).toBeInTheDocument();
  });

  it('lit l’historique d’emplacement et distingue la période courante', async () => {
    vi.mocked(emplacementsSite).mockResolvedValue([
      {
        id: 2,
        siteId: 1,
        latitude: 44.447,
        longitude: 1.441,
        altitude: null,
        dateDebut: '2026-03-01',
        dateFin: null,
        motif: 'miellee',
        note: null,
        courant: true,
      },
      {
        id: 1,
        siteId: 1,
        latitude: 43.9,
        longitude: 1.2,
        altitude: null,
        dateDebut: '2025-04-02',
        dateFin: '2026-03-01',
        motif: 'installation',
        note: null,
        courant: false,
      },
    ]);
    monter();
    await screen.findByText('Rucher du Lot');

    await userEvent.click(screen.getByRole('button', { name: 'Historique' }));

    expect(emplacementsSite).toHaveBeenCalledWith(1);
    // Une seule période est ouverte : un rucher n'est jamais à deux endroits.
    expect(await screen.findByText('Emplacement actuel')).toBeInTheDocument();
    expect(screen.getByText(/Miellée/)).toBeInTheDocument();
  });

  it('déménage par une route dédiée, sans passer par la mise à jour', async () => {
    vi.mocked(demenagerSite).mockResolvedValue(RUCHER_SITUE);
    monter();
    await screen.findByText('Rucher du Lot');

    await userEvent.click(screen.getByRole('button', { name: 'Historique' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Déménager' }));
    await userEvent.type(screen.getByLabelText(/Depuis/), '2026-05-15');
    await userEvent.click(screen.getAllByRole('button', { name: 'Déménager' })[0]);

    await waitFor(() => expect(demenagerSite).toHaveBeenCalled());
    // Corriger une position mal saisie et déplacer un rucher touchent aux mêmes
    // colonnes sans dire la même chose : seule la seconde écrit l'historique.
    expect(sites.mettreAJour).not.toHaveBeenCalled();
    expect(vi.mocked(demenagerSite).mock.calls[0][1]).toMatchObject({
      dateDebut: '2026-05-15',
      motif: 'transhumance',
    });
  });
});

describe('vue Sites — rôle sans droit d’écriture', () => {
  beforeEach(() => {
    vi.mocked(sites.lister).mockResolvedValue([RUCHER]);
    vi.mocked(fermes.lister).mockResolvedValue([]);
    vi.mocked(voisinsSite).mockResolvedValue([]);
    vi.mocked(emplacementsSite).mockResolvedValue([]);
    vi.mocked(agents.lister).mockResolvedValue([]);
    vi.mocked(listerTransports).mockResolvedValue([]);
    definir({ utilisateur: 'noe', roles: ['apiculteur'], exploitation: 'demo' });
  });

  afterEach(() => reinitialiserSession());

  it('garde la liste mais retire « Nouveau », « Modifier » et « Supprimer »', async () => {
    // Le serveur refuserait les trois en 403 (`SecurityConfig.matriceRbac`).
    // L'écran reste : un apiculteur a le droit de consulter le référentiel.
    monter();

    expect(await screen.findByText('Rucher du Lot')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '+ Nouveau' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Modifier' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Supprimer' })).not.toBeInTheDocument();
  });
});

/**
 * Emport hors ligne depuis l'ecran des ruchers (SPRINT-24, lot C).
 *
 * <p>C'est l'entree principale de la fonction : on emporte le rucher ou l'on
 * va, donc depuis la liste des ruchers. L'ecran « Hors ligne » rend compte de ce
 * qui est emporte, il n'est pas le seul endroit d'ou l'on emporte.
 */
describe('emport hors ligne (SPRINT-24)', () => {
  beforeEach(() => {
    vi.mocked(sites.lister).mockResolvedValue([site(1, 'Rucher du causse', 44.4, 1.4)]);
    vi.mocked(fermes.lister).mockResolvedValue([]);
    vi.mocked(agents.lister).mockResolvedValue([]);
    vi.mocked(listerTransports).mockResolvedValue([]);
    vi.mocked(emplacementsSite).mockResolvedValue([]);
    definir({ utilisateur: 'lea', roles: ['responsable'], exploitation: 'demo' });
  });

  it('emporte le rucher et affiche l’instant du prélèvement', async () => {
    vi.mocked(emporterRucher).mockResolvedValue({
      preleveLe: '2026-09-02T12:12:00Z',
      site: site(1, 'Rucher du causse', 44.4, 1.4),
      ruches: [],
      dernieresVisites: [],
      tachesOuvertes: [],
      sousCarence: [],
    });

    monter();
    await userEvent.click(await screen.findByRole('button', { name: 'Emporter' }));

    expect(emporterRucher).toHaveBeenCalledWith(1);
    // La date du prélèvement est annoncée dès l'emport : c'est elle qui
    // empêchera plus tard une donnée du disque de passer pour fraîche.
    expect(await screen.findByText(/Rucher du causse emporté/)).toBeInTheDocument();
    expect(JSON.parse(localStorage.getItem('zumm.emports') ?? '[]')).toHaveLength(1);
  });

  it('dit qu’emporter demande du réseau quand l’appel échoue', async () => {
    vi.mocked(emporterRucher).mockRejectedValue(new Error('hors ligne'));

    monter();
    await userEvent.click(await screen.findByRole('button', { name: 'Emporter' }));

    // La seule fonction du produit dont l'échec hors ligne est normal.
    expect(await screen.findByText(/Emporter demande du réseau/)).toBeInTheDocument();
  });
});
