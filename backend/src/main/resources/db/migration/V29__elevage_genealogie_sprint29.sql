-- ===========================================================================
-- V29 — Elevage, reines et genealogie (SPRINT-29, lot D)
--
-- Sept lignes du §7 de `docs/ECART-CONCURRENTS.md`. Trois concurrents — BeeKube,
-- APiLOG, HiveBook — en font leur argument central.
--
-- ---------------------------------------------------------------------------
-- LE PLAN SE TROMPAIT, ET IL FAUT LE DIRE
--
-- `docs/PLAN-COUVERTURE-ECARTS.md` annoncait « une cle etrangere reflexive sur
-- `suivi_reine` ». Elle aurait ete FAUSSE. `suivi_reine` (V9) n'est pas une
-- reine : c'est le JOURNAL d'une ruche — introduite, en ponte, remplacee,
-- disparue, essaimee. Une ligne y est un evenement, et une meme ruche en porte
-- des dizaines, appartenant a des reines successives.
--
-- Une cle reflexive sur cette table aurait relie des EVENEMENTS entre eux. La
-- question « de quelle mere descend cette reine ? » n'aurait eu aucune reponse
-- stable : quel evenement designe la mere ? Celui de son introduction ? De sa
-- disparition ? Le choix aurait varie selon l'appelant, et l'arbre genealogique
-- aurait dependu de la maniere dont on l'a construit.
--
-- La reine devient donc une TABLE. `suivi_reine` reste son journal et gagne un
-- `reine_id` NULLABLE — voir le point 3 pour ce que ce NULL protege.
--
-- ---------------------------------------------------------------------------
-- CE QUI N'EST PAS FAIT, ET POURQUOI
--
-- Aucune table de scores. L'index genetique se CALCULE a chaque lecture, comme
-- les indices de colonie du SPRINT-22 et le taux de varroa du SPRINT-20. Le
-- stocker creerait une valeur a maintenir en coherence avec chaque visite, et
-- personne ne saurait plus dire de quelles observations elle vient.
--
-- Et il n'y a AUCUNE note globale. Voir `IndexGenetiqueService` : la douceur, la
-- resistance au varroa et les kilos ne s'additionnent pas, et la ponderation qui
-- le permettrait n'est demandee par personne.
-- ===========================================================================


