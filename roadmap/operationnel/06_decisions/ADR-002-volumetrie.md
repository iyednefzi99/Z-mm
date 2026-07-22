# ADR-002 — Volumétrie cible et justification de TimescaleDB

- **Date** : 2026-07-19
- **Statut** : 🟢 Accepté (sur hypothèses par défaut) — 2026-07-22 · voir § Arbitrage
- **Décideurs** : architecte, client
- **Bloque** : dimensionnement infra, EPIC-004 (capteurs), coût d'hébergement

---

## Contexte

L'annexe B retient **PostgreSQL + PostGIS + TimescaleDB** dans une instance
unique. TimescaleDB est justifié par les séries temporelles de mesures de
capteurs (EPIC-004 : US-016 modèle Mesure, US-017 ingestion REST/MQTT,
US-018 seuils et alertes).

**Problème : aucun chiffre de volumétrie n'existe dans le dossier.** Ni nombre
de ruches, ni fréquence de mesure, ni horizon de rétention, ni nombre
d'utilisateurs simultanés. Or TimescaleDB n'apporte un gain qu'au-delà d'un
certain volume ; en dessous, c'est une dépendance et une complexité
d'exploitation gratuites — PostgreSQL nu suffirait.

Un choix technologique qu'on ne sait pas justifier par un chiffre est un choix
qu'on ne saura pas défendre en revue d'architecture.

## Hypothèses de travail (à valider)

En l'absence de chiffres client, les hypothèses suivantes servent de base de
dimensionnement. **Elles doivent être confirmées ou corrigées avant le
Sprint 1.**

| Paramètre | Hypothèse | Impact si ×10 |
|---|---|---|
| Exploitations (tenants) à 3 ans | 50 | Sans effet majeur (RLS) |
| Ruches par exploitation | 200 | Linéaire sur les mesures |
| Ruches totales instrumentées | 10 000 | Dimensionnement principal |
| Mesures par ruche et par heure | 4 (poids, température, humidité, activité) | Linéaire |
| **Mesures par jour** | **~960 000** | ~10 M/jour |
| **Mesures par an** | **~350 M** | ~3,5 Md/an |
| Rétention brute | 24 mois | Coût de stockage |
| Utilisateurs simultanés (pic saison) | 200 | Dimensionnement applicatif |

## Décision proposée

**Conserver TimescaleDB**, et le justifier par le seuil suivant :

> TimescaleDB devient pertinent au-delà d'environ **10 millions de lignes de
> mesures par an**. Sous ce seuil, PostgreSQL nu avec un index BRIN sur
> l'horodatage suffit.

Avec l'hypothèse de 350 M de mesures/an, on est **35 fois au-dessus du seuil** :
la décision se défend. Sont alors exploités :

- la **compression native** (facteur 10 à 20 sur des séries de capteurs), qui
  ramène la volumétrie à 3 ans dans un ordre de grandeur gérable ;
- les **agrégats continus** pour les tableaux de bord (US-013 à US-015), qui
  évitent de recalculer des moyennes sur des centaines de millions de lignes ;
- les **politiques de rétention** déclaratives, qui répondent directement à
  l'exigence RGPD de durée de conservation (cf. AIPD).

**Condition de révision.** Si la volumétrie réelle confirmée par le client
tombe sous 10 M de mesures/an, **retirer TimescaleDB** et s'en tenir à
PostgreSQL + PostGIS. Cette décision serait alors consignée dans un ADR
remplaçant celui-ci.

## Conséquences

**Positives**

- Rétention et agrégation traitées par le SGBD, non par du code applicatif —
  US-016 et US-018 s'en trouvent allégées.
- Une seule instance à exploiter, sauvegarder et superviser.

**Négatives / coûts**

- Dépendance supplémentaire à maintenir et à mettre à jour.
- L'image PostgreSQL du `docker-compose.yml` devient
  `timescale/timescaledb-ha`, plus lourde que l'image PostGIS seule.
- Les tests d'intégration (Testcontainers) doivent démarrer une image portant
  **à la fois** PostGIS et TimescaleDB — contrainte déjà prise en compte.

## Alternatives écartées

| Alternative | Raison du rejet |
|---|---|
| **PostgreSQL nu + index BRIN** | Suffisant sous 10 M lignes/an ; insuffisant aux volumes visés. À reprendre si les hypothèses sont revues à la baisse. |
| **InfluxDB / base de séries dédiée** | Ajoute un second système à exploiter, sauvegarder et synchroniser avec les données relationnelles. Le gain ne justifie pas ce coût à cette échelle. |
| **Agrégation applicative en Java** | Reporte sur l'application un travail que le SGBD fait mieux, et complexifie US-013 à US-015. |

## Arbitrage (2026-07-22)

L'équipe projet tranche sur hypothèses par défaut. **La décision proposée est
retenue** : conserver TimescaleDB, les hypothèses de volumétrie du tableau
ci-dessus servant de base de dimensionnement.

**Pourquoi cet arbitrage est peu risqué pour ouvrir le SPRINT-01.** TimescaleDB
n'est exercé qu'à partir des stories capteurs (**EPIC-004**, ~Sprint 4) : ni le
CRUD du SPRINT-01 ni les entités de référence n'en dépendent. Accepter sur
hypothèses ne fait donc courir **aucun risque au SPRINT-01**, tout en gardant la
décision révisable jusqu'à l'ouverture d'EPIC-004.

**Condition de révision maintenue et datée.** Si la volumétrie réelle confirmée
par le client tombe **sous 10 M de mesures/an**, TimescaleDB est retiré au profit
de PostgreSQL + PostGIS (index BRIN), par un ADR remplaçant celui-ci. **Cette
confirmation doit être obtenue avant le démarrage d'EPIC-004**, pas avant le
SPRINT-01 — c'est le vrai point de non-retour, l'ingestion des mesures.

**Réserve.** Chiffres non confirmés par le client. L'arbitrage vaut engagement
d'équipe ; il devient définitif à la confirmation des ordres de grandeur (ruches
instrumentées, fréquence, rétention) à la première revue.

## Questions ouvertes — réponses par défaut

1. Ruches réellement instrumentées à 1 an → hypothèse **10 000** (tableau).
2. Fréquence d'échantillonnage → **4 mesures/ruche/heure** par défaut.
3. Durée de conservation → **24 mois** (à croiser avec l'AIPD).
4. Restitution de l'historique brut → **non engagée** par défaut.

> Ces quatre réponses sont des hypothèses de dimensionnement, pas des engagements.
> Leur confirmation est requise **avant EPIC-004**, échéance portée au backlog.
