import type { ReactElement } from 'react';
import { gabarit } from '../i18n/console';
import { useT } from '../i18n/langue';
import { Bouton } from '../ui/composants';

/**
 * Écran servi quand la session est valide mais le rôle insuffisant (SPRINT-19).
 *
 * <p><strong>Ce qu'il corrige.</strong> Un 403 arrivait jusqu'ici sous la forme
 * d'un bandeau rouge portant le message du serveur, avec un bouton
 * « Réessayer » — qui rejouait la même requête pour obtenir le même refus. Or
 * un refus de rôle n'est pas une panne : réessayer n'a aucune chance
 * d'aboutir, et le proposer laisse croire le contraire.
 *
 * <p><strong>Il ne prend pas de route.</strong> Une URL `/403` qu'on peut taper
 * à la main est une page qui ment : elle affirme un refus que rien n'a
 * prononcé. Cet écran se rend à la place du contenu, sur un refus réel — celui
 * de la table des rôles avant l'appel, ou celui du serveur après.
 *
 * <p>La distinction avec « pas de session » est tenue en amont, dans `App` :
 * sans session, on va vers l'écran d'entrée ; avec une session sans le rôle, on
 * arrive ici, et surtout on n'y redirige pas — une redirection vers une
 * connexion déjà faite boucle.
 *
 * @param rolesRequis rôles qui ouvrent l'écran, quand ils sont connus. Les
 *   nommer évite l'aller-retour « pourquoi moi pas ? » avec le responsable.
 * @param onRetour sortie de secours, quand l'écran remplace toute la vue.
 *   Omise lorsqu'il s'affiche DANS une section : le rail est alors encore là,
 *   et un bouton « Revenir à l'accueil » sous un titre d'écran encore visible
 *   proposerait de quitter une page qu'on n'a pas quittée.
 */
export function InterditVue({
  rolesRequis,
  onRetour,
}: {
  rolesRequis?: readonly string[];
  onRetour?: () => void;
}): ReactElement {
  const t = useT();

  // Les rôles sont traduits quand on les connaît, et rendus tels quels sinon :
  // un rôle inconnu du dictionnaire doit s'afficher, pas disparaître.
  const listeRoles = (rolesRequis ?? [])
    .map((role) => t.roles[role as keyof typeof t.roles] ?? role)
    .join(', ');

  return (
    <section className="z-section">
      <header className="z-section__entete">
        <h1 className="z-section__titre">{t.interdit.titre}</h1>
      </header>
      <p className="z-info">{t.interdit.explication}</p>
      {listeRoles !== '' && (
        <p className="z-info">{gabarit(t.interdit.rolesRequis, { roles: listeRoles })}</p>
      )}
      {onRetour && (
        <Bouton variante="primaire" onClick={onRetour}>
          {t.interdit.retour}
        </Bouton>
      )}
    </section>
  );
}
