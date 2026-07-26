/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';
import { VitePWA } from 'vite-plugin-pwa';

export default defineConfig({
  plugins: [
    react(),
    /**
     * PWA (SPRINT-13). Le service worker ecrit a la main cachait `index.html`
     * mais PAS les bundles hashes qu'il reference : hors ligne, la coquille se
     * chargeait puis demandait un `/assets/index-XXXX.js` absent du cache —
     * ecran blanc. Seul un precache GENERE AU BUILD connait ces noms ; c'est tout
     * l'objet de Workbox.
     */
    VitePWA({
      registerType: 'prompt',
      injectRegister: null,
      manifest: false,
      workbox: {
        // Le precache prend les noms reels produits par le build. MapLibre est
        // volumineux : la limite par defaut (2 Mio) le rejetterait du precache.
        globPatterns: ['**/*.{js,css,html,svg,png,webmanifest}'],
        // Le fond cartographique est exclu du PRECACHE : l'imposer a
        // l'installation ferait telecharger 250 ko compresses a un utilisateur
        // qui n'ouvrira peut-etre jamais la carte, et annulerait le benefice du
        // chargement paresseux. Il est mis en cache a son premier usage (regle
        // d'execution ci-dessous) et reste alors disponible hors ligne.
        globIgnores: ['**/CarteFond-*.js', '**/maplibre*'],
        maximumFileSizeToCacheInBytes: 4 * 1024 * 1024,
        navigateFallback: '/index.html',
        // JAMAIS d'API en cache : une mesure de capteur perimee ou une position
        // de rucher servie depuis le disque induirait l'apiculteur en erreur.
        navigateFallbackDenylist: [/^\/api/, /^\/actuator/, /^\/realms/],
        runtimeCaching: [
          {
            // Le fond cartographique, mis en cache a son premier chargement.
            urlPattern: /\/assets\/CarteFond-.*\.js$/,
            handler: 'StaleWhileRevalidate',
            options: { cacheName: 'zumm-carte' },
          },
          {
            // Les tuiles cartographiques, elles, se cachent volontiers : un fond
            // de carte vieux d'une semaine reste juste, et c'est ce qui rend la
            // carte utilisable sur un rucher sans reseau.
            urlPattern: ({ url }) => url.hostname.endsWith('tile.openstreetmap.org'),
            handler: 'CacheFirst',
            options: {
              cacheName: 'zumm-tuiles',
              expiration: { maxEntries: 500, maxAgeSeconds: 30 * 24 * 3600 },
              cacheableResponse: { statuses: [0, 200] },
            },
          },
        ],
        cleanupOutdatedCaches: true,
      },
      devOptions: { enabled: false },
    }),
  ],
  // US-049 : Vitest partage la configuration de Vite (alias, plugins) plutot que
  // d'en maintenir une seconde. jsdom fournit le DOM ; le fichier d'amorcage
  // ajoute les assertions de jest-dom et nettoie localStorage entre les tests.
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/tests/amorcage.ts',
    css: false,
    coverage: {
      provider: 'v8',
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/tests/**', 'src/**/*.d.ts', 'src/main.tsx'],
    },
  },
  server: {
    port: 5173,
    // En developpement, l'API est appelee via le meme origine que le client :
    // pas de CORS a ouvrir. En production, c'est le proxy inverse qui route.
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      '/actuator': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
});
