import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { LangueProvider } from '../i18n/langue';
import { SelecteurTheme, ThemeProvider } from './theme';

/**
 * Bascule de thème (clair / sombre / système).
 *
 * <p>Ce que ces tests protègent : `theme/tokens.css` déclarait
 * `:root[data-theme='dark']` et `[data-theme='light']` depuis le SPRINT-13, et
 * **rien ne posait jamais l'attribut**. La moitié de la couche de jetons était
 * donc du code mort, et le thème suivait le système sans recours possible.
 */

const monter = () =>
  render(
    <LangueProvider>
      <ThemeProvider>
        <SelecteurTheme />
      </ThemeProvider>
    </LangueProvider>,
  );

beforeEach(() => {
  localStorage.clear();
  document.documentElement.removeAttribute('data-theme');
  vi.stubGlobal(
    'matchMedia',
    vi.fn().mockImplementation((requete: string) => ({
      matches: false,
      media: requete,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      addListener: vi.fn(),
      removeListener: vi.fn(),
      dispatchEvent: vi.fn(),
      onchange: null,
    })),
  );
});

afterEach(() => {
  vi.unstubAllGlobals();
  localStorage.clear();
});

describe('SelecteurTheme', () => {
  it("laisse la main au système par défaut : aucun attribut n'est posé", () => {
    monter();

    expect(document.documentElement.hasAttribute('data-theme')).toBe(false);
    expect(screen.getByRole('button', { name: 'Thème du système' })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
  });

  it('pose data-theme et le rend persistant quand on choisit le mode sombre', async () => {
    const utilisateur = userEvent.setup();
    monter();

    await utilisateur.click(screen.getByRole('button', { name: 'Thème sombre' }));

    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
    expect(localStorage.getItem('zumm.theme')).toBe('sombre');
  });

  it('fige le mode clair, y compris si le système est en sombre', async () => {
    const utilisateur = userEvent.setup();
    monter();

    await utilisateur.click(screen.getByRole('button', { name: 'Thème clair' }));

    // Le point : un apiculteur en plein soleil doit pouvoir forcer le clair
    // pendant que son téléphone est réglé en sombre.
    expect(document.documentElement.getAttribute('data-theme')).toBe('light');
  });

  it("revient au réglage système et retire l'attribut", async () => {
    const utilisateur = userEvent.setup();
    monter();

    await utilisateur.click(screen.getByRole('button', { name: 'Thème sombre' }));
    await utilisateur.click(screen.getByRole('button', { name: 'Thème du système' }));

    // Retirer l'attribut, et non le poser à « auto » : les jetons ne connaissent
    // que `light` et `dark`, la préférence système reprend par la media query.
    expect(document.documentElement.hasAttribute('data-theme')).toBe(false);
  });

  it('relit la préférence enregistrée au démarrage', () => {
    localStorage.setItem('zumm.theme', 'sombre');

    monter();

    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
  });

  it('chaque option porte un nom accessible — le symbole seul ne se lit pas', () => {
    monter();

    for (const nom of ['Thème du système', 'Thème clair', 'Thème sombre']) {
      expect(screen.getByRole('button', { name: nom })).toBeInTheDocument();
    }
  });

  it('suit une bascule du système survenue pendant la session, en mode auto', async () => {
    // Cas courant, et pourtant facile à manquer : un téléphone passe en sombre au
    // coucher du soleil, application ouverte. Le CSS suit tout seul —
    // `prefers-color-scheme` est une requête média vivante — mais la balise
    // `theme-color` ne se réévalue pas. Sans écouteur, la page devient sombre et
    // la barre du navigateur reste claire.
    const meta = document.createElement('meta');
    meta.setAttribute('name', 'theme-color');
    document.head.appendChild(meta);

    let notifier: (() => void) | null = null;
    let sombre = false;
    vi.stubGlobal(
      'matchMedia',
      vi.fn().mockImplementation((requete: string) => ({
        get matches() {
          return sombre;
        },
        media: requete,
        addEventListener: (_: string, ecouteur: () => void) => {
          notifier = ecouteur;
        },
        removeEventListener: vi.fn(),
        addListener: vi.fn(),
        removeListener: vi.fn(),
        dispatchEvent: vi.fn(),
        onchange: null,
      })),
    );

    monter();
    expect(meta.getAttribute('content')).toBe('#f5f7f5');

    // Le système bascule ; l'application est restée en « auto ».
    sombre = true;
    expect(notifier).not.toBeNull();
    await act(async () => notifier?.());

    expect(meta.getAttribute('content')).toBe('#25423b');
    meta.remove();
  });
});
