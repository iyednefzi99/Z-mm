import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactElement,
  type ReactNode,
} from 'react';
import { useT } from '../i18n/langue';
import { Bouton, Modale } from './composants';

/**
 * Dialogues de l'application (US-054, SPRINT-11).
 *
 * <p>La console appelait jusqu'ici `window.confirm`, `window.prompt` et
 * `window.alert` — douze fois, dans neuf vues. Ces boîtes ne sont pas traduites
 * (le navigateur impose ses propres libellés de boutons, en anglais sur un système
 * anglais, quelle que soit la langue de la console), ne suivent pas la charte, et
 * bloquent le fil d'exécution.
 *
 * <p>Les remplaçants gardent la même ergonomie d'appel — une promesse, `await` —
 * pour que le code des vues reste lisible :
 *
 * <pre>
 *   if (await confirmer(message)) { … }
 *   const motif = await demander(libelle);
 * </pre>
 *
 * <p><strong>Aucune vue n'appelle plus `confirmer` aujourd'hui.</strong> Les dix
 * confirmations de suppression ont été retirées : elles doublaient l'annulation
 * différée (`ui/toasts.tsx`) sur le même risque, en coûtant un geste aux
 * suppressions volontaires — c'est-à-dire à la quasi-totalité d'entre elles — et
 * en ne protégeant plus rien une fois devenues un réflexe.
 *
 * <p>La primitive est conservée, et non supprimée, parce que l'annulation ne
 * couvre pas tout : elle suppose que le geste puisse être *différé*. Une action
 * immédiate et irréversible — un envoi, une clôture réglementaire — ne peut pas
 * l'être et demandera une confirmation. `demander` reste, lui, utilisé pour le
 * motif de refus d'un planning.
 */

type Demande =
  | { genre: 'confirmation'; message: string; resoudre: (valeur: boolean) => void }
  | { genre: 'invite'; message: string; resoudre: (valeur: string | null) => void }
  | { genre: 'alerte'; message: string; resoudre: () => void };

interface Dialogues {
  /** Demande une confirmation ; rend true si l'utilisateur confirme. */
  confirmer: (message: string) => Promise<boolean>;
  /** Demande une saisie ; rend null si l'utilisateur annule. */
  demander: (message: string) => Promise<string | null>;
  /** Signale une information ou une erreur, sans choix à faire. */
  signaler: (message: string) => Promise<void>;
}

const Contexte = createContext<Dialogues | null>(null);

export function DialoguesProvider({ children }: { children: ReactNode }): ReactElement {
  const t = useT();
  const [demande, setDemande] = useState<Demande | null>(null);
  const [saisie, setSaisie] = useState('');
  const champ = useRef<HTMLInputElement>(null);

  // Une saisie doit être prête sous le curseur. `autoFocus` ferait la même chose
  // mais s'applique aussi hors dialogue, où il vole le focus à l'utilisateur.
  useEffect(() => {
    if (demande?.genre === 'invite') {
      champ.current?.focus();
    }
  }, [demande]);

  const confirmer = useCallback(
    (message: string) =>
      new Promise<boolean>((resoudre) => setDemande({ genre: 'confirmation', message, resoudre })),
    [],
  );

  const demander = useCallback(
    (message: string) =>
      new Promise<string | null>((resoudre) => {
        setSaisie('');
        setDemande({ genre: 'invite', message, resoudre });
      }),
    [],
  );

  const signaler = useCallback(
    (message: string) => new Promise<void>((resoudre) => setDemande({ genre: 'alerte', message, resoudre })),
    [],
  );

  const valeur = useMemo<Dialogues>(
    () => ({ confirmer, demander, signaler }),
    [confirmer, demander, signaler],
  );

  /** Referme le dialogue en rendant la réponse « négative » de son genre. */
  const annuler = () => {
    if (!demande) return;
    if (demande.genre === 'confirmation') demande.resoudre(false);
    else if (demande.genre === 'invite') demande.resoudre(null);
    else demande.resoudre();
    setDemande(null);
  };

  const valider = () => {
    if (!demande) return;
    if (demande.genre === 'confirmation') demande.resoudre(true);
    else if (demande.genre === 'invite') demande.resoudre(saisie);
    else demande.resoudre();
    setDemande(null);
  };

  return (
    <Contexte.Provider value={valeur}>
      {children}
      {demande && (
        <Modale titre={titre(demande.genre, t)} onFermer={annuler}>
          <form
            className="z-form"
            onSubmit={(e) => {
              e.preventDefault();
              valider();
            }}
          >
            {/* En saisie, le message EST le libellé du champ : l'afficher deux
                fois donnerait un dialogue bègue. */}
            {demande.genre === 'invite' ? (
              <label className="z-champ">
                <span className="z-champ__libelle">{demande.message}</span>
                <input
                  className="z-input"
                  ref={champ}
                  value={saisie}
                  onChange={(e) => setSaisie(e.target.value)}
                />
              </label>
            ) : (
              <p className="z-dialogue__message">{demande.message}</p>
            )}
            <div className="z-form__actions">
              {demande.genre !== 'alerte' && (
                <Bouton variante="fantome" onClick={annuler}>
                  {t.actions.annuler}
                </Bouton>
              )}
              <Bouton
                variante={demande.genre === 'confirmation' ? 'danger' : 'primaire'}
                type="submit"
              >
                {/* Pas « Fermer » : la croix de l'en-tête porte déjà ce nom, et
                    deux boutons homonymes égarent un lecteur d'écran. */}
                {demande.genre === 'alerte' ? t.dialogue.compris : t.actions.confirmer}
              </Bouton>
            </div>
          </form>
        </Modale>
      )}
    </Contexte.Provider>
  );
}

/** Dialogues de l'application. À utiliser sous {@link DialoguesProvider}. */
export function useDialogues(): Dialogues {
  const contexte = useContext(Contexte);
  if (!contexte) {
    throw new Error('useDialogues doit être utilisé dans un DialoguesProvider');
  }
  return contexte;
}

function titre(genre: Demande['genre'], t: ReturnType<typeof useT>): string {
  if (genre === 'confirmation') return t.dialogue.confirmation;
  if (genre === 'invite') return t.dialogue.saisie;
  return t.dialogue.information;
}
