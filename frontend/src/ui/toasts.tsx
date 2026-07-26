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

/**
 * Retour visible après une mutation, et annulation des suppressions.
 *
 * <p><strong>Ce que ce module corrige.</strong> Une création réussie ne produisait
 * aucun signal : la liste se rechargeait, et l'utilisateur devait chercher sa
 * ligne pour savoir si son geste avait abouti. Une suppression, elle, n'était
 * protégée que par une confirmation préalable — le seul dispositif dont on sait
 * qu'il est cliqué sans être lu quand il revient à chaque fois.
 *
 * <p><strong>Le choix : annuler plutôt que confirmer.</strong> La confirmation
 * coûte un geste à <em>toutes</em> les suppressions, y compris les 99 % qui sont
 * volontaires, et ne protège de rien une fois qu'elle est devenue un réflexe.
 * L'annulation ne coûte rien au cas courant et rattrape le seul cas qui compte.
 * Elle suppose en revanche que la suppression soit <strong>différée</strong> : un
 * « annuler » qui recrée l'objet côté serveur lui donnerait un nouvel
 * identifiant, casserait ses rattachements, et ne serait donc pas une annulation.
 *
 * <p>La région est `aria-live="polite"` : le message est annoncé sans couper ce
 * que le lecteur d'écran est en train de lire. `assertive` serait une
 * interruption, réservée à ce qui ne peut pas attendre.
 */

/** Délai pendant lequel une suppression reste annulable, en millisecondes. */
const DELAI_ANNULATION_MS = 8000;

/** Durée d'affichage d'un toast sans action, en millisecondes. */
const DELAI_SIMPLE_MS = 4000;

type Ton = 'succes' | 'erreur';

interface Toast {
  id: number;
  texte: string;
  ton: Ton;
  /** Libellé de l'action ; absent pour un toast purement informatif. */
  action?: string;
  onAction?: () => void;
}

interface Toasts {
  /** Signale une réussite. */
  succes: (texte: string) => void;
  /** Signale un échec. Reste affiché aussi longtemps qu'une réussite : un échec ne s'escamote pas. */
  erreur: (texte: string) => void;
  /**
   * Diffère une action destructive et laisse une fenêtre pour l'annuler.
   *
   * @param texte    ce qui vient d'être fait, au passé — l'utilisateur lit un
   *                 constat, pas une question
   * @param executer appelée à l'expiration du délai, si rien n'a annulé
   * @param annuler  appelée si l'utilisateur revient sur son geste
   */
  annulable: (texte: string, executer: () => void, annuler?: () => void) => void;
}

const Contexte = createContext<Toasts | null>(null);

export function ToastsProvider({ children }: { children: ReactNode }): ReactElement {
  const t = useT();
  const [toasts, setToasts] = useState<Toast[]>([]);
  const suivant = useRef(0);
  // Une minuterie par toast : les retirer au démontage évite qu'une mutation
  // d'état tombe sur un composant disparu (changement de route pendant le délai).
  const minuteries = useRef(new Map<number, ReturnType<typeof setTimeout>>());

  const retirer = useCallback((id: number) => {
    const minuterie = minuteries.current.get(id);
    if (minuterie !== undefined) {
      clearTimeout(minuterie);
      minuteries.current.delete(id);
    }
    setToasts((liste) => liste.filter((toast) => toast.id !== id));
  }, []);

  const ajouter = useCallback(
    (toast: Omit<Toast, 'id'>, delai: number, aLExpiration?: () => void) => {
      const id = (suivant.current += 1);
      setToasts((liste) => [...liste, { ...toast, id }]);
      minuteries.current.set(
        id,
        setTimeout(() => {
          minuteries.current.delete(id);
          setToasts((liste) => liste.filter((autre) => autre.id !== id));
          aLExpiration?.();
        }, delai),
      );
      return id;
    },
    [],
  );

  useEffect(() => {
    const enCours = minuteries.current;
    return () => {
      enCours.forEach(clearTimeout);
      enCours.clear();
    };
  }, []);

  const valeur = useMemo<Toasts>(
    () => ({
      succes: (texte) => {
        ajouter({ texte, ton: 'succes' }, DELAI_SIMPLE_MS);
      },
      erreur: (texte) => {
        ajouter({ texte, ton: 'erreur' }, DELAI_SIMPLE_MS);
      },
      annulable: (texte, executer, annuler) => {
        // L'identifiant n'est connu qu'après l'ajout, alors que le gestionnaire
        // d'annulation en a besoin : la référence le résout sans re-rendu.
        const reference = { id: 0 };
        reference.id = ajouter(
          {
            texte,
            ton: 'succes',
            action: t.actions.annuler,
            onAction: () => {
              retirer(reference.id);
              annuler?.();
            },
          },
          DELAI_ANNULATION_MS,
          executer,
        );
      },
    }),
    [ajouter, retirer, t.actions.annuler],
  );

  return (
    <Contexte.Provider value={valeur}>
      {children}
      {/* `role="status"` porte déjà `aria-live="polite"` ; il est répété pour les
          moteurs anciens qui n'en déduisent pas la politesse. */}
      <div className="z-toasts" role="status" aria-live="polite">
        {toasts.map((toast) => (
          <div key={toast.id} className={`z-toast z-toast--${toast.ton}`}>
            <span className="z-toast__texte">{toast.texte}</span>
            {toast.action && (
              <button type="button" className="z-toast__action" onClick={toast.onAction}>
                {toast.action}
              </button>
            )}
          </div>
        ))}
      </div>
    </Contexte.Provider>
  );
}

/**
 * Accès aux toasts.
 *
 * <p>Rend un objet <strong>inerte</strong> hors du fournisseur plutôt que de
 * lever : un signal de réussite est un agrément, et son absence ne doit jamais
 * empêcher l'opération elle-même d'aboutir. C'est aussi ce qui permet de tester
 * un composant isolément sans monter tout l'arbre.
 */
export function useToasts(): Toasts {
  const contexte = useContext(Contexte);
  return (
    contexte ?? {
      succes: () => {},
      erreur: () => {},
      // Hors fournisseur, l'action différée est exécutée SUR-LE-CHAMP : ne rien
      // faire perdrait la suppression demandée par l'utilisateur.
      annulable: (_texte, executer) => executer(),
    }
  );
}
