import { useEffect, useRef, type ReactElement, type ReactNode } from 'react';
import { gabarit } from '../i18n/console';
import { useT } from '../i18n/langue';

// ─── Bouton ───────────────────────────────────────────────────────────────

type Variante = 'primaire' | 'secondaire' | 'danger' | 'fantome';

export function Bouton({
  variante = 'secondaire',
  type = 'button',
  onClick,
  disabled,
  children,
}: {
  variante?: Variante;
  type?: 'button' | 'submit';
  onClick?: () => void;
  disabled?: boolean;
  children: ReactNode;
}): ReactElement {
  return (
    <button
      type={type}
      className={`z-btn z-btn--${variante}`}
      onClick={onClick}
      disabled={disabled}
    >
      {children}
    </button>
  );
}

// ─── Modale ─────────────────────────────────────────────────────────────────

export function Modale({
  titre,
  onFermer,
  children,
}: {
  titre: string;
  onFermer: () => void;
  children: ReactNode;
}): ReactElement {
  const t = useT();
  const boite = useRef<HTMLDivElement>(null);

  // `onFermer` est souvent une lambda recréée à chaque rendu du parent. La garder
  // dans les dépendances de l'effet le ferait rejouer à chaque frappe — donc
  // restituer le focus au déclencheur pendant que l'utilisateur tape. La référence
  // découple la fraîcheur du callback du cycle de vie de l'effet.
  const fermerRef = useRef(onFermer);
  fermerRef.current = onFermer;

  useEffect(() => {
    // Le focus doit revenir d'où il venait à la fermeture : sans cela, l'utilisateur
    // au clavier repart du début du document à chaque dialogue (US-054).
    const origine = document.activeElement as HTMLElement | null;

    const surTouche = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        fermerRef.current();
        return;
      }
      if (e.key !== 'Tab' || !boite.current) {
        return;
      }
      // Piège de focus : Tab boucle À L'INTÉRIEUR du dialogue. Sans cela, la
      // tabulation s'échappe vers la page de fond, qui est pourtant inerte.
      const focusables = boite.current.querySelectorAll<HTMLElement>(
        'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])',
      );
      if (focusables.length === 0) {
        return;
      }
      const premier = focusables[0];
      const dernier = focusables[focusables.length - 1];
      if (!e.shiftKey && document.activeElement === dernier) {
        e.preventDefault();
        premier.focus();
      } else if (e.shiftKey && (document.activeElement === premier || document.activeElement === boite.current)) {
        e.preventDefault();
        dernier.focus();
      }
    };

    document.addEventListener('keydown', surTouche);
    boite.current?.focus();
    return () => {
      document.removeEventListener('keydown', surTouche);
      origine?.focus?.();
    };
    // Volontairement monté une seule fois : cf. `fermerRef` ci-dessus.
  }, []);

  return (
    // `presentation` : le fond n'est pas un contrôle, il n'apporte qu'un raccourci
    // à la souris. Le clavier ferme par Échap, géré ci-dessus. La comparaison
    // `target === currentTarget` évite d'avoir à intercepter l'événement dans le
    // dialogue lui-même — un clic dedans ne remonte pas jusqu'au fond.
    <div
      className="z-overlay"
      role="presentation"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) {
          onFermer();
        }
      }}
    >
      <div
        className="z-modale"
        role="dialog"
        aria-modal="true"
        aria-label={titre}
        tabIndex={-1}
        ref={boite}
      >
        <header className="z-modale__entete">
          <h2 className="z-modale__titre">{titre}</h2>
          <button
            type="button"
            className="z-icone-btn"
            aria-label={t.actions.fermer}
            onClick={onFermer}
          >
            ✕
          </button>
        </header>
        {children}
      </div>
    </div>
  );
}

// ─── Pagination ───────────────────────────────────────────────────────────

/**
 * Barre de pagination d'une liste (US-052).
 *
 * <p>Ne s'affiche pas quand tout tient sur une page : une commande inerte est du
 * bruit. Les deux boutons sont de vrais boutons — donc atteignables au clavier —
 * et le compteur est annoncé aux lecteurs d'écran par `role="status"`.
 */
