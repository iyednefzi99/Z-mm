# 🏃 SPRINT-13 : PWA livrable, graphiques et carte

**Thème :** Rendre le front déployable, lisible et réellement utilisable hors ligne
**Objectif :** Que la PWA soit servie par la pile, démarre sans réseau, et montre les données au lieu de les tabuler
**Période :** 2027-02-02 → 2027-02-15 (14 jours)
**Story Points :** 32 / Capacity : 40

---

## 📋 User Stories

| ID | Story | Points | Statut |
|:---|:---|:---:|:---|
| US-064 | Déploiement de la PWA dans la pile | 5 | 🟢 Livré |
| US-065 | Démarrage hors ligne réel (précache généré) | 8 | 🟢 Livré |
| US-066 | Graphiques des tableaux de bord | 8 | 🟢 Livré |
| US-067 | Fond cartographique réel (MapLibre + OSM) | 8 | 🟢 Livré |
| US-068 | Bandeau de mise à jour de la PWA | 3 | 🟢 Livré |

---

## 🔎 Défauts corrigés

### 1. La PWA n'était pas déployée

Aucun `Dockerfile` front, et `infra/nginx/nginx.conf` envoyait `/` vers
**Keycloak**. La console n'existait que par `npm run dev`. Corrigé :
`infra/frontend.Dockerfile` (build Vite → nginx statique),
`frontend/nginx-pwa.conf` (repli SPA, cache immuable sur les bundles hashés,
`no-cache` sur la coquille), service dans le compose, et routage `/` → PWA,
`/realms/` → Keycloak.

### 2. La PWA ne démarrait pas hors ligne

Le service worker écrit à la main mettait `index.html` en cache mais **pas** les
bundles hashés qu'il référence : hors réseau, la coquille se chargeait puis
demandait un `/assets/index-XXXX.js` absent — écran blanc. Seul un précache
**généré au build** connaît ces noms. `vite-plugin-pwa` (Workbox) le produit :
30 entrées, 570 Kio.

Le fond cartographique est **exclu du précache** et mis en cache à son premier
usage : l'imposer à l'installation ferait télécharger 250 ko compressés à qui
n'ouvrira peut-être jamais la carte.

### 3. Aucun graphique

Production, prévision de récolte et série EWMA n'existaient qu'en tableaux.
`ui/graphiques.tsx` ajoute courbe multi-séries (viseur + infobulle), barres
horizontales et divergentes, et tuiles de statistique — en SVG natif
([ADR-007](../06_decisions/ADR-007-graphiques-svg.md)).

**Constat de conception.** La palette catégorielle de `DESIGN.md` a été validée
par calcul plutôt qu'à l'œil. Deux emplacements adjacents (`#EB6834` et `#E87BA4`)
n'atteignaient pas le plancher de distinction **en vision normale** — ΔE OKLab de
12,9 pour un plancher de 15. Les permuter suffit à faire passer la palette
entière. La palette du mode sombre a par ailleurs dû être **recalculée** : un
simple éclaircissement des mêmes teintes sort de la bande de clarté lisible sur
fond `#25423B`.

### 4. La carte était une projection plate

Rendu SVG avec approximation `cos(lat)`. MapLibre GL (BSD, compatible avec la
porte anti-AGPL de la CI) apporte un fond OSM et des rayons de butinage
**géodésiques**, donc justes à toute latitude. Chargé paresseusement, dans son
propre morceau ; le rendu SVG est conservé comme repli quand WebGL manque — c'est
aussi ce qui tourne en test.

⚠️ Les tuiles publiques d'OpenStreetMap ont une politique d'usage qui **exclut la
production**. `VITE_TUILES_URL` permet de pointer un fournisseur souscrit ou un
serveur interne — seule option qui garantisse qu'aucune position de rucher ne sorte
du système.

---

## ✅ Definition of Done

- [x] `npm run typecheck && npm run lint && npm test && npm run build` verts
- [x] 120 tests Vitest, 0 erreur ESLint
- [x] Morceau initial 78 ko compressés ; MapLibre isolé en morceau paresseux (252 ko)
- [x] Mode sombre et RTL portés par les jetons, sans code conditionnel

## 🔁 Rétrospective

**Ce qui a marché.** Valider la palette par un script plutôt qu'à l'œil : le défaut
trouvé était rigoureusement invisible en relecture.

**Ce qu'on retient.** « Ça marche en développement » ne dit rien du déploiement.
Le front était complet et testé depuis trois sprints — et n'était servi par
personne.

**Ce qui reste ouvert.** Les traductions restent compilées en dur
(`i18n/console.ts`, 944 lignes) : à externaliser en ressources par locale, sans
perdre le contrôle de parité qui casse aujourd'hui la compilation quand une clé
manque.
