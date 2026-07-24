import { useState, type ReactElement } from 'react';
import { demarrerConnexion, oidcConfigure } from '../auth/oidc';
import { ouvrirSession } from '../auth/session';
import { useT } from '../i18n/langue';
import { Bouton } from '../ui/composants';

/**
 * Ecran de session. En production, il sera remplace par une redirection Keycloak
 * (OIDC / PKCE) ; le point d'integration est isole dans `auth/session`. En
 * developpement, il accepte un jeton d'acces valide (portant le claim tenant_id).
 */
export function ConnexionVue(): ReactElement {
  const t = useT();
  const [jeton, setJeton] = useState('');

  return (
    <main className="z-connexion">
      <div className="z-connexion__carte">
        <div className="z-marque">
          <span className="z-marque__pastille" aria-hidden="true" />
          <span className="z-marque__nom">{t.marque}</span>
        </div>
        <h1 className="z-connexion__titre">{t.session.titre}</h1>
        <p className="z-connexion__texte">{t.session.explication}</p>

        {oidcConfigure() && (
          <>
            <Bouton variante="primaire" onClick={() => void demarrerConnexion()}>
              {t.session.connexionKeycloak}
            </Bouton>
            <p className="z-connexion__ou">{t.session.ou}</p>
          </>
        )}

        <label className="z-champ">
          <span className="z-champ__libelle">{t.session.jeton}</span>
          <textarea
            className="z-input z-input--zone"
            rows={4}
            placeholder={t.session.placeholder}
            value={jeton}
            onChange={(e) => setJeton(e.target.value)}
          />
        </label>
        <Bouton
          variante="primaire"
          onClick={() => jeton.trim() !== '' && ouvrirSession(jeton.trim())}
        >
          {t.actions.seConnecter}
        </Bouton>
      </div>
    </main>
  );
}
