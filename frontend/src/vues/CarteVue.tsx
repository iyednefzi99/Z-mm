import { Suspense, lazy, useEffect, useMemo, useState, type ReactElement } from 'react';
import { grappesSites, ruches, sites } from '../api/client';
import type { GrappeSites, Ruche, Site } from '../api/types';
import { gabarit } from '../i18n/console';
import { useT } from '../i18n/langue';
import { ChampSelect } from '../ui/composants';

/** Rayon de regroupement proposé, en kilomètres (US-045). */
const RAYONS_REGROUPEMENT = [5, 10, 15, 30];

/**
 * Fond cartographique réel (SPRINT-13). Chargé à la demande : MapLibre pèse plus
 * lourd que tout le reste de l'application, et la majorité des écrans n'en ont
 * aucun besoin.
 */
const CarteFond = lazy(() => import('./CarteFond'));

/**
 * WebGL est-il disponible ? Un rucher n'a pas toujours de réseau, un poste de
 * terrain pas toujours d'accélération graphique, et l'environnement de test pas
 * de canvas WebGL du tout. Dans ces trois cas la carte SVG autonome prend le
 * relais — elle ne dépend de rien.
 */
function webglDisponible(): boolean {
  try {
    const toile = document.createElement('canvas');
    return Boolean(toile.getContext('webgl2') ?? toile.getContext('webgl'));
  } catch {
    return false;
  }
}

/**
 * Carte des ruchers et rayons de butinage (US-030), avec regroupement spatial
 * des sites (US-045).
 *
 * <p>Rendu SVG autonome (sans tuiles externes) : chaque site est positionné selon
 * ses coordonnées, entouré des cercles de butinage à 1, 2 et 3 km. L'intégration
 * MapLibre GL + OpenStreetMap (fond cartographique réel) est l'évolution prévue.
 *
 * <p>En vue « grappes », le regroupement n'est pas recalculé ici : il vient de
 * PostGIS (`ST_ClusterDBSCAN`), la vue ne fait que placer les centroïdes reçus.
 */
