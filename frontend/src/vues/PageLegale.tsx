import type { ReactElement, ReactNode } from 'react';
import { useT } from '../i18n/langue';

/** Une section de texte légal : un intertitre, un paragraphe. */
export interface SectionLegale {
  titre: string;
  texte: string;
}

/**
 * Ossature commune des pages légales — CGU et confidentialité (SPRINT-19).
 *
 * <p>Les deux pages ont la même forme : un titre, une phrase qui dit à qui le
 * texte s'adresse, une suite d'intertitres, et la date de dernière mise à jour.
 * Le contenu vit dans les traductions, pas dans le composant : c'est du texte,
 * il se relit, se traduit et se fait valider ailleurs que dans du JSX.
 *
 * <p><strong>Le bandeau des manques.</strong> Un texte légal ne s'invente pas :
 * ni raison sociale, ni adresse, ni nom d'hébergeur, ni durée de conservation.
 * Le dépôt ne contient aucune de ces informations, et les remplir de plausible
 * serait pire que de les laisser vides — on lirait un engagement que personne
 * n'a pris. Ce qui manque est donc listé <strong>en haut de la page</strong>, et
 * pas seulement dans un commentaire du code : ces pages ne peuvent pas partir en
 * ligne par mégarde en passant pour finies.
 *
 * <p>La largeur de ligne est bornée par la feuille de style. Un texte légal est
 * déjà pénible à lire ; en pleine largeur d'écran, il ne se lit plus du tout.
 */
export function PageLegale({
  titre,
  chapo,
  maj,
  manques,
  sections,
  children,
}: {
  titre: string;
  chapo: string;
  maj: string;
  /** Ce qui reste à faire remplir par l'exploitant du service. */
  manques: readonly string[];
  sections: readonly SectionLegale[];
  children?: ReactNode;
}): ReactElement {
  const t = useT();

  return (
    <article className="z-legal">
      <header className="z-legal__entete">
        <h1 className="z-legal__titre">{titre}</h1>
        <p className="z-legal__chapo">{chapo}</p>
        <p className="z-legal__maj">{maj}</p>
      </header>

      {manques.length > 0 && (
        // `role="note"` et non `alert` : ce n'est pas une erreur survenue, c'est
        // un avertissement permanent sur l'état du document.
        <aside className="z-legal__manques" role="note" aria-labelledby="legal-manques">
          <h2 className="z-legal__manques-titre" id="legal-manques">
            {t.legal.manquesTitre}
          </h2>
          <p>{t.legal.manquesTexte}</p>
          <ul>
            {manques.map((manque) => (
              <li key={manque}>{manque}</li>
            ))}
          </ul>
        </aside>
      )}

      {sections.map((section) => (
        <section key={section.titre} className="z-legal__section">
          <h2 className="z-legal__soustitre">{section.titre}</h2>
          <p>{section.texte}</p>
        </section>
      ))}

      {children}
    </article>
  );
}
