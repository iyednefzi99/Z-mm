-- ===========================================================================
-- V1 — Socle du walking skeleton (SPRINT-00)
--
-- Objectif : prouver que la chaine Flyway + PostgreSQL + PostGIS + TimescaleDB
-- s'assemble, AVANT toute entite metier. Le modele metier reel (fermier, ferme,
-- site, rucher, ruche...) derive strictement du dictionnaire de donnees et du
-- MLD ; il n'est PAS introduit ici et arrivera au SPRINT-01 apres arbitrage.
--
-- ATTENTION : la table `ping` est une entite factice de verification. Elle doit
-- etre supprimee par une migration ulterieure des que le modele metier existe.
--
-- Multi-tenant : ADR-001 est au statut « Propose ». Aucune colonne `tenant_id`
-- ni politique RLS n'est introduite tant que la decision n'est pas « Acceptee ».
-- ===========================================================================

CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS timescaledb;

CREATE TABLE ping (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    libelle    VARCHAR(120) NOT NULL,
    cree_le    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_ping_libelle_non_vide CHECK (length(trim(libelle)) > 0)
);

COMMENT ON TABLE ping IS
    'Entite factice du walking skeleton (SPRINT-00) — a supprimer au SPRINT-01.';
