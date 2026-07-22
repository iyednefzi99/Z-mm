# ADR-001 — Multi-tenant ou mono-client

- **Date** : 2026-07-19
- **Statut** : 🟢 Accepté (sur hypothèses par défaut) — 2026-07-22 · voir § Arbitrage
- **Décideurs** : Product Owner, architecte, client
- **Bloque** : modèle de données, sécurité, sauvegardes, tarification

---

## Contexte

Le cahier des charges ne tranche jamais la question : **Zümm est-il un produit
vendu à plusieurs exploitations apicoles, ou l'outil interne d'une seule ?**

Toute la modélisation actuelle (chapitre 5, dictionnaire de données ; annexe A,
structure de la ruche) part de la hiérarchie `Fermier → Ferme → Site → Ruche`.
Cette hiérarchie décrit l'organisation **d'un** exploitant. Rien n'indique si
plusieurs exploitants coexistent dans la même base, ni comment leurs données
sont isolées.

Or ce point conditionne :

- le **modèle de données** (présence d'un `tenant_id` sur chaque table),
- l'**isolation de sécurité** (une requête mal filtrée expose les ruchers d'un
  concurrent — avec la sensibilité GPS décrite en [ADR-004](ADR-004-reprise-donnees.md)
  et dans l'annexe G),
- la **stratégie de sauvegarde et de restauration** (restaurer un seul client
  sans toucher aux autres),
- la configuration **Keycloak** (realm unique ou realm par client),
- le **modèle économique** lui-même.

Le point décisif est le coût du report : ajouter le multi-tenant *après* avoir
écrit les 40 user stories impose de reprendre chaque table, chaque requête,
chaque test et chaque écran. C'est un des rares choix d'architecture dont le
rattrapage coûte plusieurs fois la mise en œuvre initiale.

## Décision proposée

**Concevoir le système multi-tenant dès l'origine**, avec isolation par
*Row Level Security* (RLS) PostgreSQL.

- Une colonne `tenant_id` non nulle sur toutes les tables métier.
- Une politique RLS PostgreSQL par table, s'appuyant sur une variable de session
  positionnée par l'application à partir du jeton JWT.
- Un realm Keycloak unique, avec le `tenant_id` porté en *claim* du jeton.
- Une base unique, schéma unique — pas de schéma ni de base par client tant que
  la volumétrie ne l'impose pas (cf. [ADR-002](ADR-002-volumetrie.md)).

**Justification.** À une enveloppe de 200 000 $, l'hypothèse d'un produit
commercialisable est bien plus probable que celle d'un outil interne unique.
Le surcoût du multi-tenant construit dès le départ est modéré — de l'ordre de
15 à 20 jours-personne, concentrés sur le Sprint 0 et l'EPIC-001. Le surcoût du
rattrapage après coup est estimé à 3 à 4 fois ce montant, avec un risque de
régression de sécurité élevé.

La RLS est préférée au filtrage applicatif parce qu'elle place la garantie
d'isolation dans la base : un oubli de `WHERE tenant_id = ?` dans un repository
ne devient pas une fuite de données entre clients.

## Conséquences

**Positives**

- L'isolation est garantie par le SGBD, pas par la discipline des développeurs.
- La commercialisation à plusieurs clients ne demande aucune reprise.
- Les sauvegardes et exports par client deviennent des requêtes filtrées.

**Négatives / coûts**

- Surcoût initial de 15 à 20 j·p, à absorber dans le Sprint 0 et l'EPIC-001.
- Toute requête jOOQ analytique doit être écrite en conscience de la RLS.
- Les tests d'intégration (US-037, Testcontainers) doivent inclure des cas de
  **tentative de fuite inter-tenant** — c'est une exigence de test nouvelle.
- La migration de données (ADR-004) doit affecter un `tenant_id` à l'existant.

**Si la décision est refusée** (mono-client assumé) : documenter explicitement
que Zümm n'est pas commercialisable en l'état, et faire porter la décision par
le client par écrit.

## Alternatives écartées

| Alternative | Raison du rejet |
|---|---|
| **Mono-client** | Ferme la commercialisation ; rattrapage ultérieur prohibitif. |
| **Une base par client** | Isolation maximale mais coût d'exploitation qui croît linéairement (migrations × N, sauvegardes × N). À reconsidérer si un client exige une isolation physique contractuelle. |
| **Un schéma PostgreSQL par client** | Compromis intermédiaire, mais les migrations de schéma deviennent pénibles au-delà de quelques dizaines de clients. |
| **Filtrage applicatif seul** | Un seul oubli de clause `WHERE` suffit à créer une fuite. Inacceptable au vu de la sensibilité des coordonnées GPS. |

## Arbitrage (2026-07-22)

Faute d'arbitrage client dans les délais du Sprint 0, l'équipe projet (architecte,
Product Owner) tranche sur hypothèses par défaut — l'escalade J+5 prévue au
SPRINT-00 est arrivée à échéance. **La décision proposée est retenue** :
multi-tenant dès l'origine, isolation par RLS PostgreSQL, realm Keycloak unique,
`tenant_id` en *claim* JWT.

**Pourquoi trancher dans ce sens sans le client.** C'est la décision qui préserve
l'optionalité : bâtir mono-client et découvrir ensuite le besoin multi-tenant
coûte 3 à 4× le rattrapage, avec risque de fuite ; bâtir multi-tenant et rester
mono-client ne coûte qu'un léger surdimensionnement. Face à une incertitude
irréversible, on choisit l'option réversible.

**Hypothèses retenues** (à confirmer, sans bloquer la construction) :

1. Produit **commercialisable à plusieurs exploitations** — cohérent avec
   l'enveloppe de 200 000 $.
2. **Pas d'isolation physique contractuelle** à ce jour : base et schéma uniques,
   RLS. Un client l'exigeant serait traité par l'alternative « base par client »,
   sans remettre en cause la modélisation `tenant_id`.
3. **Un utilisateur appartient à une seule exploitation** (lien utilisateur↔tenant
   simple). C'est le seul point qui touche la modélisation : le défaut retenu est
   le plus simple et **extensible** (une table d'association pourra le lever plus
   tard sans casser l'existant). À signaler au client comme limitation actuelle.

**Réserve.** La DoD du Sprint 0 demandait une signature client ; elle n'est pas
acquise. Cet arbitrage a valeur d'engagement d'équipe, pas de validation
contractuelle : à confirmer par écrit à la première revue client. Si le client
tranche différemment, seul le point 3 impose une reprise (limitée) ; le cœur
`tenant_id`/RLS reste valable dans tous les scénarios sauf mono-client assumé.

## Questions ouvertes — réponses par défaut

1. Clients distincts visés à 1/3 ans → hypothèse **50 à 3 ans** (cf. ADR-002).
2. Isolation physique par contrat → **non par défaut** (voir hypothèse 2).
3. Utilisateur multi-exploitations → **non par défaut** (voir hypothèse 3).
