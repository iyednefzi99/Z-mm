import type { ReactElement, ReactNode } from 'react';
import type { Session } from '../auth/session';
import { gabarit } from '../i18n/console';
import { LANGUES } from '../i18n/messages';
import { useLangue, useT } from '../i18n/langue';
import { ONGLETS, ROUTES_PUBLIQUES } from '../routage/routes';
import { SelecteurTheme } from '../theme/theme';
import { Bouton } from '../ui/composants';

/**
 * Ossature des pages publiques — barre, pied, et rien d'autre (SPRINT-19).
 *
 * <p>Extraite de `AccueilVue` au moment où trois pages de plus ont eu besoin de
 * la même barre et du même pied. La dupliquer aurait garanti la dérive : un
 * sélecteur de langue ajouté ici, oublié là, et le visiteur perd sa langue en
 * ouvrant les mentions légales.
 *
 * <p>Elle est volontairement plus légère que la barre de la console : il n'y a
 * rien à naviguer, seulement à entrer. Le pied, lui, porte les liens légaux —
 * c'est là qu'on les cherche, et nulle part ailleurs.
 *
 * <p>Comme l'accueil, elle <strong>n'appelle aucune API</strong> : c'est
 * l'ossature de tout ce que l'application sert sans jeton.
 */
export function CoquillePublique({
  session,
  onNaviguer,
  children,
}: {
  session: Session | null;
  onNaviguer: (chemin: string) => void;
  children: ReactNode;
}): ReactElement {
  const t = useT();
  const { langue, definirLangue } = useLangue();

  const versConnexion = () => onNaviguer(ROUTES_PUBLIQUES.connexion);
  const versConsole = () => onNaviguer('/');

  /** Liens du pied : les pages qu'on ne trouve qu'ici. */
  const LIENS = [
    { chemin: ROUTES_PUBLIQUES.apropos, libelle: t.accueil.pied.apropos },
    { chemin: ROUTES_PUBLIQUES.cgu, libelle: t.accueil.pied.cgu },
    { chemin: ROUTES_PUBLIQUES.confidentialite, libelle: t.accueil.pied.confidentialite },
  ];

  return (
    <div className="z-accueil">
      <header className="z-accueil__barre">
        {/* La marque ramène à l'accueil : c'est la convention du web, et sur une
            page légale c'est la seule sortie évidente. */}
        <button
          type="button"
          className="z-marque z-marque--lien"
          onClick={() => onNaviguer(ROUTES_PUBLIQUES.accueil)}
        >
          <span className="z-marque__pastille" aria-hidden="true" />
          <span className="z-marque__nom">{t.marque}</span>
          <span className="z-marque__baseline">{t.baseline}</span>
        </button>
        <div className="z-accueil__barre-actions">
          <nav className="z-langues" aria-label={t.langue}>
            {LANGUES.map((code) => (
              <button
                key={code}
                type="button"
                className="z-langue"
                aria-current={code === langue}
                onClick={() => definirLangue(code)}
              >
                {code.toUpperCase()}
              </button>
            ))}
          </nav>
          <SelecteurTheme />
          {session ? (
            <Bouton variante="primaire" onClick={versConsole}>
              {t.accueil.ouvrirConsole}
            </Bouton>
          ) : (
            <Bouton variante="primaire" onClick={versConnexion}>
              {t.accueil.seConnecter}
            </Bouton>
          )}
        </div>
      </header>

      <main>{children}</main>

      <footer className="z-accueil__pied">
        <nav className="z-accueil__pied-liens" aria-label={t.accueil.pied.navigation}>
          {LIENS.map(({ chemin, libelle }) => (
            <button
              key={chemin}
              type="button"
              className="z-lien"
              onClick={() => onNaviguer(chemin)}
            >
              {libelle}
            </button>
          ))}
        </nav>
        <p className="z-accueil__pied-mentions">
          <span>{t.accueil.pied.note}</span>
          <span aria-hidden="true"> · </span>
          {/* Le nombre d'écrans se lit dans la table des routes : une vitrine qui
              promet seize écrans quand le produit en sert dix-sept vieillit mal. */}
          <span>{gabarit(t.accueil.pied.ecrans, { n: String(ONGLETS.length) })}</span>
          <span aria-hidden="true"> · </span>
          <span>{t.accueil.pied.langues}</span>
        </p>
      </footer>
    </div>
  );
}
