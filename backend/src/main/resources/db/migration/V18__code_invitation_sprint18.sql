-- ===========================================================================
-- V18 — Code d'invitation : rattachement d'un compte cree depuis l'application
--       (SPRINT-18, ADR-009)
--
-- Ce que cette migration rend possible : l'inscription DEPUIS Zumm. Jusqu'ici,
-- un compte naissait dans la console d'administration de Keycloak, ou un
-- administrateur posait a la main l'attribut `tenant_id` et le role metier.
--
-- Le probleme que cela pose des qu'un formulaire public cree des comptes :
-- `tenant_id` est OBLIGATOIRE dans le jeton (TenantFilter repond 403 sans lui).
-- Un compte cree sans rattachement serait donc un compte fantome — il existe,
-- il peut se connecter, et il n'atteint aucun ecran. Pire, laisser le formulaire
-- CHOISIR son exploitation reviendrait a offrir l'entree de n'importe quel
-- cheptel a qui devine un identifiant de tenant.
--
-- D'ou le code d'invitation : c'est le responsable qui decide qui rejoint son
-- exploitation, et le code est le support de cette decision. Sans code valide,
-- pas de compte — l'inscription n'est pas ouverte a tout venant, ce qui est la
-- regle attendue pour des donnees qui incluent la position des ruchers.
--
-- ---------------------------------------------------------------------------
-- Le point delicat : ce code se lit AVANT toute authentification
--
-- Au moment ou l'on resout un code, personne n'est connecte. Il n'y a donc ni
-- jeton, ni `app.current_tenant` pose — et une table sous RLS est, dans ces
-- conditions, parfaitement vide pour l'appelant. La resolution ne peut donc pas
-- passer par un SELECT ordinaire.
--
-- Elle passe par une fonction `SECURITY DEFINER`, qui s'execute avec les droits
-- de son proprietaire et contourne donc la politique. C'est une derogation, et
-- elle est etroite par construction : la fonction ne prend qu'un code, ne rend
-- QUE le tenant et le role associes, et ne rend rien si le code est inconnu,
-- expire ou epuise. Elle n'expose aucun moyen d'enumerer les exploitations.
-- ===========================================================================

CREATE TABLE code_invitation (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id       TEXT         NOT NULL,
    -- Le code est UNIQUE GLOBALEMENT, et non par exploitation : il est resolu
    -- sans contexte de tenant, donc rien ne permettrait de departager deux
    -- exploitations portant le meme code.
    code            VARCHAR(32)  NOT NULL,
    -- Role metier attribue au compte cree. Restreint aux roles humains sans
    -- pouvoir d'administration : une invitation ne fabrique pas d'admin.
    role            VARCHAR(20)  NOT NULL,
    -- Une invitation peut servir a une equipe. `utilisations_max = 1` reste le
    -- defaut : le cas courant est une personne, un code.
    utilisations_max INTEGER     NOT NULL DEFAULT 1,
    utilisations    INTEGER      NOT NULL DEFAULT 0,
    -- Une invitation sans fin est une porte laissee ouverte : un code egare il y
    -- a deux saisons ne doit plus rien ouvrir.
    expire_le       TIMESTAMPTZ  NOT NULL,
    cree_par        VARCHAR(120),
    cree_le         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    maj_le          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_code_invitation_code UNIQUE (code),
    CONSTRAINT uq_code_invitation_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT ck_code_invitation_role
        CHECK (role IN ('apiculteur', 'superviseur', 'responsable')),
    CONSTRAINT ck_code_invitation_utilisations
        CHECK (utilisations >= 0 AND utilisations <= utilisations_max),
    CONSTRAINT ck_code_invitation_max CHECK (utilisations_max > 0),
    -- Un code court et lu sur un papier : on impose la forme pour eviter les
    -- confusions de casse a la saisie. La normalisation est faite a l'ecriture.
    CONSTRAINT ck_code_invitation_forme CHECK (code = upper(code))
);

