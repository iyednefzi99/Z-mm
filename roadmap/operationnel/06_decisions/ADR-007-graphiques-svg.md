# ADR-007 — Graphiques : SVG maison plutôt que Chart.js

- **Date** : 2026-07-26
- **Statut** : 🟢 Accepté
- **Décideurs** : architecte front, designer
- **Bloque** : tableaux de bord (US-013, US-042), série de mesures (US-016/034)

---

## Contexte

L'annexe B du cahier des charges cite **Chart.js** parmi les technologies front
retenues. Au moment d'implémenter les tableaux de bord, la question s'est posée
concrètement : quatre graphiques sont nécessaires — poids par ruche, tendance de
récolte, série temporelle de capteur, chiffres de pilotage.

Écarter une technologie nommée au cahier demande une justification écrite ; c'est
l'objet de cet ADR.

## Ce que le contexte Zümm impose à un graphique

1. **Les jetons de la charte.** Couleurs, rayons, ombres, durées et courbes
   viennent de `design/tokens.json` et de `frontend/src/theme/tokens.css`. Un
   graphique qui ne les respecte pas est visuellement hors charte.
2. **Le mode sombre.** Il ne s'obtient pas en inversant les couleurs : la palette
   catégorielle sombre est **une palette différente**, recalculée puis validée
   contre la surface sombre.
3. **Le RTL.** L'arabe inverse la mise en page ; les infobulles, les étiquettes et
   les barres doivent suivre (`inset-inline-start`, jamais `left`).
4. **L'accessibilité.** Le graphique doit exister pour un lecteur d'écran et
   fournir un équivalent tabulaire — la DoD du projet et le trilinguisme le
   supposent.
5. **Le poids.** La PWA s'utilise sur un rucher, en bord de réseau.

## Décision

**Écrire les graphiques en SVG natif** (`frontend/src/ui/graphiques.tsx`, ~450
lignes), et documenter l'écart au cahier plutôt que de le taire.

### Pourquoi

- **Le thème.** En SVG, `stroke="var(--z-cat-1)"` suffit : le mode sombre et le
  RTL suivent sans une ligne de JavaScript. Chart.js dessine sur un `canvas` :
  les couleurs doivent lui être passées en valeurs résolues, donc lues au
  `getComputedStyle`, puis **re-passées à chaque changement de thème**. Le code de
  configuration nécessaire pour obtenir ce que le SVG donne gratuitement est du
  même ordre de grandeur que l'implémentation entière.
- **L'accessibilité.** Un `canvas` est un rectangle opaque pour un lecteur
  d'écran. Le SVG porte `role="img"`, un `aria-label`, et se double d'un `<table>`
  replié. Ce n'est pas un détail de conformité : c'est la seule façon dont un
  agent malvoyant lit un tableau de bord.
- **Le poids.** Chart.js pèse ~200 ko avant compression, pour quatre graphiques.
  Le module SVG en fait 6,4 ko (2,6 ko compressé) et part dans le morceau paresseux
  de la vue qui l'utilise.
- **Le contrôle des règles de lecture.** Pas d'axe Y double, extrémités arrondies,
  écarts de 2 px, étiquettes directes, motif de trait par série — autant de règles
  qu'on applique directement au lieu de les négocier avec les options d'une
  bibliothèque.

### Ce que ce choix coûte

- Les fonctions avancées sont à écrire : zoom, brossage temporel, empilement,
  export PNG. Aucune n'est demandée par une US aujourd'hui.
- Le viseur et l'infobulle sont du code à maintenir (et testé : `graphiques.test.tsx`).
- **Ce choix ne s'étend pas à la cartographie** : pour la carte, l'ADR opposé
  s'applique — MapLibre GL est intégré (US-030), parce que rendre des tuiles, gérer
  le zoom et projeter correctement est un problème qu'on n'écrit pas soi-même.
  La ligne de partage est là : on écrit ce qui est simple et très contraint par la
  charte, on intègre ce qui est complexe et standardisé.

## Conséquences

- L'annexe B du cahier doit mentionner cet ADR à la ligne « Chart.js ».
- Toute nouvelle famille de graphique passe par `graphiques.tsx` et ses règles ;
  ajouter une bibliothèque de rendu pour un seul écran rouvrirait la question.
- Le seuil de bascule est explicite : si une US exige du zoom temporel ou de
  l'export image, Chart.js redevient le bon choix et cet ADR est à réviser.

## Références

- `frontend/src/ui/graphiques.tsx`, `frontend/src/ui/graphiques.test.tsx`
- `design/DESIGN.md` § 2 (couleurs) et § 10 (tokens)
- [ADR-005](ADR-005-routage-front.md) — même logique pour le routage
