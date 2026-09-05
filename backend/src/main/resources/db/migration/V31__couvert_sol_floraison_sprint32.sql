-- ===========================================================================
-- V31 — Occupation du sol, floraison observee, rayon de butinage
--       (SPRINT-32, lot H)
--
-- Le dernier lot du plan de couverture, et le seul ou un concurrent joue sur le
-- terrain que Zumm revendique : BeeGIS. Neuf lignes du §2 et du §13.
--
-- ---------------------------------------------------------------------------
-- LA DECISION D1, ET POURQUOI ELLE N'EST PAS CELLE QU'ON ATTENDAIT
--
-- Le plan posait : « quel referentiel d'occupation du sol ? » — RPG et CartoBio
-- (precis, mais francais), Copernicus WorldCover (mondial, classes grossieres),
-- ou OpenStreetMap (mondial, completude tres inegale). Il notait qu'une couche
-- generique acceptant les trois « coute plus cher que de trancher ».
--
-- Elle ne coute plus plus cher, et deux travaux recents l'expliquent.
--
-- 1. INTERROGER une source tierce avec les coordonnees d'un rucher REVELE ou
--    sont les ruches. C'est exactement ce que `PolitiquePositions` protege
--    depuis le SPRINT-12, et ce que le mode local du SPRINT-30 vient de couper
--    pour les tuiles de carte. Une requete Overpass par rucher defairait les
--    deux.
-- 2. Le mode local (`zumm.reseau.sortant=false`) interdit tout appel sortant du
--    serveur. Une couche d'occupation du sol interrogee en direct y serait
--    muette, c'est-a-dire inutilisable la ou elle compte le plus.
--
-- La donnee est donc ACCUEILLIE, pas interrogee : l'exploitant verse un extrait
-- GeoJSON dans SON PostGIS, et tout le calcul se fait chez lui. La source
-- devient un PARAMETRE — francaise, mondiale ou communautaire — au lieu d'un
-- choix fige qui aurait limite le produit a un pays.
--
-- ---------------------------------------------------------------------------
-- CE QUE LA TAXONOMIE FERMEE PROTEGE
--
-- Chaque source nomme ses classes autrement : le RPG parle de « prairie
-- permanente », WorldCover de « grassland », OSM de `landuse=meadow`. Les
-- laisser entrer telles quelles rendrait deux exploitations incomparables, et
-- une somme de surfaces par classe n'aurait plus de sens.
--
-- Dix classes, contraintes en base. L'ingesteur TRADUIT ; il n'invente pas. Le
-- meme raisonnement que le referentiel ferme du SPRINT-28, pour la meme raison.
-- ===========================================================================


-- === 1. Couvert du sol =====================================================
CREATE TABLE couvert_sol (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id  TEXT         NOT NULL,
    classe     VARCHAR(20)  NOT NULL,
    -- D'ou vient le polygone, en clair : « RPG 2024 », « WorldCover v200 »,
    -- « OSM 2026-08 ». Affiche a l'ecran avec les surfaces — un pourcentage
    -- sans sa source n'engage personne.
    source     VARCHAR(80)  NOT NULL,
    -- Le MILLESIME, et il est obligatoire (§13). Une donnee d'occupation du sol
    -- de 2019 presentee comme l'etat du jour n'est pas une approximation, c'est
    -- une affirmation fausse : les parcelles tournent d'une annee sur l'autre,
    -- et c'est precisement ce que la ligne « historique et rotation » demande de
    -- pouvoir lire.
    millesime  INT          NOT NULL,
    geom       GEOGRAPHY(MULTIPOLYGON, 4326) NOT NULL,
    cree_le    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_couvert_classe CHECK (classe IN
        ('culture', 'prairie', 'foret', 'lande', 'verger', 'vigne',
         'eau', 'urbain', 'sol_nu', 'autre')),
    CONSTRAINT ck_couvert_millesime CHECK (millesime BETWEEN 1990 AND 2100),
    CONSTRAINT uq_couvert_id_tenant UNIQUE (id, tenant_id)
);

-- L'index qui rend l'intersection tenable : sans lui, chaque calcul de surfaces
-- balaierait la table entiere, et une couche departementale compte des centaines
-- de milliers de polygones.
CREATE INDEX ix_couvert_geom ON couvert_sol USING GIST (geom);
CREATE INDEX ix_couvert_tenant_millesime ON couvert_sol (tenant_id, millesime);

ALTER TABLE couvert_sol ENABLE ROW LEVEL SECURITY;
ALTER TABLE couvert_sol FORCE  ROW LEVEL SECURITY;
CREATE POLICY p_couvert_tenant ON couvert_sol
    USING      (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));

