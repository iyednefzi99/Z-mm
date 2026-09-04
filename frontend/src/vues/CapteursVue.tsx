import { useEffect, useState, type ReactElement } from 'react';
import {
  chargerAlertesOuvertes,
  chargerMeteo,
  chargerSerieJournaliere,
  detecterAnomalie,
  getZummHoneyActualQuantity,
  ingererMesure,
  listerPartages,
  ouvrirPartage,
  peserCompartiment,
  repartitionCompartiments,
  revoquerPartage,
  ruches,
  sites,
} from '../api/client';
import type {
  AlerteMesure,
  Anomalie,
  Meteo,
  Partage,
  PoidsCompartiment,
  PointJournalier,
  QuantiteMiel,
  Ruche,
  Site,
  TypeIndicateur,
} from '../api/types';
import { TYPES_INDICATEUR } from '../api/types';
import { gabarit } from '../i18n/console';
import { useFormats, useLangue, useT } from '../i18n/langue';
import { messageErreur } from '../hooks';
import { Bouton, ChampNombre, ChampSelect, ChampTexte, Option } from '../ui/composants';
import { Courbe, type Serie } from '../ui/graphiques';

const UNITES = ['kg', 'g', 'lb', 't'];

/** Capteurs : ingestion de mesures (US-017), alertes (US-018), météo (US-029), miel (US-026). */
export function CapteursVue(): ReactElement {
  const t = useT();
  const { langue } = useLangue();
  const f = useFormats();
  const [optRuches, setOptRuches] = useState<Option[]>([]);
  const [optSites, setOptSites] = useState<Option[]>([]);
  const [alertes, setAlertes] = useState<AlerteMesure[]>([]);
  const [rucheId, setRucheId] = useState('');
  const [indicateur, setIndicateur] = useState<TypeIndicateur>('poids');
  const [valeur, setValeur] = useState('');
  const [siteId, setSiteId] = useState('');
  // Horizon de prévision. Le serveur borne à 16 jours plutôt que de rejeter,
  // mais autant ne pas lui envoyer d'emblée une valeur qu'il devra corriger.
  const [horizon, setHorizon] = useState('7');
  const [meteo, setMeteo] = useState<Meteo | null>(null);
  const [mielRuche, setMielRuche] = useState('');
  const [unite, setUnite] = useState('kg');
  const [miel, setMiel] = useState<QuantiteMiel | null>(null);
  const [anomRuche, setAnomRuche] = useState('');
  const [anomType, setAnomType] = useState<TypeIndicateur>('poids');
  const [anomalie, setAnomalie] = useState<Anomalie | null>(null);
  const [serie, setSerie] = useState<PointJournalier[]>([]);
  const [erreur, setErreur] = useState<string | null>(null);
  // Poids par etage (SPRINT-26). Distinct de la serie de la ruche : celle-ci
  // porte ce qu'une balance pese SOUS la ruche, celui-la ce qu'on attribue a
  // chaque etage. Les additionner ferait compter deux fois le meme miel.
  const [etageRuche, setEtageRuche] = useState('');
  const [etages, setEtages] = useState<PoidsCompartiment[] | null>(null);
  const [etageChoisi, setEtageChoisi] = useState('');
  const [poidsEtage, setPoidsEtage] = useState('');
  // Partage d'un flux hors de l'exploitation (SPRINT-26).
  const [partageRuche, setPartageRuche] = useState('');
  const [partages, setPartages] = useState<Partage[]>([]);
  const [partageLibelle, setPartageLibelle] = useState('');
  const [partageDuree, setPartageDuree] = useState('30');
  const [urlPartage, setUrlPartage] = useState<string | null>(null);

  const optIndicateur: Option[] = TYPES_INDICATEUR.map((i) => ({
    valeur: i,
    libelle: t.capteur.indicateurs[i],
  }));
  const optUnite: Option[] = UNITES.map((u) => ({ valeur: u, libelle: u }));
  const optRucheMiel: Option[] = [{ valeur: '', libelle: t.capteur.total }, ...optRuches];

  const chargerEtages = (ruche: string) => {
    setEtageRuche(ruche);
    setEtageChoisi('');
    setEtages(null);
    if (ruche === '') {
      return;
    }
    void repartitionCompartiments(Number(ruche))
      .then(setEtages)
      .catch(() => setEtages([]));
  };

  const peser = async () => {
    if (etageChoisi === '' || poidsEtage === '') {
      return;
    }
    setErreur(null);
    try {
      await peserCompartiment({
        compartimentId: Number(etageChoisi),
        valeur: Number(poidsEtage),
      });
      setPoidsEtage('');
      chargerEtages(etageRuche);
    } catch (cause) {
      setErreur(cause instanceof Error ? cause.message : t.etats.erreur);
    }
  };

  const chargerPartages = (ruche: string) => {
    setPartageRuche(ruche);
    setUrlPartage(null);
    if (ruche === '') {
      setPartages([]);
      return;
    }
    void listerPartages(Number(ruche))
      .then(setPartages)
      .catch(() => setPartages([]));
  };

  /**
   * Ouvre un partage et affiche l'URL — la SEULE fois ou elle existe.
   *
   * <p>La base ne garde que l'empreinte du jeton : impossible de la relire plus
   * tard. C'est le prix, assume, de ne rien stocker de reutilisable, et l'ecran
   * doit le dire au moment ou l'utilisateur peut encore recopier.
   */
  const partager = async () => {
    if (partageRuche === '' || partageLibelle.trim() === '') {
      return;
    }
    setErreur(null);
    try {
      const partage = await ouvrirPartage({
        rucheId: Number(partageRuche),
        libelle: partageLibelle.trim(),
        dureeJours: Number(partageDuree),
      });
      setUrlPartage(partage.url);
      setPartageLibelle('');
      chargerPartagesSansEffacerUrl(partage);
    } catch (cause) {
      setErreur(cause instanceof Error ? cause.message : t.etats.erreur);
    }
  };

  const chargerPartagesSansEffacerUrl = (nouveau: Partage) => {
    void listerPartages(nouveau.rucheId)
      .then(setPartages)
      .catch(() => setPartages([nouveau]));
  };

  const revoquer = async (id: number) => {
    try {
      await revoquerPartage(id);
      setUrlPartage(null);
      chargerPartages(partageRuche);
    } catch (cause) {
      setErreur(cause instanceof Error ? cause.message : t.etats.erreur);
    }
  };

  const rafraichirAlertes = () => {
    void chargerAlertesOuvertes().then(setAlertes).catch(() => setAlertes([]));
  };

  useEffect(() => {
    void ruches.lister().then((l: Ruche[]) => setOptRuches(l.map((r) => ({ valeur: String(r.id), libelle: r.modele })))).catch(() => setOptRuches([]));
    void sites
      .lister()
      .then((l: Site[]) => {
        setOptSites(l.map((s) => ({ valeur: String(s.id), libelle: s.nom })));
        // Le `<select>` affiche le premier site dès qu'il a des options, alors que
        // l'état reste vide : « Afficher » ne faisait alors rien, en silence, sur
        // ce qui semble pourtant être un site choisi. On aligne l'état sur ce qui
        // est vu. Lecture seule, donc sans risque — les sélecteurs de RUCHE, eux,
        // restent vides à dessein : préremplir une cible d'ingestion ferait courir
        // le risque d'écrire une mesure sur la mauvaise ruche.
        if (l.length > 0) {
          setSiteId((actuel) => (actuel === '' ? String(l[0].id) : actuel));
        }
      })
      .catch(() => setOptSites([]));
    rafraichirAlertes();
  }, []);

  const ingerer = async () => {
    if (rucheId === '' || valeur === '') return;
    setErreur(null);
    try {
      await ingererMesure({
        rucheId: Number(rucheId),
        typeIndicateur: indicateur,
        valeur: Number(valeur),
        instant: null,
      });
      setValeur('');
      rafraichirAlertes();
    } catch (cause) {
      setErreur(messageErreur(cause, t.etats.serviceIndisponible));
    }
  };

  const voirMeteo = async () => {
    if (siteId === '') return;
    setErreur(null);
    try {
      setMeteo(await chargerMeteo(Number(siteId), Number(horizon)));
    } catch (cause) {
      setErreur(messageErreur(cause, t.etats.serviceIndisponible));
    }
  };

  const calculerMiel = async () => {
    setErreur(null);
    try {
      setMiel(await getZummHoneyActualQuantity(mielRuche === '' ? null : Number(mielRuche), unite));
    } catch (cause) {
      setErreur(messageErreur(cause, t.etats.serviceIndisponible));
    }
  };

  const analyser = async () => {
    if (anomRuche === '') return;
    setErreur(null);
    try {
      // Les deux appels partent ensemble : la courbe et le verdict d'anomalie
      // decrivent la MEME serie, les afficher a des instants differents
      // laisserait un ecran incoherent le temps d'un aller-retour.
      const [resultat, mesures] = await Promise.all([
        detecterAnomalie(Number(anomRuche), anomType),
        chargerSerieJournaliere(Number(anomRuche), anomType),
      ]);
      setAnomalie(resultat);
      setSerie(mesures);
    } catch (cause) {
      setErreur(messageErreur(cause, t.etats.serviceIndisponible));
    }
  };

  /**
   * Series tracees : la mesure brute, et la ligne de base EWMA en reference.
   * Elles partagent une seule echelle de valeurs — c'est le meme indicateur,
   * dans la meme unite. Deux axes Y seraient ici une faute de lecture.
   */
  const seriesCourbe: Serie[] = (() => {
    if (serie.length === 0) return [];
    const points = serie.map((m) => ({ x: Date.parse(m.jour), y: m.moyenne }));
    const series: Serie[] = [{ nom: t.graphique.mesuree, points, rang: 0 }];
    if (anomalie?.baseline != null && points.length > 0) {
      series.push({
        nom: t.graphique.moyenneMobile,
        rang: 1,
        reference: true,
        points: [
          { x: points[0].x, y: anomalie.baseline },
          { x: points[points.length - 1].x, y: anomalie.baseline },
        ],
      });
    }
    return series;
  })();

  return (
    <section className="z-section">
      <header className="z-section__entete">
        <div>
          <h1 className="z-section__titre">{t.onglets.capteurs}</h1>
          <p className="z-section__soustitre">{t.soustitres.capteurs}</p>
        </div>
      </header>

      {erreur && (
        <div className="z-erreur" role="alert">
          <span>{erreur}</span>
        </div>
      )}

      <fieldset className="z-composition">
        <legend className="z-champ__libelle">{t.capteur.ingestion}</legend>
        <div className="z-form__grille">
          <ChampSelect libelle={t.capteur.ruche} valeur={rucheId} options={optRuches} onChange={setRucheId} />
          <ChampSelect
            libelle={t.capteur.indicateur}
            valeur={indicateur}
            options={optIndicateur}
            onChange={(v) => setIndicateur(v as TypeIndicateur)}
          />
          <ChampNombre libelle={t.capteur.valeur} valeur={valeur} onChange={setValeur} />
          <div className="z-champ z-champ--aligne-bas">
            <Bouton variante="primaire" onClick={() => void ingerer()}>
              {t.capteur.ingerer}
            </Bouton>
          </div>
        </div>
      </fieldset>

      <fieldset className="z-composition">
        <legend className="z-champ__libelle">{t.capteur.alertes}</legend>
        {alertes.length === 0 ? (
          <p className="z-info">{t.capteur.aucuneAlerte}</p>
        ) : (
          <ul className="z-liste-alertes">
            {alertes.map((a) => (
              <li key={a.id} className={`z-ligne--${a.niveau}`}>
                <strong>{a.rucheModele}</strong> — {t.capteur.niveaux[a.niveau]} : {a.message}
              </li>
            ))}
          </ul>
        )}
      </fieldset>

      <div className="z-form__grille">
        <fieldset className="z-composition">
          <legend className="z-champ__libelle">{t.capteur.meteo}</legend>
          <div className="z-form__grille">
            <ChampSelect libelle={t.capteur.site} valeur={siteId} options={optSites} onChange={setSiteId} />
            <ChampNombre
              libelle={t.capteur.horizon}
              valeur={horizon}
              onChange={setHorizon}
              min={0}
              max={16}
            />
            <div className="z-champ z-champ--aligne-bas">
              <Bouton variante="secondaire" onClick={() => void voirMeteo()}>
                {t.tableau.afficher}
              </Bouton>
            </div>
          </div>
          {meteo && (
            <>
              <p className="z-info">
                {t.capteur.temperature} : {meteo.temperatureCelsius} · {t.capteur.humidite} :{' '}
                {meteo.humiditePourcent ?? '—'} · {t.capteur.vent} : {meteo.ventKmh ?? '—'} ·{' '}
                {t.capteur.source} : {meteo.source}
              </p>
              <p className="z-champ__libelle">{t.capteur.previsions}</p>
              {meteo.previsions.length === 0 ? (
                <p className="z-info">{t.capteur.aucunePrevision}</p>
              ) : (
                <div className="z-table-enveloppe">
                  <table className="z-table">
                    <thead>
                      <tr>
                        <th>{t.capteur.jour}</th>
                        <th>{t.capteur.tempMin}</th>
                        <th>{t.capteur.tempMax}</th>
                        <th>{t.capteur.pluie}</th>
                        <th>{t.capteur.ventMax}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {meteo.previsions.map((p) => (
                        <tr key={p.date}>
                          <td>{f.date(p.date)}</td>
                          <td>{f.nombre(p.temperatureMinCelsius, 1)}</td>
                          <td>{f.nombre(p.temperatureMaxCelsius, 1)}</td>
                          <td>{f.nombre(p.precipitationsMm, 1)}</td>
                          <td>{f.nombre(p.ventMaxKmh, 1)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </>
          )}
        </fieldset>

        <fieldset className="z-composition">
          <legend className="z-champ__libelle">{t.capteur.quantiteMiel}</legend>
          <div className="z-form__grille">
            <ChampSelect libelle={t.capteur.ruche} valeur={mielRuche} options={optRucheMiel} onChange={setMielRuche} />
            <ChampSelect libelle={t.capteur.unite} valeur={unite} options={optUnite} onChange={setUnite} />
            <div className="z-champ z-champ--aligne-bas">
              <Bouton variante="secondaire" onClick={() => void calculerMiel()}>
                {t.capteur.calculer}
              </Bouton>
            </div>
          </div>
          {miel && (
            <p className="z-info">
              <strong>
                {miel.quantite} {miel.unite}
              </strong>
            </p>
          )}
        </fieldset>
      </div>

      <fieldset className="z-composition">
        <legend className="z-champ__libelle">{t.etage.titre}</legend>
        {/* Ce poids-la n'entre NI dans les alertes, NI dans la prevision de
            recolte : il repond a une autre question — ou est le miel, et non
            combien pese la ruche. */}
        <p className="z-info">{t.etage.aide}</p>
        <div className="z-form__grille">
          <ChampSelect
            libelle={t.capteur.ruche}
            valeur={etageRuche}
            options={[{ valeur: '', libelle: t.champs.aucun }, ...optRuches]}
            onChange={chargerEtages}
          />
          <ChampSelect
            libelle={t.etage.compartiment}
            valeur={etageChoisi}
            options={[
              { valeur: '', libelle: t.champs.aucun },
              ...(etages ?? []).map((e) => ({
                valeur: String(e.compartimentId),
                libelle: `${t.etage[e.type]} · ${e.nbCadres} ${t.champs.cadres}`,
              })),
            ]}
            onChange={setEtageChoisi}
          />
          <ChampNombre libelle={t.etage.poids} valeur={poidsEtage} onChange={setPoidsEtage} />
          <div className="z-champ z-champ--aligne-bas">
            <Bouton
              variante="primaire"
              disabled={etageChoisi === '' || poidsEtage === ''}
              onClick={() => void peser()}
            >
              {t.etage.peser}
            </Bouton>
          </div>
        </div>
        {etages !== null && etages.length > 0 && (
          <ul className="z-liste-simple">
            {etages.map((e) => (
              <li key={e.compartimentId}>
                <strong>{t.etage[e.type]}</strong> · {e.nbCadres} {t.champs.cadres} —{' '}
                {/* Nul et non zero : une hausse jamais pesee n'est pas une
                    hausse vide, et 0 kg ferait croire a des reserves perdues. */}
                {e.valeur === null ? (
                  <span className="z-info">{t.etage.jamaisPese}</span>
                ) : (
                  <>
                    {f.nombre(e.valeur, 1)} kg
                    <small className="z-info"> · {f.dateHeure(e.instant)}</small>
                  </>
                )}
              </li>
            ))}
          </ul>
        )}
        {etages !== null && etages.length === 0 && <p className="z-info">{t.etats.vide}</p>}
      </fieldset>

      <fieldset className="z-composition">
        <legend className="z-champ__libelle">{t.partage.titre}</legend>
        <p className="z-info">{t.partage.aide}</p>
        <div className="z-form__grille">
          <ChampSelect
            libelle={t.capteur.ruche}
            valeur={partageRuche}
            options={[{ valeur: '', libelle: t.champs.aucun }, ...optRuches]}
            onChange={chargerPartages}
          />
          <ChampTexte
            libelle={t.partage.libelle}
            valeur={partageLibelle}
            onChange={setPartageLibelle}
          />
          <ChampNombre
            libelle={t.partage.duree}
            valeur={partageDuree}
            onChange={setPartageDuree}
            min={1}
            max={365}
          />
          <div className="z-champ z-champ--aligne-bas">
            <Bouton
              variante="secondaire"
              disabled={partageRuche === '' || partageLibelle.trim() === ''}
              onClick={() => void partager()}
            >
              {t.partage.ouvrir}
            </Bouton>
          </div>
        </div>
        {urlPartage && (
          <div className="z-rappels" role="status">
            <strong>{t.partage.urlUnique}</strong>
            <br />
            <code>{urlPartage}</code>
          </div>
        )}
        {partages.length > 0 && (
          <ul className="z-liste-simple">
            {partages.map((p) => (
              <li key={p.id}>
                <strong>{p.libelle}</strong>{' '}
                <small className="z-info">
                  {gabarit(t.partage.ligne, {
                    expire: f.date(p.expireLe),
                    usage:
                      p.derniereUtilisation === null
                        ? t.partage.jamaisConsulte
                        : f.dateHeure(p.derniereUtilisation),
                  })}
                </small>
                {p.actif ? (
                  <>
                    {' '}
                    <button type="button" className="z-lien" onClick={() => void revoquer(p.id)}>
                      {t.partage.revoquer}
                    </button>
                  </>
                ) : (
                  <span className="z-info"> · {t.partage.revoque}</span>
                )}
              </li>
            ))}
          </ul>
        )}
      </fieldset>

      <fieldset className="z-composition">
        <legend className="z-champ__libelle">{t.anomalie.titre}</legend>
        <div className="z-form__grille">
          <ChampSelect libelle={t.capteur.ruche} valeur={anomRuche} options={optRuches} onChange={setAnomRuche} />
          <ChampSelect
            libelle={t.anomalie.indicateur}
            valeur={anomType}
            options={optIndicateur}
            onChange={(v) => setAnomType(v as TypeIndicateur)}
          />
          <div className="z-champ z-champ--aligne-bas">
            <Bouton variante="secondaire" onClick={() => void analyser()}>
              {t.anomalie.detecter}
            </Bouton>
          </div>
        </div>
        {anomalie && seriesCourbe.length > 0 && (
          <Courbe
            titre={`${t.graphique.serieTitre} — ${t.capteur.indicateurs[anomType]}`}
            description={t.graphique.serieDescription}
            series={seriesCourbe}
            langue={langue}
            messageVide={t.graphique.aucuneDonnee}
            formatX={(x) =>
              new Intl.DateTimeFormat(langue, { day: '2-digit', month: 'short' }).format(new Date(x))
            }
            libelleTableau={t.graphique.tableauEquivalent}
            tableau={
              <div className="z-table-enveloppe">
                <table className="z-table">
                  <thead>
                    <tr>
                      <th>{t.visite.date}</th>
                      <th>{t.capteur.valeur}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {serie.map((m) => (
                      <tr key={m.jour}>
                        <td>{m.jour}</td>
                        <td>{m.moyenne}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            }
          />
        )}

        {anomalie && (
          <div>
            <p className="z-info">
              {t.anomalie.baseline} : {anomalie.baseline ?? '—'} · {t.anomalie.ecartType} :{' '}
              {anomalie.ecartType ?? '—'} · {t.anomalie.nombrePoints} : {anomalie.nombrePoints} ·{' '}
              {t.anomalie.seuilZ} : {anomalie.seuilZ}
            </p>
            {anomalie.anomalies.length === 0 ? (
              <p className="z-info">{t.anomalie.aucune}</p>
            ) : (
              <ul className="z-liste-alertes">
                {anomalie.anomalies.map((a) => (
                  <li key={a.instant} className="z-ligne--critique">
                    {a.instant} — {t.anomalie.valeur} {a.valeur} · {t.anomalie.zscore} {a.zScore}
                  </li>
                ))}
              </ul>
            )}
          </div>
        )}
      </fieldset>
    </section>
  );
}
