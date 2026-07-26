import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import ar from './locales/ar.json';
import en from './locales/en.json';
import fr from './locales/fr.json';
import { LangueProvider, useLangue, useT } from './langue';
import { LANGUES, direction } from './messages';

/**
 * Tests de l'internationalisation (US-024), SPRINT-10.
 *
 * Deux garanties tenues ici : la direction du document bascule réellement en RTL
 * pour l'arabe (pas seulement la traduction des libellés), et les trois langues
 * couvrent exactement les mêmes clés — l'équivalent, côté front, de
 * `scripts/check-sync.sh` pour le cahier des charges.
 *
 * <p>Depuis le SPRINT-15, l'anglais et l'arabe sont chargés paresseusement : les
 * assertions portant sur des libellés TRADUITS attendent donc l'arrivée de la
 * ressource. Celles qui portent sur la direction du document, elle, restent
 * synchrones — la mise en page ne dépend d'aucun chargement.
 *
 * <p>La parité de STRUCTURE est déjà tenue par le typage (`CHARGEURS` impose la
 * forme du français). Ce que ces tests ajoutent est ce que le typage laisse
 * passer : les clés EN TROP dans une traduction, et les valeurs vides.
 */

const RESSOURCES = { fr, en, ar } as const;

function Sonde(): React.ReactElement {
  const t = useT();
  const { langue, definirLangue } = useLangue();
  return (
    <div>
      <span data-testid="langue">{langue}</span>
      <span data-testid="titre-carte">{t.onglets.carte}</span>
      <button type="button" onClick={() => definirLangue('ar')}>
        arabe
      </button>
      <button type="button" onClick={() => definirLangue('en')}>
        anglais
      </button>
    </div>
  );
}

const monter = () =>
  render(
    <LangueProvider>
      <Sonde />
    </LangueProvider>,
  );

describe('contexte de langue', () => {
  it('démarre en français, la langue source', () => {
    monter();

    expect(screen.getByTestId('langue')).toHaveTextContent('fr');
    expect(screen.getByTestId('titre-carte')).toHaveTextContent('Carte');
    expect(document.documentElement.lang).toBe('fr');
    expect(document.documentElement.dir).toBe('ltr');
  });

  it('bascule le document en RTL quand on passe à l’arabe', async () => {
    monter();

    await userEvent.click(screen.getByRole('button', { name: 'arabe' }));

    expect(document.documentElement.lang).toBe('ar');
    expect(document.documentElement.dir).toBe('rtl');
  });

  it('revient en LTR en repassant sur une langue latine', async () => {
    monter();

    await userEvent.click(screen.getByRole('button', { name: 'arabe' }));
    await userEvent.click(screen.getByRole('button', { name: 'anglais' }));

    expect(document.documentElement.dir).toBe('ltr');
    await waitFor(() => expect(screen.getByTestId('titre-carte')).toHaveTextContent('Map'));
  });

  it('persiste le choix de langue pour la prochaine visite', async () => {
    monter();

    await userEvent.click(screen.getByRole('button', { name: 'arabe' }));

    expect(localStorage.getItem('zumm.langue')).toBe('ar');
  });

  it('relit la langue enregistrée au montage', () => {
    localStorage.setItem('zumm.langue', 'en');

    monter();

    expect(screen.getByTestId('langue')).toHaveTextContent('en');
  });

  it('ignore une langue enregistrée inconnue et retombe sur le français', () => {
    localStorage.setItem('zumm.langue', 'klingon');

    monter();

    expect(screen.getByTestId('langue')).toHaveTextContent('fr');
  });

  it('déclare l’arabe, et lui seul, comme écriture de droite à gauche', () => {
    expect(direction('ar')).toBe('rtl');
    expect(direction('fr')).toBe('ltr');
    expect(direction('en')).toBe('ltr');
  });
});

describe('parité des traductions de la console', () => {
  /** Chemins de toutes les feuilles d'un objet de traductions. */
  function cles(objet: unknown, prefixe = ''): string[] {
    if (typeof objet !== 'object' || objet === null) {
      return [prefixe];
    }
    return Object.entries(objet).flatMap(([cle, valeur]) =>
      cles(valeur, prefixe === '' ? cle : `${prefixe}.${cle}`),
    );
  }

  it.each(LANGUES.filter((l) => l !== 'fr'))(
    'la langue %s couvre exactement les clés du français',
    (langue) => {
      const source = cles(RESSOURCES.fr).sort();
      const traduites = cles(RESSOURCES[langue]).sort();

      expect(traduites).toEqual(source);
    },
  );

  it('ne laisse aucune traduction vide', () => {
    for (const langue of LANGUES) {
      const vides = cles(RESSOURCES[langue]).filter((chemin) => {
        const valeur = chemin
          .split('.')
          .reduce<unknown>((noeud, cle) => (noeud as Record<string, unknown>)[cle], RESSOURCES[langue]);
        return typeof valeur === 'string' && valeur.trim() === '';
      });
      expect(vides, `traductions vides en ${langue}`).toEqual([]);
    }
  });
});
