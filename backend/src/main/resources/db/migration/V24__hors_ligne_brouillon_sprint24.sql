-- ===========================================================================
-- V24 — Le terrain sans reseau (SPRINT-24, lot C)
--
-- Le lot C du plan de couverture repond a un constat du §13 du document
-- d'ecart : trois editeurs concurrents conseillent a leurs utilisateurs, dans
-- leur propre documentation, d'ouvrir les fiches de leurs ruchers AVANT
-- d'entrer en zone blanche, et trois autres leur conseillent de noter au stylo
-- au rucher pour saisir au retour. Un contournement enseigne par trois editeurs
-- n'est pas une bonne pratique : c'est une fonction manquante.
--
-- Sept des neuf lignes du lot sont purement front (service worker, emport,
-- mode economie, invite d'installation, note vocale). Deux demandent de la
-- donnee, et elles sont ici.
--
-- ---------------------------------------------------------------------------
-- 1. Le brouillon de visite, et pourquoi il n'est PAS une visite
--
-- La tentation etait d'ajouter un etat `brouillon` a `visite`. Elle est
-- mauvaise pour une raison qui se voit tout de suite en lecture : une visite
-- est un ACTE, et le registre s'en sert. Un brouillon n'est pas un acte a
-- moitie fait, c'est une SAISIE en cours — elle peut etre incoherente,
-- incomplete, contradictoire, et surtout elle peut ne jamais devenir une
-- visite. Les melanger obligerait chaque lecture du registre, chaque agregat,
-- chaque export et chaque regle a se souvenir d'exclure les brouillons ; il
-- suffirait d'un oubli pour qu'une saisie abandonnee entre dans un comptage
-- reglementaire.
--
-- Le brouillon est donc une table a part, dont le contenu est OPAQUE au
-- serveur : du JSON que le front ecrit et relit. Le serveur ne le valide pas et
-- ne le comprend pas — le valider reviendrait a exiger d'une saisie en cours
-- qu'elle soit deja complete, ce qui est exactement le contraire du besoin.
--
-- ---------------------------------------------------------------------------
-- 2. Un seul brouillon par (agent, ruche), et pourquoi
--
-- Le besoin nomme est le trajet telephone -> ordinateur : la saisie commencee
-- au rucher se termine a la maison. Deux brouillons du meme agent sur la meme
-- ruche ne repondent a aucune question — ils posent celle de savoir lequel
-- reprendre. L'index unique tranche : le dernier appareil qui ecrit gagne, et
-- `appareil` dit lequel c'etait, pour que l'agent le reconnaisse.
--
-- Ce n'est PAS une resolution de conflits : deux agents differents sur la meme
-- ruche ont chacun leur brouillon, ce qui est correct — ils n'ecrivent pas la
-- meme visite. Le conflit multi-agents se joue au rejeu de la file de
-- mutations, pas ici, et il se resout par `maj_le` (voir VisiteService).
--
-- ---------------------------------------------------------------------------
-- 3. La couverture reseau du rucher
--
-- Deduite d'un conseil d'Onibi — « verifiez la couverture reseau du site avant
-- d'installer ». C'est un attribut du LIEU, au meme titre que l'exposition :
-- il ne se mesure pas, il se constate, et il conditionne ce qu'on peut
-- deployer. Nullable a dessein : `inconnue` est la reponse honnete tant que
-- personne n'y est alle avec un telephone.
-- ===========================================================================


-- === 1. Brouillon de visite ================================================

CREATE TABLE brouillon_visite (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id   TEXT        NOT NULL,
    agent_id    BIGINT      NOT NULL,
    ruche_id    BIGINT      NOT NULL,
    -- Opaque au serveur : le front y range son formulaire en cours. Une
    -- colonne typee obligerait a faire evoluer la base a chaque champ ajoute
    -- au formulaire, pour une donnee qui n'est jamais requetee par son contenu.
    contenu     TEXT        NOT NULL,
    -- De quel appareil vient la derniere ecriture. Sans lui, l'agent qui
    -- retrouve un brouillon a la maison ne sait pas s'il s'agit du sien.
    appareil    VARCHAR(80),
    cree_le     TIMESTAMPTZ NOT NULL DEFAULT now(),
    maj_le      TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- 256 Kio : un formulaire de visite fait quelques kilo-octets. La borne
    -- n'est pas la pour la place, elle est la pour qu'une note vocale ne
    -- finisse jamais encodee en base64 dans ce champ — l'audio n'a pas
    -- d'endroit ou aller sur ce serveur, et le detour serait invisible.
    CONSTRAINT ck_brouillon_taille CHECK (length(contenu) <= 262144),
    CONSTRAINT uq_brouillon_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_brouillon_agent
        FOREIGN KEY (agent_id, tenant_id) REFERENCES agent (id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT fk_brouillon_ruche
        FOREIGN KEY (ruche_id, tenant_id) REFERENCES ruche (id, tenant_id) ON DELETE CASCADE
);

-- Un seul brouillon par agent et par ruche : deux ne repondraient a aucune
-- question, ils poseraient celle de savoir lequel reprendre.
CREATE UNIQUE INDEX uq_brouillon_agent_ruche
    ON brouillon_visite (tenant_id, agent_id, ruche_id);

-- La liste « mes brouillons », du plus recent au plus ancien : c'est la seule
-- lecture que fait l'ecran.
CREATE INDEX ix_brouillon_agent_recent
    ON brouillon_visite (tenant_id, agent_id, maj_le DESC);

ALTER TABLE brouillon_visite ENABLE ROW LEVEL SECURITY;
ALTER TABLE brouillon_visite FORCE  ROW LEVEL SECURITY;

-- La portee suit celle de l'agent (V16), mais avec une restriction de plus que
-- les autres tables : un brouillon n'appartient pas au tenant, il appartient a
-- SON AGENT. Une saisie en cours est un travail prive tant qu'elle n'est pas
-- devenue une visite — un superviseur a portee globale lit toutes les visites
-- de l'exploitation, il n'a pas a lire les phrases inachevees de ses collegues.
--
-- Noter l'absence de `zumm_portee_globale()`, seule table du schema dans ce cas :
-- une portee globale N'OUVRE PAS les brouillons. Un appelant sans agent connu
-- (compte machine, admin sans affectation) n'en voit donc aucun, ce qui est le
-- comportement voulu et non un effet de bord.
CREATE POLICY p_brouillon_agent ON brouillon_visite
    USING (tenant_id = current_setting('app.current_tenant', true)
           AND agent_id = zumm_agent_courant())
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));

