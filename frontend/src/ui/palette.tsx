import { useEffect, useMemo, useRef, useState, type ReactElement } from 'react';
import { useT } from '../i18n/langue';
import {
  GROUPES,
  GROUPES_CLES,
  ICONES,
  type Groupe,
  type Onglet,
} from '../routage/routes';

/**
 * Palette de commandes (Ctrl/⌘ + K) — accès direct aux seize écrans.
 *
 * <p><strong>Pourquoi.</strong> La navigation groupée règle la lisibilité, pas la
 * distance : atteindre « Lots & origines » depuis « Visites » demande toujours de
 * viser une cible dans une liste de seize. La palette est l'accélérateur, la
 * navigation reste la voie visible — un raccourci que personne ne découvre ne
 * navigue personne. C'est pourquoi la barre supérieure porte un bouton qui
 * l'ouvre <em>et</em> affiche le raccourci.
 *
 * <p><strong>Filtrage par sous-séquence, pas par sous-chaîne.</strong> « lts »
 * doit trouver « Lots & origines » et « tbx » « Tableaux de bord ». Un
 * `includes()` ne rendrait rien sur ces frappes-là, et une palette qui répond
 * « aucun résultat » à une abréviation plausible passe pour cassée.
 *
 * <p>La palette ne s'ouvre jamais sur le vide : sans saisie, elle liste les seize
 * écrans, groupés comme dans la navigation.
 */

/** Retire les diacritiques et la casse : « récoltes » se trouve en tapant « recoltes ». */
function normaliser(texte: string): string {
  return texte
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase();
}

/**
 * La requête est-elle une sous-séquence de la cible ?
 *
 * <p>Chaque caractère cherché doit apparaître dans l'ordre, pas nécessairement
 * côte à côte. Une requête vide correspond à tout — c'est ce qui fait que la
 * palette s'ouvre pleine.
 */
export function correspond(requete: string, cible: string): boolean {
  const q = normaliser(requete.trim());
  if (q === '') {
    return true;
  }
  const c = normaliser(cible);
  let i = 0;
  for (const caractere of c) {
    if (caractere === q[i]) {
      i += 1;
      if (i === q.length) {
        return true;
      }
    }
  }
  return false;
}

