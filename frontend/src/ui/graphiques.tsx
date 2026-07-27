/**
 * Graphiques Zümm (SPRINT-13) — SVG natif, sans dependance.
 *
 * Pourquoi pas Chart.js, pourtant cite en annexe B du cahier ? Trois raisons,
 * tranchees dans l'ADR-007 :
 *   1. le theme. Zümm a des jetons de couleur, un mode sombre et un sens de
 *      lecture RTL. Les faire respecter par une bibliotheque de rendu canvas
 *      demande autant de code de configuration que ce fichier entier ;
 *   2. le poids. La PWA s'utilise en bord de reseau ; ~200 ko de bundle pour
 *      quatre graphiques est un mauvais echange ;
 *   3. l'accessibilite. Un canvas n'est pas dans l'arbre d'accessibilite. En SVG,
 *      chaque graphique porte son role, son titre et son equivalent tabulaire.
 *
 * Regles de rendu appliquees ici, et pourquoi :
 *   — les couleurs de serie viennent de `--z-cat-*`, dans un ORDRE FIXE : une
 *     serie garde sa teinte quand un filtre en retire une autre ;
 *   — traits fins (2 px), grille recessive, extremites arrondies a 4 px ;
 *   — l'identite ne repose JAMAIS sur la seule couleur : legende toujours
 *     presente des deux series, et motif de trait distinct par serie ;
 *   — un seul axe de valeurs. Deux grandeurs d'echelles differentes = deux
 *     graphiques, jamais deux axes Y.
 */

import {
  useCallback,
  useId,
  useMemo,
  useRef,
  useState,
  type ReactElement,
  type ReactNode,
} from 'react';

/** Nombre d'emplacements de la palette categorielle (cf. tokens.css). */
const EMPLACEMENTS_SERIE = 7;

/** Couleur d'une serie par son rang. Au-dela de 7, on regroupe en amont. */
export const couleurSerie = (rang: number): string =>
  `var(--z-cat-${(rang % EMPLACEMENTS_SERIE) + 1})`;

/**
 * Motif de trait par rang — l'encodage secondaire qui rend les series
 * distinguables en daltonisme severe, en impression noir et blanc et en mode
 * contraste force.
 */
const MOTIFS = ['none', '6 3', '2 3', '10 3 2 3', '1 4', '8 4 1 4', '4 2'];
export const motifSerie = (rang: number): string => MOTIFS[rang % MOTIFS.length];

export interface Point {
  /** Abscisse : instant ISO pour une serie temporelle, ou index. */
  x: number;
  y: number | null;
  /** Libelle affiche dans l'infobulle ; a defaut, `x` formate. */
  etiquette?: string;
}

export interface Serie {
  nom: string;
  points: Point[];
  /** Rang dans la palette. Fixe par l'appelant, jamais par l'ordre d'affichage. */
  rang: number;
  /** Serie de reference (moyenne mobile, seuil) : trait fin et sans marqueur. */
  reference?: boolean;
}

interface Marges {
  haut: number;
  droite: number;
  bas: number;
  gauche: number;
}

const MARGES: Marges = { haut: 12, droite: 16, bas: 28, gauche: 44 };

/** Bornes d'un ensemble de series, avec une marge de respiration de 8 %. */
function bornes(series: Serie[]): { min: number; max: number } {
  const valeurs = series.flatMap((s) => s.points.map((p) => p.y).filter((y): y is number => y != null));
  if (valeurs.length === 0) return { min: 0, max: 1 };
  let min = Math.min(...valeurs);
  let max = Math.max(...valeurs);
  if (min === max) {
    // Serie plate : sans respiration, la ligne se colle a un bord et le lecteur
    // croit a un bug d'affichage.
    min -= 1;
    max += 1;
  }
  const respiration = (max - min) * 0.08;
  return { min: min - respiration, max: max + respiration };
}

/** Graduations « rondes » : 1, 2, 5 × 10ⁿ — jamais 3,7143. */
function graduations(min: number, max: number, cible = 4): number[] {
  const brut = (max - min) / cible;
  const magnitude = 10 ** Math.floor(Math.log10(brut));
  const pas = [1, 2, 5, 10].map((m) => m * magnitude).find((p) => p >= brut) ?? magnitude * 10;
  const debut = Math.ceil(min / pas) * pas;
  const valeurs: number[] = [];
  for (let v = debut; v <= max; v += pas) valeurs.push(Number(v.toFixed(10)));
  return valeurs;
}

const formatNombre = (v: number, langue: string): string =>
  new Intl.NumberFormat(langue, { maximumFractionDigits: 2 }).format(v);

// ─────────────────────────────────────────────────────────────────────────────
// Cadre commun : titre accessible, legende, equivalent tabulaire
// ─────────────────────────────────────────────────────────────────────────────

