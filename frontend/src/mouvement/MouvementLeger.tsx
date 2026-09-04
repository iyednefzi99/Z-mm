import { LazyMotion } from 'motion/react';
import type { ReactElement, ReactNode } from 'react';

const chargerFonctionnalites = () => import('./fonctionnalites').then((module) => module.default);

/**
 * Fournisseur de mouvement, à enrouler autour d'un écran ou d'un composant animé.
 *
 * <p><strong>Il n'est volontairement PAS posé à la racine de l'application.</strong>
 * `LazyMotion` télécharge son paquet de fonctionnalités dès qu'il est monté : le
 * placer dans `App.tsx` ferait payer ce poids à chaque ouverture de la PWA, y
 * compris aux écrans qui n'animent rien. Zümm est installée et consultée au rucher,
 * parfois en 3G ; la règle est donc de l'enrouler au plus près de ce qui bouge.
 *
 * <p><strong>`strict` est délibéré.</strong> Il fait échouer tout usage de
 * `motion.div`, qui embarquerait la totalité de la bibliothèque dans le bundle
 * initial et annulerait le chargement paresseux ci-dessus. Les composants animés
 * utilisent `m.div`, à la même syntaxe près.
 *
 * <pre>
 *   &lt;MouvementLeger&gt;
 *     &lt;m.div {...useApparition()} /&gt;
 *   &lt;/MouvementLeger&gt;
 * </pre>
 */
export function MouvementLeger({ children }: { children: ReactNode }): ReactElement {
  return (
    <LazyMotion features={chargerFonctionnalites} strict>
      {children}
    </LazyMotion>
  );
}
