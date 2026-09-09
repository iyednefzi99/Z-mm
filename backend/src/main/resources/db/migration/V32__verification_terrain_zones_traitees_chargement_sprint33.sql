-- ===========================================================================
-- V32 — Verification terrain, zones traitees declarees, feuille de chargement
--       (SPRINT-33, lot K)
--
-- Le plan de couverture etait clos au SPRINT-32 : 137 ✅, 5 🟡, 3 ❌, 8 ⛔. Ce
-- lot prend les trois lignes qui restaient du TRAVAIL — les autres etant des
-- refus argumentes (pollen, acoustique, adaptateurs de capteurs) ou du hors
-- perimetre assume.
--
-- ---------------------------------------------------------------------------
-- 1. GROUND TRUTHING — LE CONSTAT NE REMPLACE PAS LA SOURCE
--
-- BeeGIS conseille a ses utilisateurs : « verifiez sur le terrain au printemps
-- la culture reellement semee ». Le §13 en tirait une exigence : toute donnee
-- environnementale porte son millesime et peut etre marquee « a confirmer », ce
-- qui engendre une tache de verification.
--
-- Le millesime est la depuis la V31, obligatoire. Manquaient le doute et le
-- constat.
--
-- La tentation etait d'ECRASER `classe` avec ce qui a ete vu. Elle detruirait
-- exactement ce que le ground truthing cherche a etablir : que la couche se
-- trompait. Ecrasee, la parcelle raconte que le RPG avait raison depuis le
-- debut, et personne ne peut plus mesurer la fiabilite d'un millesime.
--
-- Les deux coexistent donc, comme la floraison DECLAREE de la V21 et la
-- floraison OBSERVEE de la V31 coexistent : la source dit ce qu'elle croit, le
-- terrain dit ce qui est, et les lectures prennent le second des qu'il existe
-- (COALESCE(classe_constatee, classe)).
--
-- `a_confirmer` est POSE par l'exploitant, jamais deduit. Une regle qui
-- marquerait d'office « toute culture de plus de deux ans » fabriquerait une
-- charge de travail que personne n'a demandee, sur des parcelles que personne
-- ne soupconne.
--
-- ---------------------------------------------------------------------------
-- 2. ZONES TRAITEES — CE QUE L'APICULTEUR SAIT, ET RIEN DE PLUS
--
-- « Evaluation de l'exposition aux zones traitees » etait 🟡 depuis le
-- SPRINT-32, avec le motif exact : `distanceCultureM` rend la distance a une
-- CULTURE, pas a une zone traitee, et aucune couche ouverte ne dit ce qui a ete
-- epandu ni quand.
--
-- Cela reste vrai. Ce qui change, c'est la question posee : au lieu de chercher
-- une source qui n'existe pas, on accueille celle qui existe — le voisin qui
-- previent, le pulverisateur qu'on voit passer, l'avis de traitement affiche en
-- mairie. C'est la meme decision que D1 de l'ADR-015 pour l'occupation du sol :
-- la donnee est ACCUEILLIE, jamais interrogee.
--
-- Deux bornes, et elles font la difference entre une fonction et une
-- pretention :
--
--   a) AUCUNE IDENTITE DE TIERS. Ni nom, ni adresse, ni contact. Le §13 refuse
--      un annuaire de voisins (⛔) et ce refus tient : une zone traitee est un
--      polygone, une date et une substance quand on la connait. Ajouter « qui »
--      transformerait la table en fichier de tiers, avec le regime de donnees
--      personnelles qui va avec.
--   b) LE SILENCE N'EST PAS UNE GARANTIE. Une couche declarative est par
--      construction incomplete ; la reponse porte donc le NOMBRE de
--      declarations et la date de la plus recente, pour qu'« aucune zone a
--      proximite » se lise « rien ne m'a ete declare » et non « rien n'a ete
--      epandu ».
--
-- ---------------------------------------------------------------------------
-- 3. FEUILLE DE CHARGEMENT — LA LOGISTIQUE QUI MANQUAIT
--
-- « Coordination d'equipes terrain, logistique multi-sites » etait 🟡 : la
-- charge par agent existait depuis le SPRINT-23, « restent la logistique et la
-- chaine d'approvisionnement ».
--
-- Le manque concret est connu de tout apiculteur : on arrive au troisieme
-- rucher et il manque le candi. La tournee est calculee depuis le SPRINT-10, le
-- stock a des seuils depuis le SPRINT-27 ; ce qui manquait est le LIEN — ce que
-- telle tache consomme, et donc ce qu'il faut charger dans le vehicule avant de
-- partir.
--
-- Deux colonnes sur `tache`, de la meme forme que `materiel_id` (V27), et rien
-- de plus. Une table « besoin de chargement » aurait duplique ce que la tache
-- dit deja, et aurait pu en diverger.
-- ===========================================================================


