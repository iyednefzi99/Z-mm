/**
 * Dictée : la transcription se fait sur l'appareil, ou pas du tout
 * (SPRINT-30, lot G, [ADR-013]).
 *
 * <p>Ferme la ligne « saisie vocale des observations » du §4 — « le seul geste
 * qui fonctionne avec des gants » — et sa jumelle « transcription IA embarquée,
 * hors ligne ».
 *
 * <p><strong>Un seul point d'entrée, et c'est le point.</strong> Tout ce que
 * l'application sait de la reconnaissance vocale passe par ce fichier : lui
 * donner un second moteur — Whisper WASM, ou l'API de reconnaissance sur
 * l'appareil quand elle sera partout — ne touchera aucun écran.
 *
 * <p><strong>La règle que ce module fait respecter</strong> : si le navigateur
 * ne peut pas transcrire localement, on ne transcrit pas. Chrome de bureau route
 * l'audio vers un service de reconnaissance ; envoyer la voix de l'apiculteur à
 * un fournisseur sans le lui dire serait exactement ce que le SPRINT-24 avait
 * refusé pour la note vocale, qui était restée sur l'appareil pour cette raison.
 */

/** Ce que le navigateur peut faire, tel qu'on peut le vérifier. */
export type EtatDictee =
  /** Aucune API de reconnaissance : Firefox, et les navigateurs anciens. */
  | 'absente'
  /** Présente, mais rien ne garantit qu'elle traite localement. */
  | 'distante'
  /** Présente et déclarée locale : c'est le seul cas où l'on dicte. */
  | 'locale';

/**
 * Constructeur de reconnaissance vocale, quel que soit son préfixe.
 *
 * <p>Typé ici plutôt qu'importé : `SpeechRecognition` n'est pas dans les types
 * DOM standards, et déclarer un `any` global rendrait invisible ce qu'on utilise
 * réellement — trois propriétés et deux événements.
 */
interface ReconnaissanceVocale {
  lang: string;
  continuous: boolean;
  interimResults: boolean;
  /** Chrome : demande explicitement le traitement sur l'appareil. */
  processLocally?: boolean;
  /** Safari : même demande, sous son propre nom. */
  requiresOnDeviceRecognition?: boolean;
  start: () => void;
  stop: () => void;
  onresult: ((evenement: EvenementResultat) => void) | null;
  onerror: ((evenement: { error: string }) => void) | null;
  onend: (() => void) | null;
}

interface EvenementResultat {
  resultIndex: number;
  results: ArrayLike<ArrayLike<{ transcript: string }> & { isFinal: boolean }>;
}

type FabriqueReconnaissance = new () => ReconnaissanceVocale;

interface FenetreVocale {
  SpeechRecognition?: FabriqueReconnaissance;
  webkitSpeechRecognition?: FabriqueReconnaissance;
}

function fabrique(): FabriqueReconnaissance | null {
  if (typeof window === 'undefined') {
    return null;
  }
  const w = window as unknown as FenetreVocale;
  return w.SpeechRecognition ?? w.webkitSpeechRecognition ?? null;
}

/**
 * Ce que cet appareil permet.
 *
 * <p>La détection est **volontairement conservatrice** : on ne conclut à
 * `locale` que si le navigateur expose un réglage de traitement sur l'appareil.
 * Rien ne permet de vérifier qu'il l'honore ; à défaut de preuve, la position
 * prudente est celle qui n'envoie rien.
 */
export function etatDictee(): EtatDictee {
  const Fabrique = fabrique();
  if (Fabrique === null) {
    return 'absente';
  }
  const sonde = new Fabrique();
  const local = 'processLocally' in sonde || 'requiresOnDeviceRecognition' in sonde;
  return local ? 'locale' : 'distante';
}

/** Une dictée en cours : on l'arrête, on ne la relance pas. */
export interface DicteeEnCours {
  arreter: () => void;
}

/**
 * Démarre une dictée, et refuse si l'appareil ne peut pas transcrire localement.
 *
 * <p>Le texte arrive par morceaux, et seuls les segments **finaux** sont
 * transmis : les résultats intermédiaires changent à chaque syllabe, et les
 * écrire dans un champ donnerait un texte qui se réécrit sous les doigts.
 *
 * <p>Le résultat n'est jamais enregistré tel quel : il atterrit dans un champ
 * modifiable. Le §4 le disait déjà — la relecture avant validation est le vrai
 * sujet, et une transcription approximative validée sans relecture vaut moins
 * qu'une case cochée.
 *
 * @param langue étiquette BCP-47 : `fr-FR`, `en-GB`, `ar-TN`
 * @param onTexte appelé pour chaque segment final, à concaténer par l'appelant
 */
export function demarrerDictee(
  langue: string,
  onTexte: (segment: string) => void,
  onFin: () => void,
  onErreur: (code: string) => void,
): DicteeEnCours | null {
  const Fabrique = fabrique();
  if (Fabrique === null || etatDictee() !== 'locale') {
    return null;
  }
  const reconnaissance = new Fabrique();
  reconnaissance.lang = langue;
  reconnaissance.continuous = true;
  reconnaissance.interimResults = false;
  // Les deux noms, parce que les deux navigateurs ne s'accordent pas. Poser
  // celui qu'un moteur ignore est sans effet ; ne pas poser celui qu'il honore
  // enverrait l'audio dehors.
  reconnaissance.processLocally = true;
  reconnaissance.requiresOnDeviceRecognition = true;

  reconnaissance.onresult = (evenement) => {
    for (let i = evenement.resultIndex; i < evenement.results.length; i += 1) {
      const resultat = evenement.results[i];
      if (resultat.isFinal) {
        onTexte(resultat[0].transcript.trim());
      }
    }
  };
  reconnaissance.onerror = (evenement) => onErreur(evenement.error);
  reconnaissance.onend = onFin;
  reconnaissance.start();

  return { arreter: () => reconnaissance.stop() };
}

/**
 * Étiquette de reconnaissance pour une langue de l'interface.
 *
 * <p>Une reconnaissance lancée dans la mauvaise langue ne se trompe pas un peu :
 * elle rend du charabia. La correspondance est donc explicite, et l'arabe y est
 * traité comme les deux autres — c'est la langue pour laquelle il aurait été le
 * plus tentant de ne rien faire.
 */
export function langueReconnaissance(langue: string): string {
  switch (langue) {
    case 'en':
      return 'en-GB';
    case 'ar':
      return 'ar-TN';
    default:
      return 'fr-FR';
  }
}
