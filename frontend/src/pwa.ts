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
  invite = null;
  annoncer(false);
}

// ─── Invite d'installation (SPRINT-24, lot C) ──────────────────────────────

/**
 * Evenement propose par Chrome et Edge quand l'application est installable.
 *
 * <p>Il n'est pas dans la bibliotheque standard de TypeScript parce qu'il n'est
 * pas standard : Safari ne l'emet jamais, et l'installation s'y fait par le menu
 * de partage. L'ecran doit donc dire quoi faire quand l'invite n'arrive pas.
 */
interface EvenementInstallation extends Event {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
}

let invite: EvenementInstallation | null = null;
const abonnesInstallation = new Set<(possible: boolean) => void>();

/**
 * Capte l'invite d'installation au lieu de la laisser passer.
 *
 * <p>APIGO conseille a ses utilisateurs d'ajouter l'application a leur ecran
 * d'accueil AVANT de partir au rucher (§13) : c'est ce qui la rend disponible
 * hors ligne. Le navigateur propose cette invite tout seul, une fois, a un
 * moment qu'il choisit — souvent le premier ecran, quand l'utilisateur ne sait
 * pas encore ce qu'est l'application. La capter permet de la reproposer au bon
 * endroit, c'est-a-dire sur l'ecran du terrain.
 */
export function ecouterInstallation(): void {
  window.addEventListener('beforeinstallprompt', (evenement) => {
    // Le defaut du navigateur est une banniere non maitrisee : on la retient
    // pour la rejouer a la demande.
    evenement.preventDefault();
    invite = evenement as EvenementInstallation;
    abonnesInstallation.forEach((abonne) => abonne(true));
  });
  window.addEventListener('appinstalled', () => {
    invite = null;
    abonnesInstallation.forEach((abonne) => abonne(false));
  });
}

/**
 * L'application est-elle deja lancee depuis l'ecran d'accueil ?
 *
 * <p>`matchMedia` est garde : il manque dans certains environnements
 * d'execution (jsdom, WebView anciennes). Une invite d'installation ne vaut pas
 * qu'on fasse tomber l'ecran du terrain, et repondre « non installee » est le
 * repli juste — au pire, on propose une installation deja faite.
 */
export function dejaInstallee(): boolean {
  return typeof window.matchMedia === 'function'
    && window.matchMedia('(display-mode: standalone)').matches;
}

/**
 * Rejoue l'invite retenue. Rend `true` si l'utilisateur a accepte.
 *
 * <p>L'invite ne se rejoue qu'UNE fois : le navigateur invalide l'evenement des
 * qu'il a servi. C'est pour cela que l'ecran cache le bouton apres coup plutot
 * que de laisser cliquer dans le vide.
 */
export async function proposerInstallation(): Promise<boolean> {
  if (!invite) {
    return false;
  }
  const enCours = invite;
  invite = null;
  abonnesInstallation.forEach((abonne) => abonne(false));
  await enCours.prompt();
  const { outcome } = await enCours.userChoice;
  return outcome === 'accepted';
}

/** L'installation est-elle proposable maintenant ? */
export function useInstallationPossible(): boolean {
  const [possible, setPossible] = useState(invite !== null);
  useEffect(() => {
    abonnesInstallation.add(setPossible);
    return () => {
      abonnesInstallation.delete(setPossible);
    };
  }, []);
  return possible;
}
