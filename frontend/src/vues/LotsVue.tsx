import { useCallback, useEffect, useState, type ReactElement } from 'react';
import { chargerMentionOrigine, lots, recoltes } from '../api/client';
import type { Lot, LotCorps, MentionOrigine, OrigineDeclaree, Recolte } from '../api/types';
import { useLangue, useFormats, useT } from '../i18n/langue';
import { messageErreur } from '../hooks';
import { Bouton, ChampDate, ChampNombre, ChampSelect, ChampTexte, Option } from '../ui/composants';
import { useDialogues } from '../ui/dialogues';

/**
 * Lots de conditionnement et mention d'origine (US-056).
 *
 * <p>Conformité à la directive (UE) 2024/1438, applicable au 14 juin 2026. Le pot
 * porte le ou les pays d'origine, par ordre décroissant, en pourcentages.
 *
 * <p>Deux partis pris d'interface, tous deux dictés par la contrainte réglementaire :
 * <ul>
 *   <li>le total des parts est affiché <strong>en permanence</strong>, et l'écran
 *       refuse l'enregistrement tant qu'il ne fait pas 100 % — découvrir l'erreur
 *       après huit lignes saisies est une perte de temps évitable ;
 *   <li>la mention d'origine est <strong>prévisualisée telle qu'elle sera
 *       imprimée</strong>. C'est cette chaîne qui engage le producteur en contrôle :
 *       elle doit être lue avant, pas découverte sur l'étiquette.
 * </ul>
 */

/** Pays proposés en premier — les origines les plus courantes du marché européen. */
const PAYS_COURANTS = ['FR', 'ES', 'IT', 'PT', 'DE', 'PL', 'RO', 'BG', 'UA', 'TN', 'MA', 'CN', 'AR'];

interface LigneOrigine extends OrigineDeclaree {
  /** Clé locale de rendu : les lignes n'ont pas d'identifiant avant enregistrement. */
  cle: string;
}

const ligneVide = (): LigneOrigine => ({
  cle: crypto.randomUUID(),
  recolteId: null,
  paysOrigine: 'FR',
  pourcentage: 0,
});

