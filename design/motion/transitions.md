# 🎞️ Motion — Transitions (animation · translation · morph)

> Référence de mouvement pour l'ensemble de l'interface Zümm : apparitions, déplacements
> et transformations de forme. Fait partie de la charte de design Zümm —
> voir [`../DESIGN.md`](../DESIGN.md) et la référence modale [`dialog.md`](./dialog.md).

Source : skill `transitions-dev` (Jakub Antalik), adaptée aux tokens Zümm. La modale
([`dialog.md`](./dialog.md)) reste le cas de référence ; ce fichier étend ses courbes et
durées aux autres motions.

---

## 🎯 Principes

Une interface Zümm **n'est jamais statique** : tout changement d'état visible s'accompagne
d'un mouvement **discret et intentionnel**.

- **Le mouvement guide, il ne décore pas.** En cas de doute, moins = mieux.
- **Entrée vive, sortie posée** : la sortie est plus rapide et plus douce que l'entrée
  (comme la modale : 250 ms / 150 ms).
- **Sens cohérent** : un panneau qui entre par la droite ressort par la droite ; une valeur
  qui monte se révèle du bas.
- **Toujours** protéger par `prefers-reduced-motion` (accessibilité — `DESIGN.md` §2).
- **Jamais de durée magique en dur** : utiliser les tokens ci-dessous.

Les trois familles couvertes :

| Famille | Quand | Exemples |
|---|---|---|
| **Animation** | apparition / mise à jour d'un élément | fondu, échelle, pop d'un KPI, badge d'alerte, coche de succès |
| **Translation** | déplacement d'un bloc dans l'espace | tiroir latéral, changement d'onglet/vue, révélation de contenu |
| **Morph** | la forme elle-même se transforme | accordéon, bouton `+` → menu, swap d'icône, redimensionnement de carte |

---

## 🎚️ Tokens

```css
:root {
  /* Durées */
  --z-dur-fast: 120ms;   /* micro : hover, swap d'icône, focus */
  --z-dur-base: 200ms;   /* standard : fondu, petit slide, badge */
  --z-dur-slow: 320ms;   /* ample : tiroir, panneau, transition de vue */

  /* Courbes */
  --z-ease-out:   cubic-bezier(0.22, 1, 0.36, 1);   /* entrées (repris de la modale) */
  --z-ease-in:    cubic-bezier(0.4, 0, 1, 1);        /* sorties */
  --z-ease-inout: cubic-bezier(0.65, 0, 0.35, 1);    /* morph / redimensionnement */

  /* Amplitudes */
  --z-shift:  12px;   /* petit déplacement (fade-up, reveal) */
  --z-scale-in: 0.96; /* échelle d'apparition (aligné --modal-scale) */
}
```

> Ces tokens **prolongent** ceux de [`dialog.md`](./dialog.md) : `--z-ease-out` est la même
> courbe que `--modal-ease`. Fusionne-les avec le bloc `:root` de `DESIGN.md` §10.

---

## ✨ Animation — apparition & mise à jour

### Fondu + échelle (apparition d'un élément)

```css
.z-appear {
  animation: z-appear var(--z-dur-base) var(--z-ease-out) both;
}
@keyframes z-appear {
  from { opacity: 0; transform: scale(var(--z-scale-in)); }
  to   { opacity: 1; transform: scale(1); }
}
```

### Fade-up (carte, ligne de liste, tuile ruche)

```css
.z-fade-up { animation: z-fade-up var(--z-dur-base) var(--z-ease-out) both; }
@keyframes z-fade-up {
  from { opacity: 0; transform: translateY(var(--z-shift)); }
  to   { opacity: 1; transform: translateY(0); }
}
/* Stagger : révéler une grille tuile par tuile */
.z-fade-up:nth-child(2) { animation-delay: 40ms; }
.z-fade-up:nth-child(3) { animation-delay: 80ms; }
.z-fade-up:nth-child(4) { animation-delay: 120ms; }
```

### Pop d'un chiffre KPI mis à jour

```css
.z-pop { animation: z-pop var(--z-dur-base) var(--z-ease-out); }
@keyframes z-pop {
  0%   { transform: scale(1); }
  40%  { transform: scale(1.08); }
  100% { transform: scale(1); }
}
```

### Coche de succès (fin de tâche, ruche revenue « saine »)

```css
.z-check path {
  stroke: var(--z-success);
  stroke-dasharray: 24;
  stroke-dashoffset: 24;
  animation: z-check var(--z-dur-slow) var(--z-ease-out) forwards;
}
@keyframes z-check { to { stroke-dashoffset: 0; } }
```

---

## ➡️ Translation — déplacement

