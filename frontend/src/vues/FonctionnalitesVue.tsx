import type { ReactElement } from 'react';
import { useT } from '../i18n/langue';

/**
 * Page « Fonctionnalités » — le détail par domaine métier (SPRINT-19).
 *
 * <p><strong>Ce qu'elle ajoute à l'accueil.</strong> La vitrine donne six cartes
 * d'une phrase, ce qu'il faut pour décider si le produit vous concerne. Cette
 * page-ci répond à la question suivante — « concrètement, qu'est-ce que je peux
 * y faire ? » — et chaque puce correspond à une capacité qui existe dans
 * l'application, pas à une intention.
 *
 * <p>Elle se termine sur ce que Zümm <strong>ne fait pas</strong>. C'est
 * volontaire : une liste de fonctions sans limite annoncée oblige le lecteur à
 * essayer pour découvrir le manque, et il le découvre au pire moment. Le dire
 * tout de suite coûte un paragraphe et évite une déception.
 *
 * <p>Comme le reste des pages publiques, elle <strong>n'appelle aucune API</strong>.
 */
export function FonctionnalitesVue(): ReactElement {
  const t = useT();
  const f = t.fonctionnalites;

  return (
    <article className="z-legal z-legal--large">
      <header className="z-legal__entete">
        <h1 className="z-legal__titre">{f.titre}</h1>
        <p className="z-legal__chapo">{f.chapo}</p>
      </header>

      <div className="z-domaines">
        {f.domaines.map((domaine) => (
          <section key={domaine.titre} className="z-domaine">
            <h2 className="z-legal__soustitre">{domaine.titre}</h2>
            <p className="z-domaine__texte">{domaine.texte}</p>
            <ul className="z-domaine__puces">
              {domaine.puces.map((puce) => (
                <li key={puce}>{puce}</li>
              ))}
            </ul>
          </section>
        ))}
      </div>

      <section className="z-legal__section">
        <h2 className="z-legal__soustitre">{f.finalTitre}</h2>
        <p>{f.finalTexte}</p>
      </section>
    </article>
  );
}
