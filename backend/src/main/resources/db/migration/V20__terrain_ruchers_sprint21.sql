-- ===========================================================================
-- V20 — Terrain, ruchers et ruches (SPRINT-21)
--
-- Repond au §1 de `docs/ECART-CONCURRENTS.md`, la seule section ou Zumm restait
-- en retard sur ce que DOUZE catalogues demandent des la creation d'un rucher.
-- Huit lignes y etaient ❌ ou 🟡 ; cette migration porte la part « donnee » des
-- six qui en ont une.
--
-- Contenu :
--   1. site                — adresse postale, type de rucher, exposition
--   2. ressource_florale   — sources de nectar declarees autour d'un site
--   3. emplacement_site    — historique des positions (transhumance)
--   4. division            — la division comme evenement, et la FILIATION
--   5. capture_essaim      — l'autre porte d'entree d'une colonie
--   6. photo               — rattachement a autre chose qu'une visite
--
-- ---------------------------------------------------------------------------
-- Ce qui n'est PAS ici, et pourquoi
--
-- `capture_essaim` ne porte AUCUNE coordonnee. La tentation etait forte — on
-- capture un essaim quelque part — mais une colonne de position est une surface
-- de fuite de plus a filtrer (invariant `PolitiquePositions`), pour une donnee
-- dont personne ne fait rien : le lieu de capture d'un essaim ne se cartographie
-- pas, il se raconte (« haie du voisin, chemin des Vignes »). Un champ `lieu`
-- libre le dit mieux et ne se trilatere pas.
--
-- Le CALENDRIER de floraison n'est pas ici non plus : `ressource_florale` dit
-- QUELLES ressources entourent un rucher, jamais QUAND elles fleurissent. Les
-- dates relevent de l'ecart 5 du §11 (couche d'occupation du sol et miellees),
-- qui suppose d'abord de trancher le referentiel geographique — decision produit
-- que cette migration n'a pas a preempter.
-- ===========================================================================


-- === 1. Identite postale et caractere du site ==============================
--
-- Un rucher n'a jusqu'ici qu'un couple de coordonnees. C'est suffisant pour une
-- carte, insuffisant pour tout le reste : un vehicule se guide a l'adresse, un
-- courrier de declaration s'envoie a une commune, et un controle sanitaire
-- demande ou se trouve le rucher en clair. ApiManager, HiveBook et BeeKeepPal
-- demandent ces champs des la creation.
--
-- `pays` en ISO 3166-1 alpha-2 : deux caracteres, comparables, traduisibles a
-- l'ecran. Le nom du pays en toutes lettres aurait ete intraduisible dans un
-- produit trilingue — « Tunisie / Tunisia / تونس » n'est pas une donnee, c'est
-- un affichage.
ALTER TABLE site
    ADD COLUMN adresse_rue  VARCHAR(160),
    ADD COLUMN code_postal  VARCHAR(12),
    ADD COLUMN ville        VARCHAR(80),
    ADD COLUMN pays         VARCHAR(2),
    -- Le TYPE de rucher commande la lecture de tout le reste : un rucher de
    -- fecondation ne se juge pas au poids, un rucher de transhumance n'a pas
    -- vocation a rester en place.
    ADD COLUMN type_site    VARCHAR(15),
    -- L'exposition explique une difference de developpement entre deux ruchers
    -- voisins mieux que n'importe quelle mesure.
    ADD COLUMN exposition   VARCHAR(10);

ALTER TABLE site
    ADD CONSTRAINT ck_site_pays CHECK (pays IS NULL OR pays ~ '^[A-Z]{2}$'),
    ADD CONSTRAINT ck_site_type CHECK (type_site IS NULL OR type_site IN
        ('sedentaire', 'transhumance', 'fecondation', 'elevage', 'conservatoire', 'autre')),
    ADD CONSTRAINT ck_site_exposition CHECK (exposition IS NULL OR exposition IN
        ('nord', 'nord_est', 'est', 'sud_est', 'sud', 'sud_ouest', 'ouest', 'nord_ouest'));

COMMENT ON COLUMN site.adresse_rue IS
    'Adresse postale du rucher. AUSSI SENSIBLE que la position : masquee par PolitiquePositions.';
COMMENT ON COLUMN site.pays IS
    'Code ISO 3166-1 alpha-2 (FR, TN, MA...). Le libelle est affaire d''affichage, pas de donnee.';


