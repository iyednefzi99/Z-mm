-- ===========================================================================
-- V33 — Alerte sur anomalie d'acces (SPRINT-34)
--
-- Le journal d'audit (V12) enregistrait deja les creations, modifications,
-- suppressions et forcages (V22) — mais AUCUN refus RBAC (403) n'y figurait, et
-- rien ne signalait qu'un compte accumulait des refus. `REVUE-CONSOLIDEE.md`
-- §5 le pointait depuis le SPRINT-09 : « le journal d'audit enregistre,
-- personne ne le lit en continu ».
--
-- Deux ajouts, rien de plus :
--   1. l'action `refus` sur `audit_entree` — un 403 RBAC devient une ligne ;
--   2. un index qui rend efficace le comptage par acteur sur une fenetre
--      glissante (DetecteurAnomalieAcces).
--
-- Pas de nouvelle table : la detection lit `audit_entree`, la tache engendree
-- vit dans `tache` (V22, deja outillee pour l'origine `regle`).
-- ===========================================================================

ALTER TABLE audit_entree DROP CONSTRAINT ck_audit_action;
ALTER TABLE audit_entree ADD CONSTRAINT ck_audit_action
    CHECK (action IN ('creation', 'modification', 'suppression', 'forcage', 'refus'));

COMMENT ON CONSTRAINT ck_audit_action ON audit_entree IS
    'refus (SPRINT-34) : 403 RBAC, journalise pour detecter une anomalie d''acces.';

-- Le comptage se fait par acteur, action et fenetre de temps : sans cet index,
-- chaque refus declencherait un scan de l'historique complet de l'exploitation.
CREATE INDEX ix_audit_anomalie ON audit_entree (tenant_id, acteur, action, instant DESC);
