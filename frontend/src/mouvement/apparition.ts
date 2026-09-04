import { useMemo, useSyncExternalStore } from 'react';
import type { Target, Transition } from 'motion/react';
import { jetonsMouvement, transitions } from './jetons';

/**
 * Garde d'accessibilité pour le mouvement piloté en JavaScript.
 *
 * <p><strong>Pourquoi elle est indispensable.</strong> `theme/tokens.css` neutralise
 * déjà tout le mouvement sous `@media (prefers-reduced-motion: reduce)` — mais cette
 * règle ne peut atteindre que des `transition-duration` et des `animation-duration`
 * CSS. Motion, lui, n'utilise ni l'un ni l'autre : il écrit `transform` et `opacity`
 * en ligne, image par image. <strong>La garde CSS du dépôt ne le voit pas.</strong>
 * Sans le crochet ci-dessous, chaque composant animé en JavaScript rouvrirait le
 * trou d'accessibilité que la feuille globale ferme depuis le SPRINT-13.
 *
 * <p>`useSyncExternalStore` plutôt qu'un `useEffect` : la préférence est connue dès
 * le premier rendu, donc aucune image d'animation n'est jouée avant que la garde
 * ne s'applique. Le repli sur `false` couvre l'absence de `matchMedia` (jsdom).
 */
const REQUETE = '(prefers-reduced-motion: reduce)';

function sabonner(rappel: () => void): () => void {
  if (typeof window.matchMedia !== 'function') {
    return () => undefined;
  }
  const requete = window.matchMedia(REQUETE);
  requete.addEventListener('change', rappel);
  return () => requete.removeEventListener('change', rappel);
}

function lire(): boolean {
  return typeof window.matchMedia === 'function' && window.matchMedia(REQUETE).matches;
}

/** `true` si l'utilisateur a demandé à réduire les animations. Réactif. */
export function useMouvementReduit(): boolean {
  return useSyncExternalStore(sabonner, lire, () => false);
}

/** Les trois états d'un élément animé, plus sa transition. */
export interface Apparition {
  initial: Target;
  animate: Target;
  exit: Target;
  transition: Transition;
}

/** État final immobile : l'élément est visible, à sa place, sans transition. */
const IMMOBILE: Apparition = {
  initial: { opacity: 1, y: 0 },
  animate: { opacity: 1, y: 0 },
  exit: { opacity: 1, y: 0 },
  transition: { duration: 0 },
};

/**
 * Fondu montant conforme à la charte (`z-fade-up`), neutralisé si l'utilisateur
 * réduit les animations.
 *
 * <p>La neutralisation est <em>totale</em> — pas même un fondu — pour rester
 * alignée sur la garde CSS globale, qui coupe tout. Et l'état final reste correct
 * sans mouvement : la règle de `design/motion/transitions.md` est que le mouvement
 * enrichit l'affichage, il ne le conditionne jamais.
 *
 * @param distance amplitude du décalage vertical, en pixels (défaut : `--z-shift`).
 */
export function useApparition(distance = jetonsMouvement.distance.decalage): Apparition {
  const reduit = useMouvementReduit();

  return useMemo(() => {
    if (reduit) {
      return IMMOBILE;
    }
    return {
      initial: { opacity: 0, y: distance },
      animate: { opacity: 1, y: 0 },
      exit: { opacity: 0, y: distance },
      transition: transitions.entree,
    };
  }, [reduit, distance]);
}

/**
 * Variantes d'une liste révélée en cascade. À poser sur le conteneur, les enfants
 * portant `variants={ECHELON_ENFANT}`.
 *
 * <p>L'échelonnement est coupé sous `prefers-reduced-motion` : c'est le seul
 * réglage qui change, la structure des variantes reste la même des deux côtés pour
 * qu'aucun composant n'ait à connaître la préférence.
 */
export function useEchelonnement(): { conteneur: Record<string, Target>; enfant: Apparition } {
  const reduit = useMouvementReduit();
  const apparition = useApparition();

  return useMemo(
    () => ({
      conteneur: {
        visible: {
          transition: { staggerChildren: reduit ? 0 : jetonsMouvement.echelonnement },
        } as Target,
      },
      enfant: apparition,
    }),
    [reduit, apparition],
  );
}
