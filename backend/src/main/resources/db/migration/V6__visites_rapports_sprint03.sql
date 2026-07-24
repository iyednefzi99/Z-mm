-- ===========================================================================
-- V6 — Planning, Visite, Photo (SPRINT-03, US-007 a US-010, US-028)
--
-- Workflow de visite : planification (planning) -> approbation du superviseur ->
-- realisation (visite) + rapport -> photos. Derive du dictionnaire (chap. 5).
-- Multi-tenant (ADR-001) : tenant_id + RLS, cles etrangeres composites.
-- ===========================================================================

-- === Planning ==============================================================
-- US-007 (planifier) / US-008 (approuver ou refuser). Un agent planifie la visite
-- d'une ruche ; un superviseur l'approuve ou la refuse.
CREATE TABLE planning (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id      TEXT        NOT NULL,
    ruche_id       BIGINT      NOT NULL,
    agent_id       BIGINT      NOT NULL,
    superviseur_id BIGINT,
    date_prevue    DATE        NOT NULL,
    heure_prevue   TIME,
    duree_min      INT,
    raison         VARCHAR(20) NOT NULL DEFAULT 'controle',
    statut         VARCHAR(20) NOT NULL DEFAULT 'propose',
    motif_refus    TEXT,
    cree_le        TIMESTAMPTZ NOT NULL DEFAULT now(),
    maj_le         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_planning_raison CHECK (raison IN
        ('controle', 'recolte', 'traitement', 'nourrissage', 'division', 'autre')),
    CONSTRAINT ck_planning_statut CHECK (statut IN ('propose', 'approuve', 'refuse')),
    CONSTRAINT ck_planning_duree CHECK (duree_min IS NULL OR duree_min > 0),
    CONSTRAINT uq_planning_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_planning_ruche
        FOREIGN KEY (ruche_id, tenant_id) REFERENCES ruche (id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT fk_planning_agent
        FOREIGN KEY (agent_id, tenant_id) REFERENCES agent (id, tenant_id) ON DELETE RESTRICT,
    CONSTRAINT fk_planning_superviseur
        FOREIGN KEY (superviseur_id, tenant_id)
        REFERENCES agent (id, tenant_id) ON DELETE SET NULL (superviseur_id)
);

CREATE INDEX ix_planning_ruche ON planning (ruche_id);

ALTER TABLE planning ENABLE ROW LEVEL SECURITY;
ALTER TABLE planning FORCE  ROW LEVEL SECURITY;
CREATE POLICY p_planning_tenant ON planning
    USING      (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));

-- === Visite ================================================================
-- US-009 : visite realisee et rapport. Peut decouler d'un planning approuve
-- (optionnel) ou etre saisie directement.
CREATE TABLE visite (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id            TEXT        NOT NULL,
    ruche_id             BIGINT      NOT NULL,
    agent_id             BIGINT      NOT NULL,
    planning_id          BIGINT,
    date_visite          DATE        NOT NULL,
    heure_visite         TIME,
    duree_min            INT,
    raison               VARCHAR(20) NOT NULL DEFAULT 'controle',
    constatations        TEXT,
    actions_prevues      TEXT,
    actions_effectuees   TEXT,
    recommandations      TEXT,
    effectif_qualitatif  VARCHAR(10),
    etat_sante           VARCHAR(10),
    productivite         INT,
    cree_le              TIMESTAMPTZ NOT NULL DEFAULT now(),
    maj_le               TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_visite_raison CHECK (raison IN
        ('controle', 'recolte', 'traitement', 'nourrissage', 'division', 'autre')),
    CONSTRAINT ck_visite_effectif CHECK (effectif_qualitatif IS NULL
        OR effectif_qualitatif IN ('faible', 'moyen', 'fort')),
    CONSTRAINT ck_visite_sante CHECK (etat_sante IS NULL
        OR etat_sante IN ('bon', 'moyen', 'mauvais')),
    CONSTRAINT ck_visite_productivite CHECK (productivite IS NULL OR productivite BETWEEN 1 AND 3),
    CONSTRAINT ck_visite_duree CHECK (duree_min IS NULL OR duree_min > 0),
    CONSTRAINT uq_visite_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_visite_ruche
        FOREIGN KEY (ruche_id, tenant_id) REFERENCES ruche (id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT fk_visite_agent
        FOREIGN KEY (agent_id, tenant_id) REFERENCES agent (id, tenant_id) ON DELETE RESTRICT,
    CONSTRAINT fk_visite_planning
        FOREIGN KEY (planning_id, tenant_id)
        REFERENCES planning (id, tenant_id) ON DELETE SET NULL (planning_id)
);

CREATE INDEX ix_visite_ruche ON visite (ruche_id);

ALTER TABLE visite ENABLE ROW LEVEL SECURITY;
ALTER TABLE visite FORCE  ROW LEVEL SECURITY;
CREATE POLICY p_visite_tenant ON visite
    USING      (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));

-- === Photo =================================================================
-- US-010 / US-028 : photos d'inspection attachees a un rapport de visite. On
-- stocke la reference (url) et une legende ; le stockage binaire est externe.
CREATE TABLE photo (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id  TEXT         NOT NULL,
    visite_id  BIGINT       NOT NULL,
    url        VARCHAR(500) NOT NULL,
    legende    VARCHAR(200),
    cree_le    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_photo_url_non_vide CHECK (length(trim(url)) > 0),
    CONSTRAINT uq_photo_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_photo_visite
        FOREIGN KEY (visite_id, tenant_id) REFERENCES visite (id, tenant_id) ON DELETE CASCADE
);

CREATE INDEX ix_photo_visite ON photo (visite_id);

ALTER TABLE photo ENABLE ROW LEVEL SECURITY;
ALTER TABLE photo FORCE  ROW LEVEL SECURITY;
CREATE POLICY p_photo_tenant ON photo
    USING      (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));

CREATE TRIGGER tg_planning_maj BEFORE UPDATE ON planning
    FOR EACH ROW EXECUTE FUNCTION zumm_touch_maj_le();
CREATE TRIGGER tg_visite_maj BEFORE UPDATE ON visite
    FOR EACH ROW EXECUTE FUNCTION zumm_touch_maj_le();
