# ADR-005 — Routage du front : routeur maison plutôt que `react-router`

- **Date** : 2026-07-26
- **Statut** : 🟢 Accepté — SPRINT-11 (US-051)
- **Décideurs** : architecte, développeur front
- **Bloque** : US-051 (navigation adressable), et par ricochet le découpage du paquet

---

## Contexte

Jusqu'au SPRINT-10, l'écran courant de la console vivait dans un `useState`
(`App.tsx`). Aucune adresse par écran : ni lien profond, ni bouton retour du
navigateur, ni partage d'URL — et, en PWA installée, aucun moyen de rouvrir
l'application ailleurs que sur le premier onglet.

L'US-051 corrige ce point. Elle pose une question qui dépasse le sprint : **le
front, qui n'embarque aujourd'hui aucune dépendance de fonctionnement hors React
lui-même, doit-il en accepter une première ?**

Ce dépouillement n'est pas un accident. OIDC/PKCE (US-020), l'internationalisation
avec RTL (US-024) et la file de synchronisation hors-ligne (US-011) ont tous été
écrits à la main, et l'annexe B des technologies en fait un argument : chaque
dépendance est une surface de maintenance, de sécurité et de compatibilité.

## Options

### A. `react-router`

- **Pour** : standard de fait, éprouvé, couvre les cas difficiles — routes
  imbriquées, paramètres, `blocker` sur formulaire non enregistré, préchargement.
- **Contre** : environ 15 ko compressés pour un besoin qui n'en utilise qu'une
  fraction ; rompt la règle du « zéro dépendance non justifiée » ; une montée de
  version majeure du routeur devient une tâche de maintenance récurrente.

### B. Routeur maison sur l'API History

- **Pour** : une soixantaine de lignes, aucune dépendance, lisible en entier par
  un relecteur ; les routes du produit sont **plates** — un segment, aucun
  paramètre, aucune imbrication.
- **Contre** : une pièce d'infrastructure de plus à maintenir et à tester ; si le
  produit se dote un jour de routes à paramètres (`/ruches/:id`) ou de vues
  imbriquées, la dette se paiera d'un coup.

## Décision

**Option B — routeur maison.**

Les quinze routes sont plates et le resteront tant que le produit reste une
console de listes et de formulaires modaux. Un routeur générique n'apporterait
ici que sa dette de maintenance : ni imbrication, ni paramètre de route, ni
chargement de données par route à en tirer.

Concrètement : `routage/routes.ts` porte la table des routes — seule source de
vérité — et `routage/navigation.ts` un hook sur `pushState` et `popstate`. Le
tout est couvert par 8 tests unitaires et 8 tests d'ossature.

## Conséquences

- Le paquet d'entrée reste léger ; le `React.lazy` par route l'a même fait tomber
  de **302 à 235 ko** (le générateur de QR code sort du chemin critique).
- Une URL inconnue rend un écran « page introuvable » traduit, et non un écran
  blanc ni un repli silencieux sur le premier onglet.
- La reprise de route après reconnexion OIDC est traitée par le même module —
  la redirection Keycloak ramenant toujours à la racine.

## Quand rouvrir cette décision

Trois signaux, dont un seul suffit :

1. l'apparition de **routes à paramètres** (`/ruches/:id`) ou de vues imbriquées ;
2. un besoin de **garde de navigation** (bloquer la sortie d'un formulaire non
   enregistré) — écrire cela correctement à la main est nettement plus coûteux ;
3. le passage à un **rendu côté serveur**, qui change entièrement le problème.

Le remplacement resterait circonscrit : seuls `App.tsx` et les deux modules de
`routage/` connaissent la navigation.
