import { useState, type FormEvent, type ReactElement } from 'react';
import { demarrerConnexion } from '../auth/oidc';
import { connexion, inscription, ErreurIdentite, type EchecIdentite } from '../auth/identite';
import { useT } from '../i18n/langue';
import { Bouton, ChampTexte } from '../ui/composants';

/** Les deux entrées possibles dans l'application. */
type Onglet = 'connexion' | 'inscription';

/**
 * Écran d'entrée — connexion et création de compte.
 *
 * <p>Zümm présente ici ses propres formulaires : l'utilisateur ne quitte plus
 * l'application pour voir les pages de Keycloak, que le BFF appelle en
 * arrière-plan (ADR-009). Ce qui n'a PAS changé : aucun jeton n'entre dans le
 * navigateur, la session reste un cookie {@code HttpOnly} posé par le serveur.
 *
 * <p>Le bouton « Se connecter avec Keycloak » subsiste, et ce n'est pas un
 * doublon : la fédération d'identité (Google) ne peut pas s'exécuter en
 * arrière-plan — le mot de passe d'un fournisseur tiers ne se saisit que chez ce
 * fournisseur. C'est aussi le chemin de secours si l'échange direct est
 * indisponible.
 *
 * <p>Deux choix d'ergonomie méritent d'être justifiés :
 * <ul>
 *   <li>le formulaire n'est jamais vidé après un échec. Retaper une adresse
 *       correcte parce que le mot de passe était faux est une punition, pas une
 *       protection ;
 *   <li>le message d'erreur est le même pour un compte inconnu et un mot de
 *       passe faux. Distinguer les deux transformerait cet écran en annuaire des
 *       comptes existants.
 * </ul>
 */
