import { type ReactElement } from 'react';
import { demarrerConnexion } from '../auth/oidc';
import { useT } from '../i18n/langue';
import { Bouton } from '../ui/composants';

/**
 * Écran de session.
 *
 * <p>Depuis le BFF (ADR-006), il ne collecte plus rien : ni mot de passe, ni
 * jeton collé à la main. Un seul bouton, qui envoie l'utilisateur s'authentifier
 * chez Keycloak — la PWA ne voit jamais ses identifiants et ne reçoit jamais de
 * jeton en retour.
 *
 * <p>Le champ « coller un jeton » qui existait ici a disparu volontairement :
 * c'était une commodité de développement, et le seul endroit de l'interface
 * capable d'introduire un jeton dans le navigateur.
 */
export function ConnexionVue(): ReactElement {
  const t = useT();

  return (
    <main className="z-connexion">
      <div className="z-connexion__carte">
        <div className="z-marque">
          <span className="z-marque__pastille" aria-hidden="true" />
          <span className="z-marque__nom">{t.marque}</span>
        </div>
        <h1 className="z-connexion__titre">{t.session.titre}</h1>
        <p className="z-connexion__texte">{t.session.explication}</p>

        <Bouton
          variante="primaire"
          onClick={() => demarrerConnexion(window.location.pathname)}
        >
          {t.session.connexionKeycloak}
        </Bouton>
      </div>
    </main>
  );
}
