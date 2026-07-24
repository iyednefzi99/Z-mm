import { useEffect, useState, type ReactElement } from 'react';
import {
  chargerAlertesOuvertes,
  chargerMeteo,
  detecterAnomalie,
  getZummHoneyActualQuantity,
  ingererMesure,
  ruches,
  sites,
} from '../api/client';
import type {
  AlerteMesure,
  Anomalie,
  Meteo,
  QuantiteMiel,
  Ruche,
  Site,
  TypeIndicateur,
} from '../api/types';
import { TYPES_INDICATEUR } from '../api/types';
import { useT } from '../i18n/langue';
import { messageErreur } from '../hooks';
import { Bouton, ChampNombre, ChampSelect, Option } from '../ui/composants';

const UNITES = ['kg', 'g', 'lb', 't'];

/** Capteurs : ingestion de mesures (US-017), alertes (US-018), météo (US-029), miel (US-026). */
export function CapteursVue(): ReactElement {
  const t = useT();
  const [optRuches, setOptRuches] = useState<Option[]>([]);
  const [optSites, setOptSites] = useState<Option[]>([]);
  const [alertes, setAlertes] = useState<AlerteMesure[]>([]);
  const [rucheId, setRucheId] = useState('');
  const [indicateur, setIndicateur] = useState<TypeIndicateur>('poids');
  const [valeur, setValeur] = useState('');
  const [siteId, setSiteId] = useState('');
  const [meteo, setMeteo] = useState<Meteo | null>(null);
  const [mielRuche, setMielRuche] = useState('');
  const [unite, setUnite] = useState('kg');
  const [miel, setMiel] = useState<QuantiteMiel | null>(null);
  const [anomRuche, setAnomRuche] = useState('');
  const [anomType, setAnomType] = useState<TypeIndicateur>('poids');
  const [anomalie, setAnomalie] = useState<Anomalie | null>(null);
  const [erreur, setErreur] = useState<string | null>(null);

  const optIndicateur: Option[] = TYPES_INDICATEUR.map((i) => ({
    valeur: i,
    libelle: t.capteur.indicateurs[i],
  }));
  const optUnite: Option[] = UNITES.map((u) => ({ valeur: u, libelle: u }));
  const optRucheMiel: Option[] = [{ valeur: '', libelle: t.capteur.total }, ...optRuches];

  const rafraichirAlertes = () => {
    void chargerAlertesOuvertes().then(setAlertes).catch(() => setAlertes([]));
  };

  useEffect(() => {
    void ruches.lister().then((l: Ruche[]) => setOptRuches(l.map((r) => ({ valeur: String(r.id), libelle: r.modele })))).catch(() => setOptRuches([]));
    void sites.lister().then((l: Site[]) => setOptSites(l.map((s) => ({ valeur: String(s.id), libelle: s.nom })))).catch(() => setOptSites([]));
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
      setErreur(messageErreur(cause));
    }
  };

  const voirMeteo = async () => {
    if (siteId === '') return;
    setErreur(null);
    try {
      setMeteo(await chargerMeteo(Number(siteId)));
    } catch (cause) {
      setErreur(messageErreur(cause));
    }
  };

  const calculerMiel = async () => {
    setErreur(null);
    try {
      setMiel(await getZummHoneyActualQuantity(mielRuche === '' ? null : Number(mielRuche), unite));
    } catch (cause) {
      setErreur(messageErreur(cause));
    }
  };

  const analyser = async () => {
    if (anomRuche === '') return;
    setErreur(null);
    try {
      setAnomalie(await detecterAnomalie(Number(anomRuche), anomType));
    } catch (cause) {
      setErreur(messageErreur(cause));
    }
  };

  return (
    <section className="z-section">
      <header className="z-section__entete">
        <h1 className="z-section__titre">{t.onglets.capteurs}</h1>
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
            <div className="z-champ z-champ--aligne-bas">
              <Bouton variante="secondaire" onClick={() => void voirMeteo()}>
                {t.tableau.afficher}
              </Bouton>
            </div>
          </div>
          {meteo && (
            <p className="z-info">
              {t.capteur.temperature} : {meteo.temperatureCelsius} · {t.capteur.humidite} :{' '}
              {meteo.humiditePourcent ?? '—'} · {t.capteur.vent} : {meteo.ventKmh ?? '—'} ·{' '}
              {t.capteur.source} : {meteo.source}
            </p>
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
