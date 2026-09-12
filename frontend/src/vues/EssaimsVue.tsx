import { useCallback, useEffect, useState, type ReactElement } from 'react';
import {
  agents,
  creerCapture,
  creerDivision,
  filiationRuche,
  listerDivisions,
  listerCaptures,
  listerCapturesEnAttente,
  logerCapture,
  ruches,
  supprimerCapture,
  supprimerDivision,
} from '../api/client';
import type {
  Agent,
  CaptureEssaim,
  Division,
  MethodeDivision,
  OrigineCapture,
  OrigineReineDivision,
  Ruche,
} from '../api/types';
import { METHODES_DIVISION, ORIGINES_CAPTURE, ORIGINES_REINE } from '../api/types';
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
} from '../ui/composants';

/** Les deux registres de l'écran. Un seul est monté à la fois. */
type Volet = 'captures' | 'divisions';

const VOLETS: readonly Volet[] = ['captures', 'divisions'];

/**
 * Entrées et divisions de colonies (SPRINT-21).
 *
 * <p>Répond aux deux manques du §1 de `docs/ECART-CONCURRENTS.md` que la seule
 * migration ne comblait pas : la **capture d'essaim**, que le dépôt ne nommait
 * nulle part, et la **division comme événement**, dont on ne savait ni la fille
 * ni ce qui lui avait été transféré.
 *
 * <p><strong>Un écran pour les deux, et non deux destinations.</strong> Ce sont
 * les deux façons dont une colonie entre dans le parc — l'une subie et gratuite,
 * l'autre décidée. On les consulte à la même question : « combien de colonies en
 * plus cette saison, et d'où viennent-elles ». Les séparer aurait dispersé la
 * réponse.
 *
 * <p><strong>Rien ne se modifie ici : on saisit et on supprime.</strong> Comme au
 * registre sanitaire, une division est un fait daté et non un état à corriger ;
 * le serveur n'expose aucun `PUT` sur ces deux ressources, et le tableau ne
 * propose donc pas « Modifier ».
 *
 * <p><strong>La filiation se lit dans les deux sens.</strong> Le tableau des
 * divisions montre ce dont la ruche choisie est issue ET ce qu'elle a engendré :
 * sur une ruche, les deux questions se posent au même moment.
 */
