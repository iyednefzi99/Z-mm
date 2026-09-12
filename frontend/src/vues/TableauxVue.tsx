import { useCallback, useEffect, useState, type ReactElement } from 'react';
import {
  chargeEquipe,
  correlationsFlore,
  correlationsMeteo,
  chargerAlertesSanitaires,
  chargerCalendrier,
  chargerPrevisions,
  chargerProduction,
  chargerSynthese,
  listerIndices,
  syntheseRuchers,
  telechargerExport,
} from '../api/client';
import type {
  AlerteSanitaire,
  CalendrierCellule,
  ChargeAgent,
  CorrelationFlore,
  CorrelationMeteo,
  EtatSante,
  IndiceColonie,
  LigneProduction,
  NiveauAlerte,
  PrevisionRecolte,
  Synthese,
  SyntheseRucher,
} from '../api/types';
import { gabarit } from '../i18n/console';
import { useFormats, useT } from '../i18n/langue';
import { messageErreur } from '../hooks';
import { Bouton, ChampDate, Pastille, type TonPastille } from '../ui/composants';
import { Barres, Tuile } from '../ui/graphiques';
import { BriefingPanneau } from './BriefingPanneau';
import { useLangue } from '../i18n/langue';

type Sous =
  | 'calendrier'
  | 'production'
  | 'previsions'
  | 'alertes'
  | 'synthese'
  | 'ruchers'
  | 'equipe'
  | 'indices'
  | 'correlations'
  | 'correlationsFlore';

/** Gravité d'un niveau d'alerte, et santé de la dernière visite, en tons de pastille. */
const TON_NIVEAU: Record<NiveauAlerte, TonPastille> = {
  ok: 'succes',
  attention: 'attention',
  critique: 'danger',
};