-- === 1. La reine ============================================================
CREATE TABLE reine (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id         TEXT         NOT NULL,
    -- Identifiant de l'eleveur, tel qu'il figure sur la cagette ou le carnet.
    -- Facultatif : beaucoup d'apiculteurs ne numerotent pas leurs reines, et
    -- l'exiger ferait renoncer a les enregistrer.
    code              VARCHAR(40),
    -- LA cle etrangere du lot : reine -> reine mere. Reflexive, composite, et
    -- ON DELETE SET NULL — supprimer une aieule ne doit pas emporter sa
    -- descendance, qui existe bel et bien dans le rucher.
    mere_id           BIGINT,
    -- Ruche d'ou provient la reine mere au moment du greffage. Distincte de
    -- `mere_id` : on greffe souvent depuis une colonie dont la reine n'est pas
    -- enregistree, et perdre cette information-la ferait perdre la seule trace
    -- de la souche.
    ruche_mere_id     BIGINT,
    serie_id          BIGINT,
    -- Ruche ou la reine est en service. NULL est un etat normal : une reine en
    -- nucleus, en banque a reines, ou vendue.
    ruche_id          BIGINT,
    origine           VARCHAR(20)  NOT NULL DEFAULT 'inconnue',
    -- N'a de sens que pour une reine achetee ; la contrainte le dit.
    fournisseur       VARCHAR(120),
    race              VARCHAR(60),
    annee_naissance   INT,
    couleur_marquage  VARCHAR(10),
    -- « Ailes clippees » : la ligne du §7 le demande nommement. Un booleen
    -- NULLABLE, parce que « on ne sait pas » n'est pas « non clippee » — la
    -- meme regle que partout ailleurs dans ce schema.
    ailes_clippees    BOOLEAN,
    -- Les quatre dates de l'elevage. Aucune n'est obligatoire : on enregistre
    -- une reine achetee sans connaitre son greffage.
    date_greffage     DATE,
    date_naissance    DATE,
    date_fecondation  DATE,
    date_introduction DATE,
    -- Fin de regne. Elle borne l'index genetique : n'attribuer a une reine que
    -- ce qui s'est passe pendant son regne est ce qui rend la comparaison
    -- honnete.
    date_fin          DATE,
    statut            VARCHAR(20)  NOT NULL DEFAULT 'en_service',
    note              TEXT,
    cree_le           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    maj_le            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_reine_origine CHECK (origine IN
        ('elevage', 'achat', 'essaimage', 'supersedure', 'inconnue')),
    CONSTRAINT ck_reine_statut_vie CHECK (statut IN
        ('en_service', 'reserve', 'remplacee', 'disparue', 'morte', 'vendue')),
    CONSTRAINT ck_reine_couleur_marquage CHECK (couleur_marquage IS NULL
        OR couleur_marquage IN ('blanc', 'jaune', 'rouge', 'vert', 'bleu')),
    CONSTRAINT ck_reine_naissance CHECK (annee_naissance IS NULL
        OR annee_naissance BETWEEN 2000 AND 2100),
    -- Le fournisseur n'a de sens que sur une reine achetee. Sans cette
    -- contrainte, la colonne se remplirait de noms d'eleveurs sur des reines
    -- issues d'essaimage, et la question « qu'ai-je achete cette annee ? »
    -- n'aurait plus de reponse.
    CONSTRAINT ck_reine_fournisseur CHECK (fournisseur IS NULL OR origine = 'achat'),
    -- Une reine ne descend pas d'elle-meme. Les cycles plus longs se refusent
    -- au service : PostgreSQL ne peut verifier qu'un pas.
    CONSTRAINT ck_reine_mere CHECK (mere_id IS NULL OR mere_id <> id),
    CONSTRAINT ck_reine_fin CHECK (date_fin IS NULL OR date_introduction IS NULL
        OR date_fin >= date_introduction),
    CONSTRAINT uq_reine_code UNIQUE (tenant_id, code),
    CONSTRAINT uq_reine_ligne_tenant UNIQUE (id, tenant_id),
    -- `ON DELETE SET NULL (colonne)` et non `ON DELETE SET NULL` tout court :
    -- sans la liste, PostgreSQL met a NULL TOUTES les colonnes referencantes,
    -- `tenant_id` compris — qui est NOT NULL. La suppression echouerait alors
    -- sur une violation de contrainte au lieu de detacher la reference. Voir la
    -- section 5, qui repare cinq cles ecrites sans cette liste.
    CONSTRAINT fk_reine_mere
        FOREIGN KEY (mere_id, tenant_id) REFERENCES reine (id, tenant_id)
        ON DELETE SET NULL (mere_id),
    CONSTRAINT fk_reine_ruche_mere
        FOREIGN KEY (ruche_mere_id, tenant_id) REFERENCES ruche (id, tenant_id)
        ON DELETE SET NULL (ruche_mere_id),
    CONSTRAINT fk_reine_ruche
        FOREIGN KEY (ruche_id, tenant_id) REFERENCES ruche (id, tenant_id)
        ON DELETE SET NULL (ruche_id)
);

CREATE INDEX ix_reine_mere ON reine (mere_id) WHERE mere_id IS NOT NULL;
CREATE INDEX ix_reine_ruche_service ON reine (ruche_id) WHERE ruche_id IS NOT NULL;
CREATE INDEX ix_reine_tenant_statut ON reine (tenant_id, statut);

ALTER TABLE reine ENABLE ROW LEVEL SECURITY;
ALTER TABLE reine FORCE  ROW LEVEL SECURITY;
CREATE POLICY p_reine_lignee_tenant ON reine
    USING      (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));

CREATE TRIGGER tg_reine_lignee_maj BEFORE UPDATE ON reine
    FOR EACH ROW EXECUTE FUNCTION zumm_touch_maj_le();

COMMENT ON TABLE reine IS
    'La reine comme objet, avec sa filiation (SPRINT-29). A ne pas confondre avec '
    '`suivi_reine`, qui est le JOURNAL d''une ruche et reste son journal.';