-- === 2. Ressources florales declarees ======================================
--
-- « Sources de nectar » du §1, et rien de plus. Une table fille plutot qu'une
-- colonne texte, pour la meme raison qui a fait naitre `observation_pathologie`
-- en V19 : une liste NOMMEE se compte, se filtre et se traduit ; un champ libre
-- « acacia, chataignier » ne fait aucune des trois.
--
-- Le vocabulaire volontairement mixte — colza et jujubier, chataignier et
-- palmier dattier — suit le marche que vise le trilinguisme FR/EN/AR. Un
-- referentiel purement metropolitain aurait reproduit le defaut de portabilite
-- reproche a BeeGIS au §2 du meme document.
CREATE TABLE ressource_florale (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id   TEXT        NOT NULL,
    site_id     BIGINT      NOT NULL,
    ressource   VARCHAR(20) NOT NULL,
    -- Distance approximative de la ressource au rucher. Bornee a 20 km : au-dela
    -- du rayon de butinage le plus genereux, la ressource n'est plus une
    -- ressource, c'est un paysage.
    distance_m  INT,
    note        TEXT,
    cree_le     TIMESTAMPTZ NOT NULL DEFAULT now(),
    maj_le      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_ressource_valeur CHECK (ressource IN (
        'colza', 'tournesol', 'acacia', 'chataignier', 'tilleul', 'lavande',
        'bruyere', 'luzerne', 'sarrasin', 'verger', 'agrumes', 'eucalyptus',
        'thym', 'romarin', 'jujubier', 'palmier_dattier', 'prairie', 'foret',
        'garrigue', 'autre')),
    CONSTRAINT ck_ressource_distance CHECK (distance_m IS NULL
        OR distance_m BETWEEN 0 AND 20000),
    -- Une ressource ne se declare qu'une fois par site : deux lignes « acacia »
    -- ne diraient rien de plus et fausseraient tout comptage.
    CONSTRAINT uq_ressource_site UNIQUE (site_id, ressource),
    CONSTRAINT uq_ressource_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_ressource_site
        FOREIGN KEY (site_id, tenant_id) REFERENCES site (id, tenant_id) ON DELETE CASCADE
);

CREATE INDEX ix_ressource_site ON ressource_florale (site_id);

ALTER TABLE ressource_florale ENABLE ROW LEVEL SECURITY;
ALTER TABLE ressource_florale FORCE  ROW LEVEL SECURITY;
-- La portee suit celle du SITE (V16) et non le seul tenant : enumerer les
-- ressources, c'est enumerer les sites qui les portent.
CREATE POLICY p_ressource_tenant ON ressource_florale
    USING (tenant_id = current_setting('app.current_tenant', true)
           AND (zumm_portee_globale()
                OR EXISTS (SELECT 1 FROM ruche r
                           WHERE r.site_id = ressource_florale.site_id
                             AND r.tenant_id = ressource_florale.tenant_id
                             AND r.agent_responsable_id = zumm_agent_courant())))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));

CREATE TRIGGER tg_ressource_maj BEFORE UPDATE ON ressource_florale
    FOR EACH ROW EXECUTE FUNCTION zumm_touch_maj_le();

COMMENT ON TABLE ressource_florale IS
    'Sources de nectar declarees autour d''un rucher. Dit QUOI, jamais QUAND (pas de calendrier).';


