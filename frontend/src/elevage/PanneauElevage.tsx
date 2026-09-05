import { useEffect, useState, type ReactElement } from 'react';
import {
  chargerConformite,
  chargerGenealogie,
  chargerIndexGenetique,
  ouvrirDocumentElevage,
  reinesElevage,
  ruches,
  series,
} from '../api/client';
import type {
  CritereGenetique,
  DossierConformite,
  EtatReine,
  Genealogie,
  IndexGenetique,
  OrigineReine,
  ReineElevage,
  Ruche,
  SerieElevage,
} from '../api/types';
import { ETATS_REINE, METHODES_ELEVAGE, ORIGINES_ELEVAGE } from '../api/types';
import { messageErreur } from '../hooks';
import { useFormats, useT } from '../i18n/langue';
import {
  Bouton,
  ChampDate,
  ChampNombre,
  ChampSelect,
  ChampTexte,
  Colonne,
  Option,
  Table,
} from '../ui/composants';
import { ArbreLignee } from './ArbreLignee';

/**
 * Élevage : reines, lignées, séries et documents (SPRINT-29, lot D).
 *
 * <p>Un panneau à part, monté au-dessus du journal de la ruche dans
 * `ReinesVue`. Les deux se lisent ensemble — on ouvre une reine, on regarde ce
 * qui lui est arrivé — mais ils ne parlent pas de la même chose : l'un des
 * individus, l'autre des événements. Les mélanger dans un seul tableau aurait
 * fait perdre cette distinction, qui est tout le lot.
 */