-- === 2. Serie d'elevage =====================================================
--
-- « Dates de greffage, suivi d'elevage » (§7). Ce que l'eleveur note, ce n'est
-- pas une reine a la fois : c'est un LOT — quarante cupules greffees un lundi,
-- trente et une acceptees, vingt-huit nees, vingt-deux fecondees. Le taux
-- d'acceptation est le chiffre qui decide de la methode l'annee suivante, et il
-- n'existe qu'au niveau du lot.
--
-- Les comptes sont saisis, jamais deduits des reines enregistrees : on
-- n'enregistre individuellement que les reines qu'on garde, et compter celles-la
-- donnerait un taux d'acceptation faux, toujours trop bas.
CREATE TABLE serie_elevage (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id       TEXT        NOT NULL,
    nom             VARCHAR(60) NOT NULL,
    date_greffage   DATE        NOT NULL,
    -- Souche : la reine dont on a greffe les larves, si elle est enregistree.
    souche_id       BIGINT,
    -- Colonie eleveuse, qui n'est presque jamais celle de la souche.
    ruche_eleveuse_id BIGINT,
    methode         VARCHAR(20),
    nb_greffees     INT         NOT NULL,
    nb_acceptees    INT,
    nb_nees         INT,
    nb_fecondees    INT,
    note            TEXT,
    cree_le         TIMESTAMPTZ NOT NULL DEFAULT now(),
    maj_le          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_serie_nom CHECK (length(trim(nom)) > 0),
    CONSTRAINT ck_serie_methode CHECK (methode IS NULL OR methode IN
        ('greffage', 'picking', 'cupularve', 'essaim_artificiel', 'autre')),
    CONSTRAINT ck_serie_greffees CHECK (nb_greffees > 0),
    -- L'entonnoir ne remonte pas : on ne peut pas avoir plus d'acceptees que de
    -- greffees, ni plus de fecondees que de nees. Une saisie qui l'inverse est
    -- une faute de frappe, et le taux d'acceptation qui en sortirait
    -- depasserait 100 %.
    CONSTRAINT ck_serie_entonnoir CHECK (
        (nb_acceptees IS NULL OR nb_acceptees <= nb_greffees)
        AND (nb_nees IS NULL OR nb_acceptees IS NULL OR nb_nees <= nb_acceptees)
        AND (nb_fecondees IS NULL OR nb_nees IS NULL OR nb_fecondees <= nb_nees)),
    CONSTRAINT ck_serie_positifs CHECK (
        (nb_acceptees IS NULL OR nb_acceptees >= 0)
        AND (nb_nees IS NULL OR nb_nees >= 0)
        AND (nb_fecondees IS NULL OR nb_fecondees >= 0)),
    CONSTRAINT uq_serie_nom UNIQUE (tenant_id, nom),
    CONSTRAINT uq_serie_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_serie_souche
        FOREIGN KEY (souche_id, tenant_id) REFERENCES reine (id, tenant_id)
        ON DELETE SET NULL (souche_id),
    CONSTRAINT fk_serie_ruche
        FOREIGN KEY (ruche_eleveuse_id, tenant_id) REFERENCES ruche (id, tenant_id)
        ON DELETE SET NULL (ruche_eleveuse_id)
);

CREATE INDEX ix_serie_date ON serie_elevage (tenant_id, date_greffage DESC);

ALTER TABLE serie_elevage ENABLE ROW LEVEL SECURITY;
ALTER TABLE serie_elevage FORCE  ROW LEVEL SECURITY;
CREATE POLICY p_serie_tenant ON serie_elevage
    USING      (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));

CREATE TRIGGER tg_serie_maj BEFORE UPDATE ON serie_elevage
    FOR EACH ROW EXECUTE FUNCTION zumm_touch_maj_le();

ALTER TABLE reine
    ADD CONSTRAINT fk_reine_serie
        FOREIGN KEY (serie_id, tenant_id) REFERENCES serie_elevage (id, tenant_id)
        ON DELETE SET NULL (serie_id);


-- === 3. Le journal se rattache a la reine ===================================
--
-- NULLABLE, et ce NULL est une decision. Les evenements deja enregistres ne
-- designent aucune reine : `suivi_reine` ne portait que la ruche. Deviner
-- retrospectivement laquelle — en regroupant par annee de naissance, par
-- couleur de marquage — aurait FABRIQUE une genealogie. Un arbre faux est pire
-- qu'un arbre absent : on le lit sans le savoir.
--
-- Les evenements anterieurs restent donc rattaches a la ruche seule, et le
-- disent.
ALTER TABLE suivi_reine ADD COLUMN reine_id BIGINT;

ALTER TABLE suivi_reine
    ADD CONSTRAINT fk_suivi_reine_reine
        FOREIGN KEY (reine_id, tenant_id) REFERENCES reine (id, tenant_id)
        ON DELETE SET NULL (reine_id);

