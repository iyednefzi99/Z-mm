# 🎞️ Motion — Dialogue / Modale

> Référence de mouvement pour l'ouverture et la fermeture d'une modale (dialog).
> Fait partie de la charte de design Zümm — voir [`../DESIGN.md`](../DESIGN.md).

Source : skill `transitions-dev` (Jakub Antalik), adaptée à React.

---

## 🎯 Comportement

- **Ouverture** : montée en échelle `0.96 → 1` + fondu `0 → 1` sur **250 ms**.
- **Fermeture** : redescente `1 → 0.96` + fondu `1 → 0` sur **150 ms** (plus rapide, plus douce).
- La courbe `cubic-bezier(0.22, 1, 0.36, 1)` (ease-out marqué) donne un mouvement vif à l'entrée, posé à la sortie.
- `prefers-reduced-motion` : transitions désactivées (accessibilité — voir `DESIGN.md` §2.3).

**Piège corrigé** : à la fermeture, la classe `is-closing` doit être retirée *après* la fin de l'animation
(le nœud reste monté pendant `--modal-close-dur`), sinon la modale « saute » à la réouverture.
En React, c'est le rôle de l'état `mounted` + `setTimeout(CLOSE_MS)`.

---

## 🎚️ Tokens

```css
:root {
  --modal-open-dur: 250ms;
  --modal-close-dur: 150ms;
  --modal-scale: 0.96;
  --modal-scale-close: 0.96;
  --modal-ease: cubic-bezier(0.22, 1, 0.36, 1);
}
```

> Si tu changes `--modal-close-dur`, aligne la constante `CLOSE_MS` dans [`Dialog.jsx`](./Dialog.jsx).

---

## 📦 Fichiers

| Fichier | Rôle |
|---|---|
| [`modal.css`](./modal.css) | Styles de transition (`.t-modal`, `.is-open`, `.is-closing`) + garde reduced-motion |
| [`Dialog.jsx`](./Dialog.jsx) | Composant React pilotant les classes par l'état (ouverture/fermeture, Échap, clic backdrop) |

---

## 🚀 Utilisation

1. Importe le bloc `:root` (ou fusionne-le avec tes tokens CSS de `DESIGN.md` §10).
2. Ajoute `modal.css` à ta feuille globale.
3. Utilise `<Dialog open={...} onClose={...}>…</Dialog>`.