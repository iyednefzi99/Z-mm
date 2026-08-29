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

const monter = (etat: Partial<EtatSection>, ecriture?: boolean) =>
  render(
    <LangueProvider>
      <CorpsSection
        titre="Ruches"
        etat={{ ...base, ...etat }}
        onNouveau={vi.fn()}
        ecriture={ecriture}
      >
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

  it('explique un refus de rôle au lieu de proposer de réessayer', () => {
    // « Réessayer » sur un 403 rejoue la même requête pour obtenir le même
    // refus. L'écran d'état remplace donc la section entière — titre compris :
    // quand la liste n'a pas pu être lue, il ne reste rien à titrer.
    monter({ erreur: 'Accès refusé.', statut: 403 });

    expect(screen.getByRole('heading', { name: 'Accès réservé' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Réessayer' })).toBeNull();
    expect(screen.queryByRole('heading', { name: 'Ruches' })).toBeNull();
  });

  it('distingue une panne du serveur d’une erreur ordinaire', () => {
    const recharger = vi.fn();
    monter({ erreur: 'Boom.', statut: 503, recharger });

    expect(
      screen.getByRole('heading', { name: 'Service momentanément indisponible' }),
    ).toBeInTheDocument();
    // Là, réessayer a un sens — c'est même la seule action utile.
    screen.getByRole('button', { name: 'Réessayer' }).click();
    expect(recharger).toHaveBeenCalled();
  });

  it('garde le bandeau ordinaire pour les autres erreurs', () => {
    // Un 400 ou une coupure réseau restent affichés au-dessus de l'écran : la
    // section reste là, et la liste déjà chargée avec elle.
    monter({ erreur: 'Requête invalide.', statut: 400 });

    expect(screen.getByRole('heading', { name: 'Ruches' })).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent('Requête invalide.');
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

describe('commandes d’écriture selon le rôle', () => {
  it('propose « Nouveau » par défaut', () => {
    // Le défaut est ouvert : la plupart des écrans s'écrivent avec tout rôle
    // métier, et un défaut restrictif aurait fait disparaître des commandes
    // légitimes au premier oubli.
    monter({ elements: [1] });

    expect(screen.getByRole('button', { name: /Nouveau/ })).toBeInTheDocument();
  });

  it('le retire quand le rôle ne peut pas écrire', () => {
    // Le serveur refuserait l'enregistrement en 403 : proposer le formulaire,
    // c'est faire saisir pour rien.
    monter({ elements: [1] }, false);

    expect(screen.queryByRole('button', { name: /Nouveau/ })).not.toBeInTheDocument();
  });

  it('change le texte de la liste vide au lieu d’inviter à créer', () => {
    monter({ elements: [] }, false);

    expect(screen.queryByRole('button', { name: /Nouveau/ })).not.toBeInTheDocument();
    expect(screen.getByText(/responsable peut en ajouter/)).toBeInTheDocument();
  });
});
