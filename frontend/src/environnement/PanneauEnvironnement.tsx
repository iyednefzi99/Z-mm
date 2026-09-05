import { useEffect, useState, type ReactElement } from 'react';
import { chargerCouvert, chargerFloraisons, chargerRotation } from '../api/client';
import type { ClasseCouvert, CouvertRucher, FloraisonObservee } from '../api/types';
import { gabarit } from '../i18n/console';
import { useFormats, useT } from '../i18n/langue';

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
 */
export function PanneauEnvironnement({ siteId }: { siteId: number }): ReactElement {
  const t = useT();
  const f = useFormats();
  const [couvert, setCouvert] = useState<CouvertRucher | null>(null);
  const [rotation, setRotation] = useState<CouvertRucher[]>([]);
  const [floraisons, setFloraisons] = useState<FloraisonObservee[]>([]);

  useEffect(() => {
    void chargerCouvert(siteId).then(setCouvert).catch(() => setCouvert(null));
    void chargerRotation(siteId).then(setRotation).catch(() => setRotation([]));
    void chargerFloraisons(siteId).then(setFloraisons).catch(() => setFloraisons([]));
  }, [siteId]);

  const libelle = (classe: ClasseCouvert) => t.environnement.classes[classe] ?? classe;

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
                    <td>
                      {surface.part === null ? '—' : `${surface.part} %`}
                    </td>
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
                {t.environnement.distanceCulture} :{' '}
                {f.distance(couvert.distanceCultureM)}
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
                      const trouvee = annee.surfaces.find(
                        (s) => s.classe === colonne.classe,
                      );
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
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
