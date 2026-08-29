-- ===========================================================================
-- V19 — Registre sanitaire et observations structurees (SPRINT-20)
--
-- Repond aux ecarts 1 et 3 de `docs/ECART-CONCURRENTS.md` : les trois actes
-- sanitaires n'etaient qu'une valeur de `RaisonVisite` (`traitement`,
-- `nourrissage`) — on savait QU'ON avait traite, jamais AVEC QUOI, a quelle
-- dose, ni sous quel delai de carence. Et « varroa » n'apparaissait dans tout
-- le depot que dans la prose du jeu de demonstration.
--
-- UNE SEULE migration pour tout le bloc, et c'est deliberе : chaque table
-- ajoutee demande la meme revue (tenant_id, RLS, cle etrangere composite). Les
-- separer en trois migrations aurait triple la revue sans rien isoler, puisque
-- rien ne se livre utilement sans les trois.
--
-- Contenu :
--   1. traitement              — produit, dose, cible, delai de carence
--   2. nourrissement           — type, quantite, motif
--   3. comptage_varroa         — methode et comptages bruts
--   4. observation_pathologie  — maladies et ravageurs NOMMES, par visite
--   5. colonnes d'observation sur `visite` (couvain, reserves, cellules
--      royales, temperament) et meteo FIGEE au moment de la visite
--   6. colonnes bon marche sur `ruche` (type au referentiel, couleur, origine,
--      cause de cloture)
--
-- ---------------------------------------------------------------------------
-- Pourquoi TROIS tables et non une table « intervention » avec un type
--
-- La tentation etait une table unique (ruche, date, agent, type, produit,
-- quantite, note) discriminee par une colonne `type`. Elle a ete ecartee : la
-- dose et le delai de carence n'ont de sens que pour un traitement, le motif
-- que pour un nourrissement, la methode de comptage que pour le varroa. Une
-- table unique aurait rendu NULLABLE tout ce qui fait la valeur de chaque acte,
-- et aucune contrainte n'aurait plus pu exiger ce qui est obligatoire — c'est
-- exactement le defaut qu'on corrige ici, la ou `RaisonVisite` disait le geste
-- sans jamais dire son contenu.
--
-- Ce qu'elles partagent — la maille (ruche, date, agent), l'isolation, le
-- trigger de mise a jour — est repete a l'identique, pas factorise : trois
-- tables lisibles valent mieux qu'un heritage SQL.
-- ---------------------------------------------------------------------------


