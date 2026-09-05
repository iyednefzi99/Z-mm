/**
 * Mise en page de l'arbre de lignée (SPRINT-29, lot D).
 *
 * <p>Des fonctions pures, sans React et sans SVG : elles calculent des
 * coordonnées, que le composant se contente de peindre. C'est ce qui rend
 * testable la seule partie où l'on peut se tromper sans le voir — un arbre mal
 * placé se lit quand même, et se lit faux.
 */

import type { Genealogie, NoeudLignee } from '../api/types';

/** Un nœud placé : le nœud d'origine, plus sa position en unités de grille. */
export interface NoeudPlace {
  noeud: NoeudLignee;
  /** Rang vertical : négatif au-dessus de la reine, positif en dessous. */
  rangee: number;
  /** Rang horizontal dans sa rangée, à partir de 0. */
  colonne: number;
}

/** L'arbre placé, prêt à peindre. */
export interface ArbrePlace {
  noeuds: NoeudPlace[];
  /** Liens parent → enfant, par identifiants. */
  liens: { de: number; vers: number }[];
  /** Nombre de rangées, bornes comprises. */
  hauteur: number;
  /** Largeur de la rangée la plus fournie. */
  largeur: number;
}

/**
 * Place les nœuds d'une généalogie sur une grille.
 *
 * <p><strong>La reine consultée est au rang 0</strong>, ses mères au-dessus
 * (rangs négatifs), ses filles en dessous. Ce choix vient de la lecture : on
 * ouvre la fiche d'une reine, et on veut voir d'où elle vient sans chercher où
 * elle se trouve dans l'image.
 *
 * <p>Une même rangée peut contenir plusieurs sœurs : elles sont rangées dans
 * l'ordre reçu du serveur, qui est celui de leur enregistrement. Les trier par
 * autre chose — le code, l'année — ferait sauter les sœurs d'une place à
 * l'autre à chaque ajout, et l'arbre ne serait plus reconnaissable d'une visite
 * sur l'autre.
 */
export function placer(genealogie: Genealogie): ArbrePlace {
  const noeuds: NoeudPlace[] = [];
  const liens: { de: number; vers: number }[] = [];
  const parRangee = new Map<number, number>();

  const poser = (noeud: NoeudLignee, rangee: number) => {
    const colonne = parRangee.get(rangee) ?? 0;
    parRangee.set(rangee, colonne + 1);
    noeuds.push({ noeud, rangee, colonne });
  };

  // La reine consultée, au centre. Elle n'a pas de nœud propre dans les listes
  // du serveur : c'est le point de départ des deux parcours.
  const racine: NoeudLignee = {
    id: genealogie.reine.id,
    code: genealogie.reine.code ?? `#${genealogie.reine.id}`,
    profondeur: 0,
    parentId: genealogie.reine.mereId,
    race: genealogie.reine.race,
    anneeNaissance: genealogie.reine.anneeNaissance,
    statut: genealogie.reine.statut,
  };
  poser(racine, 0);

  for (const ascendant of genealogie.ascendants) {
    poser(ascendant, -ascendant.profondeur);
  }
  for (const descendant of genealogie.descendants) {
    poser(descendant, descendant.profondeur);
  }

  const connus = new Set(noeuds.map((n) => n.noeud.id));
  for (const { noeud } of noeuds) {
    // Un lien ne se dessine que si ses DEUX extrémités sont sur l'image : la
    // mère d'une aïeule au-delà de la profondeur explorée n'y est pas, et un
    // trait qui part vers le vide se lit comme une donnée manquante.
    if (noeud.parentId !== null && connus.has(noeud.parentId)) {
      liens.push({ de: noeud.parentId, vers: noeud.id });
    }
  }

  const rangees = noeuds.map((n) => n.rangee);
  return {
    noeuds,
    liens,
    hauteur: Math.max(...rangees) - Math.min(...rangees) + 1,
    largeur: Math.max(...parRangee.values()),
  };
}

/** Position en pixels d'un nœud, pour une grille donnée. */
export function coordonnees(
  place: NoeudPlace,
  arbre: ArbrePlace,
  pas: { x: number; y: number },
): { x: number; y: number } {
  const rangeeMin = Math.min(...arbre.noeuds.map((n) => n.rangee));
  const dansLaRangee = arbre.noeuds.filter((n) => n.rangee === place.rangee).length;
  // Chaque rangée est centrée sur la largeur totale : un arbre dont les rangées
  // s'alignent à gauche penche, et on lit la pente comme une information.
  const decalage = (arbre.largeur - dansLaRangee) / 2;
  return {
    x: (decalage + place.colonne + 0.5) * pas.x,
    y: (place.rangee - rangeeMin + 0.5) * pas.y,
  };
}
