import type { ReactElement } from 'react';
import { useT } from '../i18n/langue';

/**
 * Ressources et aide (SPRINT-19).
 *
 * <p>Le centre d'aide vit ici, en <strong>public</strong>, et non dans la
 * console : c'est l'arbitrage rendu le 18/08/2026. En console il faudrait le
 * remettre à jour à chaque livraison sous peine de mentir à l'utilisateur ; en
 * public il sert aussi celui qui n'a pas encore de compte — et c'est précisément
 * lui qui a le plus de questions.
 *
 * <p>Chaque réponse décrit <strong>ce que fait l'application</strong>, vérifié
 * dans le code : l'entrée par code d'exploitation, la délégation des mots de
 * passe au service d'authentification, la file d'attente hors ligne, les deux
 * écrans réservés au responsable, le filtrage des positions, l'export des
 * visites et des ruches. Aucune promesse, aucun conseil apicole inventé.
 *
 * <p>Le rendu utilise `details`/`summary` : l'accordéon est natif, donc
 * accessible au clavier et repliable sans une ligne de JavaScript. Le premier
 * est ouvert — une page d'aide qui s'ouvre entièrement fermée demande un clic
 * avant de prouver qu'elle contient quelque chose.
 */
export function RessourcesVue(): ReactElement {
  const t = useT();
  const r = t.ressources;

  return (
    <article className="z-legal">
      <header className="z-legal__entete">
        <h1 className="z-legal__titre">{r.titre}</h1>
        <p className="z-legal__chapo">{r.chapo}</p>
      </header>

      <div className="z-faq">
        {r.questions.map((entree, rang) => (
          <details key={entree.q} className="z-faq__entree" open={rang === 0}>
            <summary className="z-faq__question">{entree.q}</summary>
            <p className="z-faq__reponse">{entree.r}</p>
          </details>
        ))}
      </div>
    </article>
  );
}