export function CarteVue(): ReactElement {
  const t = useT();
  const [listeSites, setListeSites] = useState<Site[]>([]);
  const [listeRuches, setListeRuches] = useState<Ruche[]>([]);
  const [grappes, setGrappes] = useState<GrappeSites[]>([]);
  const [vueGrappes, setVueGrappes] = useState(false);
  const [rayonKm, setRayonKm] = useState(15);
  // Le fond réel est le défaut quand la machine le permet ; le repli SVG reste
  // accessible d'un clic, y compris pour imprimer ou économiser la batterie.
  const [fondReel, setFondReel] = useState(() => webglDisponible());

  useEffect(() => {
    void sites.lister().then(setListeSites).catch(() => setListeSites([]));
    void ruches.lister().then(setListeRuches).catch(() => setListeRuches([]));
  }, []);

  useEffect(() => {
    if (!vueGrappes) return;
    void grappesSites(rayonKm * 1000).then(setGrappes).catch(() => setGrappes([]));
  }, [vueGrappes, rayonKm]);

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
          <div>
            <h1 className="z-section__titre">{t.onglets.carte}</h1>
            <p className="z-section__soustitre">{t.soustitres.carte}</p>
          </div>
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

  // Les centroïdes reçus sont projetés avec la même échelle que les sites : les
  // deux vues restent superposables.
  const pointsGrappes = grappes.map((g) => ({
    grappe: g,
    x: (g.longitudeCentre - Math.min(...lons)) * kmParDegreLon * pxParKm + marge,
    y: (Math.max(...lats) - g.latitudeCentre) * kmParDegreLat * pxParKm + marge,
  }));

  return (
    <section className="z-section">
      <header className="z-section__entete">
        <div>
          <h1 className="z-section__titre">{t.onglets.carte}</h1>
          <p className="z-section__soustitre">{t.soustitres.carte}</p>
        </div>
        <div className="z-actions-inline">
          <button
            type="button"
            className={`z-lien${vueGrappes ? '' : ' z-lien--actif'}`}
            aria-pressed={!vueGrappes}
            onClick={() => setVueGrappes(false)}
          >
            {t.carte.vueSites}
          </button>
          <button
            type="button"
            className={`z-lien${vueGrappes ? ' z-lien--actif' : ''}`}
            aria-pressed={vueGrappes}
            onClick={() => setVueGrappes(true)}
          >
            {t.carte.vueGrappes}
          </button>
          <button
            type="button"
            className={`z-lien${fondReel ? ' z-lien--actif' : ''}`}
            aria-pressed={fondReel}
            onClick={() => setFondReel((actif) => !actif)}
          >
            {t.carte.fondReel}
          </button>
        </div>
      </header>
      {vueGrappes && (
        <ChampSelect
          libelle={t.carte.rayonRegroupement}
          valeur={String(rayonKm)}
          options={RAYONS_REGROUPEMENT.map((km) => ({ valeur: String(km), libelle: String(km) }))}
          onChange={(v) => setRayonKm(Number(v))}
        />
      )}
      <p className="z-info">{vueGrappes ? t.carte.legendeGrappes : t.carte.legende}</p>
      {fondReel ? (
        <Suspense fallback={<p className="z-info">{t.etats.chargement}</p>}>
          <CarteFond
            sites={listeSites}
            grappes={grappes}
            vueGrappes={vueGrappes}
            ruchesParSite={ruchesParSite}
            etiquette={vueGrappes ? t.carte.vueGrappes : t.carte.rayons}
          />
        </Suspense>
      ) : (
      <div className="z-table-enveloppe">
        <svg
          className="z-carte-svg"
          width={largeur}
          height={hauteur}
          viewBox={`0 0 ${largeur} ${hauteur}`}
          role="img"
          aria-label={vueGrappes ? t.carte.vueGrappes : t.carte.rayons}
        >
          {vueGrappes
            ? pointsGrappes.map((p) => (
                <g key={p.grappe.numero}>
                  {/* Pastille proportionnelle au nombre de sites, plancher lisible. */}
                  <circle
                    cx={p.x}
                    cy={p.y}
                    r={12 + Math.sqrt(p.grappe.nombreSites) * 6}
                    fill="var(--z-honey-500)"
                    fillOpacity={0.25}
                    stroke="var(--z-honey-500)"
                  />
                  <text
                    x={p.x}
                    y={p.y + 4}
                    fontSize={13}
                    textAnchor="middle"
                    fill="currentColor"
                  >
                    {p.grappe.nombreSites}
                  </text>
                  <text x={p.x + 8} y={p.y - 18} fontSize={12} fill="currentColor">
                    {gabarit(t.carte.grappe, { n: String(p.grappe.numero) })} —{' '}
                    {gabarit(t.carte.resumeGrappe, {
                      sites: String(p.grappe.nombreSites),
                      ruches: String(p.grappe.nombreRuches),
                    })}
                  </text>
                </g>
              ))
            : points.map((p) => (
                <g key={p.site.id}>
                  {[3, 2, 1].map((km) => (
                    <circle
                      key={km}
                      cx={p.x}
                      cy={p.y}
                      r={km * pxParKm}
                      fill="none"
                      stroke="var(--z-honey-500)"
                      strokeOpacity={0.25 + (3 - km) * 0.2}
                      strokeDasharray="4 3"
                    />
                  ))}
                  <circle cx={p.x} cy={p.y} r={5} fill="var(--z-honey-500)" />
                  <text x={p.x + 8} y={p.y - 8} fontSize={12} fill="currentColor">
                    {p.site.nom} ({ruchesParSite.get(p.site.id) ?? 0})
                  </text>
                </g>
              ))}
        </svg>
      </div>
      )}
    </section>
  );
}
