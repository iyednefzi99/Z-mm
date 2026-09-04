-- ===========================================================================
-- V21 — Miellees, transport de transhumance et abonnement iCalendar (SPRINT-21)
--
-- Ferme les trois dernieres lignes non couvertes du §1 de
-- `docs/ECART-CONCURRENTS.md`, la V20 ayant traite les cinq autres :
--
--   1. ressource_florale : la PERIODE de floraison — les « miellees » que la
--      V20 laissait volontairement de cote ;
--   2. transport         : la planification du deplacement, que l'historique
--      d'emplacement savait constater sans jamais l'organiser ;
--   3. abonnement_calendrier : l'agenda consultable par un client de calendrier
--      SANS session, c'est-a-dire le vrai « sync iCal » des concurrents.
--
-- ---------------------------------------------------------------------------
-- Le point sensible de cette migration
--
-- `abonnement_calendrier` est la SEULE table metier du depot a ne porter ni
-- politique RLS ni discriminant de tenant, et ce n'est pas un oubli : elle est
-- lue AVANT que le tenant soit connu — c'est meme sa fonction, resoudre un jeton
-- opaque en (tenant, agent). Lui imposer la convention multi-tenant rendrait
-- l'abonnement impossible a servir, exactement comme pour `SPRING_SESSION`
-- (V17), qui est hors perimetre pour la meme raison.
--
-- La contrepartie est que le filtrage par tenant devient APPLICATIF sur cette
-- table : `ServiceAbonnementCalendrier` est le seul endroit qui la lit, et
-- chacune de ses requetes porte le tenant explicitement. Un oubli y serait une
-- fuite entre exploitations — d'ou la table volontairement pauvre : elle ne
-- contient aucune donnee metier, seulement de quoi authentifier.
-- ===========================================================================


-- === 1. Periode de floraison sur les ressources declarees ==================
--
-- Des MOIS et non des dates, et c'est la decision de fond : une floraison
-- revient chaque annee. Une date la figerait a un millesime et obligerait a
-- ressaisir les memes lignes tous les ans — ce qui, en pratique, veut dire
-- qu'elles ne seraient jamais ressaisies.
--
-- La fenetre peut ENJAMBER l'annee (novembre → fevrier pour l'eucalyptus du
-- Sud) : aucune contrainte n'exige `mois_fin >= mois_debut`, et c'est
-- deliberе. Le service lit la fenetre modulo douze.
ALTER TABLE ressource_florale
    ADD COLUMN mois_debut SMALLINT,
    ADD COLUMN mois_fin   SMALLINT;

ALTER TABLE ressource_florale
    ADD CONSTRAINT ck_ressource_mois CHECK (
        (mois_debut IS NULL AND mois_fin IS NULL)
        OR (mois_debut BETWEEN 1 AND 12 AND mois_fin BETWEEN 1 AND 12));

COMMENT ON COLUMN ressource_florale.mois_debut IS
    'Debut de floraison, en mois (1-12). La fenetre peut enjamber l''annee : fin < debut est valide.';


-- === 2. Transport de transhumance ==========================================
--
-- `emplacement_site` (V20) sait ou un rucher a ete ; il ne sait pas comment il
-- y va. C'est ce que le §1 nomme desormais « planification du transport », et
-- que deux concurrents traitent comme un module a part.
--
-- Le transport est un PLAN, pas un fait : il precede le deplacement et peut ne
-- jamais avoir lieu. Le realiser clot l'emplacement courant et en ouvre un
-- nouveau — c'est-a-dire qu'il declenche exactement ce que
-- `POST /api/sites/{id}/demenagement` fait deja. Les deux chemins convergent a
-- dessein : un rucher deplace laisse la meme trace, qu'il ait ete planifie ou
-- non.
CREATE TABLE transport (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id              TEXT         NOT NULL,
    site_id                BIGINT       NOT NULL,
    agent_id               BIGINT       NOT NULL,
    date_prevue            DATE         NOT NULL,
    heure_prevue           TIME,
    vehicule               VARCHAR(80),
    -- Ce que le vehicule peut porter, et ce qu'on compte deplacer : l'ecart
    -- entre les deux est toute l'information du plan. Un camion de vingt places
    -- pour quarante ruches, c'est deux voyages, et cela se decide avant.
    capacite_ruches        INT,
    nb_ruches              INT,
    destination_libelle    VARCHAR(160) NOT NULL,
    -- Facultatives : on planifie souvent vers un emplacement qu'on n'a pas
    -- encore releve au GPS. Sans elles, le transport se planifie mais ne peut
    -- pas se « realiser » — le service le dit alors en clair.
    destination_latitude   NUMERIC(9, 6),
    destination_longitude  NUMERIC(9, 6),
    statut                 VARCHAR(10)  NOT NULL DEFAULT 'prevu',
    note                   TEXT,
    cree_le                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    maj_le                 TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_transport_statut CHECK (statut IN ('prevu', 'realise', 'annule')),
    CONSTRAINT ck_transport_capacite CHECK (capacite_ruches IS NULL OR capacite_ruches > 0),
    CONSTRAINT ck_transport_nb CHECK (nb_ruches IS NULL OR nb_ruches >= 0),
    CONSTRAINT ck_transport_destination_libelle CHECK (length(trim(destination_libelle)) > 0),
    -- Une latitude sans longitude ne designe rien : les deux ou aucune.
    CONSTRAINT ck_transport_coordonnees CHECK (
        num_nonnulls(destination_latitude, destination_longitude) <> 1),
    CONSTRAINT ck_transport_latitude CHECK (destination_latitude IS NULL
        OR destination_latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_transport_longitude CHECK (destination_longitude IS NULL
        OR destination_longitude BETWEEN -180 AND 180),
    CONSTRAINT uq_transport_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_transport_site
        FOREIGN KEY (site_id, tenant_id) REFERENCES site (id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT fk_transport_agent
        FOREIGN KEY (agent_id, tenant_id) REFERENCES agent (id, tenant_id) ON DELETE RESTRICT
);

CREATE INDEX ix_transport_site ON transport (site_id, date_prevue DESC);
-- Index partiel : la question posee au quotidien est « qu'ai-je a deplacer », et
-- elle ne porte que sur les transports encore prevus.
CREATE INDEX ix_transport_prevu ON transport (date_prevue) WHERE statut = 'prevu';

ALTER TABLE transport ENABLE ROW LEVEL SECURITY;
ALTER TABLE transport FORCE  ROW LEVEL SECURITY;
-- Meme portee que `site` et `emplacement_site` : un transport porte une
-- destination, donc une position future. C'est une carte des ruchers a venir.
CREATE POLICY p_transport_tenant ON transport
    USING (tenant_id = current_setting('app.current_tenant', true)
           AND (zumm_portee_globale()
                OR EXISTS (SELECT 1 FROM ruche r
                           WHERE r.site_id = transport.site_id
                             AND r.tenant_id = transport.tenant_id
                             AND r.agent_responsable_id = zumm_agent_courant())))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));

