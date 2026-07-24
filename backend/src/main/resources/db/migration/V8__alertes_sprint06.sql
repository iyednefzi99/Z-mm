-- ===========================================================================
-- V8 — Alertes de seuils (SPRINT-06, US-018)
--
-- Alerte levee lorsqu'une mesure ingeree (US-017) franchit un seuil de
-- ConfigZumm.ini. Une alerte reste OUVERTE tant que l'indicateur n'est pas
-- revenu dans la bande d'hysteresis (anti-rebond) ; elle est alors fermee. On ne
-- garde qu'une alerte ouverte a la fois par (ruche, indicateur) — l'index partiel
-- unique le garantit. Multi-tenant (ADR-001) : tenant_id + RLS, FK composites.
-- ===========================================================================

CREATE TABLE alerte (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id            TEXT          NOT NULL,
    ruche_id             BIGINT        NOT NULL,
    type_indicateur      VARCHAR(20)   NOT NULL,
    niveau               VARCHAR(20)   NOT NULL,
    message              VARCHAR(300)  NOT NULL,
    valeur_declenchement NUMERIC(12, 4) NOT NULL,
    ouverte              BOOLEAN       NOT NULL DEFAULT true,
    ouverte_le           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    fermee_le            TIMESTAMPTZ,
    cree_le              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    maj_le               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_alerte_indicateur CHECK (type_indicateur IN
        ('poids', 'temperature', 'humidite', 'activite')),
    CONSTRAINT ck_alerte_niveau CHECK (niveau IN ('attention', 'critique')),
    CONSTRAINT uq_alerte_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_alerte_ruche
        FOREIGN KEY (ruche_id, tenant_id) REFERENCES ruche (id, tenant_id) ON DELETE CASCADE
);

-- Au plus une alerte OUVERTE par (ruche, indicateur) : garantit l'anti-rebond au
-- niveau du SGBD, en complement de la logique d'hysteresis applicative.
CREATE UNIQUE INDEX uq_alerte_ouverte
    ON alerte (ruche_id, type_indicateur) WHERE ouverte;

ALTER TABLE alerte ENABLE ROW LEVEL SECURITY;
ALTER TABLE alerte FORCE  ROW LEVEL SECURITY;
CREATE POLICY p_alerte_tenant ON alerte
    USING      (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));

CREATE TRIGGER tg_alerte_maj BEFORE UPDATE ON alerte
    FOR EACH ROW EXECUTE FUNCTION zumm_touch_maj_le();

COMMENT ON TABLE alerte IS
    'Alertes de seuils avec hysteresis (US-018). Une seule ouverte par ruche+indicateur.';
