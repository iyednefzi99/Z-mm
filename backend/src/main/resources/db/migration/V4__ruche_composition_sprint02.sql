-- ===========================================================================
-- V4 — Ruche et composition (SPRINT-02, US-004)
--
-- Modelise la ruche et sa composition (corps + hausses), derivee de l'annexe A :
-- « corps obligatoire, jusqu'a 5 hausses, 1 a 10 cadres par element ». Le pattern
-- Composite du dictionnaire se traduit ici par une ruche et ses compartiments.
--
-- Multi-tenant (ADR-001) : tenant_id + RLS sur chaque table, comme le modele du
-- SPRINT-01. Cles etrangeres composites incluant tenant_id.
-- ===========================================================================

-- === Ruche =================================================================
-- Une ruche est HEBERGEE par un site (emplacement) et APPARTIENT a une ferme
-- (proprietaire, distincte du site — dictionnaire chap. 5). Un agent peut en etre
-- responsable (optionnel). L'etat suit le cycle de vie de la machine a etats.
CREATE TABLE ruche (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id             TEXT         NOT NULL,
    modele                VARCHAR(120) NOT NULL,
    site_id               BIGINT       NOT NULL,
    ferme_id              BIGINT       NOT NULL,
    agent_responsable_id  BIGINT,
    etat                  VARCHAR(20)  NOT NULL DEFAULT 'creee',
    cree_le               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    maj_le                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_ruche_modele_non_vide CHECK (length(trim(modele)) > 0),
    CONSTRAINT ck_ruche_etat CHECK (etat IN
        ('creee', 'peuplee', 'active', 'en_division', 'en_collecte', 'cloturee')),
    CONSTRAINT uq_ruche_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_ruche_site
        FOREIGN KEY (site_id, tenant_id) REFERENCES site (id, tenant_id) ON DELETE RESTRICT,
    CONSTRAINT fk_ruche_ferme
        FOREIGN KEY (ferme_id, tenant_id) REFERENCES ferme (id, tenant_id) ON DELETE RESTRICT,
    CONSTRAINT fk_ruche_agent
        FOREIGN KEY (agent_responsable_id, tenant_id)
        REFERENCES agent (id, tenant_id) ON DELETE SET NULL (agent_responsable_id)
);

CREATE INDEX ix_ruche_site  ON ruche (site_id);
CREATE INDEX ix_ruche_ferme ON ruche (ferme_id);

ALTER TABLE ruche ENABLE ROW LEVEL SECURITY;
ALTER TABLE ruche FORCE  ROW LEVEL SECURITY;
CREATE POLICY p_ruche_tenant ON ruche
    USING      (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));

-- === Compartiment ==========================================================
-- Element de composition : le corps (socle) ou une hausse. 1 a 10 cadres.
-- La regle « au plus un corps par ruche » est garantie par un index unique
-- partiel ; « exactement un corps » et « au plus 5 hausses » sont verifiees
-- cote service (contraintes inter-lignes, hors portee d'un CHECK simple).
CREATE TABLE compartiment (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id  TEXT        NOT NULL,
    ruche_id   BIGINT      NOT NULL,
    type       VARCHAR(10) NOT NULL,
    nb_cadres  INT         NOT NULL,
    cree_le    TIMESTAMPTZ NOT NULL DEFAULT now(),
    maj_le     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_compartiment_type CHECK (type IN ('corps', 'hausse')),
    CONSTRAINT ck_compartiment_cadres CHECK (nb_cadres BETWEEN 1 AND 10),
    CONSTRAINT uq_compartiment_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_compartiment_ruche
        FOREIGN KEY (ruche_id, tenant_id) REFERENCES ruche (id, tenant_id) ON DELETE CASCADE
);

CREATE INDEX ix_compartiment_ruche ON compartiment (ruche_id);
-- Au plus un corps par ruche.
CREATE UNIQUE INDEX uq_compartiment_corps_unique
    ON compartiment (ruche_id) WHERE type = 'corps';

ALTER TABLE compartiment ENABLE ROW LEVEL SECURITY;
ALTER TABLE compartiment FORCE  ROW LEVEL SECURITY;
CREATE POLICY p_compartiment_tenant ON compartiment
    USING      (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));

-- Horodatage de mise a jour (meme fonction que V2).
CREATE TRIGGER tg_ruche_maj BEFORE UPDATE ON ruche
    FOR EACH ROW EXECUTE FUNCTION zumm_touch_maj_le();
CREATE TRIGGER tg_compartiment_maj BEFORE UPDATE ON compartiment
    FOR EACH ROW EXECUTE FUNCTION zumm_touch_maj_le();
