# 🏃 SPRINT-13 : PWA livrable, graphiques et carte

**Thème :** Rendre le front déployable, lisible et réellement utilisable hors ligne
**Objectif :** Que la PWA soit servie par la pile, démarre sans réseau, et montre les données au lieu de les tabuler
**Période :** 2027-02-02 → 2027-02-15 (14 jours)
**Story Points :** 32 / Capacity : 40

---

## 📅 Cérémonies Scrum

| Cérémonie | Date/Heure | Durée |
|:---|:---|:---|
| Sprint Planning | 2027-02-02 09:00-13:00 | 4h |
| Daily Scrum | Tous les jours 09:15 (15 min) | 15 min |
| Sprint Review | 2027-02-15 14:00-16:00 | 2h |
| Sprint Retrospective | 2027-02-15 16:00-17:00 | 1h |

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

## 🎯 Sprint Review - Démonstration

**Date :** 2027-02-15 14:00-16:00

Première revue jouée **entièrement depuis la pile déployée** — `docker compose up`
puis un navigateur sur `https://localhost`. Aucun `npm run dev` en séance : c'est
le sens même du sprint.

1. **La console existe.** Ouverture de `/` : la PWA est servie par la pile, plus
   la console d'administration Keycloak. Installation de l'application depuis le
   navigateur, puis lancement depuis l'icône du système.
2. **Le mode avion.** Wi-Fi coupé au milieu de la démonstration, application
   relancée à froid : elle **démarre** et rend ses écrans. Avant ce sprint, le même
   geste donnait un écran blanc — la coquille était en cache, pas les bundles
   qu'elle référence. Le précache généré au build compte 30 entrées, 570 Kio.
3. **Les données se regardent.** Bascule des tableaux de bord sur les courbes :
   production multi-séries avec viseur et infobulle, barres divergentes des écarts,
   tuiles de statistique, série EWMA d'anomalie. Chaque graphique est doublé de son
   **équivalent tabulaire**, lu au lecteur d'écran en séance.
4. **La carte est juste.** Fond OSM MapLibre, rayons de butinage **géodésiques** :
   la démonstration compare un rucher à Bizerte et un rucher en Norvège — la
   projection plate du SPRINT-10 déformait le second de plusieurs kilomètres.
   WebGL désactivé à la volée : le rendu SVG prend le relais sans erreur.
5. **La mise à jour se voit.** Nouvelle image déployée pendant la séance : le
   bandeau de mise à jour apparaît, l'utilisateur l'accepte, la nouvelle version
   prend la main sans perdre l'écran courant.
6. **Le thème et la langue.** Bascule clair/sombre du système, puis FR → AR :
   l'interface passe en RTL et la palette de séries change — non pas éclaircie,
   mais **recalculée** pour le fond sombre.

**Ce que la revue a retenu.** La palette catégorielle a été validée par calcul, pas
à l'œil : deux emplacements adjacents de la charte (`#EB6834` / `#E87BA4`)
n'atteignaient pas le plancher de distinction en vision normale (ΔE OKLab 12,9
pour un plancher de 15). Le défaut était invisible en relecture.

**Réserve levée en séance.** Les tuiles publiques d'OpenStreetMap excluent la
production. `VITE_TUILES_URL` est ajoutée pour pointer un fournisseur souscrit ou
un serveur interne — la seule option qui garantisse qu'aucune position de rucher
ne sorte du système.

---

## ⚠️ Risques Identifiés

| Risque | Impact | Mitigation |
|:---|:---|:---|
| Un service worker mal réglé sert indéfiniment une version périmée | Correctif de sécurité jamais reçu par les postes installés | `no-cache` sur la coquille, cache immuable réservé aux bundles hashés, bandeau de mise à jour (US-068) |
| Le fond cartographique tiers expose les positions des ruchers | Fuite du premier actif métier — le vol de ruches est le sinistre n° 1 | Tuiles paramétrables (`VITE_TUILES_URL`), aucune position envoyée dans l'URL de tuile |
| MapLibre alourdit le paquet initial | PWA inutilisable en 3G au rucher | Chargement paresseux dans son propre morceau : 78 ko initiaux, 252 ko à l'ouverture de la carte seulement |
| Graphiques SVG maison plutôt qu'une bibliothèque | Réinvention, accessibilité oubliée | [ADR-007](../06_decisions/ADR-007-graphiques-svg.md) ; légende et équivalent tabulaire obligatoires, jamais d'identité par la seule couleur |
| Licence AGPL introduite par une dépendance de cartographie | Contamination du livrable | Porte anti-AGPL de la CI ; MapLibre est en BSD |

---

## 📊 Burndown Chart

| Jour | Reste à faire (idéal) | Reste à faire (réel) | Notes |
|:---|:---:|:---:|:---|
| Jour 1 | 32 | 32 | Constat : le front n'est servi par personne depuis trois sprints |
| Jour 3 | 27 | 27 | US-064 : `frontend.Dockerfile`, `nginx-pwa.conf`, routage `/` → PWA |
| Jour 6 | 20 | 19 | US-065 : précache généré (Workbox), 30 entrées ; carte exclue du précache |
| Jour 9 | 13 | 14 | US-066 : courbe, barres, tuiles — la validation de palette a coûté une demi-journée |
| Jour 12 | 5 | 5 | US-067 : MapLibre, rayons géodésiques, repli SVG conservé pour les tests |
| Jour 14 | 0 | 0 | US-068 : bandeau de mise à jour ; 120 tests Vitest, build vert |

*Écart au plan : US-066 a débordé d'un jour sur la validation calculée de la
palette — dépense assumée, elle a trouvé un défaut invisible autrement.*

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
manque. → **Soldé au [SPRINT-15](SPRINT-15.md)**, la parité étant reportée sur le
type de retour des chargeurs.
