-- ===========================================================================
-- V10 — Recolte et tracabilite par lot (SPRINT-07, US-033)
--
-- Chaque recolte porte un numero de LOT unique (dans le tenant), support de la
-- tracabilite (QR code cote client) reliant le pot a la fiche ruche. Multi-tenant
-- (ADR-001) : tenant_id + RLS, cle etrangere composite.
-- ===========================================================================

CREATE TABLE recolte (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id    TEXT          NOT NULL,
    ruche_id     BIGINT        NOT NULL,
    date_recolte DATE          NOT NULL,
    quantite_kg  NUMERIC(8, 3) NOT NULL,
    type_miel    VARCHAR(60),
    lot          VARCHAR(40)   NOT NULL,
    note         TEXT,
    cree_le      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    maj_le       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_recolte_quantite CHECK (quantite_kg >= 0),
    CONSTRAINT ck_recolte_lot_non_vide CHECK (length(trim(lot)) > 0),
    CONSTRAINT uq_recolte_id_tenant UNIQUE (id, tenant_id),
    -- Le lot est unique par tenant : c'est la cle de tracabilite scannee.
    CONSTRAINT uq_recolte_lot UNIQUE (tenant_id, lot),
    CONSTRAINT fk_recolte_ruche
        FOREIGN KEY (ruche_id, tenant_id) REFERENCES ruche (id, tenant_id) ON DELETE CASCADE
);

CREATE INDEX ix_recolte_ruche ON recolte (ruche_id, date_recolte);

ALTER TABLE recolte ENABLE ROW LEVEL SECURITY;
ALTER TABLE recolte FORCE  ROW LEVEL SECURITY;
CREATE POLICY p_recolte_tenant ON recolte
    USING      (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));

CREATE TRIGGER tg_recolte_maj BEFORE UPDATE ON recolte
    FOR EACH ROW EXECUTE FUNCTION zumm_touch_maj_le();

COMMENT ON TABLE recolte IS
    'Recoltes de miel avec numero de lot unique pour la tracabilite (US-033).';
