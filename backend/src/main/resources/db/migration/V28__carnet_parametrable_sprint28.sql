-- ===========================================================================
-- V28 — Le carnet parametrable (SPRINT-28, lot I)
--
-- Ce que le SPRINT-20 a structure, il l'a FIGE : onze colonnes d'observation
-- sur `visite`, les memes pour tout le monde. Les concurrents qui gagnent sur
-- ce terrain (HiveTracks, BeeKeepPal) laissent l'apiculteur choisir ses cases.
-- Cinq lignes du §3 et du §6 du document d'ecart ferment ici.
--
-- ---------------------------------------------------------------------------
-- LE PIEGE, et la sortie retenue
--
-- Un formulaire parametrable DETRUIT la statistique s'il autorise des champs
-- libres : dix exploitations inventent dix libelles pour la meme observation,
-- et plus rien ne se compte ni ne se compare — exactement le defaut que la
-- V19 corrigeait en remplacant du texte libre par des colonnes.
--
-- La sortie est un referentiel FERME. `point_observation` est ecrit par une
-- migration et par rien d'autre : le role applicatif n'a que le SELECT dessus
-- (voir le REVOKE en fin de fichier). On ACTIVE des cases existantes ; on n'en
-- invente pas. Un gabarit choisit un sous-ensemble, il n'ajoute jamais un point.
--
-- ---------------------------------------------------------------------------
-- LE NOYAU RESTE DES COLONNES
--
-- Les onze colonnes de la V19 (couvain, ponte, reine, cellules royales, cadres,
-- temperament) ne migrent PAS dans le referentiel. Elles sont typees, indexees,
-- et lues par le moteur de regles, les indices de colonie et la fiche
-- d'inspection : les transformer en lignes cle-valeur aurait casse tout cela
-- pour un gain d'uniformite qui n'interesse personne.
--
-- Le gabarit les ALLUME ou les ETEINT (quatre booleens `noyau_*`), il ne les
-- deplace pas. Une exploitation qui ne compte jamais les cadres masque la
-- section ; la colonne reste, et la visite qui l'avait remplie garde sa valeur.
--
-- Consequence a tenir : un point du referentiel ne redit JAMAIS un champ du
-- noyau. Le referentiel decrit ce qu'on VOIT en plus ; `observation_pathologie`
-- (V19) nomme ce qu'on DIAGNOSTIQUE. Un point « traces de fausse teigne »
-- doublonnerait la pathologie du meme nom : il n'y est pas.
-- ===========================================================================


-- === 1. Referentiel ferme des points d'observation =========================
--
-- SANS `tenant_id`, et c'est la decision : ce referentiel est le meme pour tout
-- le monde PAR CONSTRUCTION. Le rendre modifiable par tenant reintroduirait le
-- champ libre par la porte de derriere — chacun ses points, plus aucune
-- comparaison possible entre deux ruchers, ni aucune statistique de parc.
--
-- Il rejoint donc `abonnement_calendrier` (V21) et `SPRING_SESSION` (V17) dans
-- la courte liste des tables hors perimetre multi-tenant. Contrairement a
-- elles, son cloisonnement n'a rien d'applicatif : il n'y a RIEN a cloisonner,
-- puisque la table ne contient aucune donnee d'exploitation.
CREATE TABLE point_observation (
    -- Le code est la cle : c'est lui qui voyage dans l'API, dans les exports et
    -- dans les fichiers de traduction du front. Un identifiant technique aurait
    -- rendu tout export illisible sans jointure.
    code            VARCHAR(40)  PRIMARY KEY,
    categorie       VARCHAR(20)  NOT NULL,
    -- `booleen` : la case cochee, le cas dominant. `echelle` : 0 a 3, pour ce
    -- qui a une INTENSITE (propolisation, mortalite devant la ruche) et dont un
    -- oui/non perdrait l'essentiel.
    type_valeur     VARCHAR(10)  NOT NULL,
    -- Libelle francais. Il n'est PAS la traduction : les trois langues vivent
    -- dans les fichiers de locale du front, indexes par ce meme code. Il sert
    -- au serveur (fiche PDF, export CSV, qui sont en francais) et de repli pour
    -- un client qui rencontrerait un point ajoute apres sa derniere mise a jour.
    libelle         VARCHAR(80)  NOT NULL,
    ordre           INT          NOT NULL,
    CONSTRAINT ck_point_type CHECK (type_valeur IN ('booleen', 'echelle')),
    CONSTRAINT ck_point_categorie CHECK (categorie IN
        ('population', 'reine', 'reserves', 'batisse', 'sanitaire',
         'materiel', 'environnement', 'geste'))
);

