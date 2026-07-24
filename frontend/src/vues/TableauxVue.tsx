import { useCallback, useEffect, useState, type ReactElement } from 'react';
import {
  chargerAlertesSanitaires,
  chargerCalendrier,
  chargerProduction,
  telechargerExport,
} from '../api/client';
import type { AlerteSanitaire, CalendrierCellule, LigneProduction } from '../api/types';
import { useT } from '../i18n/langue';
import { messageErreur } from '../hooks';
import { Bouton, ChampDate } from '../ui/composants';

type Sous = 'calendrier' | 'production' | 'alertes';

/** Premier et dernier jour du mois courant, au format ISO (valeurs par défaut du calendrier). */
function moisCourant(): { debut: string; fin: string } {
  const maintenant = new Date();
  const premier = new Date(maintenant.getFullYear(), maintenant.getMonth(), 1);
  const dernier = new Date(maintenant.getFullYear(), maintenant.getMonth() + 1, 0);
  return { debut: premier.toISOString().slice(0, 10), fin: dernier.toISOString().slice(0, 10) };
}

/**
 * Tableaux de bord de pilotage (SPRINT-05) : calendrier matriciel agents × ruches
 * (US-012), production (US-013), alertes sanitaires (US-014) et export (US-027).
 */
export function TableauxVue(): ReactElement {
  const t = useT();
  const [sous, setSous] = useState<Sous>('calendrier');
  const defaut = moisCourant();
  const [debut, setDebut] = useState(defaut.debut);
  const [fin, setFin] = useState(defaut.fin);
  const [calendrier, setCalendrier] = useState<CalendrierCellule[]>([]);
  const [production, setProduction] = useState<LigneProduction[]>([]);
  const [alertes, setAlertes] = useState<AlerteSanitaire[]>([]);
  const [erreur, setErreur] = useState<string | null>(null);

  const chargerCal = useCallback(() => {
    setErreur(null);
    void chargerCalendrier(debut, fin).then(setCalendrier).catch((c) => setErreur(messageErreur(c)));
  }, [debut, fin]);

  useEffect(() => {
    if (sous === 'calendrier') {
      chargerCal();
    } else if (sous === 'production') {
      setErreur(null);
      void chargerProduction().then(setProduction).catch((c) => setErreur(messageErreur(c)));
    } else {
      setErreur(null);
      void chargerAlertesSanitaires().then(setAlertes).catch((c) => setErreur(messageErreur(c)));
    }
  }, [sous, chargerCal]);

  const sousOnglets: Sous[] = ['calendrier', 'production', 'alertes'];

  return (
    <section className="z-section">
      <header className="z-section__entete">
        <h1 className="z-section__titre">{t.onglets.tableaux}</h1>
        <div className="z-topbar__actions">
          <Bouton variante="secondaire" onClick={() => void telechargerExport('visites', 'csv')}>
            ⬇ {t.tableau.exporterVisites}
          </Bouton>
          <Bouton variante="secondaire" onClick={() => void telechargerExport('ruches', 'csv')}>
            ⬇ {t.tableau.exporterRuches}
          </Bouton>
        </div>
      </header>

      <nav className="z-nav" aria-label={t.onglets.tableaux}>
        {sousOnglets.map((cle) => (
          <button
            key={cle}
            type="button"
            className="z-onglet"
            aria-current={cle === sous}
            onClick={() => setSous(cle)}
          >
            {t.tableau[cle]}
          </button>
        ))}
      </nav>

      {erreur && (
        <div className="z-erreur" role="alert">
          <span>{erreur}</span>
        </div>
      )}

      {sous === 'calendrier' && (
        <>
          <div className="z-form__grille">
            <ChampDate libelle={t.tableau.du} valeur={debut} onChange={setDebut} />
            <ChampDate libelle={t.tableau.au} valeur={fin} onChange={setFin} />
            <div className="z-champ z-champ--aligne-bas">
              <Bouton variante="primaire" onClick={chargerCal}>
                {t.tableau.afficher}
              </Bouton>
            </div>
          </div>
          {calendrier.length === 0 ? (
            <p className="z-info">{t.etats.vide}</p>
          ) : (
            <div className="z-table-enveloppe">
              <table className="z-table">
                <thead>
                  <tr>
                    <th>{t.tableau.agent}</th>
                    <th>{t.tableau.ruche}</th>
                    <th>{t.tableau.nbVisites}</th>
                    <th>{t.visite.date}</th>
                  </tr>
                </thead>
                <tbody>
                  {calendrier.map((c) => (
                    <tr key={`${c.agentId}-${c.rucheId}`}>
                      <td>{c.agentNom}</td>
                      <td>{c.rucheModele}</td>
                      <td>{c.nombreVisites}</td>
                      <td>{c.visites.map((v) => v.date).join(', ')}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}

      {sous === 'production' && (
        production.length === 0 ? (
          <p className="z-info">{t.etats.vide}</p>
        ) : (
          <div className="z-table-enveloppe">
            <table className="z-table">
              <thead>
                <tr>
                  <th>{t.tableau.ruche}</th>
                  <th>{t.tableau.poidsActuel}</th>
                  <th>{t.tableau.poidsMin}</th>
                  <th>{t.tableau.poidsMax}</th>
                  <th>{t.tableau.nbMesures}</th>
                  <th>{t.tableau.productivite}</th>
                </tr>
              </thead>
              <tbody>
                {production.map((p) => (
                  <tr key={p.rucheId} className={p.sousSeuil ? 'z-ligne--alerte' : ''}>
                    <td>{p.rucheModele}</td>
                    <td>{p.poidsActuelKg ?? t.tableau.aucuneMesure}</td>
                    <td>{p.poidsMinKg ?? '—'}</td>
                    <td>{p.poidsMaxKg ?? '—'}</td>
                    <td>{p.nombreMesures}</td>
                    <td>{p.productiviteMoyenne ?? '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )
      )}

      {sous === 'alertes' && (
        alertes.length === 0 ? (
          <p className="z-info">{t.etats.vide}</p>
        ) : (
          <div className="z-table-enveloppe">
            <table className="z-table">
              <thead>
                <tr>
                  <th>{t.tableau.ruche}</th>
                  <th>{t.tableau.niveau}</th>
                  <th>{t.tableau.etatSante}</th>
                  <th>{t.tableau.derniereVisite}</th>
                  <th>{t.tableau.motif}</th>
                </tr>
              </thead>
              <tbody>
                {alertes.map((a) => (
                  <tr key={a.rucheId} className={`z-ligne--${a.niveau}`}>
                    <td>{a.rucheModele}</td>
                    <td>{t.tableau.niveaux[a.niveau]}</td>
                    <td>{a.dernierEtatSante ? t.visite.santes[a.dernierEtatSante] : '—'}</td>
                    <td>
                      {a.derniereVisite
                        ? `${a.derniereVisite} (${a.joursDepuisVisite} ${t.tableau.jours})`
                        : t.tableau.jamais}
                    </td>
                    <td>{a.motif}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )
      )}
    </section>
  );
}
