import { Suspense, lazy, useEffect, useState, type ReactElement } from 'react';
import { rafraichirJeton, synchroniser } from './api/client';
import { deconnexionOidc, oidcConfigure, terminerConnexion } from './auth/oidc';
import { planifier } from './auth/rafraichissement';
import { fermerSession, jetonCourant, surSession } from './auth/session';
import { gabarit } from './i18n/console';
import { LANGUES } from './i18n/messages';
import { useLangue, useT } from './i18n/langue';
import { surFile } from './offline/file';
import { useNavigation } from './routage/navigation';
import { ONGLETS, cheminDepuisOnglet, ongletDepuisChemin, type Onglet } from './routage/routes';
import { appliquerMiseAJour, useMiseAJourPwa } from './pwa';
import { Bouton } from './ui/composants';
import { ConnexionVue } from './vues/ConnexionVue';
import { IntrouvableVue } from './vues/IntrouvableVue';
import './App.css';

/**
 * Chargement paresseux par route (US-051) : sans lui, les quinze vues partent
 * dans le même paquet, alors qu'une session n'en visite que quelques-unes.
 */
const VUES: Record<Onglet, React.LazyExoticComponent<() => ReactElement>> = {
  fermiers: lazy(() => import('./vues/FermiersVue').then((m) => ({ default: m.FermiersVue }))),
  fermes: lazy(() => import('./vues/FermesVue').then((m) => ({ default: m.FermesVue }))),
  sites: lazy(() => import('./vues/SitesVue').then((m) => ({ default: m.SitesVue }))),
  ruches: lazy(() => import('./vues/RuchesVue').then((m) => ({ default: m.RuchesVue }))),
  plannings: lazy(() => import('./vues/PlanningsVue').then((m) => ({ default: m.PlanningsVue }))),
  visites: lazy(() => import('./vues/VisitesVue').then((m) => ({ default: m.VisitesVue }))),
  taches: lazy(() => import('./vues/TachesVue').then((m) => ({ default: m.TachesVue }))),
  tableaux: lazy(() => import('./vues/TableauxVue').then((m) => ({ default: m.TableauxVue }))),
  capteurs: lazy(() => import('./vues/CapteursVue').then((m) => ({ default: m.CapteursVue }))),
  reines: lazy(() => import('./vues/ReinesVue').then((m) => ({ default: m.ReinesVue }))),
  recoltes: lazy(() => import('./vues/RecoltesVue').then((m) => ({ default: m.RecoltesVue }))),
  lots: lazy(() => import('./vues/LotsVue').then((m) => ({ default: m.LotsVue }))),
  carte: lazy(() => import('./vues/CarteVue').then((m) => ({ default: m.CarteVue }))),
  agents: lazy(() => import('./vues/AgentsVue').then((m) => ({ default: m.AgentsVue }))),
  config: lazy(() => import('./vues/ConfigVue').then((m) => ({ default: m.ConfigVue }))),
  audit: lazy(() => import('./vues/AuditVue').then((m) => ({ default: m.AuditVue }))),
};

/**
 * Console de gestion Zümm. Ossature : barre de navigation entre les ressources
 * metier, selecteur de langue (FR/EN/AR, RTL en arabe) et zone de contenu. Chaque
 * ecran a son adresse (US-051). Sans session, l'ecran de connexion prend toute la
 * place — l'URL, elle, est conservee, pour y revenir apres reconnexion.
 */
export default function App(): ReactElement {
  const t = useT();
  const { langue, definirLangue } = useLangue();
  const majDisponible = useMiseAJourPwa();
  const { chemin, naviguer } = useNavigation();
  const [jeton, setJeton] = useState<string | null>(jetonCourant());
  const [enAttente, setEnAttente] = useState(0);
  const [horsLigne, setHorsLigne] = useState(!navigator.onLine);

  const onglet = ongletDepuisChemin(chemin);

  useEffect(() => surSession(setJeton), []);

  // Retour de connexion OIDC (US-020) : échange le code contre un jeton.
  useEffect(() => {
    void terminerConnexion()
      .then((traite) => {
        // La route de retour a été restaurée dans l'historique : la vue doit suivre.
        if (traite) {
          naviguer(window.location.pathname);
        }
      })
      .catch(() => undefined);
  }, [naviguer]);

  // Session durable (US-050) : renouveler le jeton AVANT son échéance. Le 401 et
  // son rejeu restent le filet de sécurité ; ici, on évite d'y arriver. La
  // planification suit le jeton — chaque renouvellement en programme le suivant.
  useEffect(() => {
    if (!jeton) {
      return undefined;
    }
    return planifier(jeton, () => void rafraichirJeton());
  }, [jeton]);

  // Synchronisation différée (US-011) : file d'attente + retour du réseau.
  useEffect(() => {
    const desabonner = surFile(setEnAttente);
    const enLigne = () => {
      setHorsLigne(false);
      void synchroniser();
    };
    const deconnecte = () => setHorsLigne(true);
    window.addEventListener('online', enLigne);
    window.addEventListener('offline', deconnecte);
    if (navigator.onLine) {
      void synchroniser();
    }
    return () => {
      desabonner();
      window.removeEventListener('online', enLigne);
      window.removeEventListener('offline', deconnecte);
    };
  }, []);

  if (!jeton) {
    return <ConnexionVue />;
  }

  const Vue = onglet === null ? null : VUES[onglet];

  return (
    <div className="z-app">
      {majDisponible && (
        // Bandeau, pas rechargement automatique : sur un rucher, recharger sans
        // prevenir ferait perdre un rapport de visite en cours de saisie.
        <div className="z-bandeau-maj" role="status">
          <span>{t.pwa.majDisponible}</span>
          <Bouton variante="primaire" onClick={() => void appliquerMiseAJour()}>
            {t.pwa.actualiser}
          </Bouton>
        </div>
      )}
      <header className="z-topbar">
        <div className="z-marque">
          <span className="z-marque__pastille" aria-hidden="true" />
          <span className="z-marque__nom">{t.marque}</span>
          <span className="z-marque__baseline">{t.baseline}</span>
        </div>
        <div className="z-topbar__actions">
          {(horsLigne || enAttente > 0) && (
            <span className={`z-sync ${horsLigne ? 'z-sync--hors-ligne' : ''}`} role="status">
              {horsLigne ? t.sync.horsLigne : gabarit(t.sync.enAttente, { n: String(enAttente) })}
            </span>
          )}
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
          <Bouton
            variante="fantome"
            onClick={() => (oidcConfigure() ? deconnexionOidc() : fermerSession())}
          >
            {t.actions.seDeconnecter}
          </Bouton>
        </div>
      </header>

      <nav className="z-nav" aria-label={t.marque}>
        {ONGLETS.map((cle) => (
          <button
            key={cle}
            type="button"
            className="z-onglet"
            aria-current={cle === onglet}
            onClick={() => naviguer(cheminDepuisOnglet(cle))}
          >
            {t.onglets[cle]}
          </button>
        ))}
      </nav>

      <main className="z-vue">
        <Suspense
          fallback={
            <p className="z-info" role="status">
              {t.etats.chargement}
            </p>
          }
        >
          {Vue === null ? (
            <IntrouvableVue onRetour={() => naviguer('/')} />
          ) : (
            <Vue />
          )}
        </Suspense>
      </main>
    </div>
  );
}
