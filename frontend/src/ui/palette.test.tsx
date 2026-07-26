import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { LangueProvider } from '../i18n/langue';
import { PaletteCommandes, correspond } from './palette';
import { ONGLETS } from '../routage/routes';

/** Palette de commandes (Ctrl/⌘ + K) — filtrage, clavier, choix. */

const monter = (onChoisir = vi.fn(), onFermer = vi.fn()) => {
  render(
    <LangueProvider>
      <PaletteCommandes onChoisir={onChoisir} onFermer={onFermer} />
    </LangueProvider>,
  );
  return { onChoisir, onFermer };
};

describe('correspondance par sous-séquence', () => {
  it('accepte une abréviation, pas seulement une sous-chaîne', () => {
    // C'est tout l'intérêt : `includes` rendrait faux sur les trois premières.
    expect(correspond('lts', 'Lots & origines')).toBe(true);
    expect(correspond('tbx', 'Tableaux de bord')).toBe(true);
    expect(correspond('cptr', 'Capteurs')).toBe(true);
    expect(correspond('capteurs', 'Capteurs')).toBe(true);
  });

  it('ignore la casse et les diacritiques', () => {
    expect(correspond('RECOLTES', 'Récoltes')).toBe(true);
    expect(correspond('recoltes', 'Récoltes')).toBe(true);
  });

  it('rejette une lettre absente ou hors ordre', () => {
    expect(correspond('z', 'Capteurs')).toBe(false);
    expect(correspond('sruetpac', 'Capteurs')).toBe(false);
  });

  it('accepte tout sur une requête vide — la palette s’ouvre pleine', () => {
    expect(correspond('', 'Capteurs')).toBe(true);
    expect(correspond('   ', 'Capteurs')).toBe(true);
  });
});

describe('palette de commandes', () => {
  it('s’ouvre sur les seize écrans plutôt que sur le vide', () => {
    monter();

    expect(screen.getAllByRole('option')).toHaveLength(ONGLETS.length);
  });

  it('filtre à la frappe', async () => {
    monter();

    await userEvent.type(screen.getByRole('combobox'), 'reco');

    const options = screen.getAllByRole('option');
    expect(options).toHaveLength(1);
    expect(options[0]).toHaveTextContent('Récoltes');
  });

  it('sort une famille entière quand on tape son nom', async () => {
    monter();

    await userEvent.type(screen.getByRole('combobox'), 'terrain');

    // Plannings, Visites, Tâches, Carte : aucun de ces libellés ne contient
    // « terrain ». C'est la famille qui les ramène.
    expect(screen.getAllByRole('option')).toHaveLength(4);
  });

  it('ouvre l’écran sélectionné au clavier', async () => {
    const { onChoisir } = monter();

    await userEvent.type(screen.getByRole('combobox'), 'capteurs');
    await userEvent.keyboard('{Enter}');

    expect(onChoisir).toHaveBeenCalledWith('capteurs');
  });

  it('fait boucler le curseur au lieu de buter en silence', async () => {
    monter();

    await userEvent.type(screen.getByRole('combobox'), 'lots');
    // Un seul résultat : descendre puis remonter doit y revenir, pas se perdre.
    await userEvent.keyboard('{ArrowDown}{ArrowUp}');

    expect(screen.getAllByRole('option')[0]).toHaveAttribute('aria-selected', 'true');
  });

  it('ferme sur Échap', async () => {
    const { onFermer } = monter();

    await userEvent.keyboard('{Escape}');

    expect(onFermer).toHaveBeenCalled();
  });

  it('annonce l’option courante au lecteur d’écran', async () => {
    monter();

    await userEvent.type(screen.getByRole('combobox'), 'carte');

    expect(screen.getByRole('combobox')).toHaveAttribute(
      'aria-activedescendant',
      'z-palette-carte',
    );
  });

  it('ne laisse pas l’utilisateur devant un cul-de-sac muet', async () => {
    monter();

    await userEvent.type(screen.getByRole('combobox'), 'zzzz');

    expect(screen.queryAllByRole('option')).toHaveLength(0);
    expect(screen.getByText('Aucun écran ne correspond.')).toBeInTheDocument();
  });
});
