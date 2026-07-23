import { useEffect, useState, type ReactElement } from 'react';
import { agents, fermes } from '../api/client';
import type { Agent, AgentCorps, Ferme, RoleAgent } from '../api/types';
import { ROLES_AGENT } from '../api/types';
import { useRessource } from '../hooks';
import { Bouton, ChampSelect, ChampTexte, Colonne, Modale, Option, Table } from '../ui/composants';
import { gabarit, L } from '../ui/libelles';
import { CorpsSection } from './CorpsSection';

const optionsRole: Option[] = ROLES_AGENT.map((role) => ({ valeur: role, libelle: L.roles[role] }));

const colonnes: Colonne<Agent>[] = [
  { entete: L.champs.nom, rendu: (a) => a.nom },
  { entete: L.champs.role, rendu: (a) => L.roles[a.role] },
  { entete: L.champs.ferme, rendu: (a) => a.fermeNom ?? '—' },
];

export function AgentsVue(): ReactElement {
  const etat = useRessource<Agent, AgentCorps>(agents);
  const [optionsFerme, setOptionsFerme] = useState<Option[]>([]);
  const [edition, setEdition] = useState<Agent | null>(null);
  const [ouvert, setOuvert] = useState(false);
  const [nom, setNom] = useState('');
  const [role, setRole] = useState<RoleAgent>('apiculteur');
  const [fermeId, setFermeId] = useState('');
  const [erreur, setErreur] = useState<string | null>(null);

  useEffect(() => {
    fermes
      .lister()
      .then((liste: Ferme[]) =>
        setOptionsFerme([
          { valeur: '', libelle: L.champs.aucun },
          ...liste.map((f) => ({ valeur: String(f.id), libelle: f.nom })),
        ]),
      )
      .catch(() => setOptionsFerme([{ valeur: '', libelle: L.champs.aucun }]));
  }, [etat.elements]);

  const ouvrir = (a: Agent | null) => {
    setEdition(a);
    setNom(a?.nom ?? '');
    setRole(a?.role ?? 'apiculteur');
    setFermeId(a?.fermeId != null ? String(a.fermeId) : '');
    setErreur(null);
    setOuvert(true);
  };

  const enregistrer = async () => {
    const corps: AgentCorps = {
      nom,
      role,
      fermeId: fermeId === '' ? null : Number(fermeId),
    };
    try {
      await (edition ? etat.mettreAJour(edition.id, corps) : etat.creer(corps));
      setOuvert(false);
    } catch (cause) {
      setErreur(cause instanceof Error ? cause.message : L.etats.erreur);
    }
  };

  const supprimer = (a: Agent) => {
    if (window.confirm(gabarit(L.etats.confirmerSuppression, { nom: a.nom }))) {
      void etat.supprimer(a.id);
    }
  };

  return (
    <CorpsSection titre={L.onglets.agents} etat={etat} onNouveau={() => ouvrir(null)}>
      {etat.elements.length > 0 && (
        <Table colonnes={colonnes} elements={etat.elements} onModifier={ouvrir} onSupprimer={supprimer} />
      )}
      {ouvert && (
        <Modale titre={L.onglets.agents} onFermer={() => setOuvert(false)}>
          <form
            className="z-form"
            onSubmit={(e) => {
              e.preventDefault();
              void enregistrer();
            }}
          >
            <ChampTexte libelle={L.champs.nom} valeur={nom} onChange={setNom} requis />
            <ChampSelect
              libelle={L.champs.role}
              valeur={role}
              options={optionsRole}
              onChange={(v) => setRole(v as RoleAgent)}
              requis
            />
            <ChampSelect
              libelle={L.champs.ferme}
              valeur={fermeId}
              options={optionsFerme}
              onChange={setFermeId}
            />
            {erreur && <p className="z-form__erreur">{erreur}</p>}
            <div className="z-form__actions">
              <Bouton variante="fantome" onClick={() => setOuvert(false)}>
                {L.actions.annuler}
              </Bouton>
              <Bouton variante="primaire" type="submit">
                {L.actions.enregistrer}
              </Bouton>
            </div>
          </form>
        </Modale>
      )}
    </CorpsSection>
  );
}
