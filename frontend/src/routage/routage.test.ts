import { beforeEach, describe, expect, it } from 'vitest';
import {
  GROUPES,
  GROUPES_CLES,
  ICONES,
  ONGLETS,
  ONGLET_PAR_DEFAUT,
  ROLES_ONGLET,
  cheminDepuisOnglet,
  ongletAutorise,
  ongletDepuisChemin,
  ongletsVisibles,
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

  it('couvre les dix-huit écrans de la console, sans doublon', () => {
    // Le nombre est volontairement écrit en dur : ajouter un onglet doit obliger
    // à passer ici, donc à vérifier qu'il a bien été déclaré dans les trois
    // langues et branché dans App. Un `ONGLETS.length` se contenterait de se
    // recopier lui-même et ne prouverait rien.
    expect(ONGLETS).toHaveLength(18);
    expect(new Set(ONGLETS).size).toBe(18);
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

describe('écrans réservés à certains rôles', () => {
  it('ne restreint que ce que le serveur restreint déjà en lecture', () => {
    // Cette table recopie `SecurityConfig.matriceRbac` — elle ne la complète
    // pas. En ajouter un troisième écran ici sans que le serveur le refuse
    // masquerait une consultation légitime ; le référentiel, par exemple, n'a
    // que ses ÉCRITURES restreintes.
    expect(Object.keys(ROLES_ONGLET).sort()).toEqual(['audit', 'invitations', 'permissions']);
  });

  it('ouvre les écrans réservés au responsable et à l’administrateur', () => {
    for (const role of ['responsable', 'admin']) {
      expect(ongletAutorise('audit', [role])).toBe(true);
      expect(ongletAutorise('invitations', [role])).toBe(true);
      expect(ongletAutorise('permissions', [role])).toBe(true);
    }
  });

  it('les ferme aux autres rôles', () => {
    for (const role of ['apiculteur', 'superviseur']) {
      expect(ongletAutorise('audit', [role])).toBe(false);
      expect(ongletAutorise('invitations', [role])).toBe(false);
      expect(ongletAutorise('permissions', [role])).toBe(false);
    }
  });

  it('laisse passer tout écran non listé, quels que soient les rôles', () => {
    // Le défaut est OUVERT, et c'est voulu : ce filtrage est un confort de
    // navigation, pas une autorisation. Fermer par défaut ferait disparaître un
    // écran nouvellement ajouté sans que personne ne le remarque.
    for (const onglet of ONGLETS.filter((o) => !(o in ROLES_ONGLET))) {
      expect(ongletAutorise(onglet, [])).toBe(true);
    }
  });

  it('retire les écrans fermés de leur famille', () => {
    const administration = ongletsVisibles('administration', ['apiculteur']);

    expect(administration).toEqual(['agents', 'config']);
    // Le référentiel reste entier : un apiculteur a le droit de le consulter.
    expect(ongletsVisibles('cheptel', ['apiculteur'])).toEqual(GROUPES.cheptel);
  });

  it('rend la famille entière à un responsable', () => {
    for (const groupe of GROUPES_CLES) {
      expect(ongletsVisibles(groupe, ['responsable'])).toEqual(GROUPES[groupe]);
    }
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
