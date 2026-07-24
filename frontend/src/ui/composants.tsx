import { useEffect, useRef, type ReactElement, type ReactNode } from 'react';
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

  useEffect(() => {
    const surTouche = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        onFermer();
      }
    };
    document.addEventListener('keydown', surTouche);
    boite.current?.focus();
    return () => document.removeEventListener('keydown', surTouche);
  }, [onFermer]);

  return (
    <div className="z-overlay" onMouseDown={onFermer}>
      <div
        className="z-modale"
        role="dialog"
        aria-modal="true"
        aria-label={titre}
        tabIndex={-1}
        ref={boite}
        onMouseDown={(e) => e.stopPropagation()}
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
