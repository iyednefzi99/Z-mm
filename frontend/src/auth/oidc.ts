/**
 * Authentification OIDC / PKCE contre Keycloak (US-020, US-021).
 *
 * Flux « Authorization Code + PKCE » d'un client public, sans dépendance externe :
 *  1. `demarrerConnexion()` génère un couple verifier/challenge, mémorise l'état,
 *     puis redirige vers la page de connexion Keycloak — laquelle propose à la
 *     fois les comptes LOCAUX (US-021) et la fédération Google (US-020), selon la
 *     configuration du realm.
 *  2. Au retour (`?code=...`), `terminerConnexion()` échange le code contre un
 *     jeton et ouvre la session.
 *
 * La configuration (issuer, client, redirection) vient des variables Vite
 * `VITE_OIDC_*`. Si l'issuer n'est pas configuré, la connexion Keycloak est
 * simplement absente et l'écran de session propose le repli « coller un jeton »
 * (pratique en développement).
 */
import { fermerSession, ouvrirSession } from './session';

const ISSUER = import.meta.env.VITE_OIDC_ISSUER ?? '';
const CLIENT = import.meta.env.VITE_OIDC_CLIENT ?? 'zumm-frontend';
const REDIRECT = import.meta.env.VITE_OIDC_REDIRECT ?? window.location.origin + '/';

const CLE_VERIFIER = 'zumm.pkce.verifier';
const CLE_ETAT = 'zumm.pkce.etat';
// Jeton d'identité conservé pour le `id_token_hint` de la déconnexion OIDC :
// il permet à Keycloak de clôturer la bonne session sans réafficher d'écran.
const CLE_ID_TOKEN = 'zumm.oidc.id_token';

/** L'authentification Keycloak est-elle configurée ? */
export const oidcConfigure = (): boolean => ISSUER !== '';

function alea(octets: number): string {
  const tableau = new Uint8Array(octets);
  crypto.getRandomValues(tableau);
  return base64url(tableau.buffer);
}

function base64url(buffer: ArrayBuffer): string {
  const octets = new Uint8Array(buffer);
  let binaire = '';
  octets.forEach((o) => {
    binaire += String.fromCharCode(o);
  });
  return btoa(binaire).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

async function challenge(verifier: string): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier));
  return base64url(digest);
}

/** Étape 1 : redirige vers Keycloak (connexion locale ou Google). */
export async function demarrerConnexion(): Promise<void> {
  const verifier = alea(32);
  const etat = alea(16);
  sessionStorage.setItem(CLE_VERIFIER, verifier);
  sessionStorage.setItem(CLE_ETAT, etat);

  const params = new URLSearchParams({
    client_id: CLIENT,
    redirect_uri: REDIRECT,
    response_type: 'code',
    scope: 'openid profile email',
    state: etat,
    code_challenge: await challenge(verifier),
    code_challenge_method: 'S256',
  });
  window.location.assign(`${ISSUER}/protocol/openid-connect/auth?${params}`);
}

/**
 * Étape 2 : au retour de Keycloak, échange le code contre un jeton. Renvoie true
 * si un retour OIDC a été traité (et nettoie l'URL), false sinon.
 */
export async function terminerConnexion(): Promise<boolean> {
  const url = new URL(window.location.href);
  const code = url.searchParams.get('code');
  const etat = url.searchParams.get('state');
  if (!code) {
    return false;
  }
  const verifier = sessionStorage.getItem(CLE_VERIFIER);
  if (!verifier || etat !== sessionStorage.getItem(CLE_ETAT)) {
    throw new Error('Retour OIDC invalide (état ou verifier manquant).');
  }

  const reponse = await fetch(`${ISSUER}/protocol/openid-connect/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'authorization_code',
      client_id: CLIENT,
      redirect_uri: REDIRECT,
      code,
      code_verifier: verifier,
    }),
  });
  if (!reponse.ok) {
    throw new Error(`Échange du code échoué (${reponse.status}).`);
  }
  const jetons = (await reponse.json()) as { access_token: string; id_token?: string };
  sessionStorage.removeItem(CLE_VERIFIER);
  sessionStorage.removeItem(CLE_ETAT);
  if (jetons.id_token) {
    localStorage.setItem(CLE_ID_TOKEN, jetons.id_token);
  }
  // Nettoie les paramètres OIDC de l'URL.
  window.history.replaceState({}, document.title, REDIRECT);
  ouvrirSession(jetons.access_token);
  return true;
}

/**
 * Déconnexion OIDC (US-021) : clôt la session applicative ET la session SSO
 * Keycloak via l'endpoint de fin de session (RP-Initiated Logout). Sans cela,
 * effacer le seul jeton local laisserait la session Keycloak ouverte — un clic
 * sur « se connecter » reconnecterait alors silencieusement l'utilisateur.
 */
export function deconnexionOidc(): void {
  const idToken = localStorage.getItem(CLE_ID_TOKEN);
  localStorage.removeItem(CLE_ID_TOKEN);
  fermerSession();
  const params = new URLSearchParams({ post_logout_redirect_uri: REDIRECT, client_id: CLIENT });
  if (idToken) {
    params.set('id_token_hint', idToken);
  }
  window.location.assign(`${ISSUER}/protocol/openid-connect/logout?${params}`);
}