-- === 3. Historique d'emplacement ===========================================
--
-- `site.date_demenagement` savait qu'un rucher avait bouge ; il ne savait pas
-- D'OU. Un site transhume perdait donc son passe a chaque deplacement : les
-- recoltes de l'an dernier restaient attachees a un point qui n'existait plus.
--
-- Le modele est celui d'une PERIODE : une ligne par emplacement occupe, la ligne
-- courante etant la seule dont `date_fin` est nulle. `site.latitude/longitude`
-- reste la position COURANTE — denormalisation assumee : toutes les requetes
-- spatiales (PostGIS, grappes, voisins, tournee) la lisent, et les faire passer
-- par un historique les rendrait toutes plus lentes pour un gain nul.
CREATE TABLE emplacement_site (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id   TEXT          NOT NULL,
    site_id     BIGINT        NOT NULL,
    latitude    NUMERIC(9, 6) NOT NULL,
    longitude   NUMERIC(9, 6) NOT NULL,
    altitude    NUMERIC(7, 2),
    date_debut  DATE          NOT NULL,
    -- NULL = emplacement courant. Ce n'est pas une saisie incomplete, c'est
    -- l'etat normal d'un rucher en place.
    date_fin    DATE,
    motif       VARCHAR(20),
    note        TEXT,
    cree_le     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    maj_le      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_emplacement_latitude  CHECK (latitude  BETWEEN -90 AND 90),
    CONSTRAINT ck_emplacement_longitude CHECK (longitude BETWEEN -180 AND 180),
    CONSTRAINT ck_emplacement_altitude  CHECK (altitude IS NULL
        OR altitude BETWEEN -500 AND 9000),
    CONSTRAINT ck_emplacement_periode CHECK (date_fin IS NULL OR date_fin >= date_debut),
    CONSTRAINT ck_emplacement_motif CHECK (motif IS NULL OR motif IN
        ('installation', 'transhumance', 'miellee', 'securite', 'reglementaire', 'autre')),
    CONSTRAINT uq_emplacement_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_emplacement_site
        FOREIGN KEY (site_id, tenant_id) REFERENCES site (id, tenant_id) ON DELETE CASCADE
);

-- Un rucher n'est qu'a UN endroit a la fois. L'index partiel rend l'invariant
-- structurel : deux emplacements ouverts sur le meme site sont refuses par la
-- base, pas seulement par le service.
CREATE UNIQUE INDEX uq_emplacement_courant
    ON emplacement_site (site_id) WHERE date_fin IS NULL;
CREATE INDEX ix_emplacement_site ON emplacement_site (site_id, date_debut DESC);

-- Reprise de l'existant AVANT d'armer la RLS : la migration tourne avec le role
-- proprietaire, mais `FORCE ROW LEVEL SECURITY` s'applique aussi a lui, et
-- `app.current_tenant` n'est pas pose pendant une migration.
--
-- Chaque site connu recoit son emplacement d'origine. Il est CLOS a la date de
-- demenagement quand elle existe — le site a alors bouge sans qu'on sache vers
-- ou, et c'est exactement ce que la table doit dire : la position courante du
-- site est le nouvel emplacement, celui d'avant est inconnu.
INSERT INTO emplacement_site
    (tenant_id, site_id, latitude, longitude, altitude, date_debut, date_fin, motif, note)
SELECT s.tenant_id, s.id, s.latitude, s.longitude, s.altitude,
       s.date_mise_en_oeuvre, s.date_cloture, 'installation',
       'Emplacement d''origine, repris a la migration V20.'
FROM site s;

ALTER TABLE emplacement_site ENABLE ROW LEVEL SECURITY;
ALTER TABLE emplacement_site FORCE  ROW LEVEL SECURITY;
-- Meme portee que `site` (V16), et pour la meme raison, en plus fort : cette
-- table est une carte des ruchers DANS LE TEMPS.
CREATE POLICY p_emplacement_tenant ON emplacement_site
    USING (tenant_id = current_setting('app.current_tenant', true)
           AND (zumm_portee_globale()
                OR EXISTS (SELECT 1 FROM ruche r
                           WHERE r.site_id = emplacement_site.site_id
                             AND r.tenant_id = emplacement_site.tenant_id
                             AND r.agent_responsable_id = zumm_agent_courant())))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));

CREATE TRIGGER tg_emplacement_maj BEFORE UPDATE ON emplacement_site
    FOR EACH ROW EXECUTE FUNCTION zumm_touch_maj_le();

COMMENT ON TABLE emplacement_site IS
    'Historique des positions d''un rucher (transhumance). La ligne a date_fin nulle est la position courante.';