COMMENT ON TABLE point_observation IS
    'Referentiel FERME des points d''observation (SPRINT-28). Ecrit par migration '
    'uniquement : le role applicatif n''a que le SELECT. Un gabarit en choisit un '
    'sous-ensemble, il n''en ajoute jamais.';


-- === 2. Gabarit d'inspection ===============================================
--
-- Par tenant, celui-la : le choix des cases est une decision d'exploitation.
CREATE TABLE gabarit_inspection (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id   TEXT         NOT NULL,
    nom         VARCHAR(60)  NOT NULL,
    description VARCHAR(200),
    -- Les quatre sections du noyau V19. Elles s'eteignent, elles ne se
    -- suppriment pas : masquer n'est pas effacer, et une visite deja saisie
    -- garde ce qu'elle portait.
    noyau_couvain     BOOLEAN NOT NULL DEFAULT TRUE,
    noyau_reine       BOOLEAN NOT NULL DEFAULT TRUE,
    noyau_cadres      BOOLEAN NOT NULL DEFAULT TRUE,
    noyau_temperament BOOLEAN NOT NULL DEFAULT TRUE,
    -- Un seul gabarit par defaut par tenant — index unique partiel plus bas.
    par_defaut  BOOLEAN      NOT NULL DEFAULT FALSE,
    -- Un gabarit se retire du service sans se supprimer : les visites qui s'en
    -- sont servies restent lisibles, et leur gabarit reste nommable.
    actif       BOOLEAN      NOT NULL DEFAULT TRUE,
    cree_le     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    maj_le      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_gabarit_nom CHECK (length(trim(nom)) > 0),
    CONSTRAINT uq_gabarit_nom UNIQUE (tenant_id, nom),
    CONSTRAINT uq_gabarit_id_tenant UNIQUE (id, tenant_id)
);

CREATE UNIQUE INDEX uq_gabarit_defaut ON gabarit_inspection (tenant_id)
    WHERE par_defaut;

ALTER TABLE gabarit_inspection ENABLE ROW LEVEL SECURITY;
ALTER TABLE gabarit_inspection FORCE  ROW LEVEL SECURITY;
CREATE POLICY p_gabarit_tenant ON gabarit_inspection
    USING      (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));

CREATE TRIGGER tg_gabarit_maj BEFORE UPDATE ON gabarit_inspection
    FOR EACH ROW EXECUTE FUNCTION zumm_touch_maj_le();


-- === 3. Points retenus par un gabarit ======================================
CREATE TABLE gabarit_point (
    gabarit_id  BIGINT      NOT NULL,
    tenant_id   TEXT        NOT NULL,
    point_code  VARCHAR(40) NOT NULL,
    ordre       INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (gabarit_id, point_code),
    CONSTRAINT fk_gabarit_point_gabarit
        FOREIGN KEY (gabarit_id, tenant_id)
        REFERENCES gabarit_inspection (id, tenant_id) ON DELETE CASCADE,
    -- Cle etrangere vers le referentiel : un gabarit ne peut pas retenir un
    -- point qui n'existe pas. C'est la fermeture du referentiel, exprimee la ou
    -- la base peut la faire respecter.
    CONSTRAINT fk_gabarit_point_referentiel
        FOREIGN KEY (point_code) REFERENCES point_observation (code)
);

ALTER TABLE gabarit_point ENABLE ROW LEVEL SECURITY;
ALTER TABLE gabarit_point FORCE  ROW LEVEL SECURITY;
CREATE POLICY p_gabarit_point_tenant ON gabarit_point
    USING      (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));


