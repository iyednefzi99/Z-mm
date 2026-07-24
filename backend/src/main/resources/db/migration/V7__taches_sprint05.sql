-- ===========================================================================
-- V7 — Taches et rappels (SPRINT-05, US-031)
--
-- Liste de taches de l'apiculteur, eventuellement rattachees a une ruche et
-- assignees a un agent, avec une echeance. Multi-tenant (ADR-001) : tenant_id
-- + RLS, cles etrangeres composites.
-- ===========================================================================

CREATE TABLE tache (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id  TEXT         NOT NULL,
    libelle    VARCHAR(200) NOT NULL,
    ruche_id   BIGINT,
    agent_id   BIGINT,
    echeance   DATE,
    faite      BOOLEAN      NOT NULL DEFAULT false,
    cree_le    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    maj_le     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_tache_libelle_non_vide CHECK (length(trim(libelle)) > 0),
    CONSTRAINT uq_tache_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_tache_ruche
        FOREIGN KEY (ruche_id, tenant_id) REFERENCES ruche (id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT fk_tache_agent
        FOREIGN KEY (agent_id, tenant_id) REFERENCES agent (id, tenant_id) ON DELETE SET NULL (agent_id)
);

CREATE INDEX ix_tache_echeance ON tache (echeance);

ALTER TABLE tache ENABLE ROW LEVEL SECURITY;
ALTER TABLE tache FORCE  ROW LEVEL SECURITY;
CREATE POLICY p_tache_tenant ON tache
    USING      (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));

CREATE TRIGGER tg_tache_maj BEFORE UPDATE ON tache
    FOR EACH ROW EXECUTE FUNCTION zumm_touch_maj_le();
