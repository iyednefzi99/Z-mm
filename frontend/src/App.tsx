import { useEffect, useState, type ReactElement } from 'react';
import { synchroniser } from './api/client';
import { terminerConnexion } from './auth/oidc';
import { fermerSession, jetonCourant, surSession } from './auth/session';
import { gabarit } from './i18n/console';
import { LANGUES } from './i18n/messages';
import { useLangue, useT } from './i18n/langue';
import { surFile } from './offline/file';
import { Bouton } from './ui/composants';
import { AgentsVue } from './vues/AgentsVue';
import { ConfigVue } from './vues/ConfigVue';
import { ConnexionVue } from './vues/ConnexionVue';
import { FermesVue } from './vues/FermesVue';
import { FermiersVue } from './vues/FermiersVue';
import { PlanningsVue } from './vues/PlanningsVue';
import { CapteursVue } from './vues/CapteursVue';
import { CarteVue } from './vues/CarteVue';
import { RecoltesVue } from './vues/RecoltesVue';
import { ReinesVue } from './vues/ReinesVue';
import { RuchesVue } from './vues/RuchesVue';
import { SitesVue } from './vues/SitesVue';
import { TableauxVue } from './vues/TableauxVue';
import { TachesVue } from './vues/TachesVue';
import { VisitesVue } from './vues/VisitesVue';
import './App.css';

type Onglet =
  | 'fermiers'
  | 'fermes'
  | 'sites'
  | 'ruches'
  | 'plannings'
  | 'visites'
  | 'taches'
  | 'tableaux'
  | 'capteurs'
  | 'reines'
  | 'recoltes'
  | 'carte'
  | 'agents'
  | 'config';

const ONGLETS: Onglet[] = [
  'fermiers',
  'fermes',
  'sites',
  'ruches',
  'plannings',
  'visites',
  'taches',
  'tableaux',
  'capteurs',
  'reines',
  'recoltes',
  'carte',
  'agents',
  'config',
];

const VUES: Record<Onglet, ReactElement> = {
  fermiers: <FermiersVue />,
  fermes: <FermesVue />,
  sites: <SitesVue />,
  ruches: <RuchesVue />,
  plannings: <PlanningsVue />,
  visites: <VisitesVue />,
  taches: <TachesVue />,
  tableaux: <TableauxVue />,
  capteurs: <CapteursVue />,
  reines: <ReinesVue />,
  recoltes: <RecoltesVue />,
  carte: <CarteVue />,
  agents: <AgentsVue />,
  config: <ConfigVue />,
};

/**
 * Console de gestion Zümm (SPRINT-01/02). Ossature : barre de navigation entre
 * les ressources metier, selecteur de langue (FR/EN/AR, RTL en arabe) et zone de
 * contenu. Sans session, l'ecran de connexion prend toute la place.
 */
export default function App(): ReactElement {
  const t = useT();
  const { langue, definirLangue } = useLangue();
  const [jeton, setJeton] = useState<string | null>(jetonCourant());
  const [onglet, setOnglet] = useState<Onglet>('fermiers');
  const [enAttente, setEnAttente] = useState(0);
  const [horsLigne, setHorsLigne] = useState(!navigator.onLine);

  useEffect(() => surSession(setJeton), []);

  // Retour de connexion OIDC (US-020) : échange le code contre un jeton.
  useEffect(() => {
    void terminerConnexion().catch(() => undefined);
  }, []);

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

  return (
    <div className="z-app">
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
          <Bouton variante="fantome" onClick={fermerSession}>
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
            onClick={() => setOnglet(cle)}
          >
            {t.onglets[cle]}
          </button>
        ))}
      </nav>

      <main className="z-vue">{VUES[onglet]}</main>
    </div>
  );
}
