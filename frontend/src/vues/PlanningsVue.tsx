import { Suspense, lazy, useEffect, useState, type ReactElement } from 'react';
import {
  agents,
  approuverPlanning,
  chargerFeuilleChargement,
  plannings,
  refuserPlanning,
  creerAbonnement,
  listerAbonnements,
  revoquerAbonnement,
  ruches,
  telechargerAgendaIcs,
  tourneeAgent,
} from '../api/client';
import type {
  Abonnement,
  Agent,
  EtapeTournee,
  FeuilleChargement,
  Planning,
  PlanningCorps,
  RaisonVisite,
  Ruche,
  Tournee,
} from '../api/types';
import { RAISONS_VISITE } from '../api/types';
import { gabarit } from '../i18n/console';
import { useFormats, useT } from '../i18n/langue';
import { useRessource } from '../hooks';
import {
  Bouton,
  ChampDate,
  ChampHeure,
  ChampNombre,
  ChampSelect,
  ChampTexte,
  Colonne,
  Modale,
  Option,
  Pastille,
  Table,
} from '../ui/composants';
import { useDialogues } from '../ui/dialogues';
import type { ProprietesListeReordonnable } from '../ui/reordonnable';
import { afficher } from './tournee';

/**
 * Chargé paresseusement : ce composant tire Motion, que `Reorder` embarque en
 * entier. L'isoler dans son propre morceau garde la bibliothèque hors du précache
 * de la PWA — même traitement que le fond cartographique (`vite.config.ts`).
 */
