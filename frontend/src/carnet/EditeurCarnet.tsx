import { useEffect, useState, type ReactElement } from 'react';
import { gabarits, recupererPoints, recupererStatistiquesPoints } from '../api/client';
import type { Gabarit, PointReferentiel, StatistiquePoint } from '../api/types';
import { messageErreur } from '../hooks';
import { gabarit as modele } from '../i18n/console';
import { useT } from '../i18n/langue';
import { Bouton, ChampDate, ChampTexte, ChampZone } from '../ui/composants';
import { useDialogues } from '../ui/dialogues';
import { libellePoint, parCategorie } from './points';

/**
 * Éditeur des gabarits d'inspection (SPRINT-28, lot I).
 *
 * <p>Il vit dans l'écran de configuration parce que c'est une décision
 * d'exploitation, prise une fois : quelles cases figureront à la saisie engage
 * toutes les inspections à venir. Le mettre dans l'écran des visites en aurait
 * fait un réglage qu'on change en cours de route, et deux visites du même jour
 * n'auraient plus été comparables.
 *
 * <p><strong>Le référentiel est fermé.</strong> On coche des points existants ;
 * il n'y a aucun champ pour en inventer un, et il n'y en aura pas — c'est ce qui
 * garde les observations comparables d'un rucher à l'autre. Le serveur le tient
 * aussi : la table du référentiel est en lecture seule pour l'application.
 */