export function LotsVue(): ReactElement {
  const t = useT();
  const f = useFormats();
  const { langue } = useLangue();
  const { confirmer } = useDialogues();

  const [liste, setListe] = useState<Lot[]>([]);
  const [optRecoltes, setOptRecoltes] = useState<Option[]>([]);
  const [erreur, setErreur] = useState<string | null>(null);
  const [ouvert, setOuvert] = useState(false);
  const [mention, setMention] = useState<{ lot: Lot; texte: MentionOrigine } | null>(null);

  const [reference, setReference] = useState('');
  const [dateConditionnement, setDate] = useState('');
  const [quantite, setQuantite] = useState('');
  const [typeMiel, setTypeMiel] = useState('');
  const [origines, setOrigines] = useState<LigneOrigine[]>([ligneVide()]);

  const recharger = useCallback(() => {
    void lots
      .lister()
      .then(setListe)
      .catch((c) => setErreur(messageErreur(c, t.etats.serviceIndisponible)));
  }, [t.etats.serviceIndisponible]);

  useEffect(() => {
    recharger();
    void recoltes
      .lister()
      .then((l: Recolte[]) =>
        setOptRecoltes([
          // Le miel acquis à un tiers n'a pas de récolte : sans cette option, il
          // serait inreprésentable et les parts ne totaliseraient jamais 100 %.
          { valeur: '', libelle: t.lot.mielAchete },
          ...l.map((r) => ({ valeur: String(r.id), libelle: `${r.lot} — ${r.rucheModele}` })),
        ]),
      )
      .catch(() => setOptRecoltes([{ valeur: '', libelle: t.lot.mielAchete }]));
  }, [recharger, t.lot.mielAchete]);

  const total = origines.reduce((somme, o) => somme + (Number(o.pourcentage) || 0), 0);
  const totalJuste = Math.abs(total - 100) < 0.05;

  const modifier = (cle: string, champs: Partial<LigneOrigine>) =>
    setOrigines((lignes) => lignes.map((l) => (l.cle === cle ? { ...l, ...champs } : l)));

  const reinitialiser = () => {
    setReference('');
    setDate('');
    setQuantite('');
    setTypeMiel('');
    setOrigines([ligneVide()]);
  };

  const enregistrer = async () => {
    setErreur(null);
    const corps: LotCorps = {
      reference,
      dateConditionnement,
      quantiteKg: Number(quantite),
      typeMiel: typeMiel || null,
      origines: origines.map(({ recolteId, paysOrigine, pourcentage }) => ({
        recolteId,
        paysOrigine,
        pourcentage: Number(pourcentage),
      })),
    };
    try {
      await lots.creer(corps);
      setOuvert(false);
      reinitialiser();
      recharger();
    } catch (cause) {
      setErreur(messageErreur(cause, t.etats.serviceIndisponible));
    }
  };

  const supprimer = async (lot: Lot) => {
    if (!(await confirmer(t.lot.confirmerSuppression.replace('{ref}', lot.reference)))) {
      return;
    }
    try {
      await lots.supprimer(lot.id);
      recharger();
    } catch (cause) {
      setErreur(messageErreur(cause, t.etats.serviceIndisponible));
    }
  };

  const voirMention = async (lot: Lot) => {
    try {
      setMention({ lot, texte: await chargerMentionOrigine(lot.id, langue) });
    } catch (cause) {
      setErreur(messageErreur(cause, t.etats.serviceIndisponible));
    }
  };

  return (
    <section className="z-section">
      <header className="z-section__entete">
        <h1 className="z-section__titre">{t.onglets.lots}</h1>
        <Bouton variante="primaire" onClick={() => setOuvert(true)}>
          {t.actions.nouveau}
        </Bouton>
      </header>

      <p className="z-info">{t.lot.rappelDirective}</p>

      {erreur && (
        <div className="z-erreur" role="alert">
          <span>{erreur}</span>
        </div>
      )}

      {liste.length === 0 ? (
        <p className="z-info">{t.etats.vide}</p>
      ) : (
        <div className="z-table-enveloppe">
          <table className="z-table">
            <thead>
              <tr>
                <th>{t.lot.reference}</th>
                <th>{t.lot.date}</th>
                <th>{t.lot.quantite}</th>
                <th>{t.lot.typeMiel}</th>
                <th>{t.lot.origines}</th>
                <th aria-label={t.actions.modifier} />
              </tr>
            </thead>
            <tbody>
              {liste.map((lot) => (
                <tr key={lot.id}>
                  <td>{lot.reference}</td>
                  <td>{f.date(lot.dateConditionnement)}</td>
                  <td>{lot.quantiteKg}</td>
                  <td>{lot.typeMiel ?? '—'}</td>
                  <td>
                    {lot.composition
                      .map((p) => `${p.paysOrigine} ${p.pourcentage} %`)
                      .join(' · ')}
                  </td>
                  <td>
                    <button type="button" className="z-lien" onClick={() => void voirMention(lot)}>
                      {t.lot.mention}
                    </button>{' '}
                    <button type="button" className="z-lien" onClick={() => void supprimer(lot)}>
                      {t.actions.supprimer}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {mention && (
        <div className="z-etiquette">
          <h2 className="z-etiquette__titre">
            {t.lot.mention} — {mention.lot.reference}
          </h2>
          {/* Aperçu tel qu'imprimé : c'est cette chaîne qui engage en contrôle. */}
          <p className="z-etiquette__mention">{mention.texte.texte}</p>
          <p className="z-info">
            {mention.texte.melange ? t.lot.estMelange : t.lot.monoOrigine}
          </p>
          <Bouton variante="secondaire" onClick={() => setMention(null)}>
            {t.actions.fermer}
          </Bouton>
        </div>
      )}

      {ouvert && (
        <div className="z-composition">
          <div className="z-form__grille">
            <ChampTexte libelle={t.lot.reference} valeur={reference} onChange={setReference} />
            <ChampDate libelle={t.lot.date} valeur={dateConditionnement} onChange={setDate} />
            <ChampNombre libelle={t.lot.quantite} valeur={quantite} onChange={setQuantite} />
            <ChampTexte libelle={t.lot.typeMiel} valeur={typeMiel} onChange={setTypeMiel} />
          </div>

          <fieldset className="z-composition">
            <legend className="z-champ__libelle">{t.lot.origines}</legend>
            {origines.map((o) => (
              <div className="z-form__grille" key={o.cle}>
                <ChampSelect
                  libelle={t.lot.recolte}
                  valeur={o.recolteId == null ? '' : String(o.recolteId)}
                  options={optRecoltes}
                  onChange={(v) => modifier(o.cle, { recolteId: v === '' ? null : Number(v) })}
                />
                <ChampSelect
                  libelle={t.lot.pays}
                  valeur={o.paysOrigine}
                  options={PAYS_COURANTS.map((p) => ({
                    valeur: p,
                    libelle: `${p} — ${new Intl.DisplayNames([langue], { type: 'region' }).of(p) ?? p}`,
                  }))}
                  onChange={(v) => modifier(o.cle, { paysOrigine: v })}
                />
                <ChampNombre
                  libelle={t.lot.pourcentage}
                  valeur={String(o.pourcentage)}
                  onChange={(v) => modifier(o.cle, { pourcentage: Number(v) })}
                />
                <div className="z-champ z-champ--aligne-bas">
                  <Bouton
                    variante="secondaire"
                    onClick={() => setOrigines((l) => l.filter((x) => x.cle !== o.cle))}
                  >
                    {t.actions.supprimer}
                  </Bouton>
                </div>
              </div>
            ))}

            <div className="z-actions-inline">
              <Bouton variante="secondaire" onClick={() => setOrigines((l) => [...l, ligneVide()])}>
                {t.lot.ajouterOrigine}
              </Bouton>
              {/* Le total est visible en permanence : découvrir l'écart après huit
                  lignes saisies est une perte de temps évitable. */}
              <span className={totalJuste ? 'z-total z-total--juste' : 'z-total z-total--faux'}>
                {t.lot.total} : {total.toFixed(2)} %
              </span>
            </div>
          </fieldset>

          <div className="z-actions-inline">
            <Bouton variante="primaire" onClick={() => void enregistrer()} disabled={!totalJuste}>
              {t.actions.enregistrer}
            </Bouton>
            <Bouton
              variante="secondaire"
              onClick={() => {
                setOuvert(false);
                reinitialiser();
              }}
            >
              {t.actions.annuler}
            </Bouton>
          </div>
        </div>
      )}
    </section>
  );
}
