import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactElement,
  type ReactNode,
} from 'react';
import {
  CHARGEURS,
  LANGUE_SOURCE,
  TRADUCTIONS_SOURCE,
  type Traductions,
} from './console';
import { formatsDe, type Formats } from './formats';
import { LANGUES, direction, type Langue } from './messages';

interface ContexteLangue {
  langue: Langue;
  definirLangue: (langue: Langue) => void;
  t: Traductions;
}

/**
 * Ressources déjà chargées. Le module dynamique est déjà mis en cache par le
 * bundler ; ce cache-ci évite en plus le passage par une micro-tâche, donc le
 * clignotement des libellés quand on revient sur une langue déjà visitée.
 */
const CHARGEES = new Map<Langue, Traductions>([[LANGUE_SOURCE, TRADUCTIONS_SOURCE]]);

const Contexte = createContext<ContexteLangue | null>(null);
const CLE = 'zumm.langue';

function langueInitiale(): Langue {
  const enregistree = localStorage.getItem(CLE);
  return LANGUES.includes(enregistree as Langue) ? (enregistree as Langue) : 'fr';
}

/**
 * Fournit la langue courante et ses traductions (US-024). Applique la langue et
 * la direction (RTL en arabe) au document, et persiste le choix.
 */
export function LangueProvider({ children }: { children: ReactNode }): ReactElement {
  const [langue, setLangue] = useState<Langue>(langueInitiale);
  const [t, setT] = useState<Traductions>(() => CHARGEES.get(langueInitiale()) ?? TRADUCTIONS_SOURCE);

  useEffect(() => {
    const racine = document.documentElement;
    racine.lang = langue;
    racine.dir = direction(langue);
  }, [langue]);

  // Chargement de la ressource de langue (SPRINT-15). En attendant, l'affichage
  // reste sur les libellés précédents plutôt que de se vider : un écran blanc
  // pour un changement de langue serait pire que quelques dizaines de
  // millisecondes de décalage. La direction du document, elle, bascule tout de
  // suite — c'est la mise en page, pas le contenu.
  useEffect(() => {
    const deja = CHARGEES.get(langue);
    if (deja) {
      setT(deja);
      return;
    }
    let annule = false;
    void CHARGEURS[langue as Exclude<Langue, typeof LANGUE_SOURCE>]().then((module) => {
      CHARGEES.set(langue, module.default);
      // La langue a pu changer de nouveau pendant le chargement : sans cette
      // garde, une ressource arrivée en retard écraserait la langue courante.
      if (!annule) {
        setT(module.default);
      }
    });
    return () => {
      annule = true;
    };
  }, [langue]);

  const definirLangue = useCallback((choix: Langue) => {
    localStorage.setItem(CLE, choix);
    setLangue(choix);
  }, []);

  const valeur = useMemo<ContexteLangue>(
    () => ({ langue, definirLangue, t }),
    [langue, definirLangue, t],
  );

  return <Contexte.Provider value={valeur}>{children}</Contexte.Provider>;
}

function useContexteLangue(): ContexteLangue {
  const contexte = useContext(Contexte);
  if (!contexte) {
    throw new Error('useLangue doit être utilisé dans un LangueProvider');
  }
  return contexte;
}

/** Traductions de la langue courante. */
export const useT = (): Traductions => useContexteLangue().t;

/** Formateurs de dates et de nombres de la langue courante (US-053). */
export function useFormats(): Formats {
  const { langue } = useContexteLangue();
  return useMemo(() => formatsDe(langue), [langue]);
}

/** Langue courante et sélecteur. */
export function useLangue(): { langue: Langue; definirLangue: (l: Langue) => void } {
  const { langue, definirLangue } = useContexteLangue();
  return { langue, definirLangue };
}