-- === 4. Division ===========================================================
--
-- `RaisonVisite.DIVISION` et `EtatRuche.EN_DIVISION` disaient qu'une division
-- avait eu lieu. Ni la ruche fille, ni ce qui lui a ete transfere n'etaient
-- saisis — donc aucune FILIATION : `ruche.origine = 'division'` (V19) dit d'ou
-- vient une colonie, jamais DE QUI.
--
-- Cette table est la reponse aux deux : elle relie mere et fille, et elle porte
-- ce que la division a coute a la mere.
CREATE TABLE division (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id          TEXT        NOT NULL,
    ruche_mere_id      BIGINT      NOT NULL,
    -- La fille peut manquer : on divise parfois vers un nucleus qui ne sera
    -- enregistre comme ruche que s'il prend. Exiger la fille des la saisie
    -- ferait renoncer a saisir la division.
    ruche_fille_id     BIGINT,
    agent_id           BIGINT      NOT NULL,
    visite_id          BIGINT,
    date_division      DATE        NOT NULL,
    methode            VARCHAR(20),
    cadres_couvain     INT,
    cadres_provisions  INT,
    -- Ce que la fille a recu comme reine, et c'est la question qui decide de la
    -- suite : une division sur cellule se juge trois semaines plus tard.
    origine_reine      VARCHAR(20),
    note               TEXT,
    cree_le            TIMESTAMPTZ NOT NULL DEFAULT now(),
    maj_le             TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Une ruche ne se divise pas d'elle-meme vers elle-meme.
    CONSTRAINT ck_division_distinctes CHECK (ruche_fille_id IS NULL
        OR ruche_fille_id <> ruche_mere_id),
    CONSTRAINT ck_division_methode CHECK (methode IS NULL OR methode IN
        ('essaim_artificiel', 'nucleus', 'partage_egal', 'prelevement_cadres', 'autre')),
    CONSTRAINT ck_division_origine_reine CHECK (origine_reine IS NULL OR origine_reine IN
        ('cellule_royale', 'reine_introduite', 'orpheline', 'reine_mere', 'autre')),
    CONSTRAINT ck_division_cadres CHECK (
        (cadres_couvain IS NULL OR cadres_couvain BETWEEN 0 AND 40)
        AND (cadres_provisions IS NULL OR cadres_provisions BETWEEN 0 AND 40)),
    CONSTRAINT uq_division_id_tenant UNIQUE (id, tenant_id),
    -- Une fille n'est issue que d'une division : l'unicite partielle empeche de
    -- fabriquer un arbre genealogique a deux parents.
    CONSTRAINT fk_division_mere
        FOREIGN KEY (ruche_mere_id, tenant_id) REFERENCES ruche (id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT fk_division_fille
        FOREIGN KEY (ruche_fille_id, tenant_id) REFERENCES ruche (id, tenant_id) ON DELETE SET NULL,
    CONSTRAINT fk_division_agent
        FOREIGN KEY (agent_id, tenant_id) REFERENCES agent (id, tenant_id) ON DELETE RESTRICT,
    CONSTRAINT fk_division_visite
        FOREIGN KEY (visite_id, tenant_id) REFERENCES visite (id, tenant_id) ON DELETE SET NULL
);

CREATE UNIQUE INDEX uq_division_fille
    ON division (ruche_fille_id) WHERE ruche_fille_id IS NOT NULL;
CREATE INDEX ix_division_mere ON division (ruche_mere_id, date_division DESC);

ALTER TABLE division ENABLE ROW LEVEL SECURITY;
ALTER TABLE division FORCE  ROW LEVEL SECURITY;
CREATE POLICY p_division_tenant ON division
    USING      (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));

CREATE TRIGGER tg_division_maj BEFORE UPDATE ON division
    FOR EACH ROW EXECUTE FUNCTION zumm_touch_maj_le();

COMMENT ON TABLE division IS
    'Division d''une colonie : filiation mere -> fille et ce qui a ete transfere.';


