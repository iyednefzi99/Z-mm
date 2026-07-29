import { beforeEach, describe, expect, it } from 'vitest';
import {
  GROUPES,
  GROUPES_CLES,
  ICONES,
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

  it('couvre les dix-sept écrans de la console, sans doublon', () => {
    // Le nombre est volontairement écrit en dur : ajouter un onglet doit obliger
    // à passer ici, donc à vérifier qu'il a bien été déclaré dans les trois
    // langues et branché dans App. Un `ONGLETS.length` se contenterait de se
    // recopier lui-même et ne prouverait rien.
    expect(ONGLETS).toHaveLength(17);
    expect(new Set(ONGLETS).size).toBe(17);
  });
});

describe('familles de la navigation', () => {
  const ranges = GROUPES_CLES.flatMap((groupe) => GROUPES[groupe]);

  it('range chaque écran dans exactement une famille', () => {
    // L'invariant que le rail suppose. Sans lui, un écran ajouté à `ONGLETS`
    // mais oublié dans `GROUPES` resterait joignable par son URL tout en étant
    // ABSENT de la navigation — un écran mort que rien ne signale.
    expect([...ranges].sort()).toEqual([...ONGLETS].sort());
    expect(new Set(ranges).size).toBe(ONGLETS.length);
  });

  it('ouvre sur une famille de pilotage contenant l’écran par défaut', () => {
    // La première famille est celle qu'on lit d'abord : elle doit contenir
    // l'accueil, sinon la navigation commence ailleurs que l'application.
    expect(GROUPES[GROUPES_CLES[0]]).toContain(ONGLET_PAR_DEFAUT);
  });

  it('donne un pictogramme à chaque écran, et pas deux fois le même', () => {
    for (const onglet of ONGLETS) {
      expect(ICONES[onglet]).toBeTruthy();
    }
    // Deux écrans partageant une icône se confondraient dans la barre du bas,
    // où le libellé est tronqué à six caractères.
    expect(new Set(Object.values(ICONES)).size).toBe(ONGLETS.length);
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
