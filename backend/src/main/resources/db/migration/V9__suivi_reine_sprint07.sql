-- ===========================================================================
-- V9 — Suivi de la reine (SPRINT-07, US-032)
--
-- Journal des evenements de la reine d'une ruche : introduction, ponte,
-- remplacement, disparition, essaimage. La couleur de marquage suit le code
-- international (blanc, jaune, rouge, vert, bleu selon l'annee). Multi-tenant
-- (ADR-001) : tenant_id + RLS, cle etrangere composite.
-- ===========================================================================

CREATE TABLE suivi_reine (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id        TEXT        NOT NULL,
    ruche_id         BIGINT      NOT NULL,
    date_evenement   DATE        NOT NULL,
    statut           VARCHAR(20) NOT NULL,
    couleur_marquage VARCHAR(10),
    annee_naissance  INT,
    race             VARCHAR(60),
    note             TEXT,
    cree_le          TIMESTAMPTZ NOT NULL DEFAULT now(),
    maj_le           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_reine_statut CHECK (statut IN
        ('introduite', 'en_ponte', 'remplacee', 'disparue', 'essaimee')),
    CONSTRAINT ck_reine_couleur CHECK (couleur_marquage IS NULL OR couleur_marquage IN
        ('blanc', 'jaune', 'rouge', 'vert', 'bleu')),
    CONSTRAINT ck_reine_annee CHECK (annee_naissance IS NULL
        OR annee_naissance BETWEEN 2000 AND 2100),
    CONSTRAINT uq_reine_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_reine_ruche
        FOREIGN KEY (ruche_id, tenant_id) REFERENCES ruche (id, tenant_id) ON DELETE CASCADE
);

CREATE INDEX ix_reine_ruche ON suivi_reine (ruche_id, date_evenement);

ALTER TABLE suivi_reine ENABLE ROW LEVEL SECURITY;
ALTER TABLE suivi_reine FORCE  ROW LEVEL SECURITY;
CREATE POLICY p_reine_tenant ON suivi_reine
    USING      (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));

CREATE TRIGGER tg_reine_maj BEFORE UPDATE ON suivi_reine
    FOR EACH ROW EXECUTE FUNCTION zumm_touch_maj_le();

COMMENT ON TABLE suivi_reine IS
    'Journal des evenements de la reine par ruche (US-032).';
