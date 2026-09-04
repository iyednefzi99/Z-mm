import { useEffect, useState, type ReactElement } from 'react';
import {
  agents,
  executerRegles,
  listerRappels,
  ruches,
  taches,
} from '../api/client';
import type {
  Agent,
  Ruche,
  Tache,
  TacheCorps,
} from '../api/types';
import { CATEGORIES_TACHE, PRIORITES_TACHE } from '../api/types';
import { useFormats, useT } from '../i18n/langue';
import { useRessource } from '../hooks';
import {
  Bouton,
  ChampDate,
  ChampSelect,
  ChampTexte,
  Colonne,
  Modale,
  Option,
  Pastille,
  Table,
} from '../ui/composants';
import { CorpsSection } from './CorpsSection';
import { gabarit } from '../i18n/console';
import type { TonPastille } from '../ui/composants';

/**
 * Ton de la pastille par priorité.
 *
 * <p>Une table plutôt qu'un calcul : la correspondance se lit, et une priorité
 * ajoutée au référentiel casse la compilation ici plutôt que de tomber
 * silencieusement sur un ton neutre.
 */
const TON_PRIORITE: Record<Tache['priorite'], TonPastille> = {
  basse: 'neutre',
  normale: 'neutre',
  haute: 'attention',
  critique: 'danger',
};

/** Liste de tâches et rappels de l'apiculteur (US-031). */
export function TachesVue(): ReactElement {
  const t = useT();
  const f = useFormats();
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
  const [priorite, setPriorite] = useState('normale');
  const [categorie, setCategorie] = useState('');
  // Résultat de la dernière proposition : « rien à proposer » est un résultat,
  // et le taire ferait croire à un bouton mort.
  const [proposition, setProposition] = useState<string | null>(null);

  const vide: Option = { valeur: '', libelle: t.champs.aucun };
  const optFaite: Option[] = [
    { valeur: 'non', libelle: t.tache.non },
    { valeur: 'oui', libelle: t.tache.oui },
  ];

  const colonnes: Colonne<Tache>[] = [
    {
      entete: t.champs.priorite,
      rendu: (x) => (
        <Pastille ton={TON_PRIORITE[x.priorite]}>{t.tache.priorites[x.priorite]}</Pastille>
      ),
    },
    {
      entete: t.tache.libelle,
      rendu: (x) => (
        <>
          {x.libelle}
          {/* Une tâche engendrée doit se justifier : sans son origine, elle
              apparaît sans qu'on sache pourquoi, et on finit par l'ignorer. */}
          {x.origine === 'regle' && (
            <>
              <br />
              <small className="z-info">
                {t.tache.engendree}
                {x.regleCode ? ` · ${gabarit(t.tache.parRegle, { code: x.regleCode })}` : ''}
              </small>
            </>
          )}
        </>
      ),
    },
    {
      entete: t.champs.categorie,
      rendu: (x) => (x.categorie == null ? '—' : t.tache.categories[x.categorie]),
    },
    { entete: t.tache.ruche, rendu: (x) => x.rucheModele ?? '—' },
    { entete: t.tache.agent, rendu: (x) => x.agentNom ?? '—' },
    { entete: t.tache.echeance, rendu: (x) => (x.echeance ? f.date(x.echeance) : t.tache.sansEcheance) },
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
    setPriorite(x?.priorite ?? 'normale');
    setCategorie(x?.categorie ?? '');
    setErreur(null);
    setOuvert(true);
  };

  /**
   * Demande au moteur de règles ce qu'il propose.
   *
   * <p>Idempotent côté serveur : deux appels dans la journée ne produisent rien
   * la seconde fois. Une réponse vide est donc un résultat normal — et il faut
   * le dire, sans quoi l'utilisateur croit que le bouton n'a pas marché.
   */
  const proposer = async () => {
    setErreur(null);
    try {
      const creees = await executerRegles();
      setProposition(
        creees.length === 0
          ? t.tache.aucuneProposition
          : gabarit(t.tache.proposees, { nombre: String(creees.length) }),
      );
      etat.recharger();
    } catch (cause) {
      setErreur(cause instanceof Error ? cause.message : t.etats.erreur);
    }
  };

  const enregistrer = async () => {
    const corps: TacheCorps = {
      libelle,
      rucheId: rucheId === '' ? null : Number(rucheId),
      agentId: agentId === '' ? null : Number(agentId),
      echeance: echeance === '' ? null : echeance,
      faite: faite === 'oui',
      priorite: priorite as Tache['priorite'],
      categorie: categorie === '' ? null : (categorie as Tache['categorie']),
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
      titre={t.onglets.taches}
      sousTitre={t.soustitres.taches}
      actions={
        <Bouton variante="secondaire" onClick={() => void proposer()}>
          {t.actions.proposerTaches}
        </Bouton>
      }
      etat={etat} onNouveau={() => ouvrir(null)}>
      {proposition && (
        <div className="z-rappels" role="status">
          {proposition}
        </div>
      )}
      {rappels.length > 0 && (
        <div className="z-rappels" role="status">
          <strong>{t.tache.rappels} :</strong>{' '}
          {rappels.map((r) => r.libelle).join(' · ')}
        </div>
      )}
      {etat.elements.length > 0 && (
        <Table colonnes={colonnes} elements={etat.elements} onModifier={ouvrir} onSupprimer={(e) => void etat.supprimer(e.id)} />
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
              <ChampSelect
                libelle={t.champs.priorite}
                valeur={priorite}
                options={PRIORITES_TACHE.map((p) => ({
                  valeur: p,
                  libelle: t.tache.priorites[p],
                }))}
                onChange={setPriorite}
              />
              <ChampSelect
                libelle={t.champs.categorie}
                valeur={categorie}
                options={[
                  vide,
                  ...CATEGORIES_TACHE.map((c) => ({
                    valeur: c,
                    libelle: t.tache.categories[c],
                  })),
                ]}
                onChange={setCategorie}
              />
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