-- === 1. Traitement sanitaire ===============================================
--
-- L'acte qui rend le registre d'elevage opposable. Trois champs y sont
-- reglementaires et non decoratifs : la substance active, la dose reellement
-- appliquee, et le delai de carence — c'est-a-dire l'intervalle pendant lequel
-- le miel de cette ruche ne peut PAS partir en recolte.
CREATE TABLE traitement (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id             TEXT         NOT NULL,
    ruche_id              BIGINT       NOT NULL,
    agent_id              BIGINT       NOT NULL,
    visite_id             BIGINT,
    -- Nom commercial tel qu'il figure sur l'emballage : c'est ce que
    -- l'apiculteur a en main au rucher, et ce qu'un controle demande.
    produit               VARCHAR(120) NOT NULL,
    -- Substance active — la donnee qui permet de raisonner l'alternance et
    -- d'eviter la resistance. Deux noms commerciaux peuvent la partager.
    substance_active      VARCHAR(120),
    cible                 VARCHAR(30)  NOT NULL,
    dose                  NUMERIC(10, 3),
    dose_unite            VARCHAR(10),
    date_debut            DATE         NOT NULL,
    -- NULL tant que le traitement court : un traitement en cours est un etat
    -- normal, pas une saisie incomplete.
    date_fin              DATE,
    delai_carence_jours   INT,
    -- Date a partir de laquelle le miel redevient recoltable. Colonne GENEREE
    -- et non calculee au service, contrairement a la regle des 100 % de
    -- `lot_composition` (V15) : ce n'est pas une regle metier a expliquer a
    -- l'utilisateur, c'est une DONNEE derivee qu'on veut pouvoir filtrer et
    -- indexer (« quelles ruches sont sous carence aujourd'hui ? »). La calculer
    -- en Java obligerait a relire toutes les lignes pour repondre.
    date_retrait          DATE GENERATED ALWAYS AS (date_fin + delai_carence_jours) STORED,
    ordonnance            VARCHAR(120),
    note                  TEXT,
    cree_le               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    maj_le                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_traitement_produit_non_vide CHECK (length(trim(produit)) > 0),
    CONSTRAINT ck_traitement_cible CHECK (cible IN
        ('varroa', 'loque_americaine', 'loque_europeenne', 'nosema',
         'petit_coleoptere', 'fausse_teigne', 'frelon', 'autre')),
    CONSTRAINT ck_traitement_dose CHECK (dose IS NULL OR dose > 0),
    -- Une dose sans unite ne se relit pas : « 2 » ne dit ni 2 ml ni 2 lanieres.
    CONSTRAINT ck_traitement_dose_unite CHECK (
        (dose IS NULL AND dose_unite IS NULL)
        OR (dose IS NOT NULL AND dose_unite IN ('mg', 'g', 'ml', 'l', 'laniere', 'plaquette'))),
    CONSTRAINT ck_traitement_periode CHECK (date_fin IS NULL OR date_fin >= date_debut),
    CONSTRAINT ck_traitement_carence CHECK (delai_carence_jours IS NULL
        OR delai_carence_jours BETWEEN 0 AND 365),
    CONSTRAINT uq_traitement_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_traitement_ruche
        FOREIGN KEY (ruche_id, tenant_id) REFERENCES ruche (id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT fk_traitement_agent
        FOREIGN KEY (agent_id, tenant_id) REFERENCES agent (id, tenant_id) ON DELETE RESTRICT,
    CONSTRAINT fk_traitement_visite
        FOREIGN KEY (visite_id, tenant_id)
        REFERENCES visite (id, tenant_id) ON DELETE SET NULL (visite_id)
);

CREATE INDEX ix_traitement_ruche ON traitement (ruche_id, date_debut DESC);
-- Index partiel : seules les lignes qui portent une carence interessent la
-- question « puis-je recolter ». C'est la majorite en volume, pas en usage.
CREATE INDEX ix_traitement_retrait ON traitement (date_retrait)
    WHERE date_retrait IS NOT NULL;

ALTER TABLE traitement ENABLE ROW LEVEL SECURITY;
ALTER TABLE traitement FORCE  ROW LEVEL SECURITY;
CREATE POLICY p_traitement_tenant ON traitement
    USING      (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));

CREATE TRIGGER tg_traitement_maj BEFORE UPDATE ON traitement
    FOR EACH ROW EXECUTE FUNCTION zumm_touch_maj_le();

COMMENT ON TABLE traitement IS
    'Traitement sanitaire applique a une ruche : produit, dose, cible, carence.';
COMMENT ON COLUMN traitement.date_retrait IS
    'Fin de carence, generee : date_fin + delai_carence_jours. Avant elle, pas de recolte.';


-- === 2. Nourrissement ======================================================
--
-- Deuxieme acte que `RaisonVisite.NOURRISSAGE` ne savait que nommer. Il compte
-- pour deux raisons : le sirop pose avant une hausse contamine la recolte, et
-- le bilan de la saison ne se lit pas sans ce qu'on a rendu a la colonie.
CREATE TABLE nourrissement (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id     TEXT           NOT NULL,
    ruche_id      BIGINT         NOT NULL,
    agent_id      BIGINT         NOT NULL,
    visite_id     BIGINT,
    date_apport   DATE           NOT NULL,
    type_aliment  VARCHAR(20)    NOT NULL,
    quantite      NUMERIC(10, 3) NOT NULL,
    quantite_unite VARCHAR(10)   NOT NULL,
    motif         VARCHAR(20),
    note          TEXT,
    cree_le       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    maj_le        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    -- Les deux sirops sont distingues parce qu'ils ne servent pas a la meme
    -- chose : le 1:1 stimule la ponte au printemps, le 2:1 constitue les
    -- reserves d'hiver. Les confondre rendrait le motif illisible.
    CONSTRAINT ck_nourrissement_type CHECK (type_aliment IN
        ('sirop_1_1', 'sirop_2_1', 'candi', 'pollen', 'substitut_pollen', 'miel', 'eau')),
    CONSTRAINT ck_nourrissement_quantite CHECK (quantite > 0),
    CONSTRAINT ck_nourrissement_unite CHECK (quantite_unite IN ('kg', 'g', 'l', 'ml')),
    CONSTRAINT ck_nourrissement_motif CHECK (motif IS NULL OR motif IN
        ('stimulation', 'hivernage', 'disette', 'secours', 'transhumance', 'autre')),
    CONSTRAINT uq_nourrissement_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_nourrissement_ruche
        FOREIGN KEY (ruche_id, tenant_id) REFERENCES ruche (id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT fk_nourrissement_agent
        FOREIGN KEY (agent_id, tenant_id) REFERENCES agent (id, tenant_id) ON DELETE RESTRICT,
    CONSTRAINT fk_nourrissement_visite
        FOREIGN KEY (visite_id, tenant_id)
        REFERENCES visite (id, tenant_id) ON DELETE SET NULL (visite_id)
);

CREATE INDEX ix_nourrissement_ruche ON nourrissement (ruche_id, date_apport DESC);

ALTER TABLE nourrissement ENABLE ROW LEVEL SECURITY;
ALTER TABLE nourrissement FORCE  ROW LEVEL SECURITY;
CREATE POLICY p_nourrissement_tenant ON nourrissement
    USING      (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));

CREATE TRIGGER tg_nourrissement_maj BEFORE UPDATE ON nourrissement
    FOR EACH ROW EXECUTE FUNCTION zumm_touch_maj_le();

COMMENT ON TABLE nourrissement IS
    'Apport nourricier a une ruche : type, quantite, motif.';


-- === 3. Comptage de varroa =================================================
--
-- La table qui manquait le plus : huit des douze catalogues concurrents nomment
-- le varroa, plusieurs en font un module avec calculateur.
--
-- ---------------------------------------------------------------------------
-- Pourquoi le TAUX n'est pas une colonne
--
-- Il serait tentant de stocker un « taux d'infestation ». Mais il n'a pas la
-- meme unite selon la methode :
--   * lange graisse       -> varroas TOMBES PAR JOUR (chute naturelle) ;
--   * sucre glace, alcool -> varroas POUR CENT ABEILLES (echantillon).
-- Un seul nombre melangeant les deux serait faux, et les seuils d'alerte qu'on
-- en tirerait le seraient aussi. La base garde donc les comptages BRUTS et la
-- methode ; le taux et son verdict sont calcules par `ComptageVarroaService`,
-- au meme titre que la regle des 100 % de V15 : une valeur qu'on doit savoir
-- EXPLIQUER a l'utilisateur ne se cache pas dans une colonne generee.
--
-- Le CHECK garantit en revanche que le denominateur necessaire a la methode est
-- present : sans lui, la ligne serait incalculable — donc inutile.
-- ---------------------------------------------------------------------------
CREATE TABLE comptage_varroa (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id           TEXT        NOT NULL,
    ruche_id            BIGINT      NOT NULL,
    agent_id            BIGINT      NOT NULL,
    visite_id           BIGINT,
    date_comptage       DATE        NOT NULL,
    methode             VARCHAR(20) NOT NULL,
    varroas_comptes     INT         NOT NULL,
    -- Denominateur des methodes par echantillon (sucre glace, alcool, CO2).
    abeilles_echantillon INT,
    -- Denominateur de la chute naturelle : nombre de jours de pose du lange.
    jours_exposition    INT,
    note                TEXT,
    cree_le             TIMESTAMPTZ NOT NULL DEFAULT now(),
    maj_le              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_varroa_methode CHECK (methode IN
        ('lange', 'sucre_glace', 'alcool', 'co2', 'desoperculation')),
    CONSTRAINT ck_varroa_comptes CHECK (varroas_comptes >= 0),
    CONSTRAINT ck_varroa_echantillon CHECK (abeilles_echantillon IS NULL
        OR abeilles_echantillon > 0),
    CONSTRAINT ck_varroa_jours CHECK (jours_exposition IS NULL OR jours_exposition > 0),
    -- Chaque methode exige SON denominateur, et refuse celui de l'autre.
    CONSTRAINT ck_varroa_denominateur CHECK (
        (methode = 'lange'
             AND jours_exposition IS NOT NULL AND abeilles_echantillon IS NULL)
        OR (methode <> 'lange'
             AND abeilles_echantillon IS NOT NULL AND jours_exposition IS NULL)),
    CONSTRAINT uq_varroa_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_varroa_ruche
        FOREIGN KEY (ruche_id, tenant_id) REFERENCES ruche (id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT fk_varroa_agent
        FOREIGN KEY (agent_id, tenant_id) REFERENCES agent (id, tenant_id) ON DELETE RESTRICT,
    CONSTRAINT fk_varroa_visite
        FOREIGN KEY (visite_id, tenant_id)
        REFERENCES visite (id, tenant_id) ON DELETE SET NULL (visite_id)
);

CREATE INDEX ix_varroa_ruche ON comptage_varroa (ruche_id, date_comptage DESC);

ALTER TABLE comptage_varroa ENABLE ROW LEVEL SECURITY;
ALTER TABLE comptage_varroa FORCE  ROW LEVEL SECURITY;
CREATE POLICY p_varroa_tenant ON comptage_varroa
    USING      (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));

CREATE TRIGGER tg_varroa_maj BEFORE UPDATE ON comptage_varroa
    FOR EACH ROW EXECUTE FUNCTION zumm_touch_maj_le();

COMMENT ON TABLE comptage_varroa IS
    'Comptage de varroa : methode et valeurs BRUTES. Le taux est calcule au service.';


-- === 4. Pathologies observees ==============================================
--
-- Une visite peut en constater plusieurs — d'ou une table fille et non des
-- colonnes. C'est ce qui rend la maladie ANALYSABLE : jusqu'ici « loque » ne
-- pouvait s'ecrire que dans `visite.constatations`, en texte libre, ou rien ne
-- se compte ni ne se compare.
CREATE TABLE observation_pathologie (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id   TEXT        NOT NULL,
    visite_id   BIGINT      NOT NULL,
    pathologie  VARCHAR(30) NOT NULL,
    gravite     VARCHAR(10) NOT NULL DEFAULT 'suspectee',
    note        TEXT,
    cree_le     TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Contrairement a `lot_composition` (V15), cette ligne se MODIFIE : une
    -- suspicion se confirme ou s'infirme apres analyse. D'ou `maj_le` et le
    -- trigger, donc l'heritage de `EntiteTenant` cote Java.
    maj_le      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_pathologie_nom CHECK (pathologie IN
        ('varroose', 'loque_americaine', 'loque_europeenne', 'nosemose',
         'petit_coleoptere', 'fausse_teigne', 'frelon_asiatique',
         'couvain_sacciforme', 'mycose', 'pesticide', 'autre')),
    -- « suspectee » est le defaut, et c'est une position honnete : au rucher on
    -- constate un symptome, on ne pose pas un diagnostic de laboratoire.
    CONSTRAINT ck_pathologie_gravite CHECK (gravite IN
        ('suspectee', 'legere', 'moderee', 'severe')),
    -- Une pathologie ne se constate qu'une fois par visite ; deux lignes
    -- identiques seraient un doublon de saisie, pas une aggravation.
    CONSTRAINT uq_pathologie_visite UNIQUE (visite_id, pathologie),
    CONSTRAINT uq_pathologie_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_pathologie_visite
        FOREIGN KEY (visite_id, tenant_id) REFERENCES visite (id, tenant_id) ON DELETE CASCADE
);

CREATE INDEX ix_pathologie_visite ON observation_pathologie (visite_id);

ALTER TABLE observation_pathologie ENABLE ROW LEVEL SECURITY;
ALTER TABLE observation_pathologie FORCE  ROW LEVEL SECURITY;
CREATE POLICY p_pathologie_tenant ON observation_pathologie
    USING      (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));