-- === 1. Verification terrain de l'occupation du sol ========================

ALTER TABLE couvert_sol
    -- Le doute, pose a la main. Une parcelle marquee « a confirmer » reste
    -- comptee dans les surfaces : la retirer ferait baisser le total decrit
    -- sans que rien ne le dise, et « 30 % de couverture » deviendrait faux.
    ADD COLUMN a_confirmer      BOOLEAN     NOT NULL DEFAULT false,
    -- Ce qui a ete VU. NULL tant que personne n'y est alle.
    ADD COLUMN classe_constatee VARCHAR(20),
    ADD COLUMN constate_le      DATE,
    ADD COLUMN constat_note     TEXT;

ALTER TABLE couvert_sol
    ADD CONSTRAINT ck_couvert_classe_constatee CHECK (
        classe_constatee IS NULL OR classe_constatee IN
            ('culture', 'prairie', 'foret', 'lande', 'verger', 'vigne',
             'eau', 'urbain', 'sol_nu', 'autre')),
    -- Un constat sans date ne se situe pas dans le temps, et une date sans
    -- constat ne dit rien : les deux vont ensemble ou aucun des deux.
    ADD CONSTRAINT ck_couvert_constat_date CHECK (
        (classe_constatee IS NULL) = (constate_le IS NULL));

-- Index partiel : la regle de verification ne lit que les parcelles en attente,
-- et elles sont une poignee dans une couche qui en compte des milliers.
CREATE INDEX ix_couvert_a_confirmer ON couvert_sol (tenant_id, millesime)
    WHERE a_confirmer AND classe_constatee IS NULL;

COMMENT ON COLUMN couvert_sol.classe_constatee IS
    'Ce que le terrain a montre (SPRINT-33). N''ECRASE PAS `classe` : ecrasee, '
    'la parcelle raconterait que la source avait raison depuis le debut, et la '
    'fiabilite d''un millesime ne se mesurerait plus. Les lectures prennent le '
    'constat des qu''il existe.';


-- === 2. Zones traitees declarees ===========================================
CREATE TABLE zone_traitee (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id       TEXT        NOT NULL,
    -- Le polygone, et rien qui designe une personne : voir l'en-tete, borne (a).
    geom            GEOGRAPHY(MULTIPOLYGON, 4326) NOT NULL,
    date_traitement DATE        NOT NULL,
    -- La substance TELLE QU'ELLE A ETE DITE, en clair. Un referentiel ferme
    -- serait ici le mauvais outil : le SPRINT-28 en a ferme deux — points
    -- d'observation, produits varroacides — parce qu'ils decrivent des gestes
    -- de l'exploitation, comparables d'une ferme a l'autre. Ce champ rapporte
    -- la parole d'un tiers sur une culture qui n'est pas la sienne ; le
    -- contraindre reviendrait a refuser « ils ont traite, je ne sais pas avec
    -- quoi », qui est l'information la plus frequente.
    substance       VARCHAR(120),
    -- D'ou vient la declaration. Pas pour classer les tiers, mais parce qu'un
    -- avis officiel et un « il me semble avoir vu passer un tracteur » ne
    -- fondent pas la meme decision.
    origine         VARCHAR(20) NOT NULL,
    -- Delai de rentree reglementaire, en heures (6, 24, 48). NULL = inconnu, ce
    -- qui n'est pas zero : zero se lirait « on peut y aller », qui est
    -- precisement ce qu'on ne sait pas.
    delai_rentree_h INT,
    note            TEXT,
    cree_le         TIMESTAMPTZ NOT NULL DEFAULT now(),
    maj_le          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_zone_origine CHECK (origine IN
        ('voisin_declare', 'observe', 'avis_officiel', 'autre')),
    CONSTRAINT ck_zone_delai CHECK (delai_rentree_h IS NULL
        OR delai_rentree_h BETWEEN 0 AND 720),
    CONSTRAINT uq_zone_traitee_id_tenant UNIQUE (id, tenant_id)
);

