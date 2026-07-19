# ADR-001 — Multi-tenant ou mono-client

- **Date** : 2026-07-19
- **Statut** : 🟠 Proposé — arbitrage client requis
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

## Questions ouvertes à trancher avec le client

1. Combien de clients distincts visés à 1 an ? à 3 ans ?
2. Un client peut-il exiger une isolation physique (base dédiée) par contrat ?
3. Un utilisateur peut-il appartenir à plusieurs exploitations (apiculteur
   prestataire travaillant pour plusieurs fermes) ? Ce cas change la
   modélisation du lien utilisateur↔tenant.
