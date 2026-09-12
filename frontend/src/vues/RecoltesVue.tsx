import { useEffect, useState, type ReactElement } from 'react';
import {
  ErreurApi,
  calculerRefractometre,
  calculerValorisation,
  convertirUnite,
  recolterEnLot,
  recoltes,
  ruches,
  tracerLot,
} from '../api/client';
import { UNITES_MASSE, UNITES_TEMPERATURE } from '../api/types';
import type {
  RapportLot,
  Recolte,
  RecolteCorps,
  Refractometre,
  Ruche,
  Trace,
} from '../api/types';
import { QrImage } from '../ui/etiquettes';
import { gabarit } from '../i18n/console';
import { useFormats, useT } from '../i18n/langue';
import { useRessource } from '../hooks';
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
  Table,
} from '../ui/composants';
import { CorpsSection } from './CorpsSection';
import { PanneauPhotos } from '../photos/PanneauPhotos';

/** Récoltes, numéro de lot et QR de traçabilité (US-033). */
export function RecoltesVue(): ReactElement {
  const t = useT();
  const f = useFormats();
  const etat = useRessource<Recolte, RecolteCorps>(recoltes);
  const [optRuches, setOptRuches] = useState<Option[]>([]);
  const [ouvert, setOuvert] = useState(false);
  const [rucheId, setRucheId] = useState('');
  const [dateRecolte, setDateRecolte] = useState('');
  const [quantite, setQuantite] = useState('');
  const [typeMiel, setTypeMiel] = useState('');
  // Taux d'eau (SPRINT-28). Il décide de la conservation : au-delà de 18 %, le
  // miel fermente en pot, et un lot mis en pot à 20 % se perd en cave sans que
  // rien ne l'ait signalé.
  const [humidite, setHumidite] = useState('');
  const [indice, setIndice] = useState('');
  const [temperatureMesure, setTemperatureMesure] = useState('');
  const [mesure, setMesure] = useState<Refractometre | null>(null);
  const [erreurRefracto, setErreurRefracto] = useState<string | null>(null);
  const [note, setNote] = useState('');
  const [erreur, setErreur] = useState<string | null>(null);
  // Message du refus 409, s'il y en a un : il porte le produit et la date de
  // fin de carence, c'est-à-dire de quoi décider.
  const [carence, setCarence] = useState<string | null>(null);
  const [motifForcage, setMotifForcage] = useState('');
  const [qr, setQr] = useState<Recolte | null>(null);
  // La liste BRUTE des ruches est gardee a cote des options : recolter un
  // rucher entier demande le site de la ruche choisie, que  ne porte pas.
  const [ruchesConnues, setRuchesConnues] = useState<Ruche[]>([]);
  // Rapport du dernier lot. Il s'affiche tel quel : un lot reussit rarement en
  // entier, et masquer les refus ferait croire a quarante recoltes la ou il y
  // en a trente-sept.
  const [rapport, setRapport] = useState<RapportLot | null>(null);
  const [trace, setTrace] = useState<Trace | null>(null);
  const [photosDe, setPhotosDe] = useState<Recolte | null>(null);

  const colonnes: Colonne<Recolte>[] = [
    { entete: t.recolte.date, rendu: (r) => f.date(r.dateRecolte) },
    { entete: t.recolte.ruche, rendu: (r) => r.rucheModele },
    { entete: t.recolte.quantite, rendu: (r) => String(r.quantiteKg) },
    { entete: t.recolte.lot, rendu: (r) => r.lot },
    {
      entete: t.recolte.qr,
      rendu: (r) => (
        <button type="button" className="z-lien" onClick={() => { setTrace(null); setQr(r); void tracerLot(r.lot).then(setTrace).catch(() => setTrace(null)); }}>
          {t.recolte.qr}
        </button>
      ),
    },
    {
      entete: t.photos.titre,
      rendu: (r) => (
        <button type="button" className="z-lien" onClick={() => setPhotosDe(r)}>
          {t.photos.titre}
        </button>
      ),
    },
  ];

  useEffect(() => {
    void ruches
      .lister()
      .then((l: Ruche[]) => {
        setOptRuches(l.map((r) => ({ valeur: String(r.id), libelle: r.modele })));
        setRuchesConnues(l);
      })
      .catch(() => setOptRuches([]));
  }, [etat.elements]);

  const ouvrir = () => {
    setRucheId('');
    setDateRecolte('');
    setQuantite('');
    setTypeMiel('');
    setNote('');
    setErreur(null);
    setRapport(null);
    setOuvert(true);
  };

  /**
   * La meme récolte sur tout le rucher (SPRINT-23, lot B).
   *
   * <p>La quantité saisie vaut pour CHAQUE ruche, jamais comme un total à
   * répartir : partager quarante kilos en quarante lignes d'un kilo écrirait une
   * masse fausse par colonie, et la traçabilité repose sur ces masses-là.
   *
   * <p>Le forçage de carence n'est PAS proposé ici. Passer outre une carence est
   * une décision par colonie, motivée : l'offrir sur quarante ruches d'un coup
   * en ferait une case à cocher.
   */
  const recolterLeRucher = async () => {
    const site = ruchesConnues.find((r) => String(r.id) === rucheId)?.siteId;
    if (site === undefined || quantite === '' || dateRecolte === '') return;
    setErreur(null);
    setCarence(null);
    try {
      setRapport(
        await recolterEnLot({
          cible: { rucheIds: null, siteId: site },
          dateRecolte,
          quantiteKgParRuche: Number(quantite),
          typeMiel: typeMiel.trim() === '' ? null : typeMiel,
          note: note.trim() === '' ? null : note.trim(),
          forcerCarence: false,
          motifForcage: null,
        }),
      );
      etat.recharger();
    } catch (cause) {
      setErreur(cause instanceof Error ? cause.message : t.etats.erreur);
    }
  };

  /**
   * Enregistre la récolte, ou ouvre la porte de sortie.
   *
   * <p>Le serveur répond **409** quand la ruche est sous carence : la requête
   * est valide, c'est l'état de la ruche qui s'y oppose. On n'affiche donc pas
   * l'erreur comme un échec de saisie — on montre le motif du refus et le champ
   * qui permet de passer outre en s'expliquant. Un refus sans issue ferait
   * cesser de saisir le TRAITEMENT, et le registre deviendrait faux là où il
   * n'était qu'incomplet.
   */
  /**
   * Convertit une lecture de réfractomètre en taux d'eau.
   *
   * <p>Le résultat REMPLIT le champ plutôt que de s'y substituer : la valeur
   * enregistrée reste celle que l'apiculteur a sous les yeux et peut corriger,
   * par exemple s'il lit directement un pourcentage sur son appareil.
   *
   * <p>Hors de la table publiée, le serveur répond 400 et rien n'est rempli —
   * un chiffre extrapolé sur cette mesure-là serait cru.
   */
  const convertirIndice = async () => {
    if (indice === '') return;
    setErreurRefracto(null);
    try {
      const resultat = await calculerRefractometre(
        Number(indice),
        temperatureMesure === '' ? undefined : Number(temperatureMesure),
      );
      setMesure(resultat);
      setHumidite(String(resultat.humiditePct));
    } catch (cause) {
      setMesure(null);
      setErreurRefracto(
        cause instanceof ErreurApi ? cause.detail : t.refractometre.horsTable,
      );
    }
  };

  const enregistrer = async (forcer = false) => {
    if (rucheId === '' || quantite === '') return;
    try {
      await etat.creer({
        rucheId: Number(rucheId),
        dateRecolte,
        quantiteKg: Number(quantite),
        typeMiel: typeMiel.trim() === '' ? null : typeMiel,
        humiditePct: humidite === '' ? null : Number(humidite),
        note: note.trim() === '' ? null : note.trim(),
        forcerCarence: forcer,
        motifForcage: forcer && motifForcage.trim() !== '' ? motifForcage.trim() : null,
      });
      setOuvert(false);
      setCarence(null);
      setMotifForcage('');
    } catch (cause) {
      if (cause instanceof ErreurApi && cause.statut === 409) {
        setCarence(cause.detail);
        setErreur(null);
        return;
      }
      setErreur(cause instanceof Error ? cause.message : t.etats.erreur);
    }
  };

  return (
    <CorpsSection
      titre={t.onglets.recoltes}
      sousTitre={t.soustitres.recoltes}
      etat={etat} onNouveau={ouvrir}>
      {etat.elements.length > 0 && (
        <Table colonnes={colonnes} elements={etat.elements} onModifier={() => undefined} onSupprimer={(e) => void etat.supprimer(e.id)} />
      )}

      {ouvert && (
        <Modale titre={t.onglets.recoltes} onFermer={() => setOuvert(false)}>
          <form className="z-form" onSubmit={(e) => { e.preventDefault(); void enregistrer(); }}>
            <div className="z-form__grille">
              <ChampSelect libelle={t.recolte.ruche} valeur={rucheId} options={optRuches} onChange={setRucheId} requis />
              <ChampDate libelle={t.recolte.date} valeur={dateRecolte} onChange={setDateRecolte} requis />
            </div>
            <div className="z-form__grille">
              <ChampNombre libelle={t.recolte.quantite} valeur={quantite} onChange={setQuantite} requis />
              <ChampTexte libelle={t.recolte.typeMiel} valeur={typeMiel} onChange={setTypeMiel} />
            </div>
            <fieldset className="z-composition">
              <legend className="z-champ__libelle">{t.refractometre.titre}</legend>
              <div className="z-form__grille">
                <ChampNombre
                  libelle={t.refractometre.indice}
                  valeur={indice}
                  onChange={setIndice}
                  pas="0.0001"
                />
                <ChampNombre
                  libelle={t.refractometre.temperature}
                  valeur={temperatureMesure}
                  onChange={setTemperatureMesure}
                  pas="0.1"
                />
                <div className="z-champ z-champ--aligne-bas">
                  <Bouton variante="secondaire" onClick={() => void convertirIndice()}>
                    {t.refractometre.calculer}
                  </Bouton>
                </div>
              </div>
              <div className="z-form__grille">
                <ChampNombre
                  libelle={t.recolte.humidite}
                  valeur={humidite}
                  onChange={setHumidite}
                  pas="0.1"
                />
                {mesure !== null && (
                  <p className={mesure.conformeNorme ? 'z-info' : 'z-erreur'} role="status">
                    {t.refractometre[mesure.verdict]} · {t.refractometre.indiceCorrige} :{' '}
                    {mesure.indiceCorrige}
                  </p>
                )}
                {erreurRefracto !== null && (
                  <p className="z-erreur" role="alert">
                    {erreurRefracto}
                  </p>
                )}
              </div>
              <p className="z-info">{t.refractometre.aide}</p>
              <p className="z-info">{t.recolte.humiditeMielSeul}</p>
            </fieldset>
            <Valorisation />
            <ConversionUnites />
            <ChampZone libelle={t.recolte.note} valeur={note} onChange={setNote} />
            {carence !== null && (
              <div className="z-erreur" role="alert">
                <span>
                  <strong>{t.carence.bloquee}</strong>
                  <br />
                  {carence}
                  <br />
                  <small>{t.carence.aide}</small>
                </span>
              </div>
            )}
            {carence !== null && (
              <ChampZone
                libelle={t.carence.motif}
                valeur={motifForcage}
                onChange={setMotifForcage}
              />
            )}
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
            {erreur && <p className="z-form__erreur">{erreur}</p>}
            <div className="z-form__actions">
              <Bouton variante="fantome" onClick={() => setOuvert(false)}>{t.actions.annuler}</Bouton>
              {/* Le lot reste SECONDAIRE a cote de l'acte unitaire : recolter
                  quarante colonies d'un coup est le geste utile, il ne doit pas
                  etre celui qu'on declenche par reflexe. Il disparait apres un
                  409 : la question posee porte alors sur UNE ruche. */}
              {carence === null && (
                <Bouton
                  variante="secondaire"
                  onClick={() => void recolterLeRucher()}
                  disabled={rucheId === '' || quantite === '' || dateRecolte === ''}
                >
                  {t.actions.recolterLeRucher}
                </Bouton>
              )}
              {/* Tant qu'aucune carence n'a été opposée, le bouton enregistre.
                  Après un 409, il CHANGE DE NOM : « Forcer et enregistrer » dit
                  ce qu'on s'apprête à faire, et un bouton qui garde son libellé
                  ferait passer outre sans que personne ne l'ait décidé. */}
              {carence === null ? (
                <Bouton variante="primaire" type="submit">{t.actions.enregistrer}</Bouton>
              ) : (
                <Bouton
                  variante="primaire"
                  onClick={() => void enregistrer(true)}
                  disabled={motifForcage.trim() === ''}
                >
                  {t.actions.forcer}
                </Bouton>
              )}
            </div>
          </form>
        </Modale>
      )}

      {qr && (
        <Modale titre={`${t.recolte.tracabilite} — ${qr.lot}`} onFermer={() => setQr(null)}>
          <div className="z-form">
            <QrImage payload={qr.qrPayload} />
            {trace && (
              <p className="z-info">
                <strong>{t.recolte.origine} :</strong> {trace.rucheModele} · {t.recolte.site} :{' '}
                {trace.siteNom} · {t.recolte.ferme} : {trace.fermeNom} · {trace.quantiteKg} kg
                {trace.typeMiel ? ` · ${trace.typeMiel}` : ''}
              </p>
            )}
          </div>
        </Modale>
      )}

      {photosDe && (
        <Modale titre={t.photos.titre} onFermer={() => setPhotosDe(null)}>
          <PanneauPhotos cible="RECOLTE" cibleId={photosDe.id} />
        </Modale>
      )}
    </CorpsSection>
  );
}