interface CadreProps {
  titre: string;
  /** Ce que le graphique dit, en une phrase — lu par les lecteurs d'ecran. */
  description: string;
  legende?: { nom: string; rang: number }[];
  /** Equivalent tabulaire, replie par defaut : l'alternative non visuelle. */
  tableau?: ReactNode;
  libelleTableau: string;
  children: ReactNode;
}

function Cadre({
  titre,
  description,
  legende,
  tableau,
  libelleTableau,
  children,
}: CadreProps): ReactElement {
  return (
    <figure className="z-graphique">
      <figcaption className="z-graphique__entete">
        <span className="z-graphique__titre">{titre}</span>
        {legende && legende.length > 1 && (
          <ul className="z-graphique__legende">
            {legende.map((l) => (
              <li key={l.nom} className="z-graphique__legende-item">
                <svg width="18" height="8" aria-hidden="true" focusable="false">
                  <line
                    x1="0"
                    y1="4"
                    x2="18"
                    y2="4"
                    stroke={couleurSerie(l.rang)}
                    strokeWidth="2"
                    strokeDasharray={motifSerie(l.rang)}
                    strokeLinecap="round"
                  />
                </svg>
                {l.nom}
              </li>
            ))}
          </ul>
        )}
      </figcaption>
      {children}
      <p className="z-graphique__description">{description}</p>
      {tableau && (
        <details className="z-graphique__tableau">
          <summary>{libelleTableau}</summary>
          {tableau}
        </details>
      )}
    </figure>
  );
}

// ─────────────────────────────────────────────────────────────────────────────
// Courbe temporelle multi-series, avec viseur et infobulle
// ─────────────────────────────────────────────────────────────────────────────

export interface CourbeProps {
  titre: string;
  description: string;
  series: Serie[];
  /** Formate une abscisse pour l'axe et l'infobulle. */
  formatX: (x: number) => string;
  unite?: string;
  langue: string;
  libelleTableau: string;
  /**
   * Phrase affichee quand il n'y a rien a tracer.
   *
   * <p>Obligatoire, et non par defaut : ce module reste sans i18n — il recoit
   * `langue` en prop plutot qu'un contexte — donc seul l'appelant peut fournir un
   * texte traduit. Un defaut a « — » laisserait chaque appelant l'oublier en
   * silence, ce qui est precisement ce qui s'etait produit.
   */
  messageVide: string;
  tableau?: ReactNode;
  hauteur?: number;
}

