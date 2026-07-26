/**
 * Session applicative — vue du navigateur sur une session qu'il ne détient pas.
 *
 * <p>Depuis la mise en œuvre du BFF (ADR-006), **le navigateur n'a plus aucun
 * jeton**. Les jetons d'accès et de rafraîchissement vivent côté serveur ; le
 * navigateur ne porte qu'un cookie `HttpOnly`, qu'aucun script ne peut lire. Une
 * XSS ne peut donc plus exfiltrer de session réutilisable ailleurs — elle reste
 * capable d'agir depuis la page ouverte, ce que la CSP est là pour empêcher, mais
 * elle ne repart plus avec rien.
 *
 * <p>Ce module ne stocke donc plus de secret : il mémorise seulement ce que le
 * serveur veut bien dire de la session — qui est connecté, avec quels rôles, pour
 * quelle exploitation. Ces informations servent à l'affichage (menu, actions
 * masquées) et **jamais** à l'autorisation, qui reste posée par le serveur.
 */

export interface Session {
  utilisateur: string;
  roles: string[];
  exploitation: string;
}

type Abonne = (session: Session | null) => void;
const abonnes = new Set<Abonne>();

/**
 * Session courante. `null` signifie « pas connecté », `undefined` « pas encore
 * demandé au serveur » — deux états que l'interface ne doit pas confondre : le
 * second ne doit pas afficher l'écran de connexion.
 */
let courante: Session | null | undefined;

/** Session connue, ou `undefined` tant que le serveur n'a pas répondu. */
export const sessionCourante = (): Session | null | undefined => courante;

/** Vrai si l'utilisateur porte ce rôle. Confort d'affichage, pas une garantie. */
export const aLeRole = (role: string): boolean => courante?.roles.includes(role) ?? false;

/**
 * Interroge le serveur sur la session en cours.
 *
 * <p>Un 401 est une réponse NORMALE — « personne n'est connecté » — et non une
 * erreur : c'est ainsi que la PWA distingue une session absente d'un serveur
 * injoignable, lequel doit laisser l'application dans son état plutôt que la
 * renvoyer à l'écran de connexion.
 */
export async function rafraichirSession(): Promise<Session | null> {
  try {
    const reponse = await fetch('/bff/session', {
      credentials: 'include',
      headers: { Accept: 'application/json' },
    });
    definir(reponse.ok ? ((await reponse.json()) as Session) : null);
  } catch {
    // Serveur injoignable : on ne conclut pas à une déconnexion.
    definir(courante ?? null);
  }
  return courante ?? null;
}

/** Fixe la session connue et prévient les abonnés. */
export function definir(session: Session | null): void {
  courante = session;
  abonnes.forEach((abonne) => abonne(session));
}

/** S'abonne aux changements de session ; renvoie la fonction de désabonnement. */
export function surSession(abonne: Abonne): () => void {
  abonnes.add(abonne);
  return () => {
    abonnes.delete(abonne);
  };
}

/** Réservée aux tests : remet le module à son état initial. */
export function reinitialiserSession(): void {
  courante = undefined;
  abonnes.clear();
}
