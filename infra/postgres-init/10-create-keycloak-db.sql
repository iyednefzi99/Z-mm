-- ===========================================================================
-- Isolation de Keycloak : base de données dédiée
--
-- Keycloak et l'application partageaient auparavant la base `zumm` et son schéma
-- `public`. Keycloak y crée ~88 tables (Liquibase) ; selon l'ordre de démarrage,
-- Flyway trouvait alors un schéma `public` non vide sans table d'historique et
-- refusait de migrer (« Found non-empty schema(s) "public" but no schema history
-- table »), faisant échouer un déploiement neuf.
--
-- On dédie donc une base `keycloak` au fournisseur d'identité : la base `zumm`
-- reste la propriété exclusive de Flyway/JPA, et Keycloak gère la sienne.
--
-- Ce script est exécuté UNE SEULE FOIS, à l'initialisation du volume PostgreSQL
-- (répertoire /docker-entrypoint-initdb.d, sur volume vierge uniquement), par le
-- rôle propriétaire POSTGRES_USER (zumm), contre la base POSTGRES_DB (zumm).
-- ===========================================================================

CREATE DATABASE keycloak OWNER zumm;
