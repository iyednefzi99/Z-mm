import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { LangueProvider } from '../i18n/langue';
import { PaletteCommandes, correspond } from './palette';
import { ONGLETS } from '../routage/routes';

// La palette interroge le serveur depuis le SPRINT-21 : sans ce doublon, chaque
// frappe partirait vers un `fetch` que jsdom n'a pas, et l'echec arriverait
// APRES la fin du test.
vi.mock('../api/client', () => ({ rechercher: vi.fn() }));

const { rechercher } = await import('../api/client');

/** Palette de commandes (Ctrl/⌘ + K) — filtrage, clavier, choix. */

/** Roles par defaut : un responsable, qui ouvre les dix-neuf ecrans. */
const monter = (
  onChoisir = vi.fn(),
  onFermer = vi.fn(),
  roles: readonly string[] = ['responsable'],
) => {
  render(
    <LangueProvider>
      <PaletteCommandes onChoisir={onChoisir} onFermer={onFermer} roles={roles} />
    </LangueProvider>,
  );
  return { onChoisir, onFermer };
};

beforeEach(() => {
  vi.mocked(rechercher).mockResolvedValue([]);
});

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
  it('s’ouvre sur les dix-neuf écrans plutôt que sur le vide', () => {
    monter();

    expect(screen.getAllByRole('option')).toHaveLength(ONGLETS.length);
  });

  it('cache les écrans que le rôle n’ouvre pas', () => {
    // La palette doit voir EXACTEMENT ce que voit le rail. Un accélérateur qui
    // atteint un écran masqué ne raccourcit pas la navigation, il la contourne
    // — et mène droit au refus que le masquage évitait.
    monter(vi.fn(), vi.fn(), ['apiculteur']);

    // Quatre depuis le SPRINT-27 : la comptabilite a rejoint les ecrans que le
    // serveur refuse a un role de terrain.
    expect(screen.getAllByRole('option')).toHaveLength(ONGLETS.length - 4);
    expect(screen.queryByText('Audit')).toBeNull();
    expect(screen.queryByText('Invitations')).toBeNull();
    expect(screen.queryByText('Permissions')).toBeNull();
    expect(screen.queryByText('Comptabilité')).toBeNull();
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

    // Plannings, Visites, Tâches, Carte, Hors ligne : aucun de ces libellés ne
    // contient « terrain ». C'est la famille qui les ramène.
    expect(screen.getAllByRole('option')).toHaveLength(5);
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

  it('cherche aussi dans les données, au-delà de deux caractères (SPRINT-21)', async () => {
    vi.mocked(rechercher).mockResolvedValue([
      {
        type: 'site',
        id: 7,
        libelle: 'Rucher des tilleuls',
        precision: 'Figeac',
        route: '/sites',
      },
    ]);
    monter();

    await userEvent.type(screen.getByRole('combobox'), 'tilleul');

    await waitFor(() => expect(rechercher).toHaveBeenCalledWith('tilleul'));
    expect(await screen.findByText('Rucher des tilleuls')).toBeInTheDocument();
  });

  it('ne dérange pas le serveur pour un seul caractère', async () => {
    monter();

    await userEvent.type(screen.getByRole('combobox'), 'a');

    // Le seuil recopie celui du serveur : sous deux caractères, une recherche
    // n'est plus une recherche, c'est un export.
    await new Promise((resoudre) => setTimeout(resoudre, 300));
    expect(rechercher).not.toHaveBeenCalled();
  });

  it('ouvre l’écran qui porte l’objet choisi', async () => {
    vi.mocked(rechercher).mockResolvedValue([
      { type: 'ruche', id: 42, libelle: 'Ruche 42 — Dadant', precision: null, route: '/ruches' },
    ]);
    const { onChoisir } = monter();

    await userEvent.type(screen.getByRole('combobox'), 'dadant');
    await userEvent.click(await screen.findByText('Ruche 42 — Dadant'));

    // Les routes restent plates (ADR du SPRINT-11), mais une route plate accepte
    // un paramètre : l'identifiant part avec l'écran, et c'est lui qui fait
    // qu'on arrive SUR la ligne 42 plutôt que sur la liste des ruches.
    expect(onChoisir).toHaveBeenCalledWith('ruches', 42);
  });
});