CREATE TRIGGER tg_transport_maj BEFORE UPDATE ON transport
    FOR EACH ROW EXECUTE FUNCTION zumm_touch_maj_le();

COMMENT ON TABLE transport IS
    'Plan de deplacement d''un rucher : vehicule, creneau, destination. Le realiser ouvre un emplacement.';


-- === 3. Abonnement iCalendar ===============================================
--
-- Le vrai « sync iCal » : une URL qu'un client de calendrier appelle seul, sans
-- session, toutes les quelques heures. Le SPRINT-21 s'y etait refuse — un jeton
-- permanent dans une URL est un secret de plus, non revocable en pratique. Ce
-- qui suit leve l'objection plutot que la contourner :
--
--   * le jeton est tire au sort sur 256 bits et n'est JAMAIS stocke : seule son
--     empreinte SHA-256 l'est, si bien qu'une fuite de la base ne rend aucune
--     URL utilisable ;
--   * il EXPIRE, obligatoirement, et l'application borne la duree demandee ;
--   * il se REVOQUE d'un clic, et la table garde la date de revocation plutot
--     que d'effacer la ligne — savoir qu'un abonnement a ete revoque le 3 mars
--     vaut mieux que de ne plus rien savoir ;
--   * `derniere_utilisation` rend l'usage VISIBLE : un jeton qu'on croyait
--     inutilise et qui sert toutes les heures se voit dans l'ecran du compte.
--
-- Hors perimetre multi-tenant, comme `SPRING_SESSION` (V17) : voir l'en-tete.
CREATE TABLE abonnement_calendrier (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- PORTE le tenant, ne le discrimine pas : c'est ce que le jeton resout.
    tenant_id             TEXT         NOT NULL,
    agent_id              BIGINT       NOT NULL,
    libelle               VARCHAR(80)  NOT NULL,
    -- SHA-256 en hexadecimal. Le secret lui-meme n'existe qu'une fois, dans la
    -- reponse a la creation.
    jeton_empreinte       CHAR(64)     NOT NULL,
    cree_le               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expire_le             TIMESTAMPTZ  NOT NULL,
    revoque_le            TIMESTAMPTZ,
    derniere_utilisation  TIMESTAMPTZ,
    CONSTRAINT uq_abonnement_empreinte UNIQUE (jeton_empreinte),
    CONSTRAINT ck_abonnement_libelle CHECK (length(trim(libelle)) > 0),
    CONSTRAINT ck_abonnement_expiration CHECK (expire_le > cree_le),
    CONSTRAINT fk_abonnement_agent
        FOREIGN KEY (agent_id, tenant_id) REFERENCES agent (id, tenant_id) ON DELETE CASCADE
);

-- La recherche par empreinte est le chemin critique : elle est faite a chaque
-- appel du client de calendrier, sans session, avant tout contexte.
CREATE INDEX ix_abonnement_agent ON abonnement_calendrier (tenant_id, agent_id);

COMMENT ON TABLE abonnement_calendrier IS
    'Jetons d''abonnement iCalendar. HORS perimetre multi-tenant : lus avant que le tenant soit connu (cf. SPRING_SESSION, V17).';
COMMENT ON COLUMN abonnement_calendrier.jeton_empreinte IS
    'SHA-256 du jeton. Le jeton en clair n''est rendu qu''une fois, a la creation.';
