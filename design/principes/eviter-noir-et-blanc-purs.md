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

---

## 🎯 À retenir

- **Fonds clairs** : partir d'un blanc cassé (`#FAF9F6` / `#F7F6F3`) plutôt que `#FFFFFF`.
- **Textes / fonds sombres** : viser `#121212`–`#222222` plutôt que `#000000`.
- Vérifier malgré tout le **contraste AA/AAA** (voir `DESIGN.md`, accessibilité).
- À combiner avec la palette de marque (miel `#D9A521`, vert `#2E9E3F`).
