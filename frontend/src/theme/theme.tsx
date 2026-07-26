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
import { useT } from '../i18n/langue';

/**
 * Préférence de thème clair / sombre.
 *
 * <p><strong>Ce que ce module corrige.</strong> `theme/tokens.css` déclarait
 * `:root[data-theme='dark']` et `:root[data-theme='light']` depuis le SPRINT-13 —
 * et <strong>rien ne posait jamais cet attribut</strong>. Le thème suivait donc
 * `prefers-color-scheme`, sans recours : un apiculteur au rucher, en plein soleil,
 * ne pouvait pas forcer le mode clair alors que son téléphone bascule en sombre le
 * soir. La moitié de la couche de jetons était du code mort.
 *
 * <p>Trois valeurs et non deux : « auto » reste le défaut, et c'est le bon
 * défaut — l'application n'a aucune raison de contredire le réglage système tant
 * que l'utilisateur ne l'a pas demandé. Choisir explicitement clair ou sombre
 * <em>fige</em> la préférence, y compris quand le système bascule.
 */

export const THEMES = ['auto', 'clair', 'sombre'] as const;
export type Theme = (typeof THEMES)[number];

const CLE = 'zumm.theme';

/** Valeur d'`data-theme` attendue par les jetons ; `auto` retire l'attribut. */
const ATTRIBUT: Record<Theme, string | null> = {
  auto: null,
  clair: 'light',
  sombre: 'dark',
};

interface ContexteTheme {
  theme: Theme;
  definirTheme: (theme: Theme) => void;
}

const Contexte = createContext<ContexteTheme | null>(null);

function themeInitial(): Theme {
  const enregistre = localStorage.getItem(CLE);
  return THEMES.includes(enregistre as Theme) ? (enregistre as Theme) : 'auto';
}

export function ThemeProvider({ children }: { children: ReactNode }): ReactElement {
  const [theme, setTheme] = useState<Theme>(themeInitial);

  useEffect(() => {
    const racine = document.documentElement;
    const valeur = ATTRIBUT[theme];
    if (valeur === null) {
      racine.removeAttribute('data-theme');
    } else {
      racine.setAttribute('data-theme', valeur);
    }

    // La barre du navigateur et l'en-tête de la PWA installée suivent
    // `theme-color`. Sans cette ligne, une console en mode sombre garde une barre
    // claire : la coupure se voit d'autant plus que l'application est installée.
    //
    // Les valeurs sont celles de `--z-surface` — la couleur de la barre supérieure
    // de l'application, pour que la jointure avec le châssis du navigateur ne se
    // voie pas. Elles sont écrites en dur ici parce qu'`index.html` porte déjà la
    // valeur statique : un `getComputedStyle` sur la racine dépendrait du moment
    // où la feuille est appliquée.
    const meta = document.querySelector('meta[name="theme-color"]');
    if (meta) {
      const sombre =
        valeur === 'dark' ||
        (valeur === null &&
          typeof window.matchMedia === 'function' &&
          window.matchMedia('(prefers-color-scheme: dark)').matches);
      meta.setAttribute('content', sombre ? '#25423b' : '#f5f7f5');
    }
  }, [theme]);

  const definirTheme = useCallback((choix: Theme) => {
    setTheme(choix);
    localStorage.setItem(CLE, choix);
  }, []);

  const valeur = useMemo(() => ({ theme, definirTheme }), [theme, definirTheme]);
  return <Contexte.Provider value={valeur}>{children}</Contexte.Provider>;
}

export function useTheme(): ContexteTheme {
  const contexte = useContext(Contexte);
  if (contexte === null) {
    throw new Error('useTheme doit être utilisé dans un ThemeProvider.');
  }
  return contexte;
}

/**
 * Sélecteur de thème de la barre supérieure.
 *
 * <p>De vrais boutons, donc atteignables au clavier et annoncés comme tels.
 * `aria-pressed` plutôt qu'`aria-current` : il ne s'agit pas d'une position dans
 * une navigation mais d'un réglage à trois positions, dont une seule est active.
 */
export function SelecteurTheme(): ReactElement {
  const { theme, definirTheme } = useTheme();
  const t = useT();

  const libelles: Record<Theme, { symbole: string; nom: string }> = {
    auto: { symbole: '◐', nom: t.theme.auto },
    clair: { symbole: '☀', nom: t.theme.clair },
    sombre: { symbole: '☾', nom: t.theme.sombre },
  };

  return (
    <div className="z-theme" role="group" aria-label={t.theme.titre}>
      {THEMES.map((valeur) => (
        <button
          key={valeur}
          type="button"
          className="z-theme__option"
          aria-pressed={theme === valeur}
          // Le symbole seul ne se lit pas : le nom accessible porte le sens.
          aria-label={libelles[valeur].nom}
          title={libelles[valeur].nom}
          onClick={() => definirTheme(valeur)}
        >
          <span aria-hidden="true">{libelles[valeur].symbole}</span>
        </button>
      ))}
    </div>
  );
}
