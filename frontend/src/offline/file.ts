/**
 * File de synchronisation différée pour la saisie terrain hors-ligne (US-011).
 *
 * Principe : quand une requête de MUTATION (POST/PUT/DELETE) échoue faute de
 * réseau, elle est mise en file (persistée dans localStorage). Au retour du
 * réseau (événement `online`), les mutations sont rejouées dans l'ordre.
 *
 * Idempotence (SPRINT-14) : chaque mutation porte une CLÉ, générée AVANT la
 * première tentative réseau et conservée telle quelle jusqu'au rejeu. C'est ce
 * détail qui compte : `fetch` échoue aussi quand la requête est bien arrivée mais
 * que la réponse s'est perdue. Générer la clé au moment du rejeu recréerait donc
 * la ressource en double — exactement ce que l'idempotence doit empêcher. Le
 * serveur reconnaît la clé et rejoue sa réponse au lieu de retraiter (V14).
 *
 * Limite restante : pas de résolution de conflits (deux agents modifiant la même
 * visite hors ligne). Évolution identifiée, pas encore implémentée.
 */

export interface MutationEnAttente {
  id: string;
  /** Clé d'idempotence envoyée au serveur ; stable de la 1re tentative au rejeu. */
  cle: string;
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

/**
 * Ajoute une mutation à la file.
 *
 * <p>`cle` est celle déjà utilisée lors de la tentative en ligne qui a échoué. Si
 * l'appelant n'en fournit pas, on retombe sur l'identifiant de file — correct
 * pour une mutation qui n'a jamais quitté le navigateur.
 */
export function enfiler(mutation: Omit<MutationEnAttente, 'id' | 'cle'> & { cle?: string }): void {
  const file = charger();
  const id = crypto.randomUUID();
  file.push({ ...mutation, id, cle: mutation.cle ?? id });
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
 * succès (ou définitivement refusée : 4xx métier) est retirée ; une panne réseau
 * OU une session expirée arrête le rejeu et laisse la file en l'état.
 *
 * <p>La distinction session/refus métier n'est pas cosmétique : avant le
 * SPRINT-14, un 401 — jeton expiré pendant que l'appareil était hors ligne, cas
 * de loin le plus courant après une journée sur le terrain — était traité comme
 * un refus définitif et la saisie était SUPPRIMÉE. On arrête désormais le rejeu :
 * la file survit à la reconnexion de session.
 */
export async function rejouer(
  envoyer: (m: MutationEnAttente) => Promise<{ ok: boolean; reseau: boolean; session?: boolean }>,
): Promise<void> {
  let file = charger();
  while (file.length > 0) {
    const mutation = file[0];
    const resultat = await envoyer(mutation);
    if (!resultat.ok && (resultat.reseau || resultat.session)) {
      return; // Hors-ligne ou session expirée : on réessaiera, sans rien perdre.
    }
    file = charger().filter((m) => m.id !== mutation.id);
    sauver(file);
  }
}
