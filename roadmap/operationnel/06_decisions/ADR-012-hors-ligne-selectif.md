# ADR-012 — Consultation hors ligne : emport déclenché, jamais cache automatique

- **Date** : 2026-09-02
- **Statut** : ✅ **Accepté** — mis en œuvre au SPRINT-24 (lot C du plan de couverture)
- **Décideurs** : architecte, développeur front
- **Amende** : le commentaire de `vite.config.ts` interdisant tout cache de `/api`
  (SPRINT-13), qu'il précise sans le contredire
- **Dépend de** : US-011 (file de mutations hors ligne), `FiltreIdempotence` (V14)

---

## Contexte

Depuis le SPRINT-13, `vite.config.ts` place `/api`, `/actuator` et `/realms` en
`navigateFallbackDenylist`, avec ce commentaire :

> `JAMAIS d'API en cache : une mesure de capteur perimee ou une position de
> rucher servie depuis le disque induirait l'apiculteur en erreur.`

L'argument est juste et il faut le garder tel quel. Une mesure de poids vieille
de trois jours affichée comme la mesure courante ne prive pas seulement
d'information : elle en fabrique une fausse, et c'est pire que l'écran vide.

Mais le refus a été appliqué à **toute** l'API, alors qu'il ne vaut que pour les
données qui se périment vite. La liste des ruches d'un rucher, leur modèle, leur
état, leur dernier traitement et sa date de fin de carence ne changent pas dans
la journée — et ce sont précisément les données dont on a besoin sur un rucher
sans réseau.

Le résultat mesuré au §11 de `docs/ECART-CONCURRENTS.md` : la coquille de
l'application et les tuiles cartographiques fonctionnent hors ligne, **les
données non**. Six des douze catalogues concurrents mettent la consultation hors
ligne en tête de leur argumentaire ; et trois d'entre eux conseillent à leurs
utilisateurs, dans leur propre documentation, d'**ouvrir les fiches des ruchers
avant d'entrer en zone blanche** (§13). Un contournement que trois éditeurs
enseignent à leurs clients est une fonctionnalité manquante, pas une bonne
pratique — et le fait qu'il soit *manuel* chez eux dit exactement quelle forme
il doit prendre chez nous.

## La décision

**Un emport déclenché par l'utilisateur, borné à un rucher, daté à l'écran, et
périssable.** Le `navigateFallbackDenylist` reste inchangé :
aucune réponse d'API n'entre dans un cache du service worker.

Quatre propriétés, et chacune répond à une objection du commentaire de 2026 :

| Propriété | Ce qu'elle empêche |
|---|---|
| **Déclenché** — un bouton « Emporter hors ligne », jamais un cache automatique | Personne ne consulte sans le savoir une donnée qu'il n'a pas demandé d'emporter |
| **Borné à un rucher** — un seul instantané par site, pas le parc | L'emport reste petit, explicable, et se purge sans effet de bord |
| **Daté** — l'instant du prélèvement est rendu par le serveur et affiché à chaque lecture | Une donnée périmée ne peut pas passer pour fraîche : l'écran dit « données du 2 septembre à 14 h 12 » |
| **Périssable** — l'emport expire seul au bout de quatorze jours, et se purge en un geste | Un instantané oublié ne survit pas à la saison |

Techniquement, l'emport n'est pas un cache HTTP mais **une ressource explicite** :
`GET /api/ruchers/{siteId}/emport` rend en un seul appel l'instantané complet
d'un rucher, horodaté par le serveur. Le navigateur le range dans son stockage
local sous une clé nommée. C'est une donnée de l'application, pas un artefact
d'infrastructure — donc visible, listable et effaçable par l'utilisateur.

## Ce qui reste explicitement hors de l'emport

- **Les mesures de capteurs.** C'est la donnée que le commentaire de 2026
  visait, et l'objection tient intégralement : une courbe de poids figée est
  trompeuse là où une liste de ruches ne l'est pas. Les mesures ne sont pas
  emportées, et l'écran des capteurs reste vide hors ligne.
- **Les positions exactes.** L'instantané passe par `PolitiquePositions` comme
  toute autre sortie : emporter un rucher ne doit pas être un moyen d'obtenir en
  clair, sur un appareil sans session, ce que l'API dégrade en ligne.
- **La météo.** Elle a déjà son repli (simulation déterministe), et une
  prévision vieille de trois jours est du bruit.

## Conséquences

**Positives.**
- La ligne « consultation hors ligne des données » du §11 devient couvrable sans
  toucher au service worker ni rouvrir le choix de 2026.
- L'instantané est **un seul appel**, donc atomique : pas d'emport à moitié fait.
- La date de fraîcheur devient une information de premier plan à l'écran, ce qui
  vaut aussi, à terme, pour d'autres données différées.

**Négatives, et assumées.**
- Le stockage local du navigateur n'est pas chiffré. L'emport contient des noms
  de ruchers et des communes ; il ne contient pas de position exacte (voir
  ci-dessus), et il se purge. C'est le même niveau d'exposition que la file de
  mutations, qui vit déjà dans `localStorage` depuis le SPRINT-11.
- Un emport peut être **plus vieux que ce que l'utilisateur croit** s'il ignore
  la date affichée. La parade est d'afficher la date partout où l'instantané
  sert, pas seulement à l'endroit où on l'a déclenché.
- Deux appareils qui emportent le même rucher ne se synchronisent pas entre eux.
  C'est le rôle du **brouillon de visite** (même lot), qui, lui, passe par le
  serveur.

## Alternatives écartées

**1. `StaleWhileRevalidate` sur `/api`.** C'est exactement ce que le commentaire
de 2026 refuse, et il a raison : la stratégie sert la réponse périmée *sans le
dire*, et elle ne distingue pas une liste de ruches d'une série de mesures.

**2. Cache automatique des seules routes « lentes à bouger ».** Techniquement
possible (une liste d'URL en `runtimeCaching`), mais elle déplace le problème
dans un fichier de configuration : la prochaine route ajoutée sera cachée, ou ne
le sera pas, sans que personne n'ait tranché. L'emport nomme ce qui est emporté
dans **un DTO** que la revue de code voit passer.

**3. Base locale répliquée (PouchDB, RxDB, SQLite WASM).** La réplication
bidirectionnelle apporterait la synchronisation multi-appareils, mais impose son
propre modèle de conflits, ~100 ko de bibliothèque, et une seconde source de
vérité à maintenir en face de PostgreSQL. Hors de proportion avec le besoin :
consulter un rucher pendant deux heures sans réseau.

**4. Purger à chaque retour du réseau.** C'était la formulation initiale de
cette décision, et elle est fausse à l'usage : une barre de réseau qui
réapparaît dix secondes au sommet d'une côte effacerait ce que l'apiculteur
vient d'emporter, précisément au moment où il en a encore besoin. La péremption
à quatorze jours répond au même souci — aucun instantané ne traverse la saison —
sans dépendre d'un événement que le terrain déclenche au hasard.

**5. Ne rien faire.** Position tenable jusqu'ici, et elle coûte : c'est le
reproche n° 1 fait à trois des douze concurrents, sur un terrain — le rucher en
zone blanche — que le produit revendique.
