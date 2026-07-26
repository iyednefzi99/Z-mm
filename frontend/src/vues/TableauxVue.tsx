import { useCallback, useEffect, useState, type ReactElement } from 'react';
import {
  chargerAlertesSanitaires,
  chargerCalendrier,
  chargerPrevisions,
  chargerProduction,
  chargerSynthese,
  telechargerExport,
} from '../api/client';
import type {
  AlerteSanitaire,
  CalendrierCellule,
  LigneProduction,
  PrevisionRecolte,
  Synthese,
} from '../api/types';
import { useT } from '../i18n/langue';
import { messageErreur } from '../hooks';
import { Bouton, ChampDate } from '../ui/composants';
import { Barres, Tuile } from '../ui/graphiques';
import { useLangue } from '../i18n/langue';

type Sous = 'calendrier' | 'production' | 'previsions' | 'alertes' | 'synthese';

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
  const { langue } = useLangue();
  const indisponible = t.etats.serviceIndisponible;
  const [sous, setSous] = useState<Sous>('calendrier');
  const defaut = moisCourant();
  const [debut, setDebut] = useState(defaut.debut);
  const [fin, setFin] = useState(defaut.fin);
  const [calendrier, setCalendrier] = useState<CalendrierCellule[]>([]);
  const [production, setProduction] = useState<LigneProduction[]>([]);
  const [previsions, setPrevisions] = useState<PrevisionRecolte[]>([]);
  const [alertes, setAlertes] = useState<AlerteSanitaire[]>([]);
  const [synthese, setSynthese] = useState<Synthese | null>(null);
  const [erreur, setErreur] = useState<string | null>(null);

  const chargerCal = useCallback(() => {
    setErreur(null);
    void chargerCalendrier(debut, fin).then(setCalendrier).catch((c) => setErreur(messageErreur(c, indisponible)));
  }, [debut, fin, indisponible]);

  useEffect(() => {
    if (sous === 'calendrier') {
      chargerCal();
    } else if (sous === 'production') {
      setErreur(null);
      void chargerProduction().then(setProduction).catch((c) => setErreur(messageErreur(c, indisponible)));
    } else if (sous === 'previsions') {
      setErreur(null);
      void chargerPrevisions().then(setPrevisions).catch((c) => setErreur(messageErreur(c, indisponible)));
    } else if (sous === 'alertes') {
      setErreur(null);
      void chargerAlertesSanitaires().then(setAlertes).catch((c) => setErreur(messageErreur(c, indisponible)));
    } else {
      setErreur(null);
      void chargerSynthese().then(setSynthese).catch((c) => setErreur(messageErreur(c, indisponible)));
    }
  }, [sous, chargerCal, indisponible]);

  const sousOnglets: Sous[] = ['calendrier', 'production', 'previsions', 'alertes', 'synthese'];

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
          <>
          {/* Le graphique porte la lecture, le tableau porte le detail : l'un ne
              remplace pas l'autre. Le tableau reste la vue accessible de
              reference (lecteur d'ecran, copie, tri par le navigateur). */}
          <Barres
            titre={t.graphique.productionTitre}
            description={t.graphique.productionDescription}
            langue={langue}
            unite=" kg"
            libelleTableau={t.graphique.tableauEquivalent}
            donnees={production
              .filter((p) => p.poidsActuelKg != null)
              .map((p) => ({
                libelle: p.rucheModele,
                valeur: p.poidsActuelKg as number,
                alerte: p.sousSeuil,
              }))}
          />
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
          </>
        )
      )}

      {sous === 'previsions' && (
        previsions.length === 0 ? (
          <p className="z-info">{t.etats.vide}</p>
        ) : (
          <>
          {/* Echelle divergente : ici le SIGNE est l'information. Une ruche qui
              perd 200 g par jour et une qui en gagne 200 g ne sont pas « proches
              en valeur absolue », elles sont opposees. */}
          <Barres
            titre={t.graphique.previsionTitre}
            description={t.graphique.previsionDescription}
            langue={langue}
            unite=" kg/j"
            divergente
            libelleTableau={t.graphique.tableauEquivalent}
            donnees={previsions
              .filter((p) => p.tendanceKgParJour != null)
              .map((p) => ({
                libelle: p.rucheModele,
                valeur: p.tendanceKgParJour as number,
              }))}
          />
          <div className="z-table-enveloppe">
            <table className="z-table">
              <thead>
                <tr>
                  <th>{t.tableau.ruche}</th>
                  <th>{t.tableau.poidsActuel}</th>
                  <th>{t.tableau.tendance}</th>
                  <th>{t.tableau.gainJour}</th>
                  <th>{t.tableau.projection7j}</th>
                  <th>{t.tableau.nbMesures}</th>
                </tr>
              </thead>
              <tbody>
                {previsions.map((p) => (
                  <tr key={p.rucheId} className={p.tendance === 'baisse' ? 'z-ligne--alerte' : ''}>
                    <td>{p.rucheModele}</td>
                    <td>{p.poidsActuelKg ?? t.tableau.aucuneMesure}</td>
                    <td>{t.tableau.tendances[p.tendance]}</td>
                    <td>{p.tendanceKgParJour ?? '—'}</td>
                    <td>{p.projection7jKg ?? '—'}</td>
                    <td>{p.nombreMesures}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          </>
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

      {sous === 'synthese' && synthese && (
        <div className="z-synthese">
          {/* Chiffres de pilotage : un nombre suffit, un graphique serait du
              decor. Le ton porte le statut — et un lisere le double, pour que
              l'alerte ne repose pas sur la seule couleur. */}
          <div className="z-cartes">
            <Tuile libelle={t.tableau.nombreRuches} valeur={synthese.nombreRuches} />
            <Tuile libelle={t.tableau.nombreVisites} valeur={synthese.nombreVisites} />
            <Tuile
              libelle={t.tableau.poidsTotal}
              valeur={synthese.poidsTotalActuelKg}
              precision="kg"
            />
            <Tuile
              libelle={t.tableau.alertesOuvertes}
              valeur={synthese.alertesOuvertes}
              ton={synthese.alertesOuvertes > 0 ? 'danger' : 'succes'}
            />
            <Tuile
              libelle={t.tableau.roiPourcent}
              valeur={synthese.roi.roiPourcent != null ? `${synthese.roi.roiPourcent} %` : '—'}
              ton={
                synthese.roi.roiPourcent != null && synthese.roi.roiPourcent < 0
                  ? 'danger'
                  : 'succes'
              }
            />
          </div>
          <div className="z-table-enveloppe">
            <table className="z-table">
              <tbody>
                <tr>
                  <td>{t.tableau.valeurProduction}</td>
                  <td>{synthese.roi.valeurProductionEur}</td>
                </tr>
                <tr>
                  <td>{t.tableau.coutInterventions}</td>
                  <td>{synthese.roi.coutInterventionsEur}</td>
                </tr>
                <tr>
                  <td>{t.tableau.visitesParRaison}</td>
                  <td>
                    {Object.entries(synthese.visitesParRaison)
                      .map(([raison, n]) => `${raison} : ${n}`)
                      .join(' · ') || '—'}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      )}
    </section>
  );
}
