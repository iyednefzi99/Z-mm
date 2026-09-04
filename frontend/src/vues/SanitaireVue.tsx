import { useEffect, useState, type ReactElement } from 'react';
import {
  agents,
  enregistrerComptageVarroa,
  enregistrerNourrissement,
  enregistrerTraitement,
  listerCarencesEnCours,
  listerComptagesVarroa,
  listerNourrissements,
  listerTraitements,
  ruches,
  supprimerComptageVarroa,
  supprimerNourrissement,
  supprimerTraitement,
  traiterEnLot,
} from '../api/client';
import type {
  Agent,
  CibleTraitement,
  ComptageVarroa,
  MethodeVarroa,
  MotifNourrissement,
  Nourrissement,
  RapportLot,
  Ruche,
  Traitement,
  TypeAliment,
  UniteDose,
  UniteQuantite,
} from '../api/types';
import {
  CIBLES_TRAITEMENT,
  METHODES_VARROA,
  MOTIFS_NOURRISSEMENT,
  TYPES_ALIMENT,
  UNITES_DOSE,
  UNITES_QUANTITE,
  parLange,
} from '../api/types';
import { gabarit } from '../i18n/console';
import { useFormats, useT } from '../i18n/langue';
import { messageErreur } from '../hooks';
import {
  Bouton,
  ChampDate,
  ChampNombre,
  ChampSelect,
  ChampTexte,
  ChampZone,
  Colonne,
  EtatVide,
  Option,
  Pastille,
  Table,
  type TonPastille,
} from '../ui/composants';

/** Les trois registres de l'écran. Un seul est monté à la fois. */
type Volet = 'traitements' | 'nourrissements' | 'varroa';

const VOLETS: readonly Volet[] = ['traitements', 'nourrissements', 'varroa'];

/**
 * Position de la pastille glissante, par volet.
 *
 * <p>Une table plutot qu'un calcul d'index : la classe est ecrite en toutes
 * lettres, donc greppable depuis la feuille de style — un `is-milieu` qui
 * n'existerait plus en CSS se verrait ici.
 */
const POSITION_PASTILLE: Record<Volet, string> = {
  traitements: '',
  nourrissements: ' is-milieu',
  varroa: ' is-droite',
};

/**
 * Registre sanitaire d'une ruche : traitements, nourrissements, varroa (SPRINT-20).
 *
 * <p><strong>Un écran, trois registres — et non trois onglets de navigation.</strong>
 * Les trois actes se saisissent dans la même séquence, souvent la même minute :
 * on compte le varroa, on traite, on nourrit derrière. Les répartir dans trois
 * destinations aurait imposé trois fois le choix de la ruche pour une seule
 * visite au rucher.
 *
 * <p><strong>Les carences en tête, hors du choix de ruche.</strong> C'est la
 * seule question qui se pose à l'échelle de l'exploitation — « que puis-je
 * récolter aujourd'hui » — et la seule dont la réponse doit se voir sans avoir
 * rien sélectionné. Le motif y est affiché avec la date de fin : un apiculteur à
 * qui l'on dit « non » sans dire « jusqu'à quand » contourne.
 *
 * <p><strong>Rien ne se modifie ici, on saisit et on supprime.</strong> Corriger
 * un traitement déjà consigné reviendrait à réécrire après coup un registre
 * d'élevage — précisément ce qu'un contrôle vient vérifier. Le serveur n'expose
 * donc aucun `PUT`, et le tableau ne propose pas « Modifier ».
 */
