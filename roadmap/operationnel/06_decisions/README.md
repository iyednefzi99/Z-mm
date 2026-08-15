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
| [ADR-005](ADR-005-routage-front.md) | Routage du front (maison vs `react-router`) | 🟢 Accepté | US-051, découpage du paquet |
| [ADR-006](ADR-006-stockage-des-jetons.md) | Où vivent les jetons de la PWA | 🟢 Accepté et **mis en œuvre** (SPRINT-16) | US-073, CSP du proxy, mise en production |
| [ADR-007](ADR-007-graphiques-svg.md) | Graphiques : SVG maison plutôt que Chart.js | 🟢 Accepté | US-065, écart assumé au cahier |
| [ADR-008](ADR-008-rls-contre-compression.md) | Isolation RLS ou fonctionnalités avancées de TimescaleDB | 🟢 Accepté — **généralisé au SPRINT-18** | US-070, US-080, volumétrie |
| [ADR-009](ADR-009-connexion-dans-l-application.md) | Connexion et inscription depuis l'application | 🟢 Accepté et **mis en œuvre** (SPRINT-18) | Parcours d'entrée, création de comptes |
| [ADR-010](ADR-010-jetons-de-session-au-repos.md) | Les jetons de session au repos | 🟢 Accepté | Sauvegardes, restauration, procédure d'incident |

> ✅ **Les quatre premiers ADR sont arbitrés (2026-07-22), sur hypothèses par défaut.**
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
>
> 📌 **ADR-005 à 008 sont postérieurs au Sprint 0** et ne relèvent pas de cet
> arbitrage : ce sont des décisions techniques prises en cours de construction,
> chacune en réponse à un fait constaté en revue — et non des hypothèses en attente
> de confirmation client.

## Ajouter un ADR

Copier la structure d'un fichier existant : Contexte → Décision → Statut →
Conséquences → Alternatives écartées. Numéroter à la suite, ne jamais réutiliser
un numéro, ne jamais réécrire l'histoire d'un ADR accepté (en créer un nouveau
qui le remplace).
