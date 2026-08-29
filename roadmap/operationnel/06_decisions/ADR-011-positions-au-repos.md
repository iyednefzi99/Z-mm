# ADR-011 — Chiffrement au repos des positions de ruchers : arbitrage

- **Date** : 2026-08-26
- **Statut** : 🟡 **Proposé** — à trancher avant toute mise en production
- **Décideurs** : architecte, responsable sécurité, client
- **Bloque** : mise en production, conformité de l'AIPD
- **Dépend de** : [ADR-001](ADR-001-multi-tenant.md) (RLS), EPIC-012 (intelligence spatiale)

---

## Contexte

La position exacte d'un rucher est la donnée la plus sensible du produit, et elle
ne l'est pas au sens habituel : elle ne vaut rien pour un publicitaire et
beaucoup pour un voleur. Un rucher se vole ; une liste de coordonnées est une
carte au trésor. C'est ce constat qui a produit `PolitiquePositions` au
SPRINT-12 — les coordonnées **sortent** dégradées selon le rôle, l'altitude est
masquée, et les distances de voisinage sont arrondies à 100 m.

Ce dispositif protège la **restitution**. Il ne protège pas le **repos** : dans
`site`, `latitude`, `longitude` et la colonne `geography` PostGIS sont en clair.
Quiconque obtient une copie de la base — sauvegarde égarée, accès disque,
compromission du rôle propriétaire — lit toutes les positions de toutes les
exploitations.

`docs/SECURITE.md` § 9 inscrit ce point depuis le SPRINT-12 comme un
**arbitrage**, pas comme un oubli. Cet ADR l'instruit.

## Le conflit, énoncé précisément

Un chiffrement applicatif (la colonne contient un cryptogramme, l'application
déchiffre) **détruit l'indexation spatiale**. Or le produit repose dessus :

| Fonction | Requête | Ce qu'elle devient sur une colonne chiffrée |
|---|---|---|
| Sites proches d'un point (US-045) | `ST_DWithin` sur index GiST | Balayage complet + déchiffrement de chaque ligne |
| Grappes de ruchers (US-045) | `ST_ClusterDBSCAN` en base | Impossible en SQL : le regroupement devrait remonter en Java |
| Plus proches voisins (US-046) | Parcours d'index KNN (`<->`) | Impossible : l'ordre des distances n'est pas préservé |
| Ordre de tournée (US-047) | Matrice de distances en base | Impossible pour la même raison |

Le chiffrement déterministe ne sauve rien : il préserve l'égalité, pas l'ordre ni
la distance. Le chiffrement préservant l'ordre existe, fuit largement, et n'a
aucun sens sur deux dimensions.

**Il n'existe donc pas d'option qui donne à la fois le chiffrement applicatif et
l'intelligence spatiale.** C'est le même type d'incompatibilité que celle tranchée
par l'[ADR-008](ADR-008-rls-contre-compression.md), et elle mérite le même
traitement : choisir explicitement, chiffrer le coût, l'écrire une fois.

## Options

### A. Chiffrement applicatif des coordonnées

Les colonnes deviennent des cryptogrammes ; l'application déchiffre à la lecture.

- **Pour** : une copie de la base ne rend plus aucune position.
- **Contre** : l'EPIC-012 entier tombe — quatre user stories livrées et testées,
  soit 21 points, à réécrire en mémoire avec une complexité et un coût de calcul
  sans commune mesure. La clé doit vivre ailleurs que dans la base, ce qui déplace
  le problème vers une gestion de secrets qui n'existe pas encore.

### B. Chiffrement au repos par le volume (TDE / disque chiffré)

Le chiffrement est assuré sous PostgreSQL, par le stockage.

- **Pour** : couvre le vol de disque et la sauvegarde égarée — les deux scénarios
  les plus probables — **sans toucher une ligne de SQL**. Les index restent
  intacts, l'EPIC-012 est préservé.
- **Contre** : ne protège pas contre un accès à la base **en fonctionnement** :
  pour PostgreSQL, les pages sont en clair. Un compromis du rôle propriétaire
  reste total. La protection dépend de l'hébergeur, pas du dépôt.

### C. Statu quo, avec compensations explicites

`PolitiquePositions` en sortie, RLS en base, sauvegardes chiffrées au niveau du
fichier de dump, et rien de plus.

- **Pour** : aucun coût, aucun risque de régression.
- **Contre** : laisse ouvert exactement le scénario qui fait le plus mal, et
  l'AIPD le mentionne.

## Décision proposée

**Option B**, complétée de deux mesures qui ne dépendent pas de l'hébergeur :

1. **Chiffrement du volume de données** et **des sauvegardes** — `infra/sauvegarde.sh`
   produit aujourd'hui un dump en clair ; il doit le chiffrer, et
   `tester-restauration.sh` doit exercer le déchiffrement, faute de quoi on ne
   saura qu'au pire moment que la clé manquait.
2. **Purge EXIF sur les photos**, exigée par l'AIPD et non faite : une photo de
   rucher géolocalise le rucher aussi sûrement que la colonne `latitude`. Ce point
   est indépendant de l'option retenue et doit être traité dans tous les cas.

L'option A est **écartée** : payer l'EPIC-012 pour une protection qui ne couvre
pas le scénario du compromis applicatif est un mauvais échange. Si la menace
retenue devient « l'hébergeur lui-même », c'est l'hébergement qu'il faut changer,
pas le schéma.

## Conséquences

- **Assumées** : une compromission du rôle propriétaire en fonctionnement rend
  toutes les positions. C'est la raison pour laquelle l'application se connecte
  sous `zumm_app`, non superutilisateur et soumis à la RLS, et pour laquelle ce
  rôle-là ne doit jamais devenir propriétaire.
- **À faire avant la production** : chiffrement du volume, chiffrement des dumps
  et exercice de restauration correspondant, purge EXIF.
- **À rejuger** si le produit héberge un jour des exploitations concurrentes sur
  la même instance sans cloisonnement matériel.

## Ce que cet ADR ne décide pas

Il ne statue pas sur le chiffrement des **autres** données personnelles (courriels
des agents, journal d'audit). Elles relèvent du même mécanisme de volume et n'ont
pas la contrainte d'indexation spatiale ; aucun arbitrage n'y est nécessaire.
