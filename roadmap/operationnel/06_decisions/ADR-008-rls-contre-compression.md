# ADR-008 — Isolation RLS ou compression TimescaleDB : il faut choisir

- **Date** : 2026-07-26
- **Statut** : 🟢 Accepté
- **Décideurs** : architecte, DBA
- **Bloque** : volumétrie de `mesure`, coût de stockage, garantie d'isolation
- **Arbitre entre** : [ADR-001](ADR-001-multi-tenant.md) et [ADR-002](ADR-002-volumetrie.md)

---

## Contexte

Deux décisions antérieures se sont révélées **techniquement incompatibles** sur la
table `mesure` — le fait n'a été découvert qu'en tentant de les appliquer ensemble
au SPRINT-14 :

- l'**ADR-001** impose une isolation multi-tenant garantie par le SGBD : chaque
  table métier porte `tenant_id` et une politique *Row Level Security*, avec
  `FORCE ROW LEVEL SECURITY` pour que même le propriétaire y soit soumis ;
- l'**ADR-002** justifie TimescaleDB par la volumétrie attendue — un relevé toutes
  les 15 minutes, par ruche et par indicateur, soit ~140 000 lignes par ruche et
  par an — et prévoit la **compression** des tranches anciennes.

La migration V13 a tenté d'activer la compression. PostgreSQL refuse :

```
ERROR: columnstore cannot be used on table with row security   (SQLSTATE 0A000)
```

La raison est structurelle, pas contournable par configuration : une tranche
compressée n'est plus une collection de lignes portant les colonnes d'origine,
mais un objet en colonnes. Une politique `tenant_id = current_setting(...)` n'a
alors plus de `tenant_id` par ligne sur quoi s'évaluer.

**Il n'existe pas d'option qui donne les deux.**

## Options

### A. Renoncer à la RLS sur `mesure`, garder la compression

L'isolation reposerait sur le seul discriminant applicatif Hibernate `@TenantId`.
Séduisant sur le papier — le discriminant fonctionne — mais il retire la garantie
précisément là où elle compte le plus : `mesure` est la table la plus volumineuse,
la plus écrite, et celle alimentée par le canal le moins contrôlé (l'ingestion de
capteurs). C'est aussi la seule table dont les lignes seraient exposées par un
oubli de filtre dans une requête analytique écrite plus tard.

Ce qu'on échangerait : une garantie permanente contre une économie de disque.

### B. Renoncer à la compression, garder la RLS

Le coût est chiffrable : ~140 000 lignes × ~40 octets ≈ **5,6 Mo par ruche et par
an**. Une exploitation de 500 ruches produit **~2,8 Go par an** — un ordre de
grandeur qu'un disque ordinaire absorbe pendant des années, sans intervention.

L'index posé par V13 — `(tenant_id, ruche_id, type_indicateur, instant DESC)` —
traite le vrai problème de performance, qui n'était pas le volume mais le fait que
`tenant_id` était absent du parcours d'index.

### C. Table d'archive séparée, sans RLS, exposée par des vues filtrées

Les mesures de plus de 90 jours partent dans une table compressée sans RLS ; leur
accès passe exclusivement par des vues `security_barrier` filtrant sur le tenant.
On récupère la compression **et** une isolation — mais portée par des vues, donc
par la discipline de ne jamais interroger la table sous-jacente en direct.

C'est un chantier à part entière : déplacement périodique, jointure des deux
sources dans les lectures, tests d'isolation sur le chemin d'archive, et une
nouvelle façon de se tromper.

## Décision

**Option B.** La RLS est conservée sur `mesure` ; la compression est abandonnée.

Le raisonnement tient en une phrase : **le problème que la compression résout
n'est pas un problème à l'échelle visée, tandis que le problème que la RLS résout
est permanent.** Un disque se rachète, une fuite inter-tenant ne se rattrape pas.

Ce qui remplace la compression dans V13 :
- l'index aligné sur la RLS et sur la lecture des mesures récentes ;
- `ALTER COLUMN instant SET STATISTICS 1000`, pour que le planificateur cesse de
  sous-estimer la sélectivité des filtres de plage ;
- des garde-fous d'exécution sur le rôle applicatif (`statement_timeout`,
  `lock_timeout`, `idle_in_transaction_session_timeout`).

**Pas de politique de rétention** non plus, et c'est délibéré : la traçabilité du
miel (directive (UE) 2024/1438) s'appuie sur l'historique de production, et
l'analyse apicole se fait d'une année sur l'autre. Détruire les mesures au bout de
deux ans rendrait un lot indéfendable en contrôle.

## Conséquences

- L'**ADR-002** doit être annoté : la compression y était présentée comme acquise.
- Le seuil de réouverture est explicite : **au-delà de ~50 exploitations
  mutualisées ou de 100 Go sur `mesure`**, reprendre l'option C.
- Le dimensionnement de production intègre ~2,8 Go/an par tranche de 500 ruches,
  sauvegardes comprises.
- Toute future hypertable héritera du même arbitrage : RLS d'abord.

## Références

- `backend/src/main/resources/db/migration/V13__mesure_index_et_gardes_sprint14.sql`
- PostgreSQL — Row Level Security ; TimescaleDB — Compression / Columnstore
