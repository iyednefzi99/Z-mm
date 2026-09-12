import { useCallback, useEffect, useState, type ReactElement } from 'react';
import {
  chargerCouvert,
  chargerExposition,
  chargerFiabilite,
  chargerFloraisons,
  chargerMillesimes,
  chargerParcelles,
  chargerRotation,
  chargerZonesTraitees,
  constaterParcelle,
  declarerZoneTraitee,
  enregistrerFloraison,
  marquerParcelle,
  purgerCouvert,
  supprimerFloraison,
  supprimerZoneTraitee,
  verserCouvert,
} from '../api/client';
import { CLASSES_COUVERT, ORIGINES_ZONE_TRAITEE } from '../api/types';
import type {
  ClasseCouvert,
  CouvertRucher,
  ExpositionRucher,
  FiabiliteCouvert,
  FloraisonObservee,
  OrigineZoneTraitee,
  ParcelleCouvert,
  RessourceFlorale,
  ZoneTraitee,
} from '../api/types';
import { gabarit } from '../i18n/console';
import { useFormats, useT } from '../i18n/langue';
import {
  Bouton,
  ChampDate,
  ChampNombre,
  ChampSelect,
  ChampTexte,
  ChampZone,
  Pastille,
} from '../ui/composants';
import { useDialogues } from '../ui/dialogues';
import { useToasts } from '../ui/toasts';

/**
 * Environnement d'un rucher : couvert du sol et floraisons (SPRINT-32, lot H).
 *
 * <p>Le dernier lot du plan de couverture, et le seul terrain où un concurrent —
 * BeeGIS — joue sur le domaine que Zümm revendique.
 *
 * <p><strong>Ce panneau dit toujours d'où il parle.</strong> La source et le
 * millésime accompagnent les surfaces sans exception : « 42 % de cultures »
 * n'engage personne tant qu'on ne sait pas de quelle année et de quel jeu de
 * données cela vient. C'est la ligne « millésime des données environnementales »
 * du §13, et elle n'est pas décorative — les parcelles tournent.
 *
 * <p><strong>Et il dit ce qu'il ignore.</strong> Sans couche versée, il l'écrit
 * au lieu d'afficher des surfaces nulles qui se liraient comme un environnement
 * vide ; avec une couche partielle, il affiche la part réellement décrite.
 *
 * <p><strong>SPRINT-33.</strong> Le panneau ne faisait que LIRE. La migration
 * V32, les services de vérification terrain et les zones traitées existaient
 * sans qu'aucun écran ne les atteigne : la couche se versait par appel d'API, le
 * doute posé sur une parcelle ne pouvait pas être levé, et une zone traitée
 * déclarée ne se voyait nulle part. Les sections ci-dessous ferment cet écart —
 * le serveur ne gagne aucune capacité, il en perd une d'inaccessible.
 */
