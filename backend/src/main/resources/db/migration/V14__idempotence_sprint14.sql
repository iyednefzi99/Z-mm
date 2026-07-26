-- ===========================================================================
-- V14 — Idempotence des mutations (SPRINT-14, US-055)
--
-- Le probleme, concret : la PWA met en file les mutations faites hors ligne
-- (US-011) et les rejoue au retour du reseau. Or `fetch` echoue AUSSI quand la
-- requete est bien arrivee et que seule la reponse s'est perdue — coupure au
-- mauvais moment, tunnel, batterie. Le client, lui, ne peut pas faire la
-- difference : il rejoue, et la visite est creee deux fois.
--
-- Aucune contrainte metier ne l'empeche : deux visites de la meme ruche le meme
-- jour sont parfaitement legitimes. Seul le client sait que ces deux requetes
-- sont la MEME intention — d'ou une cle qu'il fournit, et que le serveur retient.
--
-- La reponse memorisee est renvoyee telle quelle au rejeu : le client obtient le
-- meme identifiant de ressource qu'a la premiere fois, et son etat local reste
-- juste.
-- ===========================================================================

CREATE TABLE requete_idempotente (
    tenant_id   TEXT         NOT NULL,
    cle         VARCHAR(120) NOT NULL,
    -- Empreinte de la requete (methode + chemin + corps). Une meme cle reutilisee
    -- pour une requete DIFFERENTE est un bug client, pas un rejeu : on le signale
    -- (409) au lieu de renvoyer silencieusement la reponse d'une autre operation.
    empreinte   CHAR(64)     NOT NULL,
    statut      SMALLINT     NOT NULL,
    corps       TEXT,
    cree_le     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_requete_idempotente PRIMARY KEY (tenant_id, cle),
    CONSTRAINT ck_requete_idempotente_statut CHECK (statut BETWEEN 100 AND 599)
);

-- La cle est fournie par le client : elle est donc, par nature, une donnee non
-- fiable. La RLS garantit qu'une cle devinee ne peut pas servir a lire la reponse
-- memorisee d'une AUTRE exploitation.
ALTER TABLE requete_idempotente ENABLE ROW LEVEL SECURITY;
ALTER TABLE requete_idempotente FORCE  ROW LEVEL SECURITY;
CREATE POLICY p_requete_idempotente_tenant ON requete_idempotente
    USING      (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));

-- Purge : l'index sert le menage periodique, pas les lectures (qui passent par la
-- cle primaire). Une entree ne vaut que le temps ou un rejeu reste plausible.
CREATE INDEX ix_requete_idempotente_cree_le ON requete_idempotente (cree_le);

COMMENT ON TABLE requete_idempotente IS
    'Reponses memorisees par cle d''idempotence (US-055). Purgeable au-dela de 7 jours.';