export function Courbe({
  titre,
  description,
  series,
  formatX,
  unite = '',
  langue,
  libelleTableau,
  messageVide,
  tableau,
  hauteur = 220,
}: CourbeProps): ReactElement {
  const largeur = 640;
  const idClip = useId();
  const svgRef = useRef<SVGSVGElement>(null);
  const [survol, setSurvol] = useState<number | null>(null);

  const utiles = useMemo(() => series.filter((s) => s.points.length > 0), [series]);
  const xs = useMemo(() => utiles.flatMap((s) => s.points.map((p) => p.x)), [utiles]);
  const xMin = xs.length ? Math.min(...xs) : 0;
  const xMax = xs.length ? Math.max(...xs) : 1;
  const { min, max } = useMemo(() => bornes(utiles), [utiles]);

  const aireL = largeur - MARGES.gauche - MARGES.droite;
  const aireH = hauteur - MARGES.haut - MARGES.bas;
  const projX = useCallback(
    (x: number) => MARGES.gauche + (xMax === xMin ? aireL / 2 : ((x - xMin) / (xMax - xMin)) * aireL),
    [aireL, xMax, xMin],
  );
  const projY = useCallback(
    (y: number) => MARGES.haut + aireH - ((y - min) / (max - min)) * aireH,
    [aireH, max, min],
  );

  /** Abscisses distinctes, triees : la trame du viseur. */
  const abscisses = useMemo(() => [...new Set(xs)].sort((a, b) => a - b), [xs]);

  const surDeplacement = (evenement: React.PointerEvent<SVGSVGElement>) => {
    const svg = svgRef.current;
    if (!svg || abscisses.length === 0) return;
    const cadre = svg.getBoundingClientRect();
    // Coordonnee ramenee au repere du SVG : l'element est mis a l'echelle par CSS.
    const xPixel = ((evenement.clientX - cadre.left) / cadre.width) * largeur;
    const cible = abscisses.reduce((meilleur, x) =>
      Math.abs(projX(x) - xPixel) < Math.abs(projX(meilleur) - xPixel) ? x : meilleur,
    );
    setSurvol(cible);
  };

  const valeursSurvolees = useMemo(() => {
    if (survol == null) return [];
    return utiles
      .map((s) => ({ serie: s, point: s.points.find((p) => p.x === survol) }))
      .filter((v): v is { serie: Serie; point: Point } => v.point != null && v.point.y != null);
  }, [survol, utiles]);

  if (utiles.length === 0) {
    return (
      <Cadre titre={titre} description={description} libelleTableau={libelleTableau}>
        <p className="z-graphique__vide">{messageVide}</p>
      </Cadre>
    );
  }

  return (
    <Cadre
      titre={titre}
      description={description}
      legende={utiles.map((s) => ({ nom: s.nom, rang: s.rang }))}
      tableau={tableau}
      libelleTableau={libelleTableau}
    >
      <div className="z-graphique__toile">
        <svg
          ref={svgRef}
          viewBox={`0 0 ${largeur} ${hauteur}`}
          className="z-graphique__svg"
          role="img"
          aria-label={`${titre}. ${description}`}
          onPointerMove={surDeplacement}
          onPointerLeave={() => setSurvol(null)}
        >
          <defs>
            <clipPath id={idClip}>
              <rect x={MARGES.gauche} y={MARGES.haut} width={aireL} height={aireH} />
            </clipPath>
          </defs>

          {/* Grille horizontale seule : les lignes verticales n'aident pas a lire
              une valeur, elles ne font que hachurer le fond. */}
          {graduations(min, max).map((v) => (
            <g key={v}>
              <line
                x1={MARGES.gauche}
                y1={projY(v)}
                x2={largeur - MARGES.droite}
                y2={projY(v)}
                stroke="var(--z-graph-grille)"
                strokeWidth="1"
              />
              <text
                x={MARGES.gauche - 8}
                y={projY(v)}
                textAnchor="end"
                dominantBaseline="middle"
                className="z-graphique__graduation"
              >
                {formatNombre(v, langue)}
              </text>
            </g>
          ))}

          {/* Axe des abscisses : trois reperes suffisent (debut, milieu, fin). */}
          {[xMin, (xMin + xMax) / 2, xMax].map((x, i) => (
            <text
              key={x}
              x={projX(x)}
              y={hauteur - 8}
              textAnchor={i === 0 ? 'start' : i === 2 ? 'end' : 'middle'}
              className="z-graphique__graduation"
            >
              {formatX(x)}
            </text>
          ))}

          <g clipPath={`url(#${idClip})`}>
            {utiles.map((s) => {
              const chemin = s.points
                .filter((p) => p.y != null)
                .map((p, i) => `${i === 0 ? 'M' : 'L'} ${projX(p.x)} ${projY(p.y as number)}`)
                .join(' ');
              return (
                <path
                  key={s.nom}
                  d={chemin}
                  fill="none"
                  stroke={couleurSerie(s.rang)}
                  strokeWidth={s.reference ? 1.5 : 2}
                  strokeDasharray={s.reference ? '4 4' : motifSerie(s.rang)}
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  opacity={s.reference ? 0.7 : 1}
                />
              );
            })}
          </g>

          {/* Viseur : une ligne, et un marqueur cercle par serie. Le halo de 2 px
              couleur surface detache le marqueur de la courbe qu'il chevauche. */}
          {survol != null && (
            <g>
              <line
                x1={projX(survol)}
                y1={MARGES.haut}
                x2={projX(survol)}
                y2={MARGES.haut + aireH}
                stroke="var(--z-graph-axe)"
                strokeWidth="1"
                strokeDasharray="3 3"
              />
              {valeursSurvolees.map(({ serie, point }) => (
                <circle
                  key={serie.nom}
                  cx={projX(point.x)}
                  cy={projY(point.y as number)}
                  r="4.5"
                  fill={couleurSerie(serie.rang)}
                  stroke="var(--z-surface)"
                  strokeWidth="2"
                />
              ))}
            </g>
          )}
        </svg>

        {survol != null && valeursSurvolees.length > 0 && (
          <div
            className="z-graphique__infobulle"
            style={{ insetInlineStart: `${(projX(survol) / largeur) * 100}%` }}
            role="status"
          >
            <strong>{formatX(survol)}</strong>
            {valeursSurvolees.map(({ serie, point }) => (
              <span key={serie.nom}>
                <i style={{ background: couleurSerie(serie.rang) }} aria-hidden="true" />
                {serie.nom} : {formatNombre(point.y as number, langue)}
                {unite}
              </span>
            ))}
          </div>
        )}
      </div>
    </Cadre>
  );
}

// ─────────────────────────────────────────────────────────────────────────────
// Barres horizontales : comparer des entites nommees
// ─────────────────────────────────────────────────────────────────────────────

export interface BarreDonnee {
  libelle: string;
  valeur: number;
  /** Met la barre en teinte d'alerte plutot qu'en teinte de serie. */
  alerte?: boolean;
}