/**
 * Valorisation d'une production (SPRINT-33).
 *
 * <p><strong>Une valorisation, jamais un chiffre d'affaires.</strong> Le prix au
 * kilo vient de {@code ConfigZumm.ini} : c'est un ordre de grandeur parametrable,
 * pas le prix auquel ce miel a ete vendu. Zumm ne connait pas les prix de vente,
 * et afficher « ce lot vaut 840 € » ferait passer un parametre pour une recette.
 */
function Valorisation(): ReactElement {
  const t = useT();
  const f = useFormats();
  const c = t.calculateurs.valorisation;
  const [kilos, setKilos] = useState('');
  const [prix, setPrix] = useState('');
  const [resultat, setResultat] = useState<{
    prixKgEur: number;
    totalEur: number;
    pots500g: number;
  } | null>(null);

  async function calculer(): Promise<void> {
    if (kilos === '') {
      return;
    }
    setResultat(
      await calculerValorisation(Number(kilos), prix === '' ? undefined : Number(prix)),
    );
  }

  return (
    <fieldset className="z-composition">
      <legend className="z-champ__libelle">{c.titre}</legend>
      <p className="z-info">{c.aide}</p>
      <div className="z-form__grille">
        <ChampNombre libelle={c.kilos} valeur={kilos} onChange={setKilos} min={0} />
        <ChampNombre libelle={c.prix} valeur={prix} onChange={setPrix} min={0} />
        <div className="z-champ z-champ--aligne-bas">
          <Bouton variante="secondaire" onClick={() => void calculer()}>
            {t.actions.calculer}
          </Bouton>
        </div>
      </div>
      {resultat !== null && (
        <p className="z-info" role="status">
          {c.total} : {f.nombre(resultat.totalEur)} € ({f.nombre(resultat.prixKgEur)} €/kg) ·{' '}
          {c.pots} : {resultat.pots500g}
        </p>
      )}
    </fieldset>
  );
}

