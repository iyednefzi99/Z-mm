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
    // L'accueil est desormais « Tableaux de bord » : le double doit couvrir ce
    // qu'il charge, sinon l'ecran par defaut ne rend rien et tous les tests de
    // navigation echouent sur le premier `findBy`.
    chargerCalendrier: () => Promise.resolve([]),
    chargerProduction: () => Promise.resolve([]),
    chargerAlertesSanitaires: () => Promise.resolve([]),
    chargerSynthese: () => Promise.resolve(null),
    chargerPrevisions: () => Promise.resolve([]),
    telechargerExport: () => Promise.resolve(),
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

  /** Place le navigateur devant un serveur qui répond « pas de session ». */
  function visiteurSansCompte() {
    // Rien n'apparaît AVANT la réponse du serveur : le navigateur ne détient
    // plus de jeton, il ne peut donc plus savoir seul s'il est connecté
    // (ADR-006). D'où l'attente dans chaque test, et l'écran de chargement
    // intermédiaire, qui évite de faire clignoter la page devant un utilisateur
    // pourtant authentifié.
    reinitialiserSession();
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 401 }));
  }

  it('sert la page d’accueil publique à la racine, sans session', async () => {
    // La vitrine passe avant le formulaire : un visiteur qui découvre Zümm doit
    // pouvoir savoir ce que fait le produit avant qu'on lui demande un compte.
    visiteurSansCompte();

    monter();

    expect(
      await screen.findByRole('heading', { name: 'Toute la ruche, sous les yeux.' }),
    ).toBeInTheDocument();
  });

  it('mène de l’accueil à l’écran d’entrée', async () => {
    visiteurSansCompte();
    monter();
    await screen.findByRole('heading', { name: 'Toute la ruche, sous les yeux.' });

    // Deux boutons portent ce nom — la barre et l'appel du héros. C'est voulu :
    // sur une page longue, l'entrée doit rester à portée sans remonter.
    await userEvent.click(screen.getAllByRole('button', { name: 'Se connecter' })[0]);

    expect(window.location.pathname).toBe('/connexion');
    expect(await screen.findByText('Session requise')).toBeInTheDocument();
  });

  it('ramène de l’écran d’entrée vers l’accueil', async () => {
    allerA('/connexion');
    visiteurSansCompte();
    monter();
    await screen.findByText('Session requise');

    await userEvent.click(screen.getByRole('button', { name: /Retour à l’accueil/ }));

    expect(window.location.pathname).toBe('/accueil');
    expect(
      await screen.findByRole('heading', { name: 'Toute la ruche, sous les yeux.' }),
    ).toBeInTheDocument();
  });

  it('exige une session sur une adresse de la console', async () => {
    // L'accueil est la SEULE page publique : une vue métier demandée sans
    // session mène au formulaire, et l'URL est conservée pour y revenir.
    allerA('/ruches');
    visiteurSansCompte();

    monter();

    expect(await screen.findByText('Session requise')).toBeInTheDocument();
    expect(window.location.pathname).toBe('/ruches');
  });

  it('sert aussi l’accueil à un utilisateur connecté', async () => {
    // La page reste une vitrine, pas un sas : elle ne se referme pas une fois
    // le compte créé. Son appel principal change, lui.
    allerA('/accueil');

    monter();

    expect(
      await screen.findByRole('heading', { name: 'Toute la ruche, sous les yeux.' }),
    ).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: 'Ouvrir la console' })[0]).toBeInTheDocument();
  });

  it('renvoie un utilisateur connecté de l’écran d’entrée vers la console', async () => {
    allerA('/connexion');

    monter();

    await waitFor(() => expect(window.location.pathname).toBe('/'));
    expect(await screen.findByRole('heading', { name: 'Tableaux de bord' })).toBeInTheDocument();
  });

  it('sert l’écran par défaut à la racine', async () => {
    // L'accueil ouvre sur les tableaux de bord et non sur le référentiel :
    // l'apiculteur doit voir l'état de son cheptel avant d'avoir à naviguer
    // (cf. `ONGLET_PAR_DEFAUT`).
    monter();

    expect(await screen.findByRole('heading', { name: 'Tableaux de bord' })).toBeInTheDocument();
  });

  it('sert directement l’écran demandé par l’URL (lien profond)', async () => {
    allerA('/plannings');

    monter();

    expect(await screen.findByRole('heading', { name: 'Plannings' })).toBeInTheDocument();
  });

  it('change l’URL en changeant d’onglet', async () => {
    monter();
    await screen.findByRole('heading', { name: 'Tableaux de bord' });

    await userEvent.click(screen.getByRole('button', { name: 'Sites' }));

    expect(window.location.pathname).toBe('/sites');
    expect(await screen.findByRole('heading', { name: 'Sites' })).toBeInTheDocument();
  });

  it('suit le bouton retour du navigateur', async () => {
    monter();
    await screen.findByRole('heading', { name: 'Tableaux de bord' });
    await userEvent.click(screen.getByRole('button', { name: 'Sites' }));
    await screen.findByRole('heading', { name: 'Sites' });

    window.history.back();

    await waitFor(() => expect(window.location.pathname).toBe('/'));
    expect(await screen.findByRole('heading', { name: 'Tableaux de bord' })).toBeInTheDocument();
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

    await userEvent.click(screen.getByRole('button', { name: 'Revenir à l’accueil' }));

    expect(window.location.pathname).toBe('/');
    expect(await screen.findByRole('heading', { name: 'Tableaux de bord' })).toBeInTheDocument();
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
