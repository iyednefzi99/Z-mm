import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { definir, reinitialiserSession } from '../auth/session';
import { LangueProvider } from '../i18n/langue';
import { ToastsProvider } from '../ui/toasts';
import { RuchesVue } from './RuchesVue';
import type { Ruche } from '../api/types';

/**
 * Tests du référentiel de la ruche (US-092, SPRINT-20).
 *
 * <p>La cause de clôture est la seule des quatre colonnes qui porte une règle :
 * la base la refuse sur une ruche encore active (`ck_ruche_cause_cloture`).
 * Laisser le champ visible en permanence produirait un refus après la saisie,
 * pour une valeur que l'écran avait pourtant proposée — le pire des deux
 * mondes. La règle est donc tenue par la ruche ET par le formulaire, et c'est
 * ce raccord que ces tests vérifient.
 */
vi.mock('../api/client', () => ({
  ruches: {
    lister: vi.fn(),
    creer: vi.fn(),
    mettreAJour: vi.fn(),
    supprimer: vi.fn(),
  },
  sites: { lister: vi.fn() },
  fermes: { lister: vi.fn() },
  agents: { lister: vi.fn() },
  ErreurApi: class ErreurApi extends Error {},
}));

const { agents, fermes, ruches, sites } = await import('../api/client');

const RUCHE: Ruche = {
  id: 1,
  modele: 'Dadant 10',
  siteId: 2,
  siteNom: 'Rucher du causse',
  fermeId: 4,
  fermeNom: 'Ferme des tilleuls',
  agentResponsableId: null,
  agentResponsableNom: null,
  etat: 'active',
  nbHausses: 0,
  compartiments: [{ id: 9, type: 'corps', nbCadres: 10 }],
  typeRuche: 'dadant',
  couleur: 'jaune',
  origine: 'division',
  causeCloture: null,
  creeLe: '2026-03-01T09:00:00Z',
  majLe: '2026-03-01T09:00:00Z',
};

const monter = () =>
  render(
    <LangueProvider>
      <ToastsProvider>
        <RuchesVue />
      </ToastsProvider>
    </LangueProvider>,
  );

describe('référentiel de la ruche', () => {
  beforeEach(() => {
    // Le referentiel ne s'ecrit qu'avec `responsable` ou `admin` : sans role,
    // les commandes d'ecriture disparaissent (ROLES_ECRITURE, SPRINT-19).
    definir({ utilisateur: 'lea', roles: ['responsable'], exploitation: 'demo' });
    vi.mocked(ruches.lister).mockResolvedValue([RUCHE]);
    vi.mocked(ruches.mettreAJour).mockResolvedValue(RUCHE);
    vi.mocked(sites.lister).mockResolvedValue([
      { id: 2, nom: 'Rucher du causse' } as never,
    ]);
    vi.mocked(fermes.lister).mockResolvedValue([
      { id: 4, nom: 'Ferme des tilleuls' } as never,
    ]);
    vi.mocked(agents.lister).mockResolvedValue([]);
  });

  afterEach(() => reinitialiserSession());

  it('affiche le type au référentiel dans la liste, pas seulement le modèle libre', async () => {
    monter();

    // `modele` reste le texte libre ; c'est `typeRuche` qui rend possible une
    // statistique par type — encore faut-il qu'il se voie.
    expect(await screen.findByText('Dadant 10')).toBeInTheDocument();
    expect(screen.getByText('Dadant')).toBeInTheDocument();
  });

  it('n’offre la cause de clôture que sur une ruche clôturée', async () => {
    monter();
    await screen.findByText('Dadant 10');
    await userEvent.click(screen.getByRole('button', { name: 'Modifier' }));

    expect(screen.queryByLabelText('Cause de clôture')).not.toBeInTheDocument();

    await userEvent.selectOptions(await screen.findByLabelText('État'), 'cloturee');

    expect(screen.getByLabelText('Cause de clôture')).toBeInTheDocument();
  });

  it('transmet le référentiel, cause de clôture comprise', async () => {
    monter();
    await screen.findByText('Dadant 10');
    await userEvent.click(screen.getByRole('button', { name: 'Modifier' }));

    await userEvent.selectOptions(await screen.findByLabelText('État'), 'cloturee');
    await userEvent.selectOptions(screen.getByLabelText('Cause de clôture'), 'vendue');
    await userEvent.selectOptions(screen.getByLabelText('Couleur'), 'bleu');
    await userEvent.click(screen.getByRole('button', { name: 'Enregistrer' }));

    expect(ruches.mettreAJour).toHaveBeenCalledWith(
      1,
      expect.objectContaining({
        etat: 'cloturee',
        typeRuche: 'dadant',
        couleur: 'bleu',
        origine: 'division',
        causeCloture: 'vendue',
      }),
    );
  });

  it('n’envoie pas de cause de clôture sur une ruche revenue à l’état actif', async () => {
    monter();
    await screen.findByText('Dadant 10');
    await userEvent.click(screen.getByRole('button', { name: 'Modifier' }));

    // Une cause choisie puis un retour en arrière sur l'état : la valeur reste
    // dans le formulaire, mais elle ne part pas — la base la refuserait, et
    // « active, parce que vendue » ne veut rien dire.
    await userEvent.selectOptions(await screen.findByLabelText('État'), 'cloturee');
    await userEvent.selectOptions(screen.getByLabelText('Cause de clôture'), 'morte');
    await userEvent.selectOptions(screen.getByLabelText('État'), 'active');
    await userEvent.click(screen.getByRole('button', { name: 'Enregistrer' }));

    expect(ruches.mettreAJour).toHaveBeenCalledWith(
      1,
      expect.objectContaining({ etat: 'active', causeCloture: null }),
    );
  });
});
