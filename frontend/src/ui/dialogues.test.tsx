import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState, type ReactElement } from 'react';
import { describe, expect, it } from 'vitest';
import { LangueProvider } from '../i18n/langue';
import { DialoguesProvider, useDialogues } from './dialogues';

/**
 * Tests des dialogues du design system (US-054, SPRINT-11) : ils remplacent
 * `window.confirm`, `window.prompt` et `window.alert`, qui n'étaient ni traduits
 * ni pilotables au clavier de façon vérifiable.
 */

/** Sonde : déclenche un dialogue et affiche ce qu'il a rendu. */
function Sonde(): ReactElement {
  const { confirmer, demander, signaler } = useDialogues();
  const [resultat, setResultat] = useState('rien');

  return (
    <div>
      <button type="button" onClick={() => void confirmer('Supprimer « Rucher du Lot » ?').then((r) => setResultat(String(r)))}>
        supprimer
      </button>
      <button type="button" onClick={() => void demander('Motif du refus').then((r) => setResultat(String(r)))}>
        refuser
      </button>
      <button type="button" onClick={() => void signaler('Le service est indisponible').then(() => setResultat('signale'))}>
        alerter
      </button>
      <span data-testid="resultat">{resultat}</span>
    </div>
  );
}

const monter = () =>
  render(
    <LangueProvider>
      <DialoguesProvider>
        <Sonde />
      </DialoguesProvider>
    </LangueProvider>,
  );

describe('confirmation', () => {
  it('affiche le message et rend true à la confirmation', async () => {
    monter();

    await userEvent.click(screen.getByRole('button', { name: 'supprimer' }));
    expect(await screen.findByText('Supprimer « Rucher du Lot » ?')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Confirmer' }));

    await waitFor(() => expect(screen.getByTestId('resultat')).toHaveTextContent('true'));
  });

  it('rend false à l’annulation', async () => {
    monter();

    await userEvent.click(screen.getByRole('button', { name: 'supprimer' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Annuler' }));

    await waitFor(() => expect(screen.getByTestId('resultat')).toHaveTextContent('false'));
  });

  it('rend false quand on ferme par la touche Échap', async () => {
    monter();

    await userEvent.click(screen.getByRole('button', { name: 'supprimer' }));
    await screen.findByRole('dialog');
    await userEvent.keyboard('{Escape}');

    await waitFor(() => expect(screen.getByTestId('resultat')).toHaveTextContent('false'));
  });

  it('est traduite : aucun libellé de bouton natif du navigateur', async () => {
    monter();

    await userEvent.click(screen.getByRole('button', { name: 'supprimer' }));

    // Les libellés viennent de la console, pas du système.
    expect(await screen.findByRole('button', { name: 'Confirmer' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Annuler' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'OK' })).not.toBeInTheDocument();
  });
});

describe('saisie', () => {
  it('rend le texte saisi', async () => {
    monter();

    await userEvent.click(screen.getByRole('button', { name: 'refuser' }));
    await userEvent.type(await screen.findByLabelText('Motif du refus'), 'Météo défavorable');
    await userEvent.click(screen.getByRole('button', { name: 'Confirmer' }));

    await waitFor(() =>
      expect(screen.getByTestId('resultat')).toHaveTextContent('Météo défavorable'),
    );
  });

  it('rend null à l’annulation — et non une chaîne vide', async () => {
    monter();

    await userEvent.click(screen.getByRole('button', { name: 'refuser' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Annuler' }));

    await waitFor(() => expect(screen.getByTestId('resultat')).toHaveTextContent('null'));
  });

  it('n’affiche pas le message deux fois', async () => {
    monter();

    await userEvent.click(screen.getByRole('button', { name: 'refuser' }));

    await screen.findByLabelText('Motif du refus');
    expect(screen.getAllByText('Motif du refus')).toHaveLength(1);
  });
});

describe('information', () => {
  it('se referme sans proposer d’annulation', async () => {
    monter();

    await userEvent.click(screen.getByRole('button', { name: 'alerter' }));
    expect(await screen.findByText('Le service est indisponible')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Annuler' })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: "J'ai compris" }));

    await waitFor(() => expect(screen.getByTestId('resultat')).toHaveTextContent('signale'));
  });
});

describe('accessibilité du dialogue', () => {
  it('est annoncé comme dialogue modal', async () => {
    monter();

    await userEvent.click(screen.getByRole('button', { name: 'supprimer' }));

    const dialogue = await screen.findByRole('dialog');
    expect(dialogue).toHaveAttribute('aria-modal', 'true');
  });

  it('garde le focus à l’intérieur et le restitue à la fermeture', async () => {
    monter();
    const declencheur = screen.getByRole('button', { name: 'supprimer' });
    declencheur.focus();

    await userEvent.click(declencheur);
    await screen.findByRole('dialog');

    // Plusieurs tabulations : le focus ne doit jamais sortir du dialogue.
    for (let i = 0; i < 6; i += 1) {
      await userEvent.tab();
      expect(screen.getByRole('dialog')).toContainElement(document.activeElement as HTMLElement);
    }

    await userEvent.keyboard('{Escape}');
    await waitFor(() => expect(document.activeElement).toBe(declencheur));
  });
});
