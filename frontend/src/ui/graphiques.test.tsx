import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { Barres, Courbe, Tuile, couleurSerie, motifSerie } from './graphiques';

/**
 * Ce que ces tests protegent : pas le pixel, mais les regles de lecture. Un
 * graphique juste techniquement et faux visuellement reste faux.
 */
describe('palette de series', () => {
  it('assigne une couleur stable a chaque rang', () => {
    // Une serie doit garder sa teinte quand un filtre en retire une autre :
    // c'est le rang qui decide, jamais l'ordre d'affichage.
    expect(couleurSerie(0)).toBe('var(--z-cat-1)');
    expect(couleurSerie(3)).toBe('var(--z-cat-4)');
  });

  it('recycle plutot que d’inventer une huitieme teinte', () => {
    // Generer une couleur au-dela de la palette validee casserait les garanties
    // de contraste et de daltonisme. Le recyclage est visible et assume.
    expect(couleurSerie(7)).toBe(couleurSerie(0));
  });

  it('donne un motif de trait distinct aux deux premieres series', () => {
    // Encodage secondaire : l'identite ne repose jamais sur la seule couleur.
    expect(motifSerie(0)).not.toBe(motifSerie(1));
  });
});

describe('barres', () => {
  const donnees = [
    { libelle: 'Ruche B', valeur: 12 },
    { libelle: 'Ruche A', valeur: 30 },
    { libelle: 'Ruche C', valeur: 8, alerte: true },
  ];

  it('classe les barres par valeur decroissante', () => {
    render(
      <Barres
        titre="Poids"
        description="desc"
        donnees={donnees}
        langue="fr"
        libelleTableau="tableau"
      />,
    );
    const libelles = screen.getAllByTitle(/Ruche/).map((n) => n.textContent);
    expect(libelles).toEqual(['Ruche A', 'Ruche B', 'Ruche C']);
  });

  it('etiquette chaque barre avec sa valeur', () => {
    // Etiquettes directes : la valeur reste lisible sans survol — c'est aussi ce
    // qui rend acceptables les teintes claires de la palette.
    render(
      <Barres
        titre="Poids"
        description="desc"
        donnees={donnees}
        unite=" kg"
        langue="fr"
        libelleTableau="tableau"
      />,
    );
    expect(screen.getByText('30 kg')).toBeInTheDocument();
    expect(screen.getByText('8 kg')).toBeInTheDocument();
  });

  it('affiche un etat vide plutot qu’un cadre vide', () => {
    render(
      <Barres titre="Poids" description="desc" donnees={[]} langue="fr" libelleTableau="tableau" />,
    );
    expect(screen.getByText('—')).toBeInTheDocument();
  });
});

describe('courbe', () => {
  const series = [
    {
      nom: 'Poids',
      rang: 0,
      points: [
        { x: 1, y: 10 },
        { x: 2, y: 12 },
        { x: 3, y: 11 },
      ],
    },
  ];

  it('porte un role et un libelle accessibles', () => {
    render(
      <Courbe
        titre="Serie"
        description="Evolution du poids"
        series={series}
        formatX={String}
        langue="fr"
        libelleTableau="tableau"
      />,
    );
    expect(screen.getByRole('img', { name: /Serie\. Evolution du poids/ })).toBeInTheDocument();
  });

  it('n’affiche pas de legende pour une serie unique', () => {
    // Le titre nomme deja la serie : une legende d'un seul element est du bruit.
    const { container } = render(
      <Courbe
        titre="Serie"
        description="desc"
        series={series}
        formatX={String}
        langue="fr"
        libelleTableau="tableau"
      />,
    );
    expect(container.querySelector('.z-graphique__legende')).toBeNull();
  });

  it('affiche une legende des qu’il y a deux series', () => {
    const { container } = render(
      <Courbe
        titre="Serie"
        description="desc"
        series={[...series, { nom: 'Reference', rang: 1, points: [{ x: 1, y: 11 }], reference: true }]}
        formatX={String}
        langue="fr"
        libelleTableau="tableau"
      />,
    );
    expect(container.querySelectorAll('.z-graphique__legende-item')).toHaveLength(2);
  });

  it('expose l’equivalent tabulaire quand il est fourni', () => {
    render(
      <Courbe
        titre="Serie"
        description="desc"
        series={series}
        formatX={String}
        langue="fr"
        libelleTableau="Voir le tableau"
        tableau={<table><tbody><tr><td>10</td></tr></tbody></table>}
      />,
    );
    expect(screen.getByText('Voir le tableau')).toBeInTheDocument();
  });
});

describe('tuile', () => {
  it('affiche le chiffre et son libelle', () => {
    render(<Tuile libelle="Ruches" valeur={42} />);
    expect(screen.getByText('42')).toBeInTheDocument();
    expect(screen.getByText('Ruches')).toBeInTheDocument();
  });

  it('double le statut par une classe, pas seulement par la couleur', () => {
    const { container } = render(<Tuile libelle="Alertes" valeur={3} ton="danger" />);
    expect(container.querySelector('.z-tuile--danger')).not.toBeNull();
  });
});
