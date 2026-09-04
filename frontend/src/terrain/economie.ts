import { useEffect, useState } from 'react';

/**
 * Mode économie (SPRINT-24, lot C).
 *
 * <p>Trois éditeurs concurrents conseillent à leurs utilisateurs d'emporter une
 * batterie externe et de fermer les applications gourmandes avant d'aller au
 * rucher (§13 de `docs/ECART-CONCURRENTS.md`). Un conseil d'économie d'énergie
 * adressé à l'utilisateur est l'aveu d'une application qui consomme sans le
 * dire — et la journée de terrain se termine à plat, souvent au moment où l'on
 * en a le plus besoin.
 *
 * <p><strong>Ce que le mode coupe, et pourquoi ces trois-là.</strong> Ce sont
 * les trois seuls postes qui consomment sans qu'on le demande :
 *
 * <ol>
 *   <li>le <strong>fond cartographique</strong> — MapLibre est du WebGL, et le
 *       GPU est le premier poste de dépense d'un téléphone. La carte reste
 *       accessible, elle demande une confirmation ;
 *   <li>les <strong>rafraîchissements de fond</strong> — un écran qui recharge
 *       ses données toutes les trente secondes réveille la radio à chaque fois,
 *       et la radio coûte plus cher que l'affichage ;
 *   <li>les <strong>animations</strong> — marginales pour la batterie, mais
 *       elles suivent la même intention, et le réglage système
 *       `prefers-reduced-motion` fait déjà exactement cela.
 * </ol>
 *
 * <p><strong>Assumé, et donc visible.</strong> Le mode ne s'active pas tout
 * seul quand la batterie baisse : une application qui change de comportement
 * sans le dire passe pour cassée. C'est un interrupteur, et l'écran dit ce qu'il
 * coupe.
 */

const CLE = 'zumm.economie';

/** Classe posée sur la racine ; `base.css` s'en sert pour couper les animations. */
const CLASSE = 'z-economie';

type Abonne = (actif: boolean) => void;
const abonnes = new Set<Abonne>();

function lire(): boolean {
  try {
    return localStorage.getItem(CLE) === 'oui';
  } catch {
    return false;
  }
}

function appliquer(actif: boolean): void {
  document.documentElement.classList.toggle(CLASSE, actif);
}

/** Le mode économie est-il actif ? Lecture directe, hors composant React. */
export function economieActive(): boolean {
  return lire();
}

export function definirEconomie(actif: boolean): void {
  try {
    localStorage.setItem(CLE, actif ? 'oui' : 'non');
  } catch {
    // Un stockage indisponible (navigation privée) ne doit pas empêcher le
    // réglage de valoir pour la session en cours.
  }
  appliquer(actif);
  abonnes.forEach((a) => a(actif));
}

/** Applique le réglage enregistré au démarrage. */
export function initialiserEconomie(): void {
  appliquer(lire());
}

/** Le réglage, et de quoi le changer. */
export function useEconomie(): [boolean, (actif: boolean) => void] {
  const [actif, setActif] = useState(lire);
  useEffect(() => {
    abonnes.add(setActif);
    return () => {
      abonnes.delete(setActif);
    };
  }, []);
  return [actif, definirEconomie];
}
