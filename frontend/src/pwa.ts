/**
 * Cycle de vie du service worker (SPRINT-13).
 *
 * Strategie « prompt » et non « autoUpdate » : l'application se recharge quand
 * l'utilisateur le decide, jamais toute seule. Un rechargement spontane pendant
 * la saisie d'un rapport de visite, sur un rucher, ferait perdre le travail —
 * exactement au moment ou l'utilisateur ne peut pas le refaire facilement.
 */

import { useEffect, useState } from 'react';

type Applicateur = (rechargerLaPage?: boolean) => Promise<void>;

let appliquer: Applicateur | null = null;
const abonnes = new Set<(disponible: boolean) => void>();
let disponible = false;

function annoncer(valeur: boolean): void {
  disponible = valeur;
  abonnes.forEach((abonne) => abonne(valeur));
}

/**
 * Enregistre le service worker. Appele une fois, au demarrage, et seulement en
 * production : en developpement, Vite sert les modules et un SW mettrait le
 * rechargement a chaud en defaut.
 */
export async function enregistrerServiceWorker(): Promise<void> {
  if (!import.meta.env.PROD || !('serviceWorker' in navigator)) {
    return;
  }
  // Import dynamique : le module virtuel n'existe qu'au build, et ce fichier est
  // aussi charge par les tests.
  const { registerSW } = await import('virtual:pwa-register');
  appliquer = registerSW({
    onNeedRefresh: () => annoncer(true),
    onOfflineReady: () => undefined,
  });
}

/** Applique la mise a jour en attente et recharge. */
export async function appliquerMiseAJour(): Promise<void> {
  if (appliquer) {
    await appliquer(true);
  }
  annoncer(false);
}

/** Une version plus recente attend-elle d'etre activee ? */
export function useMiseAJourPwa(): boolean {
  const [enAttente, setEnAttente] = useState(disponible);
  useEffect(() => {
    abonnes.add(setEnAttente);
    return () => {
      abonnes.delete(setEnAttente);
    };
  }, []);
  return enAttente;
}

/** Reservee aux tests : remet le module a son etat initial. */
export function reinitialiserPwa(): void {
  appliquer = null;
  annoncer(false);
}
