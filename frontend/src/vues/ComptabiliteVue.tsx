import { useEffect, useState, type ReactElement } from 'react';
import {
  chargerBilan,
  chargerSaisons,
  depenses,
  ressourcesExportables,
  ruches,
  sites,
  telechargerBilanAnnuel,
  telechargerRessource,
} from '../api/client';
import type {
  BilanExploitation,
  CategorieDepense,
  ComparaisonSaisons,
  Depense,
  DepenseCorps,
  Ruche,
  Site,
} from '../api/types';
import { CATEGORIES_DEPENSE } from '../api/types';
import { gabarit } from '../i18n/console';
import { useFormats, useT } from '../i18n/langue';
import { messageErreur, useRessource } from '../hooks';
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

/**
 * Dépenses, bilan et archives (SPRINT-27, lot E).
 *
 * <p>Trois choses que l'écran répète, parce qu'elles sont faciles à oublier une
 * fois le chiffre affiché :
 *
 * <ol>
 *   <li>les recettes sont une <strong>valorisation</strong> au prix paramétré,
 *       pas un chiffre d'affaires — Zümm ne connaît pas les prix de vente ;
 *   <li>les dépenses non affectées ne sont <strong>pas réparties</strong> : une
 *       assurance ne se divise pas par le nombre de ruches ;
 *   <li>la période est <strong>demandée</strong>, jamais devinée — un bilan « sur
 *       douze mois » recule chaque jour, et deux consultations ne portent alors
 *       pas sur la même chose.
 * </ol>
 */
