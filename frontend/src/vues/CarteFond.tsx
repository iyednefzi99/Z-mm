/**
 * Fond cartographique reel (SPRINT-13, US-030) — MapLibre GL + tuiles OSM.
 *
 * Ce module est charge PARESSEUSEMENT (`React.lazy`) : MapLibre pese plus que
 * tout le reste de la PWA reunie, et un apiculteur qui ouvre l'onglet « Visites »
 * n'a aucune raison de le telecharger. La vue carte reste utilisable sans lui —
 * `CarteVue` retombe sur son rendu SVG autonome quand WebGL manque ou que le
 * reseau est coupe, ce qui est le cas courant sur un rucher.
 *
 * Le style est declare EN LIGNE, sans appel a un service de style distant : un
 * seul hote externe, celui des tuiles, ce qui tient dans la CSP du proxy inverse
 * (cf. infra/nginx/nginx.conf) et ne fait fuiter la position d'aucun rucher vers
 * un tiers supplementaire.
 *
 * ⚠️ Les tuiles publiques d'OpenStreetMap sont soumises a une politique d'usage
 * qui exclut les charges de production. `VITE_TUILES_URL` permet de pointer vers
 * un fournisseur souscrit — ou vers un serveur de tuiles interne, seule option
 * qui garantisse qu'aucune coordonnee de rucher ne sorte du systeme.
 */

import { useEffect, useMemo, useRef, type ReactElement } from 'react';
import {
  Map as CarteMapLibre,
  Marker,
  NavigationControl,
  ScaleControl,
  type LngLatBoundsLike,
  type StyleSpecification,
} from 'maplibre-gl';
import 'maplibre-gl/dist/maplibre-gl.css';
import type { GrappeSites, Site } from '../api/types';

const TUILES =
  import.meta.env.VITE_TUILES_URL ?? 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';
const ATTRIBUTION =
  '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>';

const STYLE: StyleSpecification = {
  version: 8,
  sources: {
    osm: {
      type: 'raster',
      tiles: [TUILES],
      tileSize: 256,
      attribution: ATTRIBUTION,
      maxzoom: 19,
    },
  },
  layers: [{ id: 'osm', type: 'raster', source: 'osm' }],
};

export interface CarteFondProps {
  sites: Site[];
  grappes: GrappeSites[];
  vueGrappes: boolean;
  ruchesParSite: Map<number, number>;
  /** Libelle accessible de la carte. */
  etiquette: string;
  /** Rayons de butinage a tracer, en kilometres. */
  rayonsKm?: number[];
}

/** Cercle geodesique en GeoJSON : le rayon de butinage est en METRES reels. */
function cercle(longitude: number, latitude: number, rayonMetres: number, cotes = 64) {
  const coordonnees: [number, number][] = [];
  const kmParDegreLat = 110.574;
  const kmParDegreLon = 111.32 * Math.cos((latitude * Math.PI) / 180);
  for (let i = 0; i <= cotes; i++) {
    const angle = (i / cotes) * 2 * Math.PI;
    coordonnees.push([
      longitude + ((rayonMetres / 1000) * Math.cos(angle)) / kmParDegreLon,
      latitude + ((rayonMetres / 1000) * Math.sin(angle)) / kmParDegreLat,
    ]);
  }
  return coordonnees;
}

