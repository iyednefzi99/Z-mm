import { useEffect, useState, type ReactElement } from 'react';
import {
  agents,
  approuverPlanning,
  plannings,
  refuserPlanning,
  ruches,
  tourneeAgent,
} from '../api/client';
import type { Agent, Planning, PlanningCorps, RaisonVisite, Ruche, Tournee } from '../api/types';
import { RAISONS_VISITE } from '../api/types';
import { useFormats, useT } from '../i18n/langue';
import { useRessource } from '../hooks';
import {
  Bouton,
  ChampDate,
  ChampHeure,
  ChampNombre,
  ChampSelect,
  Colonne,
  Modale,
  Option,
  Table,
} from '../ui/composants';
import { useDialogues } from '../ui/dialogues';
import { CorpsSection } from './CorpsSection';

const ouVide = (v: string): string => v;

export function PlanningsVue(): ReactElement {
  const t = useT();
  const { demander, signaler } = useDialogues();
  const f = useFormats();
  const etat = useRessource<Planning, PlanningCorps>(plannings);
  const [optRuches, setOptRuches] = useState<Option[]>([]);
  const [optAgents, setOptAgents] = useState<Option[]>([]);
  const [optSuperviseurs, setOptSuperviseurs] = useState<Option[]>([]);
  const [ouvert, setOuvert] = useState(false);
  const [edition, setEdition] = useState<Planning | null>(null);
  const [rucheId, setRucheId] = useState('');
  const [agentId, setAgentId] = useState('');
  const [superviseurId, setSuperviseurId] = useState('');
  const [datePrevue, setDatePrevue] = useState('');
  const [heurePrevue, setHeurePrevue] = useState('');
  const [dureeMin, setDureeMin] = useState('');
  const [raison, setRaison] = useState<RaisonVisite>('controle');
  const [erreur, setErreur] = useState<string | null>(null);
  const [agentTournee, setAgentTournee] = useState('');
  const [dateTournee, setDateTournee] = useState('');
  const [tournee, setTournee] = useState<Tournee | null>(null);
  const [tourneeDemandee, setTourneeDemandee] = useState(false);

  const optRaison: Option[] = RAISONS_VISITE.map((r) => ({ valeur: r, libelle: t.visite.raisons[r] }));

  const decider = async (p: Planning, approuve: boolean) => {
    try {
      if (approuve) {
        await approuverPlanning(p.id);
      } else {
        const motif = (await demander(t.visite.motifRefus)) ?? '';
        if (motif.trim() === '') return;
        await refuserPlanning(p.id, motif);
      }
      etat.recharger();
    } catch (cause) {
      await signaler(cause instanceof Error ? cause.message : t.etats.erreur);
    }
  };

  const colonnes: Colonne<Planning>[] = [
    { entete: t.champs.modele, rendu: (p) => p.rucheModele },
    { entete: t.visite.agent, rendu: (p) => p.agentNom },
    { entete: t.visite.date, rendu: (p) => f.date(p.datePrevue) },
    { entete: t.visite.statut, rendu: (p) => t.visite.statuts[p.statut] },
    {
      entete: '',
      rendu: (p) =>
        p.statut === 'propose' ? (
          <span className="z-actions-inline">
            <button type="button" className="z-lien" onClick={() => void decider(p, true)}>
              {t.actions.approuver}
            </button>
            <button type="button" className="z-lien z-lien--danger" onClick={() => void decider(p, false)}>
              {t.actions.refuser}
            </button>
          </span>
        ) : (
          p.motifRefus ?? '—'
        ),
    },
  ];

  useEffect(() => {
    void ruches.lister().then((l: Ruche[]) => setOptRuches(l.map((r) => ({ valeur: String(r.id), libelle: r.modele })))).catch(() => setOptRuches([]));
    void agents.lister().then((l: Agent[]) => {
      setOptAgents(l.map((a) => ({ valeur: String(a.id), libelle: a.nom })));
      setOptSuperviseurs([{ valeur: '', libelle: t.champs.aucun }, ...l.map((a) => ({ valeur: String(a.id), libelle: a.nom }))]);
    }).catch(() => setOptAgents([]));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [etat.elements]);

  const ouvrir = (p: Planning | null) => {
    setEdition(p);
    setRucheId(p ? String(p.rucheId) : '');
    setAgentId(p ? String(p.agentId) : '');
    setSuperviseurId(p?.superviseurId != null ? String(p.superviseurId) : '');
    setDatePrevue(p?.datePrevue ?? '');
    // Le serveur publie « 09:00:00 » ; `input type="time"` veut « 09:00 ».
    setHeurePrevue(p?.heurePrevue != null ? p.heurePrevue.slice(0, 5) : '');
    setDureeMin(p?.dureeMin != null ? String(p.dureeMin) : '');
    setRaison(p?.raison ?? 'controle');
    setErreur(null);
    setOuvert(true);
  };

  const enregistrer = async () => {
    if (rucheId === '' || agentId === '') {
      setErreur(t.etats.champsRequis);
      return;
    }
    const corps: PlanningCorps = {
      rucheId: Number(rucheId),
      agentId: Number(agentId),
      superviseurId: superviseurId === '' ? null : Number(superviseurId),
      datePrevue: ouVide(datePrevue),
      heurePrevue: heurePrevue === '' ? null : heurePrevue,
      dureeMin: dureeMin === '' ? null : Number(dureeMin),
      raison,
    };
    try {
      await (edition ? etat.mettreAJour(edition.id, corps) : etat.creer(corps));
      setOuvert(false);
    } catch (cause) {
      setErreur(cause instanceof Error ? cause.message : t.etats.erreur);
    }
  };

  /** US-047 : demande au serveur l'ordre de tournée du jour pour un agent. */
  const calculerTournee = async () => {
    if (agentTournee === '' || dateTournee === '') return;
    setTournee(null);
    setTourneeDemandee(true);
    try {
      setTournee(await tourneeAgent(Number(agentTournee), dateTournee));
    } catch (cause) {
      await signaler(cause instanceof Error ? cause.message : t.etats.erreur);
      setTourneeDemandee(false);
    }
  };

  return (
    <CorpsSection
      titre={t.onglets.plannings}
      sousTitre={t.soustitres.plannings}
      etat={etat} onNouveau={() => ouvrir(null)}>
      {etat.elements.length > 0 && (
        <Table colonnes={colonnes} elements={etat.elements} onModifier={ouvrir} onSupprimer={(e) => void etat.supprimer(e.id)} />
      )}

      <section className="z-encart">
        <h2 className="z-encart__titre">{t.tournee.titre}</h2>
        <div className="z-form__grille">
          <ChampSelect
            libelle={t.visite.agent}
            valeur={agentTournee}
            options={optAgents}
            onChange={setAgentTournee}
          />
          <ChampDate libelle={t.visite.date} valeur={dateTournee} onChange={setDateTournee} />
          <div className="z-champ z-champ--aligne-bas">
            <Bouton
              variante="secondaire"
              onClick={() => void calculerTournee()}
              disabled={agentTournee === '' || dateTournee === ''}
            >
              {t.tournee.calculer}
            </Bouton>
          </div>
        </div>
        {tourneeDemandee && tournee !== null && tournee.etapes.length === 0 && (
          <p className="z-info">{t.tournee.aucune}</p>
        )}
        {tournee !== null && tournee.etapes.length > 0 && (
          <>
            <ol className="z-liste-simple">
              {tournee.etapes.map((e) => (
                <li key={e.siteId}>
                  <strong>{e.siteNom}</strong> — {t.tournee.visites} : {e.nombreVisites}
                  {e.ordre > 1 && (
                    <>
                      {' · '}
                      {t.tournee.depuisPrecedente} : {f.distance(e.distanceDepuisPrecedenteMetres)}
                    </>
                  )}
                </li>
              ))}
            </ol>
            <p className="z-info">
              {t.tournee.total} : {f.distance(tournee.distanceTotaleMetres)} —{' '}
              {t.tournee.avertissement}
            </p>
          </>
        )}
      </section>

      {ouvert && (
        <Modale titre={t.onglets.plannings} onFermer={() => setOuvert(false)}>
          <form
            className="z-form"
            onSubmit={(e) => {
              e.preventDefault();
              void enregistrer();
            }}
          >
            <ChampSelect libelle={t.champs.modele} valeur={rucheId} options={optRuches} onChange={setRucheId} requis />
            <div className="z-form__grille">
              <ChampSelect libelle={t.visite.agent} valeur={agentId} options={optAgents} onChange={setAgentId} requis />
              <ChampSelect libelle={t.visite.superviseur} valeur={superviseurId} options={optSuperviseurs} onChange={setSuperviseurId} />
            </div>
            <div className="z-form__grille">
              <ChampDate libelle={t.visite.date} valeur={datePrevue} onChange={setDatePrevue} requis />
              <ChampHeure libelle={t.visite.heure} valeur={heurePrevue} onChange={setHeurePrevue} />
              <ChampNombre libelle={t.visite.duree} valeur={dureeMin} onChange={setDureeMin} pas="1" />
            </div>
            <ChampSelect libelle={t.visite.raison} valeur={raison} options={optRaison} onChange={(v) => setRaison(v as RaisonVisite)} />
            {erreur && <p className="z-form__erreur">{erreur}</p>}
            <div className="z-form__actions">
              <Bouton variante="fantome" onClick={() => setOuvert(false)}>
                {t.actions.annuler}
              </Bouton>
              <Bouton variante="primaire" type="submit">
                {t.actions.enregistrer}
              </Bouton>
            </div>
          </form>
        </Modale>
      )}
    </CorpsSection>
  );
}
