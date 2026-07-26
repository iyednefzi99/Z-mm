import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import App from './App';
import { LangueProvider } from './i18n/langue';
import { ThemeProvider } from './theme/theme';
import { DialoguesProvider } from './ui/dialogues';
import { ToastsProvider } from './ui/toasts';
import { definir, reinitialiserSession } from './auth/session';

/**
 * Tests de l'ossature et du routage (US-051, SPRINT-11).
 *
 * Les vues sont chargées paresseusement : les assertions passent par
 * `findBy…`, le temps que le module de la route arrive.
 */
vi.mock('./api/client', async () => {
  const vide = { lister: () => Promise.resolve([]) };
  return {
    synchroniser: () => Promise.resolve(),
    jetonCsrf: () => null,
    fermiers: vide,
    fermes: vide,
    sites: vide,
    ruches: vide,
    agents: vide,
    plannings: vide,
    ErreurApi: class ErreurApi extends Error {},
  };
});

// Le module d'authentification se reduit desormais a deux navigations (ADR-006) :
// le double le refletant, il n'a plus que trois exports a simuler.
vi.mock('./auth/oidc', () => ({
  demarrerConnexion: vi.fn(),
  deconnexion: vi.fn(),
  consommerRouteDeRetour: () => null,
}));

// Les fournisseurs sont ceux de `main.tsx`, dans le même ordre : la barre
// supérieure porte désormais le sélecteur de thème, qui exige `ThemeProvider`.
const monter = () =>
  render(
    <LangueProvider>
      <ThemeProvider>
        <ToastsProvider>
          <DialoguesProvider>
            <App />
          </DialoguesProvider>
        </ToastsProvider>
      </ThemeProvider>
    </LangueProvider>,
  );

/** Place le navigateur sur un chemin donné avant le montage. */
function allerA(chemin: string) {
  window.history.pushState({}, '', chemin);
}

describe('ossature de la console', () => {
  beforeEach(() => {
    allerA('/');
    definir({ utilisateur: 'agent-test', roles: ['admin'], exploitation: 'demo' });
  });

  it('affiche l’écran de connexion une fois la session connue absente', async () => {
    // L'écran de connexion n'apparaît QU'APRÈS la réponse du serveur : le
    // navigateur ne détient plus de jeton, il ne peut donc plus savoir seul s'il
    // est connecté (ADR-006). D'où l'attente — et d'où l'écran de chargement
    // intermédiaire, qui évite de faire clignoter « Session requise » devant un
    // utilisateur pourtant authentifié.
    reinitialiserSession();
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 401 }));

    monter();

    expect(await screen.findByText('Session requise')).toBeInTheDocument();
  });

  it('sert l’écran par défaut à la racine', async () => {
    monter();

    expect(await screen.findByRole('heading', { name: 'Fermiers' })).toBeInTheDocument();
  });

  it('sert directement l’écran demandé par l’URL (lien profond)', async () => {
    allerA('/plannings');

    monter();

    expect(await screen.findByRole('heading', { name: 'Plannings' })).toBeInTheDocument();
  });

  it('change l’URL en changeant d’onglet', async () => {
    monter();
    await screen.findByRole('heading', { name: 'Fermiers' });

    await userEvent.click(screen.getByRole('button', { name: 'Sites' }));

    expect(window.location.pathname).toBe('/sites');
    expect(await screen.findByRole('heading', { name: 'Sites' })).toBeInTheDocument();
  });

  it('suit le bouton retour du navigateur', async () => {
    monter();
    await screen.findByRole('heading', { name: 'Fermiers' });
    await userEvent.click(screen.getByRole('button', { name: 'Sites' }));
    await screen.findByRole('heading', { name: 'Sites' });

    window.history.back();

    await waitFor(() => expect(window.location.pathname).toBe('/'));
    expect(await screen.findByRole('heading', { name: 'Fermiers' })).toBeInTheDocument();
  });

  it('affiche un écran « page introuvable » sur une URL inconnue', async () => {
    allerA('/ruchers-inconnus');

    monter();

    expect(await screen.findByRole('heading', { name: 'Page introuvable' })).toBeInTheDocument();
  });

  it('ramène à l’accueil depuis l’écran « page introuvable »', async () => {
    allerA('/ruchers-inconnus');
    monter();
    await screen.findByRole('heading', { name: 'Page introuvable' });

    await userEvent.click(screen.getByRole('button', { name: "Revenir a l'accueil" }));

    expect(window.location.pathname).toBe('/');
    expect(await screen.findByRole('heading', { name: 'Fermiers' })).toBeInTheDocument();
  });

  it('marque l’onglet courant pour les lecteurs d’écran', async () => {
    allerA('/carte');

    monter();

    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Carte' })).toHaveAttribute(
        'aria-current',
        'true',
      ),
    );
  });
});