const ListeReordonnable = lazy(async () => {
  const module = await import('../ui/reordonnable');
  // `lazy` efface le paramètre générique du composant. On le refixe ici, au seul
  // endroit qui sache de quoi la liste est faite.
  return {
    default: module.default as (
      proprietes: ProprietesListeReordonnable<EtapeTournee>,
    ) => ReactElement,
  };
});
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
  // Ordre choisi à l'écran, ou `null` tant que la proposition du serveur n'a pas
  // été touchée. Rien n'est persisté : aucune route ne sait enregistrer un ordre.
  const [ordreLocal, setOrdreLocal] = useState<EtapeTournee[] | null>(null);
  const [abonnements, setAbonnements] = useState<Abonnement[]>([]);
  // URL rendue par la création, montrée UNE fois : le serveur ne garde que
  // l'empreinte du jeton, et ne saura pas la reconstruire.
  const [urlAbonnement, setUrlAbonnement] = useState<string | null>(null);
  const [libelleAbonnement, setLibelleAbonnement] = useState('');
  const [dureeAbonnement, setDureeAbonnement] = useState('180');
  const [tourneeDemandee, setTourneeDemandee] = useState(false);

  // `Reorder` suit ses éléments par identité : la liste qu'on lui donne doit être
  // celle qu'il rendra dans `onReorder`. Les distances re-simulées vivent donc à
  // côté, dans `vueTournee`, au lieu d'être fusionnées dans de nouveaux objets.
  const ordreAffiche: EtapeTournee[] = ordreLocal ?? tournee?.etapes ?? [];
  const vueTournee = tournee === null ? null : afficher(tournee, ordreLocal);

  const optRaison: Option[] = RAISONS_VISITE.map((r) => ({
    valeur: r,
    libelle: t.visite.raisons[r],
  }));

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
            <button
              type="button"
              className="z-lien z-lien--danger"
              onClick={() => void decider(p, false)}
            >
              {t.actions.refuser}
            </button>
          </span>
        ) : (
          (p.motifRefus ?? '—')
        ),
    },
  ];

  useEffect(() => {
    void ruches
      .lister()
      .then((l: Ruche[]) =>
        setOptRuches(l.map((r) => ({ valeur: String(r.id), libelle: r.modele }))),
      )
      .catch(() => setOptRuches([]));
    void agents
      .lister()
      .then((l: Agent[]) => {
        setOptAgents(l.map((a) => ({ valeur: String(a.id), libelle: a.nom })));
        setOptSuperviseurs([
          { valeur: '', libelle: t.champs.aucun },
          ...l.map((a) => ({ valeur: String(a.id), libelle: a.nom })),
        ]);
      })
      .catch(() => setOptAgents([]));
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
    setOrdreLocal(null);
    setTourneeDemandee(true);
    try {
      setTournee(await tourneeAgent(Number(agentTournee), dateTournee));
    } catch (cause) {
      await signaler(cause instanceof Error ? cause.message : t.etats.erreur);
      setTourneeDemandee(false);
    }
  };

  const chargerAbonnements = async (agentId: string) => {
    if (agentId === '') {
      setAbonnements([]);
      return;
    }
    try {
      setAbonnements(await listerAbonnements(Number(agentId)));
    } catch {
      setAbonnements([]);
    }
  };

  useEffect(() => {
    void chargerAbonnements(agentTournee);
    setUrlAbonnement(null);
  }, [agentTournee]);

  const emettreAbonnement = async () => {
    try {
      const cree = await creerAbonnement({
        agentId: Number(agentTournee),
        libelle: libelleAbonnement,
        dureeJours: Number(dureeAbonnement),
      });
      setUrlAbonnement(cree.url);
      setLibelleAbonnement('');
      await chargerAbonnements(agentTournee);
    } catch (cause) {
      setUrlAbonnement(null);
      throw cause;
    }
  };

  return (
    <CorpsSection
      titre={t.onglets.plannings}
      sousTitre={t.soustitres.plannings}
      actions={
        /* Export iCalendar (SPRINT-21) : un instantané, à importer une fois.
           Pour un agenda qui se met à jour tout seul, c'est l'abonnement, plus
           bas — et celui-là suppose un jeton, donc des garanties. */
        <Bouton variante="secondaire" onClick={() => void telechargerAgendaIcs()}>
          {t.actions.exporterIcs}
        </Bouton>
      }
      etat={etat}
      onNouveau={() => ouvrir(null)}
    >
      {etat.elements.length > 0 && (
        <Table
          colonnes={colonnes}
          elements={etat.elements}
          onModifier={ouvrir}
          onSupprimer={(e) => void etat.supprimer(e.id)}
        />
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
        {vueTournee !== null && vueTournee.etapes.length > 0 && (
          <>
            <Suspense fallback={<p className="z-info">{t.etats.chargement}</p>}>
              <ListeReordonnable
                elements={ordreAffiche}
                cle={(e) => e.siteId}
                rendu={(e, rang) => (
                  <>
                    <strong>{e.siteNom}</strong> — {t.tournee.visites} : {e.nombreVisites}
                    {rang > 1 && (
                      <>
                        {' · '}
                        {t.tournee.depuisPrecedente} :{' '}
                        {f.distance(vueTournee.etapes[rang - 1].distanceDepuisPrecedenteMetres)}
                      </>
                    )}
                  </>
                )}
                onReordonner={setOrdreLocal}
                libelle={t.tournee.titre}
                libelleElement={(e) => e.siteNom}
                libelles={{
                  saisir: t.tournee.saisir,
                  monter: t.tournee.monter,
                  descendre: t.tournee.descendre,
                }}
                annoncer={(e, rang, total) =>
                  gabarit(t.tournee.deplace, {
                    site: e.siteNom,
                    rang: String(rang),
                    total: String(total),
                  })
                }
              />
            </Suspense>
            <p className="z-info">
              {t.tournee.total} : {f.distance(vueTournee.distanceTotaleMetres)} —{' '}
              {t.tournee.avertissement}
            </p>
            {vueTournee.estimee && (
              <p className="z-info">
                {t.tournee.simulee}{' '}
                <button type="button" className="z-lien" onClick={() => setOrdreLocal(null)}>
                  {t.tournee.retablir}
                </button>
              </p>
            )}
          </>
        )}

        <FeuilleDeChargement agentId={agentTournee} date={dateTournee} />
      </section>

      <section className="z-encart">
        <h2 className="z-encart__titre">{t.terrain.abonnements.titre}</h2>
        <p className="z-info">{t.terrain.abonnements.aide}</p>

        {agentTournee === '' ? (
          <p className="z-info">{t.visite.agent}</p>
        ) : (
          <>
            {abonnements.length === 0 && <p className="z-info">{t.terrain.abonnements.aucun}</p>}
            {abonnements.length > 0 && (
              <ul className="z-liste-simple">
                {abonnements.map((abonnement) => (
                  <li key={abonnement.id}>
                    <Pastille ton={abonnement.actif ? 'succes' : 'neutre'}>
                      {abonnement.actif
                        ? t.terrain.abonnements.actif
                        : t.terrain.abonnements.inactif}
                    </Pastille>{' '}
                    <strong>{abonnement.libelle}</strong> · {t.terrain.abonnements.expireLe}{' '}
                    {f.date(abonnement.expireLe)} ·{' '}
                    {/* Rendre l'usage visible EST une mesure de sécurité : un
                        abonnement qu'on croyait oublié et qui sert toutes les
                        heures se remarque ici, et se révoque. */}
                    {abonnement.derniereUtilisation === null
                      ? t.terrain.abonnements.jamaisUtilise
                      : `${t.terrain.abonnements.derniereUtilisation} ${f.date(
                          abonnement.derniereUtilisation,
                        )}`}
                    {abonnement.actif && (
                      <>
                        {' '}
                        <button
                          type="button"
                          className="z-lien z-lien--danger"
                          onClick={() => {
                            void revoquerAbonnement(abonnement.id).then(() =>
                              chargerAbonnements(agentTournee),
                            );
                          }}
                        >
                          {t.actions.revoquer}
                        </button>
                      </>
                    )}
                  </li>
                ))}
              </ul>
            )}

            {urlAbonnement !== null && (
              <div className="z-erreur" role="status">
                <span>
                  <strong>{t.terrain.abonnements.urlUnique}</strong>
                  <br />
                  <code>{urlAbonnement}</code>
                </span>
              </div>
            )}

            <div className="z-form__grille">
              <ChampTexte
                libelle={t.champs.libelle}
                valeur={libelleAbonnement}
                onChange={setLibelleAbonnement}
              />
              <ChampNombre
                libelle={t.champs.duree}
                valeur={dureeAbonnement}
                onChange={setDureeAbonnement}
              />
              <div className="z-champ z-champ--aligne-bas">
                <Bouton
                  variante="secondaire"
                  onClick={() => void emettreAbonnement()}
                  disabled={libelleAbonnement.trim() === ''}
                >
                  {t.actions.creerAbonnement}
                </Bouton>
              </div>
            </div>
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
            <ChampSelect
              libelle={t.champs.modele}
              valeur={rucheId}
              options={optRuches}
              onChange={setRucheId}
              requis
            />
            <div className="z-form__grille">
              <ChampSelect
                libelle={t.visite.agent}
                valeur={agentId}
                options={optAgents}
                onChange={setAgentId}
                requis
              />
              <ChampSelect
                libelle={t.visite.superviseur}
                valeur={superviseurId}
                options={optSuperviseurs}
                onChange={setSuperviseurId}
              />
            </div>
            <div className="z-form__grille">
              <ChampDate
                libelle={t.visite.date}
                valeur={datePrevue}
                onChange={setDatePrevue}
                requis
              />
              <ChampHeure libelle={t.visite.heure} valeur={heurePrevue} onChange={setHeurePrevue} />
              <ChampNombre
                libelle={t.visite.duree}
                valeur={dureeMin}
                onChange={setDureeMin}
                pas="1"
              />
            </div>
            <ChampSelect
              libelle={t.visite.raison}
              valeur={raison}
              options={optRaison}
              onChange={(v) => setRaison(v as RaisonVisite)}
            />
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

/**
 * Feuille de chargement d'une tournee (SPRINT-33).
 *
 * <p>Elle repond a la question qu'on se pose sur le pas de la porte : qu'est-ce
 * que je charge dans le vehicule ? Deux lectures, et il faut les deux —
 * consolidee pour charger, par etape pour savoir ou deposer.
 *
 * <p><strong>Le manque est NOMME, jamais corrige.</strong> Un consommable
 * insuffisant s'affiche comme tel ; decider quelle ruche on saute est une
 * decision d'exploitation, et un ecran qui repartirait automatiquement le stock
 * disponible prendrait cette decision a la place de l'apiculteur.
 */
function FeuilleDeChargement({
  agentId,
  date,
}: {
  agentId: string;
  date: string;
}): ReactElement | null {
  const t = useT();
  const f = useFormats();
  const c = t.chargement;
  const [feuille, setFeuille] = useState<FeuilleChargement | null>(null);

  async function etablir(): Promise<void> {
    if (agentId === '' || date === '') {
      return;
    }
    setFeuille(await chargerFeuilleChargement(Number(agentId), date));
  }

  const besoin = (b: { libelle: string; requis: number | null; unite: string }) =>
    `${b.libelle} : ${b.requis === null ? '—' : f.nombre(b.requis)} ${b.unite}`;

  return (
    <>
      <h3 className="z-champ__libelle">{c.titre}</h3>
      <p className="z-info">{c.aide}</p>
      <Bouton
        variante="fantome"
        disabled={agentId === '' || date === ''}
        onClick={() => void etablir()}
      >
        {c.charger}
      </Bouton>

      {feuille !== null && (
        <>
          <p className="z-info">
            {c.agent} : {feuille.agentNom} · {c.date} : {f.date(feuille.date)} · {c.site} :{' '}
            {feuille.nombreSites}
          </p>

          {feuille.besoins.length === 0 ? (
            <p className="z-info">{c.aucuneEtape}</p>
          ) : (
            <div className="z-table-enveloppe">
              <table className="z-table">
                <thead>
                  <tr>
                    <th>{c.consommable}</th>
                    <th>{c.requis}</th>
                    <th>{c.stock}</th>
                  </tr>
                </thead>
                <tbody>
                  {feuille.besoins.map((b) => (
                    <tr key={b.consommableId}>
                      <td>{b.libelle}</td>
                      <td className="z-nombre">
                        {b.requis === null ? '—' : `${f.nombre(b.requis)} ${b.unite}`}
                      </td>
                      <td className="z-nombre">
                        {b.suffisant ? (
                          `${f.nombre(b.enStock)} ${b.unite}`
                        ) : (
                          <Pastille ton="danger">
                            {`${c.manquant} · ${f.nombre(b.enStock)} ${b.unite}`}
                          </Pastille>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {feuille.manquants > 0 && (
            <p className="z-erreur" role="alert">
              {gabarit(c.manquants, { nombre: String(feuille.manquants) })}
            </p>
          )}
          {feuille.manquants === 0 && feuille.besoins.length > 0 && (
            <p className="z-info">{c.aucunManquant}</p>
          )}

          {feuille.etapes.length > 0 && (
            <>
              <h4 className="z-champ__libelle">{c.etapes}</h4>
              <ul className="z-liste-simple">
                {feuille.etapes.map((etape) => (
                  <li key={etape.siteId}>
                    <strong>
                      {etape.ordre}. {etape.siteNom}
                    </strong>{' '}
                    — {c.visites} : {etape.nombreVisites}
                    {etape.taches.length > 0 && (
                      <>
                        {' · '}
                        {c.taches} : {etape.taches.join(', ')}
                      </>
                    )}
                    {etape.besoins.length > 0 && (
                      <>
                        <br />
                        <small>
                          {c.besoins} : {etape.besoins.map(besoin).join(' · ')}
                        </small>
                      </>
                    )}
                  </li>
                ))}
              </ul>
            </>
          )}
        </>
      )}
    </>
  );
}
