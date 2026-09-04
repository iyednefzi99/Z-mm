-- ===========================================================================
-- V22 — Regles, taches engendrees et carence opposable (SPRINT-22, lot A)
--
-- Le lot A du plan de couverture (`docs/PLAN-COUVERTURE-ECARTS.md`) : onze
-- lignes du document d'ecart, et **presque aucune donnee nouvelle**. C'est tout
-- son interet — le SPRINT-20 a livre les colonnes d'observation, le SPRINT-21
-- le terrain, et personne n'en tire encore de conclusion.
--
-- Ce que cette migration ajoute se compte sur les doigts :
--   1. `tache`   — priorite, categorie, origine, et la CLE qui empeche une
--                  regle d'engendrer dix fois la meme tache ;
--   2. `recolte` — la trace d'un forcage de carence.
--
-- Ce qu'elle n'ajoute PAS, et c'est deliberе :
--
--   * aucune table de « score ». L'indice de sante et le risque d'essaimage se
--     CALCULENT a partir des colonnes de `visite`, `traitement` et
--     `comptage_varroa`. Les stocker aurait le defaut que `ComptageVarroaService`
--     evite deja pour le taux d'infestation : une valeur figee qui ne suit plus
--     la formule quand celle-ci change, et deux verites dans la base.
--   * aucune table de « regle ». Les regles sont du CODE — elles lisent des
--     colonnes, comparent des seuils et rendent une tache. Une table de regles
--     parametrables serait un moteur d'expression a ecrire, a tester et a
--     securiser, pour un besoin que personne n'a exprime.
-- ===========================================================================


-- === 1. Priorite, categorie et origine de la tache =========================
--
-- `tache` n'avait qu'un libelle, une echeance et un booleen. Trois consequences
-- que le §7 et le §13 du document d'ecart pointent :
--   * rien ne distingue « commander des cadres » de « retirer un traitement
--     dont la carence expire aujourd'hui » ;
--   * rien ne dit qui a cree la tache — l'apiculteur ou une regle ;
--   * rien n'empeche une regle de recreer la meme tache a chaque execution.
ALTER TABLE tache
    ADD COLUMN priorite   VARCHAR(10) NOT NULL DEFAULT 'normale',
    ADD COLUMN categorie  VARCHAR(20),
    -- 'manuelle' ou 'regle'. La distinction compte a l'affichage : une tache
    -- engendree se justifie (« pourquoi celle-ci ? »), une tache saisie non.
    ADD COLUMN origine    VARCHAR(10) NOT NULL DEFAULT 'manuelle',
    -- Le code de la regle qui l'a produite, pour l'expliquer a l'ecran.
    ADD COLUMN regle_code VARCHAR(40),
    -- ---------------------------------------------------------------------
    -- LA colonne de cette migration.
    --
    -- Une regle s'execute a chaque passage : sans cle, « retirer le traitement
    -- 42 » serait recree a chaque tour, et l'apiculteur verrait sa liste se
    -- remplir de doublons jusqu'a ne plus la lire. La cle porte l'IDENTITE de
    -- la tache — `carence-retrait:42`, `ponte-j7:visite-118` — et un index
    -- unique la rend structurellement unique.
    --
    -- Nullable : les taches saisies a la main n'en ont pas, et il n'y a aucune
    -- raison de leur en inventer une.
    ADD COLUMN cle_declencheur VARCHAR(80);

ALTER TABLE tache
    ADD CONSTRAINT ck_tache_priorite CHECK (priorite IN
        ('basse', 'normale', 'haute', 'critique')),
    ADD CONSTRAINT ck_tache_categorie CHECK (categorie IS NULL OR categorie IN
        ('controle', 'traitement', 'nourrissement', 'recolte', 'materiel',
         'elevage', 'administratif', 'autre')),
    ADD CONSTRAINT ck_tache_origine CHECK (origine IN ('manuelle', 'regle')),
    -- Une tache engendree sans code de regle serait inexplicable a l'ecran ;
    -- une tache manuelle avec un code serait un mensonge.
    ADD CONSTRAINT ck_tache_origine_regle CHECK (
        (origine = 'regle' AND regle_code IS NOT NULL AND cle_declencheur IS NOT NULL)
        OR (origine = 'manuelle' AND regle_code IS NULL));

-- Unicite par exploitation, et seulement pour les taches engendrees : deux
-- exploitations peuvent legitimement avoir la meme cle.
CREATE UNIQUE INDEX uq_tache_declencheur
    ON tache (tenant_id, cle_declencheur)
    WHERE cle_declencheur IS NOT NULL;

-- La liste du jour se lit par priorite decroissante puis par echeance : c'est
-- l'ordre dans lequel on travaille.
CREATE INDEX ix_tache_priorite ON tache (priorite, echeance)
    WHERE faite = false;

COMMENT ON COLUMN tache.cle_declencheur IS
    'Identite de la tache engendree. L''index unique empeche une regle de la recreer a chaque passage.';
COMMENT ON COLUMN tache.origine IS
    'manuelle | regle. Une tache engendree doit pouvoir se justifier a l''ecran.';


-- === 2. Forcage de carence, et sa trace ====================================
--
-- Le SPRINT-20 avait consigne le delai de carence sans l'opposer : la liste des
-- ruches sous carence se lisait, rien n'empechait d'enregistrer une recolte sur
-- l'une d'elles. Le service le refuse desormais.
--
-- ---------------------------------------------------------------------------
-- Pourquoi une porte de sortie, et pourquoi tracee
--
-- Un refus sans issue ne supprime pas le contournement, il le deplace : un
-- apiculteur qui ne PEUT pas enregistrer sa recolte cesse d'enregistrer le
-- TRAITEMENT, et le registre devient faux la ou il n'etait qu'incomplet. C'est
-- le mode de defaillance que ce couple de colonnes evite : on peut passer
-- outre, mais on dit pourquoi, et cela se voit.
--
-- Le motif est OBLIGATOIRE des lors qu'on force — une case a cocher sans
-- explication n'aurait aucune valeur devant un controle.
ALTER TABLE recolte
    ADD COLUMN carence_forcee BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN motif_forcage  TEXT;

ALTER TABLE recolte
    ADD CONSTRAINT ck_recolte_forcage CHECK (
        (carence_forcee = false AND motif_forcage IS NULL)
        OR (carence_forcee = true AND length(trim(motif_forcage)) > 0));

-- Index partiel : les recoltes forcees sont rares par construction, et ce sont
-- exactement celles qu'un controle veut lire.
CREATE INDEX ix_recolte_forcee ON recolte (date_recolte DESC)
    WHERE carence_forcee = true;

COMMENT ON COLUMN recolte.carence_forcee IS
    'Recolte enregistree malgre une carence en cours. Le motif est alors obligatoire, et l''acte est audite.';


-- === 3. Une action d'audit de plus : le forcage ============================
--
-- Le journal d'audit ne connaissait que creation / modification / suppression.
-- Forcer une carence n'est aucune des trois : c'est une decision de passer
-- outre une regle, et c'est precisement ce qu'un controle veut retrouver. La
-- distinguer evite qu'elle se noie dans les creations ordinaires.
ALTER TABLE audit_entree DROP CONSTRAINT ck_audit_action;
ALTER TABLE audit_entree ADD CONSTRAINT ck_audit_action
    CHECK (action IN ('creation', 'modification', 'suppression', 'forcage'));

COMMENT ON CONSTRAINT ck_audit_action ON audit_entree IS
    'forcage (SPRINT-22) : passage outre une regle metier, trace avec son motif.';