COMMENT ON TABLE couvert_sol IS
    'Occupation du sol versee par l''exploitation (SPRINT-32). La donnee est '
    'ACCUEILLIE, jamais interrogee en direct : demander a un tiers ce qu''il y a '
    'autour d''un rucher revelerait ou sont les ruches.';


-- === 2. Floraison OBSERVEE =================================================
--
-- La `V21` avait pose la floraison DECLAREE : « le colza fleurit en avril ».
-- C'est une connaissance generale, utile a la planification, et fausse trois
-- annees sur dix — une gelee tardive decale tout d'une quinzaine.
--
-- Cette table porte ce qui a ete VU, cette annee-la, sur ce rucher-la. Les deux
-- coexistent, et ne se remplacent pas : le declaratif prevoit, l'observe
-- constate. Les fondre ferait perdre la seule chose qui permette de dire
-- « cette annee, c'etait en avance ».
CREATE TABLE floraison_observee (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id   TEXT        NOT NULL,
    ressource_id BIGINT     NOT NULL,
    annee       INT         NOT NULL,
    date_debut  DATE        NOT NULL,
    -- Pic et fin arrivent APRES le debut, parfois des semaines apres : les
    -- exiger a la saisie obligerait a attendre la fin de la miellee pour noter
    -- qu'elle a commence.
    date_pic    DATE,
    date_fin    DATE,
    -- Abondance percue, de 0 (nulle) a 3 (exceptionnelle). Une echelle, pas une
    -- mesure : personne ne pese le nectar d'une parcelle.
    abondance   SMALLINT,
    note        TEXT,
    cree_le     TIMESTAMPTZ NOT NULL DEFAULT now(),
    maj_le      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_floraison_annee CHECK (annee BETWEEN 1990 AND 2100),
    CONSTRAINT ck_floraison_abondance CHECK (abondance IS NULL
        OR abondance BETWEEN 0 AND 3),
    CONSTRAINT ck_floraison_ordre CHECK (
        (date_pic IS NULL OR date_pic >= date_debut)
        AND (date_fin IS NULL OR date_fin >= date_debut)
        AND (date_fin IS NULL OR date_pic IS NULL OR date_fin >= date_pic)),
    -- Une ressource ne fleurit qu'une fois par an. Deux lignes seraient deux
    -- observations de la meme chose, et la comparaison inter-annuelle
    -- choisirait au hasard.
    CONSTRAINT uq_floraison_annee UNIQUE (ressource_id, annee),
    CONSTRAINT uq_floraison_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_floraison_ressource
        FOREIGN KEY (ressource_id, tenant_id)
        REFERENCES ressource_florale (id, tenant_id) ON DELETE CASCADE
);

CREATE INDEX ix_floraison_annee ON floraison_observee (tenant_id, annee DESC);

ALTER TABLE floraison_observee ENABLE ROW LEVEL SECURITY;
ALTER TABLE floraison_observee FORCE  ROW LEVEL SECURITY;
CREATE POLICY p_floraison_tenant ON floraison_observee
    USING      (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));

CREATE TRIGGER tg_floraison_maj BEFORE UPDATE ON floraison_observee
    FOR EACH ROW EXECUTE FUNCTION zumm_touch_maj_le();


-- === 3. Rayon de butinage, par rucher ======================================
--
-- « `rayonsKm` est une propriete figee a [1, 2, 3] » (§2). Le rayon reel depend
-- du terrain : trois kilometres en plaine, davantage en montagne ou en zone
-- pauvre, moins en ville. Le figer revient a dessiner le meme cercle partout.
--
-- Sur le SITE et non dans la configuration globale : deux ruchers d'une meme
-- exploitation n'ont pas le meme environnement, et c'est justement ce que le lot
-- entier cherche a mesurer. NULL = le defaut de `ConfigZumm.ini`, ce qui evite
-- d'ecrire une valeur dans chaque ligne existante.
ALTER TABLE site ADD COLUMN rayon_butinage_km NUMERIC(4, 1);

ALTER TABLE site
    ADD CONSTRAINT ck_site_rayon_butinage CHECK (
        rayon_butinage_km IS NULL OR rayon_butinage_km BETWEEN 0.5 AND 15);

COMMENT ON COLUMN site.rayon_butinage_km IS
    'Rayon de butinage de CE rucher (SPRINT-32). NULL = defaut de configuration. '
    'Borne a 15 km : au-dela, une abeille ne rentre pas.';