export function PanneauEnvironnement({
  siteId,
  ressources,
}: {
  siteId: number;
  /**
   * Ressources florales DÉCLARÉES du rucher, passées par le parent.
   *
   * <p>Elles ne sont pas rechargées ici : l'écran qui monte ce panneau tient
   * déjà le site. Aller les rechercher ajouterait un aller-retour pour une
   * donnée présente à trois lignes de là.
   */
  ressources: RessourceFlorale[];
}): ReactElement {
  const t = useT();
  const f = useFormats();
  const [couvert, setCouvert] = useState<CouvertRucher | null>(null);
  const [rotation, setRotation] = useState<CouvertRucher[]>([]);
  const [floraisons, setFloraisons] = useState<FloraisonObservee[]>([]);

  const rechargerCouvert = useCallback(() => {
    chargerCouvert(siteId).then(setCouvert).catch(() => setCouvert(null));
    chargerRotation(siteId).then(setRotation).catch(() => setRotation([]));
  }, [siteId]);

  const rechargerFloraisons = useCallback(() => {
    chargerFloraisons(siteId).then(setFloraisons).catch(() => setFloraisons([]));
  }, [siteId]);

  useEffect(() => {
    rechargerCouvert();
    rechargerFloraisons();
  }, [rechargerCouvert, rechargerFloraisons]);

  const libelle = (classe: ClasseCouvert): string => t.environnement.classes[classe];

  return (
    <section className="z-composition">
      <h3 className="z-champ__libelle">{t.environnement.titre}</h3>

      {couvert === null || couvert.millesime === null ? (
        // Rien n'arrive tout seul : c'est le coût assumé de ne rien aller
        // chercher dehors (ADR-015), et il vaut mieux l'écrire.
        <p className="z-info">{t.environnement.aucuneCouche}</p>
      ) : (
        <>
          <p className="z-info">
            {t.environnement.rayon} : {couvert.rayonKm} km · {t.environnement.millesime}{' '}
            {couvert.millesime} · {t.environnement.source} : {couvert.source}
          </p>

          <div className="z-table-enveloppe">
            <table className="z-table">
              <thead>
                <tr>
                  <th>{t.environnement.classe}</th>
                  <th>{t.environnement.surface}</th>
                  <th>{t.environnement.part}</th>
                </tr>
              </thead>
              <tbody>
                {couvert.surfaces.map((surface) => (
                  <tr key={surface.classe}>
                    <td>{libelle(surface.classe)}</td>
                    <td>{f.nombre(surface.surfaceHa)} ha</td>
                    <td>{surface.part === null ? '—' : `${surface.part} %`}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <p className="z-info">
            {t.environnement.couverte} :{' '}
            {couvert.couverte === null ? '—' : `${couvert.couverte} %`}
          </p>
          <p className="z-info">{t.environnement.aideCouverte}</p>

          {couvert.distanceCultureM !== null && (
            <>
              <p className="z-info">
                {t.environnement.distanceCulture} : {f.distance(couvert.distanceCultureM)}
              </p>
              {/* La phrase qui empêche de lire cette distance pour ce qu'elle
                  n'est pas. */}
              <p className="z-info">{t.environnement.aideCulture}</p>
            </>
          )}
        </>
      )}

      {rotation.length > 1 && (
        <>
          <h4 className="z-champ__libelle">{t.environnement.rotation}</h4>
          <div className="z-table-enveloppe">
            <table className="z-table">
              <thead>
                <tr>
                  <th>{t.environnement.millesime}</th>
                  {couvert?.surfaces.map((s) => (
                    <th key={s.classe}>{libelle(s.classe)}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {rotation.map((annee) => (
                  <tr key={annee.millesime}>
                    <td>{annee.millesime}</td>
                    {couvert?.surfaces.map((colonne) => {
                      const trouvee = annee.surfaces.find((s) => s.classe === colonne.classe);
                      return (
                        <td key={colonne.classe} className="z-nombre">
                          {trouvee === undefined ? '—' : `${f.nombre(trouvee.surfaceHa)} ha`}
                        </td>
                      );
                    })}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}

      <SectionParcelles siteId={siteId} millesime={couvert?.millesime ?? null} />

      <h4 className="z-champ__libelle">{t.environnement.floraisons}</h4>
      <p className="z-info">{t.environnement.floraisonAide}</p>
      {floraisons.length === 0 ? (
        <p className="z-info">{t.environnement.aucuneFloraison}</p>
      ) : (
        <ul className="z-liste">
          {floraisons.map((floraison) => (
            <li key={floraison.id} className="z-liste__ligne">
              <span>
                <strong>
                  {floraison.ressource} · {floraison.annee}
                </strong>
                <br />
                <small>
                  {t.environnement.debut} {f.date(floraison.dateDebut)}
                  {floraison.datePic !== null
                    ? ` · ${t.environnement.pic} ${f.date(floraison.datePic)}`
                    : ''}
                </small>
              </span>
              {floraison.ecartJours !== null && (
                <span>
                  {gabarit('{n} {jours} {sens}', {
                    n: String(Math.abs(floraison.ecartJours)),
                    jours: t.environnement.jours,
                    sens:
                      floraison.ecartJours < 0
                        ? t.environnement.enAvance
                        : t.environnement.enRetard,
                  })}
                </span>
              )}
              <button
                type="button"
                className="z-lien z-lien--danger"
                onClick={() => {
                  void supprimerFloraison(floraison.id).then(rechargerFloraisons);
                }}
              >
                {t.actions.supprimer}
              </button>
            </li>
          ))}
        </ul>
      )}

      <FormulaireFloraison
        ressources={ressources}
        surAjout={rechargerFloraisons}
      />

      <SectionExposition siteId={siteId} />
      <SectionZones />
      <SectionCouche surChangement={rechargerCouvert} />
    </section>
  );
}

/**
 * Ground truthing : ce que le terrain dit de la couche (SPRINT-33).
 *
 * <p>Deux gestes, et un seul principe. Le <strong>doute</strong> se pose et se
 * lève à la main — le déduire fabriquerait une tournée de vérification que
 * personne n'a demandée. Le <strong>constat</strong>, lui, s'écrit À CÔTÉ de la
 * classe de la source : écraser `classe` raconterait que le RPG avait raison
 * depuis le début, et plus personne ne pourrait mesurer ce que vaut un
 * millésime. Les deux colonnes cohabitent donc à l'écran comme en base.
 */
function SectionParcelles({
  siteId,
  millesime,
}: {
  siteId: number;
  millesime: number | null;
}): ReactElement {
  const t = useT();
  const f = useFormats();
  const [parcelles, setParcelles] = useState<ParcelleCouvert[]>([]);
  const [fiabilite, setFiabilite] = useState<FiabiliteCouvert | null>(null);
  const [enAttente, setEnAttente] = useState(true);
  const [constatId, setConstatId] = useState<number | null>(null);
  const [classe, setClasse] = useState<ClasseCouvert>('culture');
  const [constateLe, setConstateLe] = useState(() => new Date().toISOString().slice(0, 10));
  const [note, setNote] = useState('');

  const recharger = useCallback(() => {
    chargerParcelles(siteId, enAttente)
      .then(setParcelles)
      .catch(() => setParcelles([]));
    if (millesime !== null) {
      chargerFiabilite(millesime)
        .then(setFiabilite)
        .catch(() => setFiabilite(null));
    }
  }, [siteId, enAttente, millesime]);

  useEffect(recharger, [recharger]);

  async function basculer(parcelle: ParcelleCouvert): Promise<void> {
    await marquerParcelle(parcelle.id, !parcelle.aConfirmer);
    recharger();
  }

  async function constater(): Promise<void> {
    if (constatId === null) {
      return;
    }
    await constaterParcelle(constatId, {
      classeConstatee: classe,
      constateLe,
      note: note.trim() === '' ? null : note.trim(),
    });
    setConstatId(null);
    setNote('');
    recharger();
  }

  return (
    <>
      <h4 className="z-champ__libelle">{t.environnement.parcelles}</h4>
      <p className="z-info">{t.environnement.parcellesAide}</p>

      {fiabilite !== null && (
        <>
          <p className="z-info">
            <strong>{t.environnement.fiabilite}</strong> · {t.environnement.millesime}{' '}
            {fiabilite.millesime} · {t.environnement.parcellesTotal} {fiabilite.parcelles} ·{' '}
            {t.environnement.verifiees} {fiabilite.verifiees} · {t.environnement.dementies}{' '}
            {fiabilite.dementies} · {t.environnement.enAttente} {fiabilite.enAttente}
          </p>
          <p className="z-info">{t.environnement.fiabiliteAide}</p>
        </>
      )}

      <ChampSelect
        libelle={t.environnement.afficher}
        valeur={enAttente ? 'attente' : 'toutes'}
        options={[
          { valeur: 'attente', libelle: t.environnement.enAttente },
          { valeur: 'toutes', libelle: t.environnement.toutes },
        ]}
        onChange={(valeur) => setEnAttente(valeur === 'attente')}
      />

      {parcelles.length === 0 ? (
        <p className="z-info">{t.environnement.aucuneParcelle}</p>
      ) : (
        <div className="z-table-enveloppe">
          <table className="z-table">
            <thead>
              <tr>
                <th>{t.environnement.classeSource}</th>
                <th>{t.environnement.classeConstatee}</th>
                <th>{t.environnement.surfaceParcelle}</th>
                <th>{t.environnement.millesime}</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {parcelles.map((parcelle) => (
                <tr key={parcelle.id}>
                  <td>{t.environnement.classes[parcelle.classe]}</td>
                  <td>
                    {parcelle.classeConstatee === null ? (
                      '—'
                    ) : (
                      <Pastille
                        ton={
                          parcelle.classeConstatee === parcelle.classe ? 'succes' : 'attention'
                        }
                      >
                        {t.environnement.classes[parcelle.classeConstatee]}
                      </Pastille>
                    )}
                    {parcelle.constateLe !== null && (
                      <>
                        <br />
                        <small>
                          {t.environnement.constateLe} {f.date(parcelle.constateLe)}
                        </small>
                      </>
                    )}
                  </td>
                  <td className="z-nombre">{f.nombre(parcelle.surfaceHa)} ha</td>
                  <td>{parcelle.millesime}</td>
                  <td>
                    <button
                      type="button"
                      className="z-lien"
                      onClick={() => void basculer(parcelle)}
                    >
                      {parcelle.aConfirmer
                        ? t.environnement.demarquer
                        : t.environnement.marquer}
                    </button>{' '}
                    <button
                      type="button"
                      className="z-lien"
                      onClick={() => setConstatId(parcelle.id)}
                    >
                      {t.environnement.constater}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {constatId !== null && (
        <form
          className="z-form__grille"
          onSubmit={(evenement) => {
            evenement.preventDefault();
            void constater();
          }}
        >
          <ChampSelect
            libelle={t.environnement.classeConstatee}
            valeur={classe}
            options={CLASSES_COUVERT.map((c) => ({
              valeur: c,
              libelle: t.environnement.classes[c],
            }))}
            onChange={(valeur) => setClasse(valeur as ClasseCouvert)}
          />
          <ChampDate
            libelle={t.environnement.constateLe}
            valeur={constateLe}
            onChange={setConstateLe}
            requis
          />
          <ChampZone
            libelle={t.environnement.noteConstat}
            valeur={note}
            onChange={setNote}
          />
          <Bouton variante="primaire" type="submit">
            {t.actions.enregistrer}
          </Bouton>
          <Bouton onClick={() => setConstatId(null)}>{t.actions.annuler}</Bouton>
        </form>
      )}
    </>
  );
}

/**
 * Noter une floraison observée.
 *
 * <p>Le formulaire ne propose que les ressources DÉCLARÉES du rucher : le
 * déclaratif prévoit, l'observé constate, et les deux parlent de la même
 * ressource ou l'écart en jours ne veut rien dire.
 */
function FormulaireFloraison({
  ressources,
  surAjout,
}: {
  ressources: RessourceFlorale[];
  surAjout: () => void;
}): ReactElement {
  const t = useT();
  const [ressourceId, setRessourceId] = useState('');
  const [annee, setAnnee] = useState(() => String(new Date().getFullYear()));
  const [debut, setDebut] = useState('');
  const [pic, setPic] = useState('');
  const [fin, setFin] = useState('');
  const [abondance, setAbondance] = useState('');
  const [note, setNote] = useState('');

  async function enregistrer(): Promise<void> {
    if (ressourceId === '') {
      return;
    }
    await enregistrerFloraison({
      ressourceId: Number(ressourceId),
      annee: Number(annee),
      dateDebut: debut,
      datePic: pic === '' ? null : pic,
      dateFin: fin === '' ? null : fin,
      abondance: abondance === '' ? null : Number(abondance),
      note: note.trim() === '' ? null : note.trim(),
    });
    setDebut('');
    setPic('');
    setFin('');
    setNote('');
    surAjout();
  }

  if (ressources.length === 0) {
    return <p className="z-info">{t.champs.aucun}</p>;
  }

  return (
    <form
      className="z-form__grille"
      onSubmit={(evenement) => {
        evenement.preventDefault();
        void enregistrer();
      }}
    >
      <p className="z-info">{t.environnement.floraisonComplete}</p>
      <ChampSelect
        libelle={t.environnement.ressource}
        valeur={ressourceId}
        options={[
          { valeur: '', libelle: t.champs.aucun },
          ...ressources.map((r) => ({
            valeur: String(r.id),
            libelle: t.referentielSite.ressources[r.ressource],
          })),
        ]}
        onChange={setRessourceId}
      />
      <ChampNombre
        libelle={t.environnement.annee}
        valeur={annee}
        onChange={setAnnee}
        pas="1"
        requis
      />
      <ChampDate
        libelle={t.environnement.debut}
        valeur={debut}
        onChange={setDebut}
        requis
      />
      <ChampDate libelle={t.environnement.pic} valeur={pic} onChange={setPic} />
      <ChampDate libelle={t.environnement.fin} valeur={fin} onChange={setFin} />
      <ChampSelect
        libelle={t.environnement.abondance}
        valeur={abondance}
        options={[
          { valeur: '', libelle: t.champs.aucun },
          ...Object.entries(t.environnement.abondances).map(([niveau, texte]) => ({
            valeur: niveau,
            libelle: texte,
          })),
        ]}
        onChange={setAbondance}
      />
      <ChampZone libelle={t.champs.note} valeur={note} onChange={setNote} />
      <Bouton variante="primaire" type="submit">
        {t.environnement.nouvelleFloraison}
      </Bouton>
    </form>
  );
}

/**
 * Ce qui a été déclaré autour de CE rucher (SPRINT-33).
 *
 * <p>La distance à une culture ne dit rien de ce qui y a été épandu : cette
 * section-là parle de traitements déclarés, et elle nomme sa source à chaque
 * ligne. Le nombre de zones encore sous délai de rentrée est la seule donnée
 * qu'un apiculteur regarde avant de partir travailler.
 */
function SectionExposition({ siteId }: { siteId: number }): ReactElement {
  const t = useT();
  const f = useFormats();
  const [exposition, setExposition] = useState<ExpositionRucher | null>(null);

  useEffect(() => {
    chargerExposition(siteId)
      .then(setExposition)
      .catch(() => setExposition(null));
  }, [siteId]);

  return (
    <>
      <h4 className="z-champ__libelle">{t.environnement.exposition}</h4>
      <p className="z-info">{t.environnement.expositionAide}</p>
      {exposition === null || exposition.declarations === 0 ? (
        <p className="z-info">{t.environnement.aucuneExposition}</p>
      ) : (
        <>
          <p className="z-info">
            {t.environnement.declarations} : {exposition.declarations}
            {exposition.distanceMinM !== null && (
              <>
                {' · '}
                {t.environnement.distanceMin} : {f.distance(exposition.distanceMinM)}
              </>
            )}
            {exposition.derniereDeclaration !== null && (
              <>
                {' · '}
                {t.environnement.derniereDeclaration} :{' '}
                {f.date(exposition.derniereDeclaration)}
              </>
            )}
          </p>
          {exposition.sousDelaiRentree > 0 && (
            <p className="z-info">
              <Pastille ton="attention">
                {t.environnement.sousDelaiRentree} : {exposition.sousDelaiRentree}
              </Pastille>
            </p>
          )}
          <ListeZones zones={exposition.zones} />
        </>
      )}
    </>
  );
}

/** Zones traitées, en lecture seule : la même liste sert au rucher et au registre. */
function ListeZones({
  zones,
  surSuppression,
}: {
  zones: ZoneTraitee[];
  surSuppression?: (zone: ZoneTraitee) => void;
}): ReactElement {
  const t = useT();
  const f = useFormats();

  return (
    <div className="z-table-enveloppe">
      <table className="z-table">
        <thead>
          <tr>
            <th>{t.environnement.dateTraitement}</th>
            <th>{t.environnement.substance}</th>
            <th>{t.environnement.origine}</th>
            <th>{t.environnement.delaiRentree}</th>
            <th>{t.environnement.surfaceParcelle}</th>
            {surSuppression !== undefined && <th />}
          </tr>
        </thead>
        <tbody>
          {zones.map((zone) => (
            <tr key={zone.id}>
              <td>{f.date(zone.dateTraitement)}</td>
              <td>{zone.substance ?? '—'}</td>
              <td>{t.environnement.origines[zone.origine]}</td>
              <td className="z-nombre">{zone.delaiRentreeH ?? '—'}</td>
              <td className="z-nombre">{f.nombre(zone.surfaceHa)} ha</td>
              {surSuppression !== undefined && (
                <td>
                  <button
                    type="button"
                    className="z-lien z-lien--danger"
                    onClick={() => surSuppression(zone)}
                  >
                    {t.actions.supprimer}
                  </button>
                </td>
              )}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/**
 * Le registre des zones traitées déclarées (SPRINT-33).
 *
 * <p>L'emprise se verse en GeoJSON plutôt que de se dessiner : un éditeur de
 * polygone est un projet à lui seul, et la donnée arrive presque toujours d'un
 * fichier — un avis officiel, une capture d'un voisin. Le champ accepte donc ce
 * qui existe déjà, et refuse ce qu'il ne sait pas lire au lieu de le deviner.
 */
function SectionZones(): ReactElement {
  const t = useT();
  const toasts = useToasts();
  const dialogues = useDialogues();
  const [zones, setZones] = useState<ZoneTraitee[]>([]);
  const [ouvert, setOuvert] = useState(false);
  const [date, setDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [substance, setSubstance] = useState('');
  const [origine, setOrigine] = useState<OrigineZoneTraitee>('voisin_declare');
  const [delai, setDelai] = useState('');
  const [note, setNote] = useState('');
  const [geometrie, setGeometrie] = useState('');

  const recharger = useCallback(() => {
    chargerZonesTraitees()
      .then(setZones)
      .catch(() => setZones([]));
  }, []);

  useEffect(recharger, [recharger]);

  async function declarer(): Promise<void> {
    let emprise: unknown;
    try {
      emprise = JSON.parse(geometrie);
    } catch {
      toasts.erreur(t.environnement.fichierInvalide);
      return;
    }
    await declarerZoneTraitee({
      geometrie: emprise,
      dateTraitement: date,
      substance: substance.trim() === '' ? null : substance.trim(),
      origine,
      delaiRentreeH: delai === '' ? null : Number(delai),
      note: note.trim() === '' ? null : note.trim(),
    });
    setGeometrie('');
    setSubstance('');
    setNote('');
    setOuvert(false);
    recharger();
  }

  async function supprimer(zone: ZoneTraitee): Promise<void> {
    if (!(await dialogues.confirmer(t.environnement.supprimerZone))) {
      return;
    }
    await supprimerZoneTraitee(zone.id);
    recharger();
  }

  return (
    <>
      <h4 className="z-champ__libelle">{t.environnement.zones}</h4>
      <p className="z-info">{t.environnement.zonesAide}</p>
      {zones.length === 0 ? (
        <p className="z-info">{t.environnement.aucuneZone}</p>
      ) : (
        <ListeZones zones={zones} surSuppression={(zone) => void supprimer(zone)} />
      )}

      {!ouvert ? (
        <Bouton onClick={() => setOuvert(true)}>{t.environnement.declarerZone}</Bouton>
      ) : (
        <form
          className="z-form__grille"
          onSubmit={(evenement) => {
            evenement.preventDefault();
            void declarer();
          }}
        >
          <ChampDate
            libelle={t.environnement.dateTraitement}
            valeur={date}
            onChange={setDate}
            requis
          />
          <ChampTexte
            libelle={t.environnement.substance}
            valeur={substance}
            onChange={setSubstance}
          />
          <ChampSelect
            libelle={t.environnement.origine}
            valeur={origine}
            options={ORIGINES_ZONE_TRAITEE.map((o) => ({
              valeur: o,
              libelle: t.environnement.origines[o],
            }))}
            onChange={(valeur) => setOrigine(valeur as OrigineZoneTraitee)}
          />
          <ChampNombre
            libelle={t.environnement.delaiRentree}
            valeur={delai}
            onChange={setDelai}
            pas="1"
            min={0}
          />
          <ChampZone
            libelle={t.environnement.zoneGeometrie}
            valeur={geometrie}
            onChange={setGeometrie}
          />
          <ChampZone libelle={t.champs.note} valeur={note} onChange={setNote} />
          <Bouton variante="primaire" type="submit">
            {t.actions.enregistrer}
          </Bouton>
          <Bouton onClick={() => setOuvert(false)}>{t.actions.annuler}</Bouton>
        </form>
      )}
    </>
  );
}

/**
 * Versement et purge d'un millésime de couche (SPRINT-33).
 *
 * <p>Le versement REMPLACE le millésime : verser deux fois sans purger
 * doublerait toutes les surfaces, et le panneau afficherait des pourcentages
 * supérieurs à cent. La purge est donc offerte à côté du versement, pas cachée
 * dans une console d'administration.
 */
function SectionCouche({ surChangement }: { surChangement: () => void }): ReactElement {
  const t = useT();
  const toasts = useToasts();
  const dialogues = useDialogues();
  const [millesimes, setMillesimes] = useState<number[]>([]);
  const [source, setSource] = useState('');
  const [millesime, setMillesime] = useState(() => String(new Date().getFullYear()));
  const [enCours, setEnCours] = useState(false);

  const recharger = useCallback(() => {
    chargerMillesimes()
      .then(setMillesimes)
      .catch(() => setMillesimes([]));
  }, []);

  useEffect(recharger, [recharger]);

  async function verser(fichier: File): Promise<void> {
    setEnCours(true);
    try {
      const collection: unknown = JSON.parse(await fichier.text());
      const bilan = await verserCouvert(source, Number(millesime), collection);
      toasts.succes(
        gabarit(t.environnement.verse, { nombre: String(bilan.polygones) }),
      );
      recharger();
      surChangement();
    } catch {
      // Un GeoJSON illisible est une erreur de l'utilisateur, pas une panne :
      // on le nomme et on s'arrête là, sans rien verser de partiel.
      toasts.erreur(t.environnement.fichierInvalide);
    } finally {
      setEnCours(false);
    }
  }

  async function purger(annee: number): Promise<void> {
    if (!(await dialogues.confirmer(t.environnement.purgeConfirmation))) {
      return;
    }
    const bilan = await purgerCouvert(annee);
    toasts.succes(gabarit(t.environnement.purge, { nombre: String(bilan.supprimes) }));
    recharger();
    surChangement();
  }

  return (
    <>
      <h4 className="z-champ__libelle">{t.environnement.millesimes}</h4>
      {millesimes.length === 0 ? (
        <p className="z-info">{t.environnement.aucunMillesime}</p>
      ) : (
        <ul className="z-liste-simple">
          {millesimes.map((annee) => (
            <li key={annee}>
              <Pastille>{String(annee)}</Pastille>{' '}
              <button
                type="button"
                className="z-lien z-lien--danger"
                onClick={() => void purger(annee)}
              >
                {t.environnement.purger}
              </button>
            </li>
          ))}
        </ul>
      )}

      <p className="z-info">{t.environnement.verseAide}</p>
      <div className="z-form__grille">
        <ChampTexte
          libelle={t.environnement.sourceLibelle}
          valeur={source}
          onChange={setSource}
        />
        <ChampNombre
          libelle={t.environnement.millesime}
          valeur={millesime}
          onChange={setMillesime}
          pas="1"
        />
        <label className="z-champ">
          <span className="z-champ__libelle">{t.environnement.fichier}</span>
          <input
            className="z-input"
            type="file"
            accept=".geojson,.json,application/geo+json,application/json"
            disabled={enCours || source.trim() === ''}
            onChange={(e) => {
              const fichier = e.target.files?.[0];
              if (fichier !== undefined) {
                void verser(fichier);
              }
            }}
          />
        </label>
      </div>
    </>
  );
}