export interface BarresProps {
  titre: string;
  description: string;
  donnees: BarreDonnee[];
  unite?: string;
  langue: string;
  libelleTableau: string;
  /** Phrase affichee quand il n'y a rien a tracer. Cf. {@link CourbeProps}. */
  messageVide: string;
  tableau?: ReactNode;
  /**
   * Echelle signee : les valeurs negatives partent a gauche d'un axe zero
   * central. A utiliser quand le signe EST l'information (une tendance de poids),
   * pas pour une simple magnitude.
   */
  divergente?: boolean;
}

export function Barres({
  titre,
  description,
  donnees,
  unite = '',
  langue,
  libelleTableau,
  messageVide,
  tableau,
  divergente = false,
}: BarresProps): ReactElement {
  const [survol, setSurvol] = useState<number | null>(null);

  if (donnees.length === 0) {
    return (
      <Cadre titre={titre} description={description} libelleTableau={libelleTableau}>
        <p className="z-graphique__vide">{messageVide}</p>
      </Cadre>
    );
  }

  const maxAbsolu = Math.max(...donnees.map((d) => Math.abs(d.valeur)), 1);
  // Les barres horizontales se lisent de haut en bas : trier par valeur
  // decroissante transforme la lecture en classement, ce qui est l'usage reel.
  const triees = [...donnees].sort((a, b) => b.valeur - a.valeur);

  return (
    <Cadre
      titre={titre}
      description={description}
      tableau={tableau}
      libelleTableau={libelleTableau}
    >
      <ul className="z-barres">
        {triees.map((d, i) => {
          const proportion = (Math.abs(d.valeur) / maxAbsolu) * (divergente ? 50 : 100);
          const negative = d.valeur < 0;
          const couleur = d.alerte
            ? 'var(--z-danger)'
            : divergente && negative
              ? 'var(--z-danger)'
              : couleurSerie(0);
          return (
            <li
              key={d.libelle}
              className="z-barres__ligne"
              onPointerEnter={() => setSurvol(i)}
              onPointerLeave={() => setSurvol(null)}
            >
              <span className="z-barres__libelle" title={d.libelle}>
                {d.libelle}
              </span>
              <span className={`z-barres__piste ${divergente ? 'z-barres__piste--divergente' : ''}`}>
                <span
                  className="z-barres__remplissage"
                  style={{
                    width: `${proportion}%`,
                    background: couleur,
                    insetInlineStart: divergente ? (negative ? `${50 - proportion}%` : '50%') : '0',
                    opacity: survol == null || survol === i ? 1 : 0.55,
                  }}
                />
                {divergente && <span className="z-barres__zero" aria-hidden="true" />}
              </span>
              {/* Etiquette directe : la valeur est lisible sans survol ni legende,
                  ce qui repond aussi a l'exigence de contraste des teintes claires. */}
              <span className="z-barres__valeur">
                {formatNombre(d.valeur, langue)}
                {unite}
              </span>
            </li>
          );
        })}
      </ul>
    </Cadre>
  );
}

// ─────────────────────────────────────────────────────────────────────────────
// Tuile de statistique : un chiffre qui porte lui-meme le message
// ─────────────────────────────────────────────────────────────────────────────

export interface TuileProps {
  libelle: string;
  valeur: string | number;
  /** Precision sous le chiffre : periode, comparaison, unite. */
  precision?: string;
  ton?: 'neutre' | 'succes' | 'alerte' | 'danger';
  /** Micro-courbe de contexte : la tendance, sans axes ni graduations. */
  etincelle?: number[];
  rang?: number;
}

export function Tuile({
  libelle,
  valeur,
  precision,
  ton = 'neutre',
  etincelle,
  rang = 0,
}: TuileProps): ReactElement {
  const chemin = useMemo(() => {
    if (!etincelle || etincelle.length < 2) return null;
    const min = Math.min(...etincelle);
    const max = Math.max(...etincelle);
    const etendue = max - min || 1;
    return etincelle
      .map((v, i) => {
        const x = (i / (etincelle.length - 1)) * 100;
        const y = 24 - ((v - min) / etendue) * 20;
        return `${i === 0 ? 'M' : 'L'} ${x.toFixed(1)} ${y.toFixed(1)}`;
      })
      .join(' ');
  }, [etincelle]);

  return (
    <div className={`z-tuile z-tuile--${ton}`}>
      <span className="z-tuile__valeur">{valeur}</span>
      <span className="z-tuile__libelle">{libelle}</span>
      {precision && <span className="z-tuile__precision">{precision}</span>}
      {chemin && (
        <svg viewBox="0 0 100 28" className="z-tuile__etincelle" aria-hidden="true" focusable="false">
          <path
            d={chemin}
            fill="none"
            stroke={couleurSerie(rang)}
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
            vectorEffect="non-scaling-stroke"
          />
        </svg>
      )}
    </div>
  );
}
