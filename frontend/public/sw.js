/*
 * Service worker minimal de la PWA Zümm.
 *
 * Stratégie prudente et sûre pour une application de données :
 *  - la coquille (navigation) est servie « network-first », avec repli sur le
 *    cache hors ligne : on ne sert jamais une version périmée quand le réseau
 *    répond ;
 *  - les appels d'API (/api, /actuator) ne sont JAMAIS mis en cache : des données
 *    métier périmées seraient trompeuses, et les réponses sont authentifiées.
 *
 * Volontairement simple (pas de précache d'assets hashés) : Vite renomme les
 * bundles à chaque build, un précache statique deviendrait vite faux. À remplacer
 * par une génération outillée (vite-plugin-pwa / Workbox) quand une story l'exige.
 */

const CACHE = 'zumm-coquille-v1';
const COQUILLE = ['/', '/index.html', '/manifest.webmanifest', '/favicon.svg'];

self.addEventListener('install', (evenement) => {
  evenement.waitUntil(caches.open(CACHE).then((cache) => cache.addAll(COQUILLE)));
  self.skipWaiting();
});

self.addEventListener('activate', (evenement) => {
  evenement.waitUntil(
    caches
      .keys()
      .then((cles) => Promise.all(cles.filter((c) => c !== CACHE).map((c) => caches.delete(c))))
      .then(() => self.clients.claim()),
  );
});

self.addEventListener('fetch', (evenement) => {
  const requete = evenement.request;
  const url = new URL(requete.url);

  // Jamais de cache pour les données : réseau seul.
  if (url.pathname.startsWith('/api') || url.pathname.startsWith('/actuator')) {
    return;
  }

  // Navigation : réseau d'abord, repli cache hors ligne.
  if (requete.mode === 'navigate') {
    evenement.respondWith(
      fetch(requete)
        .then((reponse) => {
          const copie = reponse.clone();
          caches.open(CACHE).then((cache) => cache.put('/index.html', copie));
          return reponse;
        })
        .catch(() => caches.match('/index.html').then((r) => r ?? Response.error())),
    );
  }
});
