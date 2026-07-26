-- ===========================================================================
-- V16 — Portee d'autorisation par affectation d'agent (SPRINT-16, US-057)
--
-- Ce que ferme cette migration : la RLS isolait les EXPLOITATIONS entre elles
-- (ADR-001), pas les agents A L'INTERIEUR d'une exploitation. Un apiculteur
-- saisonnier, un stagiaire, un compte compromis voyaient donc l'integralite du
-- parc — toutes les ruches, tous les sites, toutes les positions.
--
-- C'est l'ecart signale depuis le SPRINT-12 dans `PolitiquePositions` : l'arrondi
-- des coordonnees limite ce qu'un profil non proprietaire peut LIRE d'un site,
-- mais ne l'empeche pas de tous les enumerer.
--
-- ---------------------------------------------------------------------------
-- Ou la regle est posee, et pourquoi
--
-- Dans le SGBD, comme l'isolation de tenant. La poser dans les services
-- reviendrait a ajouter un `WHERE` a chaque requete et a esperer que personne ne
-- l'oublie — precisement le mode de defaillance que la RLS a ete introduite pour
-- rendre impossible. Un oubli de filtre applicatif ne peut alors plus devenir une
-- fuite.
--
-- ---------------------------------------------------------------------------
-- La regle
--
--   responsable / admin  : toute l'exploitation. C'est leur fonction.
--   superviseur          : sa ferme d'affectation.
--   apiculteur           : les ruches dont il est responsable, et ce qui s'y
--                          rattache.
--
-- Elle est portee par deux variables de session, posees par l'application a
-- partir de l'identite authentifiee, exactement comme `app.current_tenant` :
--
--   app.portee_globale  'true' quand l'appelant voit toute l'exploitation ;
--   app.agent_courant   identifiant de l'agent, sinon.
--
-- Absence de variable = portee la plus RESTRICTIVE. Une tache planifiee ou une
-- connexion hors contexte ne voit rien, plutot que tout.
-- ===========================================================================

-- ---------------------------------------------------------------------------
-- 1. Lien entre l'identite authentifiee et la ligne `agent`
--
-- `sujet_oidc` porte le claim `sub` du jeton : identifiant STABLE de l'utilisateur
-- chez le fournisseur d'identite. L'adresse de courriel ne convient pas comme cle
-- — elle change, et un changement silencieux romprait l'affectation sans que rien
-- ne le signale.
--
-- Il reste nullable : les agents existants n'en ont pas encore. L'application
-- l'inscrit a la premiere connexion en s'appuyant sur le courriel, puis s'en sert
-- seul ensuite (liaison de compte auto-reparatrice).
-- ---------------------------------------------------------------------------
ALTER TABLE agent ADD COLUMN sujet_oidc VARCHAR(64);

-- Un sujet ne designe qu'un agent par exploitation. Partiel : les agents non
-- encore lies sont tous a NULL, et NULL n'entre pas dans un index unique partiel.
CREATE UNIQUE INDEX uq_agent_sujet_oidc
    ON agent (tenant_id, sujet_oidc)
    WHERE sujet_oidc IS NOT NULL;

COMMENT ON COLUMN agent.sujet_oidc IS
    'Claim `sub` du fournisseur d''identite : lien stable entre le compte et l''agent (US-057).';

-- ---------------------------------------------------------------------------
-- 2. Lecture des variables de portee
--
-- Fonctions plutot que sous-requetes repetees : la regle est ecrite une fois, et
-- chaque politique la cite. `STABLE` autorise le planificateur a n'evaluer
-- qu'une fois par requete.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION zumm_portee_globale() RETURNS BOOLEAN
    LANGUAGE sql STABLE AS $$
    SELECT coalesce(current_setting('app.portee_globale', true), 'false') = 'true';
$$;

CREATE OR REPLACE FUNCTION zumm_agent_courant() RETURNS BIGINT
    LANGUAGE sql STABLE AS $$
    SELECT nullif(current_setting('app.agent_courant', true), '')::bigint;
$$;

COMMENT ON FUNCTION zumm_portee_globale() IS
    'Vrai si l''appelant voit toute l''exploitation (responsable, admin, machine).';
COMMENT ON FUNCTION zumm_agent_courant() IS
    'Agent de l''appelant, ou NULL. NULL + portee non globale = ne voit rien.';

