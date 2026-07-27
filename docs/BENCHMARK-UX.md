# Zümm — parangonnage de l'interface

> État au 26/07/2026. Complément de [`STRATEGIE-PRODUIT.md`](STRATEGIE-PRODUIT.md),
> qui traite du **positionnement** ; celui-ci ne traite que de l'**interface** :
> ce que les consoles apicoles du marché font de l'écran, ce que Zümm en faisait,
> et ce qui a été corrigé.

---

## 1. Ce que le marché a convergé à faire

Les comparatifs 2026 (VarroaVault, beekeeping-diary.eu, tryhivesense.com) décrivent
un consensus d'interface assez net, indépendant du modèle économique :

| Convention | Qui l'applique | Ce qu'elle résout |
|---|---|---|
| **Accueil = santé du cheptel**, jamais un référentiel | HiveTracks, Nectar, HiveSense | La première question d'un apiculteur qui ouvre l'application n'est pas « quels sont mes fermiers ? » |
| **Code couleur de gravité** (vert / ambre / orange / rouge) trié par urgence | Tous les tableaux de bord multi-ruches | Lire l'état de 200 colonies sans lire 200 lignes |
| **Barre du bas sur mobile**, 3 à 5 destinations | Apiary Book, Beentry, HiveSense | Le haut d'un téléphone est hors d'atteinte à une main |
| **Cibles larges, « mode gant »** | HiveSense (revendiqué comme différenciateur) | On saisit une visite à côté d'une ruche ouverte |
| **Hors-ligne d'abord**, synchronisation au retour du réseau | Apiary Book, BeePlus, HiveSense | Un rucher n'a pas de réseau. Le reproche n°1 fait à HiveTracks est justement là |
| **Gabarits de visite** (reine, couvain, réserves, maladie, action) | Apiary Book, HiveTracks | Réduire le nombre de gestes par inspection |

Deux enseignements moins attendus :

1. **Le coût dominant n'est pas le clic, c'est la recherche.** Les critiques
   récurrentes ne portent presque jamais sur le nombre d'écrans, mais sur le temps
   passé à retrouver le bon.
2. **La saisie vocale est en train de devenir la référence de terrain** (HiveSense
   la transcrit localement). Personne ne la fait en trilingue.

---

## 2. Où Zümm était déjà devant

Ce ne sont pas des intentions : c'est du code livré, et aucun concurrent grand
public ne réunit les six.

- **Jetons de conception versionnés** (`design/tokens.json` en DTCG, miroir CSS
  dans `theme/tokens.css`) — aucune couleur ni durée en dur dans les feuilles.
- **Mode sombre à trois positions** (auto / clair / sombre), avec une palette de
  séries **recalculée** pour le fond sombre et non simplement éclaircie, et la
  balise `theme-color` qui suit.
- **Trilingue avec RTL réel** : propriétés logiques partout, donc l'arabe est un
  miroir et non une traduction posée sur une mise en page latine.
- **Accessibilité tenue** : anneau de focus unique en `:focus-visible`, piège de
  focus et restitution au déclencheur dans les dialogues, information jamais
  portée par la seule couleur, `prefers-reduced-motion` respecté globalement.
- **États d'attente et de vide traités** : squelettes qui annoncent la forme à
  venir, écrans vides porteurs d'une action.
- **Hors-ligne d'abord**, avec file d'attente visible et bandeau de mise à jour
  qui ne recharge jamais sans prévenir.

---

## 3. Les cinq défauts constatés, et ce qui a été fait

### 3.1 Seize onglets dans une barre qui défile — **corrigé**

C'était le défaut structurant. Passé la septième entrée, une barre horizontale
cesse d'être balayée et devient une liste où l'on cherche ; aucun concurrent ne
dépasse sept destinations de premier niveau.

**Fait** : les seize écrans sont répartis en **cinq familles** suivant le déroulé
du métier — Pilotage, Cheptel, Terrain, Production, Administration
(`routage/routes.ts`, `GROUPES`). Un test tient l'invariant : chaque écran
appartient à exactement une famille, sinon un écran ajouté resterait joignable
par son URL tout en étant absent de la navigation.

