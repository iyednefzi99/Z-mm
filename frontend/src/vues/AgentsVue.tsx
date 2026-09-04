import { useEffect, useState, type ReactElement } from 'react';
import { agents, fermes } from '../api/client';
import type { Agent, AgentCorps, Ferme, RoleAgent } from '../api/types';
import { ROLES_AGENT } from '../api/types';
import { useT } from '../i18n/langue';
import { useRessource, useRoles } from '../hooks';
import { peutEcrire } from '../routage/routes';
import { Bouton, ChampSelect, ChampTexte, Colonne, Modale, Option, Table } from '../ui/composants';
import { CorpsSection } from './CorpsSection';

export function AgentsVue(): ReactElement {
  const t = useT();
  const etat = useRessource<Agent, AgentCorps>(agents);
  const ecriture = peutEcrire('agents', useRoles());
  const [optionsFerme, setOptionsFerme] = useState<Option[]>([]);
  const [edition, setEdition] = useState<Agent | null>(null);
  const [ouvert, setOuvert] = useState(false);
  const [nom, setNom] = useState('');
  const [role, setRole] = useState<RoleAgent>('apiculteur');
  const [fermeId, setFermeId] = useState('');
  const [email, setEmail] = useState('');
  // SPRINT-25 : la préférence est PAR AGENT. Avant, couper les notifications se
  // faisait pour l'exploitation entière — celui qui ne voulait pas être réveillé
  // par une alerte de poids la coupait pour ses collègues aussi.
  const [notifications, setNotifications] = useState(true);
  const [erreur, setErreur] = useState<string | null>(null);

  const optionsRole: Option[] = ROLES_AGENT.map((r) => ({ valeur: r, libelle: t.roles[r] }));

  const colonnes: Colonne<Agent>[] = [
    { entete: t.champs.nom, rendu: (a) => a.nom },
    { entete: t.champs.role, rendu: (a) => t.roles[a.role] },
    { entete: t.champs.ferme, rendu: (a) => a.fermeNom ?? '—' },
    { entete: t.champs.email, rendu: (a) => a.email ?? '—' },
  ];

  useEffect(() => {
    fermes
      .lister()
      .then((liste: Ferme[]) =>
        setOptionsFerme([
          { valeur: '', libelle: t.champs.aucun },
          ...liste.map((f) => ({ valeur: String(f.id), libelle: f.nom })),
        ]),
      )
      .catch(() => setOptionsFerme([{ valeur: '', libelle: t.champs.aucun }]));
    // t.champs.aucun est stable par langue ; on recharge sur mutations de la liste.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [etat.elements]);

  const ouvrir = (a: Agent | null) => {
    setEdition(a);
    setNom(a?.nom ?? '');
    setRole(a?.role ?? 'apiculteur');
    setFermeId(a?.fermeId != null ? String(a.fermeId) : '');
    setEmail(a?.email ?? '');
    setNotifications(a?.notificationsEmail ?? true);
    setErreur(null);
    setOuvert(true);
  };

  const enregistrer = async () => {
    const corps: AgentCorps = {
      nom,
      role,
      fermeId: fermeId === '' ? null : Number(fermeId),
      email: email.trim() === '' ? null : email.trim(),
      notificationsEmail: notifications,
    };
    try {
      await (edition ? etat.mettreAJour(edition.id, corps) : etat.creer(corps));
      setOuvert(false);
    } catch (cause) {
      setErreur(cause instanceof Error ? cause.message : t.etats.erreur);
    }
  };

  return (
    <CorpsSection
      titre={t.onglets.agents}
      sousTitre={t.soustitres.agents}
      etat={etat} onNouveau={() => ouvrir(null)} ecriture={ecriture}>
      {etat.elements.length > 0 && (
        <Table colonnes={colonnes} elements={etat.elements} onModifier={ouvrir} onSupprimer={(e) => void etat.supprimer(e.id)} ecriture={ecriture} />
      )}
      {ouvert && (
        <Modale titre={t.onglets.agents} onFermer={() => setOuvert(false)}>
          <form
            className="z-form"
            onSubmit={(e) => {
              e.preventDefault();
              void enregistrer();
            }}
          >
            <ChampTexte libelle={t.champs.nom} valeur={nom} onChange={setNom} requis />
            <ChampSelect
              libelle={t.champs.role}
              valeur={role}
              options={optionsRole}
              onChange={(v) => setRole(v as RoleAgent)}
              requis
            />
            <ChampSelect
              libelle={t.champs.ferme}
              valeur={fermeId}
              options={optionsFerme}
              onChange={setFermeId}
            />
            <ChampTexte libelle={t.champs.email} valeur={email} onChange={setEmail} />
            {/* Coché ET une adresse renseignée : les deux conditions sont
                distinctes et le restent. « Pas d'adresse » est un défaut de
                paramétrage, « ne veut pas » est une décision — les confondre
                ferait passer pour un oubli un réglage que l'agent a posé. */}
            <label className="z-champ__case">
              <input
                type="checkbox"
                checked={notifications}
                onChange={(e) => setNotifications(e.target.checked)}
              />
              {t.champs.notificationsEmail}
            </label>
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
