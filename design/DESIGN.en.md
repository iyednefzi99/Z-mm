# DESIGN.md — Zümm

> Beekeeping Management Information System (SIG apicole)
>
> This is the **design reference** for the Zümm product: colors, typography, spacing,
> components, and the reasoning behind each choice. It follows the spirit of the
> `DESIGN.md` specification (see <https://getdesign.md>): a single document, readable by
> humans and coding agents alike, that guarantees the visual consistency of every Zümm
> interface and medium.
>
> **This is not front-end code.** It is the source of truth the front-end (and any other
> medium: web, mobile, print, slides) must respect.
>
> **Method**: the structure builds on **Adobe Spectrum**'s approach
> (<https://spectrum.adobe.com>) — layered tokens (global → alias/semantic → component),
> "t-shirt" sizing (sm/md/lg), semantic colors and accessibility-first — expressed in the
> `DESIGN.md` format. The visual identity, however, is **Zümm's own**.

---

## 1. Brand foundations

Zümm is a **beekeeping information system**: it links the living world of the hive to the
digital world of data. The whole identity flows from this duality.

- **Nature / beekeeping** → the bee, the honeycomb hexagon, honey amber, foliage green.
- **Technology / information** → the circuit-board trace, connected nodes, the repeatable
  hexagonal mesh (like a network of sensors).

**Personality**: precise, alive, reassuring, sober. We avoid pure "cold tech" and "kitsch
honey"; we aim for the balance of organic + engineering.

**Guiding keywords**: hexagonal, connected, natural, clear, reliable.

---

## 2. Color palette

The palette comes straight from the logo: a **honey-gold → emerald-green** gradient,
anchored by a deep **slate green**. The values below are the official tokens.

### 2.1 Brand colors

| Token | Hex | Role |
|---|---|---|
| `--z-honey-500` | `#D9A521` | **Primary — Honey.** Main accent, CTAs, active elements. |
| `--z-honey-600` | `#B8860B` | Dark honey — primary hover/press, text on light backgrounds. |
| `--z-honey-300` | `#EBC55B` | Light honey — highlights, badges, soft backgrounds. |
| `--z-green-500` | `#2E9E3F` | **Secondary — Hive green.** Success, growth, "healthy" data. |
| `--z-green-600` | `#1F7A2E` | Dark green — secondary hover/press. |
| `--z-green-300` | `#6FC46B` | Light green — subtle positive indicators. |
| `--z-slate-800` | `#2C4A42` | **Brand neutral — Slate green.** Wordmark, headings, strong text. |
| `--z-slate-900` | `#1E3A34` | Deep green — dark surfaces, headers, dark mode. |

> **Signature gradient**: `linear-gradient(120deg, #D9A521 0%, #2E9E3F 100%)`.
> Reserve it for emphasis surfaces (hero, cover page, emblem). Never place body text on it.

### 2.2 Neutrals

| Token | Hex | Role |
|---|---|---|
| `--z-ink` | `#1B2320` | Primary text (prefer over `#000`). |
| `--z-ink-muted` | `#5B6B65` | Secondary text, captions, metadata. |
| `--z-line` | `#DCE4E0` | Borders, dividers, field outlines. |
| `--z-surface` | `#F5F7F5` | Section background, recessed cards. |
| `--z-bg` | `#FCFDFC` | Page background (light mode) — green-tinted off-white, **never pure `#FFFFFF`** (see [`principes/eviter-noir-et-blanc-purs.md`](./principes/eviter-noir-et-blanc-purs.md)). |

### 2.3 Semantic colors (states)

| Token | Hex | Usage |
|---|---|---|
| `--z-success` | `#2E9E3F` | Healthy hive, task done (reuses brand green). |
| `--z-warning` | `#D9A521` | Moderate alert, threshold near (reuses honey). |
| `--z-danger` | `#C0392B` | Swarming, sensor offline, critical error. |
| `--z-info` | `#2C7A7B` | Neutral information, raw telemetry. |

> **Rule**: honey and green already carry meaning (warning / success). Don't use them
> decoratively in a dashboard where they encode a state.

### 2.4 Contrast & accessibility

Measured WCAG ratios (foreground on `--z-bg`, light mode, unless noted). Thresholds:
**AA** = 4.5:1 (normal text) / 3:1 (large text ≥ 24 px or 19 px bold); **AAA** = 7:1.

| Pair (foreground on background) | Ratio | Verdict |
|---|---|---|
| `--z-ink` on `--z-bg` | 15.8:1 | ✅ AAA |
| `--z-ink-muted` on `--z-bg` | 5.5:1 | ✅ AA |
| `--z-green-600` on `--z-bg` | 5.4:1 | ✅ AA |
| `--z-honey-600` on `--z-bg` | 3.3:1 | ⚠️ AA large text only |
| `--z-green-500` on `--z-bg` | 3.5:1 | ⚠️ AA large text / icons |
| `--z-honey-500` on `--z-bg` | 2.2:1 | ❌ fill only, never as text |
| `--z-slate-900` on `--z-honey-500` (CTA text) | 5.5:1 | ✅ AA |
| White on `--z-green-500` (secondary button) | 3.5:1 | ⚠️ AA large text |
| White on `--z-danger` | 5.4:1 | ✅ AA |
| White on `--z-info` | 5.0:1 | ✅ AA |
| `#EAF1EE` on `--z-slate-900` (dark mode) | 10.7:1 | ✅ AAA |
| `--z-honey-500` on `--z-slate-900` (dark accent) | 5.5:1 | ✅ AA |
| `--z-green-300` on `--z-slate-900` (dark mode) | 5.7:1 | ✅ AA |

**Takeaways**:
- **Honey `#D9A521` never passes AA as text**: use it as a fill for surfaces/CTAs with dark
  text (`--z-slate-900`) on top, not as a thin text color.
- Green `#2E9E3F` on `--z-bg`: fine for icons and large text; for fine text prefer
  `--z-green-600`. Secondary button (white on green): reserve for labels ≥ 16 px bold.
- In dark mode avoid green `500` as thin text → `--z-green-300` (see § 8).

---

## 3. Typography

Sober and legible; the logo uses a humanist grotesque (round shapes, `ü` with umlaut).
We extend it to the screen.

- **Families**
  - *Headings & UI*: **Inter** (or system fallback `-apple-system, Segoe UI, Roboto, sans-serif`).
    Alternative faithful to the wordmark: *Poppins* / *Nunito Sans* (roundness).
  - *Numbers & data*: Inter with **tabular-nums** (column alignment).
  - *Monospace* (logs, sensor IDs): `JetBrains Mono`, fallback `ui-monospace`.

- **Type scale** (16 px base, ~1.25 ratio)

  | Role | Size | Weight | Line height |
  |---|---|---|---|
  | Display | 40 px | 700 | 1.1 |
  | H1 | 32 px | 700 | 1.15 |
  | H2 | 25 px | 600 | 1.2 |
  | H3 | 20 px | 600 | 1.3 |
  | Body | 16 px | 400 | 1.5 |
  | Small | 14 px | 400 | 1.45 |
  | Caption | 12 px | 500 | 1.4 |

- **Rules**: headings in `--z-slate-800`, body in `--z-ink`, captions in `--z-ink-muted`.
  Max line length ≈ 70 characters. Always write **"Zümm"** with the umlaut.

---

## 4. Spacing & layout

Spacing scale on a **4 px** base (multiples):

`4 · 8 · 12 · 16 · 24 · 32 · 48 · 64`

- Card inner padding: `16–24 px`.
- Grid gutter: `24 px`.
- Vertical rhythm between sections: `48–64 px`.
- Max content width: `1200 px` (dashboards) / `720 px` (reading).

**Radii (border-radius)** — the brand is hexagonal but the UI stays soft:
`--z-radius-sm: 6px` · `--z-radius-md: 12px` · `--z-radius-lg: 20px` · `--z-radius-pill: 999px`.

**Elevation (shadows)** — subtle, tinted slate green rather than pure black:
- `--z-shadow-1: 0 1px 2px rgba(28,74,66,.08)`
- `--z-shadow-2: 0 4px 12px rgba(28,74,66,.10)`
- `--z-shadow-3: 0 12px 32px rgba(28,74,66,.14)`

---

## 5. Iconography & motifs

- **Icon style**: linear, `1.75 px` stroke, rounded corners — consistent with the logo's
  "circuit" trace. Recommended library: *Lucide* / *Phosphor* (thin/regular).
- **Signature motif**: the **hexagonal grid** (honeycomb). Use it as a very light watermark
  (`opacity ≤ .06`) on section backgrounds, never behind dense text.
- **Nodes & connections**: small filled circles linked by 90°/45° elbow lines to evoke the
  sensor network — reserved for illustrations, not functional UI.

---

## 6. Logo — usage rules

- Reference files in `../assets/logo/`:
  `zumm-logo.png` (vertical), `zumm-icon.png` (emblem), `zumm-icon-mono.png` (monochrome),
  `zumm-brandsheet.png` (brand sheet).
- **Vector source of truth**: the SVG masters in `../assets/logo/svg/`
  (`zumm-logo.svg`, `zumm-icon.svg`, `zumm-icon-mono.svg`, `zumm-logo-light.svg`).
  PNGs are **derived exports** — never retouch a raster; always re-export from the SVG.
  Folder layout and status: `../assets/logo/README.md`.
- **Export rules**:
  - *Screen*: SVG wherever possible; otherwise transparent PNG ≥ 2× the display size
    (Retina).
  - *Print*: vector PDF/EPS only (`print/` folder), or PNG ≥ 300 dpi at print size.
  - *Forbidden*: JPEG for the logo (lossy compression, no transparency).
- **Clear space**: free margin ≥ the height of the "Z" on all sides.
- **Minimum size** of the emblem: `24 px` (favicon `48×48` = provided version).
- **Allowed backgrounds**: off-white (`--z-bg`), `--z-surface`, or `--z-slate-900` (then use
  the light/monochrome version). Avoid the color logo on saturated honey or green.
- **Prohibited**: distort, recolor the gradient, add a drop shadow, rotate, or separate the
  bee from the wordmark inconsistently.

---

## 7. Components (principles)

Intent specs — the front-end translates them into its own technology.

- **Buttons**
  - *Primary*: `--z-honey-500` fill, `--z-slate-900` text, `md` radius, hover `--z-honey-600`.
  - *Secondary*: `--z-green-500` fill, white text.
  - *Ghost*: `--z-line` border, `--z-slate-800` text.
- **Cards / hive tiles**: `--z-bg` fill, `--z-line` border, shadow `1`, `lg` radius.
  Colored status bar on the left (`success`/`warning`/`danger`) for a hive's state.
- **Status badges**: pill, 300-tint semantic background, 600-tint text.
- **Input fields**: `--z-line` border, focus ring `--z-honey-500` (2 px).
- **Data tables**: numbers in `tabular-nums`, zebra rows `--z-surface`, header `--z-slate-800`.
- **Charts / telemetry**: temperature → honey, humidity → info, weight → green, critical
  thresholds → danger. One color = one metric, stable across the whole app.

### 7.1 Interactive component states

Every interactive component exposes **six consistent states**. Keyboard focus must stay
visible (`:focus-visible`), never removed.

| State | Primary button | Input field |
|---|---|---|
| Rest | `--z-honey-500` fill, `--z-slate-900` text | `--z-line` border, `--z-bg` fill |
| Hover | `--z-honey-600` fill | `--z-ink-muted` border |
| Active (pressed) | `--z-honey-600` fill + `translateY(1px)` | — |
| Focus (keyboard) | `--z-honey-500` 2 px ring + `rgba(217,165,33,.25)` halo | `--z-honey-500` 2 px ring |
| Disabled | `.45` opacity, `cursor: not-allowed` | `--z-surface` fill, `--z-ink-muted` text |
| Loading | spinner, frozen width, hidden label | — |
| Error | — | `--z-danger` border + `--z-danger` message below |

> Secondary/ghost buttons: same states, swapping `--z-green-500/600` and
> `--z-line`/`--z-slate-800` respectively. State transitions reuse `--z-dur-fast`
> (see [`motion/transitions.md`](./motion/transitions.md)).

### 7.2 Data palette (chart series)

**Semantic** colors (§ 2.3) encode a *state*; they never distinguish **series** (several
hives/apiaries on one chart). For that, a dedicated **categorical** palette, assigned
**in order, never cycled** — the 8th series becomes "Other", a small multiple, or a second
encoding (line style / texture).

Validated palette (colorblind-safe, min adjacent ΔE 35.9) — Adobe *dataviz* method,
script-verified:

| # | Role | Light mode | Dark mode |
|---|---|---|---|
| 1 | Honey (primary series) | `#D9A521` | `#B8860B` |
| 2 | Blue | `#2A78D6` | `#3987E5` |
| 3 | Hive green | `#2E9E3F` | `#008300` |
| 4 | Pink | `#E87BA4` | `#D55181` |
| 5 | Orange | `#EB6834` | `#D95926` |
| 6 | Violet | `#4A3AA7` | `#9085E9` |
| 7 | Aqua | `#1BAF7A` | `#199E70` |

**Rules**:
- **A legend is always present** for ≥ 2 series, and ≤ 4 series are also directly labeled:
  identity is never carried by color alone.
- **Color follows the entity** (the hive), not its rank: filtering a series doesn't repaint
  the survivors.
- Honey/pink/aqua have < 3:1 contrast on the light surface → always paired with a visible
  label or a table view (never a value painted on a light surface without text).
- **Magnitude** (a single metric, low → high): use a **monochrome** honey ramp light → dark
  (`--z-honey-300` → `--z-honey-600`), not the categorical palette.
- **Never a dual Y-axis**: two scales = two charts or a common indexed base.

---

## 8. Dark mode

- Background: `--z-slate-900` (`#1E3A34`); surfaces: `#25423B`.
- Text: `#EAF1EE` (primary), `#A9BDB6` (secondary).
- Honey `--z-honey-500` stays the accent (excellent contrast on deep green).
- Borders: `rgba(255,255,255,.10)`.
- Avoid green `500` as thin text on dark backgrounds → prefer `--z-green-300`.

---

## 9. Voice & tone (microcopy)

- English (and French/Arabic locales), clear, professional and neutral.
- Action-oriented and reassuring: "Hive 3: weight stable" rather than "OK".
- No needless jargon; the beekeeper comes before the engineer.

---

## 10. Token summary (copy-paste)

```css
:root {
  /* Brand */
  --z-honey-300:#EBC55B; --z-honey-500:#D9A521; --z-honey-600:#B8860B;
  --z-green-300:#6FC46B; --z-green-500:#2E9E3F; --z-green-600:#1F7A2E;
  --z-slate-800:#2C4A42; --z-slate-900:#1E3A34;
  /* Neutrals */
  --z-ink:#1B2320; --z-ink-muted:#5B6B65; --z-line:#DCE4E0;
  --z-surface:#F5F7F5; --z-bg:#FCFDFC;
  /* Semantic */
  --z-success:#2E9E3F; --z-warning:#D9A521; --z-danger:#C0392B; --z-info:#2C7A7B;
  /* Data — categorical series palette (light mode) */
  --z-cat-1:#D9A521; --z-cat-2:#2A78D6; --z-cat-3:#2E9E3F; --z-cat-4:#E87BA4;
  --z-cat-5:#EB6834; --z-cat-6:#4A3AA7; --z-cat-7:#1BAF7A;
  /* Radii */
  --z-radius-sm:6px; --z-radius-md:12px; --z-radius-lg:20px; --z-radius-pill:999px;
  /* Shadows */
  --z-shadow-1:0 1px 2px rgba(28,74,66,.08);
  --z-shadow-2:0 4px 12px rgba(28,74,66,.10);
  --z-shadow-3:0 12px 32px rgba(28,74,66,.14);
  /* Signature */
  --z-gradient:linear-gradient(120deg,#D9A521 0%,#2E9E3F 100%);
}
```

---

*Reference: `DESIGN.md` approach — <https://getdesign.md>. Brand assets: `../assets/logo/`.*
