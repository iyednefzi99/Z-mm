# 🏃 SPRINT-15 : Ressources de langue externalisées

**Thème :** Solder la dette d'internationalisation laissée par le SPRINT-13
**Objectif :** Que les traductions soient des ressources qu'un traducteur peut ouvrir, et qu'une session ne télécharge que la langue qu'elle affiche
**Période :** 2027-03-02 → 2027-03-15 (14 jours)
**Story Points :** 8 / Capacity : 40

> **Note de planification.** Sprint volontairement court : il solde un point ouvert
> plutôt qu'il n'ouvre un chantier. La capacité restante va au SPRINT-16
> (intégration matérielle des capteurs), planifié séparément.

---

## 📅 Cérémonies Scrum

| Cérémonie | Date/Heure | Durée |
|:---|:---|:---|
| Sprint Planning | 2027-03-02 09:00-11:00 | 2h |
| Daily Scrum | Tous les jours 09:15 (15 min) | 15 min |
| Sprint Review | 2027-03-15 14:00-15:00 | 1h |
| Sprint Retrospective | 2027-03-15 15:00-16:00 | 1h |

> Cérémonies raccourcies à la mesure du sprint : 8 points pour une seule US de
> dette. Le planning du SPRINT-16, lui, garde ses 4 heures.

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

## 🎯 Sprint Review - Démonstration

**Date :** 2027-03-15 14:00-15:00

Revue courte, à la mesure du sprint. Une US de dette ne se démontre pas par un
écran nouveau : elle se démontre en **cassant la garantie** qu'elle prétend tenir.

1. **Le fichier qu'un traducteur peut ouvrir.** `i18n/console.ts` (944 lignes de
   TypeScript, trois langues mêlées) est remplacé par trois `locales/*.json` de
   315 clés. Ouverture d'`ar.json` dans un éditeur quelconque en séance : il se lit,
   se compare, se confie.
2. **La garantie de parité, cassée devant témoins.** Une clé est **réellement
   supprimée** d'`en.json` pendant la revue, puis `npm run typecheck` est lancé :
   `tsc` échoue en **nommant la clé et la langue**. C'était le point dur du sprint —
   l'ancien `Record<Langue, typeof fr>` donnait cette garantie gratuitement, et la
   perdre pour quelques kilo-octets aurait été un mauvais marché. Elle est reportée
   sur le type de retour des chargeurs.
3. **Ce que le typage ne voit pas.** Ajout d'une clé **en trop** dans `ar.json`,
   puis d'une valeur vide : `tsc` passe, `langue.test.tsx` échoue. Les deux filets
   sont complémentaires, la démonstration le montre plutôt que de l'affirmer.
4. **Le poids.** Onglet réseau à l'écran : paquet initial 243 → **228 ko**
   (77,8 → 72,9 ko compressés), `en` (8,8 ko) et `ar` (11,6 ko) en morceaux
   distincts, chargés seulement au changement de langue.
5. **Le comportement à la bascule.** FR → AR sur un réseau volontairement ralenti :
   la **direction du document bascule immédiatement** — c'est de la mise en page,
   elle ne dépend d'aucun chargement — pendant que les libellés restent en français
   quelques dizaines de millisecondes. Deux bascules rapides FR → AR → EN : la garde
   annule l'application de la ressource arrivée en retard.

**Réserve soulevée en séance, et assumée.** Les deux ressources traduites restent
**précachées** par le service worker : le gain porte donc sur le temps d'affichage
initial, pas sur le volume total téléchargé. C'est voulu — une PWA de terrain doit
pouvoir changer de langue hors ligne.

---

## ⚠️ Risques Identifiés

| Risque | Impact | Mitigation |
|:---|:---|:---|
| Perdre la garantie de parité en externalisant les chaînes | Un libellé manquant découvert **en production**, dans une langue que personne dans l'équipe ne relit | Garantie reportée sur le type de retour des chargeurs ; prouvée en la cassant |
| Réécrire 315 clés à la main | Fautes silencieuses sur les apostrophes typographiques et le texte arabe | Extraction par script exécuté **depuis le module lui-même**, jamais de recopie |
| Écran vide pendant le chargement de la ressource | Régression d'ergonomie plus grave que le gain de poids | Les libellés restent sur la langue précédente le temps du chargement |
| Deux changements de langue rapprochés | La ressource lente écrase la langue finalement choisie | Garde d'annulation sur la ressource arrivée en retard, testée |
| Sprint court laissant croire à une capacité disponible | Sur-engagement du SPRINT-16 | Capacité restante explicitement fléchée vers le SPRINT-16, noté au planning |

---

## 📊 Burndown Chart

| Jour | Reste à faire (idéal) | Reste à faire (réel) | Notes |
|:---|:---:|:---:|:---|
| Jour 1 | 8 | 8 | Cadrage : le vrai enjeu est la parité, pas le déplacement des chaînes |
| Jour 3 | 6 | 6 | Script d'extraction des 315 clés depuis le module, 3 JSON produits |
| Jour 6 | 4 | 4 | Chargeurs paresseux, `fr` importé avec l'application |
| Jour 9 | 2 | 3 | Report de la garantie de parité sur le type de retour — le point dur |
| Jour 12 | 1 | 1 | Bascule RTL immédiate, garde d'annulation, tests |
| Jour 14 | 0 | 0 | Parité prouvée par suppression réelle d'une clé ; build vert |

*Le sprint n'a mobilisé que 8 des 40 points de capacité : le reste est allé à la
préparation du SPRINT-16, planifié séparément.*

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
