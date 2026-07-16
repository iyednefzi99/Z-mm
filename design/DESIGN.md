# DESIGN.md — Zümm

> Système d'Information de Gestion Apicole
>
> Ce fichier est la **référence de design** du produit Zümm : couleurs, typographie,
> espacements, composants et le raisonnement derrière chaque choix. Il suit l'esprit
> de la spécification `DESIGN.md` (voir <https://getdesign.md>) : un document unique,
> lisible par un humain comme par un agent de code, qui garantit la cohérence visuelle
> de toutes les interfaces et supports Zümm.
>
> **Ce n'est pas du code front-end.** C'est la source de vérité que le front-end (et
> tout autre support : web, mobile, print, slides) doit respecter.
>
> **Méthode** : la structure s'appuie sur l'approche d'**Adobe Spectrum**
> (<https://spectrum.adobe.com>) — jetons en couches (global → alias/sémantique → composant),
> dimensionnement « t-shirt » (sm/md/lg), couleurs sémantiques et accessibilité d'abord —
> exprimée dans le format `DESIGN.md`. L'identité visuelle, elle, est **propre à Zümm**.

---

## 1. Fondations de marque

Zümm est un **SIG apicole** : il relie le monde vivant de la ruche au monde numérique
de la donnée. Toute l'identité découle de cette dualité.

- **Nature / apiculture** → l'abeille, l'hexagone du rayon de miel, l'ambre du miel,
  le vert de la végétation.
- **Technologie / information** → le tracé façon circuit imprimé, les nœuds connectés,
  la structure hexagonale répétable (comme un maillage de capteurs).

**Personnalité** : précis, vivant, rassurant, sobre. On évite le « techno froid » pur
et le « miel kitsch » ; on vise l'équilibre organique + ingénierie.

**Mots-clés directeurs** : hexagonal, connecté, naturel, clair, fiable.

---

## 2. Palette de couleurs

La palette naît directement du logo : un dégradé **or-miel → vert émeraude**, ancré par
un **vert ardoise** profond. Les valeurs ci-dessous sont les jetons (tokens) officiels.

### 2.1 Couleurs de marque

| Token | Hex | Rôle |
|---|---|---|
| `--z-honey-500` | `#D9A521` | **Primaire — Miel.** Accent principal, CTA, éléments actifs. |
| `--z-honey-600` | `#B8860B` | Miel foncé — survol/appui du primaire, texte sur fond clair. |
| `--z-honey-300` | `#EBC55B` | Miel clair — surbrillances, badges, arrière-plans doux. |
| `--z-green-500` | `#2E9E3F` | **Secondaire — Vert ruche.** Succès, croissance, données « saines ». |
| `--z-green-600` | `#1F7A2E` | Vert foncé — survol/appui du secondaire. |
| `--z-green-300` | `#6FC46B` | Vert clair — indicateurs positifs discrets. |
| `--z-slate-800` | `#2C4A42` | **Neutre de marque — Vert ardoise.** Wordmark, titres, texte fort. |
| `--z-slate-900` | `#1E3A34` | Vert profond — fonds sombres, en-têtes, mode sombre. |

> **Dégradé signature** : `linear-gradient(120deg, #D9A521 0%, #2E9E3F 100%)`.
> À réserver aux surfaces d'emphase (héro, page de garde, emblème). Ne jamais l'utiliser
> derrière du texte courant.

### 2.2 Neutres

| Token | Hex | Rôle |
|---|---|---|
| `--z-ink` | `#1B2320` | Texte principal (préférer à `#000`). |
| `--z-ink-muted` | `#5B6B65` | Texte secondaire, légendes, métadonnées. |
| `--z-line` | `#DCE4E0` | Bordures, séparateurs, contours de champs. |
| `--z-surface` | `#F5F7F5` | Fond de section, cartes en retrait. |
| `--z-bg` | `#FCFDFC` | Fond de page (mode clair) — blanc cassé teinté vert, **jamais `#FFFFFF` pur** (voir [`principes/eviter-noir-et-blanc-purs.md`](./principes/eviter-noir-et-blanc-purs.md)). |

### 2.3 Couleurs sémantiques (états)

| Token | Hex | Usage |
|---|---|---|
| `--z-success` | `#2E9E3F` | Ruche saine, tâche terminée (réutilise le vert de marque). |
| `--z-warning` | `#D9A521` | Alerte modérée, seuil approché (réutilise le miel). |
| `--z-danger` | `#C0392B` | Essaimage, capteur hors-ligne, erreur critique. |
| `--z-info` | `#2C7A7B` | Information neutre, télémétrie brute. |

> **Règle** : le miel et le vert portent déjà du sens (alerte / succès). Ne pas les
> employer de façon décorative dans un tableau de bord où ils codent un état.

### 2.4 Accessibilité contraste

Ratios WCAG mesurés (fond `--z-bg` en mode clair, sauf mention). Seuils : **AA** = 4.5:1
(texte normal) / 3:1 (gros texte ≥ 24 px ou 19 px gras) ; **AAA** = 7:1.

| Paire (premier plan sur fond) | Ratio | Verdict |
|---|---|---|
| `--z-ink` sur `--z-bg` | 15.8:1 | ✅ AAA |
| `--z-ink-muted` sur `--z-bg` | 5.5:1 | ✅ AA |
| `--z-green-600` sur `--z-bg` | 5.4:1 | ✅ AA |
| `--z-honey-600` sur `--z-bg` | 3.3:1 | ⚠️ AA gros texte seulement |
| `--z-green-500` sur `--z-bg` | 3.5:1 | ⚠️ AA gros texte / icônes |
| `--z-honey-500` sur `--z-bg` | 2.2:1 | ❌ aplat uniquement, jamais en texte |
| `--z-slate-900` sur `--z-honey-500` (texte de CTA) | 5.5:1 | ✅ AA |
| Blanc sur `--z-green-500` (bouton secondaire) | 3.5:1 | ⚠️ AA gros texte |
| Blanc sur `--z-danger` | 5.4:1 | ✅ AA |
| Blanc sur `--z-info` | 5.0:1 | ✅ AA |
| `#EAF1EE` sur `--z-slate-900` (mode sombre) | 10.7:1 | ✅ AAA |
| `--z-honey-500` sur `--z-slate-900` (accent sombre) | 5.5:1 | ✅ AA |
| `--z-green-300` sur `--z-slate-900` (mode sombre) | 5.7:1 | ✅ AA |

**À retenir** :
- **Le miel `#D9A521` ne passe pas AA en texte** : l'utiliser en aplat pour des surfaces/CTA
  avec texte foncé (`--z-slate-900`) par-dessus, pas comme couleur de texte fin.
- Vert `#2E9E3F` sur `--z-bg` : OK pour icônes et gros texte ; pour texte fin, préférer
  `--z-green-600`. Bouton secondaire (blanc sur vert) : réserver aux libellés de taille ≥ 16 px gras.
- En mode sombre, éviter le vert `500` en texte fin → `--z-green-300` (voir § 8).

---

## 3. Typographie

Système sobre et lisible ; le logo utilise une grotesque humaniste (formes rondes,
`ü` avec tréma). On la prolonge à l'écran.

- **Familles**
  - *Titres & UI* : **Inter** (ou fallback système `-apple-system, Segoe UI, Roboto, sans-serif`).
    Alternative fidèle au wordmark : *Poppins* / *Nunito Sans* (rondeurs).
  - *Chiffres & données* : Inter en variante **tabular-nums** (alignement des colonnes).
  - *Monospace* (logs, identifiants capteurs) : `JetBrains Mono`, fallback `ui-monospace`.

- **Échelle typographique** (base 16 px, ratio ~1.25)

  | Rôle | Taille | Graisse | Interligne |
  |---|---|---|---|
  | Display | 40 px | 700 | 1.1 |
  | H1 | 32 px | 700 | 1.15 |
  | H2 | 25 px | 600 | 1.2 |
  | H3 | 20 px | 600 | 1.3 |
  | Corps | 16 px | 400 | 1.5 |
  | Petit | 14 px | 400 | 1.45 |
  | Légende | 12 px | 500 | 1.4 |

- **Règles** : titres en `--z-slate-800`, corps en `--z-ink`, légendes en `--z-ink-muted`.
  Largeur de ligne max ≈ 70 caractères. Toujours écrire **« Zümm »** avec le tréma.

---

## 4. Espacement & mise en page

Échelle d'espacement en base **4 px** (multiples) :

`4 · 8 · 12 · 16 · 24 · 32 · 48 · 64`

- Padding intérieur des cartes : `16–24 px`.
- Gouttière de grille : `24 px`.
- Rythme vertical entre sections : `48–64 px`.
- Largeur de contenu max : `1200 px` (dashboards) / `720 px` (lecture).

**Rayons (border-radius)** — la marque est hexagonale mais l'UI reste douce :
`--z-radius-sm: 6px` · `--z-radius-md: 12px` · `--z-radius-lg: 20px` · `--z-radius-pill: 999px`.

**Élévation (ombres)** — discrètes, teintées vert ardoise plutôt que noir pur :
- `--z-shadow-1: 0 1px 2px rgba(28,74,66,.08)`
- `--z-shadow-2: 0 4px 12px rgba(28,74,66,.10)`
- `--z-shadow-3: 0 12px 32px rgba(28,74,66,.14)`

---

## 5. Iconographie & motifs

- **Style d'icônes** : linéaire, trait `1.75 px`, coins arrondis — cohérent avec le
  tracé « circuit » du logo. Bibliothèque conseillée : *Lucide* / *Phosphor* (thin/regular).
- **Motif signature** : la **grille hexagonale** (nid d'abeille). À utiliser en filigrane
  très léger (`opacity ≤ .06`) sur les fonds de section, jamais derrière du texte dense.
- **Nœuds & connexions** : petits cercles pleins reliés par des lignes coudées à 90°/45°
  pour évoquer le réseau de capteurs — réservé aux illustrations, pas à l'UI fonctionnelle.

---

## 6. Logo — règles d'usage

- Fichiers de référence dans `../assets/logo/` :
  `zumm-logo.png` (vertical), `zumm-icon.png` (emblème), `zumm-icon-mono.png` (monochrome),
  `zumm-brandsheet.png` (planche de marque).
- **Source de vérité vectorielle** : les masters SVG dans `../assets/logo/svg/`
  (`zumm-logo.svg`, `zumm-icon.svg`, `zumm-icon-mono.svg`, `zumm-logo-light.svg`).
  Les PNG sont des **exports dérivés** — ne jamais retoucher un raster, toujours
  ré-exporter depuis le SVG. Arborescence et statut : `../assets/logo/README.md`.
- **Règles d'export** :
  - *Écran* : SVG partout où c'est possible ; sinon PNG transparent ≥ 2× la taille
    d'affichage (Retina).
  - *Print* : PDF/EPS vectoriel uniquement (dossier `print/`), ou PNG ≥ 300 dpi à la
    taille d'impression.
  - *Interdit* : JPEG pour le logo (compression destructive, pas de transparence).
- **Zone de protection** : marge libre ≥ hauteur du « Z » tout autour.
- **Taille mini** de l'emblème : `24 px` (favicon `48×48` = version fournie).
- **Fonds autorisés** : blanc cassé (`--z-bg`), `--z-surface`, ou `--z-slate-900` (utiliser
  alors la version claire/monochrome). Éviter le logo couleur sur fond miel ou vert saturé.
- **Interdits** : déformer, recolorer le dégradé, ajouter une ombre portée, pivoter,
  séparer l'abeille du wordmark de façon incohérente.

---

## 7. Composants (principes)

Spécifications d'intention — le front-end les traduit dans sa techno.

- **Boutons**
  - *Primaire* : fond `--z-honey-500`, texte `--z-slate-900`, rayon `md`, survol `--z-honey-600`.
  - *Secondaire* : fond `--z-green-500`, texte blanc.
  - *Fantôme* : bordure `--z-line`, texte `--z-slate-800`.
- **Cartes / tuiles ruche** : fond `--z-bg`, bordure `--z-line`, ombre `1`, rayon `lg`.
  Barre d'état colorée à gauche (`success`/`warning`/`danger`) pour l'état d'une ruche.
- **Badges d'état** : pilule, fond teinte 300 de la sémantique, texte teinte 600.
- **Champs de saisie** : bordure `--z-line`, focus anneau `--z-honey-500` (2 px).
- **Tableaux de données** : chiffres en `tabular-nums`, lignes alternées `--z-surface`,
  en-tête `--z-slate-800`.
- **Graphiques / télémétrie** : série température → miel, humidité → info, poids → vert,
  seuils critiques → danger. Une couleur = une métrique, de façon stable dans toute l'app.

### 7.1 États des composants interactifs

Tout composant interactif expose **six états** cohérents. Le focus doit rester visible
au clavier (`:focus-visible`), jamais supprimé.

| État | Bouton primaire | Champ de saisie |
|---|---|---|
| Repos | fond `--z-honey-500`, texte `--z-slate-900` | bordure `--z-line`, fond `--z-bg` |
| Survol | fond `--z-honey-600` | bordure `--z-ink-muted` |
| Actif (pressé) | fond `--z-honey-600` + `translateY(1px)` | — |
| Focus (clavier) | anneau `--z-honey-500` 2 px + halo `rgba(217,165,33,.25)` | anneau `--z-honey-500` 2 px |
| Désactivé | opacité `.45`, `cursor: not-allowed` | fond `--z-surface`, texte `--z-ink-muted` |
| Chargement | spinner, largeur figée, libellé masqué | — |
| Erreur | — | bordure `--z-danger` + message `--z-danger` sous le champ |

> Boutons secondaire/fantôme : mêmes états, en déclinant respectivement `--z-green-500/600`
> et `--z-line`/`--z-slate-800`. La transition d'état réutilise `--z-dur-fast`
> (voir [`motion/transitions.md`](./motion/transitions.md)).

### 7.2 Palette de données (séries de graphiques)

Les couleurs **sémantiques** (§ 2.3) codent un *état* ; elles ne servent jamais à distinguer
des **séries** (plusieurs ruches/ruchers sur un même graphe). Pour cela, une palette
**catégorielle** dédiée, à assigner **dans l'ordre, jamais en boucle** — la 8ᵉ série
devient « Autre », un petit multiple, ou un second encodage (trait/texture).

Palette validée (colorblind-safe, ΔE adjacent min 35.9) — méthode *dataviz* d'Adobe/skill,
vérifiée par script :

| # | Rôle | Mode clair | Mode sombre |
|---|---|---|---|
| 1 | Miel (série principale) | `#D9A521` | `#B8860B` |
| 2 | Bleu | `#2A78D6` | `#3987E5` |
| 3 | Vert ruche | `#2E9E3F` | `#008300` |
| 4 | Rose | `#E87BA4` | `#D55181` |
| 5 | Orange | `#EB6834` | `#D95926` |
| 6 | Violet | `#4A3AA7` | `#9085E9` |
| 7 | Aqua | `#1BAF7A` | `#199E70` |

**Règles** :
- **Une légende est toujours présente** pour ≥ 2 séries, et ≤ 4 séries sont aussi
  étiquetées en direct : l'identité n'est jamais portée par la couleur seule.
- **La couleur suit l'entité** (la ruche), pas son rang : filtrer une série ne repeint
  pas les survivantes.
- Miel/rose/aqua ont un contraste < 3:1 sur fond clair → toujours accompagnés d'un libellé
  visible ou d'une vue tableau (jamais de valeur peinte sur fond clair sans texte).
- **Magnitude** (une seule métrique, faible → fort) : rampe **monochrome** miel clair → foncé
  (`--z-honey-300` → `--z-honey-600`), pas la palette catégorielle.
- **Jamais de double axe Y** : deux échelles = deux graphes ou une base indexée commune.

---

## 8. Mode sombre

- Fond : `--z-slate-900` (`#1E3A34`) ; surfaces : `#25423B`.
- Texte : `#EAF1EE` (principal), `#A9BDB6` (secondaire).
- Le miel `--z-honey-500` reste l'accent (excellent contraste sur fond vert profond).
- Bordures : `rgba(255,255,255,.10)`.
- Éviter le vert `500` en texte fin sur fond sombre → préférer `--z-green-300`.

---

## 9. Voix & ton (micro-copie)

- Français, clair, tutoiement évité en contexte pro (vouvoiement neutre).
- Orienté action et rassurant : « Ruche 3 : poids stable » plutôt que « OK ».
- Jamais de jargon inutile ; l'apiculteur·rice prime sur l'ingénieur·e.

---

## 10. Résumé des tokens (copier-coller)

```css
:root {
  /* Marque */
  --z-honey-300:#EBC55B; --z-honey-500:#D9A521; --z-honey-600:#B8860B;
  --z-green-300:#6FC46B; --z-green-500:#2E9E3F; --z-green-600:#1F7A2E;
  --z-slate-800:#2C4A42; --z-slate-900:#1E3A34;
  /* Neutres */
  --z-ink:#1B2320; --z-ink-muted:#5B6B65; --z-line:#DCE4E0;
  --z-surface:#F5F7F5; --z-bg:#FCFDFC;
  /* Sémantique */
  --z-success:#2E9E3F; --z-warning:#D9A521; --z-danger:#C0392B; --z-info:#2C7A7B;
  /* Données — palette catégorielle de séries (mode clair) */
  --z-cat-1:#D9A521; --z-cat-2:#2A78D6; --z-cat-3:#2E9E3F; --z-cat-4:#E87BA4;
  --z-cat-5:#EB6834; --z-cat-6:#4A3AA7; --z-cat-7:#1BAF7A;
  /* Rayons */
  --z-radius-sm:6px; --z-radius-md:12px; --z-radius-lg:20px; --z-radius-pill:999px;
  /* Ombres */
  --z-shadow-1:0 1px 2px rgba(28,74,66,.08);
  --z-shadow-2:0 4px 12px rgba(28,74,66,.10);
  --z-shadow-3:0 12px 32px rgba(28,74,66,.14);
  /* Signature */
  --z-gradient:linear-gradient(120deg,#D9A521 0%,#2E9E3F 100%);
}
```

---

*Référence : approche `DESIGN.md` — <https://getdesign.md>. Assets de marque : `../assets/logo/`.*
