import { useEffect, useState, type ReactElement } from 'react';
import {
  agents,
  annulerTransport,
  comparerSites,
  demenagerSite,
  emplacementsSite,
  emporterRucher,
  fermes,
  listerTransports,
  planifierTransport,
  realiserTransport,
  supprimerTransport,
  sites,
  voisinsSite,
} from '../api/client';
import type {
  Agent,
  ComparaisonSite,
  Emplacement,
  Ferme,
  PrioriteTerrain,
  RessourceFloraleCorps,
  RessourceFloraleType,
  Site,
  SiteCorps,
  Transport,
  VoisinSite,
} from '../api/types';
import {
  COUVERTURES_RESEAU,
  EXPOSITIONS,
  MOTIFS_EMPLACEMENT,
  PRIORITES_TERRAIN,
  RESSOURCES_FLORALES,
  TYPES_SITE,
} from '../api/types';
import { ranger } from '../offline/emport';
import { gabarit } from '../i18n/console';
import type { Traductions } from '../i18n/console';
import { useFormats, useT } from '../i18n/langue';
import { useRessource, useRoles } from '../hooks';
import { peutEcrire } from '../routage/routes';
import {
  Bouton,
  ChampDate,
  ChampNombre,
  ChampSelect,
  ChampTexte,
  ChampZone,
  Colonne,
  Modale,
  Option,
  Pastille,
  Table,
} from '../ui/composants';
import { PanneauEnvironnement } from '../environnement/PanneauEnvironnement';
import { useDialogues } from '../ui/dialogues';
import { CorpsSection } from './CorpsSection';

const ouNull = (valeur: string): string | null => (valeur.trim() === '' ? null : valeur);

/** Les douze mois, précédés du choix vide : une floraison peut n'être pas connue. */
const optionsMois = (t: Traductions): Option[] => [
  { valeur: '', libelle: t.champs.aucun },
  ...Array.from({ length: 12 }, (_, i) => ({
    valeur: String(i + 1),
    libelle: t.referentielSite.mois[String(i + 1) as keyof typeof t.referentielSite.mois],
  })),
];

