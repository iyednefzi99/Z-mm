-- ===========================================================================
-- V17 — Sessions serveur persistees (SPRINT-17)
--
-- Le BFF (ADR-006) a deplace les jetons du navigateur vers le serveur. Le gain de
-- securite est net, mais il introduit une dette que l'ADR signalait lui-meme :
-- une session en MEMOIRE interdit de repliquer le back-end, et fait perdre toutes
-- les sessions a chaque redemarrage — y compris un simple deploiement.
--
-- Le schema est cree ICI, par Flyway, et non par Spring Session
-- (`initialize-schema: never`). Flyway est seul proprietaire du schema
-- (`ddl-auto: none`) : une seconde autorite sur les tables reintroduirait le
-- desordre que cette regle existe pour eviter, et rendrait les migrations
-- non reproductibles.
--
-- Structure imposee par Spring Session JDBC : noms de tables, de colonnes et
-- d'index sont ceux qu'attend son implementation. On ne les adapte pas.
-- ===========================================================================

CREATE TABLE SPRING_SESSION (
    PRIMARY_ID            CHAR(36)     NOT NULL,
    SESSION_ID            CHAR(36)     NOT NULL,
    CREATION_TIME         BIGINT       NOT NULL,
    LAST_ACCESS_TIME      BIGINT       NOT NULL,
    MAX_INACTIVE_INTERVAL INT          NOT NULL,
    EXPIRY_TIME           BIGINT       NOT NULL,
    PRINCIPAL_NAME        VARCHAR(100),
    CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)
);

CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID);
CREATE INDEX SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME);
CREATE INDEX SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME);

CREATE TABLE SPRING_SESSION_ATTRIBUTES (
    SESSION_PRIMARY_ID CHAR(36)     NOT NULL,
    ATTRIBUTE_NAME     VARCHAR(200) NOT NULL,
    ATTRIBUTE_BYTES    BYTEA        NOT NULL,
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID)
        REFERENCES SPRING_SESSION (PRIMARY_ID) ON DELETE CASCADE
);

-- ---------------------------------------------------------------------------
-- Droits du role applicatif
--
-- La session est ecrite a chaque requete authentifiee : le role applicatif a
-- besoin du DML complet sur ces deux tables. Les migrations, elles, tournent avec
-- le proprietaire.
-- ---------------------------------------------------------------------------
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${role_app}') THEN
        EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON SPRING_SESSION TO %I',
                       '${role_app}');
        EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON SPRING_SESSION_ATTRIBUTES TO %I',
                       '${role_app}');
    END IF;
END
$$;

-- ---------------------------------------------------------------------------
-- Pourquoi ces tables ne portent NI tenant_id NI politique RLS
--
-- Une session n'est pas une donnee metier : elle appartient a l'infrastructure
-- d'authentification, et elle est CREEE avant qu'aucun tenant ne soit connu — le
-- tenant se lit dans le jeton, donc apres l'ouverture de session. Lui imposer la
-- convention multi-tenant rendrait la connexion impossible.
--
-- L'isolation est assuree autrement, et suffisamment : l'identifiant de session
-- est un secret aleatoire de 36 caracteres, transmis par un cookie HttpOnly, et
-- son contenu n'est lisible que par l'application.
-- ---------------------------------------------------------------------------

COMMENT ON TABLE SPRING_SESSION IS
    'Sessions serveur du BFF (ADR-006). Hors perimetre multi-tenant : creees avant que le tenant soit connu.';
