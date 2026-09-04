-- ===========================================================================
-- V23 — Priorite de ruche et de rucher (SPRINT-23, lot B)
--
-- Le lot B du plan de couverture porte sur le RUCHER comme unite de travail :
-- interventions groupees, recolte en une saisie, agregats a trois niveaux,
-- comparaison d'emplacements, coordination d'equipes. Cinq de ces six lignes ne
-- demandent aucune donnee nouvelle — elles demandent des routes de lot et des
-- lectures agregees.
--
-- La sixieme, si : le §13 du document d'ecart la deduit d'un contournement que
-- deux concurrents conseillent a leurs utilisateurs — « equipez d'abord vos
-- ruches souches ou vos ruchers strategiques ». Un conseil de priorisation
-- adresse a l'utilisateur est l'aveu d'une priorite que le logiciel ne sait pas
-- porter.
--
-- ---------------------------------------------------------------------------
-- Pourquoi DEUX colonnes et non une
--
-- La priorite d'une ruche et celle d'un rucher ne se deduisent pas l'une de
-- l'autre. Un rucher strategique peut contenir une ruche ordinaire ; une ruche
-- souche peut vivre dans un rucher secondaire. Les fusionner obligerait a
-- inventer une regle d'heritage que personne n'a demandee, et qui serait fausse
-- dans les deux sens.
--
-- Trois niveaux, et pas quatre : `tache` en a quatre parce qu'une tache se
-- classe dans une journee de travail. Un rucher se classe dans une saison, et
-- au-dela de trois rangs personne ne fait la difference.
-- ===========================================================================

ALTER TABLE ruche ADD COLUMN priorite VARCHAR(10) NOT NULL DEFAULT 'normale';
ALTER TABLE site  ADD COLUMN priorite VARCHAR(10) NOT NULL DEFAULT 'normale';

ALTER TABLE ruche ADD CONSTRAINT ck_ruche_priorite
    CHECK (priorite IN ('basse', 'normale', 'haute'));
ALTER TABLE site ADD CONSTRAINT ck_site_priorite
    CHECK (priorite IN ('basse', 'normale', 'haute'));

-- Index partiels : la question posee est « qu'est-ce qui passe en premier »,
-- et elle ne porte jamais sur ce qui est ordinaire. La majorite des lignes
-- restant a `normale`, l'index reste minuscule.
CREATE INDEX ix_ruche_priorite ON ruche (priorite) WHERE priorite <> 'normale';
CREATE INDEX ix_site_priorite  ON site  (priorite) WHERE priorite <> 'normale';

COMMENT ON COLUMN ruche.priorite IS
    'Ruche souche ou secondaire. Independante de celle du rucher : l''une ne se deduit pas de l''autre.';
COMMENT ON COLUMN site.priorite IS
    'Rucher strategique ou secondaire (SPRINT-23). Reprise dans la tournee et les agregats.';