export function ComptabiliteVue(): ReactElement {
  const t = useT();
  const f = useFormats();
  const etat = useRessource<Depense, DepenseCorps>(depenses);

  const anneeCourante = new Date().getFullYear();
  const [debut, setDebut] = useState(`${anneeCourante}-01-01`);
  const [fin, setFin] = useState(`${anneeCourante}-12-31`);
  const [bilan, setBilan] = useState<BilanExploitation | null>(null);
  const [saisons, setSaisons] = useState<ComparaisonSaisons[]>([]);
  const [exportables, setExportables] = useState<string[]>([]);
  const [ressource, setRessource] = useState('recoltes');
  const [format, setFormat] = useState<'csv' | 'txt' | 'xlsx'>('csv');
  const [optRuches, setOptRuches] = useState<Option[]>([]);
  const [optSites, setOptSites] = useState<Option[]>([]);
  const [erreur, setErreur] = useState<string | null>(null);

  const [ouvert, setOuvert] = useState(false);
  const [edition, setEdition] = useState<Depense | null>(null);
  const [libelle, setLibelle] = useState('');
  const [categorie, setCategorie] = useState<CategorieDepense>('autre');
  const [montant, setMontant] = useState('');
  const [date, setDate] = useState('');
  const [rucheId, setRucheId] = useState('');
  const [siteId, setSiteId] = useState('');
  const [note, setNote] = useState('');

  useEffect(() => {
    void chargerSaisons().then(setSaisons).catch(() => setSaisons([]));
    void ressourcesExportables().then(setExportables).catch(() => setExportables([]));
    void ruches
      .lister()
      .then((l: Ruche[]) =>
        setOptRuches([
          { valeur: '', libelle: t.champs.aucun },
          ...l.map((r) => ({ valeur: String(r.id), libelle: r.modele })),
        ]),
      )
      .catch(() => setOptRuches([{ valeur: '', libelle: t.champs.aucun }]));
    void sites
      .lister()
      .then((l: Site[]) =>
        setOptSites([
          { valeur: '', libelle: t.champs.aucun },
          ...l.map((s) => ({ valeur: String(s.id), libelle: s.nom })),
        ]),
      )
      .catch(() => setOptSites([{ valeur: '', libelle: t.champs.aucun }]));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const colonnes: Colonne<Depense>[] = [
    { entete: t.recolte.date, rendu: (d) => f.date(d.dateDepense) },
    { entete: t.champs.libelle, rendu: (d) => d.libelle },
    { entete: t.champs.categorie, rendu: (d) => t.depense.categories[d.categorie] },
    { entete: t.depense.montant, rendu: (d) => `${f.nombre(d.montantEur, 2)} €` },
    {
      entete: t.depense.affectation,
      rendu: (d) => d.rucheModele ?? d.siteNom ?? <span className="z-info">{t.depense.aucune}</span>,
    },
  ];

  const ouvrir = (d: Depense | null) => {
    setEdition(d);
    setLibelle(d?.libelle ?? '');
    setCategorie(d?.categorie ?? 'autre');
    setMontant(d != null ? String(d.montantEur) : '');
    setDate(d?.dateDepense ?? '');
    setRucheId(d?.rucheId != null ? String(d.rucheId) : '');
    setSiteId(d?.siteId != null ? String(d.siteId) : '');
    setNote(d?.note ?? '');
    setErreur(null);
    setOuvert(true);
  };

  const enregistrer = async () => {
    if (libelle.trim() === '' || montant === '' || date === '') {
      setErreur(t.etats.champsRequis);
      return;
    }
    const corps: DepenseCorps = {
      libelle: libelle.trim(),
      categorie,
      montantEur: Number(montant),
      dateDepense: date,
      rucheId: rucheId === '' ? null : Number(rucheId),
      siteId: siteId === '' ? null : Number(siteId),
      note: note.trim() === '' ? null : note.trim(),
    };
    try {
      await (edition ? etat.mettreAJour(edition.id, corps) : etat.creer(corps));
      setOuvert(false);
      setBilan(null);
    } catch (cause) {
      setErreur(cause instanceof Error ? cause.message : t.etats.erreur);
    }
  };

  const calculer = () => {
    setErreur(null);
    void chargerBilan(debut, fin)
      .then(setBilan)
      .catch((cause) => setErreur(messageErreur(cause, t.etats.serviceIndisponible)));
  };

  return (
    <CorpsSection
      titre={t.onglets.comptabilite}
      sousTitre={t.soustitres.comptabilite}
      etat={etat}
      onNouveau={() => ouvrir(null)}
    >
      {erreur && (
        <div className="z-erreur" role="alert">
          {erreur}
        </div>
      )}

      {etat.elements.length > 0 && (
        <Table
          colonnes={colonnes}
          elements={etat.elements}
          onModifier={ouvrir}
          onSupprimer={(e) => void etat.supprimer(e.id)}
        />
      )}

      {/* ─── Bilan ──────────────────────────────────────────────────────── */}
      <article className="z-carte">
        <h3 className="z-carte__titre">{t.bilan.titre}</h3>
        <p className="z-info">{t.bilan.aide}</p>
        <div className="z-form__grille">
          <ChampDate libelle={t.tableau.du} valeur={debut} onChange={setDebut} />
          <ChampDate libelle={t.tableau.au} valeur={fin} onChange={setFin} />
          <div className="z-champ z-champ--aligne-bas">
            <Bouton variante="primaire" onClick={calculer}>
              {t.tableau.afficher}
            </Bouton>
          </div>
        </div>

        {bilan && (
          <>
            <p className="z-info">
              <strong>{f.nombre(bilan.productionMielKg, 1)} kg</strong> ·{' '}
              {t.bilan.recettes} : <strong>{f.nombre(bilan.recettesEur, 2)} €</strong> ·{' '}
              {t.bilan.depenses} : <strong>{f.nombre(bilan.depensesEur, 2)} €</strong> ·{' '}
              {t.bilan.resultat} : <strong>{f.nombre(bilan.resultatEur, 2)} €</strong>
            </p>
            {/* Dites entières, et jamais réparties : une assurance ne se divise
                pas par le nombre de colonies. */}
            <p className="z-info">
              {gabarit(t.bilan.nonAffectees, {
                montant: f.nombre(bilan.depensesNonAffectees, 2),
              })}
            </p>
            {bilan.parRuche.length > 0 && (
              <div className="z-table-enveloppe">
                <table className="z-table">
                  <thead>
                    <tr>
                      <th>{t.onglets.ruches}</th>
                      <th>{t.onglets.sites}</th>
                      <th>{t.bilan.production}</th>
                      <th>{t.bilan.depenses}</th>
                      <th>{t.bilan.resultat}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {bilan.parRuche.map((r) => (
                      <tr key={r.rucheId}>
                        <td>{r.rucheModele}</td>
                        <td>{r.siteNom ?? '—'}</td>
                        <td>{f.nombre(r.productionKg, 1)} kg</td>
                        <td>{f.nombre(r.depensesEur, 2)} €</td>
                        <td>{f.nombre(r.resultatEur, 2)} €</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </>
        )}
      </article>

      {/* ─── Saison contre saison ───────────────────────────────────────── */}
      <article className="z-carte">
        <h3 className="z-carte__titre">{t.saison.titre}</h3>
        <p className="z-info">{t.saison.aide}</p>
        {saisons.length === 0 ? (
          <p className="z-info">{t.etats.vide}</p>
        ) : (
          <div className="z-table-enveloppe">
            <table className="z-table">
              <thead>
                <tr>
                  <th>{t.saison.annee}</th>
                  <th>{t.bilan.production}</th>
                  <th>{t.saison.productives}</th>
                  <th>{t.saison.rendement}</th>
                  <th>{t.saison.bilanPdf}</th>
                </tr>
              </thead>
              <tbody>
                {saisons.map((s) => (
                  <tr key={s.annee}>
                    <td>{s.annee}</td>
                    <td>{f.nombre(s.productionMielKg, 1)} kg</td>
                    <td>{s.ruchesProductives}</td>
                    <td>
                      {/* Nul et non zéro : aucune ruche n'a produit, ce qui
                          n'est pas une saison catastrophique mais une absence
                          de saisie. */}
                      {s.rendementKg === null ? (
                        <span className="z-info">{t.saison.nonEvalue}</span>
                      ) : (
                        `${f.nombre(s.rendementKg, 1)} kg`
                      )}
                    </td>
                    <td>
                      <button
                        type="button"
                        className="z-lien"
                        onClick={() => void telechargerBilanAnnuel(s.annee).catch(() => undefined)}
                      >
                        {t.saison.telecharger}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </article>

      {/* ─── Export intégral ────────────────────────────────────────────── */}
      <article className="z-carte">
        <h3 className="z-carte__titre">{t.exportIntegral.titre}</h3>
        <p className="z-info">{t.exportIntegral.aide}</p>
        <div className="z-form__grille">
          <ChampSelect
            libelle={t.exportIntegral.ressource}
            valeur={ressource}
            options={exportables.map((r) => ({ valeur: r, libelle: r }))}
            onChange={setRessource}
          />
          <ChampSelect
            libelle={t.exportIntegral.format}
            valeur={format}
            options={[
              { valeur: 'csv', libelle: 'CSV' },
              { valeur: 'txt', libelle: 'TXT' },
              { valeur: 'xlsx', libelle: 'XLSX' },
            ]}
            onChange={(v) => setFormat(v as 'csv' | 'txt' | 'xlsx')}
          />
          <div className="z-champ z-champ--aligne-bas">
            <Bouton
              variante="secondaire"
              onClick={() =>
                void telechargerRessource(ressource, format).catch((cause: unknown) =>
                  setErreur(messageErreur(cause, t.etats.serviceIndisponible)),
                )
              }
            >
              {t.exportIntegral.telecharger}
            </Bouton>
          </div>
        </div>
      </article>

      {ouvert && (
        <Modale titre={t.onglets.comptabilite} onFermer={() => setOuvert(false)}>
          <form
            className="z-form"
            onSubmit={(e) => {
              e.preventDefault();
              void enregistrer();
            }}
          >
            <ChampTexte libelle={t.champs.libelle} valeur={libelle} onChange={setLibelle} requis />
            <div className="z-form__grille">
              <ChampSelect
                libelle={t.champs.categorie}
                valeur={categorie}
                options={CATEGORIES_DEPENSE.map((c) => ({
                  valeur: c,
                  libelle: t.depense.categories[c],
                }))}
                onChange={(v) => setCategorie(v as CategorieDepense)}
              />
              <ChampNombre libelle={t.depense.montant} valeur={montant} onChange={setMontant} />
              <ChampDate libelle={t.recolte.date} valeur={date} onChange={setDate} requis />
            </div>
            {/* Affectation facultative, et à deux niveaux : exiger un
                rattachement ferait inventer des affectations pour boucler une
                saisie, et la rentabilité par ruche en deviendrait fausse. */}
            <p className="z-info">{t.depense.aideAffectation}</p>
            <div className="z-form__grille">
              <ChampSelect
                libelle={t.recolte.ruche}
                valeur={rucheId}
                options={optRuches}
                onChange={setRucheId}
              />
              <ChampSelect
                libelle={t.champs.site}
                valeur={siteId}
                options={optSites}
                onChange={setSiteId}
              />
            </div>
            <ChampZone libelle={t.champs.note} valeur={note} onChange={setNote} />
            <div className="z-form__actions">
              <Bouton variante="fantome" onClick={() => setOuvert(false)}>
                {t.actions.annuler}
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
