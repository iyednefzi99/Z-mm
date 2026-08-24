import { render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { AProposVue } from './AProposVue';
import { ConditionsVue } from './ConditionsVue';
import { ConfidentialiteVue } from './ConfidentialiteVue';
import { ContactVue } from './ContactVue';
import { EditionsVue } from './EditionsVue';
import { FonctionnalitesVue } from './FonctionnalitesVue';
import { RessourcesVue } from './RessourcesVue';
import { LangueProvider } from '../i18n/langue';

/**
 * Pages d'information publiques — à propos, CGU, confidentialité (SPRINT-19).
 *
 * <p>Ce qui est vérifié ici tient à trois propriétés qu'une régression casserait
 * en silence : les textes légaux doivent continuer à AVOUER ce qu'ils n'ont pas,
 * la page de confidentialité doit continuer à distinguer les appels partant du
 * serveur de ceux partant du navigateur — c'est toute la différence pour
 * l'adresse IP du lecteur — et la page « à propos » doit s'afficher même quand
 * l'API ne répond pas.
 */
const monter = (vue: React.ReactElement) => render(<LangueProvider>{vue}</LangueProvider>);

describe('conditions générales', () => {
  it('affiche ce qui reste à faire remplir plutôt que de l’inventer', () => {
    // Sans ce bandeau, la page passerait pour finie : quelqu'un la mettrait en
    // ligne en croyant que l'éditeur et l'hébergeur y figurent.
    monter(<ConditionsVue />);

    expect(
      screen.getByRole('heading', { name: 'Ce document n’est pas complet' }),
    ).toBeInTheDocument();
    expect(screen.getByText(/Nom et localisation de l’hébergeur/)).toBeInTheDocument();
  });

  it('décrit l’entrée par code d’exploitation, telle qu’elle est codée', () => {
    monter(<ConditionsVue />);

    expect(screen.getByRole('heading', { name: 'Entrer dans l’application' })).toBeInTheDocument();
    expect(screen.getByText(/Il n’y a pas d’inscription libre/)).toBeInTheDocument();
  });
});

describe('politique de confidentialité', () => {
  it('nomme les deux seuls appels sortants du produit', () => {
    monter(<ConfidentialiteVue />);

    expect(screen.getByText('Open-Meteo')).toBeInTheDocument();
    expect(screen.getByText('Fond cartographique')).toBeInTheDocument();
  });

  it('distingue ce qui part du serveur de ce qui part du navigateur', () => {
    // C'est la seule distinction qui change quelque chose pour le lecteur : le
    // fond de carte est appelé par SON navigateur, donc l'hébergeur des tuiles
    // voit son adresse IP. La météo, elle, part du serveur.
    monter(<ConfidentialiteVue />);

    expect(screen.getByText(/L’appel part du serveur/)).toBeInTheDocument();
    expect(screen.getByText(/l’hébergeur des tuiles voit votre adresse IP/)).toBeInTheDocument();
  });

  it('traite la position des ruchers à part', () => {
    monter(<ConfidentialiteVue />);

    expect(screen.getByRole('heading', { name: 'La position des ruchers' })).toBeInTheDocument();
    expect(screen.getByText(/valent un vol de cheptel/)).toBeInTheDocument();
  });

  it('n’annonce aucune durée de conservation, faute de purge programmée', () => {
    monter(<ConfidentialiteVue />);

    expect(screen.getByText(/Aucune purge automatique/)).toBeInTheDocument();
  });
});

describe('page à propos', () => {
  it('affiche la version rendue par le serveur, jamais une version recopiée', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        json: () =>
          Promise.resolve({
            nom: 'Zümm',
            version: '0.1.0-SNAPSHOT',
            accueil: 'Bienvenue dans Zümm',
            langues: ['fr', 'en', 'ar'],
          }),
      }),
    );

    monter(<AProposVue />);

    // Isolé en LTR : sans cela, l'arabe réordonne « 0.1.0-SNAPSHOT » en
    // « SNAPSHOT-0.1.0 » — le tiret est neutre et suit le sens du paragraphe.
    expect(await screen.findByText('0.1.0-SNAPSHOT')).toHaveAttribute('dir', 'ltr');
    expect(screen.getByText('fr · en · ar')).toHaveAttribute('dir', 'ltr');
  });

  it('demande la traduction dans la langue choisie, sans envoyer de cookie', async () => {
    const faux = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve({ nom: 'Zümm', version: '1', accueil: 'ok', langues: ['fr'] }),
    });
    vi.stubGlobal('fetch', faux);

    monter(<AProposVue />);

    await waitFor(() => expect(faux).toHaveBeenCalled());
    const [url, options] = faux.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('/api/info');
    // Aucun cookie : une page publique ne doit pouvoir déconnecter personne, et
    // le seul moyen d'en être sûr est de ne rien envoyer.
    expect(options.credentials).toBe('omit');
    expect((options.headers as Record<string, string>)['Accept-Language']).toBe('fr');
  });

  it('reste lisible quand l’API ne répond pas', async () => {
    // Une page « à propos » qui ne s'affiche pas quand le serveur est tombé est
    // inutile au seul moment où l'on cherche à savoir ce qui tourne.
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('réseau')));

    monter(<AProposVue />);

    expect(await screen.findByRole('heading', { name: 'À propos de Zümm' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Ce qu’est Zümm' })).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.queryByRole('heading', { name: 'Ce qui tourne' })).toBeNull(),
    );
  });
});

