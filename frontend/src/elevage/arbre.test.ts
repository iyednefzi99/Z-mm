import { describe, expect, it } from 'vitest';
import type { Genealogie, NoeudLignee, ReineElevage } from '../api/types';
import { coordonnees, placer } from './arbre';

/**
 * Mise en page de l'arbre de lignée (SPRINT-29, lot D).
 *
 * <p>Un arbre mal placé se lit quand même — et se lit faux. C'est le seul
 * endroit du lot où l'erreur ne se voit pas à l'œil nu, d'où ces tests.
 */

const reine = (surcharge: Partial<ReineElevage> = {}): ReineElevage => ({
  id: 1,
  code: 'R-1',
  mereId: null,
  mereCode: null,
  rucheMereId: null,
  serieId: null,
  serieNom: null,
  rucheId: null,
  rucheModele: null,
  origine: 'elevage',
  fournisseur: null,
  race: 'Buckfast',
  anneeNaissance: 2026,
  couleurMarquage: null,
  ailesClippees: null,
  dateGreffage: null,
  dateNaissance: null,
  dateFecondation: null,
  dateIntroduction: null,
  dateFin: null,
  statut: 'en_service',
  note: null,
  creeLe: '2026-09-05T08:00:00Z',
  majLe: '2026-09-05T08:00:00Z',
  ...surcharge,
});

const noeud = (
  id: number,
  profondeur: number,
  parentId: number | null,
): NoeudLignee => ({
  id,
  code: `R-${id}`,
  profondeur,
  parentId,
  race: null,
  anneeNaissance: null,
  statut: 'en_service',
});

describe('placement de l’arbre', () => {
  it('met la reine consultée au rang 0, les mères au-dessus, les filles en dessous', () => {
    const genealogie: Genealogie = {
      reine: reine({ id: 5, mereId: 2 }),
      ascendants: [noeud(2, 1, 1), noeud(1, 2, null)],
      descendants: [noeud(8, 1, 5), noeud(9, 1, 5), noeud(12, 2, 8)],
    };

    const arbre = placer(genealogie);

    // On ouvre la fiche d'une reine : elle doit être au centre, sans qu'on ait
    // à la chercher dans l'image.
    expect(arbre.noeuds.find((n) => n.noeud.id === 5)?.rangee).toBe(0);
    expect(arbre.noeuds.find((n) => n.noeud.id === 2)?.rangee).toBe(-1);
    expect(arbre.noeuds.find((n) => n.noeud.id === 1)?.rangee).toBe(-2);
    expect(arbre.noeuds.find((n) => n.noeud.id === 12)?.rangee).toBe(2);
    // Cinq générations de bornes : deux au-dessus, deux en dessous, plus la
    // reine elle-même.
    expect(arbre.hauteur).toBe(5);
  });

  it('range les sœurs côte à côte dans leur rangée', () => {
    const genealogie: Genealogie = {
      reine: reine({ id: 5 }),
      ascendants: [],
      descendants: [noeud(8, 1, 5), noeud(9, 1, 5), noeud(10, 1, 5)],
    };

    const arbre = placer(genealogie);

    expect(arbre.noeuds.filter((n) => n.rangee === 1).map((n) => n.colonne))
      .toEqual([0, 1, 2]);
    expect(arbre.largeur).toBe(3);
  });

  it('ne dessine un lien que si ses deux extrémités sont sur l’image', () => {
    const genealogie: Genealogie = {
      reine: reine({ id: 5, mereId: 2 }),
      // La mère de l'aïeule est au-delà de la profondeur explorée : elle n'est
      // pas là, et un trait partant vers le vide se lirait comme une donnée
      // manquante plutôt que comme une limite d'affichage.
      ascendants: [noeud(2, 1, 99)],
      descendants: [],
    };

    const arbre = placer(genealogie);

    expect(arbre.liens).toEqual([{ de: 2, vers: 5 }]);
  });

  it('place un arbre sans parenté : la reine seule, au centre', () => {
    const arbre = placer({ reine: reine({ id: 3 }), ascendants: [], descendants: [] });

    expect(arbre.noeuds).toHaveLength(1);
    expect(arbre.liens).toHaveLength(0);
    expect(arbre.hauteur).toBe(1);
    expect(arbre.largeur).toBe(1);
  });

  it('nomme par l’identifiant la reine qui n’a pas de code', () => {
    // Toutes les reines ne sont pas numérotées, et un nœud d'arbre a besoin
    // d'un nom : un rectangle vide ne se lit pas.
    const arbre = placer({
      reine: reine({ id: 42, code: null }),
      ascendants: [],
      descendants: [],
    });

    expect(arbre.noeuds[0].noeud.code).toBe('#42');
  });
});

describe('coordonnées', () => {
  it('centre chaque rangée plutôt que de l’aligner à gauche', () => {
    const genealogie: Genealogie = {
      reine: reine({ id: 5 }),
      ascendants: [],
      descendants: [noeud(8, 1, 5), noeud(9, 1, 5), noeud(10, 1, 5)],
    };
    const arbre = placer(genealogie);
    const pas = { x: 100, y: 80 };

    const racine = arbre.noeuds.find((n) => n.rangee === 0);
    const filles = arbre.noeuds.filter((n) => n.rangee === 1);

    // La reine est seule sur sa rangée : elle se place au milieu des trois
    // filles. Un arbre aligné à gauche pencherait, et on lirait la pente comme
    // une information.
    expect(coordonnees(racine!, arbre, pas).x).toBe(150);
    expect(filles.map((f) => coordonnees(f, arbre, pas).x)).toEqual([50, 150, 250]);
    // Les rangées se suivent verticalement, la première à un demi-pas du bord.
    expect(coordonnees(racine!, arbre, pas).y).toBe(40);
    expect(coordonnees(filles[0], arbre, pas).y).toBe(120);
  });
});