-- === 4. Releve d'un point sur une visite ===================================
--
-- L'ABSENCE de ligne est une information : le point n'a pas ete regarde. Une
-- case decochee vaut `valeur_bool = FALSE` — « regarde, absent ». Confondre les
-- deux ferait compter comme « rien vu » des visites ou personne n'a ouvert la
-- ruche, et toute statistique construite dessus serait fausse dans le sens
-- rassurant, qui est le pire.
CREATE TABLE releve_observation (
    visite_id      BIGINT      NOT NULL,
    tenant_id      TEXT        NOT NULL,
    point_code     VARCHAR(40) NOT NULL,
    valeur_bool    BOOLEAN,
    valeur_echelle SMALLINT,
    PRIMARY KEY (visite_id, point_code),
    -- Exactement une valeur, celle qui correspond au type du point. Le TYPE ne
    -- se verifie pas ici — il vit dans une autre table, hors de portee d'un
    -- CHECK ; c'est le service qui refuse une echelle sur un point booleen, et
    -- un test d'integration le prouve.
    CONSTRAINT ck_releve_une_valeur CHECK (num_nonnulls(valeur_bool, valeur_echelle) = 1),
    CONSTRAINT ck_releve_echelle CHECK (valeur_echelle IS NULL
        OR valeur_echelle BETWEEN 0 AND 3),
    CONSTRAINT fk_releve_visite
        FOREIGN KEY (visite_id, tenant_id) REFERENCES visite (id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT fk_releve_referentiel
        FOREIGN KEY (point_code) REFERENCES point_observation (code)
);

CREATE INDEX ix_releve_point ON releve_observation (point_code, tenant_id);

ALTER TABLE releve_observation ENABLE ROW LEVEL SECURITY;
ALTER TABLE releve_observation FORCE  ROW LEVEL SECURITY;
CREATE POLICY p_releve_tenant ON releve_observation
    USING      (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));


-- === 5. Referentiel de produits de traitement ==============================
--
-- « Referentiel de traitements pre-renseigne » (§3). Meme regime que
-- `point_observation` : ferme, sans tenant, en lecture seule pour
-- l'application.
--
-- CE QU'IL EST, ET CE QU'IL N'EST PAS. Il pre-remplit un formulaire, il ne fait
-- pas autorite. La notice et l'AMM du pays font foi — d'ou la colonne
-- `mention`, affichee telle quelle a la saisie. Et le traitement enregistre
-- garde sa PROPRE copie du delai (`traitement.delai_carence_jours`, V19) :
-- corriger le referentiel demain ne doit pas reecrire un registre d'elevage
-- d'hier, qui est un document opposable.
--
-- Ce que ce referentiel apprend, et que le seul delai en jours ne dit pas : sur
-- la plupart des varroacides la contrainte reelle n'est pas une carence, c'est
-- « hausses retirees ». Un delai de zero jour lu seul se comprend comme « on
-- peut recolter » — d'ou la colonne dediee, et non une phrase dans une note.
CREATE TABLE produit_traitement (
    code                VARCHAR(40)  PRIMARY KEY,
    nom                 VARCHAR(80)  NOT NULL,
    substance_active    VARCHAR(120) NOT NULL,
    cible               VARCHAR(30)  NOT NULL,
    forme               VARCHAR(20)  NOT NULL,
    delai_carence_jours INT          NOT NULL DEFAULT 0,
    -- La contrainte operationnelle qui compte au rucher.
    hausses_retirees    BOOLEAN      NOT NULL DEFAULT TRUE,
    ordonnance_requise  BOOLEAN      NOT NULL DEFAULT FALSE,
    mention             VARCHAR(200),
    CONSTRAINT ck_produit_cible CHECK (cible IN
        ('varroa', 'loque_americaine', 'loque_europeenne', 'nosema',
         'petit_coleoptere', 'fausse_teigne', 'frelon', 'autre')),
    CONSTRAINT ck_produit_forme CHECK (forme IN
        ('laniere', 'gel', 'plaquette', 'solution', 'sirop', 'poudre', 'sublimation')),
    CONSTRAINT ck_produit_carence CHECK (delai_carence_jours BETWEEN 0 AND 365)
);

COMMENT ON TABLE produit_traitement IS
    'Referentiel indicatif de produits de traitement (SPRINT-28). Pre-remplit la '
    'saisie ; la notice et l''AMM du pays font foi. Le traitement enregistre garde '
    'sa propre copie du delai de carence.';


