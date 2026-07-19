# Décisions d'architecture (ADR) — code applicatif

Ce dossier consigne les décisions **d'implémentation** prises pendant la
construction du code (versionnées, sans trace IA). Il ne remplace pas le registre
des décisions **produit** de `roadmap/operationnel/06_decisions/`, qui porte les
quatre ADR structurants soumis à l'arbitrage du client.

## Statut des ADR produit (rappel)

Au démarrage du code applicatif, les quatre ADR de `roadmap/operationnel/06_decisions/`
sont **au statut « Proposé »**. Conséquence directe sur le code :

- **ADR-001 (multi-tenant)** conditionne la présence d'un `tenant_id` et d'une
  politique RLS sur **chaque** table. Tant qu'il n'est pas « Accepté », aucune
  colonne `tenant_id` n'est introduite (cf. `db/migration/V1`). L'ajouter ensuite
  se fait par une migration dédiée.
- **ADR-002 (TimescaleDB)** conditionne le maintien de l'extension. Le socle
  l'active déjà, mais son sort dépend de l'arbitrage de volumétrie.

## Format

Un fichier par décision, numéroté : Contexte → Décision → Statut → Conséquences
→ Alternatives écartées. Ne jamais réécrire l'historique d'un ADR accepté ; en
créer un nouveau qui le remplace.
