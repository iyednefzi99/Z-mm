/**
 * Mécanique de rafraîchissement du jeton d'accès (US-050, SPRINT-11).
 *
 * Ce module ne parle ni à Keycloak, ni au client d'API : il ne connaît que des
 * jetons et une fonction de rafraîchissement. C'est délibéré — la logique
 * traverse le chemin critique de *toutes* les requêtes de l'application, elle doit
 * donc être vérifiable sans réseau ni serveur d'autorisation.
 *
 * Deux problèmes y sont traités :
 *
 *  1. **quand rafraîchir** — le champ `exp` du jeton donne l'échéance ; on
 *     rafraîchit un peu avant, jamais après, pour ne pas exposer l'utilisateur à
 *     une fenêtre où ses requêtes échouent ;
 *  2. **combien de fois** — cinq requêtes qui reçoivent un 401 en même temps ne
 *     doivent déclencher qu'*un* rafraîchissement. Sans ce verrou, elles
 *     consommeraient le même `refresh_token` en parallèle et Keycloak en
 *     invaliderait la rotation.
 */

/** Marge par défaut : rafraîchir 30 s avant l'échéance. */
export const MARGE_MS = 30_000;

/**
 * Instant d'expiration d'un JWT, en millisecondes, ou {@code null} si le jeton
 * est illisible. Le décodage se limite au champ `exp` de la charge utile : la
 * signature n'est pas vérifiée ici — c'est le rôle du serveur, et le client n'a
 * de toute façon pas la clé.
 */
export function expiration(jeton: string): number | null {
  const parties = jeton.split('.');
  if (parties.length < 2) {
    return null;
  }
  try {
    const charge = JSON.parse(decodeBase64Url(parties[1])) as { exp?: number };
    return typeof charge.exp === 'number' ? charge.exp * 1000 : null;
  } catch {
    return null;
  }
}

/**
 * Délai avant le prochain rafraîchissement, en millisecondes.
 *
 * Vaut 0 lorsque l'échéance est déjà atteinte (rafraîchir tout de suite) et
 * {@code null} lorsque le jeton ne porte pas d'échéance lisible — auquel cas il
 * n'y a rien à planifier, le 401 reste le filet de sécurité.
 */
export function delaiAvantRafraichissement(
  jeton: string,
  maintenant: number = Date.now(),
  marge: number = MARGE_MS,
): number | null {
  const echeance = expiration(jeton);
  if (echeance === null) {
    return null;
  }
  return Math.max(0, echeance - marge - maintenant);
}

/**
 * Enveloppe {@code action} d'un verrou : les appels concurrents partagent la même
 * promesse au lieu d'en lancer plusieurs. Une fois l'action terminée, le verrou
 * est relâché — un appel ultérieur relance donc bien un rafraîchissement.
 */
export function creerVerrou<T>(action: () => Promise<T>): () => Promise<T> {
  let enCours: Promise<T> | null = null;
  return () => {
    if (enCours === null) {
      enCours = action().finally(() => {
        enCours = null;
      });
    }
    return enCours;
  };
}

/**
 * Planifie {@code action} pour le moment où {@code jeton} doit être rafraîchi.
 * Renvoie l'annulation — à appeler lorsque la session change ou se ferme, sans
 * quoi un rafraîchissement d'une session périmée partirait quand même.
 */
export function planifier(jeton: string, action: () => void): () => void {
  const delai = delaiAvantRafraichissement(jeton);
  if (delai === null) {
    return () => undefined;
  }
  const minuterie = setTimeout(action, delai);
  return () => clearTimeout(minuterie);
}

/** Décodage base64url — les jetons n'utilisent pas l'alphabet base64 standard. */
function decodeBase64Url(valeur: string): string {
  const base64 = valeur.replace(/-/g, '+').replace(/_/g, '/');
  const complete = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=');
  const binaire = atob(complete);
  // Les claims peuvent contenir de l'UTF-8 (un nom d'agent accentué, par exemple) :
  // `atob` rend des octets, il faut les réinterpréter.
  const octets = Uint8Array.from(binaire, (c) => c.charCodeAt(0));
  return new TextDecoder().decode(octets);
}
