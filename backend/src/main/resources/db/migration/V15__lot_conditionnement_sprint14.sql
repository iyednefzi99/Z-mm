-- ===========================================================================
-- V15 — Lot de conditionnement et mention d'origine (SPRINT-14, US-056)
--
-- Conformite a la DIRECTIVE (UE) 2024/1438 du 14 mai 2024, dite « petit
-- dejeuner », applicable au 14 juin 2026 (en France : decret n° 2026-312 du
-- 24 avril 2026).
--
-- Ce que la directive exige, et que Zumm ne savait pas representer :
--   * le ou les PAYS D'ORIGINE figurent sur l'etiquette ;
--   * pour un melange, ils sont listes par ORDRE DECROISSANT de proportion ;
--   * chaque part est chiffree en POURCENTAGE, avec une tolerance de 5 points
--     par part, calculee sur les documents de tracabilite de l'operateur.
--
-- Le modele existant s'arrete a la RECOLTE (V10) : une ruche, une date, un lot.
-- C'est la maille de production. Or ce qui part en pot n'est presque jamais une
-- recolte unique : c'est un MELANGE, extrait puis conditionne. Sans objet pour
-- ce melange, aucune mention d'origine n'est calculable — et le lot vendu n'est
-- rattachable a rien en controle.
--
-- D'ou deux tables : le lot de conditionnement, et sa composition.
-- ===========================================================================

-- === Lot de conditionnement ================================================
CREATE TABLE lot_conditionnement (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id         TEXT          NOT NULL,
    reference         VARCHAR(40)   NOT NULL,
    date_conditionnement DATE       NOT NULL,
    -- Quantite REELLE mise en pot, distincte de la somme des recoltes : il y a
    -- des pertes a l'extraction et au filtrage, et une part peut rester en fut.
    quantite_kg       NUMERIC(10, 3) NOT NULL,
    type_miel         VARCHAR(60),
    note              TEXT,
    cree_le           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    maj_le            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_lot_quantite CHECK (quantite_kg > 0),
    CONSTRAINT ck_lot_reference_non_vide CHECK (length(trim(reference)) > 0),
    CONSTRAINT uq_lot_id_tenant UNIQUE (id, tenant_id),
    -- La reference est ce qui est imprime sur le pot : elle doit designer un lot
    -- et un seul dans l'exploitation.
    CONSTRAINT uq_lot_reference UNIQUE (tenant_id, reference)
);

ALTER TABLE lot_conditionnement ENABLE ROW LEVEL SECURITY;
ALTER TABLE lot_conditionnement FORCE  ROW LEVEL SECURITY;
CREATE POLICY p_lot_tenant ON lot_conditionnement
    USING      (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));

CREATE TRIGGER tg_lot_maj BEFORE UPDATE ON lot_conditionnement
    FOR EACH ROW EXECUTE FUNCTION zumm_touch_maj_le();

COMMENT ON TABLE lot_conditionnement IS
    'Lot mis en pot (US-056). Porte la reference imprimee et la mention d''origine.';

-- === Composition du lot ====================================================
-- Une ligne = la part d'une recolte dans le lot.
--
-- `pays_origine` est porte ICI, et non sur la recolte, parce que c'est la
-- donnee que la directive fait figurer sur l'etiquette : l'origine du MIEL
-- entrant. Un lot peut melanger du miel produit par l'exploitation (origine =
-- son pays) et du miel acquis a un tiers — auquel cas la recolte est absente et
-- seule l'origine declaree existe. D'ou une cle etrangere NULLABLE : le modele
-- doit representer le miel achete, sinon les pourcentages ne totalisent jamais
-- 100 % et la mention est fausse.
CREATE TABLE lot_composition (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id    TEXT          NOT NULL,
    lot_id       BIGINT        NOT NULL,
    recolte_id   BIGINT,
    -- Code pays ISO 3166-1 alpha-2 : « FR », « ES », « UA »… Un code normalise
    -- plutot qu'un libelle libre, pour que l'etiquette soit traduisible et que
    -- deux saisies du meme pays se regroupent.
    pays_origine CHAR(2)       NOT NULL,
    pourcentage  NUMERIC(5, 2) NOT NULL,
    cree_le      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_composition_pourcentage CHECK (pourcentage > 0 AND pourcentage <= 100),
    CONSTRAINT ck_composition_pays CHECK (pays_origine ~ '^[A-Z]{2}$'),
    CONSTRAINT uq_composition_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_composition_lot
        FOREIGN KEY (lot_id, tenant_id)
        REFERENCES lot_conditionnement (id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT fk_composition_recolte
        FOREIGN KEY (recolte_id, tenant_id)
        REFERENCES recolte (id, tenant_id) ON DELETE RESTRICT
);

CREATE INDEX ix_composition_lot ON lot_composition (lot_id);
CREATE INDEX ix_composition_recolte ON lot_composition (recolte_id);

ALTER TABLE lot_composition ENABLE ROW LEVEL SECURITY;
ALTER TABLE lot_composition FORCE  ROW LEVEL SECURITY;
CREATE POLICY p_composition_tenant ON lot_composition
    USING      (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));

COMMENT ON TABLE lot_composition IS
    'Parts d''origine d''un lot (directive (UE) 2024/1438). Somme des pourcentages = 100.';

-- ---------------------------------------------------------------------------
-- Pourquoi la somme des pourcentages n'est PAS une contrainte CHECK
--
-- Une contrainte de table ne peut pas porter sur l'ensemble des lignes d'un lot.
-- Restait un TRIGGER de contrainte differe ; il a ete ecarte : la regle
-- « la somme fait 100 » est une regle METIER, avec un message d'erreur a
-- expliquer a l'utilisateur (quelle part manque, laquelle est en trop), et elle
-- se verifie au moment ou l'on constitue le lot. La releguer dans un trigger
-- rendrait la violation illisible (une erreur SQL brute en 500) et la
-- dupliquerait sans supprimer la verification applicative.
--
-- La verification vit donc dans `LotConditionnementService`, avec un test qui la
-- couvre. La base garde ce qu'elle sait faire mieux que le code : bornes,
-- format du code pays, unicite, integrite referentielle et isolation.
-- ---------------------------------------------------------------------------
