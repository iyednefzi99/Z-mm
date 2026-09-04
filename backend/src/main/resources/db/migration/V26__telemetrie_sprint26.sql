-- ===========================================================================
-- V26 — Capteurs : alimentation, poids par hausse, partage (SPRINT-26, lot F1)
--
-- Le lot F du plan de couverture porte sur les capteurs. Il se coupe en deux :
-- quatre lignes bon marche (celles-ci), et quatre autres suspendues a l'achat
-- de materiel — decision D3, qui ne se tranche pas en ecrivant du code.
--
-- Les trois travaux ci-dessous repondent a trois reproches distincts du §5 :
-- la panne de batterie silencieuse, la granularite du poids arretee a la ruche,
-- et l'impossibilite de montrer une courbe a quelqu'un d'une autre exploitation.
--
-- ---------------------------------------------------------------------------
-- 1. L'alimentation : une valeur d'enumeration, et rien d'autre
--
-- BeeLog Digital et Onibi se font tous deux reprocher les pannes de batterie
-- SILENCIEUSES : la balance cesse d'emettre, et personne ne s'en apercoit avant
-- la visite suivante. Le reproche ne porte pas sur l'absence de mesure, il porte
-- sur l'absence d'ALERTE.
--
-- D'ou le choix le plus petit possible : `alimentation` rejoint la liste des
-- indicateurs, et `SeuilAlerteService` s'en occupe comme des autres — meme
-- hysteresis, meme table d'alertes, meme notification. Inventer une table
-- « etat des capteurs » aurait cree un second mecanisme d'alerte a maintenir en
-- parallele du premier, pour dire la meme chose.
--
-- ---------------------------------------------------------------------------
-- 2. Le poids par hausse, et pourquoi une TABLE A PART
--
-- La tentation etait d'ajouter `compartiment_id` a `mesure`. Elle est mauvaise
-- pour trois raisons qui se cumulent :
--
--   a) `mesure` est une HYPERTABLE dont la cle primaire est
--      (ruche_id, type_indicateur, instant). Y glisser une colonne nullable
--      obligerait a remplacer la cle primaire par un index unique en
--      NULLS NOT DISTINCT — sur la table la plus critique du systeme, celle que
--      lisent les alertes, la prevision de recolte et la detection d'anomalie ;
--   b) la contourner par un sentinel (`compartiment_id = 0` pour « toute la
--      ruche ») ferait perdre la CLE ETRANGERE : plus rien ne garantirait que la
--      hausse existe, dans un depot ou toutes les autres tables la portent ;
--   c) surtout, ce ne sont pas les memes donnees. `mesure` porte ce qu'une
--      balance pese SOUS une ruche ; celle-ci porte ce qu'on attribue a une
--      hausse. Les melanger obligerait chaque lecture existante — sans
--      exception — a se souvenir d'exclure les lignes de hausse, et il suffirait
--      d'un oubli pour qu'une prevision de recolte compte deux fois le meme miel.
--
-- Le commentaire de la V5 dit « une mesure = une ruche, un indicateur, un
-- instant ». Cette table le laisse vrai au lieu de le casser en silence.
--
-- ---------------------------------------------------------------------------
-- 3. Le partage de telemetrie, HORS perimetre multi-tenant
--
-- « Aucun partage inter-exploitations comme chez BeeLog Digital » (§5). Le
-- partage entre agents d'une meme exploitation existe deja — c'est le tenant.
-- Ce qui manque est de montrer une courbe a quelqu'un du DEHORS : un mentor, un
-- technicien sanitaire, un groupement.
--
-- Le precedent est ecrit et argumente : `abonnement_calendrier` (V21). Meme
-- forme, memes garanties — jeton de 256 bits, stocke en EMPREINTE SHA-256,
-- expiration obligatoire, revocation, derniere utilisation visible. Et la meme
-- consequence : la table PORTE le tenant sans le discriminer, parce qu'elle est
-- lue AVANT que le tenant soit connu. Son cloisonnement est donc APPLICATIF et
-- tient dans un seul service — c'est le point a auditer si elle est lue ailleurs.
--
-- Une difference avec l'abonnement, et elle est deliberee : le partage est borne
-- a UNE ruche. Un jeton qui ouvrirait l'exploitation entiere ne serait plus un
-- partage, ce serait un compte sans mot de passe.
-- ===========================================================================


-- === 1. L'indicateur d'alimentation ========================================

ALTER TABLE mesure DROP CONSTRAINT ck_mesure_indicateur;
ALTER TABLE mesure ADD CONSTRAINT ck_mesure_indicateur CHECK (type_indicateur IN
    ('poids', 'temperature', 'humidite', 'activite', 'alimentation'));

-- `alerte` porte SA PROPRE liste d'indicateurs, et l'oublier faisait echouer
-- l'ouverture de l'alerte APRES l'ecriture de la mesure — 409 sur une ingestion
-- par ailleurs valide. C'est un test d'integration qui l'a montre : les deux
-- contraintes disent la meme chose a deux endroits, et rien ne les tient
-- ensemble sinon la vigilance.
ALTER TABLE alerte DROP CONSTRAINT ck_alerte_indicateur;
ALTER TABLE alerte ADD CONSTRAINT ck_alerte_indicateur CHECK (type_indicateur IN
    ('poids', 'temperature', 'humidite', 'activite', 'alimentation'));

