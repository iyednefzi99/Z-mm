/**
 * Déplace un élément d'un cran dans une liste.
 *
 * <p>Rend la liste inchangée aux extrémités plutôt que de lever : l'appelant est
 * un gestionnaire de clic, et devoir vérifier les bornes avant chaque appel est
 * précisément l'oubli qui produit un `undefined` en bout de liste.
 *
 * <p>La liste d'origine n'est jamais modifiée, et les éléments gardent leur
 * identité — `Reorder` suit les siens par référence, pas par valeur.
 */
export function deplacer<T>(liste: readonly T[], index: number, sens: -1 | 1): T[] {
  const cible = index + sens;
  if (index < 0 || index >= liste.length || cible < 0 || cible >= liste.length) {
    return [...liste];
  }
  const copie = [...liste];
  [copie[index], copie[cible]] = [copie[cible], copie[index]];
  return copie;
}
