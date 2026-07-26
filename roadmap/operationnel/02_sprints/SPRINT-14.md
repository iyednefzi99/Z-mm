# 🏃 SPRINT-14 : Idempotence, index et conformité miel

**Thème :** Fiabilité de la synchronisation, tenue à l'échelle, conformité réglementaire
**Objectif :** Qu'une saisie hors ligne ne se duplique ni ne se perde, que les lectures tiennent sur un parc réel, et que l'étiquette soit conforme au 14 juin 2026
**Période :** 2027-02-16 → 2027-03-01 (14 jours)
**Story Points :** 34 / Capacity : 40

---

## 📋 User Stories

| ID | Story | Points | Statut |
|:---|:---|:---:|:---|
| US-055 | Idempotence des mutations rejouées | 8 | 🟢 Livré |
| US-056 | Lot de conditionnement et mention d'origine | 13 | 🟢 Livré |
| US-069 | Index et garde-fous d'exécution sur `mesure` | 5 | 🟢 Livré |
| US-070 | Suppression des N+1 sur les listages | 5 | 🟢 Livré |
| US-071 | Mesure et plancher de couverture (JaCoCo) | 3 | 🟢 Livré |

---

## 🔎 Ce qui a été fait, et pourquoi

### Idempotence (US-055) — deux bugs, dont un de perte de données

1. **Duplication.** `fetch` échoue aussi quand la requête est bien arrivée et que
   seule la réponse s'est perdue. Le client ne peut pas faire la différence : il
   rejoue, et la visite est créée deux fois. Aucune contrainte métier ne l'empêche
   — deux visites de la même ruche le même jour sont parfaitement légitimes.
2. **Perte.** `client.ts` traitait tout 4xx comme un refus définitif et **retirait
   la saisie de la file**. Un 401 — jeton expiré pendant la coupure, le cas le plus
   courant après une journée sur le terrain — détruisait donc le travail.

Corrigé par un en-tête `Idempotency-Key` généré **avant la première tentative** et
conservé jusqu'au rejeu (une clé regénérée au rejeu ne protégerait de rien), une
table `requete_idempotente` sous RLS, et un filtre qui rejoue la réponse mémorisée
au lieu de retraiter. Le 401/403 arrête désormais le rejeu sans rien perdre.

Trois comportements, chacun testé : clé inconnue → traitement puis mémorisation
(des seules réponses réussies) ; clé connue et même empreinte → rejeu ; clé connue
et empreinte différente → **409**, parce que c'est un bug client, pas un rejeu.

### Conformité directive (UE) 2024/1438 (US-056)

Applicable au **14 juin 2026** (décret n° 2026-312) : le pot porte le ou les pays
d'origine, par ordre décroissant, en pourcentages.

Le modèle s'arrêtait à la **récolte** — une ruche, une date. Or ce qui part en pot
est presque toujours un **mélange**, et c'est le mélange qui est étiqueté. D'où
`lot_conditionnement` + `lot_composition`, avec un point de modélisation qui
compte : la référence à la récolte est **facultative**, pour représenter le miel
acquis à un tiers. L'exiger rendrait ce miel inreprésentable, les pourcentages ne
totaliseraient jamais 100 % et la mention imprimée serait fausse.

Le service consolide les parts **par pays** avant de trier : trois récoltes
françaises donnent « France 75 % », pas trois lignes « France ». La mention est
rendue dans la langue négociée — un miel exporté s'étiquette dans la langue du
marché.

### Base de données (US-069)

La clé primaire de `mesure` ne porte pas `tenant_id` (contrainte de l'hypertable :
toute unicité doit inclure la colonne de partitionnement). La RLS s'évaluait donc
**après** le parcours d'index. Index ajouté avec le tenant en tête, statistiques
relevées sur `instant`, et garde-fous d'exécution sur le rôle applicatif
(`statement_timeout`, `lock_timeout`, `idle_in_transaction_session_timeout`).

**Découverte à l'exécution.** PostgreSQL refuse la compression TimescaleDB sur une
table sous RLS : `columnstore cannot be used on table with row security`. L'ADR-001
et l'ADR-002 étaient donc **incompatibles** sans que personne l'ait vu — parce que
personne n'avait tenté de les appliquer ensemble. Arbitrage tranché dans
[ADR-008](../06_decisions/ADR-008-rls-contre-compression.md) : la RLS est conservée,
la compression abandonnée, avec le chiffrage du coût (~2,8 Go/an pour 500 ruches).
Pas de politique de rétention non plus : la traçabilité du miel s'appuie sur
l'historique de production.

### N+1 et couverture (US-070, US-071)

Six repositories de listage déclenchaient une requête par ligne rendue pour lire le
libellé de son parent. `@EntityGraph` les ramène à une requête.

La DoD exige ≥ 70 % de couverture depuis le SPRINT-01 ; **rien ne le mesurait**.
JaCoCo fusionne désormais les campagnes unitaire et intégration — compter la seule
campagne unitaire sous-estimerait très largement un projet dont l'essentiel des
règles est prouvé par Testcontainers. **82,6 %** mesurés, plancher posé à 80 %.

---

## ✅ Definition of Done

- [x] `./mvnw verify` : 55 unitaires + 92 d'intégration, **Skipped : 0**, seuil tenu
- [x] 12 tests d'intégration nouveaux (5 idempotence, 7 conformité)
- [x] Front : 120 tests Vitest, 0 erreur ESLint
- [x] Deux ADR ajoutés ([007](../06_decisions/ADR-007-graphiques-svg.md), [008](../06_decisions/ADR-008-rls-contre-compression.md))

## 🔁 Rétrospective

**Ce qui a marché.** Partir du scénario de terrain — « le réseau tombe entre la
requête et la réponse » — plutôt que de la fonctionnalité. C'est ce qui a fait
apparaître les deux bugs du rejeu, dont un de perte de données que personne
n'aurait signalé : l'utilisateur aurait conclu qu'il avait oublié de saisir.

**Ce qu'on retient.** Deux décisions d'architecture peuvent être justes séparément
et incompatibles ensemble. Les appliquer réellement est le seul test qui le montre.

**Ce qui reste ouvert.**
- agrégats des tableaux de bord toujours calculés en Java plutôt qu'en SQL ;
- portée d'autorisation par affectation d'agent (US-057) : la RLS isole les
  exploitations, pas les agents entre eux ;
- client d'API front toujours écrit à la main alors que le contrat OpenAPI existe.
