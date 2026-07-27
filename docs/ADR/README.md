# Décisions d'architecture (ADR) — code applicatif

Ce dossier consigne les décisions **d'implémentation** prises pendant la
construction du code (versionnées, sans trace IA). Il ne remplace pas le registre
des décisions **produit** de `roadmap/operationnel/06_decisions/`, qui porte les
quatre ADR structurants soumis à l'arbitrage du client.

## Deux registres, deux numérotations

⚠️ **Les numéros des deux dossiers sont indépendants et se recoupent.**
`ADR-001` désigne ici le *socle technique du walking skeleton*, et dans
`roadmap/operationnel/06_decisions/` la décision *multi-tenant*. Toujours citer
un ADR avec son dossier.

| Registre | Contenu | Arbitrage |
|---|---|---|
| `roadmap/operationnel/06_decisions/` | 8 ADR **produit** : multi-tenant, volumétrie, exploitation, reprise, routage front, stockage des jetons, graphiques SVG, RLS contre TimescaleDB | Client (ADR-001 à 004) ou équipe (005 à 008) |
| `docs/ADR/` *(ce dossier)* | Décisions **d'implémentation** prises pendant la construction | Équipe |

## Statut des ADR produit (rappel)

Les quatre ADR structurants ont été **acceptés le 2026-07-22**, sur hypothèses
par défaut faute d'arbitrage client dans les délais du Sprint 0. Conséquences
effectives sur le code :

- **ADR-001 (multi-tenant) accepté** : toute table métier porte `tenant_id`, sa
  politique RLS et une clé étrangère composite `(id, tenant_id)`, depuis les
  migrations `V2`/`V3`. Seule `ping`, sonde du walking skeleton, fait exception.
- **ADR-002 (TimescaleDB) accepté** : l'extension est conservée et `mesure` est
  une hypertable (`V5`). En revanche, l'[ADR-008](../../roadmap/operationnel/06_decisions/ADR-008-rls-contre-compression.md)
  a établi qu'**aucune fonctionnalité matérialisante** de TimescaleDB
  (compression, agrégat continu) n'est disponible sous RLS — PostgreSQL les
  refuse. L'isolation a été conservée, ces fonctions abandonnées.

## Format

Un fichier par décision, numéroté : Contexte → Décision → Statut → Conséquences
→ Alternatives écartées. Ne jamais réécrire l'historique d'un ADR accepté ; en
créer un nouveau qui le remplace.
