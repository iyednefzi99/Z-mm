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
| **Accepté (sur hypothèses par défaut)** | Arbitré par l'équipe projet faute d'arbitrage client dans les délais ; fait autorité pour la construction, sous réserves documentées à confirmer par le client. |
| **Remplacé** | Remplacé par un ADR plus récent (le référencer). |
| **Obsolète** | Ne s'applique plus, conservé pour l'historique. |

## Registre

| ADR | Titre | Statut | Bloque |
|---|---|---|---|
| [ADR-001](ADR-001-multi-tenant.md) | Multi-tenant ou mono-client | 🟢 Accepté (hypothèses) | Modèle de données, sécurité, sauvegardes |
| [ADR-002](ADR-002-volumetrie.md) | Volumétrie cible et choix TimescaleDB | 🟢 Accepté (hypothèses) | Dimensionnement, EPIC-004 |
| [ADR-003](ADR-003-exploitation.md) | Exploitation après livraison | 🟢 Accepté (hypothèses) | Cible de déploiement, budget transfert |
| [ADR-004](ADR-004-reprise-donnees.md) | Reprise de l'existant | 🟢 Accepté (hypothèses) | Périmètre, planning de fin de projet |

> ✅ **Les quatre ADR sont arbitrés (2026-07-22), sur hypothèses par défaut.**
> Faute d'arbitrage client dans les délais du Sprint 0 (escalade J+5 échue),
> l'équipe projet a tranché dans le sens des décisions proposées, chacune sous
> réserves explicites détaillées dans sa section *Arbitrage*. **La construction du
> SPRINT-01 peut démarrer.**
>
> ⚠️ **Ces arbitrages valent engagement d'équipe, pas validation contractuelle.**
> La DoD du Sprint 0 demandait une signature client, qui n'est pas acquise :
> chaque ADR liste ce qui reste à faire confirmer, et à quelle échéance (revue
> client, ou avant EPIC-004 pour ADR-002). Le seul point dont un revirement client
> imposerait une reprise est le lien utilisateur↔tenant d'ADR-001 (hypothèse 3).

## Ajouter un ADR

Copier la structure d'un fichier existant : Contexte → Décision → Statut →
Conséquences → Alternatives écartées. Numéroter à la suite, ne jamais réutiliser
un numéro, ne jamais réécrire l'histoire d'un ADR accepté (en créer un nouveau
qui le remplace).
