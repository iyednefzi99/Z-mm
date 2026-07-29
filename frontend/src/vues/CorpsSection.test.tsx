import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { CorpsSection, type EtatSection } from './CorpsSection';
import { LangueProvider } from '../i18n/langue';

/**
 * Ossature partagee des ecrans de liste.
 *
 * <p>Elle est utilisee par une quinzaine de vues : ce qui est verifie ici vaut
 * donc pour toutes. Le compteur de volume est la seule addition qui puisse mentir
 * — d'ou deux tests sur les cas ou il doit se taire.
 */
const base: EtatSection = {
  chargement: false,
  erreur: null,
  elements: [],
  recharger: vi.fn(),
};

const monter = (etat: Partial<EtatSection>) =>
  render(
    <LangueProvider>
      <CorpsSection titre="Ruches" etat={{ ...base, ...etat }} onNouveau={vi.fn()}>
        <p>contenu</p>
      </CorpsSection>
    </LangueProvider>,
  );

describe('ossature de section', () => {
  it('compte les éléments à côté du titre', () => {
    monter({ elements: [1, 2, 3] });

    expect(screen.getByText('3')).toBeInTheDocument();
  });

  it('préfère le total paginé au nombre de lignes affichées', () => {
    // Sans cela, le compteur dirait « 20 » sur un parc de trois cents ruches —
    // pire qu'une absence de compteur, parce qu'on le croirait.
    monter({ elements: [1, 2], page: 0, taille: 2, total: 317, allerPage: vi.fn() });

    expect(screen.getByText('317')).toBeInTheDocument();
    expect(screen.queryByText('2')).toBeNull();
  });

  it('se tait pendant le chargement', () => {
    // Un « 0 » pendant le chargement annonce une liste vide qui n'en est pas une.
    monter({ chargement: true, elements: [] });

    expect(screen.queryByText('0')).toBeNull();
    expect(screen.getByRole('status')).toBeInTheDocument();
  });

  it('se tait sur une liste vide, où l’état vide parle déjà', () => {
    monter({ elements: [] });

    expect(screen.queryByText('0')).toBeNull();
  });

  it('affiche le sous-titre et les actions quand on les fournit', () => {
    render(
      <LangueProvider>
        <CorpsSection
          titre="Ruches"
          sousTitre="Le parc de l’exploitation."
          actions={<button type="button">Exporter</button>}
          etat={{ ...base, elements: [1] }}
          onNouveau={vi.fn()}
        >
          <p>contenu</p>
        </CorpsSection>
      </LangueProvider>,
    );

    expect(screen.getByText('Le parc de l’exploitation.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Exporter' })).toBeInTheDocument();
  });
});
