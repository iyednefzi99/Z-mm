# ADR-004 — Reprise de l'existant

- **Date** : 2026-07-19
- **Statut** : 🟢 Accepté (sur hypothèses par défaut) — 2026-07-22 · voir § Arbitrage
- **Décideurs** : Product Owner, client, architecte
- **Bloque** : périmètre, planning de fin de projet, AIPD

---

## Contexte

Le cahier des charges décrit un système neuf et **ne mentionne aucune reprise de
données**. C'est une omission classique et coûteuse : les apiculteurs tiennent
déjà des registres. En pratique on trouve, par ordre de fréquence :

- des **carnets papier** de visites, parfois sur plusieurs années ;
- des **tableurs** Excel/LibreOffice, aux structures toutes différentes ;
- des **applications concurrentes** (HiveTracks, Apiary Book, BeePlus) dont le
  chapitre 4 s'inspire explicitement — donc que les utilisateurs cibles
  connaissent et utilisent peut-être déjà ;
- des **registres réglementaires** de déclaration de ruchers.

Deux conséquences directes :

1. **Sur le planning.** Une migration non anticipée surgit en fin de projet, au
   pire moment, et se règle en heures supplémentaires ou en report de mise en
   production.
2. **Sur l'adoption.** Un système qui démarre vide oblige à ressaisir. C'est la
   première cause d'abandon d'un outil de gestion terrain — et à 200 000 $,
   l'échec d'adoption est un échec de projet, même si le logiciel est correct.

Un enjeu de conformité s'y ajoute : reprendre un historique, c'est importer des
données personnelles (agents, géolocalisation) dont la base légale et la durée
de conservation doivent être traitées dans l'AIPD.

## Décision proposée

**Prévoir une reprise limitée et cadrée, avec un import CSV générique.**

- **Un inventaire de l'existant est réalisé pendant le Sprint 0** — c'est un
  livrable du Sprint 0, pas une tâche de fin de projet.
- Périmètre de reprise proposé, par ordre de priorité :

  | Priorité | Données | Justification |
  |---|---|---|
  | 1 | Référentiel : fermiers, fermes, sites, ruches, agents | Sans lui, le système démarre vide et n'est pas utilisable. |
  | 2 | Dernier état connu de chaque ruche (composition, statut reine) | Permet de reprendre la saison en cours. |
  | 3 | Historique des visites des 12 derniers mois | Donne de la valeur immédiate aux tableaux de bord. |
  | — | Historique au-delà de 12 mois, photos anciennes | **Hors périmètre** — coût élevé, valeur faible. |

- **Mécanisme** : un import CSV documenté, avec modèle de fichier fourni, et un
  rapport d'import détaillant les lignes rejetées et leur motif. Pas de
  connecteur spécifique par application concurrente : le rapport
  coût/bénéfice ne le justifie pas.
- **Effort estimé** : 13 SP, à ajouter au backlog en EPIC-001, à planifier au
  **Sprint 2** (pas en fin de projet), pour disposer de données réalistes le
  plus tôt possible dans les tableaux de bord.

**Justification.** L'import CSV couvre tous les cas de figure — un tableur
s'exporte en CSV, un carnet papier se saisit dans un tableur, et les
applications concurrentes proposent toutes un export. C'est le plus petit
mécanisme qui traite l'ensemble des sources.

## Conséquences

**Positives**

- Le système démarre avec des données réelles, ce qui sécurise l'adoption.
- Les tableaux de bord (EPIC-003) sont démontrables dès le Sprint 5 sur des
  données crédibles plutôt que sur du jeu de test.
- Un import CSV sert aussi, plus tard, aux clients suivants.

**Négatives / coûts**

- **+13 SP au périmètre** (soit 317 au lieu de 304) — c'est un ajout assumé,
  pas une réestimation des stories existantes.
- La qualité des données sources est inconnue : prévoir des itérations de
  nettoyage avec le client, et refuser tout engagement de résultat sur des
  données que nous ne maîtrisons pas.
- Chaque enregistrement importé doit recevoir un `tenant_id`
  (cf. [ADR-001](ADR-001-multi-tenant.md)).
- Traitement à documenter dans l'AIPD.

## Alternatives écartées

| Alternative | Raison du rejet |
|---|---|
| **Aucune reprise** | Risque d'adoption majeur ; ressaisie complète à la charge de l'utilisateur. |
| **Connecteurs par application** (HiveTracks, etc.) | Coût multiplié par le nombre de sources, dépendance à des API tierces non contractualisées. |
| **Reprise exhaustive de l'historique** | Coût disproportionné au regard de la valeur d'un historique de visites de plus de 12 mois. |
| **Saisie manuelle assistée** | Envisageable pour un très petit volume, mais ne passe pas l'échelle et déplace la charge sur le client. |

## Arbitrage (2026-07-22)

L'équipe projet tranche sur hypothèses par défaut. **Le mécanisme proposé est
retenu** : reprise limitée et cadrée par **import CSV générique** (modèle de
fichier fourni, rapport d'import détaillant les lignes rejetées), périmètre par
priorités du tableau ci-dessus, **+13 SP** planifiés au **Sprint 2**.

**Correction d'un point de la décision proposée.** Le texte prévoyait un
« inventaire de l'existant réalisé pendant le Sprint 0 ». **Cet inventaire n'a pas
été réalisé** : il dépend d'un accès au client qui n'a pas eu lieu. Il reste donc
**dû**, et devient une **dépendance d'entrée du Sprint 2** — le développement de
l'import CSV au Sprint 2 est conditionné à sa réalisation en amont. Cela ne change
pas le mécanisme retenu (le CSV couvre toutes les sources, connu ou non
l'inventaire), seulement le calendrier de sa préparation.

**Pourquoi cet arbitrage n'affecte pas le SPRINT-01.** La reprise est planifiée au
Sprint 2 ; le SPRINT-01 (CRUD des entités de référence) en est le prérequis, pas
l'inverse. Accepter ADR-004 fixe le périmètre (+13 SP) et lève le blocage de
principe, sans rien exiger avant le Sprint 1.

**Hypothèses retenues** (à confirmer sans bloquer le Sprint 1) :

- Sources = **tableurs + carnets papier + exports d'applications concurrentes**,
  toutes ramenées au CSV ; **pas de connecteur spécifique**.
- Nettoyage des données à la charge du **client**, avec itérations conjointes ;
  **aucun engagement de résultat** sur des données non maîtrisées.
- Chaque enregistrement importé reçoit un `tenant_id` (ADR-001).
- Base légale et durée de conservation de l'historique repris : **traitées dans
  l'AIPD** avant l'import (dépendance de conformité, pas seulement technique).

**Réserve.** Inventaire client non fait → dépendance ouverte du Sprint 2. L'arbitrage
vaut engagement d'équipe ; le périmètre exact se confirme à l'inventaire.

## Questions ouvertes — réponses par défaut

1. Sources réelles (format/volume) → **inconnues, inventaire dû avant Sprint 2**.
2. Exploitations concernées à la mise en production → hypothèse **1 à quelques**
   (cf. ADR-001/002).
3. Responsable du nettoyage → **client**, par défaut.
4. Base légale / conservation de l'historique → **à traiter dans l'AIPD**.
