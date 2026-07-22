-- ===========================================================================
-- V2 — Modele metier de reference (SPRINT-01)
--
-- Introduit la hierarchie Fermier -> Ferme -> Site et l'entite Agent, socle des
-- US-001, US-002, US-003, US-005 et US-006. Derive du dictionnaire de donnees
-- (chapitre 5) et de l'annexe A.
--
-- Multi-tenant (ADR-001, arbitre le 22/07) : chaque table metier porte un
-- `tenant_id` NON NUL et une politique RLS PostgreSQL. L'isolation est garantie
-- par le SGBD, pas par la discipline applicative — un oubli de filtre dans un
-- repository ne peut pas devenir une fuite inter-tenant.
--
-- L'entite factice `ping` du walking skeleton (V1) est CONSERVEE : elle prouve
-- toujours la chaine de bout en bout et ne porte pas de tenant. Sa suppression
-- est une tache de nettoyage du SPRINT-01, pas de cette migration de fondation.
-- ===========================================================================

-- ---------------------------------------------------------------------------
-- Convention RLS commune
--
-- Le tenant courant est lu dans la variable de session `app.current_tenant`,
-- positionnee par l'application a partir du claim JWT (cf. TenantConnectionProvider).
-- `current_setting(..., true)` renvoie NULL si la variable est absente : une
-- requete hors contexte tenant ne voit alors AUCUNE ligne (refus par defaut).
--
-- FORCE ROW LEVEL SECURITY est indispensable : le proprietaire de la table
-- contourne sinon la RLS.
--
-- ⚠️ DURCISSEMENT REQUIS EN PRODUCTION : un SUPERUTILISATEUR contourne la RLS
-- MEME avec FORCE. L'utilisateur applicatif par defaut du conteneur et de
-- `docker-compose` (`zumm`) est superutilisateur : la couche RLS est donc INERTE
-- tant que l'application s'y connecte. Pour que la garde SGBD soit reelle,
-- l'application doit tourner avec un role NON-superutilisateur (les migrations
-- Flyway, elles, gardent un role proprietaire). Tache tracee dans
-- docs/SPRINT-01-FONDATION.md ; ModeleMetierIsolationIT le prouve sous SET ROLE.
-- ---------------------------------------------------------------------------

-- === Fermier ===============================================================
CREATE TABLE fermier (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id  TEXT         NOT NULL,
    nom        VARCHAR(120) NOT NULL,
    contact    VARCHAR(180),
    cree_le    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    maj_le     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_fermier_nom_non_vide CHECK (length(trim(nom)) > 0),
    -- Cible des cles etrangeres composites : garantit qu'une reference reste
    -- dans le meme tenant (cf. ferme.fermier_id ci-dessous).
    CONSTRAINT uq_fermier_id_tenant UNIQUE (id, tenant_id)
);

ALTER TABLE fermier ENABLE ROW LEVEL SECURITY;
ALTER TABLE fermier FORCE  ROW LEVEL SECURITY;
CREATE POLICY p_fermier_tenant ON fermier
    USING      (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));

-- === Ferme =================================================================
-- US-002 : une ferme est rattachee a un fermier. La cle etrangere est composite
-- (fermier_id, tenant_id) : un tenant ne peut pas rattacher sa ferme au fermier
-- d'un autre, meme si la verification FK contourne la RLS.
CREATE TABLE ferme (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id  TEXT         NOT NULL,
    nom        VARCHAR(120) NOT NULL,
    fermier_id BIGINT       NOT NULL,
    cree_le    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    maj_le     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_ferme_nom_non_vide CHECK (length(trim(nom)) > 0),
    CONSTRAINT uq_ferme_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_ferme_fermier
        FOREIGN KEY (fermier_id, tenant_id)
        REFERENCES fermier (id, tenant_id) ON DELETE RESTRICT
);

CREATE INDEX ix_ferme_fermier ON ferme (fermier_id);

ALTER TABLE ferme ENABLE ROW LEVEL SECURITY;
ALTER TABLE ferme FORCE  ROW LEVEL SECURITY;
CREATE POLICY p_ferme_tenant ON ferme
    USING      (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));

