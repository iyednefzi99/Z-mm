import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ReactElement } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { LangueProvider } from '../i18n/langue';
import { ToastsProvider, useToasts } from './toasts';

/**
 * Tests du retour après mutation et de l'annulation des suppressions.
 *
 * <p>Le point vérifié n'est pas qu'un message s'affiche — c'est que l'action
 * destructive est **réellement différée** : une annulation qui recréerait l'objet
 * lui donnerait un nouvel identifiant et casserait ses rattachements. Le test
 * échoue donc si `executer` part avant l'expiration du délai.
 */

/** Sonde : déclenche les trois formes de toast et compte les effets. */
function Sonde({
  executer,
  annuler,
}: {
  executer: () => void;
  annuler: () => void;
}): ReactElement {
  const toasts = useToasts();
  return (
    <div>
      <button type="button" onClick={() => toasts.succes('Élément créé.')}>
        creer
      </button>
      <button type="button" onClick={() => toasts.erreur('La création a échoué.')}>
        echouer
      </button>
      <button
        type="button"
        onClick={() => toasts.annulable('Élément supprimé.', executer, annuler)}
      >
        supprimer
      </button>
    </div>
  );
}

const monter = (executer: () => void, annuler: () => void) =>
  render(
    <LangueProvider>
      <ToastsProvider>
        <Sonde executer={executer} annuler={annuler} />
      </ToastsProvider>
    </LangueProvider>,
  );

afterEach(() => {
  vi.useRealTimers();
});

describe('Toasts', () => {
  it('annonce une réussite dans une région polie, pas assertive', async () => {
    const utilisateur = userEvent.setup();
    monter(
      () => {},
      () => {},
    );

    await utilisateur.click(screen.getByRole('button', { name: 'creer' }));

    const region = screen.getByRole('status');
    expect(region).toHaveAttribute('aria-live', 'polite');
    expect(region).toHaveTextContent('Élément créé.');
  });

  it("affiche l'échec sans le confondre avec une réussite", async () => {
    const utilisateur = userEvent.setup();
    monter(
      () => {},
      () => {},
    );

    await utilisateur.click(screen.getByRole('button', { name: 'echouer' }));

    expect(screen.getByRole('status')).toHaveTextContent('La création a échoué.');
    // L'état ne repose pas sur la seule couleur, mais la classe doit distinguer
    // les deux tons — c'est elle qui porte le liseré.
    expect(document.querySelector('.z-toast--erreur')).not.toBeNull();
  });

  it('DIFFÈRE la suppression : rien n’est exécuté tant que le délai court', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const utilisateur = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    const executer = vi.fn();
    const annuler = vi.fn();
    monter(executer, annuler);

    await utilisateur.click(screen.getByRole('button', { name: 'supprimer' }));

    expect(screen.getByRole('status')).toHaveTextContent('Élément supprimé.');
    // Le point du test : l'action destructive n'est PAS encore partie.
    expect(executer).not.toHaveBeenCalled();

    // `act` : l'expiration retire le toast, donc met l'état à jour hors d'un
    // événement React. Sans l'enrober, le rendu qui suit ne serait pas garanti.
    await act(async () => {
      vi.advanceTimersByTime(8000);
    });
    expect(executer).toHaveBeenCalledTimes(1);
    expect(annuler).not.toHaveBeenCalled();
  });

  it("annule la suppression et ne l'exécute jamais", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const utilisateur = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    const executer = vi.fn();
    const annuler = vi.fn();
    monter(executer, annuler);

    await utilisateur.click(screen.getByRole('button', { name: 'supprimer' }));
    await utilisateur.click(screen.getByRole('button', { name: 'Annuler' }));

    expect(annuler).toHaveBeenCalledTimes(1);

    // Même après l'expiration du délai initial : la minuterie doit avoir été
    // retirée, pas seulement le toast masqué.
    await act(async () => {
      vi.advanceTimersByTime(20000);
    });
    expect(executer).not.toHaveBeenCalled();
  });

  it('exécute sur-le-champ hors du fournisseur, plutôt que de perdre le geste', async () => {
    const utilisateur = userEvent.setup();
    const executer = vi.fn();
    render(
      <LangueProvider>
        <Sonde executer={executer} annuler={() => {}} />
      </LangueProvider>,
    );

    await utilisateur.click(screen.getByRole('button', { name: 'supprimer' }));

    expect(executer).toHaveBeenCalledTimes(1);
  });
});