ALTER TABLE code_invitation ENABLE ROW LEVEL SECURITY;
ALTER TABLE code_invitation FORCE  ROW LEVEL SECURITY;

-- La politique vaut pour la GESTION des codes — les lister, en creer, les
-- revoquer depuis l'application authentifiee. La resolution a l'inscription ne
-- passe pas par la (voir plus bas).
CREATE POLICY p_code_invitation_tenant ON code_invitation
    USING      (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));

CREATE TRIGGER tg_code_invitation_maj BEFORE UPDATE ON code_invitation
    FOR EACH ROW EXECUTE FUNCTION zumm_touch_maj_le();

CREATE INDEX ix_code_invitation_tenant ON code_invitation (tenant_id);

COMMENT ON TABLE code_invitation IS
    'Invitation a rejoindre une exploitation (ADR-009). Porte le tenant_id et le role du futur compte.';
COMMENT ON COLUMN code_invitation.utilisations IS
    'Nombre de comptes deja crees avec ce code. Incremente sous verrou par zumm_reserver_invitation().';

-- ---------------------------------------------------------------------------
-- Reservation d'une invitation
--
-- Une SEULE fonction verifie ET reserve, plutot que deux appels : entre un
-- « ce code est valide » et un « je le consomme », deux inscriptions simultanees
-- passeraient toutes les deux sur la derniere place disponible. Le `UPDATE …
-- RETURNING` conditionne rend l'operation atomique — le SGBD verrouille la ligne,
-- et le second appel ne trouve plus de place.
--
-- La reservation a lieu AVANT la creation du compte chez le fournisseur
-- d'identite, et non apres : si l'on creait le compte d'abord, un echec de
-- reservation laisserait un compte sans rattachement, c'est-a-dire le compte
-- fantome que tout ceci vise a empecher. L'ordre inverse expose au risque
-- symetrique — une place perdue si la creation echoue — que l'application
-- rattrape en relachant la reservation (`zumm_relacher_invitation`).
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION zumm_reserver_invitation(p_code TEXT)
    RETURNS TABLE (tenant_id TEXT, role TEXT)
    LANGUAGE sql
    SECURITY DEFINER
    -- `search_path` fige : sans cela, un appelant pourrait interposer un schema
    -- portant une table `code_invitation` de son cru, et la fonction s'executerait
    -- dessus avec les droits du proprietaire.
    SET search_path = pg_catalog, public
    AS $$
    UPDATE code_invitation
       SET utilisations = utilisations + 1
     WHERE code = upper(trim(p_code))
       AND expire_le > now()
       AND utilisations < utilisations_max
    RETURNING code_invitation.tenant_id, code_invitation.role;
$$;

CREATE OR REPLACE FUNCTION zumm_relacher_invitation(p_code TEXT)
    RETURNS VOID
    LANGUAGE sql
    SECURITY DEFINER
    SET search_path = pg_catalog, public
    AS $$
    UPDATE code_invitation
       SET utilisations = greatest(utilisations - 1, 0)
     WHERE code = upper(trim(p_code));
$$;

COMMENT ON FUNCTION zumm_reserver_invitation(TEXT) IS
    'Reserve une place sur un code valide et rend (tenant_id, role). Aucune ligne si le code est inconnu, expire ou epuise.';
COMMENT ON FUNCTION zumm_relacher_invitation(TEXT) IS
    'Rend la place reservee quand la creation du compte a echoue.';

-- Ces deux fonctions sont le SEUL chemin de lecture hors tenant : la table
-- elle-meme reste inaccessible au role applicatif en dehors de sa politique.
REVOKE ALL ON FUNCTION zumm_reserver_invitation(TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION zumm_relacher_invitation(TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION zumm_reserver_invitation(TEXT) TO ${role_app};
GRANT EXECUTE ON FUNCTION zumm_relacher_invitation(TEXT) TO ${role_app};
