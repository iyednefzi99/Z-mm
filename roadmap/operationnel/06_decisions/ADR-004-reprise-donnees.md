# ADR-004 — Reprise de l'existant

- **Date** : 2026-07-19
- **Statut** : 🟠 Proposé — inventaire client requis
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

## Questions ouvertes à trancher avec le client

1. Quelles sources existent réellement, sous quel format, sur quel volume ?
2. Combien d'exploitations sont concernées à la mise en production ?
3. Qui est responsable du nettoyage des données avant import — nous ou eux ?
4. Quelle base légale et quelle durée de conservation pour l'historique repris ?
