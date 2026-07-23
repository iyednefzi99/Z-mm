-- ===========================================================================
-- V5 — Modele de donnees Mesure (SPRINT-02, US-016)
--
-- Serie temporelle des mesures de capteurs par ruche (poids, temperature,
-- humidite, activite), stockee en HYPERTABLE TimescaleDB — justification et
-- volumetrie dans l'ADR-002. Ce sprint pose le MODELE ; l'ingestion REST/MQTT
-- et les seuils/alertes arrivent avec l'EPIC-004 (US-017, US-018).
--
-- Contrainte TimescaleDB : toute contrainte d'unicite doit inclure la colonne de
-- partitionnement (`instant`). La cle primaire est donc naturelle et composite
-- (ruche, indicateur, instant), sans identifiant de substitution.
--
-- Multi-tenant (ADR-001) : tenant_id + RLS, comme les autres tables metier.
-- ===========================================================================

CREATE TABLE mesure (
    tenant_id        TEXT          NOT NULL,
    ruche_id         BIGINT        NOT NULL,
    type_indicateur  VARCHAR(20)   NOT NULL,
    instant          TIMESTAMPTZ   NOT NULL,
    valeur           NUMERIC(12, 4) NOT NULL,
    CONSTRAINT ck_mesure_indicateur CHECK (type_indicateur IN
        ('poids', 'temperature', 'humidite', 'activite')),
    -- Inclut `instant` : exige par l'hypertable, et naturel (une mesure = une
    -- ruche, un indicateur, un instant).
    CONSTRAINT pk_mesure PRIMARY KEY (ruche_id, type_indicateur, instant),
    CONSTRAINT fk_mesure_ruche
        FOREIGN KEY (ruche_id, tenant_id) REFERENCES ruche (id, tenant_id) ON DELETE CASCADE
);

-- Transformation en hypertable, partitionnee par `instant`. Idempotent.
SELECT create_hypertable('mesure', 'instant', if_not_exists => TRUE);

ALTER TABLE mesure ENABLE ROW LEVEL SECURITY;
ALTER TABLE mesure FORCE  ROW LEVEL SECURITY;
CREATE POLICY p_mesure_tenant ON mesure
    USING      (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));

COMMENT ON TABLE mesure IS
    'Serie temporelle des mesures de capteurs (US-016). Hypertable TimescaleDB partitionnee par instant.';