CREATE TRIGGER tg_pathologie_maj BEFORE UPDATE ON observation_pathologie
    FOR EACH ROW EXECUTE FUNCTION zumm_touch_maj_le();

COMMENT ON TABLE observation_pathologie IS
    'Maladies et ravageurs NOMMES constates lors d''une visite (une ligne par pathologie).';


-- === 5. Observations structurees et meteo figee sur la visite ==============
--
-- Ces colonnes sortent de `visite.constatations` ce qui doit pouvoir se
-- compter. Le texte libre reste — il porte ce qu'aucune case ne prevoit — mais
-- il cesse d'etre la seule trace du couvain, des reserves et des cellules
-- royales.
--
-- Toutes NULLABLES : une visite eclair (« poser une hausse ») ne remplit rien,
-- et exiger la grille complete ferait sauter la saisie plutot que la completer.
ALTER TABLE visite
    ADD COLUMN couvain_oeufs           BOOLEAN,
    ADD COLUMN couvain_larves          BOOLEAN,
    ADD COLUMN couvain_opercule        BOOLEAN,
    ADD COLUMN motif_ponte             VARCHAR(15),
    ADD COLUMN reine_vue               BOOLEAN,
    ADD COLUMN cellules_royales        INT,
    ADD COLUMN cellules_royales_cause  VARCHAR(15),
    ADD COLUMN cadres_couvain          INT,
    ADD COLUMN cadres_miel             INT,
    ADD COLUMN cadres_pollen           INT,
    ADD COLUMN temperament             VARCHAR(10),
    -- Meteo FIGEE : recopiee du fournisseur au moment de la visite, jamais
    -- rappelee ensuite. Une prevision se revise, un releve non — et c'est le
    -- releve qui permet de correler conditions et production. Aller rechercher
    -- la meteo du 12 mars six mois plus tard rendrait la correlation fausse.
    ADD COLUMN meteo_temperature_c     NUMERIC(4, 1),
    ADD COLUMN meteo_humidite_pct      INT,
    ADD COLUMN meteo_vent_kmh          NUMERIC(5, 1),
    ADD COLUMN meteo_source            VARCHAR(20);

