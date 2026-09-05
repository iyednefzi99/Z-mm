import type { ReactElement } from 'react';
import type { PointReferentiel } from '../api/types';
import { useT } from '../i18n/langue';
import { ChampSelect } from '../ui/composants';
import {
  ARIA_ETAT,
  SUIVANT,
  libellePoint,
  parCategorie,
  type EtatCase,
  type SaisieCarnet,
} from './points';

/**
 * Grille des points du carnet paramétrable (SPRINT-28, lot I).
 *
 * <p><strong>Une case à trois états, et non une case à cocher.</strong> C'est le
 * point de conception de tout l'écran. Une case à cocher ordinaire n'a que deux
 * positions, et la position « vide » y sert à deux choses incompatibles : « je
 * n'ai pas regardé » et « j'ai regardé, ce n'est pas là ». Les confondre ferait
 * compter comme constats négatifs des visites où personne n'a ouvert la ruche.
 *
 * <p>Le motif employé est celui de la case indéterminée, standard et connu des
 * lecteurs d'écran : {@code role="checkbox"} avec {@code aria-checked="mixed"}.
 * Un clic fait tourner l'état — inconnu, oui, non — ce qui garde le geste à
 * <em>un</em> tap pour le cas courant, celui qu'on fait avec des gants.
 */
export function GrilleCarnet({
  points,
  saisie,
  onChange,
}: {
  points: readonly PointReferentiel[];
  saisie: SaisieCarnet;
  onChange: (saisie: SaisieCarnet) => void;
}): ReactElement | null {
  const t = useT();

  if (points.length === 0) {
    return null;
  }

  const basculer = (code: string) => {
    const actuel = saisie.cases[code] ?? 'inconnu';
    onChange({ ...saisie, cases: { ...saisie.cases, [code]: SUIVANT[actuel] } });
  };

  const changerNiveau = (code: string, valeur: string) => {
    onChange({ ...saisie, niveaux: { ...saisie.niveaux, [code]: valeur } });
  };

  const libelleEtat: Record<EtatCase, string> = {
    inconnu: t.carnet.nonRegarde,
    oui: t.visite.oui,
    non: t.visite.non,
  };

  const niveaux = [
    { valeur: '', libelle: t.carnet.nonRegarde },
    { valeur: '0', libelle: '0' },
    { valeur: '1', libelle: '1' },
    { valeur: '2', libelle: '2' },
    { valeur: '3', libelle: '3' },
  ];

  return (
    <fieldset className="z-composition">
      <legend className="z-champ__libelle">{t.carnet.observations}</legend>
      {parCategorie(points).map(([categorie, liste]) => (
        <div key={categorie} className="z-carnet__famille">
          <p className="z-carnet__titre">
            {t.carnet.categories[categorie as keyof typeof t.carnet.categories] ?? categorie}
          </p>
          <div className="z-form__grille">
            {liste.map((point) =>
              point.typeValeur === 'echelle' ? (
                <ChampSelect
                  key={point.code}
                  libelle={libellePoint(t.carnet.points, point)}
                  valeur={saisie.niveaux[point.code] ?? ''}
                  options={niveaux}
                  onChange={(valeur) => changerNiveau(point.code, valeur)}
                />
              ) : (
                <button
                  key={point.code}
                  type="button"
                  role="checkbox"
                  aria-checked={ARIA_ETAT[saisie.cases[point.code] ?? 'inconnu']}
                  className={`z-carnet__case z-carnet__case--${
                    saisie.cases[point.code] ?? 'inconnu'
                  }`}
                  onClick={() => basculer(point.code)}
                >
                  <span className="z-carnet__libelle">
                    {libellePoint(t.carnet.points, point)}
                  </span>
                  <span className="z-carnet__etat">
                    {libelleEtat[saisie.cases[point.code] ?? 'inconnu']}
                  </span>
                </button>
              ),
            )}
          </div>
        </div>
      ))}
      <p className="z-info">{t.carnet.statistiquesAide}</p>
    </fieldset>
  );
}
