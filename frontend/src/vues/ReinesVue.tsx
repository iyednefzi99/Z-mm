import { useEffect, useState, type ReactElement } from 'react';
import {
  enregistrerReine,
  listerReines,
  ruches,
  supprimerReine,
} from '../api/client';
import type { CouleurReine, Reine, Ruche, StatutReine } from '../api/types';
import { COULEURS_REINE, STATUTS_REINE } from '../api/types';
import { useT } from '../i18n/langue';
import { messageErreur } from '../hooks';
import {
  Bouton,
  ChampDate,
  ChampNombre,
  ChampSelect,
  ChampTexte,
  Colonne,
  Option,
  Table,
} from '../ui/composants';

/** Suivi de la reine par ruche : historique + ajout d'événement (US-032). */
export function ReinesVue(): ReactElement {
  const t = useT();
  const [optRuches, setOptRuches] = useState<Option[]>([]);
  const [rucheId, setRucheId] = useState('');
  const [historique, setHistorique] = useState<Reine[]>([]);
  const [date, setDate] = useState('');
  const [statut, setStatut] = useState<StatutReine>('introduite');
  const [couleur, setCouleur] = useState('');
  const [annee, setAnnee] = useState('');
  const [race, setRace] = useState('');
  const [erreur, setErreur] = useState<string | null>(null);

  const vide: Option = { valeur: '', libelle: t.champs.aucun };
  const optStatut: Option[] = STATUTS_REINE.map((s) => ({ valeur: s, libelle: t.reine.statuts[s] }));
  const optCouleur: Option[] = [vide, ...COULEURS_REINE.map((c) => ({ valeur: c, libelle: t.reine.couleurs[c] }))];

  const colonnes: Colonne<Reine>[] = [
    { entete: t.reine.date, rendu: (r) => r.dateEvenement },
    { entete: t.reine.statut, rendu: (r) => t.reine.statuts[r.statut] },
    { entete: t.reine.couleur, rendu: (r) => (r.couleurMarquage ? t.reine.couleurs[r.couleurMarquage] : '—') },
    { entete: t.reine.annee, rendu: (r) => (r.anneeNaissance != null ? String(r.anneeNaissance) : '—') },
    { entete: t.reine.race, rendu: (r) => r.race ?? '—' },
  ];

  useEffect(() => {
    void ruches.lister().then((l: Ruche[]) => setOptRuches([vide, ...l.map((r) => ({ valeur: String(r.id), libelle: r.modele }))])).catch(() => setOptRuches([vide]));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const charger = (id: string) => {
    setRucheId(id);
    setHistorique([]);
    if (id !== '') {
      void listerReines(Number(id)).then(setHistorique).catch((c) => setErreur(messageErreur(c)));
    }
  };

  const ajouter = async () => {
    if (rucheId === '' || date === '') return;
    setErreur(null);
    try {
      await enregistrerReine({
        rucheId: Number(rucheId),
        dateEvenement: date,
        statut,
        couleurMarquage: couleur === '' ? null : (couleur as CouleurReine),
        anneeNaissance: annee === '' ? null : Number(annee),
        race: race.trim() === '' ? null : race,
        note: null,
      });
      setDate('');
      setCouleur('');
      setAnnee('');
      setRace('');
      charger(rucheId);
    } catch (cause) {
      setErreur(messageErreur(cause));
    }
  };

  const retirer = (r: Reine) => {
    void supprimerReine(r.id).then(() => charger(rucheId)).catch((c) => setErreur(messageErreur(c)));
  };

  return (
    <section className="z-section">
      <header className="z-section__entete">
        <h1 className="z-section__titre">{t.onglets.reines}</h1>
      </header>

      {erreur && (
        <div className="z-erreur" role="alert">
          <span>{erreur}</span>
        </div>
      )}

      <div className="z-form__grille">
        <ChampSelect libelle={t.reine.choisirRuche} valeur={rucheId} options={optRuches} onChange={charger} />
      </div>

      {rucheId !== '' && (
        <>
          <fieldset className="z-composition">
            <legend className="z-champ__libelle">{t.reine.ajouter}</legend>
            <div className="z-form__grille">
              <ChampDate libelle={t.reine.date} valeur={date} onChange={setDate} />
              <ChampSelect libelle={t.reine.statut} valeur={statut} options={optStatut} onChange={(v) => setStatut(v as StatutReine)} />
              <ChampSelect libelle={t.reine.couleur} valeur={couleur} options={optCouleur} onChange={setCouleur} />
            </div>
            <div className="z-form__grille">
              <ChampNombre libelle={t.reine.annee} valeur={annee} onChange={setAnnee} pas="1" />
              <ChampTexte libelle={t.reine.race} valeur={race} onChange={setRace} />
              <div className="z-champ z-champ--aligne-bas">
                <Bouton variante="primaire" onClick={() => void ajouter()}>{t.reine.ajouter}</Bouton>
              </div>
            </div>
          </fieldset>

          {historique.length > 0 ? (
            <Table colonnes={colonnes} elements={historique} onModifier={() => undefined} onSupprimer={retirer} />
          ) : (
            <p className="z-info">{t.etats.vide}</p>
          )}
        </>
      )}
    </section>
  );
}
