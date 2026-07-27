/**
 * Langues servies par le client, et sens d'ecriture.
 *
 * Le francais est la langue source ; `en` et `ar` en sont des traductions.
 * Aucune chaine visible ne doit etre ecrite en dur dans un composant.
 *
 * <p>Ce fichier portait aussi une table de traduction en dur (`messages`), heritee
 * de l'ecran d'etat de l'API du SPRINT-00. Depuis le SPRINT-15 les libelles vivent
 * dans `locales/{fr,en,ar}.json` et sont charges par `console.ts` : la table
 * n'etait plus importee par personne, et ses cles decrivaient un ecran supprime.
 * Elle a ete retiree ; il ne reste ici que ce qui sert reellement.
 */
export const LANGUES = ['fr', 'en', 'ar'] as const;

export type Langue = (typeof LANGUES)[number];

/** Langues dont l'ecriture va de droite a gauche. */
export const LANGUES_RTL: readonly Langue[] = ['ar'];

export const direction = (langue: Langue): 'rtl' | 'ltr' =>
  LANGUES_RTL.includes(langue) ? 'rtl' : 'ltr';
