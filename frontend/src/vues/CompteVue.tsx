import type { ReactElement } from 'react';
import { jetonCsrf } from '../api/client';
import { deconnexion } from '../auth/oidc';
import type { Session } from '../auth/session';
import { useT } from '../i18n/langue';
import { ROUTES_PUBLIQUES } from '../routage/routes';
import { Bouton, ChampSelect } from '../ui/composants';
import { ETENDUES, useEtendue, type Etendue } from '../terrain/interface';

/**
 * Écran « Mon compte » (SPRINT-19, lot 4).
 *
 * <p><strong>Ce qu'il montre, et ce qu'il refuse de montrer.</strong> Tout vient
 * de la session rendue par le serveur — utilisateur, exploitation, rôles. Rien
 * n'est lu dans le navigateur, parce qu'il n'y a rien à y lire : aucun jeton n'y
 * est stocké depuis l'ADR-006.
 *
 * <p><strong>Il ne change pas le mot de passe</strong>, et ce n'est pas un
 * oubli : le mot de passe appartient au service d'authentification, et le BFF
 * n'expose que la connexion et l'inscription. Poser ici un formulaire qui
 * n'aboutirait nulle part serait le même défaut que le formulaire de contact du
 * lot 3 — l'utilisateur croirait avoir changé son mot de passe.
 *
 * <p><strong>Il dit où vivent les préférences.</strong> Langue et thème sont
 * enregistrés dans ce navigateur seulement. Le passer sous silence produit la
 * surprise classique : on change d'appareil et l'application « a oublié » un
 * réglage qu'elle n'a jamais promis de retenir.
 */
export function CompteVue({
  session,
  onNaviguer,
}: {
  session: Session;
  onNaviguer: (chemin: string) => void;
}): ReactElement {
  const t = useT();
  const c = t.compte;
  const [etendue, definirEtendue] = useEtendue();

  const roles =
    session.roles.length === 0
      ? t.profil.sansRole
      : session.roles.map((role) => t.roles[role as keyof typeof t.roles] ?? role).join(', ');

  return (
    <section className="z-section">
      <header className="z-section__entete">
        <div>
          <h1 className="z-section__titre">{c.titre}</h1>
          <p className="z-section__soustitre">{c.sousTitre}</p>
        </div>
      </header>

      <div className="z-legal__section">
        <h2 className="z-legal__soustitre">{c.identiteTitre}</h2>
        <dl className="z-legal__fiche">
          <dt>{c.utilisateur}</dt>
          {/* Identifiant technique : isolé en LTR pour que l'arabe ne réordonne
              pas un nom de compte, comme il réordonnait le numéro de version. */}
          <dd className="z-legal__valeur">
            <bdi dir="ltr">{session.utilisateur}</bdi>
          </dd>
          <dt>{t.profil.exploitation}</dt>
          <dd className="z-legal__valeur">
            <bdi dir="ltr">{session.exploitation}</bdi>
          </dd>
          <dt>{t.profil.roles}</dt>
          <dd>{roles}</dd>
        </dl>
      </div>

      <div className="z-legal__section">
        <h2 className="z-legal__soustitre">{c.securiteTitre}</h2>
        <p>{c.securiteTexte}</p>
        {/* Le renvoi vers la page publique plutôt qu'une répétition : elle porte
            déjà l'explication complète, et son bandeau des manques dit ce qui
            reste à activer côté authentification. */}
        <button
          type="button"
          className="z-lien"
          onClick={() => onNaviguer(ROUTES_PUBLIQUES.recuperation)}
        >
          {t.recuperation.titre}
        </button>
      </div>

      <div className="z-legal__section">
        <h2 className="z-legal__soustitre">{c.preferencesTitre}</h2>
        <p>{c.preferencesTexte}</p>
      </div>

      <div className="z-legal__section">
        <h2 className="z-legal__soustitre">{t.interfaceProgressive.titre}</h2>
        {/* Masquer n'est pas interdire : les rôles décident de ce qui est
            PERMIS, ce réglage de ce qui est MONTRÉ. Un écran masqué reste
            atteignable par son lien, et le réglage revient en un clic. */}
        <p>{t.interfaceProgressive.aide}</p>
        <ChampSelect
          libelle={t.interfaceProgressive.titre}
          valeur={etendue}
          options={ETENDUES.map((valeur) => ({
            valeur,
            libelle: t.interfaceProgressive[valeur],
          }))}
          onChange={(valeur) => definirEtendue(valeur as Etendue)}
        />
      </div>

      <div className="z-legal__section">
        <h2 className="z-legal__soustitre">{c.donneesTitre}</h2>
        <p>{c.donneesTexte}</p>
      </div>

      <div className="z-legal__section">
        <h2 className="z-legal__soustitre">{c.sessionTitre}</h2>
        <p>{c.sessionTexte}</p>
        <div className="z-actions-inline">
          <Bouton variante="danger" onClick={() => void deconnexion(jetonCsrf())}>
            {t.actions.seDeconnecter}
          </Bouton>
        </div>
      </div>
    </section>
  );
}