export function EditeurCarnet({ ecriture }: { ecriture: boolean }): ReactElement {
  const t = useT();
  const { confirmer } = useDialogues();
  const [referentiel, setReferentiel] = useState<PointReferentiel[]>([]);
  const [liste, setListe] = useState<Gabarit[]>([]);
  const [edition, setEdition] = useState<Gabarit | null>(null);
  const [ouvert, setOuvert] = useState(false);
  const [nom, setNom] = useState('');
  const [description, setDescription] = useState('');
  const [noyau, setNoyau] = useState({
    couvain: true,
    reine: true,
    cadres: true,
    temperament: true,
  });
  const [parDefaut, setParDefaut] = useState(false);
  const [actif, setActif] = useState(true);
  const [choisis, setChoisis] = useState<string[]>([]);
  const [erreur, setErreur] = useState<string | null>(null);

  const recharger = () => {
    void gabarits.lister().then(setListe).catch(() => setListe([]));
  };

  useEffect(() => {
    void recupererPoints().then(setReferentiel).catch(() => setReferentiel([]));
    recharger();
  }, []);

  const ouvrir = (g: Gabarit | null) => {
    setEdition(g);
    setNom(g?.nom ?? '');
    setDescription(g?.description ?? '');
    setNoyau({
      couvain: g?.noyauCouvain ?? true,
      reine: g?.noyauReine ?? true,
      cadres: g?.noyauCadres ?? true,
      temperament: g?.noyauTemperament ?? true,
    });
    setParDefaut(g?.parDefaut ?? false);
    setActif(g?.actif ?? true);
    setChoisis(g?.points ?? []);
    setErreur(null);
    setOuvert(true);
  };

  /** Coche ou décoche un point. L'ordre de sélection fait l'ordre d'affichage. */
  const basculerPoint = (code: string) => {
    setChoisis((actuels) =>
      actuels.includes(code) ? actuels.filter((c) => c !== code) : [...actuels, code],
    );
  };

  const enregistrer = async () => {
    if (nom.trim() === '') {
      setErreur(t.etats.champsRequis);
      return;
    }
    const corps = {
      nom: nom.trim(),
      description: description.trim() === '' ? null : description.trim(),
      noyauCouvain: noyau.couvain,
      noyauReine: noyau.reine,
      noyauCadres: noyau.cadres,
      noyauTemperament: noyau.temperament,
      parDefaut,
      actif,
      points: choisis,
    };
    try {
      if (edition === null) {
        await gabarits.creer(corps);
      } else {
        await gabarits.mettreAJour(edition.id, corps);
      }
      setOuvert(false);
      recharger();
    } catch (cause) {
      setErreur(messageErreur(cause, t.etats.erreur));
    }
  };

  const supprimer = async (g: Gabarit) => {
    const suite = await confirmer(
      modele(t.carnet.confirmationSuppression, { nom: g.nom }),
    );
    if (!suite) {
      return;
    }
    try {
      await gabarits.supprimer(g.id);
      recharger();
    } catch (cause) {
      setErreur(messageErreur(cause, t.etats.erreur));
    }
  };

  return (
    <section className="z-legal__section">
      <h2 className="z-legal__soustitre">{t.carnet.gabarits}</h2>
      <p>{t.carnet.aide}</p>

      {erreur !== null && (
        <div className="z-erreur" role="alert">
          {erreur}
        </div>
      )}

      {liste.length === 0 ? (
        <p className="z-info">{t.carnet.aucunGabarit}</p>
      ) : (
        <ul className="z-liste">
          {liste.map((g) => (
            <li key={g.id} className="z-liste__ligne">
              <span>
                <strong>{g.nom}</strong>
                {g.parDefaut ? ` · ${t.carnet.parDefaut}` : ''}
                {g.actif ? '' : ` · ${t.carnet.inactif}`}
                {` · ${String(g.points.length)} ${t.carnet.pointsRetenus.toLowerCase()}`}
              </span>
              {ecriture && (
                <span className="z-liste__actions">
                  <Bouton variante="fantome" onClick={() => ouvrir(g)}>
                    {t.actions.modifier}
                  </Bouton>
                  <Bouton variante="fantome" onClick={() => void supprimer(g)}>
                    {t.actions.supprimer}
                  </Bouton>
                </span>
              )}
            </li>
          ))}
        </ul>
      )}

      {ecriture && !ouvert && (
        <div className="z-form__actions">
          <Bouton variante="primaire" onClick={() => ouvrir(null)}>
            {t.carnet.nouveau}
          </Bouton>
        </div>
      )}

      {ouvert && (
        <form
          className="z-form"
          onSubmit={(e) => {
            e.preventDefault();
            void enregistrer();
          }}
        >
          <div className="z-form__grille">
            <ChampTexte libelle={t.carnet.nom} valeur={nom} onChange={setNom} />
          </div>
          <ChampZone
            libelle={t.carnet.description}
            valeur={description}
            onChange={setDescription}
          />

          <fieldset className="z-composition">
            <legend className="z-champ__libelle">{t.carnet.sectionsNoyau}</legend>
            <p className="z-info">{t.carnet.noyauAide}</p>
            <div className="z-form__grille">
              <Interrupteur
                libelle={t.carnet.noyauCouvain}
                valeur={noyau.couvain}
                onChange={(v) => setNoyau({ ...noyau, couvain: v })}
              />
              <Interrupteur
                libelle={t.carnet.noyauReine}
                valeur={noyau.reine}
                onChange={(v) => setNoyau({ ...noyau, reine: v })}
              />
              <Interrupteur
                libelle={t.carnet.noyauCadres}
                valeur={noyau.cadres}
                onChange={(v) => setNoyau({ ...noyau, cadres: v })}
              />
              <Interrupteur
                libelle={t.carnet.noyauTemperament}
                valeur={noyau.temperament}
                onChange={(v) => setNoyau({ ...noyau, temperament: v })}
              />
            </div>
          </fieldset>

          <fieldset className="z-composition">
            <legend className="z-champ__libelle">
              {t.carnet.pointsRetenus} ({choisis.length})
            </legend>
            {choisis.length === 0 && <p className="z-info">{t.carnet.aucunPoint}</p>}
            {parCategorie(referentiel).map(([categorie, points]) => (
              <div key={categorie} className="z-carnet__famille">
                <p className="z-carnet__titre">
                  {t.carnet.categories[categorie as keyof typeof t.carnet.categories]
                    ?? categorie}
                </p>
                <div className="z-form__grille">
                  {points.map((point) => (
                    <Interrupteur
                      key={point.code}
                      libelle={libellePoint(t.carnet.points, point)}
                      valeur={choisis.includes(point.code)}
                      onChange={() => basculerPoint(point.code)}
                    />
                  ))}
                </div>
              </div>
            ))}
          </fieldset>

          <div className="z-form__grille">
            <Interrupteur
              libelle={t.carnet.parDefaut}
              valeur={parDefaut}
              onChange={setParDefaut}
            />
            <Interrupteur libelle={t.carnet.actif} valeur={actif} onChange={setActif} />
          </div>

          <div className="z-form__actions">
            <Bouton variante="secondaire" onClick={() => setOuvert(false)}>
              {t.actions.annuler}
            </Bouton>
            <Bouton variante="primaire" type="submit">
              {t.actions.enregistrer}
            </Bouton>
          </div>
        </form>
      )}

      <StatistiquesPoints />
    </section>
  );
}

