/**
 * Navigation adressable, sur l'API History du navigateur (US-051, SPRINT-11).
 *
 * <p>Avant ce sprint, l'écran courant vivait dans un {@code useState} : ni lien
 * profond, ni bouton retour, ni partage d'URL — et, en PWA installée, aucun moyen
 * de rouvrir l'application ailleurs que sur le premier onglet.
 *
 * <p>La reprise après reconnexion est traitée ici aussi : la redirection OIDC
 * ramène toujours l'utilisateur à la racine, il faut donc mémoriser la route
 * quittée avant de partir vers Keycloak.
 */
import { useCallback, useEffect, useState } from 'react';

const CLE_RETOUR = 'zumm.route.retour';

/** Chemin courant et fonction de navigation. */
export function useNavigation(): { chemin: string; naviguer: (chemin: string) => void } {
  // Chemin ET requête : depuis le SPRINT-21, `/ruches?id=42` ouvre la fiche 42.
  // Ne suivre que `pathname` ferait perdre le paramètre au premier rendu.
  const [chemin, setChemin] = useState(
    () => window.location.pathname + window.location.search,
  );

  useEffect(() => {
    // Bouton précédent / suivant du navigateur.
    const surRetourArriere = () =>
      setChemin(window.location.pathname + window.location.search);
    window.addEventListener('popstate', surRetourArriere);
    return () => window.removeEventListener('popstate', surRetourArriere);
  }, []);

  const naviguer = useCallback((cible: string) => {
    if (cible !== window.location.pathname + window.location.search) {
      window.history.pushState({}, '', cible);
    }
    setChemin(cible);
  }, []);

  return { chemin, naviguer };
}

/**
 * Mémorise la route courante avant un départ vers Keycloak. Le stockage est de
 * session : une reconnexion dans un autre onglet ne doit pas détourner celui-ci.
 */
export function memoriserRouteDeRetour(chemin: string = window.location.pathname): void {
  if (chemin !== '/') {
    sessionStorage.setItem(CLE_RETOUR, chemin);
  }
}

/**
 * Consomme la route mémorisée, s'il y en a une. Elle n'est rendue qu'une fois :
 * un rechargement ultérieur ne doit pas re-détourner l'utilisateur.
 */
export function consommerRouteDeRetour(): string | null {
  const chemin = sessionStorage.getItem(CLE_RETOUR);
  sessionStorage.removeItem(CLE_RETOUR);
  return chemin;
}