describe('page fonctionnalités', () => {
  it('détaille les six domaines et ce que chacun permet', () => {
    monter(<FonctionnalitesVue />);

    for (const domaine of ['Pilotage', 'Cheptel', 'Terrain', 'Production']) {
      expect(screen.getByRole('heading', { name: domaine })).toBeInTheDocument();
    }
    expect(screen.getByText(/Traçabilité d’un lot jusqu’aux ruches/)).toBeInTheDocument();
  });

  it('annonce aussi ce que le produit ne fait pas', () => {
    // Une liste de fonctions sans limite annoncée oblige le lecteur à essayer
    // pour découvrir le manque, et il le découvre au pire moment.
    monter(<FonctionnalitesVue />);

    expect(screen.getByRole('heading', { name: 'Ce que Zümm ne fait pas' })).toBeInTheDocument();
    expect(screen.getByText(/Ni facturation, ni boutique/)).toBeInTheDocument();
  });
});

describe('page ressources', () => {
  it('répond aux questions dans un accordéon natif, la première ouverte', () => {
    monter(<RessourcesVue />);

    const entrees = document.querySelectorAll('details');
    expect(entrees).toHaveLength(7);
    expect(entrees[0]).toHaveAttribute('open');
    expect(entrees[1]).not.toHaveAttribute('open');
  });

  it('explique le refus d’un écran par le rôle, et non par une panne', () => {
    // La question revient forcément une fois le rail filtré : autant y répondre
    // là où un utilisateur sans compte peut déjà la lire.
    monter(<RessourcesVue />);

    expect(
      screen.getByText(/le journal d’audit et les invitations sont réservés/),
    ).toBeInTheDocument();
  });
});

describe('page éditions', () => {
  it('n’affiche aucun prix et aucun palier inventé', () => {
    monter(<EditionsVue />);

    expect(
      screen.getByRole('heading', { name: 'Une seule édition, tout inclus' }),
    ).toBeInTheDocument();
    expect(screen.getByText(/Modèle économique/)).toBeInTheDocument();
    // Aucun montant : ni symbole monétaire, ni palier commercial.
    expect(document.body.textContent).not.toMatch(/[€$]|\d+\s?(€|EUR|\/mois)/);
  });
});

describe('page contact', () => {
  it('ne propose pas de formulaire, faute de service pour le recevoir', () => {
    // Un formulaire qui n'envoie rien est pire que pas de formulaire : il laisse
    // croire que le message est parti.
    monter(<ContactVue />);

    expect(document.querySelector('form')).toBeNull();
    expect(document.querySelector('input')).toBeNull();
    expect(
      screen.getByRole('heading', { name: 'Pourquoi il n’y a pas de formulaire ici' }),
    ).toBeInTheDocument();
  });

  it('aiguille vers les interlocuteurs qui existent', () => {
    monter(<ContactVue />);

    expect(screen.getByText(/Votre interlocuteur est le responsable/)).toBeInTheDocument();
  });
});