CREATE INDEX ix_zone_traitee_geom ON zone_traitee USING GIST (geom);
CREATE INDEX ix_zone_traitee_date ON zone_traitee (tenant_id, date_traitement DESC);

ALTER TABLE zone_traitee ENABLE ROW LEVEL SECURITY;
ALTER TABLE zone_traitee FORCE  ROW LEVEL SECURITY;
CREATE POLICY p_zone_traitee_tenant ON zone_traitee
    USING      (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));

CREATE TRIGGER tg_zone_traitee_maj BEFORE UPDATE ON zone_traitee
    FOR EACH ROW EXECUTE FUNCTION zumm_touch_maj_le();

COMMENT ON TABLE zone_traitee IS
    'Zones traitees DECLAREES par l''exploitation (SPRINT-33). Aucune couche '
    'ouverte ne dit ce qui a ete epandu ni quand ; l''apiculteur, lui, le sait '
    'parfois. Ne porte AUCUNE identite de tiers, et son silence n''est pas une '
    'garantie d''absence de traitement.';


-- === 3. Ce qu'une tache consomme ===========================================
--
-- Meme forme que `materiel_id` (V27) : une colonne nullable, une cle etrangere
-- composite, et le detachement qui NOMME sa colonne.
--
-- `ON DELETE SET NULL (consommable_id)` : sans la liste, PostgreSQL annule aussi
-- `tenant_id`, colonne NOT NULL, et la suppression echoue en 500 sur un message
-- qui ne parle pas du bon sujet. Cinq cles avaient ce defaut entre la V20 et la
-- V27 ; la V29 les a reparees. Celle-ci nait correcte.
ALTER TABLE tache
    ADD COLUMN consommable_id  BIGINT,
    ADD COLUMN quantite_prevue NUMERIC(10, 2);

ALTER TABLE tache
    ADD CONSTRAINT fk_tache_consommable
        FOREIGN KEY (consommable_id, tenant_id)
        REFERENCES consommable (id, tenant_id) ON DELETE SET NULL (consommable_id),
    -- Une quantite sans consommable ne designe rien. L'inverse est permis :
    -- « prendre du candi » sans savoir encore combien reste une consigne utile.
    ADD CONSTRAINT ck_tache_quantite CHECK (
        quantite_prevue IS NULL OR consommable_id IS NOT NULL),
    ADD CONSTRAINT ck_tache_quantite_positive CHECK (
        quantite_prevue IS NULL OR quantite_prevue > 0);

CREATE INDEX ix_tache_consommable ON tache (tenant_id, consommable_id)
    WHERE consommable_id IS NOT NULL;

COMMENT ON COLUMN tache.consommable_id IS
    'Ce que la tache consomme (SPRINT-33), pour la feuille de chargement : on '
    'arrive au troisieme rucher et il manque le candi. La tournee est calculee '
    'depuis le SPRINT-10, le stock a des seuils depuis le SPRINT-27 ; c''est le '
    'lien entre les deux qui manquait.';