export function EssaimsVue(): ReactElement {
  const t = useT();
  const f = useFormats();
  const e = t.terrain;

  const [volet, setVolet] = useState<Volet>('captures');
  const [optRuches, setOptRuches] = useState<Option[]>([]);
  const [optAgents, setOptAgents] = useState<Option[]>([]);
  const [erreur, setErreur] = useState<string | null>(null);

  // Captures
  const [captures, setCaptures] = useState<CaptureEssaim[]>([]);
  const [filtreCapture, setFiltreCapture] = useState<'toutes' | 'enAttente'>('toutes');
  const [dateCapture, setDateCapture] = useState('');
  const [origineCapture, setOrigineCapture] = useState<OrigineCapture>('essaim_naturel');
  const [lieu, setLieu] = useState('');
  const [poids, setPoids] = useState('');
  const [hauteur, setHauteur] = useState('');
  const [agentCapture, setAgentCapture] = useState('');
  const [noteCapture, setNoteCapture] = useState('');

  // Divisions
  const [rucheMere, setRucheMere] = useState('');
  const [divisions, setDivisions] = useState<Division[]>([]);
  // Deux lectures de la filiation, et elles ne disent pas la meme chose : les
  // divisions FAITES depuis cette ruche, ou la filiation dans les deux sens.
  // Melanger « issue de » et « a donne » dans un seul tableau rendait la lecture
  // ambigue des qu'une ruche etait a la fois mere et fille.
  const [porteeDivision, setPorteeDivision] = useState<'issues' | 'filiation'>('filiation');
  const [rucheFille, setRucheFille] = useState('');
  const [agentDivision, setAgentDivision] = useState('');
  const [dateDivision, setDateDivision] = useState('');
  const [methode, setMethode] = useState('');
  const [origineReine, setOrigineReine] = useState('');
  const [cadresCouvain, setCadresCouvain] = useState('');
  const [cadresProvisions, setCadresProvisions] = useState('');
  const [noteDivision, setNoteDivision] = useState('');

  const signaler = useCallback(
    (cause: unknown) => setErreur(messageErreur(cause, t.etats.erreur)),
    [t.etats.erreur],
  );

  useEffect(() => {
    ruches
      .lister()
      .then((liste: Ruche[]) =>
        setOptRuches(
          liste.map((r) => ({ valeur: String(r.id), libelle: `${r.id} — ${r.modele}` })),
        ),
      )
      .catch(() => setOptRuches([]));
    agents
      .lister()
      .then((liste: Agent[]) =>
        setOptAgents(liste.map((a) => ({ valeur: String(a.id), libelle: a.nom }))),
      )
      .catch(() => setOptAgents([]));
  }, []);

  const rechargerCaptures = useCallback(() => {
    const chargement =
      filtreCapture === 'enAttente' ? listerCapturesEnAttente() : listerCaptures();
    chargement.then(setCaptures).catch(signaler);
  }, [filtreCapture, signaler]);

  useEffect(() => rechargerCaptures(), [rechargerCaptures]);

  const rechargerDivisions = useCallback(() => {
    if (rucheMere === '') {
      setDivisions([]);
      return;
    }
    const chargement =
      porteeDivision === 'issues'
        ? listerDivisions(Number(rucheMere))
        : filiationRuche(Number(rucheMere));
    chargement.then(setDivisions).catch(signaler);
  }, [rucheMere, porteeDivision, signaler]);

  useEffect(() => rechargerDivisions(), [rechargerDivisions]);

  const enregistrerCapture = async () => {
    setErreur(null);
    try {
      await creerCapture({
        agentId: Number(agentCapture),
        rucheId: null,
        siteId: null,
        dateCapture,
        origine: origineCapture,
        lieu: lieu.trim() === '' ? null : lieu.trim(),
        poidsKg: poids.trim() === '' ? null : Number(poids),
        hauteurM: hauteur.trim() === '' ? null : Number(hauteur),
        note: noteCapture.trim() === '' ? null : noteCapture.trim(),
      });
      setLieu('');
      setPoids('');
      setHauteur('');
      setNoteCapture('');
      rechargerCaptures();
    } catch (cause) {
      signaler(cause);
    }
  };

  const enregistrerDivision = async () => {
    setErreur(null);
    try {
      await creerDivision({
        rucheMereId: Number(rucheMere),
        rucheFilleId: rucheFille === '' ? null : Number(rucheFille),
        agentId: Number(agentDivision),
        visiteId: null,
        dateDivision,
        methode: methode === '' ? null : (methode as MethodeDivision),
        cadresCouvain: cadresCouvain.trim() === '' ? null : Number(cadresCouvain),
        cadresProvisions: cadresProvisions.trim() === '' ? null : Number(cadresProvisions),
        origineReine: origineReine === '' ? null : (origineReine as OrigineReineDivision),
        note: noteDivision.trim() === '' ? null : noteDivision.trim(),
      });
      setRucheFille('');
      setCadresCouvain('');
      setCadresProvisions('');
      setNoteDivision('');
      rechargerDivisions();
    } catch (cause) {
      signaler(cause);
    }
  };

  /** Loger, c'est-à-dire faire entrer l'essaim dans le parc comme colonie. */
  const loger = (capture: CaptureEssaim, rucheId: string) => {
    if (rucheId === '') {
      return;
    }
    setErreur(null);
    logerCapture(capture.id, Number(rucheId)).then(rechargerCaptures).catch(signaler);
  };

  const optionsAvecVide = (options: Option[]): Option[] => [
    { valeur: '', libelle: t.champs.aucun },
    ...options,
  ];

  const colonnesCaptures: Colonne<CaptureEssaim>[] = [
    { entete: t.champs.dateCapture, rendu: (c) => f.date(c.dateCapture) },
    { entete: t.champs.origine, rendu: (c) => e.captures.origines[c.origine] },
    { entete: t.champs.lieu, rendu: (c) => c.lieu ?? '—' },
    { entete: t.champs.poidsEssaim, rendu: (c) => (c.poidsKg == null ? '—' : String(c.poidsKg)) },
    {
      entete: t.champs.etat,
      rendu: (c) =>
        c.logee ? (
          <Pastille ton="succes">{e.captures.logee}</Pastille>
        ) : (
          <Pastille ton="attention">{e.captures.enAttente}</Pastille>
        ),
    },
    {
      entete: t.actions.loger,
      // Une capture déjà logée n'offre plus le choix : elle rappelle sa ruche.
      rendu: (c) =>
        c.logee ? (
          <span className="z-info">{c.rucheId}</span>
        ) : (
          <ChampSelect
            libelle={t.actions.loger}
            valeur=""
            options={optionsAvecVide(optRuches)}
            onChange={(valeur) => loger(c, valeur)}
          />
        ),
    },
  ];

  const colonnesDivisions: Colonne<Division>[] = [
    { entete: t.champs.dateDivision, rendu: (d) => f.date(d.dateDivision) },
    { entete: t.champs.rucheMere, rendu: (d) => `${d.rucheMereId} — ${d.rucheMereModele}` },
    {
      entete: t.champs.rucheFille,
      rendu: (d) =>
        d.rucheFilleId == null
          ? e.divisions.filleAbsente
          : `${d.rucheFilleId} — ${d.rucheFilleModele ?? ''}`,
    },
    {
      entete: t.champs.methode,
      rendu: (d) => (d.methode == null ? '—' : e.divisions.methodes[d.methode]),
    },
    {
      entete: t.champs.cadresCouvain,
      rendu: (d) => (d.cadresCouvain == null ? '—' : String(d.cadresCouvain)),
    },
    { entete: t.champs.agentResponsable, rendu: (d) => d.agentNom },
  ];

  return (
    <section className="z-section">
      <header className="z-section__entete">
        <div>
          <h1 className="z-section__titre">{t.onglets.essaims}</h1>
          <p className="z-section__soustitre">{t.soustitres.essaims}</p>
        </div>
      </header>

      {erreur && (
        <div className="z-erreur" role="alert">
          <span>{erreur}</span>
        </div>
      )}

      {/* Même composant d'onglets que le registre sanitaire : la pastille glisse
          d'un cran plutôt que de clignoter d'un registre à l'autre. */}
      <div className="z-onglets" role="tablist" aria-label={t.onglets.essaims}>
        {VOLETS.map((nom) => (
          <button
            key={nom}
            type="button"
            role="tab"
            id={`onglet-${nom}`}
            aria-selected={volet === nom}
            aria-controls="volet-essaims"
            className={`z-onglets__item${volet === nom ? ' is-actif' : ''}`}
            onClick={() => setVolet(nom)}
          >
            {nom === 'captures' ? e.captures.titre : e.divisions.titre}
          </button>
        ))}
        <span
          className={`z-onglets__pastille${volet === 'divisions' ? ' is-droite' : ''}`}
          aria-hidden="true"
        />
      </div>

      <div id="volet-essaims" role="tabpanel" aria-labelledby={`onglet-${volet}`}>
        {volet === 'captures' && (
          <>
            <form
              className="z-form"
              onSubmit={(ev) => {
                ev.preventDefault();
                void enregistrerCapture();
              }}
            >
              <fieldset className="z-composition">
                <legend className="z-champ__libelle">{e.captures.nouvelle}</legend>
                <div className="z-form__grille">
                  <ChampDate
                    libelle={t.champs.dateCapture}
                    valeur={dateCapture}
                    onChange={setDateCapture}
                    requis
                  />
                  <ChampSelect
                    libelle={t.champs.origine}
                    valeur={origineCapture}
                    options={ORIGINES_CAPTURE.map((o) => ({
                      valeur: o,
                      libelle: e.captures.origines[o],
                    }))}
                    onChange={(valeur) => setOrigineCapture(valeur as OrigineCapture)}
                    requis
                  />
                  <ChampSelect
                    libelle={t.champs.agentResponsable}
                    valeur={agentCapture}
                    options={optionsAvecVide(optAgents)}
                    onChange={setAgentCapture}
                    requis
                  />
                </div>
                <div className="z-form__grille">
                  <ChampTexte libelle={t.champs.lieu} valeur={lieu} onChange={setLieu} />
                  <ChampNombre libelle={t.champs.poidsEssaim} valeur={poids} onChange={setPoids} />
                  <ChampNombre libelle={t.champs.hauteur} valeur={hauteur} onChange={setHauteur} />
                </div>
                <ChampZone libelle={t.champs.note} valeur={noteCapture} onChange={setNoteCapture} />
              </fieldset>
              <div className="z-form__actions">
                <Bouton variante="primaire" type="submit">
                  {t.actions.enregistrer}
                </Bouton>
              </div>
            </form>

            <ChampSelect
              libelle={e.captures.filtre}
              valeur={filtreCapture}
              options={[
                { valeur: 'toutes', libelle: e.captures.toutes },
                { valeur: 'enAttente', libelle: e.captures.enAttente },
              ]}
              onChange={(valeur) => setFiltreCapture(valeur as 'toutes' | 'enAttente')}
            />

            {captures.length === 0 ? (
              <EtatVide titre={e.captures.aucune} />
            ) : (
              <Table
                colonnes={colonnesCaptures}
                elements={captures}
                onModifier={() => undefined}
                onSupprimer={(c) => {
                  supprimerCapture(c.id).then(rechargerCaptures).catch(signaler);
                }}
              />
            )}
          </>
        )}

        {volet === 'divisions' && (
          <>
            <ChampSelect
              libelle={t.champs.rucheMere}
              valeur={rucheMere}
              options={optionsAvecVide(optRuches)}
              onChange={setRucheMere}
            />
            <ChampSelect
              libelle={e.divisions.portee}
              valeur={porteeDivision}
              options={[
                { valeur: 'filiation', libelle: e.divisions.filiation },
                { valeur: 'issues', libelle: e.divisions.issues },
              ]}
              onChange={(v) => setPorteeDivision(v === 'issues' ? 'issues' : 'filiation')}
            />

            {rucheMere === '' ? (
              <EtatVide titre={t.champs.rucheMere} />
            ) : (
              <>
                <form
                  className="z-form"
                  onSubmit={(ev) => {
                    ev.preventDefault();
                    void enregistrerDivision();
                  }}
                >
                  <fieldset className="z-composition">
                    <legend className="z-champ__libelle">{e.divisions.nouvelle}</legend>
                    <div className="z-form__grille">
                      <ChampDate
                        libelle={t.champs.dateDivision}
                        valeur={dateDivision}
                        onChange={setDateDivision}
                        requis
                      />
                      <ChampSelect
                        libelle={t.champs.agentResponsable}
                        valeur={agentDivision}
                        options={optionsAvecVide(optAgents)}
                        onChange={setAgentDivision}
                        requis
                      />
                      {/* Facultative : on divise souvent vers un nucléus qui ne
                          sera enregistré comme ruche que s'il prend. L'exiger ici
                          ferait renoncer à saisir la division. */}
                      <ChampSelect
                        libelle={t.champs.rucheFille}
                        valeur={rucheFille}
                        options={optionsAvecVide(optRuches)}
                        onChange={setRucheFille}
                      />
                    </div>
                    <div className="z-form__grille">
                      <ChampSelect
                        libelle={t.champs.methode}
                        valeur={methode}
                        options={optionsAvecVide(
                          METHODES_DIVISION.map((m) => ({
                            valeur: m,
                            libelle: e.divisions.methodes[m],
                          })),
                        )}
                        onChange={setMethode}
                      />
                      <ChampSelect
                        libelle={t.champs.origineReine}
                        valeur={origineReine}
                        options={optionsAvecVide(
                          ORIGINES_REINE.map((o) => ({
                            valeur: o,
                            libelle: e.divisions.origines[o],
                          })),
                        )}
                        onChange={setOrigineReine}
                      />
                    </div>
                    <div className="z-form__grille">
                      <ChampNombre
                        libelle={t.champs.cadresCouvain}
                        valeur={cadresCouvain}
                        onChange={setCadresCouvain}
                      />
                      <ChampNombre
                        libelle={t.champs.cadresProvisions}
                        valeur={cadresProvisions}
                        onChange={setCadresProvisions}
                      />
                    </div>
                    <ChampZone
                      libelle={t.champs.note}
                      valeur={noteDivision}
                      onChange={setNoteDivision}
                    />
                  </fieldset>
                  <div className="z-form__actions">
                    <Bouton variante="primaire" type="submit">
                      {t.actions.enregistrer}
                    </Bouton>
                  </div>
                </form>

                {divisions.length === 0 ? (
                  <EtatVide titre={e.divisions.aucune} />
                ) : (
                  <Table
                    colonnes={colonnesDivisions}
                    elements={divisions}
                    onModifier={() => undefined}
                    onSupprimer={(d) => {
                      supprimerDivision(d.id).then(rechargerDivisions).catch(signaler);
                    }}
                  />
                )}
              </>
            )}
          </>
        )}
      </div>
    </section>
  );
}
