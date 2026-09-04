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
 * Conflits et refus (SPRINT-24, lot C) : une mutation rejouée que le serveur
 * refuse n'est plus JETÉE. Jusqu'ici, tout 4xx était considéré comme
 * définitivement traité et la saisie disparaissait sans un mot — c'est le pire
 * des sorts pour une observation faite au rucher trois heures plus tôt, et c'est
 * exactement ce qui arrivait quand deux agents modifiaient la même visite hors
 * ligne. Elle passe désormais en QUARANTAINE, avec le motif du serveur, et
 * l'apiculteur tranche : réappliquer, ou abandonner.
 */

export interface MutationEnAttente {
  id: string;
  /** Clé d'idempotence envoyée au serveur ; stable de la 1re tentative au rejeu. */
  cle: string;
  methode: 'POST' | 'PUT' | 'DELETE';
  url: string;
  corps?: string;
  /**
   * En-têtes propres à cette mutation, rejoués tels quels (SPRINT-24).
   *
   * <p>Le seul usage aujourd'hui est `X-Zumm-Version` : la version que
   * l'appareil avait sous les yeux quand la saisie a été faite. Sans elle, le
   * rejeu écraserait en silence ce qu'un autre agent a enregistré entre-temps —
   * et le dernier à retrouver du réseau gagnerait.
   */
  entetes?: Record<string, string>;
}

/** Une mutation que le serveur a refusée, et la raison qu'il en a donnée. */
export interface MutationRefusee {
  mutation: MutationEnAttente;
  statut: number;
  detail: string;
  /** `maj_le` du serveur, présent sur un conflit de version (409). */
  versionServeur?: string;
  refuseeLe: string;
}

/** Ce que l'émetteur rapporte au rejeu. */
export interface ResultatEnvoi {
  ok: boolean;
  reseau: boolean;
  session?: boolean;
  /** Motif d'un refus serveur : présent, la mutation part en quarantaine. */
  refus?: { statut: number; detail: string; versionServeur?: string };
}

const CLE = 'zumm.file.mutations';
const CLE_REFUS = 'zumm.file.refus';
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
  envoyer: (m: MutationEnAttente) => Promise<ResultatEnvoi>,
): Promise<void> {
  let file = charger();
  while (file.length > 0) {
    const mutation = file[0];
    const resultat = await envoyer(mutation);
    if (!resultat.ok && (resultat.reseau || resultat.session)) {
      return; // Hors-ligne ou session expirée : on réessaiera, sans rien perdre.
    }
    // Refusée par le serveur : elle sort de la file — la garder la bloquerait —
    // mais elle n'est pas perdue. Une saisie faite au rucher ne disparaît pas
    // parce qu'un autre agent est passé avant.
    if (resultat.refus) {
      quarantaine([
        ...refus(),
        {
          mutation,
          statut: resultat.refus.statut,
          detail: resultat.refus.detail,
          versionServeur: resultat.refus.versionServeur,
          refuseeLe: new Date().toISOString(),
        },
      ]);
    }
    file = charger().filter((m) => m.id !== mutation.id);
    sauver(file);
  }
}

// ─── Quarantaine : ce que le serveur a refusé ──────────────────────────────

function quarantaine(liste: MutationRefusee[]): void {
  localStorage.setItem(CLE_REFUS, JSON.stringify(liste));
}

/** Saisies refusées au rejeu, en attente d'un arbitrage humain. */
export function refus(): MutationRefusee[] {
  try {
    return JSON.parse(localStorage.getItem(CLE_REFUS) ?? '[]') as MutationRefusee[];
  } catch {
    return [];
  }
}

/**
 * Remet une saisie refusée dans la file, SANS sa garde de version.
 *
 * <p>C'est la décision « ma saisie l'emporte » : l'apiculteur a vu le motif du
 * refus et choisit d'écraser. Conserver `X-Zumm-Version` la ferait refuser une
 * seconde fois, en boucle.
 */
export function reappliquer(id: string): void {
  const refusee = refus().find((r) => r.mutation.id === id);
  if (!refusee) {
    return;
  }
  const { entetes, ...reste } = refusee.mutation;
  const sansVersion = Object.fromEntries(
    Object.entries(entetes ?? {}).filter(([nom]) => nom !== 'X-Zumm-Version'),
  );
  sauver([...charger(), { ...reste, entetes: sansVersion }]);
  quarantaine(refus().filter((r) => r.mutation.id !== id));
}

/** Abandonne définitivement une saisie refusée. */
export function abandonner(id: string): void {
  quarantaine(refus().filter((r) => r.mutation.id !== id));
}
