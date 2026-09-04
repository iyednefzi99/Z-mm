-- ===========================================================================
-- V27 — Production, stock, materiel, comptabilite (SPRINT-27, lot E)
--
-- Le lot le plus large du plan de couverture, et le plus mecanique : quatorze
-- lignes du §6, du §7 et du §13. Rien d'intellectuellement difficile ici — la
-- difficulte est de savoir OU s'arreter.
--
-- ---------------------------------------------------------------------------
-- LA FRONTIERE, posee une fois pour toutes
--
-- La comptabilite s'arrete a la RENTABILITE PAR RUCHE. Facturation, TVA, devis,
-- clients, factures d'achat restent ⛔ (§9 du document d'ecart) : Zumm est un
-- SIG apicole, pas un ERP, et ce bloc a le poids d'un produit a lui seul.
--
-- Concretement : `depense` porte un montant, une date, une categorie et une
-- affectation facultative. Elle ne porte NI fournisseur, NI numero de piece, NI
-- TVA, NI echeance de paiement. Chacune de ces colonnes appellerait la suivante,
-- et la troisieme rendrait le module obligatoire pour boucler un exercice —
-- alors que personne n'a demande a Zumm de tenir des comptes.
--
-- ---------------------------------------------------------------------------
-- 1. Les produits autres que le miel
--
-- « `Recolte.quantiteKg` + `typeMiel` : le modele PRESUPPOSE du miel » (§6).
-- Quatre concurrents ventilent par produit. La correction est une colonne, pas
-- une table : une recolte de cire est une recolte, faite le meme jour, sur la
-- meme ruche, par le meme agent. Lui donner sa propre table aurait duplique la
-- tracabilite, le lot, le forcage de carence et l'export.
--
-- Une seconde colonne suit, et elle est indispensable : l'UNITE. Cinq essaims
-- ne pesent pas cinq kilogrammes, et deux reines encore moins. Sans elle,
-- `quantite_kg` mentirait des la premiere capture — et le total de production
-- additionnerait des kilos a des individus.
--
-- ---------------------------------------------------------------------------
-- 2. Maturation et DLUO : deux dates, pas une etape
--
-- « L'etape de maturation et les dates de peremption non » (§6). La tentation
-- etait un workflow — recolte → maturation → mise en pot — avec ses etats et
-- ses transitions. Deux dates suffisent, et disent la meme chose : quand le
-- miel est descendu du maturateur, et jusqu'a quand le lot se consomme.
--
-- La DDM (date de durabilite minimale) a remplace la DLUO en droit francais
-- depuis 2015 ; la colonne porte le nom legal actuel.
--
-- ---------------------------------------------------------------------------
-- 3. Materiel et maintenance : deux tables, et pourquoi
--
-- Onibi conseille de « nettoyer les optiques, gratter la propolis sur les
-- glissieres » (§13). Le conseil dit deux choses distinctes : il faut savoir CE
-- QU'ON POSSEDE (inventaire, etat d'entretien), et il faut savoir QUAND Y
-- REVENIR (periodicite).
--
-- L'echeance elle-meme n'est PAS stockee : elle se calcule
-- (`derniere_maintenance + periodicite_jours`), et la tache est engendree par le
-- moteur de regles du SPRINT-22. Stocker une date « prochaine maintenance »
-- aurait cree une valeur a maintenir en cohérence avec la derniere — la meme
-- dette que `ComptageVarroaService` evite pour le taux de varroa.
--
-- ---------------------------------------------------------------------------
-- 4. Le stock, et le seuil qui le rend utile
--
-- Un stock sans seuil est un inventaire : il dit ce qu'on a, jamais ce qui
-- manque. C'est le seuil qui engendre la tache de reapprovisionnement, et c'est
-- pour cela qu'il est NOT NULL avec un defaut a zero — un seuil absent
-- desactiverait silencieusement la seule fonction utile de la table.
-- ===========================================================================


-- === 1. Produits autres que le miel ========================================

ALTER TABLE recolte ADD COLUMN type_produit VARCHAR(20) NOT NULL DEFAULT 'miel';
ALTER TABLE recolte ADD COLUMN unite VARCHAR(10) NOT NULL DEFAULT 'kg';

ALTER TABLE recolte ADD CONSTRAINT ck_recolte_produit CHECK (type_produit IN
    ('miel', 'cire', 'pollen', 'propolis', 'gelee_royale', 'essaim', 'reine'));
ALTER TABLE recolte ADD CONSTRAINT ck_recolte_unite CHECK (unite IN ('kg', 'unite'));

-- Un essaim et une reine se comptent, ils ne se pesent pas. La contrainte ferme
-- l'incoherence a la source plutot que de la laisser produire des totaux faux.
ALTER TABLE recolte ADD CONSTRAINT ck_recolte_unite_produit CHECK (
    (type_produit IN ('essaim', 'reine') AND unite = 'unite')
    OR (type_produit NOT IN ('essaim', 'reine') AND unite = 'kg'));

