-- ===========================================================================
-- V3 — Role applicatif non-superutilisateur (durcissement RLS, ADR-001)
--
-- La couche RLS posee en V2 est INERTE si l'application se connecte en
-- superutilisateur : un superutilisateur contourne toujours la RLS, meme sous
-- FORCE ROW LEVEL SECURITY. L'utilisateur `zumm` cree par le conteneur et par
-- docker-compose est superutilisateur ; il reste le proprietaire du schema et
-- l'utilisateur des MIGRATIONS Flyway (qui exigent des droits DDL), mais
-- l'APPLICATION doit desormais se connecter avec le role dedie cree ici.
--
-- `${role_app}` et `${motdepasse_app}` sont des placeholders Flyway (definis dans
-- application.yml, surchargeables par l'environnement) : aucun secret n'est
-- versionne. En production, `DB_APP_PASSWORD` alimente a la fois ce mot de passe
-- et celui de la source de donnees applicative (cf. docker-compose.yml).
--
-- Ce role :
--   * est NON-superutilisateur → soumis a la RLS ;
--   * n'est PAS proprietaire des tables → ne les contourne pas non plus ;
--   * ne recoit que le DML (SELECT/INSERT/UPDATE/DELETE), jamais le DDL.
-- ===========================================================================

-- Creation idempotente : les roles sont globaux au cluster, donc une base soeur
-- peut l'avoir deja cree. On ne touche pas au mot de passe d'un role existant.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${role_app}') THEN
        EXECUTE format(
            'CREATE ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT PASSWORD %L',
            '${role_app}', '${motdepasse_app}');
    END IF;
END $$;

-- Droit de se connecter a la base courante (sans coder son nom en dur).
DO $$
BEGIN
    EXECUTE format('GRANT CONNECT ON DATABASE %I TO %I', current_database(), '${role_app}');
END $$;

-- Acces au schema et DML sur les tables et sequences existantes.
GRANT USAGE ON SCHEMA public TO ${role_app};
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO ${role_app};
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO ${role_app};

-- Memes droits, automatiquement, sur les tables et sequences des migrations a
-- venir — creees par le proprietaire `zumm`, donc couvertes par SES defauts.
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ${role_app};
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO ${role_app};
