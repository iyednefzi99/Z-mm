import { useEffect, useState, type ReactElement } from 'react';
import {
  agents,
  ajouterPhoto,
  chargerMeteo,
  listerPhotos,
  plannings,
  ruches,
  supprimerPhoto,
  telechargerRapportVisite,
  visites,
} from '../api/client';
import type {
  Agent,
  CauseCellules,
  EffectifQualitatif,
  EtatSante,
  GravitePathologie,
  MotifPonte,
  Pathologie,
  PathologieCorps,
  Photo,
  Planning,
  RaisonVisite,
  Ruche,
  SourceMeteo,
  Temperament,
  Visite,
  VisiteCorps,
} from '../api/types';
import {
  CAUSES_CELLULES,
  GRAVITES_PATHOLOGIE,
  MOTIFS_PONTE,
  PATHOLOGIES,
  RAISONS_VISITE,
  TEMPERAMENTS,
} from '../api/types';
import { useFormats, useT } from '../i18n/langue';
import { useRessource } from '../hooks';
import {
  Bouton,
  ChampDate,
  ChampHeure,
  ChampNombre,
  ChampSelect,
  ChampTexte,
  ChampZone,
  Colonne,
  Modale,
  Option,
  Table,
} from '../ui/composants';
import { CorpsSection } from './CorpsSection';

/**
 * Valeurs d'une case a trois etats.
 *
 * <p>Une vraie case a cocher n'en a que deux, et c'est precisement le piege :
 * « pas coche » deviendrait « non », alors que la plupart des visites n'ont
 * simplement pas regarde. La distinction est portee jusqu'a la base, ou chaque
 * colonne est NULLABLE — la perdre ici rendrait fausse toute statistique
 * construite sur la grille (voir `ObservationVisite`).
 */
const TROIS_ETATS = { indefini: '', oui: 'oui', non: 'non' } as const;

/** Chaine du formulaire vers le booleen du contrat. */
const versBooleen = (valeur: string): boolean | null =>
  valeur === '' ? null : valeur === TROIS_ETATS.oui;

/** Booleen du contrat vers la chaine du formulaire. */
const versTroisEtats = (valeur: boolean | null | undefined): string =>
  valeur == null ? '' : valeur ? TROIS_ETATS.oui : TROIS_ETATS.non;

const EFFECTIFS: EffectifQualitatif[] = ['faible', 'moyen', 'fort'];
const SANTES: EtatSante[] = ['bon', 'moyen', 'mauvais'];
const PRODUCTIVITES = ['1', '2', '3'];

