import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ConnexionVue } from './ConnexionVue';
import { LangueProvider } from '../i18n/langue';
import { reinitialiserSession } from '../auth/session';

/**
 * Écran d'entrée — connexion et création de compte (ADR-009).
 *
 * <p>Ce qui est verifie ici tient a trois proprietes qu'une regression
 * casserait sans faire echouer le reste : le formulaire ne doit pas VIDER ce que
 * l'utilisateur a saisi quand le serveur refuse, il ne doit pas REVELER si un
 * compte existe, et il ne doit pas laisser partir deux requetes pour un double
 * clic sur le bouton.
 */
vi.mock('../api/client', () => ({ jetonCsrf: () => 'jeton-de-test' }));
vi.mock('../auth/oidc', () => ({ demarrerConnexion: vi.fn() }));

const monter = () =>
  render(
    <LangueProvider>
      <ConnexionVue />
    </LangueProvider>,
  );

/** Double de `fetch` : une reponse par appel, dans l'ordre. */
function repondre(...reponses: Array<{ ok: boolean; status?: number; corps?: unknown }>) {
  const appels: string[] = [];
  const faux = vi.fn((url: string, options?: RequestInit) => {
    appels.push(url);
    void options;
    const reponse = reponses.shift() ?? { ok: true, corps: {} };
    return Promise.resolve({
      ok: reponse.ok,
      status: reponse.status ?? (reponse.ok ? 200 : 401),
      json: () => Promise.resolve(reponse.corps ?? {}),
    } as Response);
  });
  vi.stubGlobal('fetch', faux);
  return { appels, faux };
}

describe("écran d'entrée", () => {
  beforeEach(() => {
    reinitialiserSession();
    vi.unstubAllGlobals();
  });

  it('poste les identifiants sur le BFF, sans jamais les mettre dans l’URL', async () => {
    const { appels, faux } = repondre(
      { ok: true },
      { ok: true, corps: { utilisateur: 'zoubeir', roles: ['apiculteur'], exploitation: 't1' } },
    );
    monter();

    await userEvent.type(screen.getByLabelText('Identifiant ou courriel'), 'zoubeir');
    await userEvent.type(screen.getByLabelText('Mot de passe'), 'ruche-sans-fin-2026');
    await userEvent.click(screen.getByRole('button', { name: 'Se connecter' }));

    expect(appels[0]).toBe('/bff/connexion');
    // Le mot de passe part dans le corps, jamais dans la ligne de requete : une
    // URL se retrouve dans les journaux du proxy et l'historique du navigateur.
    const options = faux.mock.calls[0][1] as RequestInit;
    expect(options.method).toBe('POST');
    expect(String(options.body)).toContain('ruche-sans-fin-2026');
    expect(appels[0]).not.toContain('ruche-sans-fin-2026');
    // La session est relue par le canal normal apres la pose du cookie.
    expect(appels[1]).toBe('/bff/session');
  });

  it('garde la saisie et reste muet sur l’existence du compte quand le serveur refuse', async () => {
    repondre({ ok: false, status: 401, corps: { code: 'identifiants-invalides' } });
    monter();

    const identifiant = screen.getByLabelText('Identifiant ou courriel');
    await userEvent.type(identifiant, 'zoubeir');
    await userEvent.type(screen.getByLabelText('Mot de passe'), 'faux');
    await userEvent.click(screen.getByRole('button', { name: 'Se connecter' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Identifiant ou mot de passe incorrect.',
    );
    // Retaper son identifiant parce que le mot de passe etait faux est une
    // punition, pas une protection.
    expect(identifiant).toHaveValue('zoubeir');
    // Le message ne distingue pas « compte inconnu » de « mot de passe faux ».
    expect(screen.queryByText(/inconnu|existe/i)).toBeNull();
  });

  it("demande le code d'exploitation à l'inscription, et le remonte au serveur", async () => {
    const { faux } = repondre(
      { ok: true },
      { ok: true },
      { ok: true, corps: { utilisateur: 'nour', roles: ['apiculteur'], exploitation: 't1' } },
    );
    monter();

    await userEvent.click(screen.getByRole('tab', { name: 'Créer un compte' }));
    await userEvent.type(screen.getByLabelText('Nom complet'), 'Nour Ben Salah');
    await userEvent.type(screen.getByLabelText('Adresse électronique'), 'nour@example.tn');
    await userEvent.type(screen.getByLabelText('Mot de passe'), 'ruche-sans-fin-2026');
    // Le code porte le tenant_id : sans lui, le compte serait cree puis refuse
    // par TenantFilter — un compte fantome.
    await userEvent.type(screen.getByLabelText('Code d’exploitation'), 'zm-4712');
    await userEvent.click(screen.getByRole('button', { name: 'Créer mon compte' }));

    const corps = String((faux.mock.calls[0][1] as RequestInit).body);
    expect(faux.mock.calls[0][0]).toBe('/bff/inscription');
    // Saisi en minuscules, envoye en majuscules : le code est lu sur un papier.
    expect(corps).toContain('ZM-4712');
  });

  it('traduit le refus du code sans effacer le formulaire', async () => {
    repondre({ ok: false, status: 422, corps: { code: 'code-inconnu' } });
    monter();

    await userEvent.click(screen.getByRole('tab', { name: 'Créer un compte' }));
    await userEvent.type(screen.getByLabelText('Nom complet'), 'Nour');
    await userEvent.type(screen.getByLabelText('Adresse électronique'), 'nour@example.tn');
    await userEvent.type(screen.getByLabelText('Mot de passe'), 'ruche-sans-fin-2026');
    const code = screen.getByLabelText('Code d’exploitation');
    await userEvent.type(code, 'ZM-0000');
    await userEvent.click(screen.getByRole('button', { name: 'Créer mon compte' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Ce code d’exploitation est inconnu ou expiré.',
    );
    expect(code).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByLabelText('Nom complet')).toHaveValue('Nour');
  });

  it('bascule l’affichage du mot de passe sans changer sa valeur', async () => {
    repondre();
    monter();

    const champ = screen.getByLabelText('Mot de passe');
    await userEvent.type(champ, 'ruche-sans-fin-2026');
    expect(champ).toHaveAttribute('type', 'password');

    await userEvent.click(screen.getByRole('button', { name: 'Afficher le mot de passe' }));
    expect(champ).toHaveAttribute('type', 'text');
    expect(champ).toHaveValue('ruche-sans-fin-2026');
  });

  it('annonce un service indisponible plutôt qu’un refus quand le réseau tombe', async () => {
    // Un serveur injoignable n'est pas un refus : dire « identifiants
    // invalides » enverrait l'utilisateur retaper un mot de passe correct.
    vi.stubGlobal('fetch', vi.fn(() => Promise.reject(new Error('hors ligne'))));
    monter();

    await userEvent.type(screen.getByLabelText('Identifiant ou courriel'), 'zoubeir');
    await userEvent.type(screen.getByLabelText('Mot de passe'), 'ruche-sans-fin-2026');
    await userEvent.click(screen.getByRole('button', { name: 'Se connecter' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Service momentanément indisponible. Réessayez.',
    );
  });
});
