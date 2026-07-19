# 🧭 Décisions d'architecture (ADR)

Ce dossier consigne les **décisions structurantes** du projet Zümm sous forme
d'*Architecture Decision Records*. Une décision par fichier, numérotée et datée.

## Pourquoi

Une décision d'architecture non écrite se rediscute à chaque sprint, et son
« pourquoi » se perd dès qu'un membre de l'équipe change. L'ADR fige le contexte
qui a mené au choix — c'est ce contexte, plus que le choix lui-même, qui permet
de savoir plus tard si la décision reste valable.

## Statuts

| Statut | Signification |
|---|---|
| **Proposé** | Rédigé, en attente d'arbitrage. **Ne pas coder dessus.** |
| **Accepté** | Arbitré et validé. Fait autorité. |
| **Remplacé** | Remplacé par un ADR plus récent (le référencer). |
| **Obsolète** | Ne s'applique plus, conservé pour l'historique. |

## Registre

| ADR | Titre | Statut | Bloque |
|---|---|---|---|
| [ADR-001](ADR-001-multi-tenant.md) | Multi-tenant ou mono-client | 🟠 Proposé | Modèle de données, sécurité, sauvegardes |
| [ADR-002](ADR-002-volumetrie.md) | Volumétrie cible et choix TimescaleDB | 🟠 Proposé | Dimensionnement, EPIC-004 |
| [ADR-003](ADR-003-exploitation.md) | Exploitation après livraison | 🟠 Proposé | Cible de déploiement, budget transfert |
| [ADR-004](ADR-004-reprise-donnees.md) | Reprise de l'existant | 🟠 Proposé | Périmètre, planning de fin de projet |

> ⚠️ **Les quatre ADR sont au statut « Proposé ».** Ils portent une
> recommandation argumentée, pas un arbitrage. Chacun engage un coût que seul le
> client peut valider. Le Sprint 0 a pour objet de les faire passer à
> « Accepté » — la construction ne devrait pas démarrer avant.

## Ajouter un ADR

Copier la structure d'un fichier existant : Contexte → Décision → Statut →
Conséquences → Alternatives écartées. Numéroter à la suite, ne jamais réutiliser
un numéro, ne jamais réécrire l'histoire d'un ADR accepté (en créer un nouveau
qui le remplace).
