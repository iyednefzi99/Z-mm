import { useState, type ReactElement } from 'react';
import { ouvrirSession } from '../auth/session';
import { Bouton } from '../ui/composants';
import { L } from '../ui/libelles';

/**
 * Ecran de session. En production, il sera remplace par une redirection Keycloak
 * (OIDC / PKCE) ; le point d'integration est isole dans `auth/session`. En
 * developpement, il accepte un jeton d'acces valide (portant le claim tenant_id).
 */
export function ConnexionVue(): ReactElement {
  const [jeton, setJeton] = useState('');

  return (
    <main className="z-connexion">
      <div className="z-connexion__carte">
        <div className="z-marque">
          <span className="z-marque__pastille" aria-hidden="true" />
          <span className="z-marque__nom">{L.marque}</span>
        </div>
        <h1 className="z-connexion__titre">{L.session.titre}</h1>
        <p className="z-connexion__texte">{L.session.explication}</p>
        <label className="z-champ">
          <span className="z-champ__libelle">{L.session.jeton}</span>
          <textarea
            className="z-input z-input--zone"
            rows={4}
            placeholder={L.session.placeholder}
            value={jeton}
            onChange={(e) => setJeton(e.target.value)}
          />
        </label>
        <Bouton
          variante="primaire"
          onClick={() => jeton.trim() !== '' && ouvrirSession(jeton.trim())}
        >
          {L.actions.seConnecter}
        </Bouton>
      </div>
    </main>
  );
}