export function VisitesVue(): ReactElement {
  const t = useT();
  const f = useFormats();
  const etat = useRessource<Visite, VisiteCorps>(visites);
  const [optRuches, setOptRuches] = useState<Option[]>([]);
  const [optAgents, setOptAgents] = useState<Option[]>([]);
  const [approuves, setApprouves] = useState<Planning[]>([]);
  const [ouvert, setOuvert] = useState(false);
  const [edition, setEdition] = useState<Visite | null>(null);
  const [rucheId, setRucheId] = useState('');
  const [agentId, setAgentId] = useState('');
  const [planningId, setPlanningId] = useState('');
  const [dateVisite, setDateVisite] = useState('');
  const [heureVisite, setHeureVisite] = useState('');
  const [dureeMin, setDureeMin] = useState('');
  const [raison, setRaison] = useState<RaisonVisite>('controle');
  const [constatations, setConstatations] = useState('');
  const [actionsPrevues, setActionsPrevues] = useState('');
  const [actionsEffectuees, setActionsEffectuees] = useState('');
  const [recommandations, setRecommandations] = useState('');
  const [effectif, setEffectif] = useState('');
  const [sante, setSante] = useState('');
  const [productivite, setProductivite] = useState('');
  const [couvainOeufs, setCouvainOeufs] = useState('');
  const [couvainLarves, setCouvainLarves] = useState('');
  const [couvainOpercule, setCouvainOpercule] = useState('');
  const [motifPonte, setMotifPonte] = useState('');
  const [reineVue, setReineVue] = useState('');
  const [cellulesRoyales, setCellulesRoyales] = useState('');
  const [causeCellules, setCauseCellules] = useState('');
  const [cadresCouvain, setCadresCouvain] = useState('');
  const [cadresMiel, setCadresMiel] = useState('');
  const [cadresPollen, setCadresPollen] = useState('');
  const [temperament, setTemperament] = useState('');
  const [temperature, setTemperature] = useState('');
  const [humidite, setHumidite] = useState('');
  const [vent, setVent] = useState('');
  const [sourceMeteo, setSourceMeteo] = useState<SourceMeteo | null>(null);
  const [pathologies, setPathologies] = useState<PathologieCorps[]>([]);
  const [pathologie, setPathologie] = useState<Pathologie>('varroose');
  const [gravite, setGravite] = useState<GravitePathologie>('suspectee');
  const [parcRuches, setParcRuches] = useState<Ruche[]>([]);
  const [photos, setPhotos] = useState<Photo[]>([]);
  const [urlPhoto, setUrlPhoto] = useState('');
  const [legende, setLegende] = useState('');
  const [erreur, setErreur] = useState<string | null>(null);

  const optRaison: Option[] = RAISONS_VISITE.map((r) => ({ valeur: r, libelle: t.visite.raisons[r] }));
  const vide: Option = { valeur: '', libelle: t.champs.aucun };
  const optEffectif: Option[] = [vide, ...EFFECTIFS.map((e) => ({ valeur: e, libelle: t.visite.effectifs[e] }))];
  const optSante: Option[] = [vide, ...SANTES.map((s) => ({ valeur: s, libelle: t.visite.santes[s] }))];
  const optProd: Option[] = [vide, ...PRODUCTIVITES.map((p) => ({ valeur: p, libelle: p }))];
  const optTroisEtats: Option[] = [
    { valeur: TROIS_ETATS.indefini, libelle: t.visite.nonObserve },
    { valeur: TROIS_ETATS.oui, libelle: t.visite.oui },
    { valeur: TROIS_ETATS.non, libelle: t.visite.non },
  ];
  const optMotifPonte: Option[] = [
    vide,
    ...MOTIFS_PONTE.map((m) => ({ valeur: m, libelle: t.visite.motifsPonte[m] })),
  ];
  const optCauseCellules: Option[] = [
    vide,
    ...CAUSES_CELLULES.map((c) => ({ valeur: c, libelle: t.visite.causesCellules[c] })),
  ];
  const optTemperament: Option[] = [
    vide,
    ...TEMPERAMENTS.map((x) => ({ valeur: x, libelle: t.visite.temperaments[x] })),
  ];
  const optPathologie: Option[] = PATHOLOGIES.map((x) => ({
    valeur: x,
    libelle: t.sanitaire.pathologies[x],
  }));
  const optGravite: Option[] = GRAVITES_PATHOLOGIE.map((g) => ({
    valeur: g,
    libelle: t.sanitaire.gravites[g],
  }));

  // Un rapport ne se rattache qu'a un planning APPROUVE (US-008), et seulement a
  // ceux de la ruche visitee : proposer les autres inviterait a une incoherence
  // que le serveur refuserait de toute facon.
  const optPlannings: Option[] = [
    vide,
    ...approuves
      .filter((p) => rucheId === '' || String(p.rucheId) === rucheId)
      .map((p) => ({
        valeur: String(p.id),
        libelle: `${f.date(p.datePrevue)} — ${t.visite.raisons[p.raison]}`,
      })),
  ];

  const colonnes: Colonne<Visite>[] = [
    { entete: t.champs.modele, rendu: (v) => v.rucheModele },
    { entete: t.visite.agent, rendu: (v) => v.agentNom },
    { entete: t.visite.date, rendu: (v) => f.date(v.dateVisite) },
    { entete: t.visite.sante, rendu: (v) => (v.etatSante ? t.visite.santes[v.etatSante] : '—') },
    { entete: t.visite.photos, rendu: (v) => String(v.photos.length) },
    {
      entete: t.visite.rapport,
      rendu: (v) => (
        <Bouton variante="fantome" onClick={() => void telechargerRapportVisite(v.id)}>
          ⬇ {t.visite.rapportPdf}
        </Bouton>
      ),
    },
  ];

  useEffect(() => {
    void ruches
      .lister()
      .then((l: Ruche[]) => {
        // Le parc entier est conserve, et pas seulement les libelles : relever
        // la meteo demande le SITE de la ruche visitee, que le formulaire ne
        // connait pas autrement.
        setParcRuches(l);
        setOptRuches(l.map((r) => ({ valeur: String(r.id), libelle: r.modele })));
      })
      .catch(() => setOptRuches([]));
    void agents.lister().then((l: Agent[]) => setOptAgents(l.map((a) => ({ valeur: String(a.id), libelle: a.nom })))).catch(() => setOptAgents([]));
    void plannings
      .lister()
      .then((l: Planning[]) => setApprouves(l.filter((p) => p.statut === 'approuve')))
      .catch(() => setApprouves([]));
  }, [etat.elements]);

  const ouvrir = (v: Visite | null) => {
    setEdition(v);
    setRucheId(v ? String(v.rucheId) : '');
    setAgentId(v ? String(v.agentId) : '');
    setPlanningId(v?.planningId != null ? String(v.planningId) : '');
    setDateVisite(v?.dateVisite ?? '');
    // Le serveur publie « 14:30:00 » ; `input type="time"` veut « 14:30 ».
    setHeureVisite(v?.heureVisite != null ? v.heureVisite.slice(0, 5) : '');
    setDureeMin(v?.dureeMin != null ? String(v.dureeMin) : '');
    setRaison(v?.raison ?? 'controle');
    setConstatations(v?.constatations ?? '');
    setActionsPrevues(v?.actionsPrevues ?? '');
    setActionsEffectuees(v?.actionsEffectuees ?? '');
    setRecommandations(v?.recommandations ?? '');
    setEffectif(v?.effectifQualitatif ?? '');
    setSante(v?.etatSante ?? '');
    setProductivite(v?.productivite != null ? String(v.productivite) : '');
    setCouvainOeufs(versTroisEtats(v?.observation?.couvainOeufs));
    setCouvainLarves(versTroisEtats(v?.observation?.couvainLarves));
    setCouvainOpercule(versTroisEtats(v?.observation?.couvainOpercule));
    setMotifPonte(v?.observation?.motifPonte ?? '');
    setReineVue(versTroisEtats(v?.observation?.reineVue));
    setCellulesRoyales(
      v?.observation?.cellulesRoyales != null ? String(v.observation.cellulesRoyales) : '',
    );
    setCauseCellules(v?.observation?.cellulesRoyalesCause ?? '');
    setCadresCouvain(
      v?.observation?.cadresCouvain != null ? String(v.observation.cadresCouvain) : '',
    );
    setCadresMiel(v?.observation?.cadresMiel != null ? String(v.observation.cadresMiel) : '');
    setCadresPollen(
      v?.observation?.cadresPollen != null ? String(v.observation.cadresPollen) : '',
    );
    setTemperament(v?.observation?.temperament ?? '');
    setTemperature(
      v?.meteo?.temperatureCelsius != null ? String(v.meteo.temperatureCelsius) : '',
    );
    setHumidite(v?.meteo?.humiditePourcent != null ? String(v.meteo.humiditePourcent) : '');
    setVent(v?.meteo?.ventKmh != null ? String(v.meteo.ventKmh) : '');
    setSourceMeteo(v?.meteo?.source ?? null);
    setPathologies(
      (v?.pathologies ?? []).map((x) => ({
        pathologie: x.pathologie,
        gravite: x.gravite,
        note: x.note,
      })),
    );
    setPathologie('varroose');
    setGravite('suspectee');
    setPhotos(v?.photos ?? []);
    setUrlPhoto('');
    setLegende('');
    setErreur(null);
    setOuvert(true);
  };

  const enregistrer = async () => {
    if (rucheId === '' || agentId === '') {
      setErreur(t.etats.champsRequis);
      return;
    }
    const texte = (valeur: string) => (valeur.trim() === '' ? null : valeur.trim());
    const nombre = (valeur: string) => (valeur === '' ? null : Number(valeur));
    const observation = {
      couvainOeufs: versBooleen(couvainOeufs),
      couvainLarves: versBooleen(couvainLarves),
      couvainOpercule: versBooleen(couvainOpercule),
      motifPonte: motifPonte === '' ? null : (motifPonte as MotifPonte),
      reineVue: versBooleen(reineVue),
      cellulesRoyales: nombre(cellulesRoyales),
      // La cause ne part qu'avec des cellules : « supersedure, zero cellule »
      // est une contradiction que la base refuse, et qu'il vaut mieux ne pas
      // envoyer plutot que faire rejeter apres la saisie.
      cellulesRoyalesCause:
        causeCellules !== '' && Number(cellulesRoyales) > 0
          ? (causeCellules as CauseCellules)
          : null,
      cadresCouvain: nombre(cadresCouvain),
      cadresMiel: nombre(cadresMiel),
      cadresPollen: nombre(cadresPollen),
      temperament: temperament === '' ? null : (temperament as Temperament),
    };
    const meteo = {
      temperatureCelsius: nombre(temperature),
      humiditePourcent: nombre(humidite),
      ventKmh: nombre(vent),
      source: sourceMeteo,
    };
    // Une grille vide et une grille de « non » ne disent pas la meme chose : on
    // n'envoie l'objet que si quelque chose a ete renseigne.
    const renseigne = (valeurs: object) =>
      Object.values(valeurs).some((valeur) => valeur !== null);
    const corps: VisiteCorps = {
      rucheId: Number(rucheId),
      agentId: Number(agentId),
      planningId: planningId === '' ? null : Number(planningId),
      dateVisite,
      heureVisite: heureVisite === '' ? null : heureVisite,
      dureeMin: dureeMin === '' ? null : Number(dureeMin),
      raison,
      constatations: texte(constatations),
      actionsPrevues: texte(actionsPrevues),
      actionsEffectuees: texte(actionsEffectuees),
      recommandations: texte(recommandations),
      effectifQualitatif: effectif === '' ? null : (effectif as EffectifQualitatif),
      etatSante: sante === '' ? null : (sante as EtatSante),
      productivite: productivite === '' ? null : Number(productivite),
      observation: renseigne(observation) ? observation : null,
      meteo: renseigne(meteo) ? meteo : null,
      pathologies,
    };
    try {
      await (edition ? etat.mettreAJour(edition.id, corps) : etat.creer(corps));
      setOuvert(false);
    } catch (cause) {
      setErreur(cause instanceof Error ? cause.message : t.etats.erreur);
    }
  };

  /**
   * Fige la meteo du site de la ruche visitee (US-091).
   *
   * <p>C'est le geste que la colonne attendait : la valeur est recopiee du
   * fournisseur AU MOMENT de la visite, avec sa source, puis ne bouge plus.
   * Aller la rechercher six mois plus tard donnerait la valeur reconstituee
   * d'aujourd'hui — et rendrait fausse la correlation meteo x production
   * qu'elle sert precisement a etablir.
   */
  const releverMeteo = async () => {
    const ruche = parcRuches.find((r) => String(r.id) === rucheId);
    if (!ruche) {
      setErreur(t.etats.champsRequis);
      return;
    }
    try {
      // Horizon zero : on veut l'instantane, pas la prevision — c'est un releve.
      const meteo = await chargerMeteo(ruche.siteId, 0);
      setTemperature(String(meteo.temperatureCelsius));
      setHumidite(meteo.humiditePourcent != null ? String(meteo.humiditePourcent) : '');
      setVent(meteo.ventKmh != null ? String(meteo.ventKmh) : '');
      // La source vient du serveur ; toute autre valeur serait une saisie.
      setSourceMeteo(meteo.source === 'open-meteo' || meteo.source === 'simulation'
        ? meteo.source
        : 'saisie');
    } catch (cause) {
      setErreur(cause instanceof Error ? cause.message : t.etats.erreur);
    }
  };

  /** Une saisie a la main reste une saisie : la source le dit. */
  const saisirMeteo = (poser: (valeur: string) => void) => (valeur: string) => {
    poser(valeur);
    setSourceMeteo('saisie');
  };

  const ajouterPathologie = () => {
    // Une pathologie ne se constate qu'une fois par visite : la base porte la
    // meme contrainte, mais un doublon refuse APRES la saisie serait un refus
    // que rien n'annoncait.
    if (pathologies.some((x) => x.pathologie === pathologie)) {
      return;
    }
    setPathologies([...pathologies, { pathologie, gravite, note: null }]);
  };

  const ajouterUnePhoto = async () => {
    if (!edition || urlPhoto.trim() === '') return;
    try {
      await ajouterPhoto(edition.id, { url: urlPhoto, legende: legende.trim() === '' ? null : legende });
      setPhotos(await listerPhotos(edition.id));
      setUrlPhoto('');
      setLegende('');
      etat.recharger();
    } catch (cause) {
      setErreur(cause instanceof Error ? cause.message : t.etats.erreur);
    }
  };

  const retirerPhoto = async (photo: Photo) => {
    if (!edition) return;
    await supprimerPhoto(edition.id, photo.id);
    setPhotos(await listerPhotos(edition.id));
    etat.recharger();
  };

  return (
    <CorpsSection
      titre={t.onglets.visites}
      sousTitre={t.soustitres.visites}
      etat={etat} onNouveau={() => ouvrir(null)}>
      {etat.elements.length > 0 && (
        <Table colonnes={colonnes} elements={etat.elements} onModifier={ouvrir} onSupprimer={(e) => void etat.supprimer(e.id)} />
      )}
      {ouvert && (
        <Modale titre={t.onglets.visites} onFermer={() => setOuvert(false)}>
          <form
            className="z-form"
            onSubmit={(e) => {
              e.preventDefault();
              void enregistrer();
            }}
          >
            <div className="z-form__grille">
              <ChampSelect libelle={t.champs.modele} valeur={rucheId} options={optRuches} onChange={setRucheId} requis />
              <ChampSelect libelle={t.visite.agent} valeur={agentId} options={optAgents} onChange={setAgentId} requis />
            </div>
            <ChampSelect libelle={t.visite.planning} valeur={planningId} options={optPlannings} onChange={setPlanningId} />
            <div className="z-form__grille">
              <ChampDate libelle={t.visite.date} valeur={dateVisite} onChange={setDateVisite} requis />
              <ChampHeure libelle={t.visite.heure} valeur={heureVisite} onChange={setHeureVisite} />
              <ChampNombre libelle={t.visite.duree} valeur={dureeMin} onChange={setDureeMin} pas="1" />
            </div>
            <ChampSelect libelle={t.visite.raison} valeur={raison} options={optRaison} onChange={(v) => setRaison(v as RaisonVisite)} />
            <ChampZone libelle={t.visite.constatations} valeur={constatations} onChange={setConstatations} />
            <ChampZone libelle={t.visite.actionsPrevues} valeur={actionsPrevues} onChange={setActionsPrevues} />
            <ChampZone libelle={t.visite.actionsEffectuees} valeur={actionsEffectuees} onChange={setActionsEffectuees} />
            <ChampZone libelle={t.visite.recommandations} valeur={recommandations} onChange={setRecommandations} />
            <div className="z-form__grille">
              <ChampSelect libelle={t.visite.effectif} valeur={effectif} options={optEffectif} onChange={setEffectif} />
              <ChampSelect libelle={t.visite.sante} valeur={sante} options={optSante} onChange={setSante} />
              <ChampSelect libelle={t.visite.productivite} valeur={productivite} options={optProd} onChange={setProductivite} />
            </div>

            <fieldset className="z-composition">
              <legend className="z-champ__libelle">{t.visite.observation}</legend>
              <div className="z-form__grille">
                <ChampSelect
                  libelle={t.visite.couvainOeufs}
                  valeur={couvainOeufs}
                  options={optTroisEtats}
                  onChange={setCouvainOeufs}
                />
                <ChampSelect
                  libelle={t.visite.couvainLarves}
                  valeur={couvainLarves}
                  options={optTroisEtats}
                  onChange={setCouvainLarves}
                />
                <ChampSelect
                  libelle={t.visite.couvainOpercule}
                  valeur={couvainOpercule}
                  options={optTroisEtats}
                  onChange={setCouvainOpercule}
                />
              </div>
              <div className="z-form__grille">
                <ChampSelect
                  libelle={t.visite.motifPonte}
                  valeur={motifPonte}
                  options={optMotifPonte}
                  onChange={setMotifPonte}
                />
                <ChampSelect
                  libelle={t.visite.reineVue}
                  valeur={reineVue}
                  options={optTroisEtats}
                  onChange={setReineVue}
                />
                <ChampSelect
                  libelle={t.visite.temperament}
                  valeur={temperament}
                  options={optTemperament}
                  onChange={setTemperament}
                />
              </div>
              <div className="z-form__grille">
                <ChampNombre
                  libelle={t.visite.cellulesRoyales}
                  valeur={cellulesRoyales}
                  onChange={setCellulesRoyales}
                  pas="1"
                  min={0}
                />
                {/* La cause n'a de sens qu'avec des cellules — la demander avant
                    inviterait a une contradiction que la base refuse. */}
                {Number(cellulesRoyales) > 0 && (
                  <ChampSelect
                    libelle={t.visite.cellulesRoyalesCause}
                    valeur={causeCellules}
                    options={optCauseCellules}
                    onChange={setCauseCellules}
                  />
                )}
              </div>
              <div className="z-form__grille">
                <ChampNombre
                  libelle={t.visite.cadresCouvain}
                  valeur={cadresCouvain}
                  onChange={setCadresCouvain}
                  pas="1"
                  min={0}
                  max={40}
                />
                <ChampNombre
                  libelle={t.visite.cadresMiel}
                  valeur={cadresMiel}
                  onChange={setCadresMiel}
                  pas="1"
                  min={0}
                  max={40}
                />
                <ChampNombre
                  libelle={t.visite.cadresPollen}
                  valeur={cadresPollen}
                  onChange={setCadresPollen}
                  pas="1"
                  min={0}
                  max={40}
                />
              </div>
            </fieldset>

            <fieldset className="z-composition">
              <legend className="z-champ__libelle">{t.visite.meteo}</legend>
              <div className="z-form__grille">
                <ChampNombre
                  libelle={t.visite.temperature}
                  valeur={temperature}
                  onChange={saisirMeteo(setTemperature)}
                />
                <ChampNombre
                  libelle={t.visite.humidite}
                  valeur={humidite}
                  onChange={saisirMeteo(setHumidite)}
                  pas="1"
                  min={0}
                  max={100}
                />
                <ChampNombre
                  libelle={t.visite.vent}
                  valeur={vent}
                  onChange={saisirMeteo(setVent)}
                  min={0}
                />
              </div>
              <div className="z-form__grille">
                <p className="z-info">
                  {t.visite.sourceMeteo} :{' '}
                  {sourceMeteo === null ? '—' : t.visite.sourcesMeteo[sourceMeteo]}
                </p>
                <div className="z-champ z-champ--aligne-bas">
                  <Bouton
                    variante="secondaire"
                    onClick={() => void releverMeteo()}
                    disabled={rucheId === ''}
                  >
                    {t.visite.meteo}
                  </Bouton>
                </div>
              </div>
            </fieldset>

            <fieldset className="z-composition">
              <legend className="z-champ__libelle">{t.visite.pathologiesConstatees}</legend>
              {pathologies.map((x) => (
                <div key={x.pathologie} className="z-composition__hausse">
                  <span className="z-photo-ref">
                    {t.sanitaire.pathologies[x.pathologie]} —{' '}
                    {t.sanitaire.gravites[x.gravite ?? 'suspectee']}
                  </span>
                  <Bouton
                    variante="fantome"
                    onClick={() =>
                      setPathologies(pathologies.filter((y) => y.pathologie !== x.pathologie))
                    }
                  >
                    ✕
                  </Bouton>
                </div>
              ))}
              <div className="z-form__grille">
                <ChampSelect
                  libelle={t.visite.pathologie}
                  valeur={pathologie}
                  options={optPathologie}
                  onChange={(v) => setPathologie(v as Pathologie)}
                />
                <ChampSelect
                  libelle={t.visite.gravite}
                  valeur={gravite}
                  options={optGravite}
                  onChange={(v) => setGravite(v as GravitePathologie)}
                />
                <div className="z-champ z-champ--aligne-bas">
                  <Bouton variante="secondaire" onClick={ajouterPathologie}>
                    + {t.visite.ajouterPathologie}
                  </Bouton>
                </div>
              </div>
            </fieldset>

            {edition && (
              <fieldset className="z-composition">
                <legend className="z-champ__libelle">{t.visite.photos}</legend>
                {photos.map((p) => (
                  <div key={p.id} className="z-composition__hausse">
                    <span className="z-photo-ref">{p.legende ?? p.url}</span>
                    <Bouton variante="fantome" onClick={() => void retirerPhoto(p)}>
                      ✕
                    </Bouton>
                  </div>
                ))}
                <div className="z-form__grille">
                  <ChampTexte libelle={t.visite.url} valeur={urlPhoto} onChange={setUrlPhoto} />
                  <ChampTexte libelle={t.visite.legende} valeur={legende} onChange={setLegende} />
                </div>
                <Bouton variante="secondaire" onClick={() => void ajouterUnePhoto()}>
                  + {t.actions.ajouterPhoto}
                </Bouton>
              </fieldset>
            )}

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
