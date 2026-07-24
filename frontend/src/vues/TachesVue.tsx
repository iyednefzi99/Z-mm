import { useEffect, useState, type ReactElement } from 'react';
import { agents, listerRappels, ruches, taches } from '../api/client';
import type { Agent, Ruche, Tache, TacheCorps } from '../api/types';
import { gabarit } from '../i18n/console';
import { useT } from '../i18n/langue';
import { useRessource } from '../hooks';
import {
  Bouton,
  ChampDate,
  ChampSelect,
  ChampTexte,
  Colonne,
  Modale,
  Option,
  Table,
} from '../ui/composants';
import { CorpsSection } from './CorpsSection';

/** Liste de tâches et rappels de l'apiculteur (US-031). */
export function TachesVue(): ReactElement {
  const t = useT();
  const etat = useRessource<Tache, TacheCorps>(taches);
  const [optRuches, setOptRuches] = useState<Option[]>([]);
  const [optAgents, setOptAgents] = useState<Option[]>([]);
  const [rappels, setRappels] = useState<Tache[]>([]);
  const [ouvert, setOuvert] = useState(false);
  const [edition, setEdition] = useState<Tache | null>(null);
  const [libelle, setLibelle] = useState('');
  const [rucheId, setRucheId] = useState('');
  const [agentId, setAgentId] = useState('');
  const [echeance, setEcheance] = useState('');
  const [faite, setFaite] = useState('non');
  const [erreur, setErreur] = useState<string | null>(null);

  const vide: Option = { valeur: '', libelle: t.champs.aucun };
  const optFaite: Option[] = [
    { valeur: 'non', libelle: t.tache.non },
    { valeur: 'oui', libelle: t.tache.oui },
  ];

  const colonnes: Colonne<Tache>[] = [
    { entete: t.tache.libelle, rendu: (x) => x.libelle },
    { entete: t.tache.ruche, rendu: (x) => x.rucheModele ?? '—' },
    { entete: t.tache.agent, rendu: (x) => x.agentNom ?? '—' },
    { entete: t.tache.echeance, rendu: (x) => x.echeance ?? t.tache.sansEcheance },
    { entete: t.tache.faite, rendu: (x) => (x.faite ? t.tache.oui : t.tache.non) },
  ];

  useEffect(() => {
    void ruches.lister().then((l: Ruche[]) => setOptRuches([vide, ...l.map((r) => ({ valeur: String(r.id), libelle: r.modele }))])).catch(() => setOptRuches([vide]));
    void agents.lister().then((l: Agent[]) => setOptAgents([vide, ...l.map((a) => ({ valeur: String(a.id), libelle: a.nom }))])).catch(() => setOptAgents([vide]));
    void listerRappels().then(setRappels).catch(() => setRappels([]));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [etat.elements]);

  const ouvrir = (x: Tache | null) => {
    setEdition(x);
    setLibelle(x?.libelle ?? '');
    setRucheId(x?.rucheId != null ? String(x.rucheId) : '');
    setAgentId(x?.agentId != null ? String(x.agentId) : '');
    setEcheance(x?.echeance ?? '');
    setFaite(x?.faite ? 'oui' : 'non');
    setErreur(null);
    setOuvert(true);
  };

  const enregistrer = async () => {
    const corps: TacheCorps = {
      libelle,
      rucheId: rucheId === '' ? null : Number(rucheId),
      agentId: agentId === '' ? null : Number(agentId),
      echeance: echeance === '' ? null : echeance,
      faite: faite === 'oui',
    };
    try {
      await (edition ? etat.mettreAJour(edition.id, corps) : etat.creer(corps));
      setOuvert(false);
    } catch (cause) {
      setErreur(cause instanceof Error ? cause.message : t.etats.erreur);
    }
  };

  const supprimer = (x: Tache) => {
    if (window.confirm(gabarit(t.etats.confirmerSuppression, { nom: x.libelle }))) {
      void etat.supprimer(x.id);
    }
  };

  return (
    <CorpsSection titre={t.onglets.taches} etat={etat} onNouveau={() => ouvrir(null)}>
      {rappels.length > 0 && (
        <div className="z-rappels" role="status">
          <strong>{t.tache.rappels} :</strong>{' '}
          {rappels.map((r) => r.libelle).join(' · ')}
        </div>
      )}
      {etat.elements.length > 0 && (
        <Table colonnes={colonnes} elements={etat.elements} onModifier={ouvrir} onSupprimer={supprimer} />
      )}
      {ouvert && (
        <Modale titre={t.onglets.taches} onFermer={() => setOuvert(false)}>
          <form
            className="z-form"
            onSubmit={(e) => {
              e.preventDefault();
              void enregistrer();
            }}
          >
            <ChampTexte libelle={t.tache.libelle} valeur={libelle} onChange={setLibelle} requis />
            <div className="z-form__grille">
              <ChampSelect libelle={t.tache.ruche} valeur={rucheId} options={optRuches} onChange={setRucheId} />
              <ChampSelect libelle={t.tache.agent} valeur={agentId} options={optAgents} onChange={setAgentId} />
            </div>
            <div className="z-form__grille">
              <ChampDate libelle={t.tache.echeance} valeur={echeance} onChange={setEcheance} />
              <ChampSelect libelle={t.tache.faite} valeur={faite} options={optFaite} onChange={setFaite} />
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