CREATE INDEX ix_suivi_reine_reine ON suivi_reine (reine_id) WHERE reine_id IS NOT NULL;

COMMENT ON COLUMN suivi_reine.reine_id IS
    'Reine concernee (SPRINT-29). NULL sur les evenements anterieurs : deviner '
    'laquelle aurait fabrique une genealogie fausse.';


-- === 4. Un point de plus au referentiel ferme ===============================
--
-- Le test hygienique est LE critere d'elevage que les concurrents mettent en
-- avant, et le seul des quatre du §7 dont aucune donnee n'existait. Il entre par
-- la porte prevue a cet effet : une migration ajoute un point au referentiel
-- ferme du SPRINT-28. C'est exactement pour cela que cette porte existe.
--
-- Il vaudra `null` pour tout le monde tant que personne ne l'aura releve — et
-- l'index genetique le dira, plutot que d'afficher une note batie sur rien.
INSERT INTO point_observation (code, categorie, type_valeur, libelle, ordre) VALUES
    ('test_hygienique', 'sanitaire', 'echelle',
     'Nettoyage du couvain teste (test hygienique)', 55);


-- === 5. Cinq cles etrangeres reparees =======================================
--
-- DEFAUT TROUVE EN ECRIVANT CE LOT, et il ne vient pas de lui.
--
-- `ON DELETE SET NULL` sans liste de colonnes met a NULL **toutes** les colonnes
-- referencantes — `tenant_id` compris, qui est NOT NULL partout dans ce schema.
-- La suppression de la ligne referencee echoue donc sur une violation de
-- contrainte, la ou elle devait simplement detacher la reference.
--
-- Les migrations V2, V4, V6, V7 et V19 ecrivaient la forme correcte, avec la
-- liste : `ON DELETE SET NULL (visite_id)`. Cinq cles posees en V20 et V27 l'ont
-- perdue. Consequence concrete : supprimer une ruche portant une depense, une
-- division ou une capture d'essaim echouait en 500, avec un message parlant de
-- `tenant_id` — c'est-a-dire au dernier endroit ou l'on aurait cherche.
--
-- Aucune donnee n'est touchee ici : seule la reaction a une suppression future
-- change. `ElevageGenealogieIT.suppressionDUneRucheReferencee` en fait la preuve :
-- correctif retire, ce test echoue sur
-- « null value in column "tenant_id" of relation "depense" violates not-null
-- constraint ».
ALTER TABLE division DROP CONSTRAINT fk_division_fille;
ALTER TABLE division
    ADD CONSTRAINT fk_division_fille
        FOREIGN KEY (ruche_fille_id, tenant_id) REFERENCES ruche (id, tenant_id)
        ON DELETE SET NULL (ruche_fille_id);

ALTER TABLE capture_essaim DROP CONSTRAINT fk_capture_ruche;
ALTER TABLE capture_essaim
    ADD CONSTRAINT fk_capture_ruche
        FOREIGN KEY (ruche_id, tenant_id) REFERENCES ruche (id, tenant_id)
        ON DELETE SET NULL (ruche_id);

ALTER TABLE capture_essaim DROP CONSTRAINT fk_capture_site;
ALTER TABLE capture_essaim
    ADD CONSTRAINT fk_capture_site
        FOREIGN KEY (site_id, tenant_id) REFERENCES site (id, tenant_id)
        ON DELETE SET NULL (site_id);

ALTER TABLE materiel DROP CONSTRAINT fk_materiel_site;
ALTER TABLE materiel
    ADD CONSTRAINT fk_materiel_site
        FOREIGN KEY (site_id, tenant_id) REFERENCES site (id, tenant_id)
        ON DELETE SET NULL (site_id);

ALTER TABLE depense DROP CONSTRAINT fk_depense_ruche;
ALTER TABLE depense
    ADD CONSTRAINT fk_depense_ruche
        FOREIGN KEY (ruche_id, tenant_id) REFERENCES ruche (id, tenant_id)
        ON DELETE SET NULL (ruche_id);

ALTER TABLE depense DROP CONSTRAINT fk_depense_site;
ALTER TABLE depense
    ADD CONSTRAINT fk_depense_site
        FOREIGN KEY (site_id, tenant_id) REFERENCES site (id, tenant_id)
        ON DELETE SET NULL (site_id);
