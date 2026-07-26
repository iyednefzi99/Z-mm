/**
 * Traductions de la console de gestion (US-024) — FR (source), EN, AR.
 *
 * <p>Les libellés vivent dans `locales/*.json` depuis le SPRINT-15, et non plus
 * dans ce fichier. Deux raisons :
 *
 * <ol>
 *   <li><strong>le poids.</strong> Les trois langues partaient dans le paquet
 *       initial, alors qu'une session n'en affiche qu'une. L'anglais et l'arabe
 *       sont désormais des morceaux à part, chargés au moment où on les
 *       demande ;
 *   <li><strong>la traduction.</strong> Un fichier JSON se confie à un
 *       traducteur, se compare et se relit. Un objet TypeScript de mille lignes,
 *       non.
 * </ol>
 *
 * <p><strong>La parité reste vérifiée à la compilation</strong>, et c'est le
 * point à ne pas perdre : le type de retour des chargeurs est `Traductions`,
 * c'est-à-dire la forme du français. Une clé manquante dans `en.json` ou
 * `ar.json` fait échouer `tsc`, exactement comme le faisait le
 * `Record<Langue, typeof fr>` d'avant. `langue.test.tsx` complète en attrapant
 * ce que le typage laisse passer : les clés EN TROP et les valeurs vides.
 *
 * <p>Le français est chargé <strong>avec l'application</strong>, pas
 * paresseusement : c'est la langue source et le repli. Sans lui, un premier
 * rendu n'aurait aucun libellé à afficher, et il faudrait un écran d'attente
 * pour trois cents libellés.
 *
 * <p>La terminologie métier provient du glossaire du cahier des charges.
 */
import fr from './locales/fr.json';
import type { Langue } from './messages';

/** Forme de référence des traductions : celle de la langue source. */
export type Traductions = typeof fr;

/** Langue source, embarquée dans le paquet initial et servant de repli. */
export const LANGUE_SOURCE = 'fr' as const;

/** Traductions de la langue source, disponibles sans attendre. */
export const TRADUCTIONS_SOURCE: Traductions = fr;

/**
 * Chargeurs des langues traduites, un morceau chacun.
 *
 * <p>C'est ici que la parité se vérifie : le type annoncé impose que chaque
 * ressource soit assignable à {@link Traductions}. Retirer une clé de
 * `en.json` casse la compilation, pas seulement l'affichage.
 */
export const CHARGEURS: Record<
  Exclude<Langue, typeof LANGUE_SOURCE>,
  () => Promise<{ default: Traductions }>
> = {
  en: () => import('./locales/en.json'),
  ar: () => import('./locales/ar.json'),
};

/**
 * Remplace les jetons `{nom}` d'un modèle par leurs valeurs.
 *
 * <p>Un gabarit plutôt qu'une concaténation : l'ordre des mots change d'une
 * langue à l'autre, et l'arabe se lit de droite à gauche. Concaténer figerait
 * l'ordre du français.
 */
export const gabarit = (modele: string, valeurs: Record<string, string>): string =>
  modele.replace(/\{(\w+)\}/g, (_, cle: string) => valeurs[cle] ?? `{${cle}}`);