-- === 6. Ordonnance veterinaire =============================================
--
-- `traitement.ordonnance` (V19) portait deja une reference. Il lui manquait de
-- quoi la rendre verifiable : QUI l'a signee et QUAND. Le document lui-meme
-- s'attache par `photo` (point 7) — un scan d'ordonnance est une image, et le
-- stockage de fichiers existe depuis la V20 pour cinq cibles.
ALTER TABLE traitement
    ADD COLUMN ordonnance_veterinaire VARCHAR(120),
    ADD COLUMN ordonnance_date        DATE;

ALTER TABLE traitement
    -- Une date d'ordonnance sans ordonnance ne designe rien. L'inverse reste
    -- permis : une reference notee sur le terrain, les details completes apres.
    ADD CONSTRAINT ck_traitement_ordonnance CHECK (
        (ordonnance_veterinaire IS NULL AND ordonnance_date IS NULL)
        OR ordonnance IS NOT NULL);


-- === 7. La photo gagne une sixieme cible ===================================
--
-- Le scan de l'ordonnance. La V20 avait pose l'invariant « exactement une
-- cible » ; il se maintient a six comme il tenait a cinq.
ALTER TABLE photo ADD COLUMN traitement_id BIGINT;

ALTER TABLE photo
    ADD CONSTRAINT fk_photo_traitement
        FOREIGN KEY (traitement_id, tenant_id)
        REFERENCES traitement (id, tenant_id) ON DELETE CASCADE;

ALTER TABLE photo DROP CONSTRAINT ck_photo_cible_unique;
ALTER TABLE photo
    ADD CONSTRAINT ck_photo_cible_unique CHECK (
        num_nonnulls(visite_id, ruche_id, site_id, suivi_reine_id, recolte_id,
                     traitement_id) = 1);

COMMENT ON CONSTRAINT ck_photo_cible_unique ON photo IS
    'Une photo a EXACTEMENT une cible : visite, ruche, site, reine, recolte ou '
    'traitement (le scan de l''ordonnance).';

CREATE INDEX ix_photo_traitement ON photo (traitement_id) WHERE traitement_id IS NOT NULL;


-- === 8. Humidite du miel recolte ===========================================
--
-- Le refractometre, reporte du lot E. La mesure se RANGE ici parce qu'elle
-- decide de la conservation : au-dela de 18 %, le miel fermente, et un lot mis
-- en pot a 20 % se perd en cave sans que rien ne l'ait signale.
--
-- Reservee au miel : un taux d'humidite sur un essaim ou une reine ne veut rien
-- dire. La contrainte le dit, comme `ck_recolte_unite_produit` (V27) le disait
-- pour l'unite.
ALTER TABLE recolte ADD COLUMN humidite_pct NUMERIC(4, 1);

ALTER TABLE recolte
    ADD CONSTRAINT ck_recolte_humidite CHECK (
        humidite_pct IS NULL
        OR (type_produit = 'miel' AND humidite_pct BETWEEN 10 AND 30));