COMMENT ON COLUMN mesure.type_indicateur IS
    'poids | temperature | humidite | activite | alimentation. '
    'L''alimentation est le niveau de batterie du capteur, en pourcent '
    '(SPRINT-26) : elle repond a la panne silencieuse, pas a l''etat de la colonie.';


-- === 2. Poids par hausse ===================================================

CREATE TABLE mesure_compartiment (
    tenant_id        TEXT           NOT NULL,
    compartiment_id  BIGINT         NOT NULL,
    type_indicateur  VARCHAR(20)    NOT NULL,
    instant          TIMESTAMPTZ    NOT NULL,
    valeur           NUMERIC(12, 4) NOT NULL,
    -- Le poids seul, pour l'instant. Temperature et humidite par hausse
    -- existent chez personne : les autoriser ici serait promettre une
    -- granularite qu'aucun materiel ne produit.
    CONSTRAINT ck_mesure_compartiment_indicateur CHECK (type_indicateur IN ('poids')),
    CONSTRAINT pk_mesure_compartiment PRIMARY KEY (compartiment_id, type_indicateur, instant),
    -- Cle etrangere COMPOSITE, comme toute table metier du depot : c'est ce que
    -- le sentinel aurait fait perdre. Une mesure de hausse ne peut designer ni
    -- une hausse inexistante, ni celle d'une autre exploitation.
    CONSTRAINT fk_mesure_compartiment
        FOREIGN KEY (compartiment_id, tenant_id)
        REFERENCES compartiment (id, tenant_id) ON DELETE CASCADE
);

SELECT create_hypertable('mesure_compartiment', 'instant', if_not_exists => TRUE);

ALTER TABLE mesure_compartiment ENABLE ROW LEVEL SECURITY;
ALTER TABLE mesure_compartiment FORCE  ROW LEVEL SECURITY;

-- La portee SUIT celle de `mesure` (V16), et il le faut : le commentaire de la
-- V16 dit pourquoi — « le poids d'une ruche dit si elle vaut d'etre volee ».
-- Une politique au seul tenant aurait rouvert, par la hausse, ce que la V16
-- avait ferme par la ruche. Le chemin est un cran plus long, la regle est la
-- meme : compartiment -> ruche -> agent responsable.
CREATE POLICY p_mesure_compartiment_tenant ON mesure_compartiment
    USING (tenant_id = current_setting('app.current_tenant', true)
           AND (zumm_portee_globale()
                OR EXISTS (SELECT 1 FROM compartiment c
                           JOIN ruche r ON r.id = c.ruche_id AND r.tenant_id = c.tenant_id
                           WHERE c.id = mesure_compartiment.compartiment_id
                             AND c.tenant_id = mesure_compartiment.tenant_id
                             AND r.agent_responsable_id = zumm_agent_courant())))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));

COMMENT ON TABLE mesure_compartiment IS
    'Poids attribue a un compartiment (SPRINT-26). DISTINCTE de `mesure`, qui '
    'porte ce qu''une balance pese sous la ruche entiere : les melanger ferait '
    'compter deux fois le meme miel dans la prevision de recolte.';


-- === 3. Partage d'un flux de telemetrie ====================================

CREATE TABLE partage_telemetrie (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- PORTE le tenant, ne le discrimine pas : c'est ce que le jeton resout.
    tenant_id             TEXT         NOT NULL,
    ruche_id              BIGINT       NOT NULL,
    libelle               VARCHAR(80)  NOT NULL,
    -- SHA-256 en hexadecimal. Le secret lui-meme n'existe qu'une fois, dans la
    -- reponse a la creation.
    jeton_empreinte       CHAR(64)     NOT NULL,
    cree_le               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expire_le             TIMESTAMPTZ  NOT NULL,
    revoque_le            TIMESTAMPTZ,
    derniere_utilisation  TIMESTAMPTZ,
    CONSTRAINT uq_partage_empreinte UNIQUE (jeton_empreinte),
    CONSTRAINT ck_partage_libelle CHECK (length(trim(libelle)) > 0),
    CONSTRAINT ck_partage_expiration CHECK (expire_le > cree_le),
    CONSTRAINT fk_partage_ruche
        FOREIGN KEY (ruche_id, tenant_id) REFERENCES ruche (id, tenant_id) ON DELETE CASCADE
);

-- La recherche par empreinte est le chemin critique : elle est faite a chaque
-- consultation du destinataire, sans session, avant tout contexte.
CREATE INDEX ix_partage_ruche ON partage_telemetrie (tenant_id, ruche_id);

COMMENT ON TABLE partage_telemetrie IS
    'Jetons de partage d''un flux de mesures, borne a UNE ruche (SPRINT-26). '
    'HORS perimetre multi-tenant, comme abonnement_calendrier (V21) : lus avant '
    'que le tenant soit connu. Cloisonnement APPLICATIF, dans un seul service.';
COMMENT ON COLUMN partage_telemetrie.jeton_empreinte IS
    'SHA-256 du jeton. Le jeton en clair n''est rendu qu''une fois, a la creation.';