### Tiroir / panneau latéral (détail d'une ruche)

```css
.z-drawer {
  transform: translateX(100%);
  transition: transform var(--z-dur-slow) var(--z-ease-out);
  will-change: transform;
}
.z-drawer.is-open { transform: translateX(0); }
/* Sortie : même courbe, plus rapide (entrée vive, sortie posée) */
.z-drawer.is-closing { transition-duration: var(--z-dur-base); }
```

### Reveal de contenu (bloc replié → déplié verticalement)

```css
.z-reveal {
  transform: translateY(calc(-1 * var(--z-shift)));
  opacity: 0;
  transition:
    transform var(--z-dur-base) var(--z-ease-out),
    opacity   var(--z-dur-base) var(--z-ease-out);
}
.z-reveal.is-shown { transform: translateY(0); opacity: 1; }
```

### Changement d'onglet / de vue (glissement horizontal)

```css
.z-view { transition: transform var(--z-dur-slow) var(--z-ease-inout), opacity var(--z-dur-base) linear; }
.z-view.leave-left  { transform: translateX(-24px); opacity: 0; }
.z-view.enter-right { transform: translateX(24px);  opacity: 0; }
.z-view.active      { transform: translateX(0);     opacity: 1; }
```

> Indicateur d'onglet actif qui **glisse** sous l'onglet sélectionné : anime `transform: translateX()`
> de la barre (pas `left`), en `--z-dur-base var(--z-ease-inout)`.

---

## 🔀 Morph — transformation de forme

### Accordéon / disclosure (déplier une section)

```css
.z-acc-panel {
  display: grid;
  grid-template-rows: 0fr;
  transition: grid-template-rows var(--z-dur-slow) var(--z-ease-inout);
}
.z-acc-panel > .inner { overflow: hidden; }
.z-acc-panel.is-open { grid-template-rows: 1fr; }
/* Chevron qui pivote en même temps */
.z-acc-chevron { transition: transform var(--z-dur-base) var(--z-ease-inout); }
.z-acc-trigger[aria-expanded="true"] .z-acc-chevron { transform: rotate(180deg); }
```

### Bouton `+` → menu (FAB morph)

```css
.z-fab .bar { transition: transform var(--z-dur-base) var(--z-ease-inout); transform-origin: center; }
.z-fab[aria-expanded="true"] .bar-v { transform: rotate(90deg) scaleY(0); } /* + devient × / disparaît */
.z-fab[aria-expanded="true"] .bar-h { transform: rotate(180deg); }
```

### Swap d'icône (état ↔ état, ex. capteur en ligne ↔ hors-ligne)

```css
.z-swap { display: grid; }
.z-swap > * { grid-area: 1 / 1; transition: opacity var(--z-dur-fast) linear, transform var(--z-dur-fast) var(--z-ease-out); }
.z-swap > .out { opacity: 0; transform: scale(0.8); }
.z-swap > .in  { opacity: 1; transform: scale(1); }
```

### Redimensionnement de carte (tuile ruche compacte ↔ détaillée)

```css
.z-resize { transition: width var(--z-dur-slow) var(--z-ease-inout), height var(--z-dur-slow) var(--z-ease-inout); }
```

> Pour un morph fluide entre deux tailles/positions non triviales, préférer l'API
> **View Transitions** (`document.startViewTransition`) quand elle est disponible ;
> `--z-ease-inout` et `--z-dur-slow` restent les valeurs de référence.

---

## ♿ Garde accessibilité (obligatoire)

À placer **une fois** dans la feuille globale — elle neutralise tout ce qui précède
pour les personnes sensibles au mouvement.

```css
@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    animation-duration: 0.001ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.001ms !important;
    scroll-behavior: auto !important;
  }
}
```

> L'état final (ouvert/fermé, visible/masqué) doit rester **correct sans l'animation** :
> le mouvement enrichit, il ne conditionne jamais l'affichage.

---

## 🚀 Utilisation

1. Importe le bloc `:root` de **Tokens** (ou fusionne-le avec `DESIGN.md` §10 et
   [`dialog.md`](./dialog.md)).
2. Ajoute la **garde `prefers-reduced-motion`** à ta feuille globale.
3. Choisis la famille selon l'intention : *animation* (apparition), *translation*
   (déplacement), *morph* (transformation) — puis reprends le CSS correspondant.
4. Modale / dialog : ne réinvente rien, utilise [`dialog.md`](./dialog.md) +
   [`modal.css`](./modal.css) + [`Dialog.jsx`](./Dialog.jsx).

> En cas de doute sur un choix de mouvement, la charte [`../DESIGN.md`](../DESIGN.md) fait foi.