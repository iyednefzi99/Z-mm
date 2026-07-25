import { beforeEach, describe, expect, it } from 'vitest';
import {
  ONGLETS,
  ONGLET_PAR_DEFAUT,
  cheminDepuisOnglet,
  ongletDepuisChemin,
} from './routes';
import { consommerRouteDeRetour, memoriserRouteDeRetour } from './navigation';

/** Tests de la table des routes et de la reprise après connexion (US-051). */

describe('table des routes', () => {
  it('associe chaque onglet à son chemin, et réciproquement', () => {
    for (const onglet of ONGLETS) {
      expect(ongletDepuisChemin(cheminDepuisOnglet(onglet))).toBe(onglet);
    }
  });

  it('sert l’écran par défaut à la racine', () => {
    expect(ongletDepuisChemin('/')).toBe(ONGLET_PAR_DEFAUT);
    expect(ongletDepuisChemin('')).toBe(ONGLET_PAR_DEFAUT);
  });

  it('tolère une barre oblique finale', () => {
    expect(ongletDepuisChemin('/sites/')).toBe('sites');
  });

  it('rend null sur un chemin inconnu, au lieu de retomber sur le premier onglet', () => {
    expect(ongletDepuisChemin('/ruchers')).toBeNull();
    expect(ongletDepuisChemin('/sites/42')).toBeNull();
    expect(ongletDepuisChemin('/admin')).toBeNull();
  });

  it('couvre les quinze écrans de la console', () => {
    expect(ONGLETS).toHaveLength(15);
    expect(new Set(ONGLETS).size).toBe(15);
  });
});

describe('reprise de route après connexion', () => {
  beforeEach(() => sessionStorage.clear());

  it('mémorise la route quittée et la restitue une seule fois', () => {
    memoriserRouteDeRetour('/plannings');

    expect(consommerRouteDeRetour()).toBe('/plannings');
    // Consommée : un rechargement ultérieur ne doit plus détourner l'utilisateur.
    expect(consommerRouteDeRetour()).toBeNull();
  });

  it('ne mémorise pas la racine — il n’y a rien à restituer', () => {
    memoriserRouteDeRetour('/');

    expect(consommerRouteDeRetour()).toBeNull();
  });

  it('rend null quand aucune route n’a été mémorisée', () => {
    expect(consommerRouteDeRetour()).toBeNull();
  });
});
