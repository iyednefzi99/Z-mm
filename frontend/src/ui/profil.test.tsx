import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { MenuProfil } from './profil';
import { LangueProvider } from '../i18n/langue';
import type { Session } from '../auth/session';

/**
 * Menu de profil de la barre superieure.
 *
 * <p>Ce qui est verifie : que l'exploitation et les roles soient bien LA, et
 * qu'ils ne s'affichent qu'a l'ouverture. La barre superieure ne les montrait
 * nulle part, et c'est l'information dont l'absence coute le plus cher sur un
 * produit multi-exploitation.
 */
vi.mock('../api/client', () => ({ jetonCsrf: () => 'jeton-de-test' }));
const deconnexion = vi.fn();
vi.mock('../auth/oidc', () => ({ deconnexion: (...args: unknown[]) => deconnexion(...args) }));

const session: Session = {
  utilisateur: 'nour.bensalah',
  roles: ['apiculteur', 'superviseur'],
  exploitation: 'exploitation-demo',
};

const monter = (donnees: Session = session) =>
  render(
    <LangueProvider>
      <MenuProfil session={donnees} />
    </LangueProvider>,
  );

describe('menu de profil', () => {
  it('montre le nom, et rien de plus tant qu’il est fermé', () => {
    monter();

    expect(screen.getByText('nour.bensalah')).toBeInTheDocument();
    // L'exploitation est une information de contexte, pas un ornement de barre :
    // elle vit dans le menu, qui est fermé au départ.
    expect(screen.queryByText('exploitation-demo')).toBeNull();
    expect(screen.getByRole('button')).toHaveAttribute('aria-expanded', 'false');
  });

  it('ouvre le menu sur l’exploitation et les rôles traduits', async () => {
    monter();

    await userEvent.click(screen.getByRole('button'));

    expect(screen.getByText('exploitation-demo')).toBeInTheDocument();
    // Les rôles expliquent l'interface : un apiculteur qui ne voit pas l'écran
    // des invitations doit pouvoir comprendre pourquoi sans appeler personne.
    expect(screen.getByText(/Apiculteur/i)).toBeInTheDocument();
  });

  it('se ferme sur Échap', async () => {
    monter();

    const declencheur = screen.getByRole('button');
    await userEvent.click(declencheur);
    expect(declencheur).toHaveAttribute('aria-expanded', 'true');

    await userEvent.keyboard('{Escape}');
    expect(declencheur).toHaveAttribute('aria-expanded', 'false');
  });

  it('dit « aucun rôle » plutôt que rien quand le compte n’en porte pas', async () => {
    // Un compte sans role metier atteint la console mais aucun endpoint : le
    // silence laisserait l'utilisateur devant une interface inexplicablement
    // vide.
    monter({ ...session, roles: [] });

    await userEvent.click(screen.getByRole('button'));

    expect(screen.getByText('Aucun rôle métier')).toBeInTheDocument();
  });

  it('déconnecte en passant le jeton CSRF', async () => {
    monter();

    await userEvent.click(screen.getByRole('button'));
    await userEvent.click(screen.getByRole('menuitem'));

    // Sans jeton, une deconnexion forcee depuis un site tiers reste possible.
    expect(deconnexion).toHaveBeenCalledWith('jeton-de-test');
  });
});
