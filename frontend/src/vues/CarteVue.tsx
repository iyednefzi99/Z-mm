import { useEffect, useMemo, useState, type ReactElement } from 'react';
import { ruches, sites } from '../api/client';
import type { Ruche, Site } from '../api/types';
import { useT } from '../i18n/langue';

/**
 * Carte des ruchers et rayons de butinage (US-030).
 *
 * <p>Rendu SVG autonome (sans tuiles externes) : chaque site est positionné selon
 * ses coordonnées, entouré des cercles de butinage à 1, 2 et 3 km. L'intégration
 * MapLibre GL + OpenStreetMap (fond cartographique réel) est l'évolution prévue.
 */
export function CarteVue(): ReactElement {
  const t = useT();
  const [listeSites, setListeSites] = useState<Site[]>([]);
  const [listeRuches, setListeRuches] = useState<Ruche[]>([]);

  useEffect(() => {
    void sites.lister().then(setListeSites).catch(() => setListeSites([]));
    void ruches.lister().then(setListeRuches).catch(() => setListeRuches([]));
  }, []);

  const projection = useMemo(() => {
    if (listeSites.length === 0) return null;
    const lats = listeSites.map((s) => s.latitude);
    const lons = listeSites.map((s) => s.longitude);
    const latMoy = (Math.min(...lats) + Math.max(...lats)) / 2;
    // 1° latitude ≈ 111 km ; 1° longitude ≈ 111 km × cos(lat).
    const kmParDegreLat = 111;
    const kmParDegreLon = 111 * Math.cos((latMoy * Math.PI) / 180);
    // Échelle : pixels par km, choisie pour tenir dans la vue.
    const pxParKm = 26;
    return { lats, lons, kmParDegreLat, kmParDegreLon, pxParKm };
  }, [listeSites]);

  const ruchesParSite = useMemo(() => {
    const compte = new Map<number, number>();
    listeRuches.forEach((r) => compte.set(r.siteId, (compte.get(r.siteId) ?? 0) + 1));
    return compte;
  }, [listeRuches]);

  if (!projection) {
    return (
      <section className="z-section">
        <header className="z-section__entete">
          <h1 className="z-section__titre">{t.onglets.carte}</h1>
        </header>
        <p className="z-info">{t.carte.aucunSite}</p>
      </section>
    );
  }

  const { lats, lons, kmParDegreLat, kmParDegreLon, pxParKm } = projection;
  const marge = 3 * pxParKm + 40; // place pour le cercle 3 km + libellés
  const points = listeSites.map((s) => ({
    site: s,
    x: (s.longitude - Math.min(...lons)) * kmParDegreLon * pxParKm + marge,
    y: (Math.max(...lats) - s.latitude) * kmParDegreLat * pxParKm + marge,
  }));
  const largeur = Math.max(...points.map((p) => p.x)) + marge;
  const hauteur = Math.max(...points.map((p) => p.y)) + marge;

  return (
    <section className="z-section">
      <header className="z-section__entete">
        <h1 className="z-section__titre">{t.onglets.carte}</h1>
      </header>
      <p className="z-info">{t.carte.legende}</p>
      <div className="z-table-enveloppe">
        <svg
          className="z-carte-svg"
          width={largeur}
          height={hauteur}
          viewBox={`0 0 ${largeur} ${hauteur}`}
          role="img"
          aria-label={t.carte.rayons}
        >
          {points.map((p) => (
            <g key={p.site.id}>
              {[3, 2, 1].map((km) => (
                <circle
                  key={km}
                  cx={p.x}
                  cy={p.y}
                  r={km * pxParKm}
                  fill="none"
                  stroke="var(--z-honey)"
                  strokeOpacity={0.25 + (3 - km) * 0.2}
                  strokeDasharray="4 3"
                />
              ))}
              <circle cx={p.x} cy={p.y} r={5} fill="var(--z-honey)" />
              <text x={p.x + 8} y={p.y - 8} fontSize={12} fill="currentColor">
                {p.site.nom} ({ruchesParSite.get(p.site.id) ?? 0})
              </text>
            </g>
          ))}
        </svg>
      </div>
    </section>
  );
}
