# 🎨 Éviter le noir et le blanc purs

> Note de principes — préférer des neutres légèrement teintés plutôt que le noir/blanc absolus.
> Fait partie de la charte de design — voir [`../DESIGN.md`](../DESIGN.md) (§ couleurs).

Le blanc pur (`#FFFFFF`) et le noir pur (`#000000`) créent un contraste trop dur, fatiguent
l'œil et donnent un rendu « brut ». On utilise des neutres proches, plus doux.

---

## ⬜ À la place du blanc `#FFFFFF`

| Aperçu | Hex |
|---|---|
| ▫️ | `#FAF9F6` |
| ▫️ | `#F7F6F3` |
| ▫️ | `#F5F5F5` |
| ▫️ | `#F0F2F5` |
| ▫️ | `#EDEDED` |

## ⬛ À la place du noir `#000000`

| Aperçu | Hex |
|---|---|
| ◾ | `#2C2C2C` |
| ◾ | `#222222` |
| ◾ | `#121212` |
| ◾ | `#0C0C0C` |

> Les hex ci-dessus sont des exemples **génériques** (neutres chauds). Les neutres de Zümm,
> eux, sont **teintés vert** pour rester dans l'identité (voir tokens ci-dessous).

---

## 🐝 Les neutres de Zümm (source de vérité)

Ne pas piocher un blanc/noir au hasard : le système Zümm fixe déjà ses neutres teintés vert.

| Token | Hex | Rôle | Remplace |
|---|---|---|---|
| `--z-bg` | `#FCFDFC` | Fond de page clair | ~~`#FFFFFF`~~ |
| `--z-surface` | `#F5F7F5` | Fond de section, cartes | — |
| `--z-ink` | `#1B2320` | Texte principal | ~~`#000000`~~ |
| `--z-slate-900` | `#1E3A34` | Fond de page sombre | ~~`#000000`~~ |

---

## 🎯 À retenir

- **Fonds clairs** : utiliser `--z-bg` (`#FCFDFC`) — jamais `#FFFFFF` pur.
- **Textes / fonds sombres** : utiliser `--z-ink` (`#1B2320`) / `--z-slate-900` (`#1E3A34`),
  jamais `#000000`.
- Vérifier malgré tout le **contraste AA/AAA** (voir `DESIGN.md` § 2.4, ratios chiffrés).
- Les neutres restent **teintés vers le vert de marque**, pas des gris chauds génériques.
