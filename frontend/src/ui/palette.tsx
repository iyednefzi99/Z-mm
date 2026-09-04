import { useEffect, useMemo, useRef, useState, type ReactElement } from 'react';
import { rechercher } from '../api/client';
import type { ResultatRecherche } from '../api/types';
import { useT } from '../i18n/langue';
import {
  GROUPES,
  GROUPES_CLES,
  ICONES,
  ongletAutorise,
  ongletDepuisChemin,
  type Groupe,
  type Onglet,
} from '../routage/routes';

/**
 * Palette de commandes (Ctrl/⌘ + K) — accès direct aux dix-neuf écrans.
 *
 * <p><strong>Pourquoi.</strong> La navigation groupée règle la lisibilité, pas la
 * distance : atteindre « Lots & origines » depuis « Visites » demande toujours de
 * viser une cible dans une liste de dix-neuf. La palette est l'accélérateur, la
 * navigation reste la voie visible — un raccourci que personne ne découvre ne
 * navigue personne. C'est pourquoi la barre supérieure porte un bouton qui
 * l'ouvre <em>et</em> affiche le raccourci.
 *
 * <p><strong>Filtrage par sous-séquence, pas par sous-chaîne.</strong> « lts »
 * doit trouver « Lots & origines » et « tbx » « Tableaux de bord ». Un
 * `includes()` ne rendrait rien sur ces frappes-là, et une palette qui répond
 * « aucun résultat » à une abréviation plausible passe pour cassée.
 *
 * <p>La palette ne s'ouvre jamais sur le vide : sans saisie, elle liste les vingt
 * écrans, groupés comme dans la navigation.
 *
 * <p><strong>Depuis le SPRINT-21, elle cherche aussi dans les DONNÉES.</strong>
 * C'était le dernier ❌ du §1 de `docs/ECART-CONCURRENTS.md` : la palette
 * cherchait parmi les écrans, jamais parmi les objets, et rien ne répondait à
 * « où est la ruche 42 ». Deux sections distinctes, jamais mélangées — les
 * écrans se filtrent localement et répondent à la frappe, les objets
 * demandent un aller-retour au serveur.
 *
 * <p><strong>Ce que la navigation par objet peut et ne peut pas.</strong> Les
 * routes sont plates (ADR du SPRINT-11) : choisir un résultat ouvre l'écran
 * qui le porte, pas la fiche. Ouvrir la fiche demanderait des routes
 * paramétrées, c'est-à-dire le routeur que le projet a écarté ; le dire ici
 * vaut mieux que de laisser croire à un lien mort.
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

/** Identifiant DOM de l'entrée active, pour `aria-activedescendant`. */
function identifiantActif(
  entree:
    | { genre: 'ecran'; onglet: Onglet }
    | { genre: 'objet'; objet: ResultatRecherche }
    | undefined,
): string | undefined {
  if (!entree) {
    return undefined;
  }
  return entree.genre === 'ecran'
    ? `z-palette-${entree.onglet}`
    : `z-palette-objet-${entree.objet.type}-${entree.objet.id}`;
}

