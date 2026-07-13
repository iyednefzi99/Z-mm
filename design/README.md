# 🎨 Zümm — Design System

> Système d'Information de Gestion Apicole · Beekeeping Management Information System · نظام معلومات إدارة تربية النحل

Ce dossier contient la **charte de design** de Zümm : la source de vérité visuelle (couleurs,
typographie, espacements, composants, usage du logo). **Ce n'est pas le front-end** — c'est
la référence que tout support (web, mobile, print, slides) doit respecter.

---

## 📄 Documents / Documents / الوثائق

| Langue | Fichier | Sens de lecture |
|---|---|---|
| 🇫🇷 Français *(par défaut)* | [`DESIGN.md`](./DESIGN.md) | LTR |
| 🇬🇧 English | [`DESIGN.en.md`](./DESIGN.en.md) | LTR |
| 🇸🇦 العربية | [`DESIGN.ar.md`](./DESIGN.ar.md) | RTL |

> Les trois versions sont **équivalentes** : même palette, mêmes tokens, mêmes règles.
> En cas de doute, la version **française** fait foi.

---

## 🧭 Méthode / Method / المنهج

La charte combine deux références :

- **[getdesign.md](https://getdesign.md)** — le *format* : un fichier `DESIGN.md` unique,
  lisible par un humain comme par un agent de code, qui documente couleurs, type, espacement,
  composants et le raisonnement derrière.
- **[Adobe Spectrum](https://spectrum.adobe.com)** — la *méthode* : jetons en couches
  (global → alias/sémantique → composant), dimensionnement « t-shirt » (`sm`/`md`/`lg`),
  couleurs sémantiques et **accessibilité d'abord**.

➡️ Le **format** vient de getdesign.md, la **méthode** d'Adobe Spectrum, et l'**identité
visuelle** est propre à Zümm (dégradé miel → vert émeraude, emblème abeille/hexagone).

---

## 🎯 Aperçu de la marque

| Rôle | Couleur | Hex |
|---|---|---|
| Primaire — Miel | 🟡 | `#D9A521` |
| Secondaire — Vert ruche | 🟢 | `#2E9E3F` |
| Neutre — Vert ardoise | 🌲 | `#2C4A42` |

**Dégradé signature** : `linear-gradient(120deg, #D9A521 0%, #2E9E3F 100%)`

Assets de marque : [`../assets/logo/`](../assets/logo/) — logo, emblème, version monochrome,
planche de marque.

---

## 🚀 Utilisation

1. Lis le `DESIGN.md` dans ta langue avant de créer une interface, une slide ou un document.
2. Copie le bloc de **tokens CSS** (section 10) comme base de tes variables.
3. Respecte les règles d'usage du logo (section 6) et la sémantique des couleurs (section 2.3).
4. En RTL (arabe), inverse la mise en page et aligne chiffres/tableaux à droite.
