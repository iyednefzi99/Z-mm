import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { CompteVue } from './CompteVue';
import { PermissionsVue, MATRICE } from './PermissionsVue';
import { RecuperationVue } from './RecuperationVue';
import { LangueProvider } from '../i18n/langue';
import { ROLES_ONGLET } from '../routage/routes';
import type { Session } from '../auth/session';

/**
 * Compte, récupération et matrice des permissions (SPRINT-19, lot 4).
 *
 * <p>Le test qui compte vraiment ici est celui de la DÉRIVE : la matrice
 * affichée recopie une règle qui vit côté serveur, sans endpoint pour la
 * publier. Elle ne peut pas se vérifier toute seule, mais elle peut au moins
 * refuser de se taire sur un écran que le front déclare réservé.
 */
vi.mock('../api/client', () => ({ jetonCsrf: () => 'jeton-de-test' }));
vi.mock('../auth/oidc', () => ({ deconnexion: vi.fn() }));

const session: Session = {
  utilisateur: 'nour.bensalah',
  roles: ['apiculteur'],
  exploitation: 'exploitation-demo',
};

const monter = (vue: React.ReactElement) => render(<LangueProvider>{vue}</LangueProvider>);

describe('matrice des permissions', () => {
  it('couvre chaque écran que le front déclare réservé', () => {
    // Le garde-fou contre la dérive : ajouter un écran à `ROLES_ONGLET` sans le
    // documenter ici laisserait un utilisateur devant une porte fermée que rien
    // n'explique.
    const documentees = MATRICE.map((regle) => regle.cle as string);

    for (const onglet of Object.keys(ROLES_ONGLET)) {
      expect(documentees).toContain(onglet);
    }
  });

  it('nomme les écrans retirés de la navigation, sans les réécrire', () => {
    monter(<PermissionsVue />);

    expect(screen.getByText(/Audit, Invitations, Permissions/)).toBeInTheDocument();
  });

  it('rappelle que le masquage n’est pas la protection', () => {
    monter(<PermissionsVue />);

    expect(screen.getByText(/c’est le serveur qui refuse/)).toBeInTheDocument();
  });

  it('ne propose rien à modifier', () => {
    // Écran de consultation : ni bouton « Nouveau », ni formulaire.
    monter(<PermissionsVue />);

    expect(screen.queryByRole('button', { name: /Nouveau/ })).toBeNull();
    expect(document.querySelector('form')).toBeNull();
  });
});

describe('écran mon compte', () => {
  it('affiche ce que le serveur dit de la session', () => {
    monter(<CompteVue session={session} onNaviguer={vi.fn()} />);

    expect(screen.getByText('nour.bensalah')).toBeInTheDocument();
    expect(screen.getByText('exploitation-demo')).toBeInTheDocument();
    expect(screen.getByText('Apiculteur')).toBeInTheDocument();
  });

  it('ne propose aucun changement de mot de passe', () => {
    // Le BFF n'expose que la connexion et l'inscription : un formulaire ici
    // n'aboutirait nulle part, et l'utilisateur croirait avoir changé son mot
    // de passe.
    monter(<CompteVue session={session} onNaviguer={vi.fn()} />);

    expect(document.querySelector('input[type="password"]')).toBeNull();
    expect(document.querySelector('form')).toBeNull();
  });

  it('renvoie vers la page de récupération plutôt que de la répéter', async () => {
    const naviguer = vi.fn();
    monter(<CompteVue session={session} onNaviguer={naviguer} />);

    await userEvent.click(screen.getByRole('button', { name: 'Mot de passe oublié' }));

    expect(naviguer).toHaveBeenCalledWith('/recuperation');
  });
});

describe('page mot de passe oublié', () => {
  it('avoue que l’envoi de courriel n’est pas configuré', () => {
    monter(<RecuperationVue />);

    expect(screen.getByText(/aucun serveur d’envoi n’est configuré/)).toBeInTheDocument();
  });

  it('nomme le seul chemin qui fonctionne aujourd’hui', () => {
    monter(<RecuperationVue />);

    expect(
      screen.getByRole('heading', { name: 'Passez par votre responsable' }),
    ).toBeInTheDocument();
  });
});
