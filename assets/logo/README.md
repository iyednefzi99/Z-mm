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
│   ├── zumm-logo.svg          ✅ logo vertical (dégradé signature en <linearGradient>)
│   ├── zumm-icon.svg          ✅ emblème couleur seul
│   ├── zumm-icon-mono.svg     ✅ emblème monochrome (fill="currentColor", recolorable)
│   └── zumm-logo-light.svg    ⬜ variante claire pour fond --z-slate-900
├── png/                   # Exports raster transparents (rendus depuis les SVG)
│   ├── zumm-logo-2048.png     ✅
│   ├── zumm-icon-1024.png     ✅
│   └── zumm-icon-mono-1024.png ✅ mono corrigé (encre #2C4A42, fond transparent)
├── favicon/               ✅ favicon.svg (clair/sombre auto) · favicon.ico (16+32+48)
│                          # favicon-16/32/48.png · apple-touch-icon 180 px
│                          # icon-192.png · icon-512.png (PWA)
└── print/                 ✅ zumm-logo.pdf (vectoriel, 200×200 mm, RVB)
```

> **Statut** :
> - Les 3 masters SVG ont été vectorisés (potrace, 2 couches pour la couleur : forme
>   ardoise `#2C4A42` dessous, zones colorées par-dessus). Le dégradé est un
>   `<linearGradient>` **normalisé sur les tokens de la charte** (`#D9A521 → #2E9E3F`,
>   axe 120°), avec la répartition spatiale mesurée sur le PNG original (miel maintenu
>   jusqu'à ~45 %, bascule 45–75 %).
> - Défaut du fichier racine `zumm-icon-mono.png` : son damier de « transparence » est
>   **incrusté dans les pixels** (alpha opaque partout). Utiliser les versions corrigées
>   (`svg/` ou `png/zumm-icon-mono-1024.png`) ; la racine est conservée pour compatibilité.
> - `print/zumm-logo.pdf` est vectoriel mais **RVB** — pour un imprimeur exigeant le
>   CMJN, convertir (Ghostscript/Acrobat) ou réexporter depuis Inkscape/Scribus.
> - Restent en option : la variante claire `zumm-logo-light.svg` (fond `--z-slate-900`),
>   et une variante simplifiée de l'emblème pour les très petites tailles (16–24 px),
>   le motif circuit étant dense à cette échelle.

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
