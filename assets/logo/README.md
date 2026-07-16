# Assets logo Zümm

Organisation des fichiers de marque. La règle d'or : **le SVG est la source de vérité**,
tout le reste (PNG, favicon, print) est un export dérivé — on ne retouche jamais un export,
on ré-exporte depuis le master.

## Arborescence

```
assets/logo/
├── zumm-logo.png          # Logo vertical (628×628) — export historique, conservé
├── zumm-icon.png          # Emblème (479×479) — export historique, conservé
├── zumm-icon-mono.png     # Emblème monochrome (1024×1024) — export historique, conservé
├── zumm-brandsheet.png    # Planche de marque (1024×1024)
├── svg/                   # ⭐ Masters vectoriels (source de vérité)
│   ├── zumm-icon-mono.svg     ✅ emblème monochrome (fill="currentColor", recolorable)
│   ├── zumm-logo.svg          ⬜ logo vertical (dégradé signature en <linearGradient>)
│   ├── zumm-icon.svg          ⬜ emblème couleur seul
│   └── zumm-logo-light.svg    ⬜ variante claire pour fond --z-slate-900
├── png/                   # Exports raster transparents
│   └── zumm-icon-mono-1024.png ✅ mono corrigé (encre #2C4A42, fond transparent)
├── favicon/               ✅ favicon.svg (clair/sombre auto) · favicon.ico (16+32+48)
│                          # favicon-16/32/48.png · apple-touch-icon 180 px · icon-192.png
│                          ⬜ icon-512.png (PWA — attend le master SVG couleur)
└── print/                 ⬜ zumm-logo.pdf / .eps vectoriel (CMJN pour l'imprimeur)
```

> **Statut** :
> - `zumm-icon-mono.svg` a été vectorisé (potrace) depuis le PNG mono **nettoyé** : le
>   fichier racine `zumm-icon-mono.png` a un défaut — son damier de « transparence » est
>   **incrusté dans les pixels** (alpha opaque partout). Utiliser les versions corrigées
>   (`svg/` ou `png/zumm-icon-mono-1024.png`) ; la racine est conservée pour compatibilité.
> - Les masters **couleur** (`zumm-logo.svg`, `zumm-icon.svg`, variante claire) restent à
>   produire par vectorisation manuelle (Figma / Inkscape / Illustrator — éviter le tracé
>   automatique sur un logo à dégradé). Les exports ⬜ en découleront.
> - Amélioration possible : une variante simplifiée de l'emblème pour les très petites
>   tailles (16–24 px), le motif circuit étant dense à cette échelle.

## Règles d'export (qualité)

| Usage | Format | Règle |
|---|---|---|
| Web / interfaces | SVG | Toujours préférer le SVG (poids ~5–20 Ko, netteté infinie) |
| Écran, si raster requis | PNG transparent | ≥ 2× la taille d'affichage (Retina) |
| Impression | PDF / EPS vectoriel | Sinon PNG ≥ 300 dpi à la taille d'impression |
| App / PWA | PNG | Icône iOS 1024×1024, PWA 192 + 512 px maskable |
| **Jamais** | JPEG | Compression destructive + pas de transparence |

## Rappels de la charte (`design/DESIGN.md` §6)

- Dégradé signature : `linear-gradient(120deg, #D9A521 0%, #2E9E3F 100%)` — ne pas recolorer.
- Zone de protection ≥ hauteur du « Z » ; emblème ≥ 24 px.
- Fonds autorisés : `--z-bg`, `--z-surface`, `--z-slate-900` (version claire/mono).
- Interdits : déformation, ombre portée, rotation, séparation abeille/wordmark incohérente.
