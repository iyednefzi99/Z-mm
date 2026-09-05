import { afterEach, describe, expect, it, vi } from 'vitest';
import { demarrerDictee, etatDictee, langueReconnaissance } from './dictee';

/**
 * Dictée : la transcription se fait sur l'appareil, ou pas du tout
 * (SPRINT-30, lot G, ADR-013).
 *
 * <p>Tout ce fichier tourne autour d'un seul refus : ne pas envoyer la voix de
 * l'apiculteur à un service de reconnaissance sans le lui dire. C'est la
 * généralisation de la décision du SPRINT-24, où la note vocale était restée sur
 * l'appareil pour la même raison.
 */

interface FenetreSimulee {
  SpeechRecognition?: unknown;
  webkitSpeechRecognition?: unknown;
}

/** Un moteur qui n'expose AUCUN réglage de traitement local : Chrome de bureau. */
class MoteurDistant {
  lang = '';
  continuous = false;
  interimResults = false;
  onresult: unknown = null;
  onerror: unknown = null;
  onend: unknown = null;
  start = vi.fn();
  stop = vi.fn();
}

/** Un moteur qui déclare pouvoir transcrire sur l'appareil. */
class MoteurLocal extends MoteurDistant {
  processLocally = false;
}

const poser = (moteur: unknown) => {
  (window as unknown as FenetreSimulee).SpeechRecognition = moteur;
};

afterEach(() => {
  delete (window as unknown as FenetreSimulee).SpeechRecognition;
  delete (window as unknown as FenetreSimulee).webkitSpeechRecognition;
});

describe('état de la dictée', () => {
  it('dit « absente » quand le navigateur n’a pas l’API', () => {
    // Firefox, et tout navigateur ancien. L'écran retire alors le bouton
    // plutôt que d'en offrir un qui ne réagit pas.
    expect(etatDictee()).toBe('absente');
  });

  it('dit « distante » quand rien ne garantit le traitement local', () => {
    poser(MoteurDistant);

    // La détection est volontairement conservatrice : à défaut de preuve, la
    // position prudente est celle qui n'envoie rien.
    expect(etatDictee()).toBe('distante');
  });

  it('dit « locale » quand le moteur expose un réglage de traitement sur l’appareil', () => {
    poser(MoteurLocal);

    expect(etatDictee()).toBe('locale');
  });
});

describe('démarrage', () => {
  it('refuse de démarrer si la transcription ne peut pas être locale', () => {
    poser(MoteurDistant);

    const session = demarrerDictee('fr-FR', () => undefined, () => undefined, () => undefined);

    // Le refus est le comportement, pas un échec : envoyer la voix à un
    // fournisseur sans le dire serait exactement ce que l'ADR-013 interdit.
    expect(session).toBeNull();
  });

  it('demande explicitement le traitement local, sous les deux noms', () => {
    poser(MoteurLocal);

    const session = demarrerDictee('ar-TN', () => undefined, () => undefined, () => undefined);

    expect(session).not.toBeNull();
  });

  it('ne transmet que les segments finaux', () => {
    poser(MoteurLocal);
    const recus: string[] = [];
    let instance: MoteurLocal | null = null;
    poser(
      class extends MoteurLocal {
        constructor() {
          super();
          // eslint-disable-next-line @typescript-eslint/no-this-alias
          instance = this;
        }
      },
    );

    demarrerDictee('fr-FR', (segment) => recus.push(segment), () => undefined, () => undefined);
    const surResultat = instance!.onresult as (e: unknown) => void;
    surResultat({
      resultIndex: 0,
      results: [
        Object.assign([{ transcript: '  couvain compact  ' }], { isFinal: true }),
        Object.assign([{ transcript: 'et rese' }], { isFinal: false }),
      ],
    });

    // Les résultats intermédiaires changent à chaque syllabe : les écrire dans
    // le champ donnerait un texte qui se réécrit sous les doigts.
    expect(recus).toEqual(['couvain compact']);
  });
});

describe('langue de reconnaissance', () => {
  it('donne une étiquette BCP-47 par langue de l’interface', () => {
    // Une reconnaissance lancée dans la mauvaise langue ne se trompe pas un
    // peu : elle rend du charabia. L'arabe y est traité comme les deux autres.
    expect(langueReconnaissance('fr')).toBe('fr-FR');
    expect(langueReconnaissance('en')).toBe('en-GB');
    expect(langueReconnaissance('ar')).toBe('ar-TN');
    expect(langueReconnaissance('xx')).toBe('fr-FR');
  });
});
