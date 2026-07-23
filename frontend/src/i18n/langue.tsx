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
import { CONSOLE, type Traductions } from './console';
import { LANGUES, direction, type Langue } from './messages';

interface ContexteLangue {
  langue: Langue;
  definirLangue: (langue: Langue) => void;
  t: Traductions;
}

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

  useEffect(() => {
    const racine = document.documentElement;
    racine.lang = langue;
    racine.dir = direction(langue);
  }, [langue]);

  const definirLangue = useCallback((choix: Langue) => {
    localStorage.setItem(CLE, choix);
    setLangue(choix);
  }, []);

  const valeur = useMemo<ContexteLangue>(
    () => ({ langue, definirLangue, t: CONSOLE[langue] }),
    [langue, definirLangue],
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

/** Langue courante et sélecteur. */
export function useLangue(): { langue: Langue; definirLangue: (l: Langue) => void } {
  const { langue, definirLangue } = useContexteLangue();
  return { langue, definirLangue };
}
