import {
  useCallback,
  useEffect,
  useId,
  useRef,
  useState,
  type ReactElement,
  type ReactNode,
} from 'react';
import { gabarit } from '../i18n/console';
import { useT } from '../i18n/langue';

/**
 * Duree de la sortie de modale, en millisecondes.
 *
 * <p>Doit rester ALIGNEE sur `--z-modal-close-dur` (theme/tokens.css). Le CSS
 * anime, le JavaScript demonte : si le second va plus vite, le noeud disparait
 * pendant l'animation et la sortie ne se voit pas.
 */
const DUREE_FERMETURE_MS = 150;

/** L'utilisateur a-t-il demande la reduction des animations ? */
function mouvementReduit(): boolean {
  return (
    typeof window !== 'undefined' &&
    typeof window.matchMedia === 'function' &&
    window.matchMedia('(prefers-reduced-motion: reduce)').matches
  );
}

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

// ─── Pastille d'état ──────────────────────────────────────────────────────

export type TonPastille = 'succes' | 'attention' | 'danger' | 'neutre';

/**
 * Étiquette d'état — niveau d'alerte, santé d'une colonie, statut d'une tâche.
 *
 * <p><strong>Ce qu'elle corrige.</strong> Les états sortaient en texte nu dans
 * une colonne de tableau, et la gravité n'était portée que par la teinte du fond
 * de ligne. Deux défauts : sur une liste longue, le regard ne trie pas des mots
 * de même graisse ; et une information encodée par la seule couleur disparaît
 * pour les 8 % de personnes qui ne les distinguent pas. La pastille double donc
 * systématiquement la couleur d'un <strong>point plein</strong> et d'un fond
 * teinté, et garde le libellé traduit — jamais un point seul.
 *
 * @param ton gravité ; `neutre` pour un état sans enjeu, ou une donnée absente
 */
export function Pastille({
  ton = 'neutre',
  children,
}: {
  ton?: TonPastille;
  children: ReactNode;
}): ReactElement {
  return (
    <span className={`z-pastille z-pastille--${ton}`}>
      <span className="z-pastille__point" aria-hidden="true" />
      {children}
    </span>
  );
}

// ─── Modale ─────────────────────────────────────────────────────────────────

/**
 * Dialogue modal, ouvert ET fermé avec transition (`design/motion/dialog.md`).
 *
 * <p><strong>Pourquoi la modale retarde elle-même sa fermeture.</strong> Les seize
 * vues l'appellent sous la forme `{ouvert && <Modale …/>}` : dès que le parent
 * repasse à `false`, React démonte le nœud, et une animation de sortie n'a plus
 * de support sur lequel jouer. C'est pourquoi la modale disparaissait net alors
 * qu'elle s'ouvrait en fondu.
 *
 * <p>Plutôt que de réécrire seize appels, la modale intercepte la fermeture :
 * elle passe en `is-closing`, laisse l'animation se jouer, puis appelle
 * `onFermer` — le parent démonte alors un nœud dont la sortie est terminée. Le
 * piège classique (« la modale saute à la réouverture ») vient de l'ordre
 * inverse : retirer l'état de fermeture avant la fin de l'animation.
 *
 * <p>Sous `prefers-reduced-motion`, le délai est supprimé — pas seulement
 * l'animation. Attendre 150 ms sans rien montrer serait une latence pure.
 */
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
  // Rendu FERMÉ au premier passage, ouvert au suivant : sans ce décalage d'une
  // image, le navigateur ne voit qu'un seul état et n'interpole rien.
  const [ouvert, setOuvert] = useState(false);
  const [fermeture, setFermeture] = useState(false);
  const minuterie = useRef<ReturnType<typeof setTimeout> | null>(null);

  // `onFermer` est souvent une lambda recréée à chaque rendu du parent. La garder
  // dans les dépendances de l'effet le ferait rejouer à chaque frappe — donc
  // restituer le focus au déclencheur pendant que l'utilisateur tape. La référence
  // découple la fraîcheur du callback du cycle de vie de l'effet.
  const fermerRef = useRef(onFermer);
  fermerRef.current = onFermer;

  /**
   * Joue la sortie, puis prévient le parent.
   *
   * <p>Idempotent : un double Échap, ou un Échap suivi d'un clic sur le fond, ne
   * déclenche qu'une seule fermeture. Sans cette garde, deux minuteries se
   * chevaucheraient et `onFermer` serait appelé deux fois.
   *
   * <p>Stable par construction — elle ne ferme que sur des références et des
   * fonctions d'état, toutes invariantes.
   */
  const demanderFermeture = useCallback(() => {
    if (minuterie.current !== null) {
      return;
    }
    if (mouvementReduit()) {
      fermerRef.current();
      return;
    }
    setFermeture(true);
    minuterie.current = setTimeout(() => fermerRef.current(), DUREE_FERMETURE_MS);
  }, []);

  useEffect(() => {
    // Une image après le montage : c'est ce qui déclenche la transition d'entrée.
    const image = requestAnimationFrame(() => setOuvert(true));
    return () => {
      cancelAnimationFrame(image);
      if (minuterie.current !== null) {
        clearTimeout(minuterie.current);
      }
    };
  }, []);

  useEffect(() => {
    // Le focus doit revenir d'où il venait à la fermeture : sans cela, l'utilisateur
    // au clavier repart du début du document à chaque dialogue (US-054).
    const origine = document.activeElement as HTMLElement | null;

    const surTouche = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        demanderFermeture();
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
    // Monté une seule fois : cf. `fermerRef` ci-dessus. `demanderFermeture` est
    // stable, sa présence en dépendance ne rejoue donc jamais l'effet.
  }, [demanderFermeture]);

  // `is-closing` prime sur `is-open` : pendant la sortie, l'élément doit suivre la
  // règle de fermeture, pas rester sur celle d'ouverture.
  const etat = fermeture ? 'is-closing' : ouvert ? 'is-open' : '';

  return (
    // `presentation` : le fond n'est pas un contrôle, il n'apporte qu'un raccourci
    // à la souris. Le clavier ferme par Échap, géré ci-dessus. La comparaison
    // `target === currentTarget` évite d'avoir à intercepter l'événement dans le
    // dialogue lui-même — un clic dedans ne remonte pas jusqu'au fond.
    <div
      className={`z-overlay ${etat}`.trimEnd()}
      role="presentation"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) {
          demanderFermeture();
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
            onClick={demanderFermeture}
          >
            ✕
          </button>
        </header>
        {children}
      </div>
    </div>
  );
}