export function SitesVue(): ReactElement {
  const t = useT();
  const dialogues = useDialogues();
  const f = useFormats();
  const etat = useRessource<Site, SiteCorps>(sites);
  const ecriture = peutEcrire('sites', useRoles());
  const [optionsFerme, setOptionsFerme] = useState<Option[]>([]);
  const [edition, setEdition] = useState<Site | null>(null);
  const [ouvert, setOuvert] = useState(false);
  const [nom, setNom] = useState('');
  const [fermeId, setFermeId] = useState('');
  const [latitude, setLatitude] = useState('');
  const [longitude, setLongitude] = useState('');
  const [altitude, setAltitude] = useState('');
  const [rayonButinage, setRayonButinage] = useState('');
  const [miseEnOeuvre, setMiseEnOeuvre] = useState('');
  const [demenagement, setDemenagement] = useState('');
  const [cloture, setCloture] = useState('');
  const [erreur, setErreur] = useState<string | null>(null);
  const [siteVoisine, setSiteVoisine] = useState<Site | null>(null);
  // Comparaison d'emplacements (SPRINT-23, lot B) : une selection libre, et un
  // tableau qui aligne des criteres SANS les additionner.
  const [comparaisonOuverte, setComparaisonOuverte] = useState(false);
  const [choisis, setChoisis] = useState<number[]>([]);
  const [comparees, setComparees] = useState<ComparaisonSite[] | null>(null);
  const [voisins, setVoisins] = useState<VoisinSite[] | null>(null);
  // Identité postale (SPRINT-21). L'adresse revient NULLE pour les profils non
  // propriétaires : le serveur la retire avec l'altitude.
  const [adresseRue, setAdresseRue] = useState('');
  const [codePostal, setCodePostal] = useState('');
  const [ville, setVille] = useState('');
  const [pays, setPays] = useState('');
  const [typeSite, setTypeSite] = useState('');
  const [exposition, setExposition] = useState('');
  // SPRINT-23 : trois niveaux, et un defaut a `normale` — un defaut nul
  // obligerait chaque lecture a traiter l'absence comme un cas particulier.
  const [priorite, setPriorite] = useState<PrioriteTerrain>('normale');
  // SPRINT-24 : ce qu'on capte sur place. Vide = inconnue, et c'est la reponse
  // honnete tant que personne n'y est alle avec un telephone.
  const [couverture, setCouverture] = useState('');
  // Emport hors ligne (ADR-012) : le rucher en cours de prelevement, et le
  // dernier message rendu — « emporte a 14 h 12 », ou l'echec.
  const [emportEnCours, setEmportEnCours] = useState<number | null>(null);
  const [messageEmport, setMessageEmport] = useState<string | null>(null);
  const [ressources, setRessources] = useState<RessourceFloraleCorps[]>([]);
  const [ressourceAjout, setRessourceAjout] = useState('');
  // Fenêtre de floraison de la ressource qu'on ajoute — la « miellée » du §1.
  const [moisDebutAjout, setMoisDebutAjout] = useState('');
  const [moisFinAjout, setMoisFinAjout] = useState('');
  // Plans de transhumance (SPRINT-21) : lus dans la même modale que
  // l'historique, parce que « où le rucher a été » et « où il va » se regardent
  // ensemble.
  const [transports, setTransports] = useState<Transport[]>([]);
  const [optionsAgent, setOptionsAgent] = useState<Option[]>([]);
  const [planOuvert, setPlanOuvert] = useState(false);
  const [planAgent, setPlanAgent] = useState('');
  const [planDate, setPlanDate] = useState('');
  const [planVehicule, setPlanVehicule] = useState('');
  const [planCapacite, setPlanCapacite] = useState('');
  const [planNbRuches, setPlanNbRuches] = useState('');
  const [planDestination, setPlanDestination] = useState('');
  const [planLatitude, setPlanLatitude] = useState('');
  const [planLongitude, setPlanLongitude] = useState('');
  // Historique d'emplacement et déménagement (SPRINT-21, transhumance).
  const [siteHistorique, setSiteHistorique] = useState<Site | null>(null);
  const [emplacements, setEmplacements] = useState<Emplacement[] | null>(null);
  const [siteDemenage, setSiteDemenage] = useState<Site | null>(null);
  const [nouvelleLatitude, setNouvelleLatitude] = useState('');
  const [nouvelleLongitude, setNouvelleLongitude] = useState('');
  const [nouvelleAltitude, setNouvelleAltitude] = useState('');
  const [dateEmplacement, setDateEmplacement] = useState('');
  const [motifEmplacement, setMotifEmplacement] = useState('transhumance');
  const [noteEmplacement, setNoteEmplacement] = useState('');

  const colonnes: Colonne<Site>[] = [
    { entete: t.champs.nom, rendu: (s) => s.nom },
    { entete: t.champs.ferme, rendu: (s) => s.fermeNom },
    { entete: t.champs.ville, rendu: (s) => s.ville ?? '—' },
    {
      entete: t.champs.typeSite,
      rendu: (s) =>
        s.typeSite == null ? '—' : t.referentielSite.types[s.typeSite],
    },
    { entete: t.champs.latitude, rendu: (s) => s.latitude.toFixed(4) },
    { entete: t.champs.longitude, rendu: (s) => s.longitude.toFixed(4) },
    {
      entete: t.actions.historique,
      rendu: (s) => (
        <button type="button" className="z-lien" onClick={() => void afficherHistorique(s)}>
          {t.actions.historique}
        </button>
      ),
    },
    {
      entete: t.voisins.titre,
      rendu: (s) => (
        <button type="button" className="z-lien" onClick={() => void afficherVoisins(s)}>
          {t.voisins.charger}
        </button>
      ),
    },
    {
      entete: t.horsLigne.emporter,
      rendu: (s) => (
        <button
          type="button"
          className="z-lien"
          disabled={emportEnCours === s.id}
          onClick={() => void emporter(s)}
        >
          {emportEnCours === s.id ? t.etats.chargement : t.horsLigne.emporter}
        </button>
      ),
    },
  ];

  /**
   * Emporte un rucher hors ligne (ADR-012).
   *
   * <p>Un geste explicite, jamais un cache automatique : ce que l'apiculteur n'a
   * pas demandé à emporter, il ne le consultera pas sans le savoir. L'instantané
   * est daté par le SERVEUR — c'est ce qui empêche une donnée du disque de
   * passer un jour pour une donnée fraîche.
   */
  const emporter = async (s: Site) => {
    setEmportEnCours(s.id);
    setMessageEmport(null);
    try {
      const emport = ranger(await emporterRucher(s.id));
      setMessageEmport(
        gabarit(t.horsLigne.emporte, {
          rucher: emport.siteNom,
          instant: f.dateHeure(emport.preleveLe),
        }),
      );
    } catch {
      // Emporter demande du réseau : c'est la seule fonction du produit dont
      // l'échec hors ligne est normal, et le dire évite de la croire cassée.
      setMessageEmport(t.horsLigne.emportImpossible);
    } finally {
      setEmportEnCours(null);
    }
  };

  /** US-046 : les trois sites les plus proches, distance calculée par PostGIS. */
  const afficherVoisins = async (s: Site) => {
    setSiteVoisine(s);
    setVoisins(null);
    try {
      setVoisins(await voisinsSite(s.id, 3));
    } catch {
      setVoisins([]);
    }
  };

  useEffect(() => {
    agents
      .lister()
      .then((liste: Agent[]) =>
        setOptionsAgent(liste.map((a) => ({ valeur: String(a.id), libelle: a.nom }))),
      )
      .catch(() => setOptionsAgent([]));
  }, []);

  useEffect(() => {
    fermes
      .lister()
      .then((liste: Ferme[]) =>
        setOptionsFerme(liste.map((f) => ({ valeur: String(f.id), libelle: f.nom }))),
      )
      .catch(() => setOptionsFerme([]));
  }, [etat.elements]);

  const ouvrir = (s: Site | null) => {
    setEdition(s);
    setNom(s?.nom ?? '');
    setFermeId(s ? String(s.fermeId) : '');
    setLatitude(s ? String(s.latitude) : '');
    setLongitude(s ? String(s.longitude) : '');
    setAltitude(s?.altitude != null ? String(s.altitude) : '');
    setRayonButinage(s?.rayonButinageKm != null ? String(s.rayonButinageKm) : '');
    setMiseEnOeuvre(s?.dateMiseEnOeuvre ?? '');
    setDemenagement(s?.dateDemenagement ?? '');
    setCloture(s?.dateCloture ?? '');
    setAdresseRue(s?.adresseRue ?? '');
    setCodePostal(s?.codePostal ?? '');
    setVille(s?.ville ?? '');
    setPays(s?.pays ?? '');
    setTypeSite(s?.typeSite ?? '');
    setExposition(s?.exposition ?? '');
    setRessources(
      (s?.ressources ?? []).map((r) => ({
        ressource: r.ressource,
        distanceM: r.distanceM,
        moisDebut: r.moisDebut,
        moisFin: r.moisFin,
        note: r.note,
      })),
    );
    setRessourceAjout('');
    setPriorite(s?.priorite ?? 'normale');
    setCouverture(s?.couvertureReseau ?? '');
    setErreur(null);
    setOuvert(true);
  };

  const enregistrer = async () => {
    if (fermeId === '') {
      setErreur(`${t.champs.ferme} ?`);
      return;
    }
    const corps: SiteCorps = {
      nom,
      fermeId: Number(fermeId),
      latitude: Number(latitude),
      longitude: Number(longitude),
      altitude: altitude.trim() === '' ? null : Number(altitude),
      rayonButinageKm: rayonButinage.trim() === '' ? null : Number(rayonButinage),
      dateMiseEnOeuvre: miseEnOeuvre,
      dateDemenagement: ouNull(demenagement),
      dateCloture: ouNull(cloture),
      adresseRue: ouNull(adresseRue),
      codePostal: ouNull(codePostal),
      ville: ouNull(ville),
      // Le code ISO se saisit en majuscules quoi qu'en dise le clavier : la
      // base refuse « fr » et l'utilisateur ne saurait pas pourquoi.
      pays: pays.trim() === '' ? null : pays.trim().toUpperCase(),
      typeSite: typeSite === '' ? null : (typeSite as Site['typeSite']),
      exposition: exposition === '' ? null : (exposition as Site['exposition']),
      priorite,
      couvertureReseau:
        couverture === '' ? null : (couverture as Site['couvertureReseau']),
      ressources,
    };
    try {
      await (edition ? etat.mettreAJour(edition.id, corps) : etat.creer(corps));
      setOuvert(false);
    } catch (cause) {
      setErreur(cause instanceof Error ? cause.message : t.etats.erreur);
    }
  };

  /** SPRINT-21 : la suite des emplacements occupés, la position courante en tête. */
  const afficherHistorique = async (s: Site) => {
    setSiteHistorique(s);
    setEmplacements(null);
    setTransports([]);
    try {
      setEmplacements(await emplacementsSite(s.id));
    } catch {
      setEmplacements([]);
    }
    try {
      setTransports(await listerTransports(s.id));
    } catch {
      setTransports([]);
    }
  };

  const rechargerTransports = async (siteId: number) => {
    try {
      setTransports(await listerTransports(siteId));
    } catch (cause) {
      setErreur(cause instanceof Error ? cause.message : t.etats.erreur);
    }
  };

  /**
   * Planifie un déplacement. Sans coordonnées, le plan reste un plan : le
   * serveur refusera de le réaliser, et il le dira en clair plutôt que d'ouvrir
   * un emplacement sans position.
   */
  const planifier = async () => {
    if (!siteHistorique) {
      return;
    }
    try {
      await planifierTransport({
        siteId: siteHistorique.id,
        agentId: Number(planAgent),
        datePrevue: planDate,
        heurePrevue: null,
        vehicule: ouNull(planVehicule),
        capaciteRuches: planCapacite.trim() === '' ? null : Number(planCapacite),
        nbRuches: planNbRuches.trim() === '' ? null : Number(planNbRuches),
        destinationLibelle: planDestination,
        destinationLatitude: planLatitude.trim() === '' ? null : Number(planLatitude),
        destinationLongitude: planLongitude.trim() === '' ? null : Number(planLongitude),
        note: null,
      });
      setPlanOuvert(false);
      setPlanVehicule('');
      setPlanCapacite('');
      setPlanNbRuches('');
      setPlanDestination('');
      setPlanLatitude('');
      setPlanLongitude('');
      await rechargerTransports(siteHistorique.id);
    } catch (cause) {
      setErreur(cause instanceof Error ? cause.message : t.etats.erreur);
    }
  };

  /**
   * Supprime le PLAN, et lui seul.
   *
   * <p>Annuler et supprimer ne font pas la même chose : l'annulation garde la
   * trace qu'un transport avait été prévu, la suppression retire la ligne. Un
   * transport RÉALISÉ n'est proposé ni à l'une ni à l'autre — il a fait
   * déménager le rucher, et l'historique d'emplacement en porte la trace.
   */
  const supprimerPlan = async (transport: Transport) => {
    if (!(await dialogues.confirmer(t.terrain.transports.supprimer))) {
      return;
    }
    try {
      await supprimerTransport(transport.id);
      await rechargerTransports(transport.siteId);
    } catch (cause) {
      setErreur(cause instanceof Error ? cause.message : t.etats.erreur);
    }
  };

  /** Réaliser un transport fait déménager le rucher : même trace qu'un déménagement direct. */
  const realiser = async (transport: Transport) => {
    try {
      await realiserTransport(transport.id);
      await rechargerTransports(transport.siteId);
      setEmplacements(await emplacementsSite(transport.siteId));
      etat.recharger();
    } catch (cause) {
      setErreur(cause instanceof Error ? cause.message : t.etats.erreur);
    }
  };

  /**
   * Ouvre le déménagement.
   *
   * <p>Les champs partent de la position ACTUELLE : un rucher se déplace de
   * quelques centaines de mètres aussi souvent qu'il change de département, et
   * repartir d'un formulaire vide obligerait à ressaisir des coordonnées connues.
   */
  const ouvrirDemenagement = (s: Site) => {
    setSiteDemenage(s);
    setNouvelleLatitude(String(s.latitude));
    setNouvelleLongitude(String(s.longitude));
    setNouvelleAltitude(s.altitude != null ? String(s.altitude) : '');
    setDateEmplacement('');
    setMotifEmplacement('transhumance');
    setNoteEmplacement('');
    setErreur(null);
  };

  const demenager = async () => {
    if (!siteDemenage) {
      return;
    }
    try {
      await demenagerSite(siteDemenage.id, {
        latitude: Number(nouvelleLatitude),
        longitude: Number(nouvelleLongitude),
        altitude: nouvelleAltitude.trim() === '' ? null : Number(nouvelleAltitude),
        dateDebut: dateEmplacement,
        motif: motifEmplacement === '' ? null : (motifEmplacement as Emplacement['motif']),
        note: ouNull(noteEmplacement),
      });
      setSiteDemenage(null);
      etat.recharger();
    } catch (cause) {
      setErreur(cause instanceof Error ? cause.message : t.etats.erreur);
    }
  };

  /** Une ressource ne se déclare qu'une fois par rucher : la base le refuse aussi. */
  const ajouterRessource = () => {
    if (ressourceAjout === '' || ressources.some((r) => r.ressource === ressourceAjout)) {
      return;
    }
    // Les deux mois vont ensemble : une floraison qui commence sans finir ne se
    // lit pas, et le serveur la refuse. On n'envoie donc la fenêtre que
    // complète.
    const fenetre = moisDebutAjout !== '' && moisFinAjout !== '';
    setRessources([
      ...ressources,
      {
        ressource: ressourceAjout as RessourceFloraleType,
        distanceM: null,
        moisDebut: fenetre ? Number(moisDebutAjout) : null,
        moisFin: fenetre ? Number(moisFinAjout) : null,
        note: null,
      },
    ]);
    setRessourceAjout('');
    setMoisDebutAjout('');
    setMoisFinAjout('');
  };

  /** « mai – juin », ou rien si la fenêtre n'est pas renseignée. */
  const floraison = (r: { moisDebut: number | null; moisFin: number | null }): string =>
    r.moisDebut == null || r.moisFin == null
      ? ''
      : ` · ${t.referentielSite.mois[String(r.moisDebut) as keyof typeof t.referentielSite.mois]}` +
        ` – ${t.referentielSite.mois[String(r.moisFin) as keyof typeof t.referentielSite.mois]}`;

  return (
    <CorpsSection
      titre={t.onglets.sites}
      sousTitre={t.soustitres.sites}
      etat={etat}
      onNouveau={() => ouvrir(null)}
      ecriture={ecriture}
      actions={
        <Bouton
          variante="secondaire"
          onClick={() => {
            setChoisis([]);
            setComparees(null);
            setComparaisonOuverte(true);
          }}
        >
          {t.comparaison.titre}
        </Bouton>
      }
    >
      {etat.elements.length > 0 && (
        <Table colonnes={colonnes} elements={etat.elements} onModifier={ouvrir} onSupprimer={(e) => void etat.supprimer(e.id)} ecriture={ecriture} />
      )}
      {messageEmport && (
        <p className="z-info" role="status">
          {messageEmport}
        </p>
      )}
      {comparaisonOuverte && (
        <Modale titre={t.comparaison.titre} onFermer={() => setComparaisonOuverte(false)}>
          {/* L'absence de note globale est un choix, et il se dit : melanger des
              kilos, des especes florales et une altitude donnerait un chiffre
              qui a l'autorite d'une mesure sans en avoir la matiere. */}
          <p className="z-info">{t.comparaison.aide}</p>
          <fieldset className="z-composition">
            <legend className="z-champ__libelle">{t.lot.selection}</legend>
            <ul className="z-liste-simple">
              {etat.elements.map((s) => (
                <li key={s.id}>
                  <label className="z-champ__case">
                    <input
                      type="checkbox"
                      checked={choisis.includes(s.id)}
                      onChange={() =>
                        setChoisis((avant) =>
                          avant.includes(s.id)
                            ? avant.filter((id) => id !== s.id)
                            : [...avant, s.id],
                        )
                      }
                    />
                    {s.nom}
                    {s.ville ? <small className="z-info"> · {s.ville}</small> : null}
                  </label>
                </li>
              ))}
            </ul>
          </fieldset>
          <div className="z-form__actions">
            <Bouton variante="fantome" onClick={() => setComparaisonOuverte(false)}>
              {t.actions.fermer}
            </Bouton>
            <Bouton
              variante="primaire"
              disabled={choisis.length < 2}
              onClick={() => {
                void comparerSites(choisis)
                  .then(setComparees)
                  .catch(() => setComparees(null));
              }}
            >
              {t.actions.comparer}
            </Bouton>
          </div>
          {choisis.length < 2 && <p className="z-info">{t.comparaison.choisir}</p>}
          {comparees !== null && comparees.length > 0 && (
            <div className="z-table-enveloppe">
              <table className="z-table">
                <thead>
                  <tr>
                    <th>{t.champs.nom}</th>
                    <th>{t.champs.typeSite}</th>
                    <th>{t.champs.exposition}</th>
                    <th>{t.champs.altitude}</th>
                    <th>{t.onglets.ruches}</th>
                    <th>{t.comparaison.rendement}</th>
                    <th>{t.comparaison.ressources}</th>
                    <th>{t.comparaison.voisinage}</th>
                  </tr>
                </thead>
                <tbody>
                  {comparees.map((c) => (
                    <tr key={c.siteId}>
                      <td>
                        {c.siteNom}
                        {c.ville ? <small className="z-info"> · {c.ville}</small> : null}
                      </td>
                      <td>{c.typeSite ? t.referentielSite.types[c.typeSite] : '—'}</td>
                      <td>
                        {c.exposition ? t.referentielSite.expositions[c.exposition] : '—'}
                      </td>
                      <td>{c.altitude === null ? '—' : `${f.nombre(c.altitude)} m`}</td>
                      <td>{c.nbRuches}</td>
                      <td>
                        {/* Un rucher sans colonie n'a pas un rendement de zero :
                            il n'en a pas. Ecrire 0 le classerait dernier d'une
                            comparaison a laquelle il n'a pas encore participe. */}
                        {c.rendementKgParRuche === null ? (
                          <span className="z-info">{t.comparaison.sansRuche}</span>
                        ) : (
                          `${f.nombre(c.rendementKgParRuche)} kg`
                        )}
                      </td>
                      <td>
                        {c.ressourcesDeclarees}
                        {c.ressourcesEnFleur > 0 ? (
                          <small className="z-info">
                            {' '}
                            · {c.ressourcesEnFleur} {t.comparaison.enFleur}
                          </small>
                        ) : null}
                      </td>
                      <td>{c.ruchersA3km}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Modale>
      )}
      {siteVoisine && (
        <Modale
          titre={`${t.voisins.titre} — ${siteVoisine.nom}`}
          onFermer={() => setSiteVoisine(null)}
        >
          {voisins === null && <p className="z-info">{t.etats.chargement}</p>}
          {voisins !== null && voisins.length === 0 && <p className="z-info">{t.voisins.aucun}</p>}
          {voisins !== null && voisins.length > 0 && (
            <ul className="z-liste-simple">
              {voisins.map((v) => (
                <li key={v.site.id}>
                  <strong>{v.site.nom}</strong> — {t.voisins.distance} :{' '}
                  {f.distance(v.distanceMetres)}
                </li>
              ))}
            </ul>
          )}
        </Modale>
      )}
      {siteHistorique && (
        <Modale
          titre={`${t.terrain.emplacements.titre} — ${siteHistorique.nom}`}
          onFermer={() => setSiteHistorique(null)}
        >
          <p className="z-info">{t.terrain.emplacements.aide}</p>
          {emplacements === null && <p className="z-info">{t.etats.chargement}</p>}
          {emplacements !== null && emplacements.length === 0 && (
            <p className="z-info">{t.terrain.emplacements.aucun}</p>
          )}
          {emplacements !== null && emplacements.length > 0 && (
            <ul className="z-liste-simple">
              {emplacements.map((e) => (
                <li key={e.id}>
                  {e.courant && <Pastille ton="succes">{t.terrain.emplacements.courant}</Pastille>}{' '}
                  {t.terrain.emplacements.depuis} {f.date(e.dateDebut)}
                  {e.dateFin && ` — ${t.terrain.emplacements.jusqua} ${f.date(e.dateFin)}`} ·{' '}
                  {e.latitude.toFixed(4)}, {e.longitude.toFixed(4)}
                  {e.motif && ` · ${t.referentielSite.motifs[e.motif]}`}
                </li>
              ))}
            </ul>
          )}
          {/* L'environnement (SPRINT-32) se lit dans la fiche du LIEU : ce qu'il
              y a autour d'un rucher est une propriété de l'emplacement, pas de
              la colonie — et cela change quand le rucher déménage. */}
          <PanneauEnvironnement
            siteId={siteHistorique.id}
            ressources={siteHistorique.ressources}
          />

          <fieldset className="z-composition">
            <legend className="z-champ__libelle">{t.terrain.transports.titre}</legend>
            <p className="z-info">{t.terrain.transports.aide}</p>
            {transports.length === 0 && <p className="z-info">{t.terrain.transports.aucun}</p>}
            {transports.length > 0 && (
              <ul className="z-liste-simple">
                {transports.map((transport) => (
                  <li key={transport.id}>
                    <Pastille ton={transport.statut === 'prevu' ? 'attention' : 'succes'}>
                      {t.terrain.transports.statuts[transport.statut]}
                    </Pastille>{' '}
                    {f.date(transport.datePrevue)} · {transport.destinationLibelle}
                    {transport.vehicule ? ` · ${transport.vehicule}` : ''}
                    {/* Le nombre de voyages est calculé par le serveur : c'est
                        l'écart entre la capacité et le nombre de ruches qui fait
                        toute l'information du plan. */}
                    {transport.voyages != null &&
                      ` · ${gabarit(t.terrain.transports.voyages, {
                        nombre: String(transport.voyages),
                      })}`}
                    {ecriture && transport.statut === 'prevu' && (
                      <>
                        {' '}
                        <button
                          type="button"
                          className="z-lien"
                          onClick={() => void realiser(transport)}
                        >
                          {t.actions.realiser}
                        </button>{' '}
                        <button
                          type="button"
                          className="z-lien z-lien--danger"
                          onClick={() => {
                            void annulerTransport(transport.id).then(() =>
                              rechargerTransports(transport.siteId),
                            );
                          }}
                        >
                          {t.actions.annuler}
                        </button>
                      </>
                    )}
                    {ecriture && transport.statut !== 'realise' && (
                      <>
                        {' '}
                        <button
                          type="button"
                          className="z-lien z-lien--danger"
                          onClick={() => void supprimerPlan(transport)}
                        >
                          {t.terrain.transports.supprimer}
                        </button>
                      </>
                    )}
                    {transport.statut === 'realise' && (
                      <>
                        <br />
                        <small className="z-info">
                          {t.terrain.transports.suppressionRealise}
                        </small>
                      </>
                    )}
                  </li>
                ))}
              </ul>
            )}
            {ecriture && !planOuvert && (
              <Bouton variante="fantome" onClick={() => setPlanOuvert(true)}>
                {t.terrain.transports.nouveau}
              </Bouton>
            )}
            {ecriture && planOuvert && (
              <div className="z-form">
                <div className="z-form__grille">
                  <ChampDate
                    libelle={t.champs.datePrevue}
                    valeur={planDate}
                    onChange={setPlanDate}
                    requis
                  />
                  <ChampSelect
                    libelle={t.champs.agentResponsable}
                    valeur={planAgent}
                    options={optionsAgent}
                    onChange={setPlanAgent}
                    requis
                  />
                  <ChampTexte
                    libelle={t.champs.vehicule}
                    valeur={planVehicule}
                    onChange={setPlanVehicule}
                  />
                </div>
                <div className="z-form__grille">
                  <ChampNombre
                    libelle={t.champs.capaciteRuches}
                    valeur={planCapacite}
                    onChange={setPlanCapacite}
                  />
                  <ChampNombre
                    libelle={t.champs.nbRuches}
                    valeur={planNbRuches}
                    onChange={setPlanNbRuches}
                  />
                </div>
                <ChampTexte
                  libelle={t.champs.destination}
                  valeur={planDestination}
                  onChange={setPlanDestination}
                  requis
                />
                <div className="z-form__grille">
                  <ChampNombre
                    libelle={t.champs.latitude}
                    valeur={planLatitude}
                    onChange={setPlanLatitude}
                  />
                  <ChampNombre
                    libelle={t.champs.longitude}
                    valeur={planLongitude}
                    onChange={setPlanLongitude}
                  />
                </div>
                <div className="z-form__actions">
                  <Bouton variante="fantome" onClick={() => setPlanOuvert(false)}>
                    {t.actions.annuler}
                  </Bouton>
                  <Bouton variante="primaire" onClick={() => void planifier()}>
                    {t.actions.enregistrer}
                  </Bouton>
                </div>
              </div>
            )}
          </fieldset>
          {ecriture && (
            <div className="z-form__actions">
              <Bouton
                variante="primaire"
                onClick={() => {
                  ouvrirDemenagement(siteHistorique);
                  setSiteHistorique(null);
                }}
              >
                {t.actions.demenager}
              </Bouton>
            </div>
          )}
        </Modale>
      )}
      {siteDemenage && (
        <Modale
          titre={`${t.actions.demenager} — ${siteDemenage.nom}`}
          onFermer={() => setSiteDemenage(null)}
        >
          <form
            className="z-form"
            onSubmit={(e) => {
              e.preventDefault();
              void demenager();
            }}
          >
            <p className="z-info">{t.terrain.emplacements.aide}</p>
            <div className="z-form__grille">
              <ChampNombre
                libelle={t.champs.latitude}
                valeur={nouvelleLatitude}
                onChange={setNouvelleLatitude}
                requis
              />
              <ChampNombre
                libelle={t.champs.longitude}
                valeur={nouvelleLongitude}
                onChange={setNouvelleLongitude}
                requis
              />
              <ChampNombre
                libelle={t.champs.altitude}
                valeur={nouvelleAltitude}
                onChange={setNouvelleAltitude}
              />
            </div>
            <div className="z-form__grille">
              <ChampDate
                libelle={t.terrain.emplacements.depuis}
                valeur={dateEmplacement}
                onChange={setDateEmplacement}
                requis
              />
              <ChampSelect
                libelle={t.champs.motif}
                valeur={motifEmplacement}
                options={MOTIFS_EMPLACEMENT.map((m) => ({
                  valeur: m,
                  libelle: t.referentielSite.motifs[m],
                }))}
                onChange={setMotifEmplacement}
              />
            </div>
            <ChampZone
              libelle={t.champs.note}
              valeur={noteEmplacement}
              onChange={setNoteEmplacement}
            />
            {erreur && <p className="z-form__erreur">{erreur}</p>}
            <div className="z-form__actions">
              <Bouton variante="fantome" onClick={() => setSiteDemenage(null)}>
                {t.actions.annuler}
              </Bouton>
              <Bouton variante="primaire" type="submit">
                {t.actions.demenager}
              </Bouton>
            </div>
          </form>
        </Modale>
      )}
      {ouvert && (
        <Modale titre={t.onglets.sites} onFermer={() => setOuvert(false)}>
          <form
            className="z-form"
            onSubmit={(e) => {
              e.preventDefault();
              void enregistrer();
            }}
          >
            <ChampTexte libelle={t.champs.nom} valeur={nom} onChange={setNom} requis />
            <ChampSelect
              libelle={t.champs.ferme}
              valeur={fermeId}
              options={optionsFerme}
              onChange={setFermeId}
              requis
            />
            <div className="z-form__grille">
              <ChampNombre libelle={t.champs.latitude} valeur={latitude} onChange={setLatitude} requis />
              <ChampNombre libelle={t.champs.longitude} valeur={longitude} onChange={setLongitude} requis />
              <ChampNombre libelle={t.champs.altitude} valeur={altitude} onChange={setAltitude} />
              {/* Le rayon de butinage (SPRINT-32) : vide, le défaut de
                  `ConfigZumm.ini` s'applique. Le figer reviendrait à dessiner le
                  même cercle partout — et à calculer les surfaces dessus. */}
              <ChampNombre
                libelle={t.champs.rayonButinage}
                valeur={rayonButinage}
                onChange={setRayonButinage}
                pas="0.5"
                min={0.5}
                max={15}
              />
            </div>
            <fieldset className="z-composition">
              <legend className="z-champ__libelle">{t.champs.adresseRue}</legend>
              {/* L'adresse est aussi sensible que la position : le serveur la
                  retire aux profils non propriétaires, qui verront ces champs
                  vides sans que ce soit une perte de donnée. */}
              <ChampTexte
                libelle={t.champs.adresseRue}
                valeur={adresseRue}
                onChange={setAdresseRue}
              />
              <div className="z-form__grille">
                <ChampTexte
                  libelle={t.champs.codePostal}
                  valeur={codePostal}
                  onChange={setCodePostal}
                />
                <ChampTexte libelle={t.champs.ville} valeur={ville} onChange={setVille} />
                <ChampTexte libelle={t.champs.pays} valeur={pays} onChange={setPays} />
              </div>
            </fieldset>
            <div className="z-form__grille">
              <ChampSelect
                libelle={t.champs.typeSite}
                valeur={typeSite}
                options={[
                  { valeur: '', libelle: t.champs.aucun },
                  ...TYPES_SITE.map((type) => ({
                    valeur: type,
                    libelle: t.referentielSite.types[type],
                  })),
                ]}
                onChange={setTypeSite}
              />
              <ChampSelect
                libelle={t.champs.exposition}
                valeur={exposition}
                options={[
                  { valeur: '', libelle: t.champs.aucun },
                  ...EXPOSITIONS.map((sens) => ({
                    valeur: sens,
                    libelle: t.referentielSite.expositions[sens],
                  })),
                ]}
                onChange={setExposition}
              />
              <ChampSelect
                libelle={t.champs.couvertureReseau}
                valeur={couverture}
                options={[
                  { valeur: '', libelle: t.horsLigne.couvertureInconnue },
                  ...COUVERTURES_RESEAU.map((niveau) => ({
                    valeur: niveau,
                    libelle: t.couvertureReseau[niveau],
                  })),
                ]}
                onChange={setCouverture}
              />
              <ChampSelect
                libelle={t.champs.prioriteTerrain}
                valeur={priorite}
                options={PRIORITES_TERRAIN.map((niveau) => ({
                  valeur: niveau,
                  libelle: t.prioriteTerrain[niveau],
                }))}
                onChange={(valeur) => setPriorite(valeur as PrioriteTerrain)}
              />
            </div>
            <fieldset className="z-composition">
              <legend className="z-champ__libelle">{t.champs.ressourcesFlorales}</legend>
              {ressources.length === 0 && <p className="z-info">{t.champs.aucun}</p>}
              {ressources.length > 0 && (
                <ul className="z-liste-simple">
                  {ressources.map((r) => (
                    <li key={r.ressource}>
                      <Pastille>{t.referentielSite.ressources[r.ressource]}</Pastille>
                      {floraison(r)}{' '}
                      <button
                        type="button"
                        className="z-lien z-lien--danger"
                        onClick={() =>
                          setRessources(ressources.filter((x) => x.ressource !== r.ressource))
                        }
                      >
                        {t.actions.supprimer}
                      </button>
                    </li>
                  ))}
                </ul>
              )}
              <div className="z-form__grille">
                <ChampSelect
                  libelle={t.actions.ajouterRessource}
                  valeur={ressourceAjout}
                  options={[
                    { valeur: '', libelle: t.champs.aucun },
                    ...RESSOURCES_FLORALES.map((r) => ({
                      valeur: r,
                      libelle: t.referentielSite.ressources[r],
                    })),
                  ]}
                  onChange={setRessourceAjout}
                />
                <ChampSelect
                  libelle={t.champs.moisDebut}
                  valeur={moisDebutAjout}
                  options={optionsMois(t)}
                  onChange={setMoisDebutAjout}
                />
                <ChampSelect
                  libelle={t.champs.moisFin}
                  valeur={moisFinAjout}
                  options={optionsMois(t)}
                  onChange={setMoisFinAjout}
                />
                <Bouton variante="fantome" onClick={ajouterRessource}>
                  {t.actions.ajouterRessource}
                </Bouton>
              </div>
            </fieldset>
            <div className="z-form__grille">
              <ChampDate
                libelle={t.champs.dateMiseEnOeuvre}
                valeur={miseEnOeuvre}
                onChange={setMiseEnOeuvre}
                requis
              />
              <ChampDate libelle={t.champs.dateDemenagement} valeur={demenagement} onChange={setDemenagement} />
              <ChampDate libelle={t.champs.dateCloture} valeur={cloture} onChange={setCloture} />
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
