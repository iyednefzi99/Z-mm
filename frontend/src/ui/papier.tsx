import type { ReactElement } from 'react';

/**
 * Bord déchiré — la texture de la vitrine publique (SPRINT-19).
 *
 * <p><strong>Pourquoi un SVG et non une image.</strong> Un bord déchiré en PNG
 * se pixellise au premier écran à densité double, pèse pour ce qu'il est — une
 * ligne — et fige sa couleur : il faudrait un fichier par teinte de bande, et
 * un jeu complet de plus pour le mode sombre. Le tracé ci-dessous se colore par
 * `currentColor`, donc il suit les jetons de la charte sans qu'on y touche.
 *
 * <p><strong>Pourquoi des dents fixes et non tirées au hasard.</strong> Une
 * déchirure aléatoire est tentante — c'est ce qui fait « vrai papier ». Elle
 * rend surtout le rendu non reproductible : deux builds ne produisent plus la
 * même page, et toute capture de référence devient instable. Le hasard est donc
 * choisi une fois, ici, et il ne bouge plus.
 *
 * <p><strong>Où elle a le droit d'apparaître.</strong> Sur la coquille publique
 * uniquement, et seulement là où le fond CHANGE réellement. Une déchirure entre
 * deux bandes de même couleur ne se lit pas ; répétée à chaque section, elle
 * cesse d'être une matière pour devenir un tic. La page d'accueil en compte
 * deux, le pied une : trois pour toute la vitrine.
 *
 * <p>Elle est purement décorative, donc `aria-hidden` : elle ne sépare rien que
 * le balisage `<section>` ne dise déjà à un lecteur d'écran.
 */

/**
 * Profil de la déchirure : hauteur de chaque dent, en fraction de l'amplitude.
 *
 * <p>L'alternance courte/longue est ce qui distingue un papier arraché d'un
 * zigzag régulier : un pas constant se lit comme une frise, pas comme une
 * fibre. Vingt-cinq points suffisent — au-delà, l'œil ne distingue plus les
 * dents, et le tracé s'alourdit pour rien.
 */
const DENTS = [
  0.55, 0.12, 0.78, 0.3, 0.64, 0.05, 0.42, 0.88, 0.21, 0.6, 0.09, 0.73, 0.35, 0.51, 0.02, 0.68,
  0.26, 0.83, 0.17, 0.47, 0.94, 0.33, 0.58, 0.11, 0.7,
];

/** Repère haut du tracé, dans le système du `viewBox` (0 → 8). */
const BASE = 1;

/** Débattement vertical des dents. Au-delà de 5, la déchirure mange la bande. */
const AMPLITUDE = 5;

/**
 * Trace le contour de la bande supérieure : plein du haut jusqu'à la déchirure.
 *
 * <p>Le parcours part du coin haut-gauche, longe le haut, puis redescend les
 * dents de la droite vers la gauche avant de se refermer. `decalage` sert à
 * poser une seconde copie légèrement plus bas — la fibre qui dépasse sous
 * l'arrachement, et qui fait qu'on lit du papier plutôt qu'un découpage.
 */
function chemin(decalage: number): string {
  const pas = 100 / (DENTS.length - 1);
  const points = DENTS.map(
    (dent, index) =>
      `${(100 - index * pas).toFixed(2)},${(BASE + dent * AMPLITUDE + decalage).toFixed(2)}`,
  );
  return `M0,0 H100 L${points.join(' L')} Z`;
}

const CHEMIN_FIBRE = chemin(1.4);
const CHEMIN_BORD = chemin(0);

/** Teinte de la bande qui se DÉCHIRE, c'est-à-dire celle qui est au-dessus. */
export type TeintePapier = 'fond' | 'surface';

export function BordDechire({ teinte }: { teinte: TeintePapier }): ReactElement {
  return (
    <div className={`z-dechirure z-dechirure--${teinte}`} aria-hidden="true">
      {/* `preserveAspectRatio="none"` : la déchirure s'étire en largeur avec la
          fenêtre. C'est voulu — une dent plus large sur un grand écran reste une
          dent, alors qu'un motif répété laisserait voir sa couture. */}
      <svg viewBox="0 0 100 8" preserveAspectRatio="none" focusable="false">
        <path className="z-dechirure__fibre" d={CHEMIN_FIBRE} />
        <path className="z-dechirure__bord" d={CHEMIN_BORD} />
      </svg>
    </div>
  );
}