// ─── Squelette de chargement ──────────────────────────────────────────────

/**
 * Silhouette d'une liste en cours de chargement.
 *
 * <p>Remplace la phrase « Chargement… ». Un squelette dit deux choses qu'une
 * phrase ne dit pas : que la réponse aura la forme d'une table, et combien de
 * lignes environ. L'écran ne se réorganise donc pas à l'arrivée des données.
 *
 * <p>Il est <strong>invisible pour les lecteurs d'écran</strong> : décrire des
 * rectangles n'apporte rien. L'attente est annoncée une seule fois, par la région
 * `role="status"` de la section qui l'entoure.
 *
 * @param lignes nombre de lignes esquissées ; 5 correspond à ce qu'une liste
 *               courte affiche sans faire défiler
 */
export function Squelette({ lignes = 5 }: { lignes?: number }): ReactElement {
  return (
    <div className="z-squelette" aria-hidden="true">
      {Array.from({ length: lignes }, (_, i) => (
        <div key={i} className="z-squelette__ligne" />
      ))}
    </div>
  );
}

// ─── État vide ────────────────────────────────────────────────────────────

/**
 * Écran d'une liste vide.
 *
 * <p>Une liste vide n'est pas une erreur : pour un nouvel utilisateur, c'est le
 * <strong>premier</strong> écran. Il porte donc une action, pas une constatation —
 * « Aucun élément » laisse l'utilisateur devant un cul-de-sac alors que la seule
 * chose à faire est de créer le premier.
 *
 * @param action bouton d'amorçage ; omis quand la vue ne crée rien elle-même
 *               (une liste filtrée, par exemple, se corrige en changeant le filtre)
 */
export function EtatVide({
  pictogramme = '🐝',
  titre,
  texte,
  action,
}: {
  pictogramme?: string;
  titre: string;
  texte?: string;
  action?: ReactNode;
}): ReactElement {
  return (
    <div className="z-vide">
      {/* Décoratif : le titre porte déjà le sens, l'annoncer serait un doublon. */}
      <span className="z-vide__pictogramme" aria-hidden="true">
        {pictogramme}
      </span>
      <p className="z-vide__titre">{titre}</p>
      {texte && <p className="z-vide__texte">{texte}</p>}
      {action && <div className="z-vide__action">{action}</div>}
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
  type = 'text',
  autoComplete,
  aide,
  invalide,
}: {
  libelle: string;
  valeur: string;
  onChange: (valeur: string) => void;
  requis?: boolean;
  /** `password` et `email` amenent clavier adapte et gestionnaire de mots de passe. */
  type?: 'text' | 'email' | 'password';
  /** Indispensable sur un formulaire d'identite : sans lui, le navigateur
   *  propose n'importe quoi, et l'utilisateur renonce au gestionnaire. */
  autoComplete?: string;
  /** Consigne affichee sous le champ, reliee par `aria-describedby`. */
  aide?: string;
  invalide?: boolean;
}): ReactElement {
  // Un identifiant stable par champ : `aria-describedby` doit pointer sur un
  // noeud existant, et deux champs d'aide ne peuvent pas partager le meme id.
  const idChamp = useId();
  const idLibelle = `${idChamp}-libelle`;
  const idAide = `${idChamp}-aide`;
  return (
    <label className="z-champ">
      <span className="z-champ__libelle" id={idLibelle}>
        {libelle}
      </span>
      <input
        className="z-input"
        type={type}
        value={valeur}
        required={requis}
        autoComplete={autoComplete}
        aria-invalid={invalide || undefined}
        // Le nom accessible vient du seul libelle. Sans ce pointage, l'enveloppe
        // `<label>` donnerait au champ le texte de TOUS ses descendants, consigne
        // comprise : le champ s'appellerait « Mot de passe 12 caracteres… ».
        aria-labelledby={idLibelle}
        aria-describedby={aide ? idAide : undefined}
        onChange={(e) => onChange(e.target.value)}
      />
      {aide ? (
        <span className="z-champ__aide" id={idAide}>
          {aide}
        </span>
      ) : null}
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

/**
 * Heure seule, sans date.
 *
 * <p>`input type="time"` rend « HH:MM », que `LocalTime` accepte : le contrat
 * publie « 14:30:00 », mais `ISO_LOCAL_TIME` lit les deux. Pas de conversion
 * côté client, donc rien à maintenir en parallèle du contrat.
 */
export function ChampHeure({
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
      <input
        className="z-input"
        type="time"
        value={valeur}
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
