import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { AccueilVue } from './AccueilVue';
import { LangueProvider } from '../i18n/langue';
import { ThemeProvider } from '../theme/theme';
import { ONGLETS } from '../routage/routes';
import type { Session } from '../auth/session';

/**
 * Page d'accueil publique (SPRINT-19).
 *
 * <p>Trois propriétés qu'une régression casserait sans faire échouer le reste :
 * la page ne doit RIEN demander au réseau — c'est le seul écran servi sans
 * jeton ; elle doit proposer l'entrée qui correspond à l'état de session ; et
 * elle doit annoncer un nombre d'écrans qui suit réellement la table des routes,
 * plutôt qu'un chiffre recopié qui vieillit.
 */
const SESSION: Session = {
  utilisateur: 'agent-test',
  roles: ['admin'],
  exploitation: 'demo',
} as Session;

const monter = (session: Session | null, onNaviguer = vi.fn()) => {
  render(
    <LangueProvider>
      <ThemeProvider>
        <AccueilVue session={session} onNaviguer={onNaviguer} />
      </ThemeProvider>
    </LangueProvider>,
  );
  return onNaviguer;
};

describe('page d’accueil publique', () => {
  it('présente les fonctionnalités à un visiteur sans compte', async () => {
    monter(null);

    expect(
      screen.getByRole('heading', { name: 'Toute la ruche, sous les yeux.' }),
    ).toBeInTheDocument();
    // Les six domaines mis en avant, et les trois profils : ce sont eux qui
    // répondent à « à quoi sert ce logiciel » avant toute demande de compte.
    for (const titre of ['Pilotage', 'Cheptel', 'Terrain', 'Production', 'Capteurs', 'Hors ligne']) {
      expect(screen.getByRole('heading', { name: titre })).toBeInTheDocument();
    }
    expect(screen.getByRole('heading', { name: 'Agent de terrain' })).toBeInTheDocument();
  });

  it('n’appelle aucune API : elle est servie sans jeton', () => {
    // Un endpoint protégé ici, et la vitrine s'afficherait cassée au premier
    // visiteur — celui qui n'a précisément pas de session.
    const faux = vi.fn();
    vi.stubGlobal('fetch', faux);

    monter(null);

    expect(faux).not.toHaveBeenCalled();
  });

  it('mène le visiteur vers l’écran d’entrée', async () => {
    const naviguer = monter(null);

    // Deux appels portent ce nom — le héros et la clôture de page. C'est voulu :
    // sur une page longue, l'entrée doit rester à portée sans remonter.
    await userEvent.click(screen.getAllByRole('button', { name: 'Créer un compte' })[0]);

    expect(naviguer).toHaveBeenCalledWith('/connexion');
  });

  it('propose la console, et non la connexion, à un utilisateur déjà connecté', async () => {
    const naviguer = monter(SESSION);

    expect(screen.queryByRole('button', { name: 'Se connecter' })).not.toBeInTheDocument();
    await userEvent.click(screen.getAllByRole('button', { name: 'Ouvrir la console' })[0]);

    expect(naviguer).toHaveBeenCalledWith('/');
  });

  it('annonce le nombre d’écrans de la table des routes, et non un chiffre écrit à la main', () => {
    monter(null);

    expect(screen.getByText(`${ONGLETS.length} écrans métier`)).toBeInTheDocument();
  });

  it('se traduit : les fonctionnalités suivent la langue choisie', async () => {
    monter(null);

    await userEvent.click(screen.getByRole('button', { name: 'EN' }));

    // L'anglais est chargé paresseusement (SPRINT-15) : d'où l'attente.
    await waitFor(() =>
      expect(
        screen.getByRole('heading', { name: 'Your whole apiary, at a glance.' }),
      ).toBeInTheDocument(),
    );
    expect(screen.getByRole('heading', { name: 'Offline' })).toBeInTheDocument();
  });
});