-- === 9. Le referentiel des points ==========================================
--
-- Quarante-quatre points, huit categories. « Environ cinquante » est le chiffre
-- annonce par HiveTracks ; on n'en compte pas cinquante pour l'annoncer aussi.
--
-- Ce qui n'y est PAS, et pourquoi :
--   * rien qui redise le noyau V19 (oeufs, larves, opercule, reine vue,
--     cellules royales, cadres, temperament) — deux sources pour le meme fait
--     divergent toujours ;
--   * rien qui redise `observation_pathologie` : la teigne, le frelon, la loque
--     se NOMMENT dans cette table-la, avec une gravite. Ce referentiel-ci
--     enregistre des SYMPTOMES (ailes deformees, dysenterie, odeur) que l'on
--     voit sans diagnostiquer ;
--   * rien qui redise un acte deja trace ailleurs (traitement pose,
--     nourrissement, essaim recolte, photo prise).
INSERT INTO point_observation (code, categorie, type_valeur, libelle, ordre) VALUES
    -- Population (5)
    ('pop_forte',                'population',    'booleen', 'Population forte',                      10),
    ('pop_faible',               'population',    'booleen', 'Population faible',                     11),
    ('activite_entree',          'population',    'echelle', 'Activite au trou de vol',               12),
    ('faux_bourdons',            'population',    'booleen', 'Faux-bourdons presents',                13),
    ('essaimage_signes',         'population',    'booleen', 'Signes de preparation a l''essaimage',  14),
    -- Reine, hors noyau (4)
    ('reine_marquee',            'reine',         'booleen', 'Reine marquee',                         20),
    ('reine_clippee',            'reine',         'booleen', 'Reine clippee',                         21),
    ('reine_remplacee',          'reine',         'booleen', 'Reine remplacee ce jour',               22),
    ('ponte_bourdonneuse',       'reine',         'booleen', 'Ponte de bourdonneuse',                 23),
    -- Reserves (5)
    ('reserves_miel_suffisantes','reserves',      'booleen', 'Reserves de miel suffisantes',          30),
    ('reserves_pollen_suffisantes','reserves',    'booleen', 'Reserves de pollen suffisantes',        31),
    ('nourrisseur_en_place',     'reserves',      'booleen', 'Nourrisseur en place',                  32),
    ('candi_consomme',           'reserves',      'booleen', 'Candi consomme',                        33),
    ('eau_a_proximite',          'reserves',      'booleen', 'Point d''eau a proximite',              34),
    -- Batisse et cadres (9)
    ('cadres_a_reformer',        'batisse',       'booleen', 'Cadres noirs a reformer',               40),
    ('cire_gaufree_posee',       'batisse',       'booleen', 'Cire gaufree posee',                    41),
    ('batisses_sauvages',        'batisse',       'booleen', 'Batisses sauvages',                     42),
    ('propolisation',            'batisse',       'echelle', 'Propolisation',                         43),
    ('cadres_ajoutes',           'batisse',       'booleen', 'Cadres ajoutes',                        44),
    ('hausse_posee',             'batisse',       'booleen', 'Hausse posee',                          45),
    ('hausse_retiree',           'batisse',       'booleen', 'Hausse retiree',                        46),
    ('grille_a_reine',           'batisse',       'booleen', 'Grille a reine en place',               47),
    ('partition_posee',          'batisse',       'booleen', 'Partition posee',                       48),
    -- Sanitaire : des SYMPTOMES, jamais un diagnostic (5)
    ('varroas_visibles',         'sanitaire',     'echelle', 'Varroas visibles sur les abeilles',     50),
    ('ailes_deformees',          'sanitaire',     'booleen', 'Abeilles aux ailes deformees',          51),
    ('mortalite_devant_ruche',   'sanitaire',     'echelle', 'Mortalite devant la ruche',             52),
    ('traces_dysenterie',        'sanitaire',     'booleen', 'Traces de dysenterie',                  53),
    ('odeur_anormale',           'sanitaire',     'booleen', 'Odeur anormale au couvain',             54),
    -- Materiel de la ruche (7)
    ('plancher_propre',          'materiel',      'booleen', 'Plancher propre',                       60),
    ('toit_etanche',             'materiel',      'booleen', 'Toit etanche',                          61),
    ('corps_a_remplacer',        'materiel',      'booleen', 'Corps a remplacer',                     62),
    ('support_stable',           'materiel',      'booleen', 'Support stable',                        63),
    ('identification_lisible',   'materiel',      'booleen', 'Identification lisible',                64),
    ('sangle_posee',             'materiel',      'booleen', 'Sangle posee',                          65),
    ('reducteur_entree',         'materiel',      'booleen', 'Reducteur d''entree en place',          66),
    -- Environnement du rucher (4)
    ('vegetation_a_couper',      'environnement', 'booleen', 'Vegetation a couper',                   70),
    ('traces_predateur',         'environnement', 'booleen', 'Traces de predateur',                   71),
    ('nuisance_voisinage',       'environnement', 'booleen', 'Nuisance signalee par le voisinage',    72),
    ('floraison_en_cours',       'environnement', 'booleen', 'Floraison en cours a proximite',        73),
    -- Gestes effectues, hors actes deja traces ailleurs (5)
    ('ruche_nettoyee',           'geste',         'booleen', 'Ruche nettoyee',                        80),
    ('cadres_permutes',          'geste',         'booleen', 'Cadres permutes',                       81),
    ('colonie_deplacee',         'geste',         'booleen', 'Colonie deplacee sur le rucher',        82),
    ('echantillon_preleve',      'geste',         'booleen', 'Echantillon preleve pour analyse',      83),
    ('tenue_sur_cadre',          'geste',         'echelle', 'Tenue des abeilles sur le cadre',       84);