CREATE INDEX ix_recolte_produit ON recolte (tenant_id, type_produit, date_recolte DESC);

COMMENT ON COLUMN recolte.type_produit IS
    'miel | cire | pollen | propolis | gelee_royale | essaim | reine (SPRINT-27). '
    'Defaut `miel` : c''est ce que le modele presupposait avant.';
COMMENT ON COLUMN recolte.unite IS
    'kg | unite. Cinq essaims ne pesent pas cinq kilogrammes.';


-- === 2. Maturation et durabilite ===========================================

ALTER TABLE lot_conditionnement ADD COLUMN date_maturation DATE;
ALTER TABLE lot_conditionnement ADD COLUMN date_durabilite DATE;

-- On ne conditionne pas avant d'avoir matura, et un lot ne se perime pas avant
-- d'etre fait.
ALTER TABLE lot_conditionnement ADD CONSTRAINT ck_lot_dates CHECK (
    (date_maturation IS NULL OR date_maturation <= date_conditionnement)
    AND (date_durabilite IS NULL OR date_durabilite > date_conditionnement));

COMMENT ON COLUMN lot_conditionnement.date_maturation IS
    'Descente du maturateur (SPRINT-27). Une date, pas une etape de workflow.';
COMMENT ON COLUMN lot_conditionnement.date_durabilite IS
    'DDM — date de durabilite minimale, qui a remplace la DLUO en 2015.';


-- === 3. Materiel et plan de maintenance ====================================

CREATE TABLE materiel (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id             TEXT         NOT NULL,
    libelle               VARCHAR(120) NOT NULL,
    categorie             VARCHAR(20)  NOT NULL,
    quantite              INT          NOT NULL DEFAULT 1,
    -- Ou il se trouve. Facultatif : un extracteur vit a la miellerie, qui n'est
    -- pas un rucher, et forcer un site ferait inventer des ruchers fictifs.
    site_id               BIGINT,
    etat                  VARCHAR(20)  NOT NULL DEFAULT 'bon',
    -- Periodicite d'entretien, en jours. NULLE = materiel qui ne s'entretient
    -- pas (une hausse vide), et non « a entretenir quand on y pense ».
    periodicite_jours     INT,
    derniere_maintenance  DATE,
    note                  TEXT,
    cree_le               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    maj_le                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_materiel_categorie CHECK (categorie IN
        ('ruche', 'hausse', 'cadre', 'extracteur', 'maturateur', 'enfumoir',
         'protection', 'vehicule', 'balance', 'autre')),
    CONSTRAINT ck_materiel_etat CHECK (etat IN ('neuf', 'bon', 'a_reviser', 'hors_service')),
    CONSTRAINT ck_materiel_quantite CHECK (quantite > 0),
    CONSTRAINT ck_materiel_periodicite CHECK (
        periodicite_jours IS NULL OR periodicite_jours BETWEEN 1 AND 3650),
    CONSTRAINT uq_materiel_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_materiel_site
        FOREIGN KEY (site_id, tenant_id) REFERENCES site (id, tenant_id) ON DELETE SET NULL
);

-- Index partiel sur ce qui appelle une action : on cherche le materiel a
-- reviser, jamais celui qui va bien.
CREATE INDEX ix_materiel_a_revoir ON materiel (tenant_id, etat)
    WHERE etat IN ('a_reviser', 'hors_service');
CREATE INDEX ix_materiel_maintenance ON materiel (tenant_id, derniere_maintenance)
    WHERE periodicite_jours IS NOT NULL;

ALTER TABLE materiel ENABLE ROW LEVEL SECURITY;
ALTER TABLE materiel FORCE  ROW LEVEL SECURITY;
-- Portee du seul tenant : le materiel appartient a l'exploitation, pas a
-- l'agent qui s'en sert. Un apiculteur doit voir qu'un extracteur existe.
CREATE POLICY p_materiel_tenant ON materiel
    USING      (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));

CREATE TRIGGER tg_materiel_maj BEFORE UPDATE ON materiel
    FOR EACH ROW EXECUTE FUNCTION zumm_touch_maj_le();

COMMENT ON TABLE materiel IS
    'Inventaire et etat d''entretien (SPRINT-27). La prochaine echeance ne s''y '
    'trouve pas : elle se calcule (derniere_maintenance + periodicite_jours), et '
    'la tache est engendree par le moteur de regles.';


-- Une tache peut viser un MATERIEL et non une ruche : c'est exactement ce que
-- demandait le §13 — « taches recurrentes attachees a un equipement, pas a une
-- ruche ».
ALTER TABLE tache ADD COLUMN materiel_id BIGINT;
ALTER TABLE tache ADD CONSTRAINT fk_tache_materiel
    FOREIGN KEY (materiel_id, tenant_id) REFERENCES materiel (id, tenant_id) ON DELETE CASCADE;
CREATE INDEX ix_tache_materiel ON tache (materiel_id) WHERE materiel_id IS NOT NULL;

