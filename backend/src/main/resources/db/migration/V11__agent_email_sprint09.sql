-- ===========================================================================
-- SPRINT-09 — Adresse e-mail de l'agent
--
-- Support des notifications d'alerte par e-mail (US-041) : l'agent responsable
-- d'une ruche reçoit un message quand une alerte de seuil s'ouvre sur celle-ci.
-- Colonne facultative : un agent sans e-mail n'est simplement pas notifié.
-- ===========================================================================

ALTER TABLE agent ADD COLUMN email VARCHAR(180);

ALTER TABLE agent
    ADD CONSTRAINT ck_agent_email
    CHECK (email IS NULL OR email ~ '^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$');
