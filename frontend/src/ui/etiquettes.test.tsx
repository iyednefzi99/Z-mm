import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { chargeQrRuche, codeCourt, PlancheEtiquettes } from './etiquettes';

/**
 * Étiquetage durable des ruches (SPRINT-25, lot J).
 *
 * <p>Deux propriétés seulement, mais ce sont celles qui décident si l'étiquette
 * sert au rucher :
 *
 * <ol>
 *   <li>le code court est <strong>l'identifiant de la ruche</strong>, pas un
 *       second identifiant. En inventer un opaque aurait créé deux façons de
 *       nommer la même colonie, et un jour deux réponses ;
 *   <li>la charge du QR est celle que le NFC écrira — même objet, même adresse.
 * </ol>
 */
describe('étiquettes de ruche', () => {
  it('dérive le code court de l’identifiant, sans en inventer un second', () => {
    expect(codeCourt(42)).toBe('R-42');
    expect(codeCourt(7)).toBe('R-7');
  });

  it('produit une charge utile de la même forme que celle du lot de récolte', () => {
    expect(chargeQrRuche(42)).toBe('zumm:ruche:42');
  });

  it('rend une étiquette par ruche, avec son code et son rucher', () => {
    render(
      <PlancheEtiquettes
        rucherNom="Rucher du causse"
        ruches={[
          { id: 1, modele: 'Dadant 10' },
          { id: 2, modele: 'Langstroth' },
        ]}
      />,
    );

    expect(screen.getByText('R-1')).toBeInTheDocument();
    expect(screen.getByText('R-2')).toBeInTheDocument();
    // Le rucher figure sur chaque étiquette : une ruche transhume, et
    // l'étiquette dit d'où elle vient.
    expect(screen.getAllByText('Rucher du causse')).toHaveLength(2);
  });

  it('affiche la charge utile en clair tant que le QR n’est pas rendu', () => {
    render(<PlancheEtiquettes rucherNom="Rucher" ruches={[{ id: 9, modele: 'Dadant' }]} />);

    // Repli synchrone : mieux vaut un identifiant lisible qu'un carré vide, y
    // compris à l'impression si la génération échoue.
    expect(screen.getByText('zumm:ruche:9')).toBeInTheDocument();
  });
});