export function PaletteCommandes({
  onChoisir,
  onFermer,
  roles,
}: {
  /** Le second argument, depuis le SPRINT-21, est l'objet à ouvrir. */
  onChoisir: (onglet: Onglet, cibleId?: number) => void;
  onFermer: () => void;
  /**
   * Roles de la session. La palette doit voir EXACTEMENT ce que voit le rail :
   * un accelerateur qui atteint un ecran que la navigation masque n'accelere
   * rien, il contourne. Obligatoire, et sans valeur par defaut : un tableau
   * vide implicite retirerait deux ecrans a un responsable sans que rien ne le
   * signale.
   */
  roles: readonly string[];
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
  const [objets, setObjets] = useState<ResultatRecherche[]>([]);
  const [cherche, setCherche] = useState(false);

  /** Écrans retenus, à plat et dans l'ordre d'affichage : c'est l'index du curseur. */
  const resultats = useMemo(() => {
    const retenus: { groupe: Groupe; onglet: Onglet }[] = [];
    for (const groupe of GROUPES_CLES) {
      for (const onglet of GROUPES[groupe]) {
        // Le nom de la famille est cherché lui aussi : taper « terrain » sort les
        // quatre écrans de terrain, ce qu'aucun libellé d'onglet ne permet.
        if (!ongletAutorise(onglet, roles)) {
          continue;
        }
        if (
          correspond(requete, t.onglets[onglet]) ||
          correspond(requete, t.groupes[groupe])
        ) {
          retenus.push({ groupe, onglet });
        }
      }
    }
    return retenus;
  }, [requete, roles, t]);

  /**
   * Recherche serveur, différée de 250 ms après la dernière frappe.
   *
   * <p>Sans ce délai, « tilleul » enverrait sept requêtes dont six inutiles. Le
   * seuil de deux caractères recopie celui du serveur : sous cette longueur, une
   * recherche n'est plus une recherche, c'est un export.
   */
  useEffect(() => {
    const motif = requete.trim();
    if (motif.length < 2) {
      setObjets([]);
      setCherche(false);
      return;
    }
    setCherche(true);
    let abandonne = false;
    const minuterie = setTimeout(() => {
      rechercher(motif)
        .then((trouves) => {
          if (!abandonne) {
            setObjets(trouves);
          }
        })
        // Un échec de recherche ne doit pas casser la navigation par écran, qui
        // elle fonctionne hors ligne : la section « données » reste simplement vide.
        .catch(() => {
          if (!abandonne) {
            setObjets([]);
          }
        })
        .finally(() => {
          if (!abandonne) {
            setCherche(false);
          }
        });
    }, 250);
    return () => {
      abandonne = true;
      clearTimeout(minuterie);
    };
  }, [requete]);

  // Le curseur revient en tête à chaque frappe : le laisser en place le ferait
  // pointer un écran qui n'est plus celui que l'utilisateur regarde.
  useEffect(() => setActif(0), [requete]);

  useEffect(() => {
    const image = requestAnimationFrame(() => setOuvert(true));
    champ.current?.focus();
    return () => cancelAnimationFrame(image);
  }, []);

  /**
   * Entrées navigables, à plat : les écrans puis les objets.
   *
   * <p>Un seul index pour les deux sections — c'est ce qui permet à la flèche bas
   * de passer des écrans aux données sans que l'utilisateur ait à changer de
   * geste.
   */
  const entrees = useMemo(
    () => [
      ...resultats.map((r) => ({ genre: 'ecran' as const, ...r })),
      ...objets.map((o) => ({ genre: 'objet' as const, objet: o })),
    ],
    [resultats, objets],
  );

  /**
   * Ouvre une entrée : un écran directement, un objet par l'écran qui le porte —
   * en lui passant l'identifiant, si bien que la ligne s'ouvre surlignée.
   */
  const ouvrir = (entree: (typeof entrees)[number]) => {
    if (entree.genre === 'ecran') {
      onChoisir(entree.onglet);
      return;
    }
    const onglet = ongletDepuisChemin(entree.objet.route);
    if (onglet) {
      onChoisir(onglet, entree.objet.id);
    }
  };

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
      if (entrees.length === 0) {
        return;
      }
      const pas = e.key === 'ArrowDown' ? 1 : -1;
      // Boucle : arrivé en bas, on repart en haut. Buter en silence sur la
      // dernière ligne laisse croire que la touche ne répond plus.
      setActif((i) => (i + pas + entrees.length) % entrees.length);
      return;
    }
    if (e.key === 'Enter' && entrees[actif]) {
      e.preventDefault();
      ouvrir(entrees[actif]);
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
            placeholder={t.palette.inviteGlobale}
            aria-label={t.palette.inviteGlobale}
            // Le champ pilote une liste : le lecteur d'écran doit annoncer
            // l'option atteinte au clavier, pas seulement le texte tapé.
            role="combobox"
            aria-expanded="true"
            aria-controls="z-palette-liste"
            aria-activedescendant={identifiantActif(entrees[actif])}
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

        {/* Deux messages distincts, jamais fondus en un seul : « aucun ecran » est
            un resultat definitif, « recherche en cours » un etat transitoire de la
            seconde section. Les confondre ferait clignoter le premier a chaque
            frappe alors que sa reponse, elle, est deja connue. */}
        {entrees.length === 0 ? (
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
            {objets.map((objet, index) => {
              const rang = resultats.length + index;
              const premier = index === 0;
              return (
                <li key={`${objet.type}-${objet.id}`} className="z-palette__entree">
                  {premier && (
                    <span className="z-palette__famille" aria-hidden="true">
                      {t.palette.objets}
                    </span>
                  )}
                  <button
                    type="button"
                    id={`z-palette-objet-${objet.type}-${objet.id}`}
                    className="z-palette__option"
                    role="option"
                    aria-selected={rang === actif}
                    onMouseMove={() => setActif(rang)}
                    onClick={() => ouvrir({ genre: 'objet', objet })}
                    tabIndex={-1}
                  >
                    <span className="z-palette__icone" aria-hidden="true">
                      🔎
                    </span>
                    {objet.libelle}
                    <span className="z-palette__precision">
                      {t.palette.typesObjet[objet.type]}
                      {objet.precision ? ` · ${objet.precision}` : ''}
                    </span>
                  </button>
                </li>
              );
            })}
          </ul>
        )}

        {cherche && <p className="z-palette__vide">{t.palette.chercheEnCours}</p>}

        <p className="z-palette__indice">{t.palette.indice}</p>
      </div>
    </div>
  );
}