-- === 5. Capture d'essaim ===================================================
--
-- L'autre porte d'entree d'une colonie, et la seule qui ne coute rien. HiveBook
-- l'enregistre au meme rang qu'une division ; Zumm ne la nommait nulle part.
--
-- Volontairement sans coordonnees (voir l'en-tete) : `lieu` est un repere
-- humain, pas un point sur une carte.
CREATE TABLE capture_essaim (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id      TEXT        NOT NULL,
    agent_id       BIGINT      NOT NULL,
    -- La ruche dans laquelle l'essaim a ete loge. Nulle tant qu'il est en
    -- ruchette d'attente : une capture existe avant d'avoir une ruche.
    ruche_id       BIGINT,
    site_id        BIGINT,
    date_capture   DATE        NOT NULL,
    origine        VARCHAR(20) NOT NULL,
    lieu           VARCHAR(200),
    poids_kg       NUMERIC(5, 2),
    hauteur_m      NUMERIC(4, 1),
    note           TEXT,
    cree_le        TIMESTAMPTZ NOT NULL DEFAULT now(),
    maj_le         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_capture_origine CHECK (origine IN
        ('essaim_naturel', 'piege', 'recuperation', 'signalement', 'autre')),
    CONSTRAINT ck_capture_poids CHECK (poids_kg IS NULL OR poids_kg BETWEEN 0 AND 20),
    CONSTRAINT ck_capture_hauteur CHECK (hauteur_m IS NULL OR hauteur_m BETWEEN 0 AND 60),
    CONSTRAINT uq_capture_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_capture_agent
        FOREIGN KEY (agent_id, tenant_id) REFERENCES agent (id, tenant_id) ON DELETE RESTRICT,
    CONSTRAINT fk_capture_ruche
        FOREIGN KEY (ruche_id, tenant_id) REFERENCES ruche (id, tenant_id) ON DELETE SET NULL,
    CONSTRAINT fk_capture_site
        FOREIGN KEY (site_id, tenant_id) REFERENCES site (id, tenant_id) ON DELETE SET NULL
);

CREATE INDEX ix_capture_date ON capture_essaim (date_capture DESC);

ALTER TABLE capture_essaim ENABLE ROW LEVEL SECURITY;
ALTER TABLE capture_essaim FORCE  ROW LEVEL SECURITY;
CREATE POLICY p_capture_tenant ON capture_essaim
    USING      (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));

CREATE TRIGGER tg_capture_maj BEFORE UPDATE ON capture_essaim
    FOR EACH ROW EXECUTE FUNCTION zumm_touch_maj_le();

COMMENT ON TABLE capture_essaim IS
    'Capture d''essaim : l''entree d''une colonie qui ne vient ni d''un achat ni d''une division.';


-- === 6. Photos ailleurs que sur une visite =================================
--
-- `photo.visite_id` etait NOT NULL : une photo de ruche, de reine marquee, de
-- cadre de recolte ou de rucher n'avait aucun endroit ou aller. Les quatre
-- colonnes ajoutees ouvrent ces rattachements sans creer quatre tables.
--
-- L'invariant qui rend le modele sur : EXACTEMENT UNE cible. Une photo attachee
-- a tout n'est attachee a rien, et une photo attachee a rien est une fuite de
-- stockage. `num_nonnulls` l'exprime en une ligne, et la base la fait respecter.
ALTER TABLE photo
    ALTER COLUMN visite_id DROP NOT NULL,
    ADD COLUMN ruche_id       BIGINT,
    ADD COLUMN site_id        BIGINT,
    ADD COLUMN suivi_reine_id BIGINT,
    ADD COLUMN recolte_id     BIGINT;

ALTER TABLE photo
    ADD CONSTRAINT ck_photo_cible_unique CHECK (
        num_nonnulls(visite_id, ruche_id, site_id, suivi_reine_id, recolte_id) = 1),
    ADD CONSTRAINT fk_photo_ruche
        FOREIGN KEY (ruche_id, tenant_id) REFERENCES ruche (id, tenant_id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_photo_site
        FOREIGN KEY (site_id, tenant_id) REFERENCES site (id, tenant_id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_photo_reine
        FOREIGN KEY (suivi_reine_id, tenant_id) REFERENCES suivi_reine (id, tenant_id)
        ON DELETE CASCADE,
    ADD CONSTRAINT fk_photo_recolte
        FOREIGN KEY (recolte_id, tenant_id) REFERENCES recolte (id, tenant_id) ON DELETE CASCADE;

CREATE INDEX ix_photo_ruche   ON photo (ruche_id)       WHERE ruche_id IS NOT NULL;
CREATE INDEX ix_photo_site    ON photo (site_id)        WHERE site_id IS NOT NULL;
CREATE INDEX ix_photo_reine   ON photo (suivi_reine_id) WHERE suivi_reine_id IS NOT NULL;
CREATE INDEX ix_photo_recolte ON photo (recolte_id)     WHERE recolte_id IS NOT NULL;

COMMENT ON CONSTRAINT ck_photo_cible_unique ON photo IS
    'Une photo a EXACTEMENT une cible : visite, ruche, site, reine ou recolte.';
