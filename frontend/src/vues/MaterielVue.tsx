import { useEffect, useState, type ReactElement } from 'react';
import {
  consommables,
  entretenirMateriel,
  materiels,
  mouvementerStock,
  sites,
} from '../api/client';
import type {
  CategorieConsommable,
  CategorieMateriel,
  Consommable,
  ConsommableCorps,
  EtatMateriel,
  Materiel,
  MaterielCorps,
  Site,
} from '../api/types';
import {
  CATEGORIES_CONSOMMABLE,
  CATEGORIES_MATERIEL,
  ETATS_MATERIEL,
} from '../api/types';
import { gabarit } from '../i18n/console';
import { useFormats, useT } from '../i18n/langue';
import { useRessource, useRoles } from '../hooks';
import { peutEcrire } from '../routage/routes';
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
 * L'atelier : inventaire du matériel et stock de consommables (SPRINT-27, lot E).
 *
 * <p>Deux tables sur un même écran, et c'est délibéré : ce sont les deux
 * réponses à la même question — « qu'est-ce que je possède, et qu'est-ce qui me
 * manque ». Les séparer aurait fait deux entrées de menu pour un seul geste, et
 * personne n'ouvre deux écrans avant de partir à la miellerie.
 *
 * <p><strong>Ce que l'écran met en avant.</strong> L'entretien en retard et le
 * stock sous seuil, parce que ce sont les seules lignes qui appellent une
 * action ; le reste est un inventaire, et un inventaire se consulte, il ne se
 * surveille pas.
 */
