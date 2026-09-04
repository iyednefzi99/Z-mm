import { Reorder, useDragControls } from 'motion/react';
import { useState, type ReactElement, type ReactNode } from 'react';
import { useMouvementReduit } from '../mouvement/apparition';
import { relachement, transitions } from '../mouvement/jetons';
import { deplacer } from './listes';

/**
 * Liste dont l'utilisateur choisit l'ordre (SPRINT-22).
 *
 * <p><strong>Le glissement n'est pas le seul chemin, et ce n'est pas un
 * supplément.</strong> Une liste réordonnable au doigt seul est inutilisable au
 * clavier, et l'était aussi avec des gants sur un téléphone au soleil. Chaque ligne
 * porte donc deux boutons — monter, descendre — qui font exactement ce que fait le
 * glissement, et un déplacement au clavier est annoncé dans une région `aria-live`,
 * sans quoi rien ne signalerait le changement à qui ne voit pas la liste bouger.
 *
 * <p><strong>Le glissement part d'une poignée</strong> (`dragListener={false}` plus
 * `dragControls`) et non de la ligne entière : sur un téléphone, une ligne
 * entièrement glissable capture le défilement de la page et rend la liste
 * impossible à parcourir.
 *
 * <p>Le relâchement suit `relachement` — la ligne lâchée ne part pas dériver, elle
 * rejoint le rang décidé (voir `design/motion/transitions.md` § Relâchement). Sous
 * `prefers-reduced-motion`, le réarrangement devient instantané ; le glissement,
 * lui, reste possible : c'est un geste de l'utilisateur, pas une animation qu'on
 * lui impose.
 *
 * <p><strong>Ce module n'utilise PAS `MouvementLeger`, et c'est mesuré.</strong>
 * `Reorder` rend des composants `motion` en interne : sous `LazyMotion strict` il
 * avertit — « This will break tree shaking » — et embarque la bibliothèque entière
 * quoi qu'il arrive. Le découpage se fait donc un cran plus haut : `PlanningsVue`
 * importe ce fichier paresseusement, ce qui isole Motion dans un morceau à part,
 * exclu du précache et mis en cache à son premier usage — exactement le traitement
 * réservé à MapLibre (`vite.config.ts`). Sans cela, les 45 ko compressés de Motion
 * partaient à l'installation de la PWA, y compris chez les utilisateurs qui
 * n'ouvrent jamais l'écran des plannings.
 */
export interface ProprietesListeReordonnable<T> {
  elements: T[];
  cle: (element: T) => string | number;
  rendu: (element: T, rang: number) => ReactNode;
  onReordonner: (elements: T[]) => void;
  /** Nom accessible de la liste elle-même. */
  libelle: string;
  /** Nom accessible d'une ligne, repris par ses trois commandes. */
  libelleElement: (element: T) => string;
  libelles: { saisir: string; monter: string; descendre: string };
  /** Phrase annoncée après un déplacement au clavier. */
  annoncer: (element: T, rang: number, total: number) => string;
}

export default function ListeReordonnable<T>({
  elements,
  cle,
  rendu,
  onReordonner,
  libelle,
  libelleElement,
  libelles,
  annoncer,
}: ProprietesListeReordonnable<T>): ReactElement {
  const reduit = useMouvementReduit();
  const [annonce, setAnnonce] = useState('');

  const parClavier = (index: number, sens: -1 | 1) => {
    const suivante = deplacer(elements, index, sens);
    const rang = index + sens;
    if (rang < 0 || rang >= elements.length) {
      return;
    }
    onReordonner(suivante);
    setAnnonce(annoncer(elements[index], rang + 1, elements.length));
  };

  return (
    <>
      <Reorder.Group
        as="ol"
        axis="y"
        values={elements}
        onReorder={onReordonner}
        className="z-reordonnable"
        aria-label={libelle}
      >
        {elements.map((element, index) => (
          <Ligne
            key={cle(element)}
            element={element}
            reduit={reduit}
            nom={libelleElement(element)}
            libelles={libelles}
            premier={index === 0}
            dernier={index === elements.length - 1}
            onMonter={() => parClavier(index, -1)}
            onDescendre={() => parClavier(index, 1)}
          >
            {rendu(element, index + 1)}
          </Ligne>
        ))}
      </Reorder.Group>
      {/* Le déplacement au clavier ne produit aucun son ni aucun focus nouveau :
          sans cette région, il serait invisible à un lecteur d'écran. */}
      <p className="z-visuellement-cache" role="status" aria-live="polite">
        {annonce}
      </p>
    </>
  );
}

interface ProprietesLigne<T> {
  element: T;
  reduit: boolean;
  nom: string;
  libelles: { saisir: string; monter: string; descendre: string };
  premier: boolean;
  dernier: boolean;
  onMonter: () => void;
  onDescendre: () => void;
  children: ReactNode;
}

function Ligne<T>({
  element,
  reduit,
  nom,
  libelles,
  premier,
  dernier,
  onMonter,
  onDescendre,
  children,
}: ProprietesLigne<T>): ReactElement {
  const controles = useDragControls();
  const [saisi, setSaisi] = useState(false);

  return (
    <Reorder.Item
      as="li"
      value={element}
      dragListener={false}
      dragControls={controles}
      dragTransition={relachement}
      transition={reduit ? { duration: 0 } : transitions.entree}
      onDragStart={() => setSaisi(true)}
      onDragEnd={() => setSaisi(false)}
      className={saisi ? 'z-reordonnable__ligne is-saisie' : 'z-reordonnable__ligne'}
    >
      <button
        type="button"
        className="z-poignee"
        aria-label={`${libelles.saisir} : ${nom}`}
        onPointerDown={(evenement) => controles.start(evenement)}
      >
        <span aria-hidden="true">⠿</span>
      </button>
      <span className="z-reordonnable__corps">{children}</span>
      <span className="z-reordonnable__commandes">
        <button
          type="button"
          className="z-lien"
          aria-label={`${libelles.monter} : ${nom}`}
          disabled={premier}
          onClick={onMonter}
        >
          <span aria-hidden="true">▲</span>
        </button>
        <button
          type="button"
          className="z-lien"
          aria-label={`${libelles.descendre} : ${nom}`}
          disabled={dernier}
          onClick={onDescendre}
        >
          <span aria-hidden="true">▼</span>
        </button>
      </span>
    </Reorder.Item>
  );
}
