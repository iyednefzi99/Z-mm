import type { ReactElement } from 'react';
import { useT } from '../i18n/langue';
import { Icone, type NomIcone } from '../ui/icones';
import { BordDechire } from '../ui/papier';

/**
 * Pictogrammes des six domaines, dans l'ordre du tableau de traductions.
 *
 * <p>C'est le même ordre que celui des cartes de l'accueil, et il est le même
 * dans les trois locales — pilotage, cheptel, terrain, production, capteurs,
 * hors ligne. L'association se fait donc par rang, faute de clé stable dans le
 * JSON : les domaines y sont un tableau d'objets `{ titre, texte, puces }`, sans
 * identifiant.
 *
 * <p>Ce rang est une hypothèse, pas une garantie — d'où le `.at()` plus bas
 * plutôt qu'un accès direct. Si quelqu'un ajoute un septième domaine à une
 * traduction, la carte sort sans pictogramme ; elle ne sort pas cassée, et elle
 * n'hérite surtout pas de l'icône du voisin.
 */
const ICONES_DOMAINES: readonly NomIcone[] = [
  'graphique',
  'ruche',
  'carte',
  'miel',
  'capteur',
  'horsLigne',
];

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
 *
 * <p><strong>Trois bandes, deux déchirures</strong>, comme l'accueil : ce qu'on
 * annonce, ce qu'on sait faire, ce qu'on ne sait pas faire. Le papier arraché
 * s'arrête aux pages d'acquisition — les CGU et la confidentialité restent sur
 * une colonne nue, parce qu'une texture sous un texte de loi le rend plus dur à
 * lire au moment précis où il faut le lire.
 *
 * <p>La dernière bande est en teinte de FOND : c'est ce que suppose la
 * déchirure du pied, qui est peinte de cette couleur (voir
 * {@link CoquillePublique}). Une page publique qui se terminerait sur une bande
 * en surface laisserait un liseré à la jointure.
 */
export function FonctionnalitesVue(): ReactElement {
  const t = useT();
  const f = t.fonctionnalites;

  return (
    <article>
      <div className="z-bande z-bande--fond">
        <div className="z-bande__colonne z-bande__colonne--large">
          <header className="z-legal__entete">
            <h1 className="z-legal__titre">{f.titre}</h1>
            <p className="z-legal__chapo">{f.chapo}</p>
          </header>
        </div>
      </div>

      <div className="z-bande z-bande--surface">
        <BordDechire teinte="fond" />
        <div className="z-bande__colonne z-bande__colonne--large">
          <div className="z-domaines">
            {f.domaines.map((domaine, rang) => {
              const icone = ICONES_DOMAINES.at(rang);
              return (
                <section key={domaine.titre} className="z-domaine">
                  {/* Décoratif : le titre traduit porte seul le sens, comme sur
                      les cartes de l'accueil. */}
                  {icone && (
                    <span className="z-alveole" aria-hidden="true">
                      <Icone nom={icone} />
                    </span>
                  )}
                  <h2 className="z-legal__soustitre">{domaine.titre}</h2>
                  <p className="z-domaine__texte">{domaine.texte}</p>
                  <ul className="z-domaine__puces">
                    {domaine.puces.map((puce) => (
                      <li key={puce}>{puce}</li>
                    ))}
                  </ul>
                </section>
              );
            })}
          </div>
        </div>
      </div>

      <div className="z-bande z-bande--fond">
        <BordDechire teinte="surface" />
        <div className="z-bande__colonne z-bande__colonne--large">
          <section className="z-legal__section">
            <h2 className="z-legal__soustitre">{f.finalTitre}</h2>
            <p>{f.finalTexte}</p>
          </section>
        </div>
      </div>
    </article>
  );
}
