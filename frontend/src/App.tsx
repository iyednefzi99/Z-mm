import { useEffect, useState, type ReactElement } from 'react';
import { fermerSession, jetonCourant, surSession } from './auth/session';
import { LANGUES } from './i18n/messages';
import { useLangue, useT } from './i18n/langue';
import { Bouton } from './ui/composants';
import { AgentsVue } from './vues/AgentsVue';
import { ConfigVue } from './vues/ConfigVue';
import { ConnexionVue } from './vues/ConnexionVue';
import { FermesVue } from './vues/FermesVue';
import { FermiersVue } from './vues/FermiersVue';
import { RuchesVue } from './vues/RuchesVue';
import { SitesVue } from './vues/SitesVue';
import './App.css';

type Onglet = 'fermiers' | 'fermes' | 'sites' | 'ruches' | 'agents' | 'config';

const ONGLETS: Onglet[] = ['fermiers', 'fermes', 'sites', 'ruches', 'agents', 'config'];

const VUES: Record<Onglet, ReactElement> = {
  fermiers: <FermiersVue />,
  fermes: <FermesVue />,
  sites: <SitesVue />,
  ruches: <RuchesVue />,
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

  useEffect(() => surSession(setJeton), []);

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