export function PaletteCommandes({
  onChoisir,
  onFermer,
}: {
  onChoisir: (onglet: Onglet) => void;
  onFermer: () => void;
}): ReactElement {
  const t = useT();
  const [requete, setRequete] = useState('');
  const [actif, setActif] = useState(0);
  // Rendu fermé au premier passage puis ouvert au suivant : sans ce décalage
  // d'une image, le navigateur ne voit qu'un état et n'interpole rien. Même
  // mécanique que `Modale` — la charte n'a qu'une façon d'ouvrir un dialogue.
  const [ouvert, setOuvert] = useState(false);
  const champ = useRef<HTMLInputElement>(null);
  const liste = useRef<HTMLUListElement>(null);

  /** Écrans retenus, à plat et dans l'ordre d'affichage : c'est l'index du curseur. */
  const resultats = useMemo(() => {
    const retenus: { groupe: Groupe; onglet: Onglet }[] = [];
    for (const groupe of GROUPES_CLES) {
      for (const onglet of GROUPES[groupe]) {
        // Le nom de la famille est cherché lui aussi : taper « terrain » sort les
        // quatre écrans de terrain, ce qu'aucun libellé d'onglet ne permet.
        if (
          correspond(requete, t.onglets[onglet]) ||
          correspond(requete, t.groupes[groupe])
        ) {
          retenus.push({ groupe, onglet });
        }
      }
    }
    return retenus;
  }, [requete, t]);

  // Le curseur revient en tête à chaque frappe : le laisser en place le ferait
  // pointer un écran qui n'est plus celui que l'utilisateur regarde.
  useEffect(() => setActif(0), [requete]);

  useEffect(() => {
    const image = requestAnimationFrame(() => setOuvert(true));
    champ.current?.focus();
    return () => cancelAnimationFrame(image);
  }, []);

  // Le curseur suit le clavier, y compris hors du cadre visible : sans cela, la
  // dixième entrée se sélectionne sans jamais apparaître.
  //
  // `scrollIntoView` est appelé en optionnel : jsdom ne l'implémente pas, et une
  // palette ne doit pas dépendre d'un confort de défilement pour se monter.
  useEffect(() => {
    const selectionnee = liste.current?.querySelector('[aria-selected="true"]');
    selectionnee?.scrollIntoView?.({ block: 'nearest' });
  }, [actif]);

  const surTouche = (e: React.KeyboardEvent) => {
    if (e.key === 'Escape') {
      e.preventDefault();
      onFermer();
      return;
    }
    if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
      e.preventDefault();
      if (resultats.length === 0) {
        return;
      }
      const pas = e.key === 'ArrowDown' ? 1 : -1;
      // Boucle : arrivé en bas, on repart en haut. Buter en silence sur la
      // dernière ligne laisse croire que la touche ne répond plus.
      setActif((i) => (i + pas + resultats.length) % resultats.length);
      return;
    }
    if (e.key === 'Enter' && resultats[actif]) {
      e.preventDefault();
      onChoisir(resultats[actif].onglet);
      return;
    }
    if (e.key === 'Tab') {
      // Piège de focus, réduit à sa plus simple expression : la palette n'a qu'un
      // seul élément focusable, le champ. Les options se parcourent aux flèches
      // et portent `tabindex="-1"`. Sans cette garde, Tab renverrait le focus sur
      // la page de fond alors que le dialogue est toujours ouvert.
      e.preventDefault();
      champ.current?.focus();
    }
  };

  let groupePrecedent: Groupe | null = null;

  return (
    <div
      className={`z-overlay z-overlay--haut ${ouvert ? 'is-open' : ''}`.trimEnd()}
      role="presentation"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) {
          onFermer();
        }
      }}
    >
      <div
        className="z-palette"
        role="dialog"
        aria-modal="true"
        aria-label={t.palette.titre}
      >
        <div className="z-palette__barre">
          <span className="z-palette__loupe" aria-hidden="true">
            🔍
          </span>
          <input
            ref={champ}
            className="z-palette__champ"
            type="text"
            value={requete}
            placeholder={t.palette.invite}
            aria-label={t.palette.invite}
            // Le champ pilote une liste : le lecteur d'écran doit annoncer
            // l'option atteinte au clavier, pas seulement le texte tapé.
            role="combobox"
            aria-expanded="true"
            aria-controls="z-palette-liste"
            aria-activedescendant={
              resultats[actif] ? `z-palette-${resultats[actif].onglet}` : undefined
            }
            autoComplete="off"
            onChange={(e) => setRequete(e.target.value)}
            // Le clavier est ecoute SUR LE CHAMP, et non sur le conteneur de
            // dialogue. Deux raisons : c'est la ou le focus se trouve — il y est
            // place a l'ouverture (voir l'effet plus haut) — et un `div` n'est pas
            // un element interactif, donc lui attacher un gestionnaire de touches
            // cree un piege pour les technologies d'assistance, que `jsx-a11y`
            // signale a juste titre.
            onKeyDown={surTouche}
          />
        </div>

        {resultats.length === 0 ? (
          <p className="z-palette__vide">{t.palette.aucun}</p>
        ) : (
          <ul className="z-palette__liste" id="z-palette-liste" role="listbox" ref={liste}>
            {resultats.map(({ groupe, onglet }, index) => {
              const nouveauGroupe = groupe !== groupePrecedent;
              groupePrecedent = groupe;
              return (
                <li key={onglet} className="z-palette__entree">
                  {nouveauGroupe && (
                    <span className="z-palette__famille" aria-hidden="true">
                      {t.groupes[groupe]}
                    </span>
                  )}
                  <button
                    type="button"
                    id={`z-palette-${onglet}`}
                    className="z-palette__option"
                    role="option"
                    aria-selected={index === actif}
                    // Le survol déplace le curseur : deux repères concurrents
                    // (souris et clavier) laisseraient l'utilisateur ouvrir autre
                    // chose que ce qu'il vise.
                    onMouseMove={() => setActif(index)}
                    onClick={() => onChoisir(onglet)}
                    tabIndex={-1}
                  >
                    <span className="z-palette__icone" aria-hidden="true">
                      {ICONES[onglet]}
                    </span>
                    {t.onglets[onglet]}
                  </button>
                </li>
              );
            })}
          </ul>
        )}

        <p className="z-palette__indice">{t.palette.indice}</p>
      </div>
    </div>
  );
}