/** Case à cocher ordinaire : ici, « pas coché » veut bien dire « non retenu ». */
function Interrupteur({
  libelle,
  valeur,
  onChange,
}: {
  libelle: string;
  valeur: boolean;
  onChange: (valeur: boolean) => void;
}): ReactElement {
  return (
    <label className="z-champ z-champ--case">
      <input
        type="checkbox"
        checked={valeur}
        onChange={(e) => onChange(e.target.checked)}
      />
      <span className="z-champ__libelle">{libelle}</span>
    </label>
  );
}

/**
 * Ce que les cases ont réellement donné (SPRINT-33).
 *
 * <p>C'est le retour d'expérience qui manquait à l'éditeur : décider quels points
 * garder au gabarit sans savoir lesquels sont effectivement remplis revient à
 * composer à l'aveugle. Un point jamais regardé en trois mois n'a pas sa place
 * dans une grille de terrain.
 *
 * <p><strong>Le dénominateur est le nombre de fois où le point a été REGARDÉ</strong>,
 * jamais le nombre de visites : une visite éclair qui n'a rien coché ne doit pas
 * faire chuter le taux d'un point que personne n'a examiné ce jour-là. C'est la
 * raison d'être de la case à trois états, et elle se retrouve jusqu'ici.
 */
function StatistiquesPoints(): ReactElement {
  const t = useT();
  const [depuis, setDepuis] = useState('');
  const [jusqu, setJusqu] = useState('');
  const [lignes, setLignes] = useState<StatistiquePoint[] | null>(null);

  async function calculer(): Promise<void> {
    if (depuis === '' || jusqu === '') {
      return;
    }
    setLignes(await recupererStatistiquesPoints(depuis, jusqu));
  }

  return (
    <section className="z-encart">
      <h2 className="z-encart__titre">{t.carnet.statistiques}</h2>
      <p className="z-info">{t.carnet.statistiquesAide}</p>
      <div className="z-form__grille">
        <ChampDate libelle={t.carnet.periode} valeur={depuis} onChange={setDepuis} />
        <ChampDate libelle={t.carnet.periode} valeur={jusqu} onChange={setJusqu} />
        <div className="z-champ z-champ--aligne-bas">
          <Bouton
            variante="secondaire"
            disabled={depuis === '' || jusqu === ''}
            onClick={() => void calculer()}
          >
            {t.actions.calculer}
          </Bouton>
        </div>
      </div>

      {lignes !== null &&
        (lignes.length === 0 ? (
          <p className="z-info">{t.carnet.aucuneStatistique}</p>
        ) : (
          <div className="z-table-enveloppe">
            <table className="z-table">
              <thead>
                <tr>
                  <th>{t.champs.libelle}</th>
                  <th>{t.carnet.categorie}</th>
                  <th>{t.carnet.releves}</th>
                  <th>{t.carnet.presents}</th>
                  <th>{t.carnet.moyenne}</th>
                </tr>
              </thead>
              <tbody>
                {lignes.map((ligne) => (
                  <tr key={ligne.code}>
                    <td>{ligne.libelle}</td>
                    <td>{t.carnet.categories[ligne.categorie]}</td>
                    <td className="z-nombre">{ligne.releves}</td>
                    <td className="z-nombre">{ligne.presents}</td>
                    <td className="z-nombre">{ligne.moyenneEchelle ?? '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ))}
    </section>
  );
}
