-- ===========================================================================
-- V25 — Preference de notification et jeu de demonstration (SPRINT-25, lot J)
--
-- Le lot J du plan de couverture rassemble sept lignes courtes et sans lien
-- technique entre elles — QR par ruche, NFC, planche d'etiquettes, mot de passe
-- en libre-service, interface progressive, notifications par utilisateur, jeu de
-- demonstration. Cinq sont purement front ou relevent du fournisseur
-- d'identite. Deux demandent de la donnee, et elles sont ici.
--
-- ---------------------------------------------------------------------------
-- 1. Les notifications, par agent et non plus pour tout le monde
--
-- `zumm.notifications.email.enabled` est une propriete GLOBALE : elle allume ou
-- eteint le courriel pour l'exploitation entiere. BeeKeepPal conseille a ses
-- utilisateurs de desactiver les notifications « pour eviter la fuite
-- d'adresses » (§13) — un conseil qui n'a de sens que s'il s'adresse a UNE
-- personne. Ici, l'apiculteur qui ne veut pas etre reveille par une alerte de
-- poids devait la couper pour ses collegues aussi.
--
-- Defaut a VRAI, et c'est deliberé : une alerte de seuil est ouverte parce que
-- quelque chose ne va pas dans une ruche. Un defaut a faux ferait taire, au
-- premier deploiement, exactement ce que le produit promet de signaler — et
-- personne ne saurait qu'il faut aller le rallumer.
--
-- ---------------------------------------------------------------------------
-- 2. Le jeu de demonstration, et pourquoi il se TRACE
--
-- BeeLog Digital conseille de « configurer une seule ruche test avant de
-- basculer l'exploitation ». Le depot a bien un `infra/seed-demo.sql`, mais il
-- s'execute cote exploitant, avec psql, sur le tenant de developpement : rien
-- qu'un utilisateur puisse charger — et surtout RETIRER — depuis l'application.
--
-- Le mot qui compte dans la ligne du §13 est « reversible ». Une demonstration
-- qu'on ne peut pas defaire n'est pas une demonstration, c'est une pollution :
-- l'exploitation garderait pour toujours cinq ruches fictives melees aux
-- vraies, et personne n'oserait plus supprimer quoi que ce soit de peur de se
-- tromper de cible.
--
-- D'ou cette table : elle note CE QUE le chargement a cree, ligne par ligne. La
-- purge ne supprime alors que cela — jamais une donnee que l'utilisateur aurait
-- saisie entre-temps, meme si elle lui ressemble. Deviner par le nom (« tout ce
-- qui commence par Demo ») aurait detruit le rucher d'un apiculteur qui aurait
-- eu le tort d'appeler le sien « Demonstration ».
--
-- L'ordre de suppression compte : `ruche` casse en cascade ses visites, taches,
-- recoltes et mesures, mais `site` et `ferme` sont en ON DELETE RESTRICT. La
-- colonne `ordre` porte donc la sequence de purge, du plus dependant au moins
-- dependant, plutot que de la laisser au hasard d'un tri par identifiant.
-- ===========================================================================


-- === 1. Preference de notification, par agent ==============================

ALTER TABLE agent ADD COLUMN notifications_email BOOLEAN NOT NULL DEFAULT TRUE;

COMMENT ON COLUMN agent.notifications_email IS
    'L''agent accepte-t-il les courriels d''alerte et de tache critique '
    '(SPRINT-25) ? S''ajoute au reglage global, ne le remplace pas : les deux '
    'doivent etre vrais pour qu''un message parte.';


-- === 2. Trace du jeu de demonstration ======================================

CREATE TABLE jeu_demonstration (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id  TEXT        NOT NULL,
    -- Nom de la table visee. VARCHAR et non enum : la liste des entites du jeu
    -- changera plus souvent que le schema, et une valeur inconnue au moment de
    -- la purge doit se voir dans les donnees, pas faire echouer une migration.
    entite     VARCHAR(40) NOT NULL,
    entite_id  BIGINT      NOT NULL,
    -- Ordre de purge : du plus dependant au moins dependant. `ruche` casse en
    -- cascade ce qui pend sous elle, mais `site` et `ferme` sont en RESTRICT.
    ordre      INT         NOT NULL,
    cree_le    TIMESTAMPTZ NOT NULL DEFAULT now(),
    maj_le     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_jeu_demo_entite CHECK (entite IN
        ('ruche', 'site', 'agent', 'ferme', 'fermier')),
    CONSTRAINT uq_jeu_demo_id_tenant UNIQUE (id, tenant_id),
    -- Une meme ligne ne se trace pas deux fois : le chargement est refuse tant
    -- qu'un jeu est deja en place, mais l'index le garantit meme si cette regle
    -- applicative venait a sauter.
    CONSTRAINT uq_jeu_demo_cible UNIQUE (tenant_id, entite, entite_id)
);

CREATE INDEX ix_jeu_demo_purge ON jeu_demonstration (tenant_id, ordre);

ALTER TABLE jeu_demonstration ENABLE ROW LEVEL SECURITY;
ALTER TABLE jeu_demonstration FORCE  ROW LEVEL SECURITY;

-- Portee du seul tenant, sans restriction d'agent : un jeu de demonstration
-- appartient a l'exploitation, pas a la personne qui l'a charge. C'est meme le
-- point — celui qui le trouve encombrant doit pouvoir le retirer.
CREATE POLICY p_jeu_demo_tenant ON jeu_demonstration
    USING (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));

CREATE TRIGGER tg_jeu_demo_maj BEFORE UPDATE ON jeu_demonstration
    FOR EACH ROW EXECUTE FUNCTION zumm_touch_maj_le();

COMMENT ON TABLE jeu_demonstration IS
    'Ce que le chargement de demonstration a cree (SPRINT-25). La purge ne '
    'supprime que ces lignes : jamais une donnee saisie par l''utilisateur, '
    'meme si elle porte le meme nom.';