export default function CarteFond({
  sites,
  grappes,
  vueGrappes,
  ruchesParSite,
  etiquette,
  rayonsKm = [1, 2, 3],
}: CarteFondProps): ReactElement {
  const conteneur = useRef<HTMLDivElement>(null);
  const carte = useRef<CarteMapLibre | null>(null);
  const marqueurs = useRef<Marker[]>([]);

  const emprise = useMemo<LngLatBoundsLike | null>(() => {
    const points = vueGrappes
      ? grappes.map((g) => [Number(g.longitudeCentre), Number(g.latitudeCentre)] as [number, number])
      : sites.map((s) => [Number(s.longitude), Number(s.latitude)] as [number, number]);
    if (points.length === 0) return null;
    const lons = points.map((p) => p[0]);
    const lats = points.map((p) => p[1]);
    return [
      [Math.min(...lons), Math.min(...lats)],
      [Math.max(...lons), Math.max(...lats)],
    ];
  }, [grappes, sites, vueGrappes]);

  // Creation : une seule fois. Recreer la carte a chaque rendu ferait clignoter
  // le fond et perdrait la position choisie par l'utilisateur.
  useEffect(() => {
    if (!conteneur.current || carte.current) return;
    carte.current = new CarteMapLibre({
      container: conteneur.current,
      style: STYLE,
      center: [1.44, 44],
      zoom: 8,
      attributionControl: { compact: true },
    });
    carte.current.addControl(new NavigationControl({ showCompass: false }), 'top-right');
    carte.current.addControl(
      new ScaleControl({ maxWidth: 120, unit: 'metric' }),
      'bottom-left',
    );
    const instance = carte.current;
    return () => {
      instance.remove();
      carte.current = null;
    };
  }, []);

  // Contenu : rejoue a chaque changement de donnees ou de vue.
  useEffect(() => {
    const c = carte.current;
    if (!c) return;

    const dessiner = () => {
      marqueurs.current.forEach((m) => m.remove());
      marqueurs.current = [];

      if (c.getLayer('butinage')) c.removeLayer('butinage');
      if (c.getLayer('butinage-trait')) c.removeLayer('butinage-trait');
      if (c.getSource('butinage')) c.removeSource('butinage');

      if (!vueGrappes && sites.length > 0) {
        // Rayons de butinage : des polygones geodesiques, donc des distances
        // JUSTES a toute latitude — la carte SVG de repli, elle, approxime.
        c.addSource('butinage', {
          type: 'geojson',
          data: {
            type: 'FeatureCollection',
            features: sites.flatMap((s) =>
              rayonsKm.map((km) => ({
                type: 'Feature' as const,
                properties: { km },
                geometry: {
                  type: 'Polygon' as const,
                  coordinates: [cercle(Number(s.longitude), Number(s.latitude), km * 1000)],
                },
              })),
            ),
          },
        });
        c.addLayer({
          id: 'butinage',
          type: 'fill',
          source: 'butinage',
          paint: { 'fill-color': '#d9a521', 'fill-opacity': 0.06 },
        });
        c.addLayer({
          id: 'butinage-trait',
          type: 'line',
          source: 'butinage',
          paint: { 'line-color': '#d9a521', 'line-width': 1, 'line-dasharray': [3, 2] },
        });
      }

      const ajouter = (lon: number, lat: number, html: string, taille: number) => {
        const pastille = document.createElement('div');
        pastille.className = 'z-carte-marqueur';
        pastille.style.width = `${taille}px`;
        pastille.style.height = `${taille}px`;
        // textContent, jamais innerHTML : les noms de site viennent de la base.
        pastille.textContent = html;
        marqueurs.current.push(new Marker({ element: pastille }).setLngLat([lon, lat]).addTo(c));
      };

      if (vueGrappes) {
        grappes.forEach((g) =>
          ajouter(
            Number(g.longitudeCentre),
            Number(g.latitudeCentre),
            String(g.nombreSites),
            20 + Math.sqrt(g.nombreSites) * 8,
          ),
        );
      } else {
        sites.forEach((s) =>
          ajouter(
            Number(s.longitude),
            Number(s.latitude),
            String(ruchesParSite.get(s.id) ?? 0),
            26,
          ),
        );
      }

      if (emprise) {
        c.fitBounds(emprise, { padding: 64, maxZoom: 13, duration: 400 });
      }
    };

    if (c.isStyleLoaded()) {
      dessiner();
    } else {
      c.once('load', dessiner);
    }
  }, [emprise, grappes, rayonsKm, ruchesParSite, sites, vueGrappes]);

  return <div ref={conteneur} className="z-carte-fond" role="application" aria-label={etiquette} />;
}