export function SanitaireVue(): ReactElement {
  const t = useT();
  const f = useFormats();
  const s = t.sanitaire;

  const [optRuches, setOptRuches] = useState<Option[]>([]);
  const [ruchesConnues, setRuchesConnues] = useState<Ruche[]>([]);
  const [optAgents, setOptAgents] = useState<Option[]>([]);
  const [rucheId, setRucheId] = useState('');
  const [agentId, setAgentId] = useState('');
  const [volet, setVolet] = useState<Volet>('traitements');
  const [carences, setCarences] = useState<Traitement[]>([]);
  const [traitements, setTraitements] = useState<Traitement[]>([]);
  const [nourrissements, setNourrissements] = useState<Nourrissement[]>([]);
  const [comptages, setComptages] = useState<ComptageVarroa[]>([]);
  const [erreur, setErreur] = useState<string | null>(null);
  // Rapport de la derniere operation de lot, affiche tel quel : les refus
  // motives sont ce qui permet de reprendre trois ruches au lieu de quarante.
  const [rapport, setRapport] = useState<RapportLot | null>(null);

  /** Ruche actuellement selectionnee, pour retrouver son rucher. */
  const rucheChoisie = (): Ruche | undefined =>
    ruchesConnues.find((r) => String(r.id) === rucheId);

  // Traitement
  const [produit, setProduit] = useState('');
  const [substance, setSubstance] = useState('');
  const [cible, setCible] = useState<CibleTraitement>('varroa');
  const [dose, setDose] = useState('');
  const [doseUnite, setDoseUnite] = useState('');
  const [dateDebut, setDateDebut] = useState('');
  const [dateFin, setDateFin] = useState('');
  const [carenceJours, setCarenceJours] = useState('');
  const [ordonnance, setOrdonnance] = useState('');
  const [noteTraitement, setNoteTraitement] = useState('');

  // Nourrissement
  const [dateApport, setDateApport] = useState('');
  const [aliment, setAliment] = useState<TypeAliment>('sirop_1_1');
  const [quantite, setQuantite] = useState('');
  const [quantiteUnite, setQuantiteUnite] = useState<UniteQuantite>('kg');
  const [motif, setMotif] = useState('');
  const [noteNourrissement, setNoteNourrissement] = useState('');

  // Comptage de varroa
  const [dateComptage, setDateComptage] = useState('');
  const [methode, setMethode] = useState<MethodeVarroa>('lange');
  const [varroasComptes, setVarroasComptes] = useState('');
  const [abeilles, setAbeilles] = useState('');
  const [jours, setJours] = useState('');
  const [noteComptage, setNoteComptage] = useState('');

  const vide: Option = { valeur: '', libelle: t.champs.aucun };
  const optCible: Option[] = CIBLES_TRAITEMENT.map((c) => ({ valeur: c, libelle: s.cibles[c] }));
  const optDoseUnite: Option[] = [
    vide,
    ...UNITES_DOSE.map((u) => ({ valeur: u, libelle: s.unitesDose[u] })),
  ];
  const optAliment: Option[] = TYPES_ALIMENT.map((a) => ({ valeur: a, libelle: s.aliments[a] }));
  const optQuantiteUnite: Option[] = UNITES_QUANTITE.map((u) => ({
    valeur: u,
    libelle: s.unitesQuantite[u],
  }));
  const optMotif: Option[] = [
    vide,
    ...MOTIFS_NOURRISSEMENT.map((m) => ({ valeur: m, libelle: s.motifs[m] })),
  ];
  const optMethode: Option[] = METHODES_VARROA.map((m) => ({
    valeur: m,
    libelle: s.methodes[m],
  }));

  /** Le verdict porte une couleur, mais jamais elle seule — la pastille garde son mot. */
  const tonVerdict = (comptage: ComptageVarroa): TonPastille => {
    if (comptage.verdict === 'traiter') return 'danger';
    if (comptage.verdict === 'surveiller') return 'attention';
    return comptage.verdict === 'faible' ? 'succes' : 'neutre';
  };

  const colonnesTraitement: Colonne<Traitement>[] = [
    { entete: s.dateDebut, rendu: (x) => f.date(x.dateDebut) },
    { entete: s.produit, rendu: (x) => x.produit },
    { entete: s.cible, rendu: (x) => s.cibles[x.cible] },
    {
      entete: s.dose,
      rendu: (x) =>
        x.dose == null || x.doseUnite == null
          ? '—'
          : `${f.nombre(x.dose, 2)} ${s.unitesDose[x.doseUnite]}`,
    },
    {
      entete: s.dateRetrait,
      rendu: (x) =>
        x.sousCarence ? (
          <Pastille ton="danger">
            {gabarit(s.recolteAPartirDu, { date: f.date(x.dateRetrait) })}
          </Pastille>
        ) : (
          f.date(x.dateRetrait)
        ),
    },
    { entete: s.agent, rendu: (x) => x.agentNom },
  ];

  const colonnesNourrissement: Colonne<Nourrissement>[] = [
    { entete: s.dateApport, rendu: (x) => f.date(x.dateApport) },
    { entete: s.typeAliment, rendu: (x) => s.aliments[x.typeAliment] },
    {
      entete: s.quantite,
      rendu: (x) => `${f.nombre(x.quantite, 2)} ${s.unitesQuantite[x.quantiteUnite]}`,
    },
    { entete: s.motif, rendu: (x) => (x.motif ? s.motifs[x.motif] : '—') },
    { entete: s.agent, rendu: (x) => x.agentNom },
  ];

  const colonnesComptage: Colonne<ComptageVarroa>[] = [
    { entete: s.dateComptage, rendu: (x) => f.date(x.dateComptage) },
    { entete: s.methode, rendu: (x) => s.methodes[x.methode] },
    { entete: s.varroasComptes, rendu: (x) => String(x.varroasComptes) },
    {
      // Le taux ne sort JAMAIS sans son unité : « 3,5 » ne dit pas s'il s'agit de
      // varroas par jour ou pour cent abeilles, et le seuil n'est pas le même.
      entete: s.taux,
      rendu: (x) =>
        x.taux == null ? '—' : `${f.nombre(x.taux, 2)} ${s.unitesTaux[x.tauxUnite]}`,
    },
    {
      entete: s.verdict,
      rendu: (x) => <Pastille ton={tonVerdict(x)}>{s.verdicts[x.verdict]}</Pastille>,
    },
  ];

  const signaler = (cause: unknown) =>
    setErreur(messageErreur(cause, t.etats.serviceIndisponible));

  useEffect(() => {
    void ruches
      .lister()
      .then((liste: Ruche[]) => {
        setOptRuches([vide, ...liste.map((r) => ({ valeur: String(r.id), libelle: r.modele }))]);
        // La liste BRUTE est conservee en plus des options : une operation de
        // lot a besoin du rucher de la ruche choisie, qu'une Option ne porte pas.
        setRuchesConnues(liste);
      })
      .catch(() => setOptRuches([vide]));
    void agents
      .lister()
      .then((liste: Agent[]) =>
        setOptAgents([vide, ...liste.map((a) => ({ valeur: String(a.id), libelle: a.nom }))]),
      )
      .catch(() => setOptAgents([vide]));
    void listerCarencesEnCours().then(setCarences).catch(() => setCarences([]));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const recharger = (id: string) => {
    if (id === '') {
      setTraitements([]);
      setNourrissements([]);
      setComptages([]);
      return;
    }
    const ruche = Number(id);
    void listerTraitements(ruche).then(setTraitements).catch(signaler);
    void listerNourrissements(ruche).then(setNourrissements).catch(signaler);
    void listerComptagesVarroa(ruche).then(setComptages).catch(signaler);
  };

  const choisirRuche = (id: string) => {
    setRucheId(id);
    setErreur(null);
    recharger(id);
  };

  /** Rafraîchit aussi le bandeau : un traitement saisi peut fermer la récolte. */
  const apresMutation = () => {
    recharger(rucheId);
    void listerCarencesEnCours().then(setCarences).catch(() => setCarences([]));
  };

  const texte = (valeur: string) => (valeur.trim() === '' ? null : valeur.trim());
  const nombre = (valeur: string) => (valeur === '' ? null : Number(valeur));

  /**
   * Le meme traitement sur tout le rucher (SPRINT-23, lot B).
   *
   * <p>Le rapport est affiche tel quel : un lot reussit rarement en entier, et
   * masquer les refus ferait croire a quarante traitements la ou il y en a
   * trente-sept.
   */
  const traiterLeRucher = async () => {
    const site = rucheChoisie()?.siteId;
    if (site === undefined || agentId === '' || produit.trim() === '' || dateDebut === '') {
      setErreur(t.etats.champsRequis);
      return;
    }
    setErreur(null);
    try {
      setRapport(
        await traiterEnLot({
          cible: { rucheIds: null, siteId: site },
          traitement: {
            rucheId: Number(rucheId),
            agentId: Number(agentId),
            visiteId: null,
            produit: produit.trim(),
            substanceActive: texte(substance),
            cible,
            dose: nombre(dose),
            doseUnite: doseUnite === '' ? null : (doseUnite as UniteDose),
            dateDebut,
            dateFin: dateFin === '' ? null : dateFin,
            delaiCarenceJours: nombre(carenceJours),
            ordonnance: texte(ordonnance),
            note: texte(noteTraitement),
          },
        }),
      );
      apresMutation();
    } catch (cause) {
      signaler(cause);
    }
  };

  const ajouterTraitement = async () => {
    if (rucheId === '' || agentId === '' || produit.trim() === '' || dateDebut === '') {
      setErreur(t.etats.champsRequis);
      return;
    }
    setErreur(null);
    setRapport(null);
    try {
      await enregistrerTraitement({
        rucheId: Number(rucheId),
        agentId: Number(agentId),
        visiteId: null,
        produit: produit.trim(),
        substanceActive: texte(substance),
        cible,
        dose: nombre(dose),
        doseUnite: doseUnite === '' ? null : (doseUnite as UniteDose),
        dateDebut,
        dateFin: dateFin === '' ? null : dateFin,
        delaiCarenceJours: nombre(carenceJours),
        ordonnance: texte(ordonnance),
        note: texte(noteTraitement),
      });
      setProduit('');
      setSubstance('');
      setDose('');
      setDoseUnite('');
      setDateFin('');
      setCarenceJours('');
      setOrdonnance('');
      setNoteTraitement('');
      apresMutation();
    } catch (cause) {
      signaler(cause);
    }
  };

  const ajouterNourrissement = async () => {
    if (rucheId === '' || agentId === '' || dateApport === '' || quantite === '') {
      setErreur(t.etats.champsRequis);
      return;
    }
    setErreur(null);
    try {
      await enregistrerNourrissement({
        rucheId: Number(rucheId),
        agentId: Number(agentId),
        visiteId: null,
        dateApport,
        typeAliment: aliment,
        quantite: Number(quantite),
        quantiteUnite,
        motif: motif === '' ? null : (motif as MotifNourrissement),
        note: texte(noteNourrissement),
      });
      setQuantite('');
      setMotif('');
      setNoteNourrissement('');
      apresMutation();
    } catch (cause) {
      signaler(cause);
    }
  };

  const ajouterComptage = async () => {
    if (rucheId === '' || agentId === '' || dateComptage === '' || varroasComptes === '') {
      setErreur(t.etats.champsRequis);
      return;
    }
    setErreur(null);
    try {
      await enregistrerComptageVarroa({
        rucheId: Number(rucheId),
        agentId: Number(agentId),
        visiteId: null,
        dateComptage,
        methode,
        varroasComptes: Number(varroasComptes),
        // Chaque méthode porte SON dénominateur et refuse celui de l'autre : le
        // formulaire n'affiche que le bon, et n'envoie jamais les deux.
        abeillesEchantillon: parLange(methode) ? null : nombre(abeilles),
        joursExposition: parLange(methode) ? nombre(jours) : null,
        note: texte(noteComptage),
      });
      setVarroasComptes('');
      setAbeilles('');
      setJours('');
      setNoteComptage('');
      apresMutation();
    } catch (cause) {
      signaler(cause);
    }
  };

  const retirer = (operation: Promise<void>) => {
    operation.then(apresMutation).catch(signaler);
  };

  return (
    <section className="z-section">
      <header className="z-section__entete">
        <div>
          <h1 className="z-section__titre">{t.onglets.sanitaire}</h1>
          <p className="z-section__soustitre">{t.soustitres.sanitaire}</p>
        </div>
      </header>

      {erreur && (
        <div className="z-erreur" role="alert">
          <span>{erreur}</span>
        </div>
      )}

      <fieldset className="z-composition">
        <legend className="z-champ__libelle">{s.carence}</legend>
        <p className="z-info">{s.carenceExplication}</p>
        {carences.length === 0 ? (
          <p className="z-info">{s.aucuneCarence}</p>
        ) : (
          <ul className="z-liste-simple">
            {carences.map((c) => (
              <li key={c.id}>
                <Pastille ton="danger">{c.rucheModele}</Pastille> {c.produit} —{' '}
                {gabarit(s.recolteAPartirDu, { date: f.date(c.dateRetrait) })}
              </li>
            ))}
          </ul>
        )}
      </fieldset>

      <div className="z-form__grille">
        <ChampSelect
          libelle={s.choisirRuche}
          valeur={rucheId}
          options={optRuches}
          onChange={choisirRuche}
        />
        <ChampSelect libelle={s.agent} valeur={agentId} options={optAgents} onChange={setAgentId} />
      </div>

      {rucheId === '' ? (
        <EtatVide titre={s.choisirRuche} />
      ) : (
        <>
          {/* Meme composant d'onglets que l'ecran de connexion, etendu a trois
              volets : la pastille glisse d'un ou deux crans plutot que de
              clignoter d'un registre a l'autre (transitions Zumm, famille
              « translation »). */}
          <div className="z-onglets z-onglets--trois" role="tablist" aria-label={t.onglets.sanitaire}>
            {VOLETS.map((nom) => (
              <button
                key={nom}
                type="button"
                role="tab"
                id={`onglet-${nom}`}
                aria-selected={volet === nom}
                aria-controls="volet-sanitaire"
                className={`z-onglets__item${volet === nom ? ' is-actif' : ''}`}
                onClick={() => setVolet(nom)}
              >
                {s[nom]}
              </button>
            ))}
            <span
              className={`z-onglets__pastille${POSITION_PASTILLE[volet]}`}
              aria-hidden="true"
            />
          </div>

          <div id="volet-sanitaire" role="tabpanel" aria-labelledby={`onglet-${volet}`}>

          {volet === 'traitements' && (
            <>
              {rapport && (
                <div className="z-rappels" role="status">
                  <strong>
                    {gabarit(t.lot.rapport, {
                      reussites: String(rapport.reussites.length),
                      demandees: String(rapport.demandees),
                    })}
                  </strong>
                  {rapport.echecs.length === 0 ? (
                    <> {t.lot.aucunEchec}</>
                  ) : (
                    <>
                      <br />
                      {t.lot.echecs}
                      <ul className="z-liste-simple">
                        {rapport.echecs.map((e) => (
                          <li key={e.rucheId}>
                            {e.rucheId} — {e.motif}
                          </li>
                        ))}
                      </ul>
                    </>
                  )}
                </div>
              )}
              <fieldset className="z-composition">
                <legend className="z-champ__libelle">{s.ajouterTraitement}</legend>
                <div className="z-form__grille">
                  <ChampTexte libelle={s.produit} valeur={produit} onChange={setProduit} />
                  <ChampTexte
                    libelle={s.substanceActive}
                    valeur={substance}
                    onChange={setSubstance}
                  />
                  <ChampSelect
                    libelle={s.cible}
                    valeur={cible}
                    options={optCible}
                    onChange={(v) => setCible(v as CibleTraitement)}
                  />
                </div>
                <div className="z-form__grille">
                  <ChampNombre libelle={s.dose} valeur={dose} onChange={setDose} />
                  <ChampSelect
                    libelle={s.doseUnite}
                    valeur={doseUnite}
                    options={optDoseUnite}
                    onChange={setDoseUnite}
                  />
                  <ChampNombre
                    libelle={s.delaiCarence}
                    valeur={carenceJours}
                    onChange={setCarenceJours}
                    pas="1"
                    min={0}
                    max={365}
                  />
                </div>
                <div className="z-form__grille">
                  <ChampDate libelle={s.dateDebut} valeur={dateDebut} onChange={setDateDebut} />
                  <ChampDate libelle={s.dateFin} valeur={dateFin} onChange={setDateFin} />
                  <ChampTexte
                    libelle={s.ordonnance}
                    valeur={ordonnance}
                    onChange={setOrdonnance}
                  />
                </div>
                <div className="z-form__grille">
                  <ChampZone
                    libelle={s.note}
                    valeur={noteTraitement}
                    onChange={setNoteTraitement}
                  />
                  <div className="z-champ z-champ--aligne-bas">
                    <Bouton variante="primaire" onClick={() => void ajouterTraitement()}>
                      {s.ajouterTraitement}
                    </Bouton>
                  </div>
                  {/* Le lot est SECONDAIRE à côté de l'acte unitaire : traiter
                      quarante colonies d'un coup est le geste utile, mais il ne
                      doit pas être celui qu'on déclenche par réflexe. */}
                  <div className="z-champ z-champ--aligne-bas">
                    <Bouton
                      variante="secondaire"
                      onClick={() => void traiterLeRucher()}
                      disabled={rucheChoisie() === undefined}
                    >
                      {t.actions.traiterLeRucher}
                    </Bouton>
                  </div>
                </div>
              </fieldset>

              {traitements.length > 0 ? (
                <Table
                  colonnes={colonnesTraitement}
                  elements={traitements}
                  onModifier={() => undefined}
                  onSupprimer={(x) => retirer(supprimerTraitement(x.id))}
                />
              ) : (
                <p className="z-info">{t.etats.vide}</p>
              )}
            </>
          )}

          {volet === 'nourrissements' && (
            <>
              <fieldset className="z-composition">
                <legend className="z-champ__libelle">{s.ajouterNourrissement}</legend>
                <div className="z-form__grille">
                  <ChampDate libelle={s.dateApport} valeur={dateApport} onChange={setDateApport} />
                  <ChampSelect
                    libelle={s.typeAliment}
                    valeur={aliment}
                    options={optAliment}
                    onChange={(v) => setAliment(v as TypeAliment)}
                  />
                  <ChampSelect
                    libelle={s.motif}
                    valeur={motif}
                    options={optMotif}
                    onChange={setMotif}
                  />
                </div>
                <div className="z-form__grille">
                  <ChampNombre libelle={s.quantite} valeur={quantite} onChange={setQuantite} />
                  <ChampSelect
                    libelle={s.quantiteUnite}
                    valeur={quantiteUnite}
                    options={optQuantiteUnite}
                    onChange={(v) => setQuantiteUnite(v as UniteQuantite)}
                  />
                </div>
                <div className="z-form__grille">
                  <ChampZone
                    libelle={s.note}
                    valeur={noteNourrissement}
                    onChange={setNoteNourrissement}
                  />
                  <div className="z-champ z-champ--aligne-bas">
                    <Bouton variante="primaire" onClick={() => void ajouterNourrissement()}>
                      {s.ajouterNourrissement}
                    </Bouton>
                  </div>
                </div>
              </fieldset>

              {nourrissements.length > 0 ? (
                <Table
                  colonnes={colonnesNourrissement}
                  elements={nourrissements}
                  onModifier={() => undefined}
                  onSupprimer={(x) => retirer(supprimerNourrissement(x.id))}
                />
              ) : (
                <p className="z-info">{t.etats.vide}</p>
              )}
            </>
          )}

          {volet === 'varroa' && (
            <>
              <fieldset className="z-composition">
                <legend className="z-champ__libelle">{s.ajouterComptage}</legend>
                <div className="z-form__grille">
                  <ChampDate
                    libelle={s.dateComptage}
                    valeur={dateComptage}
                    onChange={setDateComptage}
                  />
                  <ChampSelect
                    libelle={s.methode}
                    valeur={methode}
                    options={optMethode}
                    onChange={(v) => setMethode(v as MethodeVarroa)}
                  />
                  <ChampNombre
                    libelle={s.varroasComptes}
                    valeur={varroasComptes}
                    onChange={setVarroasComptes}
                    pas="1"
                    min={0}
                  />
                </div>
                <div className="z-form__grille">
                  {parLange(methode) ? (
                    <ChampNombre
                      libelle={s.joursExposition}
                      valeur={jours}
                      onChange={setJours}
                      pas="1"
                      min={1}
                    />
                  ) : (
                    <ChampNombre
                      libelle={s.abeillesEchantillon}
                      valeur={abeilles}
                      onChange={setAbeilles}
                      pas="1"
                      min={1}
                    />
                  )}
                  <p className="z-info">
                    {parLange(methode) ? s.denominateurLange : s.denominateurEchantillon}
                  </p>
                </div>
                <div className="z-form__grille">
                  <ChampZone libelle={s.note} valeur={noteComptage} onChange={setNoteComptage} />
                  <div className="z-champ z-champ--aligne-bas">
                    <Bouton variante="primaire" onClick={() => void ajouterComptage()}>
                      {s.ajouterComptage}
                    </Bouton>
                  </div>
                </div>
              </fieldset>

              {comptages.length > 0 ? (
                <Table
                  colonnes={colonnesComptage}
                  elements={comptages}
                  onModifier={() => undefined}
                  onSupprimer={(x) => retirer(supprimerComptageVarroa(x.id))}
                />
              ) : (
                <p className="z-info">{t.etats.vide}</p>
              )}
            </>
          )}
          </div>
        </>
      )}
    </section>
  );
}
