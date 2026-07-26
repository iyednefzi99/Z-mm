-- ===========================================================================
-- V13 — Exploitation reelle de TimescaleDB sur `mesure` (SPRINT-14)
--
-- Jusqu'ici, `mesure` etait une hypertable SANS politique : autant dire une
-- table partitionnee ordinaire. TimescaleDB etait justifie dans l'ADR-002 par la
-- volumetrie attendue (un releve toutes les 15 min par ruche et par indicateur,
-- soit ~140 000 lignes par ruche et par an) ; cette migration active enfin ce
-- qui rend cette volumetrie tenable.
--
-- Trois apports, dans l'ordre de leur effet :
--   1. un index aligne sur la MANIERE dont l'application interroge ;
--   2. la compression des tranches anciennes ;
--   3. les statistiques de planification adaptees a une serie temporelle.
--
-- La politique de RETENTION (suppression automatique des vieilles mesures) est
-- volontairement ABSENTE : voir la note en fin de fichier.
-- ===========================================================================

-- ---------------------------------------------------------------------------
-- 1. Index d'acces
--
-- La cle primaire est (ruche_id, type_indicateur, instant) : elle ne porte PAS
-- `tenant_id`, contrainte de l'hypertable (toute unicite doit inclure la colonne
-- de partitionnement, et ajouter le tenant n'apporterait rien a l'unicite).
-- Consequence : la politique RLS `tenant_id = current_setting(...)` s'evalue
-- APRES le parcours d'index. Sur une base mono-tenant c'est indolore ; sur une
-- base mutualisee a vingt exploitations, chaque lecture parcourt les tranches de
-- toutes les autres avant de les jeter.
--
-- Cet index remet `tenant_id` en tete, donc dans le parcours. `instant DESC`
-- reflete l'usage reel : on lit les mesures RECENTES (derniere valeur, courbe des
-- 30 derniers jours), jamais l'historique depuis l'origine.
CREATE INDEX IF NOT EXISTS ix_mesure_tenant_ruche_type_instant
    ON mesure (tenant_id, ruche_id, type_indicateur, instant DESC);

COMMENT ON INDEX ix_mesure_tenant_ruche_type_instant IS
    'Aligne sur la RLS (tenant en tete) et sur la lecture des mesures recentes.';

-- ---------------------------------------------------------------------------
-- 2. Compression : ECARTEE, et c'est un arbitrage, pas un oubli (ADR-008)
--
-- L'intention initiale de cette migration etait d'activer la compression des
-- tranches anciennes : une serie temporelle se compresse d'un facteur 10 a 20,
-- les valeurs voisines dans le temps se ressemblant.
--
-- PostgreSQL le refuse :
--
--     ERROR: columnstore cannot be used on table with row security  (SQLSTATE 0A000)
--
-- Le stockage en colonnes de TimescaleDB et la Row Level Security de PostgreSQL
-- sont MUTUELLEMENT EXCLUSIFS : une tranche compressee est un objet interne dont
-- les lignes ne portent plus les colonnes d'origine, donc sur lequel une
-- politique `tenant_id = current_setting(...)` ne peut plus s'evaluer.
--
-- Zumm doit donc choisir entre l'ADR-001 (isolation multi-tenant garantie par le
-- SGBD) et l'ADR-002 (volumetrie tenue par la compression). L'arbitrage retenu :
--
--   * on GARDE la RLS. C'est la seule barriere qui survit a un bug applicatif ;
--     l'abandonner sur `mesure` — la table la plus volumineuse, celle qui porte
--     l'activite reelle de chaque exploitation — ferait dependre l'isolation de
--     la seule discipline du code ;
--   * on PERD la compression. Le cout se chiffre : ~140 000 lignes par ruche et
--     par an, environ 40 octets par ligne, soit ~5,6 Mo par ruche et par an. Une
--     exploitation de 500 ruches produit ~2,8 Go par an — un volume qu'un disque
--     ordinaire absorbe sans difficulte pendant des annees.
--
-- Autrement dit : le probleme que la compression resolvait n'est pas un probleme
-- a l'echelle visee, tandis que le probleme que la RLS resout est permanent.
--
-- Si la compression redevenait necessaire (mutualisation a grande echelle), la
-- voie est une table d'ARCHIVE separee, sans RLS, dont l'acces passe par des vues
-- filtrees en `security_barrier` — un chantier a part entiere, pas une option a
-- cocher.

-- ---------------------------------------------------------------------------
-- 3. Statistiques de planification
--
-- `instant` est fortement correle a l'ordre physique des lignes (on insere au fil
-- du temps). Le planificateur l'ignore par defaut et sous-estime la selectivite
-- d'un filtre de plage, ce qui le pousse vers des parcours sequentiels.
ALTER TABLE mesure ALTER COLUMN instant SET STATISTICS 1000;

-- ---------------------------------------------------------------------------
-- Note sur la RETENTION — decision explicite, pas un oubli
--
-- `add_retention_policy` supprimerait les tranches passe un certain age. C'est le
-- reflexe habituel sur une serie temporelle, et c'est ICI un contresens :
--
--   * la tracabilite du miel (directive (UE) 2024/1438, applicable au 14/06/2026)
--     s'appuie sur l'historique de production ; le detruire au bout de deux ans
--     rendrait un lot indefendable en controle ;
--   * l'analyse apicole se fait d'une ANNEE SUR L'AUTRE — comparer la miellee de
--     juin a celle des cinq etes precedents est l'usage metier de reference ;
--   * la compression ci-dessus regle deja le probleme que la retention pretend
--     resoudre : le cout de stockage.
--
-- Une exploitation qui souhaite malgre tout purger le fera explicitement :
--     SELECT add_retention_policy('mesure', INTERVAL '10 years');
-- ---------------------------------------------------------------------------

-- ---------------------------------------------------------------------------
-- 4. Garde-fous d'execution sur le role applicatif
--
-- Une requete qui part en vrille (jointure oubliee, filtre absent) ne doit pas
-- pouvoir immobiliser une connexion indefiniment, ni tenir une transaction
-- ouverte qui bloquerait le nettoyage (VACUUM) et ferait gonfler la base.
-- Ces bornes s'appliquent au ROLE, donc a toute connexion applicative, sans que
-- le code ait a y penser. Flyway (role proprietaire) n'est pas concerne : une
-- migration a le droit d'etre longue.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${role_app}') THEN
        EXECUTE format('ALTER ROLE %I SET statement_timeout = %L', '${role_app}', '30s');
        EXECUTE format('ALTER ROLE %I SET idle_in_transaction_session_timeout = %L',
                       '${role_app}', '60s');
        EXECUTE format('ALTER ROLE %I SET lock_timeout = %L', '${role_app}', '5s');
    END IF;
END
$$;