/**
 * Conversion d'unites (US-019).
 *
 * <p>Masses et temperatures, et <strong>les deux familles ne se croisent
 * pas</strong> : le serveur refuse des grammes vers des degres au lieu de rendre
 * un nombre approximatif. L'erreur est donc affichee telle qu'il la formule —
 * la reformuler ici en inventerait une seconde version.
 */
function ConversionUnites(): ReactElement {
  const t = useT();
  const f = useFormats();
  const c = t.calculateurs.conversion;
  const [valeur, setValeur] = useState('1');
  const [de, setDe] = useState('kg');
  const [vers, setVers] = useState('g');
  const [resultat, setResultat] = useState<number | null>(null);
  const [erreur, setErreur] = useState<string | null>(null);

  const unites = [...UNITES_MASSE, ...UNITES_TEMPERATURE].map((u) => ({
    valeur: u,
    libelle: c.unites[u],
  }));

  async function convertir(): Promise<void> {
    setErreur(null);
    try {
      const conversion = await convertirUnite(Number(valeur), de, vers);
      setResultat(conversion.resultat);
    } catch (cause) {
      setResultat(null);
      setErreur(cause instanceof ErreurApi ? cause.detail : c.aide);
    }
  }

  return (
    <fieldset className="z-composition">
      <legend className="z-champ__libelle">{c.titre}</legend>
      <p className="z-info">{c.aide}</p>
      <div className="z-form__grille">
        <ChampNombre libelle={c.valeur} valeur={valeur} onChange={setValeur} />
        <ChampSelect libelle={c.de} valeur={de} options={unites} onChange={setDe} />
        <ChampSelect libelle={c.vers} valeur={vers} options={unites} onChange={setVers} />
        <div className="z-champ z-champ--aligne-bas">
          <Bouton variante="secondaire" onClick={() => void convertir()}>
            {t.actions.calculer}
          </Bouton>
        </div>
      </div>
      {resultat !== null && (
        <p className="z-info" role="status">
          {c.resultat} : {f.nombre(resultat, 4)} {c.unites[vers as keyof typeof c.unites]}
        </p>
      )}
      {erreur !== null && (
        <p className="z-erreur" role="alert">
          {erreur}
        </p>
      )}
    </fieldset>
  );
}
