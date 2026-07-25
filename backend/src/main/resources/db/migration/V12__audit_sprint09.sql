-- ===========================================================================
-- V12 — Journal d'audit (SPRINT-09, US-043)
--
-- Trace « qui a fait quoi, quand » sur les entités métier : chaque création,
-- modification ou suppression via les services applicatifs y dépose une entrée.
-- Journal en ajout seul (jamais mis à jour) ; multi-tenant (ADR-001) : tenant_id
-- + RLS, comme les autres tables. L'acteur provient du jeton JWT (US-020).
-- ===========================================================================

CREATE TABLE audit_entree (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id  TEXT         NOT NULL,
    instant    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    acteur     VARCHAR(180) NOT NULL,
    action     VARCHAR(20)  NOT NULL,
    entite     VARCHAR(60)  NOT NULL,
    entite_id  BIGINT,
    resume     VARCHAR(300),
    CONSTRAINT ck_audit_action CHECK (action IN ('creation', 'modification', 'suppression'))
);

CREATE INDEX ix_audit_instant ON audit_entree (instant DESC);

ALTER TABLE audit_entree ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_entree FORCE  ROW LEVEL SECURITY;
CREATE POLICY p_audit_tenant ON audit_entree
    USING      (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));

COMMENT ON TABLE audit_entree IS
    'Journal d''audit en ajout seul (US-043) : création/modification/suppression des entités métier.';
