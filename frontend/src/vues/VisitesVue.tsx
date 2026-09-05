import { useEffect, useState, type ReactElement } from 'react';
import {
  agents,
  ajouterPhoto,
  chargerMeteo,
  deposerBrouillon,
  effacerBrouillon,
  listerBrouillons,
  listerPhotos,
  plannings,
  recupererPoints,
  ruches,
  supprimerPhoto,
  telechargerRapportVisite,
  visites,
} from '../api/client';
import { gabarits as apiGabarits } from '../api/client';
import type {
  Agent,
  Brouillon,
  CauseCellules,
  Gabarit as GabaritCarnet,
  EffectifQualitatif,
  EtatSante,
  GravitePathologie,
  MotifPonte,
  Pathologie,
  PathologieCorps,
  Photo,
  Planning,
  PointReferentiel,
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
  ChampZone,
  Colonne,
  Modale,
  Option,
  Table,
} from '../ui/composants';
import { BoutonDictee } from '../voix/BoutonDictee';
import { GrilleCarnet } from '../carnet/GrilleCarnet';
import {
  SAISIE_VIDE,
  depuisReleves,
  pointsDuGabarit,
  versReleves,
  type SaisieCarnet,
} from '../carnet/points';
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
  // Le carnet paramétrable (SPRINT-28) : le référentiel FERMÉ des points, et
  // le gabarit que l'exploitation a choisi. Sans gabarit, la grille du
  // SPRINT-20 s'applique seule — c'est-à-dire exactement l'écran d'avant.
  const [referentiel, setReferentiel] = useState<PointReferentiel[]>([]);
  const [gabaritCarnet, setGabaritCarnet] = useState<GabaritCarnet | null>(null);
  const [saisieCarnet, setSaisieCarnet] = useState<SaisieCarnet>(SAISIE_VIDE);
  const [pathologies, setPathologies] = useState<PathologieCorps[]>([]);
  const [pathologie, setPathologie] = useState<Pathologie>('varroose');
  const [gravite, setGravite] = useState<GravitePathologie>('suspectee');
  const [parcRuches, setParcRuches] = useState<Ruche[]>([]);
  const [photos, setPhotos] = useState<Photo[]>([]);
  const [urlPhoto, setUrlPhoto] = useState('');
  const [legende, setLegende] = useState('');
  const [erreur, setErreur] = useState<string | null>(null);
  // Brouillon reprenable d'un appareil a l'autre (SPRINT-24, lot C). La file de
  // mutations resout le trajet terrain -> serveur ; elle ne resout pas le trajet
  // telephone -> ordinateur, qui est celui que trois editeurs conseillent a
  // leurs utilisateurs de faire au stylo.
  const [brouillonTrouve, setBrouillonTrouve] = useState<Brouillon | null>(null);
  const [messageBrouillon, setMessageBrouillon] = useState<string | null>(null);
  // Note vocale : elle ne quitte JAMAIS l'appareil (voir le bloc du formulaire).
  const [noteVocale, setNoteVocale] = useState<string | null>(null);
  const [enregistreur, setEnregistreur] = useState<MediaRecorder | null>(null);

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

  // Le référentiel et le gabarit ne changent qu'au déploiement d'une migration
  // ou d'un réglage : une lecture au montage suffit, et un échec est sans
  // conséquence — la grille standard reste affichée.
  useEffect(() => {
    void recupererPoints().then(setReferentiel).catch(() => setReferentiel([]));
    void apiGabarits
      .lister()
      .then((liste) => setGabaritCarnet(liste.find((g) => g.parDefaut && g.actif) ?? null))
      .catch(() => setGabaritCarnet(null));
  }, []);

  const pointsCarnet = pointsDuGabarit(gabaritCarnet, referentiel);
  // Sections du noyau à afficher. Un gabarit absent les montre toutes : masquer
  // par défaut ferait disparaître la grille du SPRINT-20 chez une exploitation
  // qui n'a rien demandé.
  const montre = {
    couvain: gabaritCarnet?.noyauCouvain ?? true,
    reine: gabaritCarnet?.noyauReine ?? true,
    cadres: gabaritCarnet?.noyauCadres ?? true,
    temperament: gabaritCarnet?.noyauTemperament ?? true,
  };

  // Un brouillon existe-t-il pour cette ruche et cet agent ? La question ne se
  // pose qu'a la creation : reprendre un brouillon par-dessus une visite deja
  // enregistree ecraserait le registre avec une saisie abandonnee.
  useEffect(() => {
    if (!ouvert || edition || rucheId === '' || agentId === '') {
      setBrouillonTrouve(null);
      return;
    }
    void listerBrouillons(Number(agentId))
      .then((liste) => setBrouillonTrouve(liste.find((b) => b.rucheId === Number(rucheId)) ?? null))
      .catch(() => setBrouillonTrouve(null));
  }, [ouvert, edition, rucheId, agentId]);

  /**
   * Note vocale, enregistree LOCALEMENT (SPRINT-24, lot C).
   *
   * <p>Elle ne part pas au serveur, et ce n'est pas un raccourci : le depot n'a
   * aucun stockage binaire — `photo.url` ne porte qu'une adresse, et il en va de
   * meme des ordonnances veterinaires. Encoder de l'audio en base64 dans un
   * champ texte aurait fabrique un stockage de fichiers clandestin, invisible en
   * revue et impossible a purger. La note sert donc a ce que trois editeurs
   * conseillent — noter au rucher, ressaisir au retour — sur CET appareil.
   */
  const demarrerNote = async () => {
    try {
      const flux = await navigator.mediaDevices.getUserMedia({ audio: true });
      const media = new MediaRecorder(flux);
      const morceaux: Blob[] = [];
      media.ondataavailable = (evenement) => morceaux.push(evenement.data);
      media.onstop = () => {
        setNoteVocale(URL.createObjectURL(new Blob(morceaux, { type: media.mimeType })));
        flux.getTracks().forEach((piste) => piste.stop());
      };
      media.start();
      setEnregistreur(media);
    } catch {
      // Micro refuse, absent, ou page non securisee : le dire, sinon le bouton
      // passe pour casse.
      setErreur(t.horsLigne.noteVocaleImpossible);
    }
  };

  const arreterNote = () => {
    enregistreur?.stop();
    setEnregistreur(null);
  };

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
    setSaisieCarnet(v ? depuisReleves(v.points) : SAISIE_VIDE);
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
    setMessageBrouillon(null);
    setNoteVocale(null);
    setErreur(null);
    setOuvert(true);
  };

  const enregistrer = async () => {
    if (rucheId === '' || agentId === '') {
      setErreur(t.etats.champsRequis);
      return;
    }
    const corps: VisiteCorps = construireCorps();
    try {
      await (edition
        ? etat.mettreAJour(edition.id, corps, { 'X-Zumm-Version': edition.majLe })
        : etat.creer(corps));
      // La visite est versee au registre : le brouillon a fait son office. Le
      // garder ferait reapparaitre demain une saisie deja enregistree.
      if (brouillonTrouve) {
        void effacerBrouillon(brouillonTrouve.id).catch(() => undefined);
        setBrouillonTrouve(null);
      }
      setOuvert(false);
    } catch (cause) {
      setErreur(cause instanceof Error ? cause.message : t.etats.erreur);
    }
  };

  /**
   * Le formulaire, tel qu'il part au serveur — ou dans un brouillon.
   *
   * <p>Extrait de `enregistrer` au SPRINT-24 : le brouillon et l'enregistrement
   * doivent produire EXACTEMENT la meme forme, sans quoi une saisie reprise
   * demain ne serait plus celle qu'on avait laissee.
   */
  const construireCorps = (): VisiteCorps => {
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
    return {
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
      // Seuls les points REGARDÉS partent. Compléter la liste avec des « non »
      // pour les cases non touchées ferait dire à l'inspection ce qu'elle n'a
      // pas dit — et c'est sur cette différence que reposent les statistiques.
      points: versReleves(pointsCarnet, saisieCarnet),
    };
  };

  /**
   * Depose la saisie en cours comme brouillon (SPRINT-24, lot C).
   *
   * <p>Le contenu part tel quel, sans validation : une saisie en cours a le
   * droit d'etre incomplete, et la refuser tant qu'elle ne l'est pas ferait
   * perdre exactement ce qu'on cherche a sauver. C'est aussi pour cela que le
   * brouillon n'est PAS une visite a l'etat brouillon — le registre n'a pas a
   * connaitre les phrases inachevees.
   */
  const enregistrerBrouillon = async () => {
    if (rucheId === '' || agentId === '') {
      setErreur(t.etats.champsRequis);
      return;
    }
    try {
      const depose = await deposerBrouillon({
        agentId: Number(agentId),
        rucheId: Number(rucheId),
        contenu: JSON.stringify(construireCorps()),
        // `navigator.userAgent` est illisible ; la plateforme suffit a se
        // reconnaitre entre un telephone et le poste du local.
        appareil: navigator.platform || null,
      });
      setBrouillonTrouve(depose);
      setMessageBrouillon(
        gabarit(t.horsLigne.brouillonEnregistre, { instant: f.dateHeure(depose.majLe) }),
      );
    } catch (cause) {
      setErreur(cause instanceof Error ? cause.message : t.etats.erreur);
    }
  };

  /** Repose le formulaire tel qu'il avait ete laisse sur l'autre appareil. */
  const reprendreBrouillon = (brouillon: Brouillon) => {
    let corps: VisiteCorps;
    try {
      corps = JSON.parse(brouillon.contenu) as VisiteCorps;
    } catch {
      // Contenu illisible : on ne le devine pas, et on le dit plutot que de
      // vider silencieusement le formulaire.
      setErreur(t.etats.erreur);
      return;
    }
    setDateVisite(corps.dateVisite ?? '');
    setHeureVisite(corps.heureVisite ?? '');
    setDureeMin(corps.dureeMin != null ? String(corps.dureeMin) : '');
    setRaison(corps.raison ?? 'controle');
    setConstatations(corps.constatations ?? '');
    setActionsPrevues(corps.actionsPrevues ?? '');
    setActionsEffectuees(corps.actionsEffectuees ?? '');
    setRecommandations(corps.recommandations ?? '');
    setEffectif(corps.effectifQualitatif ?? '');
    setSante(corps.etatSante ?? '');
    setProductivite(corps.productivite != null ? String(corps.productivite) : '');
    setPathologies(corps.pathologies ?? []);
    setMessageBrouillon(null);
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
            {/* La dictée (SPRINT-30) là où le texte est long et où l'on porte des
                gants. Le texte s'AJOUTE au champ et se relit avant validation :
                une transcription approximative validée sans relecture vaut moins
                qu'une case cochée. */}
            <BoutonDictee
              valeur={constatations}
              onChange={setConstatations}
              libelle={t.dictee.dicter}
            />
            <ChampZone libelle={t.visite.actionsPrevues} valeur={actionsPrevues} onChange={setActionsPrevues} />
            <ChampZone libelle={t.visite.actionsEffectuees} valeur={actionsEffectuees} onChange={setActionsEffectuees} />
            <ChampZone libelle={t.visite.recommandations} valeur={recommandations} onChange={setRecommandations} />
            <div className="z-form__grille">
              <ChampSelect libelle={t.visite.effectif} valeur={effectif} options={optEffectif} onChange={setEffectif} />
              <ChampSelect libelle={t.visite.sante} valeur={sante} options={optSante} onChange={setSante} />
              <ChampSelect libelle={t.visite.productivite} valeur={productivite} options={optProd} onChange={setProductivite} />
            </div>

            {/* Les quatre sections de la grille du SPRINT-20 suivent le gabarit
                (SPRINT-28). Éteindre une section la MASQUE ; les colonnes
                restent, et une visite déjà saisie garde ce qu'elle portait. */}
            <fieldset className="z-composition">
              <legend className="z-champ__libelle">{t.visite.observation}</legend>
              {montre.couvain && (
                <>
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
                  </div>
                </>
              )}
              {montre.reine && (
                <div className="z-form__grille">
                  <ChampSelect
                    libelle={t.visite.reineVue}
                    valeur={reineVue}
                    options={optTroisEtats}
                    onChange={setReineVue}
                  />
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
              )}
              {montre.cadres && (
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
              )}
              {montre.temperament && (
                <div className="z-form__grille">
                  <ChampSelect
                    libelle={t.visite.temperament}
                    valeur={temperament}
                    options={optTemperament}
                    onChange={setTemperament}
                  />
                </div>
              )}
            </fieldset>

            <GrilleCarnet
              points={pointsCarnet}
              saisie={saisieCarnet}
              onChange={setSaisieCarnet}
            />

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

            {brouillonTrouve && !edition && (
              <div className="z-rappels" role="status">
                <strong>{t.horsLigne.brouillons}</strong>{' '}
                {gabarit(t.horsLigne.brouillonDe, {
                  instant: f.dateHeure(brouillonTrouve.majLe),
                  appareil: brouillonTrouve.appareil ?? t.horsLigne.appareilInconnu,
                })}
                <br />
                <button
                  type="button"
                  className="z-lien"
                  onClick={() => reprendreBrouillon(brouillonTrouve)}
                >
                  {t.horsLigne.reprendre}
                </button>
              </div>
            )}

            <fieldset className="z-composition">
              <legend className="z-champ__libelle">{t.horsLigne.noteVocale}</legend>
              {/* La note NE QUITTE PAS l'appareil : le serveur ne stocke aucun
                  fichier binaire, et l'encoder en base64 dans un champ texte
                  aurait fabrique un stockage clandestin. */}
              <p className="z-info">{t.horsLigne.noteVocaleAide}</p>
              {noteVocale && (
                /* Pas de piste de sous-titres, et ce n'est pas un oubli : la
                   note est enregistree PAR l'utilisateur POUR lui-meme, sur son
                   appareil, et rien n'en produit de transcription — la question
                   de l'ou tourne la reconnaissance vocale est ouverte, et
                   tranchee nulle part (decision D4 du plan de couverture). Le
                   jour ou un brouillon transporterait l'audio d'une personne a
                   une autre, la piste deviendrait obligatoire. */
                // eslint-disable-next-line jsx-a11y/media-has-caption
                <audio controls src={noteVocale} />
              )}
              <div className="z-form__actions">
                {enregistreur ? (
                  <Bouton variante="secondaire" onClick={arreterNote}>
                    {t.horsLigne.arreterNote}
                  </Bouton>
                ) : (
                  <Bouton variante="secondaire" onClick={() => void demarrerNote()}>
                    {t.horsLigne.enregistrerNote}
                  </Bouton>
                )}
                {noteVocale && (
                  <Bouton
                    variante="fantome"
                    onClick={() => {
                      URL.revokeObjectURL(noteVocale);
                      setNoteVocale(null);
                    }}
                  >
                    {t.horsLigne.effacerNote}
                  </Bouton>
                )}
              </div>
            </fieldset>

            {messageBrouillon && (
              <p className="z-info" role="status">
                {messageBrouillon}
              </p>
            )}
            {erreur && <p className="z-form__erreur">{erreur}</p>}
            <div className="z-form__actions">
              <Bouton variante="fantome" onClick={() => setOuvert(false)}>
                {t.actions.annuler}
              </Bouton>
              {/* Le brouillon est un geste SECONDAIRE : il sauve une saisie en
                  cours, il ne la verse pas au registre. */}
              <Bouton variante="secondaire" onClick={() => void enregistrerBrouillon()}>
                {t.horsLigne.enregistrerBrouillon}
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
