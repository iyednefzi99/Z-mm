# 🏃 SPRINT-15 : Ressources de langue externalisées

**Thème :** Solder la dette d'internationalisation laissée par le SPRINT-13
**Objectif :** Que les traductions soient des ressources qu'un traducteur peut ouvrir, et qu'une session ne télécharge que la langue qu'elle affiche
**Période :** 2027-03-02 → 2027-03-15 (14 jours)
**Story Points :** 8 / Capacity : 40

> **Note de planification.** Sprint volontairement court : il solde un point ouvert
> plutôt qu'il n'ouvre un chantier. La capacité restante va au SPRINT-16
> (intégration matérielle des capteurs), planifié séparément.

---

## 📋 User Stories

| ID | Story | Points | Statut |
|:---|:---|:---:|:---|
| US-072 | Traductions en ressources de locale, chargées à la demande | 8 | 🟢 Livré |

---

## 🔎 Ce qui a été fait, et pourquoi

`i18n/console.ts` portait **944 lignes** : les trois langues, en dur, dans un
objet TypeScript. Deux conséquences, l'une pour l'utilisateur, l'autre pour
l'équipe.

**Pour l'utilisateur** : les trois langues partaient dans le paquet initial, alors
qu'une session n'en affiche jamais qu'une. Le français, l'anglais et l'arabe
étaient téléchargés, analysés et exécutés à chaque chargement.

**Pour l'équipe** : un objet TypeScript de mille lignes ne se confie pas à un
traducteur. Un fichier JSON, si — il s'ouvre dans n'importe quel outil de
traduction, se compare et se relit.

### Ce qui a été fait

Les 315 clés ont été extraites **depuis le module lui-même**, par un script
jetable exécuté dans l'environnement de test, plutôt que réécrites à la main :
une conversion manuelle de mille lignes d'apostrophes typographiques et de texte
arabe est une source de fautes silencieuses.

`locales/fr.json` est importé **avec l'application** ; `en.json` et `ar.json` sont
des morceaux à part, chargés au changement de langue.

### La parité, qui était le vrai enjeu

L'ancien `Record<Langue, typeof fr>` faisait échouer la compilation dès qu'une clé
manquait dans une traduction. Perdre cette garantie en échange de quelques
kilo-octets aurait été un mauvais marché : une clé oubliée serait devenue un
libellé manquant découvert en production, dans une langue que personne dans
l'équipe ne relit.

Elle est **conservée**, reportée sur le type de retour des chargeurs :

```ts
export const CHARGEURS: Record<Exclude<Langue, 'fr'>, () => Promise<{ default: Traductions }>>
```

Vérifié en retirant réellement une clé d'`en.json` : `tsc` échoue, en nommant la
clé et la langue. `langue.test.tsx` complète en attrapant ce que le typage laisse
passer — les clés **en trop** et les valeurs vides.

### Comportement au changement de langue

La direction du document (RTL en arabe) bascule **immédiatement** : c'est la mise
en page, elle ne dépend d'aucun chargement. Les libellés, eux, restent sur la
langue précédente le temps que la ressource arrive, plutôt que de se vider — un
écran blanc serait pire que quelques dizaines de millisecondes de décalage. Une
garde annule l'application d'une ressource arrivée en retard, si l'utilisateur a
entre-temps changé de langue de nouveau.

---

## ✅ Definition of Done

- [x] `npm run typecheck && npm run lint && npm test && npm run build` verts
- [x] 120 tests Vitest, 0 erreur ESLint
- [x] Parité **prouvée** : suppression d'une clé d'`en.json` → échec de `tsc`
- [x] Paquet initial : 243 → **228 ko** (77,8 → **72,9 ko** compressés) ;
      `en` (8,8 ko) et `ar` (11,6 ko) sortis en morceaux distincts

## 🔁 Rétrospective

**Ce qui a marché.** Extraire les ressources par un script plutôt qu'à la main, et
prouver la garantie de parité en la cassant volontairement — plutôt que de
supposer qu'elle tenait encore.

**Ce qu'on retient.** Un refactor qui échange une garantie de compilation contre
un gain de poids est un mauvais refactor. Le travail utile a consisté à trouver
où reporter la garantie, pas à déplacer les chaînes.

**Point de détail assumé.** Les deux ressources traduites restent **précachées**
par le service worker : elles sont donc téléchargées en arrière-plan après le
premier rendu. Le gain porte sur le temps d'affichage initial, pas sur le volume
total — et c'est voulu, une PWA de terrain devant pouvoir changer de langue hors
ligne.

**Ce qui reste ouvert.** Agrégats des tableaux de bord calculés en Java ; portée
d'autorisation par affectation d'agent (US-057) ; client d'API front toujours écrit
à la main alors que le contrat OpenAPI existe.
