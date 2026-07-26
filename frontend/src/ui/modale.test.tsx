import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState, type ReactElement } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { LangueProvider } from '../i18n/langue';
import { Modale, Squelette, EtatVide } from './composants';

/**
 * Transition d'ouverture et de fermeture de la modale, états de chargement et
 * état vide.
 *
 * <p>Le défaut corrigé : la modale s'ouvrait en fondu et disparaissait net. Les
 * seize vues l'appellent sous la forme `{ouvert && <Modale/>}` — dès que le
 * parent repasse à `false`, le nœud est démonté et aucune animation de sortie ne
 * peut jouer. La modale retarde donc elle-même sa fermeture. Ce qui est vérifié
 * ici, c'est précisément ce **délai** : sans lui, le correctif n'en est pas un.
 */

function Sonde(): ReactElement {
  const [ouvert, setOuvert] = useState(true);
  return (
    <div>
      <button type="button" onClick={() => setOuvert(true)}>
        ouvrir
      </button>
      {ouvert && (
        <Modale titre="Nouveau site" onFermer={() => setOuvert(false)}>
          <p>contenu</p>
        </Modale>
      )}
    </div>
  );
}

/** Par défaut, le poste de test ne demande PAS la réduction des animations. */
function simulerMouvement(reduit: boolean): void {
  vi.stubGlobal(
    'matchMedia',
    vi.fn().mockImplementation((requete: string) => ({
      matches: reduit && requete.includes('prefers-reduced-motion'),
      media: requete,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      addListener: vi.fn(),
      removeListener: vi.fn(),
      dispatchEvent: vi.fn(),
      onchange: null,
    })),
  );
}

beforeEach(() => simulerMouvement(false));

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

describe('Modale', () => {
  it("s'ouvre en deux temps : rendue fermée, puis ouverte à l'image suivante", async () => {
    render(
      <LangueProvider>
        <Sonde />
      </LangueProvider>,
    );

    // Sans ce décalage d'une image, le navigateur ne verrait qu'un seul état et
    // n'interpolerait rien — la transition d'entrée n'existerait pas.
    // `act` : le passage à `is-open` vient d'une image d'animation, donc hors
    // d'un événement React.
    await act(async () => {
      await new Promise((resolu) => requestAnimationFrame(resolu));
    });
    expect(document.querySelector('.z-overlay')).toHaveClass('is-open');
  });

  it('joue la sortie AVANT de démonter, sur Échap', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const utilisateur = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    render(
      <LangueProvider>
        <Sonde />
      </LangueProvider>,
    );

    await utilisateur.keyboard('{Escape}');

    // Le dialogue est encore là, en cours de fermeture. C'est tout l'enjeu.
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(document.querySelector('.z-overlay')).toHaveClass('is-closing');
    expect(document.querySelector('.z-overlay')).not.toHaveClass('is-open');

    await act(async () => {
      vi.advanceTimersByTime(150);
    });
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('ne ferme qu’une fois, même si Échap et le bouton se suivent', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const utilisateur = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    render(
      <LangueProvider>
        <Sonde />
      </LangueProvider>,
    );

    await utilisateur.keyboard('{Escape}');
    await utilisateur.click(screen.getByRole('button', { name: 'Fermer' }));

    // Deux minuteries qui se chevaucheraient appelleraient `onFermer` deux fois.
    await act(async () => {
      vi.advanceTimersByTime(400);
    });
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('supprime le délai — pas seulement l’animation — en mouvement réduit', async () => {
    simulerMouvement(true);
    const utilisateur = userEvent.setup();
    render(
      <LangueProvider>
        <Sonde />
      </LangueProvider>,
    );

    await utilisateur.keyboard('{Escape}');

    // Attendre 150 ms sans rien montrer serait une latence pure, pas une
    // transition : la fermeture doit être immédiate.
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });
});

describe('Squelette', () => {
  it("esquisse la forme à venir sans rien annoncer aux lecteurs d'écran", () => {
    render(<Squelette lignes={3} />);

    const squelette = document.querySelector('.z-squelette');
    expect(squelette).toHaveAttribute('aria-hidden', 'true');
    expect(document.querySelectorAll('.z-squelette__ligne')).toHaveLength(3);
  });
});

describe('EtatVide', () => {
  it('porte une action plutôt qu’un constat', () => {
    render(
      <EtatVide
        titre="Rien à afficher pour l’instant"
        texte="Créez le premier élément."
        action={<button type="button">+ Nouveau</button>}
      />,
    );

    expect(screen.getByText('Rien à afficher pour l’instant')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '+ Nouveau' })).toBeInTheDocument();
    // Le pictogramme est décoratif : l'annoncer doublerait le titre.
    expect(document.querySelector('.z-vide__pictogramme')).toHaveAttribute(
      'aria-hidden',
      'true',
    );
  });
});
