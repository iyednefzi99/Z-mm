import { useEffect, useState, type ReactElement } from 'react';
import {
  ErreurApi,
  recolterEnLot,
  recoltes,
  ruches,
  tracerLot,
} from '../api/client';
import type {
  RapportLot,
  Recolte,
  RecolteCorps,
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
  const enregistrer = async (forcer = false) => {
    if (rucheId === '' || quantite === '') return;
    try {
      await etat.creer({
        rucheId: Number(rucheId),
        dateRecolte,
        quantiteKg: Number(quantite),
        typeMiel: typeMiel.trim() === '' ? null : typeMiel,
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
    </CorpsSection>
  );
}