export function MaterielVue(): ReactElement {
  const t = useT();
  const f = useFormats();
  const roles = useRoles();
  const ecriture = peutEcrire('materiel', roles);
  const etat = useRessource<Materiel, MaterielCorps>(materiels);

  const [stock, setStock] = useState<Consommable[]>([]);
  const [optSites, setOptSites] = useState<Option[]>([]);
  const [erreur, setErreur] = useState<string | null>(null);

  // Formulaire du matériel
  const [ouvert, setOuvert] = useState(false);
  const [edition, setEdition] = useState<Materiel | null>(null);
  const [libelle, setLibelle] = useState('');
  const [categorie, setCategorie] = useState<CategorieMateriel>('autre');
  const [quantite, setQuantite] = useState('1');
  const [siteId, setSiteId] = useState('');
  const [etatMateriel, setEtatMateriel] = useState<EtatMateriel>('bon');
  const [periodicite, setPeriodicite] = useState('');
  const [derniere, setDerniere] = useState('');
  const [note, setNote] = useState('');

  // Formulaire du consommable
  const [stockOuvert, setStockOuvert] = useState(false);
  const [stockEdition, setStockEdition] = useState<Consommable | null>(null);
  const [sLibelle, setSLibelle] = useState('');
  const [sCategorie, setSCategorie] = useState<CategorieConsommable>('autre');
  const [sQuantite, setSQuantite] = useState('0');
  const [sUnite, setSUnite] = useState<'kg' | 'l' | 'unite'>('kg');
  const [sSeuil, setSSeuil] = useState('0');
  const [sNote, setSNote] = useState('');

  const rafraichirStock = () => {
    void consommables
      .lister()
      .then(setStock)
      .catch(() => setStock([]));
  };

  useEffect(() => {
    rafraichirStock();
    void sites
      .lister()
      .then((liste: Site[]) =>
        setOptSites([
          { valeur: '', libelle: t.champs.aucun },
          ...liste.map((s) => ({ valeur: String(s.id), libelle: s.nom })),
        ]),
      )
      .catch(() => setOptSites([{ valeur: '', libelle: t.champs.aucun }]));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const colonnes: Colonne<Materiel>[] = [
    { entete: t.champs.libelle, rendu: (m) => m.libelle },
    { entete: t.champs.categorie, rendu: (m) => t.materiel.categories[m.categorie] },
    { entete: t.champs.quantite, rendu: (m) => String(m.quantite) },
    { entete: t.champs.site, rendu: (m) => m.siteNom ?? '—' },
    { entete: t.champs.etat, rendu: (m) => t.materiel.etats[m.etat] },
    {
      entete: t.materiel.prochaine,
      rendu: (m) =>
        m.prochaineMaintenance === null ? (
          // Pas de périodicité : ce matériel ne s'entretient PAS, ce qui n'est
          // pas la même chose que « à entretenir quand on y pense ».
          <span className="z-info">{t.materiel.sansEntretien}</span>
        ) : (
          <span className={m.enRetard ? 'z-erreur-texte' : undefined}>
            {f.date(m.prochaineMaintenance)}
          </span>
        ),
    },
    {
      entete: t.materiel.entretien,
      rendu: (m) =>
        m.periodiciteJours === null ? (
          <span className="z-info">—</span>
        ) : (
          <button type="button" className="z-lien" onClick={() => void entretenir(m)}>
            {t.materiel.marquerFait}
          </button>
        ),
    },
  ];

  const ouvrir = (m: Materiel | null) => {
    setEdition(m);
    setLibelle(m?.libelle ?? '');
    setCategorie(m?.categorie ?? 'autre');
    setQuantite(String(m?.quantite ?? 1));
    setSiteId(m?.siteId != null ? String(m.siteId) : '');
    setEtatMateriel(m?.etat ?? 'bon');
    setPeriodicite(m?.periodiciteJours != null ? String(m.periodiciteJours) : '');
    setDerniere(m?.derniereMaintenance ?? '');
    setNote(m?.note ?? '');
    setErreur(null);
    setOuvert(true);
  };

  const enregistrer = async () => {
    if (libelle.trim() === '') {
      setErreur(t.etats.champsRequis);
      return;
    }
    const corps: MaterielCorps = {
      libelle: libelle.trim(),
      categorie,
      quantite: Number(quantite) || 1,
      siteId: siteId === '' ? null : Number(siteId),
      etat: etatMateriel,
      periodiciteJours: periodicite === '' ? null : Number(periodicite),
      derniereMaintenance: derniere === '' ? null : derniere,
      note: note.trim() === '' ? null : note.trim(),
    };
    try {
      await (edition ? etat.mettreAJour(edition.id, corps) : etat.creer(corps));
      setOuvert(false);
    } catch (cause) {
      setErreur(cause instanceof Error ? cause.message : t.etats.erreur);
    }
  };

  const entretenir = async (m: Materiel) => {
    try {
      await entretenirMateriel(m.id);
      etat.recharger();
    } catch (cause) {
      setErreur(cause instanceof Error ? cause.message : t.etats.erreur);
    }
  };

  // ─── Consommables ─────────────────────────────────────────────────────────

  const ouvrirStock = (c: Consommable | null) => {
    setStockEdition(c);
    setSLibelle(c?.libelle ?? '');
    setSCategorie(c?.categorie ?? 'autre');
    setSQuantite(String(c?.quantite ?? 0));
    setSUnite(c?.unite ?? 'kg');
    setSSeuil(String(c?.seuilAlerte ?? 0));
    setSNote(c?.note ?? '');
    setErreur(null);
    setStockOuvert(true);
  };

  const enregistrerStock = async () => {
    if (sLibelle.trim() === '') {
      setErreur(t.etats.champsRequis);
      return;
    }
    const corps: ConsommableCorps = {
      libelle: sLibelle.trim(),
      categorie: sCategorie,
      quantite: Number(sQuantite) || 0,
      unite: sUnite,
      seuilAlerte: Number(sSeuil) || 0,
      note: sNote.trim() === '' ? null : sNote.trim(),
    };
    try {
      await (stockEdition
        ? consommables.mettreAJour(stockEdition.id, corps)
        : consommables.creer(corps));
      setStockOuvert(false);
      rafraichirStock();
    } catch (cause) {
      setErreur(cause instanceof Error ? cause.message : t.etats.erreur);
    }
  };

  const mouvement = async (c: Consommable, delta: number) => {
    setErreur(null);
    try {
      await mouvementerStock(c.id, delta);
      rafraichirStock();
    } catch (cause) {
      setErreur(cause instanceof Error ? cause.message : t.etats.erreur);
    }
  };

  return (
    <CorpsSection
      titre={t.onglets.materiel}
      sousTitre={t.soustitres.materiel}
      etat={etat}
      onNouveau={() => ouvrir(null)}
      ecriture={ecriture}
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
          ecriture={ecriture}
        />
      )}

      {/* ─── Stock de consommables ──────────────────────────────────────── */}
      <article className="z-carte">
        <h3 className="z-carte__titre">{t.stock.titre}</h3>
        <p className="z-info">{t.stock.aide}</p>
        {stock.length === 0 ? (
          <p className="z-info">{t.stock.aucun}</p>
        ) : (
          <ul className="z-liste-simple">
            {stock.map((c) => (
              <li key={c.id}>
                <strong>{c.libelle}</strong>{' '}
                <span className={c.sousSeuil ? 'z-erreur-texte' : 'z-info'}>
                  {gabarit(t.stock.ligne, {
                    quantite: f.nombre(c.quantite),
                    unite: c.unite,
                    seuil: f.nombre(c.seuilAlerte),
                  })}
                </span>
                {c.sousSeuil && <strong className="z-erreur-texte"> · {t.stock.aRacheter}</strong>}
                {ecriture && (
                  <>
                    {' '}
                    <button type="button" className="z-lien" onClick={() => void mouvement(c, 1)}>
                      +1
                    </button>{' '}
                    <button type="button" className="z-lien" onClick={() => void mouvement(c, -1)}>
                      −1
                    </button>{' '}
                    <button type="button" className="z-lien" onClick={() => ouvrirStock(c)}>
                      {t.actions.modifier}
                    </button>
                  </>
                )}
              </li>
            ))}
          </ul>
        )}
        {ecriture && (
          <Bouton variante="secondaire" onClick={() => ouvrirStock(null)}>
            {t.stock.nouveau}
          </Bouton>
        )}
      </article>

      {ouvert && (
        <Modale titre={t.onglets.materiel} onFermer={() => setOuvert(false)}>
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
                options={CATEGORIES_MATERIEL.map((c) => ({
                  valeur: c,
                  libelle: t.materiel.categories[c],
                }))}
                onChange={(v) => setCategorie(v as CategorieMateriel)}
              />
              <ChampNombre libelle={t.champs.quantite} valeur={quantite} onChange={setQuantite} />
              <ChampSelect
                libelle={t.champs.site}
                valeur={siteId}
                options={optSites}
                onChange={setSiteId}
              />
              <ChampSelect
                libelle={t.champs.etat}
                valeur={etatMateriel}
                options={ETATS_MATERIEL.map((e) => ({ valeur: e, libelle: t.materiel.etats[e] }))}
                onChange={(v) => setEtatMateriel(v as EtatMateriel)}
              />
            </div>
            {/* Une périodicité vide veut dire « ne s'entretient pas », et non
                « à entretenir quand on y pense » : la phrase le dit, faute de
                quoi le champ vide passerait pour un oubli. */}
            <p className="z-info">{t.materiel.aidePeriodicite}</p>
            <div className="z-form__grille">
              <ChampNombre
                libelle={t.materiel.periodicite}
                valeur={periodicite}
                onChange={setPeriodicite}
                min={1}
                max={3650}
              />
              <ChampDate
                libelle={t.materiel.derniere}
                valeur={derniere}
                onChange={setDerniere}
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

      {stockOuvert && (
        <Modale titre={t.stock.titre} onFermer={() => setStockOuvert(false)}>
          <form
            className="z-form"
            onSubmit={(e) => {
              e.preventDefault();
              void enregistrerStock();
            }}
          >
            <ChampTexte
              libelle={t.champs.libelle}
              valeur={sLibelle}
              onChange={setSLibelle}
              requis
            />
            <div className="z-form__grille">
              <ChampSelect
                libelle={t.champs.categorie}
                valeur={sCategorie}
                options={CATEGORIES_CONSOMMABLE.map((c) => ({
                  valeur: c,
                  libelle: t.stock.categories[c],
                }))}
                onChange={(v) => setSCategorie(v as CategorieConsommable)}
              />
              <ChampNombre libelle={t.champs.quantite} valeur={sQuantite} onChange={setSQuantite} />
              <ChampSelect
                libelle={t.capteur.unite}
                valeur={sUnite}
                options={[
                  { valeur: 'kg', libelle: 'kg' },
                  { valeur: 'l', libelle: 'l' },
                  { valeur: 'unite', libelle: t.stock.unite },
                ]}
                onChange={(v) => setSUnite(v as 'kg' | 'l' | 'unite')}
              />
              <ChampNombre libelle={t.stock.seuil} valeur={sSeuil} onChange={setSSeuil} />
            </div>
            <p className="z-info">{t.stock.aideSeuil}</p>
            <ChampZone libelle={t.champs.note} valeur={sNote} onChange={setSNote} />
            <div className="z-form__actions">
              <Bouton variante="fantome" onClick={() => setStockOuvert(false)}>
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
