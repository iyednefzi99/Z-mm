import { useEffect, useState, type ReactElement } from 'react';
import { agents, approuverPlanning, plannings, refuserPlanning, ruches } from '../api/client';
import type { Agent, Planning, PlanningCorps, RaisonVisite, Ruche } from '../api/types';
import { RAISONS_VISITE } from '../api/types';
import { gabarit } from '../i18n/console';
import { useT } from '../i18n/langue';
import { useRessource } from '../hooks';
import {
  Bouton,
  ChampDate,
  ChampSelect,
  Colonne,
  Modale,
  Option,
  Table,
} from '../ui/composants';
import { CorpsSection } from './CorpsSection';

const ouVide = (v: string): string => v;

export function PlanningsVue(): ReactElement {
  const t = useT();
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
  const [raison, setRaison] = useState<RaisonVisite>('controle');
  const [erreur, setErreur] = useState<string | null>(null);

  const optRaison: Option[] = RAISONS_VISITE.map((r) => ({ valeur: r, libelle: t.visite.raisons[r] }));

  const decider = async (p: Planning, approuve: boolean) => {
    try {
      if (approuve) {
        await approuverPlanning(p.id);
      } else {
        const motif = window.prompt(t.visite.motifRefus) ?? '';
        if (motif.trim() === '') return;
        await refuserPlanning(p.id, motif);
      }
      etat.recharger();
    } catch (cause) {
      window.alert(cause instanceof Error ? cause.message : t.etats.erreur);
    }
  };

  const colonnes: Colonne<Planning>[] = [
    { entete: t.champs.modele, rendu: (p) => p.rucheModele },
    { entete: t.visite.agent, rendu: (p) => p.agentNom },
    { entete: t.visite.date, rendu: (p) => p.datePrevue },
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
    setRaison(p?.raison ?? 'controle');
    setErreur(null);
    setOuvert(true);
  };

  const enregistrer = async () => {
    if (rucheId === '' || agentId === '') {
      setErreur('?');
      return;
    }
    const corps: PlanningCorps = {
      rucheId: Number(rucheId),
      agentId: Number(agentId),
      superviseurId: superviseurId === '' ? null : Number(superviseurId),
      datePrevue: ouVide(datePrevue),
      heurePrevue: null,
      dureeMin: null,
      raison,
    };
    try {
      await (edition ? etat.mettreAJour(edition.id, corps) : etat.creer(corps));
      setOuvert(false);
    } catch (cause) {
      setErreur(cause instanceof Error ? cause.message : t.etats.erreur);
    }
  };

  const supprimer = (p: Planning) => {
    if (window.confirm(gabarit(t.etats.confirmerSuppression, { nom: p.rucheModele }))) {
      void etat.supprimer(p.id);
    }
  };

  return (
    <CorpsSection titre={t.onglets.plannings} etat={etat} onNouveau={() => ouvrir(null)}>
      {etat.elements.length > 0 && (
        <Table colonnes={colonnes} elements={etat.elements} onModifier={ouvrir} onSupprimer={supprimer} />
      )}
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
              <ChampSelect libelle={t.visite.raison} valeur={raison} options={optRaison} onChange={(v) => setRaison(v as RaisonVisite)} />
            </div>
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
