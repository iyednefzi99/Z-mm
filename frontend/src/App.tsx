import { useEffect, useState, type ReactElement } from 'react';
import { fermerSession, jetonCourant, surSession } from './auth/session';
import { Bouton } from './ui/composants';
import { L } from './ui/libelles';
import { AgentsVue } from './vues/AgentsVue';
import { ConfigVue } from './vues/ConfigVue';
import { ConnexionVue } from './vues/ConnexionVue';
import { FermesVue } from './vues/FermesVue';
import { FermiersVue } from './vues/FermiersVue';
import { SitesVue } from './vues/SitesVue';
import './App.css';

type Onglet = keyof typeof L.onglets;

const VUES: Record<Onglet, ReactElement> = {
  fermiers: <FermiersVue />,
  fermes: <FermesVue />,
  sites: <SitesVue />,
  agents: <AgentsVue />,
  config: <ConfigVue />,
};

const ONGLETS = Object.keys(L.onglets) as Onglet[];

/**
 * Console de gestion Zümm (SPRINT-01). Ossature : barre de navigation entre les
 * ressources metier et zone de contenu. Sans session, l'ecran de connexion prend
 * toute la place.
 */
export default function App(): ReactElement {
  const [jeton, setJeton] = useState<string | null>(jetonCourant());
  const [onglet, setOnglet] = useState<Onglet>('fermiers');

  useEffect(() => surSession(setJeton), []);

  if (!jeton) {
    return <ConnexionVue />;
  }

  return (
    <div className="z-app">
      <header className="z-topbar">
        <div className="z-marque">
          <span className="z-marque__pastille" aria-hidden="true" />
          <span className="z-marque__nom">{L.marque}</span>
          <span className="z-marque__baseline">{L.baseline}</span>
        </div>
        <Bouton variante="fantome" onClick={fermerSession}>
          {L.actions.seDeconnecter}
        </Bouton>
      </header>

      <nav className="z-nav" aria-label={L.marque}>
        {ONGLETS.map((cle) => (
          <button
            key={cle}
            type="button"
            className="z-onglet"
            aria-current={cle === onglet}
            onClick={() => setOnglet(cle)}
          >
            {L.onglets[cle]}
          </button>
        ))}
      </nav>

      <main className="z-vue">{VUES[onglet]}</main>
    </div>
  );
}