### 3.2 Aucune navigation adaptée au pouce — **corrigé**

**Fait** : un **seul** élément de navigation dans le DOM, deux présentations —
rail latéral persistant au-dessus de 900 px, barre du bas en dessous, icône
au-dessus du libellé, 56 px de côté. Dupliquer le balisage aurait dupliqué seize
noms accessibles ; tout se joue en CSS.

Un menu replié aurait coûté moins de code, mais cacher la navigation principale
en fait chuter l'usage — la contrainte retenue est qu'elle reste **visible**.

### 3.3 L'accueil ouvrait sur le référentiel — **corrigé**

`ONGLET_PAR_DEFAUT` valait `fermiers` : la première ligne de la liste, choisie
par défaut plutôt que décidée. L'apiculteur devait naviguer avant de savoir si
son cheptel allait bien.

**Fait** : l'accueil est `tableaux` — synthèse, alertes sanitaires, production.

### 3.4 Cibles tactiles sous le plancher — **corrigé**

Les boutons faisaient ~36 px de haut, les actions de ligne ~28 px, pour une PWA
qui se tient dehors, d'une main, souvent avec un gant.

**Fait** : 44 px de plancher sur tous les boutons, 56 px dans la barre du bas, et
les actions de ligne passent à 44 px sous `@media (pointer: coarse)` — élargies
partout, elles auraient doublé la hauteur de chaque tableau pour rien.

### 3.5 La gravité reposait sur un mot et une teinte de fond — **corrigé**

**Fait** : composant `Pastille` (point plein + fond teinté + libellé traduit),
appliqué au tableau des alertes sanitaires. La colonne se trie du regard, et
l'information ne disparaît pas en vision monochrome.

### 3.6 En plus : palette de commandes (Ctrl/⌘ + K)

Grouper règle la lisibilité, pas la distance. La palette filtre les seize écrans
par **sous-séquence** — « lts » trouve « Lots & origines », « tbx » trouve
« Tableaux de bord » — et cherche aussi les noms de familles, si bien que taper
« terrain » sort les quatre écrans de terrain. Elle s'ouvre pleine, jamais sur le
vide, et la barre supérieure porte un bouton qui l'ouvre : un raccourci que
personne ne découvre ne fait naviguer personne.

---

## 4. Ce qui reste, par ordre de valeur

| Sujet | Pourquoi | Effort |
|---|---|---|
| **Saisie vocale de la visite** (Web Speech API, FR/EN/AR) | Le geste de terrain le plus coûteux ; personne ne le fait en trilingue | Moyen |
| **Gabarits de visite** (reine · couvain · réserves · sanitaire · action) | Standard du marché ; réduit une inspection à quelques appuis | Moyen |
| **Vue « fleet » des ruches triée par urgence** | Le tableau des alertes existe ; il manque l'entrée par ruche, pastille en tête de ligne | Faible |
| **QR sur la ruche → sa fiche** | Déjà mentionné en Priorité 4 ; le générateur QR est embarqué | Faible |
| **Recherche de données** dans la palette (ruche, site, lot) | Aujourd'hui elle ne navigue qu'entre écrans | Moyen |
| **Undo plutôt que confirmation** sur les suppressions | Les toasts portent déjà une action ; la confirmation punit tout le monde pour une erreur rare | Faible |

---

## Sources

- [VarroaVault — Beekeeping App Comparison 2026](https://varroavault.com/beekeeping-app-comparison-2026)
- [VarroaVault — Best Beekeeping Management Software 2026](https://varroavault.com/best-beekeeping-management-software-2026)
- [Beekeeping Diary — Best Beekeeping Apps 2026](https://beekeeping-diary.eu/blog/en/best-beekeeping-apps-2026/)
- [HiveSense — Best Beekeeping Apps in 2026: A Neutral Buyer's Guide](https://tryhivesense.com/blog/best-beekeeping-apps-2026)
- [HiveBook — Top 5 Free HiveTracks Alternatives](https://hivebook.app/blog/hivetracks-alternatives-free)
- [Beentry](https://www.beentry.com/) · [Beeing](https://beeing.gr/)