ALTER TABLE visite
    ADD CONSTRAINT ck_visite_motif_ponte CHECK (motif_ponte IS NULL OR motif_ponte IN
        ('compact', 'lacunaire', 'irregulier', 'absent')),
    -- La CAUSE ne se saisit que s'il y a des cellules : « supersedure, zero
    -- cellule » est une contradiction, pas une observation.
    ADD CONSTRAINT ck_visite_cellules CHECK (cellules_royales IS NULL
        OR cellules_royales >= 0),
    ADD CONSTRAINT ck_visite_cellules_cause CHECK (
        cellules_royales_cause IS NULL
        OR (cellules_royales IS NOT NULL AND cellules_royales > 0
            AND cellules_royales_cause IN ('essaimage', 'supersedure', 'urgence'))),
    ADD CONSTRAINT ck_visite_cadres CHECK (
        (cadres_couvain IS NULL OR cadres_couvain BETWEEN 0 AND 40)
        AND (cadres_miel IS NULL OR cadres_miel BETWEEN 0 AND 40)
        AND (cadres_pollen IS NULL OR cadres_pollen BETWEEN 0 AND 40)),
    ADD CONSTRAINT ck_visite_temperament CHECK (temperament IS NULL OR temperament IN
        ('doux', 'normal', 'agressif')),
    ADD CONSTRAINT ck_visite_meteo_humidite CHECK (meteo_humidite_pct IS NULL
        OR meteo_humidite_pct BETWEEN 0 AND 100),
    ADD CONSTRAINT ck_visite_meteo_vent CHECK (meteo_vent_kmh IS NULL OR meteo_vent_kmh >= 0),
    ADD CONSTRAINT ck_visite_meteo_source CHECK (meteo_source IS NULL
        OR meteo_source IN ('open-meteo', 'simulation', 'saisie'));