export function PanneauElevage(): ReactElement {
  const t = useT();
  const f = useFormats();
  const [liste, setListe] = useState<ReineElevage[]>([]);
  const [lots, setLots] = useState<SerieElevage[]>([]);
  const [optRuches, setOptRuches] = useState<Option[]>([]);
  const [choisie, setChoisie] = useState<ReineElevage | null>(null);
  const [genealogie, setGenealogie] = useState<Genealogie | null>(null);
  const [index, setIndex] = useState<IndexGenetique | null>(null);
  const [dossier, setDossier] = useState<DossierConformite | null>(null);
  const [erreur, setErreur] = useState<string | null>(null);
  const [formulaire, setFormulaire] = useState(false);
  const [code, setCode] = useState('');
  const [origine, setOrigine] = useState<OrigineReine>('elevage');
  const [fournisseur, setFournisseur] = useState('');
  const [mereId, setMereId] = useState('');
  const [rucheId, setRucheId] = useState('');
  const [race, setRace] = useState('');
  const [annee, setAnnee] = useState('');
  const [introduction, setIntroduction] = useState('');
  const [etat, setEtat] = useState<EtatReine>('en_service');
  // Série en cours de saisie.
  const [serie, setSerie] = useState(false);
  const [nomSerie, setNomSerie] = useState('');
  const [dateGreffage, setDateGreffage] = useState('');
  const [greffees, setGreffees] = useState('');
  const [acceptees, setAcceptees] = useState('');
  const [periode, setPeriode] = useState({
    debut: new Date().getFullYear() + '-01-01',
    fin: new Date().getFullYear() + '-12-31',
  });

  const vide: Option = { valeur: '', libelle: t.champs.aucun };

  const recharger = () => {
    void reinesElevage.lister().then(setListe).catch(() => setListe([]));
    void series.lister().then(setLots).catch(() => setLots([]));
  };

  useEffect(() => {
    recharger();
    void ruches
      .lister()
      .then((l: Ruche[]) =>
        setOptRuches([vide, ...l.map((r) => ({ valeur: String(r.id), libelle: r.modele }))]),
      )
      .catch(() => setOptRuches([vide]));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const ouvrir = (reine: ReineElevage) => {
    setChoisie(reine);
    setGenealogie(null);
    setIndex(null);
    void chargerGenealogie(reine.id).then(setGenealogie).catch(() => setGenealogie(null));
    void chargerIndexGenetique(reine.id).then(setIndex).catch(() => setIndex(null));
  };

  const enregistrer = async () => {
    setErreur(null);
    try {
      await reinesElevage.creer({
        code: code.trim() === '' ? null : code.trim(),
        mereId: mereId === '' ? null : Number(mereId),
        rucheMereId: null,
        serieId: null,
        rucheId: rucheId === '' ? null : Number(rucheId),
        origine,
        // Le serveur l'efface hors achat ; ne pas l'envoyer du tout évite
        // d'afficher un champ rempli qui reviendra vide.
        fournisseur: origine === 'achat' && fournisseur.trim() !== '' ? fournisseur.trim() : null,
        race: race.trim() === '' ? null : race.trim(),
        anneeNaissance: annee === '' ? null : Number(annee),
        couleurMarquage: null,
        ailesClippees: null,
        dateGreffage: null,
        dateNaissance: null,
        dateFecondation: null,
        dateIntroduction: introduction === '' ? null : introduction,
        dateFin: null,
        statut: etat,
        note: null,
      });
      setFormulaire(false);
      setCode('');
      setMereId('');
      setRace('');
      setAnnee('');
      setIntroduction('');
      recharger();
    } catch (cause) {
      setErreur(messageErreur(cause, t.etats.erreur));
    }
  };

  const enregistrerSerie = async () => {
    setErreur(null);
    try {
      await series.creer({
        nom: nomSerie.trim(),
        dateGreffage,
        soucheId: null,
        rucheEleveuseId: null,
        methode: 'greffage',
        nbGreffees: Number(greffees),
        nbAcceptees: acceptees === '' ? null : Number(acceptees),
        nbNees: null,
        nbFecondees: null,
        note: null,
      });
      setSerie(false);
      setNomSerie('');
      setDateGreffage('');
      setGreffees('');
      setAcceptees('');
      recharger();
    } catch (cause) {
      setErreur(messageErreur(cause, t.etats.erreur));
    }
  };

  const colonnes: Colonne<ReineElevage>[] = [
    { entete: t.elevage.code, rendu: (r) => r.code ?? `#${r.id}` },
    { entete: t.elevage.origine, rendu: (r) => t.elevage.origines[r.origine] },
    { entete: t.elevage.mere, rendu: (r) => r.mereCode ?? '—' },
    { entete: t.elevage.rucheEnService, rendu: (r) => r.rucheModele ?? '—' },
    { entete: t.elevage.race, rendu: (r) => r.race ?? '—' },
    { entete: t.elevage.etat, rendu: (r) => t.elevage.etats[r.statut] },
  ];

  const colonnesSeries: Colonne<SerieElevage>[] = [
    { entete: t.elevage.nom, rendu: (s) => s.nom },
    { entete: t.elevage.dateGreffage, rendu: (s) => f.date(s.dateGreffage) },
    { entete: t.elevage.nbGreffees, rendu: (s) => String(s.nbGreffees) },
    {
      entete: t.elevage.tauxAcceptation,
      rendu: (s) => (s.tauxAcceptation === null ? '—' : `${s.tauxAcceptation} %`),
    },
    {
      entete: t.elevage.tauxReussite,
      rendu: (s) => (s.tauxReussite === null ? '—' : `${s.tauxReussite} %`),
    },
  ];

  return (
    <>
      {erreur !== null && (
        <div className="z-erreur" role="alert">
          {erreur}
        </div>
      )}

      <fieldset className="z-composition">
        <legend className="z-champ__libelle">{t.elevage.reines}</legend>
        {liste.length === 0 ? (
          <p className="z-info">{t.elevage.aucuneReine}</p>
        ) : (
          <Table
            colonnes={colonnes}
            elements={liste}
            onModifier={ouvrir}
            onSupprimer={(r) => void reinesElevage.supprimer(r.id).then(recharger)}
          />
        )}
        {!formulaire ? (
          <div className="z-form__actions">
            <Bouton variante="primaire" onClick={() => setFormulaire(true)}>
              {t.elevage.nouvelle}
            </Bouton>
          </div>
        ) : (
          <>
            <div className="z-form__grille">
              <ChampTexte libelle={t.elevage.code} valeur={code} onChange={setCode} />
              <ChampSelect
                libelle={t.elevage.origine}
                valeur={origine}
                options={ORIGINES_ELEVAGE.map((o) => ({
                  valeur: o,
                  libelle: t.elevage.origines[o],
                }))}
                onChange={(v) => setOrigine(v as OrigineReine)}
              />
              {/* Le fournisseur n'a de sens que sur une reine achetée — la base
                  le refuse ailleurs, et le champ disparaît plutôt que de faire
                  saisir une valeur qui sera effacée. */}
              {origine === 'achat' && (
                <ChampTexte
                  libelle={t.elevage.fournisseur}
                  valeur={fournisseur}
                  onChange={setFournisseur}
                />
              )}
            </div>
            <div className="z-form__grille">
              <ChampSelect
                libelle={t.elevage.mere}
                valeur={mereId}
                options={[
                  vide,
                  ...liste.map((r) => ({
                    valeur: String(r.id),
                    libelle: r.code ?? `#${r.id}`,
                  })),
                ]}
                onChange={setMereId}
              />
              <ChampSelect
                libelle={t.elevage.rucheEnService}
                valeur={rucheId}
                options={optRuches}
                onChange={setRucheId}
              />
              <ChampSelect
                libelle={t.elevage.etat}
                valeur={etat}
                options={ETATS_REINE.map((e) => ({ valeur: e, libelle: t.elevage.etats[e] }))}
                onChange={(v) => setEtat(v as EtatReine)}
              />
            </div>
            <div className="z-form__grille">
              <ChampTexte libelle={t.elevage.race} valeur={race} onChange={setRace} />
              <ChampNombre
                libelle={t.elevage.anneeNaissance}
                valeur={annee}
                onChange={setAnnee}
                pas="1"
              />
              <ChampDate
                libelle={t.elevage.dateIntroduction}
                valeur={introduction}
                onChange={setIntroduction}
              />
            </div>
            <div className="z-form__actions">
              <Bouton variante="secondaire" onClick={() => setFormulaire(false)}>
                {t.actions.annuler}
              </Bouton>
              <Bouton variante="primaire" onClick={() => void enregistrer()}>
                {t.actions.enregistrer}
              </Bouton>
            </div>
          </>
        )}
      </fieldset>

      {choisie !== null && genealogie !== null && <ArbreLignee genealogie={genealogie} />}

      {choisie !== null && index !== null && (
        <fieldset className="z-composition">
          <legend className="z-champ__libelle">{t.elevage.index}</legend>
          {index.rucheId === null ? (
            <p className="z-info">{t.elevage.pasDeRuche}</p>
          ) : (
            <>
              <p className="z-info">
                {t.elevage.regne} : {f.date(index.debut)} → {f.date(index.fin)}
              </p>
              <div className="z-form__grille">
                {index.criteres.map((critere) => (
                  <Critere key={critere.code} critere={critere} />
                ))}
              </div>
              {/* La phrase qui empêche de fabriquer une note globale. */}
              <p className="z-info">{t.elevage.indexAide}</p>
            </>
          )}
        </fieldset>
      )}

      <fieldset className="z-composition">
        <legend className="z-champ__libelle">{t.elevage.series}</legend>
        <p className="z-info">{t.elevage.serieAide}</p>
        {lots.length > 0 && (
          <Table
            colonnes={colonnesSeries}
            elements={lots}
            onModifier={() => undefined}
            onSupprimer={(s) => void series.supprimer(s.id).then(recharger)}
          />
        )}
        {!serie ? (
          <div className="z-form__actions">
            <Bouton variante="secondaire" onClick={() => setSerie(true)}>
              {t.elevage.nouvelleSerie}
            </Bouton>
          </div>
        ) : (
          <>
            <div className="z-form__grille">
              <ChampTexte libelle={t.elevage.nom} valeur={nomSerie} onChange={setNomSerie} />
              <ChampDate
                libelle={t.elevage.dateGreffage}
                valeur={dateGreffage}
                onChange={setDateGreffage}
              />
              <ChampSelect
                libelle={t.elevage.methode}
                valeur="greffage"
                options={METHODES_ELEVAGE.map((m) => ({
                  valeur: m,
                  libelle: t.elevage.methodes[m],
                }))}
                onChange={() => undefined}
              />
            </div>
            <div className="z-form__grille">
              <ChampNombre
                libelle={t.elevage.nbGreffees}
                valeur={greffees}
                onChange={setGreffees}
                pas="1"
                min={1}
              />
              <ChampNombre
                libelle={t.elevage.nbAcceptees}
                valeur={acceptees}
                onChange={setAcceptees}
                pas="1"
                min={0}
              />
              <div className="z-champ z-champ--aligne-bas">
                <Bouton variante="primaire" onClick={() => void enregistrerSerie()}>
                  {t.actions.enregistrer}
                </Bouton>
              </div>
            </div>
          </>
        )}
      </fieldset>

      <fieldset className="z-composition">
        <legend className="z-champ__libelle">{t.elevage.documents}</legend>
        <div className="z-form__grille">
          <ChampDate
            libelle={t.elevage.periodeDebut}
            valeur={periode.debut}
            onChange={(v) => setPeriode({ ...periode, debut: v })}
          />
          <ChampDate
            libelle={t.elevage.periodeFin}
            valeur={periode.fin}
            onChange={(v) => setPeriode({ ...periode, fin: v })}
          />
          <div className="z-champ z-champ--aligne-bas">
            <Bouton
              variante="secondaire"
              onClick={() => ouvrirDocumentElevage('registre', periode.debut, periode.fin)}
            >
              {t.elevage.registre}
            </Bouton>
          </div>
        </div>
        <div className="z-form__actions">
          <Bouton
            variante="secondaire"
            onClick={() =>
              void chargerConformite(periode.debut, periode.fin)
                .then(setDossier)
                .catch((cause: unknown) => setErreur(messageErreur(cause, t.etats.erreur)))
            }
          >
            {t.elevage.conformite}
          </Bouton>
          <Bouton
            variante="secondaire"
            onClick={() => ouvrirDocumentElevage('conformite', periode.debut, periode.fin)}
          >
            {t.elevage.dossier}
          </Bouton>
        </div>
        {dossier !== null && (
          <>
            {/* L'avertissement d'abord, et toujours : Zümm ne certifie rien. */}
            <p className="z-erreur" role="status">
              {dossier.avertissement}
            </p>
            <ul className="z-liste">
              {dossier.points.map((point) => (
                <li key={point.code} className="z-liste__ligne">
                  <span>
                    <strong>
                      {t.elevage.points[point.code as keyof typeof t.elevage.points]
                        ?? point.code}
                    </strong>{' '}
                    · {t.elevage.controles[point.statut]}
                    <br />
                    <small>{point.detail}</small>
                  </span>
                  <span>
                    {point.nombre} {t.elevage.elements}
                  </span>
                </li>
              ))}
            </ul>
          </>
        )}
      </fieldset>
    </>
  );
}

/**
 * Un critère, avec ce qui le fonde.
 *
 * <p>Le nombre d'observations est affiché <strong>toujours</strong>, y compris
 * quand le critère est calculable : c'est lui qui permet de savoir si l'on
 * compare deux reines ou deux anecdotes.
 */
function Critere({ critere }: { critere: CritereGenetique }): ReactElement {
  const t = useT();
  const libelle =
    t.elevage.criteres[critere.code as keyof typeof t.elevage.criteres] ?? critere.code;
  return (
    <div className="z-critere">
      <span className="z-critere__valeur">
        {critere.suffisant && critere.valeur !== null
          ? `${critere.valeur} ${critere.unite ?? ''}`
          : t.elevage.insuffisant}
      </span>
      <span className="z-critere__libelle">{libelle}</span>
      <span className="z-critere__fond">
        {critere.observations} {t.elevage.observations}
      </span>
    </div>
  );
}