export function Pagination({
  page,
  taille,
  total,
  onPage,
}: {
  page: number;
  taille: number;
  total: number;
  onPage: (page: number) => void;
}): ReactElement | null {
  const t = useT();
  const pages = Math.max(1, Math.ceil(total / taille));
  if (total <= taille) {
    return null;
  }

  return (
    <nav className="z-pagination" aria-label={t.pagination.titre}>
      <Bouton onClick={() => onPage(page - 1)} disabled={page <= 0}>
        {t.pagination.precedent}
      </Bouton>
      <span className="z-pagination__etat" role="status">
        {gabarit(t.pagination.etat, {
          page: String(page + 1),
          pages: String(pages),
          total: String(total),
        })}
      </span>
      <Bouton onClick={() => onPage(page + 1)} disabled={page + 1 >= pages}>
        {t.pagination.suivant}
      </Bouton>
    </nav>
  );
}

// ─── Table ────────────────────────────────────────────────────────────────

export interface Colonne<E> {
  entete: string;
  rendu: (element: E) => ReactNode;
}

export function Table<E extends { id: number }>({
  colonnes,
  elements,
  onModifier,
  onSupprimer,
}: {
  colonnes: Colonne<E>[];
  elements: E[];
  onModifier: (element: E) => void;
  onSupprimer: (element: E) => void;
}): ReactElement {
  const t = useT();
  return (
    <div className="z-table-enveloppe">
      <table className="z-table">
        <thead>
          <tr>
            {colonnes.map((colonne) => (
              <th key={colonne.entete}>{colonne.entete}</th>
            ))}
            <th className="z-table__actions" aria-label={t.actions.modifier} />
          </tr>
        </thead>
        <tbody>
          {elements.map((element) => (
            <tr key={element.id}>
              {colonnes.map((colonne) => (
                <td key={colonne.entete}>{colonne.rendu(element)}</td>
              ))}
              <td className="z-table__actions">
                <button type="button" className="z-lien" onClick={() => onModifier(element)}>
                  {t.actions.modifier}
                </button>
                <button
                  type="button"
                  className="z-lien z-lien--danger"
                  onClick={() => onSupprimer(element)}
                >
                  {t.actions.supprimer}
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

// ─── Champs de formulaire ───────────────────────────────────────────────────

export function ChampTexte({
  libelle,
  valeur,
  onChange,
  requis,
}: {
  libelle: string;
  valeur: string;
  onChange: (valeur: string) => void;
  requis?: boolean;
}): ReactElement {
  return (
    <label className="z-champ">
      <span className="z-champ__libelle">{libelle}</span>
      <input
        className="z-input"
        value={valeur}
        required={requis}
        onChange={(e) => onChange(e.target.value)}
      />
    </label>
  );
}

export function ChampNombre({
  libelle,
  valeur,
  onChange,
  requis,
  pas = 'any',
}: {
  libelle: string;
  valeur: string;
  onChange: (valeur: string) => void;
  requis?: boolean;
  pas?: string;
}): ReactElement {
  return (
    <label className="z-champ">
      <span className="z-champ__libelle">{libelle}</span>
      <input
        className="z-input"
        type="number"
        step={pas}
        value={valeur}
        required={requis}
        onChange={(e) => onChange(e.target.value)}
      />
    </label>
  );
}

export function ChampDate({
  libelle,
  valeur,
  onChange,
  requis,
}: {
  libelle: string;
  valeur: string;
  onChange: (valeur: string) => void;
  requis?: boolean;
}): ReactElement {
  return (
    <label className="z-champ">
      <span className="z-champ__libelle">{libelle}</span>
      <input
        className="z-input"
        type="date"
        value={valeur}
        required={requis}
        onChange={(e) => onChange(e.target.value)}
      />
    </label>
  );
}

export function ChampZone({
  libelle,
  valeur,
  onChange,
}: {
  libelle: string;
  valeur: string;
  onChange: (valeur: string) => void;
}): ReactElement {
  return (
    <label className="z-champ">
      <span className="z-champ__libelle">{libelle}</span>
      <textarea
        className="z-input z-input--texte"
        rows={2}
        value={valeur}
        onChange={(e) => onChange(e.target.value)}
      />
    </label>
  );
}

export interface Option {
  valeur: string;
  libelle: string;
}

export function ChampSelect({
  libelle,
  valeur,
  options,
  onChange,
  requis,
}: {
  libelle: string;
  valeur: string;
  options: Option[];
  onChange: (valeur: string) => void;
  requis?: boolean;
}): ReactElement {
  return (
    <label className="z-champ">
      <span className="z-champ__libelle">{libelle}</span>
      <select
        className="z-input"
        value={valeur}
        required={requis}
        onChange={(e) => onChange(e.target.value)}
      >
        {options.map((option) => (
          <option key={option.valeur} value={option.valeur}>
            {option.libelle}
          </option>
        ))}
      </select>
    </label>
  );
}
