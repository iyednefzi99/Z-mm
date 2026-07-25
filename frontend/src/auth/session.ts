/**
 * Session applicative — jeton d'acces a l'API et jeton de rafraichissement.
 *
 * Ce module isole la SOURCE des jetons : le reste de l'application ne connait que
 * `jetonCourant()`. En production ils viennent de Keycloak (client PWA public,
 * PKCE — cf. infra/keycloak/realm-zumm.json) et portent le claim `tenant_id` ; en
 * developpement, l'ecran de session accepte un jeton colle a la main.
 *
 * Le jeton de rafraichissement (US-050) est conserve a cote du jeton d'acces :
 * sans lui, la session mourait a l'expiration de l'acces — soit cinq minutes avec
 * la configuration par defaut de Keycloak.
 */

const CLE = 'zumm.jeton';
const CLE_RAFRAICHISSEMENT = 'zumm.jeton.rafraichissement';

type Abonne = (jeton: string | null) => void;
const abonnes = new Set<Abonne>();

/** Jeton d'acces courant, ou null si aucune session. */
export function jetonCourant(): string | null {
  return localStorage.getItem(CLE);
}

/** Jeton de rafraichissement courant, ou null s'il n'y en a pas. */
export function jetonRafraichissement(): string | null {
  return localStorage.getItem(CLE_RAFRAICHISSEMENT);
}

/**
 * Ouvre ou prolonge une session. Le jeton de rafraichissement est optionnel : la
 * saisie manuelle d'un jeton d'acces (developpement) n'en fournit pas.
 *
 * <p>Passer {@code undefined} en rafraichissement CONSERVE celui deja en place —
 * Keycloak ne renvoie pas toujours un nouveau jeton de rafraichissement lors d'un
 * renouvellement, et l'effacer condamnerait la session au renouvellement suivant.
 * Passer {@code null} l'efface explicitement.
 */
export function ouvrirSession(jeton: string, rafraichissement?: string | null): void {
  localStorage.setItem(CLE, jeton);
  if (rafraichissement === null) {
    localStorage.removeItem(CLE_RAFRAICHISSEMENT);
  } else if (rafraichissement !== undefined) {
    localStorage.setItem(CLE_RAFRAICHISSEMENT, rafraichissement);
  }
  notifier();
}

/** Ferme la session et efface les deux jetons. */
export function fermerSession(): void {
  localStorage.removeItem(CLE);
  localStorage.removeItem(CLE_RAFRAICHISSEMENT);
  notifier();
}

/** S'abonne aux changements de session ; renvoie la fonction de desabonnement. */
export function surSession(abonne: Abonne): () => void {
  abonnes.add(abonne);
  return () => abonnes.delete(abonne);
}

function notifier(): void {
  const jeton = jetonCourant();
  abonnes.forEach((abonne) => abonne(jeton));
}