COMMENT ON COLUMN visite.meteo_temperature_c IS
    'Meteo FIGEE au moment de la visite (US-029). Ne jamais la recalculer a posteriori.';


-- === 6. Colonnes bon marche sur la ruche ===================================
--
-- Trois colonnes et une contrainte, pour trois manques que quatre concurrents
-- posent des la creation d'une ruche.
ALTER TABLE ruche
    -- `modele` reste le texte libre (« Dadant 10 cadres, fond grillage Nicot »).
    -- `type_ruche` est le REFERENTIEL au-dessus : c'est lui qui rend possible
    -- une statistique par type, qu'un texte libre interdisait.
    ADD COLUMN type_ruche    VARCHAR(15),
    ADD COLUMN couleur       VARCHAR(15),
    ADD COLUMN origine       VARCHAR(20),
    -- Distingue ce que `EtatRuche.CLOTUREE` confondait : une ruche morte, une
    -- ruche vendue et une ruche fusionnee etaient indiscernables en statistique.
    ADD COLUMN cause_cloture VARCHAR(15);

ALTER TABLE ruche
    ADD CONSTRAINT ck_ruche_type CHECK (type_ruche IS NULL OR type_ruche IN
        ('langstroth', 'dadant', 'warre', 'voirnot', 'top_bar', 'kenyane', 'autre')),
    ADD CONSTRAINT ck_ruche_couleur CHECK (couleur IS NULL OR couleur IN
        ('blanc', 'jaune', 'orange', 'rouge', 'vert', 'bleu', 'violet', 'gris', 'bois')),
    ADD CONSTRAINT ck_ruche_origine CHECK (origine IS NULL OR origine IN
        ('essaim_capture', 'essaim_achete', 'division', 'nucleus', 'paquet', 'achat', 'autre')),
    -- Une cause de cloture sur une ruche active n'a pas de sens : la colonne ne
    -- se remplit qu'avec l'etat qui la justifie.
    ADD CONSTRAINT ck_ruche_cause_cloture CHECK (
        cause_cloture IS NULL
        OR (etat = 'cloturee' AND cause_cloture IN
            ('morte', 'fusionnee', 'vendue', 'volee', 'reformee', 'essaimee', 'autre')));

COMMENT ON COLUMN ruche.type_ruche IS
    'Referentiel de type (Langstroth, Dadant, Warre...). `modele` reste le detail libre.';
COMMENT ON COLUMN ruche.cause_cloture IS
    'Pourquoi la ruche est cloturee. Ne se remplit que si etat = cloturee.';
