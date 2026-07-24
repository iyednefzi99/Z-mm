/**
 * File de synchronisation différée pour la saisie terrain hors-ligne (US-011).
 *
 * Principe : quand une requête de MUTATION (POST/PUT/DELETE) échoue faute de
 * réseau, elle est mise en file (persistée dans localStorage). Au retour du
 * réseau (événement `online`), les mutations sont rejouées dans l'ordre.
 *
 * Limites assumées (cf. rétrospective SPRINT-04) : pas de résolution de conflits
 * ni de clés d'idempotence — un rejeu après coup peut recréer une ressource déjà
 * créée côté serveur. La résolution de conflits au retour du réseau est une
 * évolution identifiée, pas encore implémentée.
 */

export interface MutationEnAttente {
  id: string;
  methode: 'POST' | 'PUT' | 'DELETE';
  url: string;
  corps?: string;
}

const CLE = 'zumm.file.mutations';
type Abonne = (taille: number) => void;
const abonnes = new Set<Abonne>();

function charger(): MutationEnAttente[] {
  try {
    return JSON.parse(localStorage.getItem(CLE) ?? '[]') as MutationEnAttente[];
  } catch {
    return [];
  }
}

function sauver(file: MutationEnAttente[]): void {
  localStorage.setItem(CLE, JSON.stringify(file));
  abonnes.forEach((a) => a(file.length));
}

/** Nombre de mutations en attente de synchronisation. */
export const tailleFile = (): number => charger().length;

/** Ajoute une mutation à la file. */
export function enfiler(mutation: Omit<MutationEnAttente, 'id'>): void {
  const file = charger();
  file.push({ ...mutation, id: crypto.randomUUID() });
  sauver(file);
}

/** S'abonne aux changements de taille de la file ; renvoie le désabonnement. */
export function surFile(abonne: Abonne): () => void {
  abonnes.add(abonne);
  abonne(tailleFile());
  return () => abonnes.delete(abonne);
}

/**
 * Rejoue les mutations en attente via {@code envoyer}. Une mutation rejouée avec
 * succès (ou définitivement refusée : 4xx) est retirée ; une panne réseau arrête
 * le rejeu et laisse la file en l'état pour la prochaine reconnexion.
 */
export async function rejouer(
  envoyer: (m: MutationEnAttente) => Promise<{ ok: boolean; reseau: boolean }>,
): Promise<void> {
  let file = charger();
  while (file.length > 0) {
    const mutation = file[0];
    const resultat = await envoyer(mutation);
    if (!resultat.ok && resultat.reseau) {
      return; // Toujours hors-ligne : on réessaiera plus tard.
    }
    file = charger().filter((m) => m.id !== mutation.id);
    sauver(file);
  }
}
