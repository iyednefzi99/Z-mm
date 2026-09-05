/**
 * Mode local : rien ne sort de cet appareil (SPRINT-30, lot G).
 *
 * <p>Seconde moitié de la ligne « traitement local, sans aucun trafic sortant »
 * du §8. La première est côté serveur (`PolitiqueReseau`, `zumm.reseau.sortant`)
 * et coupe la météo et le microservice ; celle-ci coupe ce que le
 * **navigateur** va chercher de lui-même — les tuiles de carte.
 *
 * <p><strong>Les deux moitiés sont distinctes, et l'écran doit le dire.</strong>
 * Un exploitant qui coupe le réseau du serveur croirait sinon son poste muet
 * alors qu'il continue de télécharger des tuiles chez un tiers, ce qui révèle
 * précisément où sont ses ruchers. Promettre plus qu'on ne tient serait pire que
 * ne rien promettre.
 *
 * <p>Le réglage est **par appareil**, dans `localStorage`, et c'est voulu : le
 * téléphone qui monte au rucher et le poste du bureau n'ont pas la même
 * exposition. Le rendre serveur en ferait une politique d'exploitation, ce qu'il
 * n'est pas — la politique d'exploitation, c'est `zumm.reseau.sortant`.
 */

const CLE = 'zumm.mode-local';

/**
 * Le mode local est-il actif sur cet appareil ?
 *
 * <p>`localStorage` peut lever — navigation privée, stockage désactivé — et un
 * réglage de confort ne doit jamais empêcher l'application de démarrer.
 */
export function modeLocal(): boolean {
  try {
    return window.localStorage.getItem(CLE) === 'oui';
  } catch {
    return false;
  }
}

/** Change le réglage. Rend l'état obtenu, qui peut différer si l'écriture échoue. */
export function definirModeLocal(actif: boolean): boolean {
  try {
    window.localStorage.setItem(CLE, actif ? 'oui' : 'non');
    return actif;
  } catch {
    return false;
  }
}