const TON_SANTE: Record<EtatSante, TonPastille> = {
  bon: 'succes',
  moyen: 'attention',
  mauvais: 'danger',
};

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
  const f = useFormats();
  const indisponible = t.etats.serviceIndisponible;
  const [sous, setSous] = useState<Sous>('calendrier');
  const defaut = moisCourant();
  const [debut, setDebut] = useState(defaut.debut);
  const [fin, setFin] = useState(defaut.fin);
  const [calendrier, setCalendrier] = useState<CalendrierCellule[]>([]);
  // Indices et corrélations : deux lectures CALCULÉES, jamais stockées — elles
  // se rechargent à chaque ouverture de l'onglet plutôt que de vivre en cache.
  const [indices, setIndices] = useState<IndiceColonie[]>([]);
  const [correlations, setCorrelations] = useState<CorrelationMeteo[]>([]);
  const [correlationsSol, setCorrelationsSol] = useState<CorrelationFlore[]>([]);
  const [production, setProduction] = useState<LigneProduction[]>([]);
  const [previsions, setPrevisions] = useState<PrevisionRecolte[]>([]);
  const [alertes, setAlertes] = useState<AlerteSanitaire[]>([]);
  const [synthese, setSynthese] = useState<Synthese | null>(null);
  // Le niveau intermediaire — celui auquel on travaille : personne ne se
  // deplace pour une ruche ni pour une exploitation, on va au rucher.
  const [ruchers, setRuchers] = useState<SyntheseRucher[]>([]);
  const [equipe, setEquipe] = useState<ChargeAgent[]>([]);
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
    } else if (sous === 'ruchers') {
      void syntheseRuchers().then(setRuchers)
        .catch((c) => setErreur(messageErreur(c, indisponible)));
    } else if (sous === 'equipe') {
      void chargeEquipe().then(setEquipe)
        .catch((c) => setErreur(messageErreur(c, indisponible)));
    } else if (sous === 'alertes') {
      setErreur(null);
      void chargerAlertesSanitaires().then(setAlertes).catch((c) => setErreur(messageErreur(c, indisponible)));
    } else if (sous === 'indices') {
      setErreur(null);
      void listerIndices().then(setIndices).catch((c) => setErreur(messageErreur(c, indisponible)));
    } else if (sous === 'correlations') {
      setErreur(null);
      void correlationsMeteo().then(setCorrelations).catch((c) => setErreur(messageErreur(c, indisponible)));
    } else if (sous === 'correlationsFlore') {
      setErreur(null);
      void correlationsFlore().then(setCorrelationsSol).catch((c) => setErreur(messageErreur(c, indisponible)));
    } else {
      setErreur(null);
      void chargerSynthese().then(setSynthese).catch((c) => setErreur(messageErreur(c, indisponible)));
    }
  }, [sous, chargerCal, indisponible]);

  const sousOnglets: Sous[] = ['calendrier', 'production', 'previsions', 'alertes',
    'synthese', 'ruchers', 'equipe', 'indices', 'correlations', 'correlationsFlore'];

  return (
    <section className="z-section">
      <header className="z-section__entete">
        <div>
          <h1 className="z-section__titre">{t.onglets.tableaux}</h1>
          <p className="z-section__soustitre">{t.soustitres.tableaux}</p>
        </div>
        <div className="z-topbar__actions">
          <Bouton variante="secondaire" onClick={() => void telechargerExport('visites', 'csv')}>
            ⬇ {t.tableau.exporterVisites}
          </Bouton>
          <Bouton variante="secondaire" onClick={() => void telechargerExport('ruches', 'csv')}>
            ⬇ {t.tableau.exporterRuches}
          </Bouton>
        </div>
      </header>

      {/* Le point du jour (SPRINT-30) vient AVANT les onglets : c'est ce qu'on
          ouvre le matin, et le mettre sous une navigation en ferait un écran de
          plus à penser à visiter. */}
      <BriefingPanneau />

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
            messageVide={t.graphique.aucuneDonnee}
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
            messageVide={t.graphique.aucuneDonnee}
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
                    {/* La gravité était un mot posé dans une colonne, doublé par
                        la seule teinte du fond de ligne. La pastille en fait un
                        repère : le regard trie la colonne sans la lire. */}
                    <td>
                      <Pastille ton={TON_NIVEAU[a.niveau]}>{t.tableau.niveaux[a.niveau]}</Pastille>
                    </td>
                    <td>
                      {a.dernierEtatSante ? (
                        <Pastille ton={TON_SANTE[a.dernierEtatSante]}>
                          {t.visite.santes[a.dernierEtatSante]}
                        </Pastille>
                      ) : (
                        '—'
                      )}
                    </td>
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

      {sous === 'ruchers' && (
        <>
          <p className="z-info">{t.rucher.aide}</p>
          {ruchers.length === 0 ? (
            <p className="z-info">{t.etats.vide}</p>
          ) : (
            <div className="z-table-enveloppe">
              <table className="z-table">
                <thead>
                  <tr>
                    <th>{t.onglets.sites}</th>
                    <th>{t.champs.prioriteTerrain}</th>
                    <th>{t.onglets.ruches}</th>
                    <th>{t.rucher.sante}</th>
                    <th>{t.rucher.risqueMax}</th>
                    <th>{t.rucher.sousCarence}</th>
                    <th>{t.rucher.production}</th>
                  </tr>
                </thead>
                <tbody>
                  {ruchers.map((r) => (
                    <tr key={r.siteId}>
                      <td>
                        {r.siteNom}
                        {r.ville ? <small className="z-info"> · {r.ville}</small> : null}
                      </td>
                      <td>{t.prioriteTerrain[r.priorite]}</td>
                      <td>
                        {r.nbActives} / {r.nbRuches}
                      </td>
                      <td>
                        {/* Pas de jauge sur du vide : un rucher qu'on n'a pas
                            encore visite n'est pas en mauvaise sante, il est
                            inconnu. */}
                        {r.santeMoyenne === null ? (
                          <span className="z-info">{t.rucher.nonEvalue}</span>
                        ) : (
                          <>
                            {r.santeMoyenne}
                            <small className="z-info">
                              {' '}
                              ({gabarit(t.rucher.evaluees, {
                                nombre: String(r.coloniesEvaluees),
                              })})
                            </small>
                          </>
                        )}
                      </td>
                      <td>
                        {r.risqueEssaimageMax === null ? '—' : r.risqueEssaimageMax}
                      </td>
                      <td>{r.ruchesSousCarence === 0 ? '—' : r.ruchesSousCarence}</td>
                      <td>{f.nombre(r.productionKg)} kg</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}

      {sous === 'equipe' && (
        <>
          <p className="z-info">{t.equipe.aide}</p>
          {equipe.length === 0 ? (
            <p className="z-info">{t.etats.vide}</p>
          ) : (
            <div className="z-table-enveloppe">
              <table className="z-table">
                <thead>
                  <tr>
                    <th>{t.visite.agent}</th>
                    <th>{t.equipe.ruches}</th>
                    <th>{t.equipe.ruchers}</th>
                    <th>{t.equipe.taches}</th>
                    <th>{t.equipe.retard}</th>
                    <th>{t.equipe.critiques}</th>
                    <th>{t.equipe.visites}</th>
                  </tr>
                </thead>
                <tbody>
                  {equipe.map((a) => (
                    <tr key={a.agentId}>
                      <td>{a.agentNom}</td>
                      <td>{a.ruchesResponsable}</td>
                      <td>{a.ruchersConcernes}</td>
                      <td>{a.tachesOuvertes}</td>
                      <td>{a.tachesEnRetard === 0 ? '—' : a.tachesEnRetard}</td>
                      <td>{a.tachesCritiques === 0 ? '—' : a.tachesCritiques}</td>
                      <td>{a.visites7Jours}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}

      {sous === 'indices' && (
        <>
          <p className="z-info">{t.indice.aide}</p>
          {indices.length === 0 ? (
            <p className="z-info">{t.indice.aucun}</p>
          ) : (
            <div className="z-table-enveloppe">
              <table className="z-table">
                <thead>
                  <tr>
                    <th>{t.tableau.ruche}</th>
                    <th>{t.indice.sante}</th>
                    <th>{t.indice.essaimage}</th>
                    <th>{t.indice.motifsTitre}</th>
                  </tr>
                </thead>
                <tbody>
                  {indices.map((indice) => (
                    <tr key={indice.rucheId}>
                      <td>{indice.rucheModele}</td>
                      {/* Sans observation prise en compte, aucune jauge : une
                          note sur du vide ferait passer l'ignorance pour un
                          diagnostic. */}
                      <td>{indice.composantes === 0 ? t.indice.nonEvalue : indice.sante}</td>
                      <td>
                        {indice.composantes === 0
                          ? t.indice.nonEvalue
                          : indice.risqueEssaimage}
                      </td>
                      <td>
                        {indice.motifs.length === 0
                          ? '—'
                          : indice.motifs
                              .map((motif) => t.indice.motifs[motif as keyof typeof t.indice.motifs])
                              .join(' · ')}
                        <br />
                        <small>
                          {gabarit(t.indice.composantes, {
                            nombre: String(indice.composantes),
                          })}
                        </small>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}

      {sous === 'correlations' && (
        <>
          {/* L'avertissement précède le tableau, et non l'inverse : lu après les
              coefficients, il arriverait trop tard. */}
          <p className="z-info">{t.correlation.avertissement}</p>
          {correlations.length === 0 ? (
            <p className="z-info">{t.correlation.aucune}</p>
          ) : (
            <div className="z-table-enveloppe">
              <table className="z-table">
                <thead>
                  <tr>
                    <th>{t.correlation.indicateur}</th>
                    <th>{t.correlation.coefficient}</th>
                    <th>{t.correlation.lecture}</th>
                    <th>{t.tableau.nbMesures}</th>
                  </tr>
                </thead>
                <tbody>
                  {correlations.map((correlation) => (
                    <tr key={correlation.indicateur}>
                      <td>{t.correlation[correlation.indicateur]}</td>
                      <td className="z-nombre">
                        {correlation.coefficient === null
                          ? '—'
                          : f.nombre(correlation.coefficient, 2)}
                      </td>
                      <td>
                        {
                          t.correlation.lectures[
                            correlation.interpretation as keyof typeof t.correlation.lectures
                          ]
                        }
                      </td>
                      <td className="z-nombre">
                        {gabarit(t.correlation.echantillon, {
                          nombre: String(correlation.echantillon),
                        })}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}

      {sous === 'correlationsFlore' && (
        <>
          {/* Deux avertissements, dans l'ordre ou ils s'appliquent : la cause
              d'abord (vaut pour toute correlation), la portee ensuite (propre
              a celle-ci — dix classes testees sur le meme echantillon). */}
          <p className="z-info">{t.correlation.avertissement}</p>
          <p className="z-info">{t.correlation.avertissementFlore}</p>
          {correlationsSol.length === 0 ? (
            <p className="z-info">{t.correlation.aucuneFlore}</p>
          ) : (
            <div className="z-table-enveloppe">
              <table className="z-table">
                <thead>
                  <tr>
                    <th>{t.correlation.classe}</th>
                    <th>{t.correlation.coefficient}</th>
                    <th>{t.correlation.lecture}</th>
                    <th>{t.tableau.nbMesures}</th>
                  </tr>
                </thead>
                <tbody>
                  {correlationsSol.map((correlation) => (
                    <tr key={correlation.classe}>
                      <td>{t.environnement.classes[correlation.classe]}</td>
                      <td className="z-nombre">
                        {correlation.coefficient === null
                          ? '—'
                          : f.nombre(correlation.coefficient, 2)}
                      </td>
                      <td>
                        {
                          t.correlation.lectures[
                            correlation.interpretation as keyof typeof t.correlation.lectures
                          ]
                        }
                      </td>
                      <td className="z-nombre">
                        {gabarit(t.correlation.echantillonRuchers, {
                          nombre: String(correlation.echantillon),
                        })}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
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
