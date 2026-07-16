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
│   ├── zumm-logo.svg          — logo vertical (dégradé signature en <linearGradient>)
│   ├── zumm-icon.svg          — emblème seul
│   ├── zumm-icon-mono.svg     — emblème monochrome (fonds sombres)
│   └── zumm-logo-light.svg    — variante claire pour fond --z-slate-900
├── png/                   # Exports raster transparents : 512 / 1024 / 2048 / 4096 px
├── favicon/               # favicon.svg · 16 / 32 / 48 px · apple-touch-icon 180 px
│                          # icônes PWA 192 / 512 px (maskable)
└── print/                 # zumm-logo.pdf / .eps vectoriel (CMJN pour l'imprimeur)
```

> **Statut** : les masters SVG restent à produire (vectorisation manuelle du logo dans
> Figma / Inkscape / Illustrator — éviter le tracé automatique, qui salit les courbes
> d'un logo à dégradé). Les PNG à la racine sont conservés tels quels : ils sont
> référencés par le `README.md` du dépôt et les documents LaTeX (`\graphicspath`).

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