export function ConnexionVue(): ReactElement {
  const t = useT();
  const [onglet, setOnglet] = useState<Onglet>('connexion');
  const [identifiant, setIdentifiant] = useState('');
  const [motDePasse, setMotDePasse] = useState('');
  const [nom, setNom] = useState('');
  const [courriel, setCourriel] = useState('');
  const [code, setCode] = useState('');
  const [motDePasseVisible, setMotDePasseVisible] = useState(false);
  const [enCours, setEnCours] = useState(false);
  const [erreur, setErreur] = useState<EchecIdentite | null>(null);

  /** Change d'onglet en effaçant l'erreur : elle parlait de l'autre formulaire. */
  function choisir(cible: Onglet): void {
    setOnglet(cible);
    setErreur(null);
  }

  async function soumettre(evenement: FormEvent): Promise<void> {
    evenement.preventDefault();
    if (enCours) {
      return;
    }
    setEnCours(true);
    setErreur(null);
    try {
      if (onglet === 'connexion') {
        await connexion(identifiant.trim(), motDePasse);
      } else {
        await inscription({
          nom: nom.trim(),
          courriel: courriel.trim(),
          motDePasse,
          code: code.trim().toUpperCase(),
        });
      }
      // Succès : la session est posée, `App` bascule sur la console. Le mot de
      // passe est oublié ici même — il ne doit survivre à aucun rendu.
      setMotDePasse('');
    } catch (leve) {
      setErreur(leve instanceof ErreurIdentite ? leve.code : 'indisponible');
      setEnCours(false);
    }
  }

  const inscrit = onglet === 'inscription';

  return (
    <main className="z-entree">
      {/* Volet de marque : la seule surface décorative de l'application, et le
          premier contact avec le produit. Masqué aux lecteurs d'écran, il ne
          porte aucune information que le formulaire ne donne déjà. */}
      <aside className="z-entree__marque" aria-hidden="true">
        <div className="z-entree__halo" />
        <div className="z-marque z-marque--grand">
          <span className="z-marque__pastille" />
          <span className="z-marque__nom">{t.marque}</span>
        </div>
        <p className="z-entree__accroche">{t.session.accroche}</p>
        <p className="z-entree__baseline">{t.session.baseline}</p>
      </aside>

      <div className="z-entree__panneau">
        <div className="z-entree__carte">
          {/* La page a besoin d'un titre de niveau 1, mais l'écrire en grand
              au-dessus de deux onglets qui disent déjà « Se connecter » serait
              redondant à l'œil. Il reste donc pour les lecteurs d'écran. */}
          <h1 className="z-visuellement-cache">{t.session.titre}</h1>
          {/* Onglets : `radiogroup` plutôt que des boutons, parce qu'il s'agit
              bien de choisir entre deux états exclusifs du même écran. */}
          <div className="z-onglets" role="tablist" aria-label={t.session.titre}>
            <button
              type="button"
              role="tab"
              id="onglet-connexion"
              aria-selected={!inscrit}
              aria-controls="volet-identite"
              className={`z-onglets__item${!inscrit ? ' is-actif' : ''}`}
              onClick={() => choisir('connexion')}
            >
              {t.session.ongletConnexion}
            </button>
            <button
              type="button"
              role="tab"
              id="onglet-inscription"
              aria-selected={inscrit}
              aria-controls="volet-identite"
              className={`z-onglets__item${inscrit ? ' is-actif' : ''}`}
              onClick={() => choisir('inscription')}
            >
              {t.session.ongletInscription}
            </button>
            {/* Pastille glissante : elle relie visuellement les deux états au
                lieu de les faire clignoter. */}
            <span className={`z-onglets__pastille${inscrit ? ' is-droite' : ''}`} aria-hidden="true" />
          </div>

          <form
            className="z-entree__formulaire"
            id="volet-identite"
            role="tabpanel"
            aria-labelledby={inscrit ? 'onglet-inscription' : 'onglet-connexion'}
            onSubmit={soumettre}
          >
            {inscrit ? (
              <>
                <ChampTexte
                  libelle={t.session.nom}
                  valeur={nom}
                  onChange={setNom}
                  requis
                  autoComplete="name"
                />
                <ChampTexte
                  libelle={t.session.courriel}
                  valeur={courriel}
                  onChange={setCourriel}
                  requis
                  type="email"
                  autoComplete="email"
                />
              </>
            ) : (
              <ChampTexte
                libelle={t.session.identifiant}
                valeur={identifiant}
                onChange={setIdentifiant}
                requis
                autoComplete="username"
                invalide={erreur === 'identifiants-invalides'}
              />
            )}

            <div className="z-champ-secret">
              <ChampTexte
                libelle={t.session.motDePasse}
                valeur={motDePasse}
                onChange={setMotDePasse}
                requis
                type={motDePasseVisible ? 'text' : 'password'}
                autoComplete={inscrit ? 'new-password' : 'current-password'}
                aide={inscrit ? t.session.motDePasseAide : undefined}
                invalide={erreur === 'identifiants-invalides' || erreur === 'mot-de-passe-refuse'}
              />
              {/* Voir ce qu'on tape réduit les échecs, surtout au gant et au
                  soleil — le terrain de cette application. */}
              <button
                type="button"
                className="z-champ-secret__bascule"
                aria-pressed={motDePasseVisible}
                aria-label={
                  motDePasseVisible ? t.session.masquerMotDePasse : t.session.afficherMotDePasse
                }
                onClick={() => setMotDePasseVisible((visible) => !visible)}
              >
                {motDePasseVisible ? '⦸' : '👁'}
              </button>
            </div>

            {inscrit ? (
              <ChampTexte
                libelle={t.session.code}
                valeur={code}
                onChange={setCode}
                requis
                autoComplete="off"
                aide={t.session.codeAide}
                invalide={erreur === 'code-inconnu'}
              />
            ) : null}

            {/* Région vivante : l'erreur doit être annoncée sans déplacer le
                focus, que l'utilisateur soit au clavier ou au lecteur d'écran. */}
            <p className="z-entree__erreur" role="alert" aria-live="polite">
              {erreur ? t.session.erreurs[erreur] : ''}
            </p>

            <Bouton variante="primaire" type="submit" disabled={enCours}>
              {enCours
                ? inscrit
                  ? t.session.enCoursInscription
                  : t.session.enCours
                : inscrit
                  ? t.session.validerInscription
                  : t.session.valider}
            </Bouton>
          </form>

          <div className="z-entree__separateur">
            <span>{t.session.ou}</span>
          </div>

          <Bouton
            variante="secondaire"
            onClick={() => demarrerConnexion(window.location.pathname)}
          >
            {t.session.connexionKeycloak}
          </Bouton>

          <p className="z-entree__note">{t.session.explication}</p>
        </div>
      </div>
    </main>
  );
}
