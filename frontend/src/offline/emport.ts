import type { EmportRucher } from '../api/types';

/**
 * Ruchers emportés hors ligne (SPRINT-24, lot C).
 *
 * <p>Mise en œuvre de l'ADR-012. Le refus de mettre `/api` en cache
 * (`vite.config.ts`, SPRINT-13) reste entier : **rien ici ne passe par le
 * service worker**. Un emport est une ressource que l'utilisateur a demandée,
 * bornée à un rucher, horodatée par le serveur et purgeable — pas une réponse
 * HTTP interceptée à son insu.
 *
 * <p><strong>Ce que ce module garantit, et pourquoi c'est tout ce qui compte.</strong>
 * L'objection de 2026 était juste : une donnée servie depuis le disque ne doit
 * jamais passer pour une donnée fraîche. La réponse n'est donc pas de cacher
 * moins, c'est de **dater**. `preleveLe` accompagne l'instantané partout où il
 * sert, et l'interface l'affiche à chaque lecture — pas seulement à l'endroit où
 * l'emport a été déclenché.
 *
 * <p><strong>Péremption.</strong> Un emport plus vieux que {@link JOURS_VALIDITE}
 * est ignoré à la lecture et signalé à l'écran. La borne n'est pas une durée de
 * conservation technique : c'est le point au-delà duquel un apiculteur qui
 * consulte « ses ruches » consulte en réalité un souvenir.
 */

const CLE = 'zumm.emports';

/**
 * Deux semaines. Une visite par rucher toutes les deux à trois semaines est le
 * rythme courant en saison : au-delà, l'instantané décrit un rucher qu'on a déjà
 * modifié soi-même.
 */
export const JOURS_VALIDITE = 14;

export interface EmportLocal {
  siteId: number;
  siteNom: string;
  /** Instant du prélèvement, tel que le serveur l'a daté. */
  preleveLe: string;
  contenu: EmportRucher;
}

type Abonne = (nombre: number) => void;
const abonnes = new Set<Abonne>();

function charger(): EmportLocal[] {
  try {
    return JSON.parse(localStorage.getItem(CLE) ?? '[]') as EmportLocal[];
  } catch {
    // Un stockage corrompu ne doit pas empêcher d'ouvrir l'application : le pire
    // qui arrive est de repartir sans emport, ce que l'écran dit.
    return [];
  }
}

function sauver(liste: EmportLocal[]): void {
  localStorage.setItem(CLE, JSON.stringify(liste));
  abonnes.forEach((a) => a(liste.length));
}

/** Tous les ruchers emportés, du plus récemment prélevé au plus ancien. */
export function emports(): EmportLocal[] {
  return charger().sort((a, b) => b.preleveLe.localeCompare(a.preleveLe));
}

/** Range un instantané, en remplaçant celui du même rucher s'il existe. */
export function ranger(contenu: EmportRucher): EmportLocal {
  const emport: EmportLocal = {
    siteId: contenu.site.id,
    siteNom: contenu.site.nom,
    preleveLe: contenu.preleveLe,
    contenu,
  };
  sauver([...charger().filter((e) => e.siteId !== emport.siteId), emport]);
  return emport;
}

/**
 * L'instantané d'un rucher, s'il existe **et** s'il est encore valable.
 *
 * <p>Un emport périmé rend `null` plutôt que son contenu : le laisser passer
 * avec un simple avertissement reviendrait à parier que l'utilisateur lit
 * l'avertissement, ce qui est exactement le pari que l'ADR refuse de faire.
 */
export function emportDe(siteId: number, maintenant: Date = new Date()): EmportLocal | null {
  const emport = charger().find((e) => e.siteId === siteId);
  if (!emport || perime(emport, maintenant)) {
    return null;
  }
  return emport;
}

/** Âge de l'emport en jours pleins, pour l'afficher sans calcul dans la vue. */
export function ageEnJours(emport: EmportLocal, maintenant: Date = new Date()): number {
  const millisecondes = maintenant.getTime() - new Date(emport.preleveLe).getTime();
  return Math.floor(millisecondes / 86_400_000);
}

export function perime(emport: EmportLocal, maintenant: Date = new Date()): boolean {
  return ageEnJours(emport, maintenant) >= JOURS_VALIDITE;
}

/** Efface l'emport d'un rucher. */
export function purger(siteId: number): void {
  sauver(charger().filter((e) => e.siteId !== siteId));
}

/**
 * Efface tous les emports.
 *
 * <p>Geste explicite, jamais déclenché par le retour du réseau : une barre de
 * réseau qui réapparaît dix secondes au sommet d'une côte effacerait ce que
 * l'apiculteur vient d'emporter, exactement quand il en a encore besoin. Ce qui
 * empêche un instantané de traverser la saison, c'est sa **péremption**
 * ({@link JOURS_VALIDITE}), pas un événement que le terrain déclenche au hasard.
 */
export function purgerTout(): void {
  sauver([]);
}

/** S'abonne au nombre d'emports ; renvoie le désabonnement. */
export function surEmports(abonne: Abonne): () => void {
  abonnes.add(abonne);
  abonne(charger().length);
  return () => abonnes.delete(abonne);
}