-- === Site ==================================================================
-- US-003 : emplacement geolocalise. Latitude/longitude/altitude sont stockees
-- en NUMERIC (vue metier du dictionnaire) ; une colonne PostGIS `geog` derivee
-- en est generee pour les requetes spatiales (proximite, emprise), indexee GiST.
--
-- La position exacte est une donnee sensible (risque de vol de ruches) : son
-- arrondi pour les profils non proprietaires est traite cote applicatif
-- (US-003 / RBAC, seuil `arrondi_degres_public` de ConfigZumm.ini).
CREATE TABLE site (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id            TEXT           NOT NULL,
    nom                  VARCHAR(120)   NOT NULL,
    ferme_id             BIGINT         NOT NULL,
    latitude             NUMERIC(9, 6)  NOT NULL,
    longitude            NUMERIC(9, 6)  NOT NULL,
    altitude             NUMERIC(7, 2),
    date_mise_en_oeuvre  DATE           NOT NULL,
    date_demenagement    DATE,
    date_cloture         DATE,
    cree_le              TIMESTAMPTZ    NOT NULL DEFAULT now(),
    maj_le               TIMESTAMPTZ    NOT NULL DEFAULT now(),
    -- Colonne spatiale derivee : deterministe (fonctions PostGIS immuables),
    -- donc admissible en colonne generee STORED.
    geog GEOGRAPHY(POINT, 4326) GENERATED ALWAYS AS
        (ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography) STORED,
    CONSTRAINT ck_site_nom_non_vide CHECK (length(trim(nom)) > 0),
    CONSTRAINT ck_site_latitude  CHECK (latitude  BETWEEN -90  AND 90),
    CONSTRAINT ck_site_longitude CHECK (longitude BETWEEN -180 AND 180),
    -- Bornes larges : point le plus bas emerge (mer Morte ~ -430 m) au plus haut.
    CONSTRAINT ck_site_altitude  CHECK (altitude IS NULL OR altitude BETWEEN -500 AND 9000),
    -- US-006 : coherence du cycle de vie (dictionnaire : demenagement et cloture
    -- optionnels, mais jamais anterieurs a la mise en oeuvre).
    CONSTRAINT ck_site_demenagement_apres_mise_en_oeuvre
        CHECK (date_demenagement IS NULL OR date_demenagement >= date_mise_en_oeuvre),
    CONSTRAINT ck_site_cloture_apres_mise_en_oeuvre
        CHECK (date_cloture IS NULL OR date_cloture >= date_mise_en_oeuvre),
    CONSTRAINT uq_site_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_site_ferme
        FOREIGN KEY (ferme_id, tenant_id)
        REFERENCES ferme (id, tenant_id) ON DELETE RESTRICT
);

CREATE INDEX ix_site_ferme ON site (ferme_id);
CREATE INDEX ix_site_geog  ON site USING GIST (geog);

ALTER TABLE site ENABLE ROW LEVEL SECURITY;
ALTER TABLE site FORCE  ROW LEVEL SECURITY;
CREATE POLICY p_site_tenant ON site
    USING      (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));

-- === Agent =================================================================
-- US-005 : agent et role. Le role est contraint a l'enumeration du cahier ;
-- il est aussi porte par Keycloak (realm-zumm.json) — les deux doivent rester
-- alignes. Un agent appartient a une ferme (optionnel pour un role transverse
-- comme administrateur).
CREATE TABLE agent (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id  TEXT         NOT NULL,
    nom        VARCHAR(120) NOT NULL,
    role       VARCHAR(20)  NOT NULL,
    ferme_id   BIGINT,
    cree_le    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    maj_le     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_agent_nom_non_vide CHECK (length(trim(nom)) > 0),
    CONSTRAINT ck_agent_role
        CHECK (role IN ('apiculteur', 'superviseur', 'responsable', 'admin')),
    CONSTRAINT uq_agent_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_agent_ferme
        FOREIGN KEY (ferme_id, tenant_id)
        REFERENCES ferme (id, tenant_id) ON DELETE SET NULL (ferme_id)
);

CREATE INDEX ix_agent_ferme ON agent (ferme_id);

ALTER TABLE agent ENABLE ROW LEVEL SECURITY;
ALTER TABLE agent FORCE  ROW LEVEL SECURITY;
CREATE POLICY p_agent_tenant ON agent
    USING      (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));

-- === Horodatage de mise a jour =============================================
-- `maj_le` reflete la derniere modification, sans confier cette responsabilite
-- au code applicatif (une mise a jour SQL directe doit aussi la respecter).
CREATE OR REPLACE FUNCTION zumm_touch_maj_le() RETURNS trigger AS $$
BEGIN
    NEW.maj_le := now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_fermier_maj BEFORE UPDATE ON fermier
    FOR EACH ROW EXECUTE FUNCTION zumm_touch_maj_le();
CREATE TRIGGER tg_ferme_maj BEFORE UPDATE ON ferme
    FOR EACH ROW EXECUTE FUNCTION zumm_touch_maj_le();
CREATE TRIGGER tg_site_maj BEFORE UPDATE ON site
    FOR EACH ROW EXECUTE FUNCTION zumm_touch_maj_le();
CREATE TRIGGER tg_agent_maj BEFORE UPDATE ON agent
    FOR EACH ROW EXECUTE FUNCTION zumm_touch_maj_le();

COMMENT ON COLUMN fermier.tenant_id IS 'Exploitation proprietaire (ADR-001) — pose par l''application via @TenantId, filtre par RLS.';
COMMENT ON COLUMN site.geog IS 'Point WGS84 derive de (longitude, latitude) pour les requetes spatiales PostGIS.';