CREATE TRIGGER tg_brouillon_maj BEFORE UPDATE ON brouillon_visite
    FOR EACH ROW EXECUTE FUNCTION zumm_touch_maj_le();

COMMENT ON TABLE brouillon_visite IS
    'Saisie de visite en cours, reprenable sur un autre appareil (SPRINT-24). '
    'N''est PAS une visite : contenu opaque, jamais lu par le registre ni les agregats.';
COMMENT ON COLUMN brouillon_visite.contenu IS
    'JSON du formulaire en cours, ecrit et relu par le front. Le serveur ne le valide pas.';


-- === 2. Couverture reseau du rucher ========================================

ALTER TABLE site ADD COLUMN couverture_reseau VARCHAR(10);

ALTER TABLE site ADD CONSTRAINT ck_site_couverture_reseau
    CHECK (couverture_reseau IS NULL
           OR couverture_reseau IN ('aucune', 'faible', 'correcte', 'bonne'));

-- Index partiel sur ce qui change une decision : on cherche les ruchers ou le
-- reseau manque, jamais ceux ou il passe.
CREATE INDEX ix_site_sans_reseau
    ON site (couverture_reseau) WHERE couverture_reseau IN ('aucune', 'faible');

COMMENT ON COLUMN site.couverture_reseau IS
    'Couverture mobile constatee sur place (SPRINT-24). NULL = inconnue, '
    'ce qui est la reponse honnete tant que personne n''y est alle.';