COMMENT ON COLUMN tache.materiel_id IS
    'Equipement concerne (SPRINT-27). Exclusif de `ruche_id` dans les faits, '
    'sans contrainte pour autant : « reviser l''extracteur avant la recolte de '
    'la ruche 12 » est une phrase qui a un sens.';


-- === 4. Stock de consommables ==============================================

CREATE TABLE consommable (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id     TEXT           NOT NULL,
    libelle       VARCHAR(120)   NOT NULL,
    categorie     VARCHAR(20)    NOT NULL,
    quantite      NUMERIC(10, 2) NOT NULL DEFAULT 0,
    unite         VARCHAR(10)    NOT NULL,
    -- Un stock sans seuil est un inventaire : il dit ce qu'on a, jamais ce qui
    -- manque. NOT NULL avec defaut a zero — un seuil absent desactiverait
    -- silencieusement la seule fonction utile de cette table.
    seuil_alerte  NUMERIC(10, 2) NOT NULL DEFAULT 0,
    note          TEXT,
    cree_le       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    maj_le        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ck_consommable_categorie CHECK (categorie IN
        ('sirop', 'candi', 'traitement', 'cire_gaufree', 'pot', 'etiquette',
         'cadre', 'protection', 'autre')),
    CONSTRAINT ck_consommable_unite CHECK (unite IN ('kg', 'l', 'unite')),
    CONSTRAINT ck_consommable_quantite CHECK (quantite >= 0),
    CONSTRAINT ck_consommable_seuil CHECK (seuil_alerte >= 0),
    CONSTRAINT uq_consommable_id_tenant UNIQUE (id, tenant_id)
);

-- L'index sert la question « que dois-je racheter » : elle porte sur la
-- comparaison entre deux colonnes, qu'aucun index ne couvre. Celui-ci accelere
-- au moins le parcours par tenant.
CREATE INDEX ix_consommable_tenant ON consommable (tenant_id, libelle);

ALTER TABLE consommable ENABLE ROW LEVEL SECURITY;
ALTER TABLE consommable FORCE  ROW LEVEL SECURITY;
CREATE POLICY p_consommable_tenant ON consommable
    USING      (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));

CREATE TRIGGER tg_consommable_maj BEFORE UPDATE ON consommable
    FOR EACH ROW EXECUTE FUNCTION zumm_touch_maj_le();

COMMENT ON TABLE consommable IS
    'Stock de consommables et seuil de reapprovisionnement (SPRINT-27).';


-- === 5. Depenses ===========================================================

CREATE TABLE depense (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id    TEXT           NOT NULL,
    libelle      VARCHAR(160)   NOT NULL,
    categorie    VARCHAR(20)    NOT NULL,
    montant_eur  NUMERIC(10, 2) NOT NULL,
    date_depense DATE           NOT NULL,
    -- Affectation FACULTATIVE, et a deux niveaux : une depense se rattache a
    -- une ruche (une reine achetee), a un rucher (un transport), ou a rien du
    -- tout (une assurance). Forcer une affectation ferait inventer des
    -- rattachements pour boucler une saisie.
    ruche_id     BIGINT,
    site_id      BIGINT,
    note         TEXT,
    cree_le      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    maj_le       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ck_depense_categorie CHECK (categorie IN
        ('materiel', 'consommable', 'traitement', 'nourrissement', 'cheptel',
         'transport', 'analyse', 'assurance', 'formation', 'autre')),
    -- Le montant peut etre nul (un don recu, une reprise), jamais negatif : une
    -- depense negative est une recette, et les recettes se calculent depuis les
    -- recoltes. Les melanger rendrait tout total ambigu.
    CONSTRAINT ck_depense_montant CHECK (montant_eur >= 0),
    CONSTRAINT uq_depense_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_depense_ruche
        FOREIGN KEY (ruche_id, tenant_id) REFERENCES ruche (id, tenant_id) ON DELETE SET NULL,
    CONSTRAINT fk_depense_site
        FOREIGN KEY (site_id, tenant_id) REFERENCES site (id, tenant_id) ON DELETE SET NULL
);

CREATE INDEX ix_depense_periode ON depense (tenant_id, date_depense DESC);
CREATE INDEX ix_depense_ruche ON depense (ruche_id) WHERE ruche_id IS NOT NULL;

ALTER TABLE depense ENABLE ROW LEVEL SECURITY;
ALTER TABLE depense FORCE  ROW LEVEL SECURITY;
-- Portee du seul tenant, sans restriction d'agent : la comptabilite est une vue
-- d'exploitation. La restreindre par affectation donnerait a un apiculteur un
-- total partiel presente comme un total, ce qui est pire que pas de total.
CREATE POLICY p_depense_tenant ON depense
    USING      (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));

CREATE TRIGGER tg_depense_maj BEFORE UPDATE ON depense
    FOR EACH ROW EXECUTE FUNCTION zumm_touch_maj_le();

COMMENT ON TABLE depense IS
    'Depenses de l''exploitation (SPRINT-27). Ni fournisseur, ni TVA, ni numero '
    'de piece : la frontiere du produit s''arrete a la rentabilite par ruche.';
