import type { ReactElement, ReactNode } from 'react';
import type { Session } from '../auth/session';
import { gabarit } from '../i18n/console';
import { LANGUES } from '../i18n/messages';
import { useLangue, useT } from '../i18n/langue';
import { ONGLETS, ROUTES_PUBLIQUES } from '../routage/routes';
import { SelecteurTheme } from '../theme/theme';
import { Bouton } from '../ui/composants';
import { BordDechire } from '../ui/papier';

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
 *
 * <p><strong>Le pied est arraché du reste de la page.</strong> Il change de
 * fond — vert ardoise sous une encre claire — et la déchirure marque ce
 * changement plutôt qu'un filet d'un pixel. Un filet sépare deux zones de même
 * matière ; ici la matière change, et le bord doit le dire. C'est le seul
 * endroit de la coquille qui porte cette texture : les pages légales sont de la
 * prose longue, et une trame sous un texte de loi le rend plus dur à lire.
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

  /**
   * Liens du pied : les pages qu'on ne trouve qu'ici.
   *
   * <p>L'ordre suit l'intention du lecteur, pas l'ordre de livraison : d'abord
   * ce qui fait décider (fonctionnalités, éditions), puis ce qui aide
   * (ressources, contact), enfin ce qui engage (à propos, CGU, confidentialité).
   */
  const LIENS = [
    { chemin: ROUTES_PUBLIQUES.fonctionnalites, libelle: t.accueil.pied.fonctionnalites },
    { chemin: ROUTES_PUBLIQUES.editions, libelle: t.accueil.pied.editions },
    { chemin: ROUTES_PUBLIQUES.ressources, libelle: t.accueil.pied.ressources },
    { chemin: ROUTES_PUBLIQUES.contact, libelle: t.accueil.pied.contact },
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
        <BordDechire teinte="fond" />
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
              promet un total que le produit a dépassé depuis vieillit mal. */}
          <span>{gabarit(t.accueil.pied.ecrans, { n: String(ONGLETS.length) })}</span>
          <span aria-hidden="true"> · </span>
          <span>{t.accueil.pied.langues}</span>
        </p>
      </footer>
    </div>
  );
}
