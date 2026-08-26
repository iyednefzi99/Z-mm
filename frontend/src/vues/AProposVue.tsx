import { useEffect, useState, type ReactElement } from 'react';
import { chargerInfo, type InfoApplication } from '../api/client';
import { useLangue, useT } from '../i18n/langue';
import { BordDechire } from '../ui/papier';

/**
 * Page « À propos » (SPRINT-19).
 *
 * <p>C'est la <strong>seule page publique qui appelle l'API</strong>, et elle le
 * fait sur le seul endpoint métier ouvert sans jeton : `GET /api/info`, déclaré
 * `permitAll` dans `SecurityConfig` avec le commentaire « page d'accueil
 * publique ». Il rend le nom, la version, un message d'accueil traduit par le
 * serveur et la liste des langues actives.
 *
 * <p><strong>L'appel n'est pas obligatoire au rendu.</strong> Si le serveur ne
 * répond pas, la page s'affiche sans la carte de version plutôt que de montrer
 * une erreur : ce qu'elle raconte du produit reste vrai serveur éteint. Une page
 * « à propos » qui ne s'affiche pas quand l'API est tombée est une page inutile
 * au seul moment où l'on cherche à savoir ce qui tourne.
 *
 * <p>La version affichée vient du serveur et n'est jamais recopiée ici : c'est
 * lui qui sait ce qui est déployé.
 *
 * <p><strong>Trois bandes, deux déchirures</strong>, comme l'accueil et les
 * fonctionnalités. La dernière est en teinte de fond et se rend
 * <strong>toujours</strong>, même quand l'API n'a rien donné : elle n'est pas là
 * pour la fiche de version, elle est là parce que la déchirure du pied est
 * peinte de cette couleur (voir {@link CoquillePublique}). La conditionner à
 * `info` ferait dépendre la jointure du bas de page de la disponibilité du
 * serveur — un liseré qui apparaît quand le back tombe est exactement le genre
 * de défaut qu'on ne reproduit jamais en local.
 */
export function AProposVue(): ReactElement {
  const t = useT();
  const { langue } = useLangue();
  const [info, setInfo] = useState<InfoApplication | null>(null);

  useEffect(() => {
    let vivant = true;
    // L'échec est avalé, et c'est intentionnel : l'absence de la carte dit déjà
    // tout ce qu'il y a à dire, et un bandeau rouge sur une page de présentation
    // alarmerait un visiteur pour une information d'appoint.
    void chargerInfo(langue)
      .then((recu) => {
        if (vivant) {
          setInfo(recu);
        }
      })
      .catch(() => {
        if (vivant) {
          setInfo(null);
        }
      });
    return () => {
      vivant = false;
    };
  }, [langue]);

  return (
    <article>
      <div className="z-bande z-bande--fond">
        <div className="z-bande__colonne">
          <header className="z-legal__entete">
            <h1 className="z-legal__titre">{t.apropos.titre}</h1>
            <p className="z-legal__chapo">{t.apropos.chapo}</p>
          </header>
        </div>
      </div>

      <div className="z-bande z-bande--surface">
        <BordDechire teinte="fond" />
        <div className="z-bande__colonne">
          {t.apropos.sections.map((section) => (
            <section key={section.titre} className="z-legal__section">
              <h2 className="z-legal__soustitre">{section.titre}</h2>
              <p>{section.texte}</p>
            </section>
          ))}
        </div>
      </div>

      <div className="z-bande z-bande--fond z-bande--cloture">
        <BordDechire teinte="surface" />
        <div className="z-bande__colonne">
          {info && (
            <section className="z-legal__section">
              <h2 className="z-legal__soustitre">{t.apropos.versionTitre}</h2>
              <dl className="z-legal__fiche">
                <dt>{t.apropos.champs.nom}</dt>
                <dd>{info.nom}</dd>
                <dt>{t.apropos.champs.version}</dt>
                {/* `bdi` et non `span` : en arabe, l'algorithme bidirectionnel
                    réordonne « 0.1.0-SNAPSHOT » en « SNAPSHOT-0.1.0 » — le tiret est
                    un caractère neutre, il se rattache au sens du paragraphe. Un
                    numéro de version n'est pas de la prose et ne doit jamais être
                    réordonné. L'isolation le protège sans figer l'alignement, qui
                    reste celui de la fiche.
                    La fonte à chasse fixe, elle, évite de lire « 0.1.0 » comme une
                    phrase. */}
                <dd className="z-legal__valeur">
                  <bdi dir="ltr">{info.version}</bdi>
                </dd>
                <dt>{t.apropos.champs.langues}</dt>
                <dd className="z-legal__valeur">
                  <bdi dir="ltr">{info.langues.join(' · ')}</bdi>
                </dd>
                <dt>{t.apropos.champs.message}</dt>
                <dd>{info.accueil}</dd>
              </dl>
            </section>
          )}
        </div>
      </div>
    </article>
  );
}
