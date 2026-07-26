/**
 * Connexion et déconnexion — deux redirections, et rien de plus.
 *
 * <p>Avant le BFF (ADR-006), ce module menait lui-même le flux Authorization
 * Code + PKCE : génération du verifier, échange du code, stockage des jetons,
 * rafraîchissement sous verrou, révocation à la déconnexion. Près de deux cents
 * lignes de protocole exécutées dans le navigateur, avec les jetons à la clé.
 *
 * <p>Tout cela vit désormais côté serveur. Ce qui reste ici tient en deux
 * navigations : partir se connecter, et partir se déconnecter. C'est le meilleur
 * résumé de ce que le BFF apporte — le code le plus sûr est celui qu'on n'écrit
 * plus.
 */

import { definir } from './session';

/** Point d'entrée de connexion, géré par Spring Security côté serveur. */
const CONNEXION = '/oauth2/authorization/keycloak';

/** Point de déconnexion du BFF ; ferme aussi la session Keycloak. */
const DECONNEXION = '/bff/deconnexion';

/**
 * Envoie l'utilisateur se connecter.
 *
 * <p>La route courante est mémorisée côté navigateur : le serveur ramène à la
 * racine après la connexion, et la PWA restaure ensuite l'écran quitté (US-051).
 */
export function demarrerConnexion(routeDeRetour?: string): void {
  if (routeDeRetour) {
    sessionStorage.setItem('zumm.route-retour', routeDeRetour);
  }
  window.location.assign(CONNEXION);
}

/** Route mémorisée avant la connexion, consommée une seule fois. */
export function consommerRouteDeRetour(): string | null {
  const route = sessionStorage.getItem('zumm.route-retour');
  sessionStorage.removeItem('zumm.route-retour');
  return route;
}

/**
 * Ferme la session applicative PUIS la session SSO.
 *
 * <p>Le serveur répond l'URL de fin de session au lieu d'une redirection : un
 * `fetch` ne peut pas suivre une redirection vers une autre origine, c'est donc
 * au client de naviguer. Le jeton CSRF est exigé — une déconnexion forcée depuis
 * un site tiers reste une nuisance.
 */
export async function deconnexion(jetonCsrf: string | null): Promise<void> {
  try {
    const reponse = await fetch(DECONNEXION, {
      method: 'POST',
      credentials: 'include',
      headers: jetonCsrf ? { 'X-XSRF-TOKEN': jetonCsrf } : {},
    });
    definir(null);
    if (reponse.ok) {
      const corps = (await reponse.json().catch(() => null)) as { redirection?: string } | null;
      if (corps?.redirection) {
        window.location.assign(corps.redirection);
        return;
      }
    }
  } catch {
    definir(null);
  }
  // Aucun fournisseur configuré, ou réponse inattendue : la session locale est
  // close, on revient à l'accueil.
  window.location.assign('/');
}
