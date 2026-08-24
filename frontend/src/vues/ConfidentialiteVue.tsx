import type { ReactElement } from 'react';
import { useT } from '../i18n/langue';
import { PageLegale } from './PageLegale';

/**
 * Politique de confidentialité — RGPD (SPRINT-19).
 *
 * <p><strong>Ce que cette page a de particulier.</strong> Elle n'est pas une
 * traduction en prose d'un modèle : chaque ligne des deux tableaux a été relevée
 * dans le code. Les catégories de données sont celles des entités JPA ; les
 * destinataires sont les seuls appels sortants du produit, et il n'y en a que
 * deux.
 *
 * <p>Deux points méritent d'être lus avant le reste, parce qu'ils sont propres à
 * ce produit et absents des modèles :
 *
 * <ul>
 *   <li><strong>la position des ruchers</strong> est une donnée sensible
 *       commercialement — elle vaut un vol de cheptel. Le code la filtre déjà
 *       selon le rôle (`PolitiquePositions`) ; la page le dit, parce qu'un
 *       utilisateur a le droit de savoir ce qui protège ses ruchers ;
 *   <li><strong>le fond de carte est appelé par le navigateur</strong>, pas par
 *       le serveur. Son hébergeur voit donc l'adresse IP et la zone consultée —
 *       c'est-à-dire, approximativement, où sont les ruchers. La météo, elle,
 *       part du serveur : l'hébergeur du service météo voit des coordonnées,
 *       jamais l'utilisateur qui les consulte.
 * </ul>
 *
 * <p>Les durées de conservation ne sont pas écrites : aucune purge n'existe dans
 * les migrations Flyway, et en annoncer une serait promettre un effacement que
 * rien n'exécute.
 */
export function ConfidentialiteVue(): ReactElement {
  const t = useT();
  const c = t.confidentialite;

  return (
    <PageLegale
      titre={c.titre}
      chapo={c.chapo}
      maj={c.maj}
      manques={c.manques}
      sections={c.sections}
    >
      <section className="z-legal__section">
        <h2 className="z-legal__soustitre">{c.donneesTitre}</h2>
        <p>{c.donneesTexte}</p>
        <div className="z-table-enveloppe">
          <table className="z-table">
            <thead>
              <tr>
                <th>{c.colonnes.categorie}</th>
                <th>{c.colonnes.contenu}</th>
                <th>{c.colonnes.origine}</th>
              </tr>
            </thead>
            <tbody>
              {c.donnees.map((ligne) => (
                <tr key={ligne.categorie}>
                  <td>{ligne.categorie}</td>
                  <td>{ligne.contenu}</td>
                  <td>{ligne.origine}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <section className="z-legal__section">
        <h2 className="z-legal__soustitre">{c.tiersTitre}</h2>
        <p>{c.tiersTexte}</p>
        <div className="z-table-enveloppe">
          <table className="z-table">
            <thead>
              <tr>
                <th>{c.colonnesTiers.nom}</th>
                <th>{c.colonnesTiers.pourquoi}</th>
                <th>{c.colonnesTiers.quoi}</th>
              </tr>
            </thead>
            <tbody>
              {c.tiers.map((ligne) => (
                <tr key={ligne.nom}>
                  <td>{ligne.nom}</td>
                  <td>{ligne.pourquoi}</td>
                  <td>{ligne.quoi}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </PageLegale>
  );
}