-- === 10. Le referentiel des produits =======================================
--
-- Douze produits de lutte contre le varroa, plus une entree generique pour
-- l'acide oxalique en degouttement. Le choix de s'en tenir au varroa n'est pas
-- une paresse : c'est la seule cible pour laquelle des medicaments sont
-- couramment autorises en rucher. Les antibiotiques contre les loques ne le
-- sont pas dans l'Union europeenne, et pre-remplir un formulaire avec des
-- produits interdits serait pire qu'un formulaire vide.
--
-- Les delais sont ceux couramment portes par les notices ; `mention` renvoie a
-- la notice, qui reste la reference. Aucune de ces lignes ne dispense d'une
-- ordonnance la ou la reglementation en exige une.
INSERT INTO produit_traitement
    (code, nom, substance_active, cible, forme, delai_carence_jours,
     hausses_retirees, ordonnance_requise, mention) VALUES
    ('apivar',       'Apivar',        'amitraze',                    'varroa', 'laniere',     0, TRUE,  FALSE,
     'Hausses retirees pendant tout le traitement. Duree 10 a 12 semaines. Se referer a la notice.'),
    ('apitraz',      'Apitraz',       'amitraze',                    'varroa', 'laniere',     0, TRUE,  FALSE,
     'Hausses retirees. Alterner les substances actives d''une annee sur l''autre.'),
    ('apiguard',     'Apiguard',      'thymol',                      'varroa', 'gel',         0, TRUE,  FALSE,
     'Efficacite dependante de la temperature exterieure. Peut faire deserter le couvain.'),
    ('thymovar',     'Thymovar',      'thymol',                      'varroa', 'plaquette',   0, TRUE,  FALSE,
     'Hausses retirees. Le thymol peut marquer le gout du miel des reserves.'),
    ('apilifevar',   'Api Life Var',  'thymol, eucalyptol, menthol', 'varroa', 'plaquette',   0, TRUE,  FALSE,
     'Hausses retirees. Se referer a la notice pour la temperature d''application.'),
    ('varromed',     'VarroMed',      'acide formique, acide oxalique', 'varroa', 'solution', 0, TRUE, FALSE,
     'Hausses retirees. Utilisable en presence de couvain selon la notice.'),
    ('oxybee',       'Oxybee',        'acide oxalique',              'varroa', 'solution',    0, TRUE,  FALSE,
     'Degouttement hors couvain. Hausses retirees.'),
    ('apibioxal',    'Api-Bioxal',    'acide oxalique',              'varroa', 'poudre',      0, TRUE,  FALSE,
     'Degouttement ou sublimation selon la notice. Hausses retirees.'),
    ('maqs',         'MAQS',          'acide formique',              'varroa', 'plaquette',   0, TRUE,  FALSE,
     'Traitement court. Temperature d''application encadree par la notice.'),
    ('formicpro',    'Formic Pro',    'acide formique',              'varroa', 'plaquette',   0, TRUE,  FALSE,
     'Traitement court. Temperature d''application encadree par la notice.'),
    ('polyvar',      'PolyVar Yellow','flumethrine',                 'varroa', 'laniere',     0, TRUE,  FALSE,
     'Pose a l''entree de la ruche. Hausses retirees.'),
    ('bayvarol',     'Bayvarol',      'flumethrine',                 'varroa', 'laniere',     0, TRUE,  FALSE,
     'Hausses retirees. Surveiller l''apparition de resistances.'),
    ('oxalique_deg', 'Acide oxalique (degouttement)', 'acide oxalique', 'varroa', 'solution', 0, TRUE, FALSE,
     'Preparation extemporanee : n''utiliser qu''une specialite autorisee dans le pays d''exercice.');


-- === 11. Deux referentiels en LECTURE SEULE ================================
--
-- La V3 a pose des droits par defaut qui donnent le DML au role applicatif sur
-- toute table creee ensuite. Ces deux-la doivent y echapper : c'est ce REVOKE,
-- et lui seul, qui rend les referentiels reellement fermes. Sans lui, la
-- fermeture ne serait qu'une intention ecrite dans un commentaire.
REVOKE INSERT, UPDATE, DELETE ON point_observation  FROM ${role_app};
REVOKE INSERT, UPDATE, DELETE ON produit_traitement FROM ${role_app};
