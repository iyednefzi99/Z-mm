import type { ReactElement } from 'react';
import type { Genealogie } from '../api/types';
import { useT } from '../i18n/langue';
import { coordonnees, placer } from './arbre';

/** Pas de la grille, en pixels. Une case tient un code de reine et son année. */
const PAS = { x: 132, y: 84 };
const BOITE = { largeur: 112, hauteur: 46 };

/**
 * Arbre de lignée en SVG (SPRINT-29, lot D).
 *
 * <p>Du SVG écrit à la main, comme les graphiques d'
 * <a href="../../../roadmap/operationnel/06_decisions/ADR-007-graphiques-svg.md">ADR-007</a> :
 * une bibliothèque d'arbres pèserait plus que ce fichier pour dessiner des
 * rectangles et des traits.
 *
 * <p><strong>La reine consultée est au centre</strong>, ses mères au-dessus, ses
 * filles en dessous — et elle est la seule à porter la couleur d'accent. Sans ce
 * repère, on ouvre une fiche et l'on cherche où l'on est.
 *
 * <p>L'arbre est <strong>doublé d'une liste</strong> pour les lecteurs d'écran :
 * un SVG de rectangles ne s'annonce pas, et une généalogie est exactement le
 * genre d'information qu'on ne peut pas se contenter de montrer.
 */
export function ArbreLignee({ genealogie }: { genealogie: Genealogie }): ReactElement {
  const t = useT();
  const arbre = placer(genealogie);
  const largeur = Math.max(arbre.largeur, 1) * PAS.x;
  const hauteur = arbre.hauteur * PAS.y;
  const positions = new Map(
    arbre.noeuds.map((place) => [place.noeud.id, coordonnees(place, arbre, PAS)]),
  );

  return (
    <figure className="z-arbre">
      <figcaption className="z-champ__libelle">{t.elevage.lignee}</figcaption>
      <div className="z-arbre__cadre">
        <svg
          viewBox={`0 0 ${largeur} ${hauteur}`}
          width={largeur}
          height={hauteur}
          role="img"
          aria-label={t.elevage.ligneeDescription}
        >
          {arbre.liens.map((lien) => {
            const de = positions.get(lien.de);
            const vers = positions.get(lien.vers);
            if (de === undefined || vers === undefined) {
              return null;
            }
            return (
              <path
                key={`${lien.de}-${lien.vers}`}
                className="z-arbre__lien"
                d={`M ${de.x} ${de.y + BOITE.hauteur / 2}
                    C ${de.x} ${(de.y + vers.y) / 2},
                      ${vers.x} ${(de.y + vers.y) / 2},
                      ${vers.x} ${vers.y - BOITE.hauteur / 2}`}
              />
            );
          })}
          {arbre.noeuds.map((place) => {
            const point = positions.get(place.noeud.id);
            if (point === undefined) {
              return null;
            }
            const courante = place.rangee === 0;
            return (
              <g key={place.noeud.id} transform={`translate(${point.x} ${point.y})`}>
                <rect
                  className={`z-arbre__boite${courante ? ' z-arbre__boite--courante' : ''}`}
                  x={-BOITE.largeur / 2}
                  y={-BOITE.hauteur / 2}
                  width={BOITE.largeur}
                  height={BOITE.hauteur}
                  rx={8}
                />
                <text className="z-arbre__code" textAnchor="middle" y={-3}>
                  {place.noeud.code}
                </text>
                <text className="z-arbre__detail" textAnchor="middle" y={13}>
                  {[place.noeud.anneeNaissance, place.noeud.race]
                    .filter((x) => x !== null && x !== '')
                    .join(' · ')}
                </text>
              </g>
            );
          })}
        </svg>
      </div>

      {/* Le même contenu en liste : un SVG de rectangles ne s'annonce pas. */}
      <ul className="z-visuellement-cache">
        {genealogie.ascendants.map((noeud) => (
          <li key={`a-${noeud.id}`}>
            {t.elevage.mere} ({noeud.profondeur}) : {noeud.code}
          </li>
        ))}
        {genealogie.descendants.map((noeud) => (
          <li key={`d-${noeud.id}`}>
            {t.elevage.fille} ({noeud.profondeur}) : {noeud.code}
          </li>
        ))}
      </ul>
    </figure>
  );
}