-- ---------------------------------------------------------------------------
-- 3. Politiques : le tenant ET la portee
--
-- Chaque politique conserve sa condition de tenant, inchangee, et lui ajoute la
-- portee. Les deux se cumulent : la portee ne peut jamais elargir au-dela du
-- tenant.
--
-- WITH CHECK reste volontairement au SEUL tenant. Un apiculteur doit pouvoir
-- CREER une visite ou une mesure sur une ruche, y compris juste avant qu'elle ne
-- lui soit affectee ; c'est la LECTURE qui trahit le parc, pas l'ecriture. Un
-- WITH CHECK aligne sur la portee rendrait par ailleurs invisible toute ligne
-- creee puis immediatement relue.
-- ---------------------------------------------------------------------------

-- === Ruche : affectation directe ===========================================
DROP POLICY p_ruche_tenant ON ruche;
CREATE POLICY p_ruche_tenant ON ruche
    USING (tenant_id = current_setting('app.current_tenant', true)
           AND (zumm_portee_globale()
                OR agent_responsable_id = zumm_agent_courant()))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));

CREATE INDEX IF NOT EXISTS ix_ruche_agent_responsable
    ON ruche (agent_responsable_id);

-- === Visite, planning, tache : l'agent est porte par la ligne ==============
DROP POLICY p_visite_tenant ON visite;
CREATE POLICY p_visite_tenant ON visite
    USING (tenant_id = current_setting('app.current_tenant', true)
           AND (zumm_portee_globale() OR agent_id = zumm_agent_courant()))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));

DROP POLICY p_planning_tenant ON planning;
CREATE POLICY p_planning_tenant ON planning
    USING (tenant_id = current_setting('app.current_tenant', true)
           AND (zumm_portee_globale() OR agent_id = zumm_agent_courant()))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));

-- `tache.agent_id` est NULLABLE : une tache non assignee n'appartient a personne
-- en particulier et reste visible de tous — c'est une corbeille commune, pas une
-- fuite.
DROP POLICY p_tache_tenant ON tache;
CREATE POLICY p_tache_tenant ON tache
    USING (tenant_id = current_setting('app.current_tenant', true)
           AND (zumm_portee_globale()
                OR agent_id = zumm_agent_courant()
                OR agent_id IS NULL))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));

-- === Site : visible s'il porte une ruche de l'agent ========================
-- C'est LA table sensible : la liste des sites est la carte des ruchers.
-- La sous-requete s'appuie sur `ix_ruche_site` et sur le nouvel index
-- d'affectation ; elle n'est evaluee que pour les appelants non globaux.
DROP POLICY p_site_tenant ON site;
CREATE POLICY p_site_tenant ON site
    USING (tenant_id = current_setting('app.current_tenant', true)
           AND (zumm_portee_globale()
                OR EXISTS (SELECT 1 FROM ruche r
                           WHERE r.site_id = site.id
                             AND r.tenant_id = site.tenant_id
                             AND r.agent_responsable_id = zumm_agent_courant())))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));

-- === Mesure : suit la ruche ================================================
-- Sans cela, un agent pourrait lire la serie de n'importe quelle ruche en
-- devinant un identifiant — et le poids d'une ruche dit si elle vaut d'etre volee.
DROP POLICY p_mesure_tenant ON mesure;
CREATE POLICY p_mesure_tenant ON mesure
    USING (tenant_id = current_setting('app.current_tenant', true)
           AND (zumm_portee_globale()
                OR EXISTS (SELECT 1 FROM ruche r
                           WHERE r.id = mesure.ruche_id
                             AND r.tenant_id = mesure.tenant_id
                             AND r.agent_responsable_id = zumm_agent_courant())))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));

-- ---------------------------------------------------------------------------
-- Ce qui n'est PAS filtre par affectation, et pourquoi
--
--   fermier, ferme : le referentiel d'organisation. Un agent doit savoir pour qui
--                    il travaille ; ces tables ne portent aucune position.
--   recolte, reine, photo, compartiment : rattaches a une ruche deja filtree —
--                    les atteindre suppose d'en connaitre l'identifiant, et leur
--                    contenu ne revele pas d'emplacement.
--   audit, alerte  : deja restreints par le RBAC applicatif.
--
-- Ces choix sont a rejuger si l'une de ces tables se met a porter une donnee de
-- localisation.
-- ---------------------------------------------------------------------------
