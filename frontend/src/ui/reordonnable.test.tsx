import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState, type ReactElement } from 'react';
import { describe, expect, it, vi } from 'vitest';
import ListeReordonnable from './reordonnable';

interface Site {
  id: number;
  nom: string;
}

const SITES: Site[] = [
  { id: 1, nom: 'Bizerte' },
  { id: 2, nom: 'Tunis' },
  { id: 3, nom: 'Sfax' },
];

const LIBELLES = { saisir: 'Déplacer', monter: 'Monter', descendre: 'Descendre' };

function Harnais({ onOrdre }: { onOrdre?: (sites: Site[]) => void }): ReactElement {
  const [sites, setSites] = useState(SITES);
  return (
    <ListeReordonnable
      elements={sites}
      cle={(s) => s.id}
      rendu={(s, rang) => `${rang}. ${s.nom}`}
      onReordonner={(suivants) => {
        setSites(suivants);
        onOrdre?.(suivants);
      }}
      libelle="Ordre de tournée"
      libelleElement={(s) => s.nom}
      libelles={LIBELLES}
      annoncer={(s, rang, total) => `${s.nom} passe en position ${rang} sur ${total}.`}
    />
  );
}

const rangs = (): string[] =>
  screen.getAllByRole('listitem').map((li) => li.textContent?.replace(/[⠿▲▼]/g, '').trim() ?? '');

describe('ListeReordonnable', () => {
  it('rend une liste nommée, dans l’ordre reçu', () => {
    render(<Harnais />);
    expect(screen.getByRole('list', { name: 'Ordre de tournée' })).toBeInTheDocument();
    expect(rangs()).toEqual(['1. Bizerte', '2. Tunis', '3. Sfax']);
  });

  it('réordonne au clavier, sans jamais passer par le glissement', async () => {
    // Le test qui compte : c'est ce chemin-là, et lui seul, qui rend la liste
    // utilisable au clavier — et aussi avec des gants, sur un telephone.
    const onOrdre = vi.fn();
    render(<Harnais onOrdre={onOrdre} />);

    await userEvent.click(screen.getByRole('button', { name: 'Monter : Sfax' }));

    expect(rangs()).toEqual(['1. Bizerte', '2. Sfax', '3. Tunis']);
    // Les références sont conservées : `Reorder` suit ses éléments par identité.
    expect(onOrdre).toHaveBeenCalledWith([SITES[0], SITES[2], SITES[1]]);
  });

  it('annonce le déplacement dans une région vivante', async () => {
    render(<Harnais />);
    expect(screen.getByRole('status')).toHaveTextContent('');

    await userEvent.click(screen.getByRole('button', { name: 'Descendre : Bizerte' }));

    expect(screen.getByRole('status')).toHaveTextContent('Bizerte passe en position 2 sur 3.');
  });

  it('désactive les commandes qui ne mènent nulle part', () => {
    render(<Harnais />);
    expect(screen.getByRole('button', { name: 'Monter : Bizerte' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Descendre : Sfax' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Monter : Tunis' })).toBeEnabled();
  });

  it('donne un nom à chaque poignée de saisie', () => {
    render(<Harnais />);
    // Sans nom accessible, la poignée s'annoncerait comme un bouton sans intitulé,
    // trois fois de suite.
    expect(screen.getByRole('button', { name: 'Déplacer : Tunis' })).toBeInTheDocument();
  });
});
