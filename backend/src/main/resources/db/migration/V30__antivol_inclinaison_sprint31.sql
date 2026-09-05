-- ===========================================================================
-- V30 — Alarme anti-vol et inclinaison (SPRINT-31, lot F2)
--
-- Deux des quatre lignes du lot F2 (§5 de `docs/ECART-CONCURRENTS.md`). Les
-- deux autres sont traitees dans ADR-014, et l'une d'elles est REFUSEE — voir
-- la note de revision du document d'ecart.
--
-- ---------------------------------------------------------------------------
-- POURQUOI CE LOT N'A PAS BESOIN D'ACHETER DE MATERIEL
--
-- « Le vol de ruches est pourtant la menace qui justifie `PolitiquePositions` :
-- Zumm CACHE la position pour proteger du vol, Onibi ALERTE quand il survient.
-- Les deux reponses sont complementaires, Zumm n'a que la premiere. »
--
-- La detection ne demande aucun capteur nouveau : une ruche volee ou renversee
-- se voit dans la serie de POIDS que l'API ingere deja. Ce qui manquait n'est
-- pas une donnee, c'est une REGLE — et le seul point delicat de cette regle
-- tient en une phrase : une chute de vingt kilogrammes est une RECOLTE si une
-- recolte a ete enregistree ce jour-la, et un vol sinon.
--
-- Sans cette verification, la premiere miellee de l'annee reveillerait
-- l'alarme sur tout le rucher, et l'apiculteur la couperait. Une alerte qu'on
-- coupe ne protege plus de rien.
--
-- ---------------------------------------------------------------------------
-- LA LECON DE LA V26, APPLIQUEE D'EMBLEE
--
-- Au SPRINT-26, `alimentation` avait ete ajoute a `ck_mesure_indicateur` mais
-- pas a `ck_alerte_indicateur` : l'ingestion reussissait, l'ouverture de
-- l'alerte echouait en 409, et c'est un test d'integration qui l'a montre.
-- `inclinaison` est donc ajoute aux DEUX contraintes dans le meme fichier.
-- ===========================================================================


-- === 1. L'inclinaison, sixieme indicateur ==================================
--
-- En degres. Un capteur qui en pousse est rare ; le point n'est pas de le
-- supposer present, mais de ne pas obliger celui qui en a a inventer un
-- indicateur. Le meme raisonnement qu'a l'alimentation (V26) : une valeur
-- d'enumeration, un seuil, et `SeuilAlerteService` s'en occupe comme des
-- autres.
ALTER TABLE mesure DROP CONSTRAINT ck_mesure_indicateur;
ALTER TABLE mesure ADD CONSTRAINT ck_mesure_indicateur CHECK (type_indicateur IN
    ('poids', 'temperature', 'humidite', 'activite', 'alimentation', 'inclinaison'));

ALTER TABLE alerte DROP CONSTRAINT ck_alerte_indicateur;
ALTER TABLE alerte ADD CONSTRAINT ck_alerte_indicateur CHECK (type_indicateur IN
    ('poids', 'temperature', 'humidite', 'activite', 'alimentation', 'inclinaison'));


-- === 2. Une alerte porte desormais sa CATEGORIE ============================
--
-- Le probleme, decouvert en ecrivant la regle : une ruche peut etre A LA FOIS
-- legere et volee. `AlerteRepository` ne cherchait qu'un couple
-- (ruche, indicateur, ouverte), si bien qu'une alerte de seuil deja ouverte sur
-- le poids aurait EMPECHE l'alerte de vol de s'ouvrir — c'est-a-dire
-- exactement au moment ou elle sert.
--
-- Deux categories, donc, et deux alertes qui coexistent sur le meme indicateur.
-- La valeur par defaut `seuil` decrit toutes les lignes existantes : aucune
-- alerte d'avant ce sprint n'est un vol.
ALTER TABLE alerte ADD COLUMN categorie VARCHAR(20) NOT NULL DEFAULT 'seuil';

ALTER TABLE alerte
    ADD CONSTRAINT ck_alerte_categorie CHECK (categorie IN ('seuil', 'antivol'));

-- ET l'index UNIQUE suit, sans quoi la moitie du travail serait faite. `V8`
-- garantissait « au plus une alerte ouverte par (ruche, indicateur) » ; la
-- garder telle quelle ferait echouer l'insertion de l'alerte de vol en 409 sur
-- une ruche qui a deja une alerte de poids — c'est-a-dire exactement le cas
-- qu'on vient d'ouvrir. Le finder et l'index disent desormais la meme chose.
DROP INDEX uq_alerte_ouverte;
CREATE UNIQUE INDEX uq_alerte_ouverte
    ON alerte (ruche_id, type_indicateur, categorie) WHERE ouverte;

COMMENT ON COLUMN alerte.categorie IS
    'Famille de l''alerte (SPRINT-31) : `seuil` pour un depassement, `antivol` '
    'pour une chute brutale sans recolte. Une ruche peut etre legere ET volee ; '
    'sans cette colonne, la premiere alerte empechait la seconde.';
