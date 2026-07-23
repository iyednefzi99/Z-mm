/**
 * Session applicative — jeton d'acces a l'API.
 *
 * ATTENTION (integration) : en production, le jeton est obtenu aupres de Keycloak
 * (client PWA public, PKCE — cf. infra/keycloak/realm-zumm.json), et porte le
 * claim `tenant_id`. Le flux OIDC complet (keycloak-js / oidc-client-ts) est le
 * point d'integration a cabler. Ici, on isole la SOURCE du jeton derriere ce
 * module : le reste de l'application ne connait que `jetonCourant()`. Basculer
 * vers Keycloak ne touchera que ce fichier.
 */

const CLE = 'zumm.jeton';

type Abonne = (jeton: string | null) => void;
const abonnes = new Set<Abonne>();

/** Jeton courant, ou null si aucune session. */
export function jetonCourant(): string | null {
  return localStorage.getItem(CLE);
}

/** Ouvre une session avec un jeton (ex. obtenu de Keycloak). */
export function ouvrirSession(jeton: string): void {
  localStorage.setItem(CLE, jeton);
  notifier();
}

/** Ferme la session. */
export function fermerSession(): void {
  localStorage.removeItem(CLE);
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
