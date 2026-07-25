-- ===========================================================================
-- seed-demo.sql — Jeu de données de démonstration (test réel du site)
--
-- Peuple la base avec une exploitation apicole réaliste, sous le tenant
-- « exploitation-demo » — celui porté par les comptes de test du realm Keycloak
-- de développement (infra/keycloak/realm-zumm.dev.json, claim tenant_id).
--
-- ⚠️ DÉVELOPPEMENT / DÉMONSTRATION UNIQUEMENT. Ne jamais jouer en production.
--
-- Idempotent : purge d'abord les données du tenant, puis les réinsère. À lancer
-- via infra/seed-demo.sh, ou directement :
--   docker compose --env-file .env -f infra/docker-compose.yml \
--     exec -T postgres psql -U zumm -d zumm < infra/seed-demo.sql
-- ===========================================================================

-- Contexte tenant : utile si le script est joué avec le rôle applicatif (RLS).
-- Avec le rôle propriétaire/superutilisateur, la RLS est contournée ; le SET
-- reste sans effet néfaste.
SET app.current_tenant = 'exploitation-demo';

DO $$
DECLARE
    t              TEXT := 'exploitation-demo';
    v_fermier      BIGINT;
    v_ferme_n      BIGINT;
    v_ferme_s      BIGINT;
    v_ag_resp      BIGINT;
    v_ag_api       BIGINT;
    v_ag_sup       BIGINT;
    v_ag_admin     BIGINT;
    v_site_beja    BIGINT;
    v_site_nabeul  BIGINT;
    v_site_kairouan BIGINT;
    v_r1 BIGINT; v_r2 BIGINT; v_r3 BIGINT; v_r4 BIGINT; v_r5 BIGINT; v_r6 BIGINT;
    v_plan1 BIGINT; v_plan2 BIGINT;
    v_vis1 BIGINT;
BEGIN
    -- ─── Purge du tenant (ordre enfant → parent) ───────────────────────────
    DELETE FROM alerte       WHERE tenant_id = t;
    DELETE FROM mesure       WHERE tenant_id = t;
    DELETE FROM recolte      WHERE tenant_id = t;
    DELETE FROM suivi_reine  WHERE tenant_id = t;
    DELETE FROM tache        WHERE tenant_id = t;
    DELETE FROM photo        WHERE tenant_id = t;
    DELETE FROM visite       WHERE tenant_id = t;
    DELETE FROM planning     WHERE tenant_id = t;
    DELETE FROM compartiment WHERE tenant_id = t;
    DELETE FROM ruche        WHERE tenant_id = t;
    DELETE FROM site         WHERE tenant_id = t;
    DELETE FROM agent        WHERE tenant_id = t;
    DELETE FROM ferme        WHERE tenant_id = t;
    DELETE FROM fermier      WHERE tenant_id = t;

    -- ─── Fermier & fermes ──────────────────────────────────────────────────
    INSERT INTO fermier (tenant_id, nom, contact)
    VALUES (t, 'Domaine des Oliviers', 'contact@domaine-oliviers.tn')
    RETURNING id INTO v_fermier;

    INSERT INTO ferme (tenant_id, nom, fermier_id)
    VALUES (t, 'Ferme Nord', v_fermier) RETURNING id INTO v_ferme_n;
    INSERT INTO ferme (tenant_id, nom, fermier_id)
    VALUES (t, 'Ferme Sud', v_fermier) RETURNING id INTO v_ferme_s;

    -- ─── Agents (un par rôle) ──────────────────────────────────────────────
    INSERT INTO agent (tenant_id, nom, role, ferme_id, email)
    VALUES (t, 'Sofien Ben Ali', 'responsable', v_ferme_n, 'sofien@domaine-oliviers.tn') RETURNING id INTO v_ag_resp;
    INSERT INTO agent (tenant_id, nom, role, ferme_id, email)
    VALUES (t, 'Amine Trabelsi', 'apiculteur', v_ferme_n, 'amine@domaine-oliviers.tn') RETURNING id INTO v_ag_api;
    INSERT INTO agent (tenant_id, nom, role, ferme_id, email)
    VALUES (t, 'Leïla Haddad', 'superviseur', v_ferme_s, 'leila@domaine-oliviers.tn') RETURNING id INTO v_ag_sup;
    INSERT INTO agent (tenant_id, nom, role, ferme_id, email)
    VALUES (t, 'Nadia Khelifi', 'admin', NULL, 'nadia@domaine-oliviers.tn') RETURNING id INTO v_ag_admin;

    -- ─── Sites géolocalisés (Tunisie) ──────────────────────────────────────
    INSERT INTO site (tenant_id, nom, ferme_id, latitude, longitude, altitude, date_mise_en_oeuvre)
    VALUES (t, 'Rucher Béja', v_ferme_n, 36.725000, 9.181000, 210.0, DATE '2025-03-15')
    RETURNING id INTO v_site_beja;
    INSERT INTO site (tenant_id, nom, ferme_id, latitude, longitude, altitude, date_mise_en_oeuvre)
    VALUES (t, 'Rucher Nabeul', v_ferme_s, 36.451000, 10.735000, 25.0, DATE '2025-04-02')
    RETURNING id INTO v_site_nabeul;
    INSERT INTO site (tenant_id, nom, ferme_id, latitude, longitude, altitude, date_mise_en_oeuvre)
    VALUES (t, 'Rucher Kairouan', v_ferme_s, 35.678000, 10.096000, 68.0, DATE '2025-05-10')
    RETURNING id INTO v_site_kairouan;

    -- ─── Ruches (2 par site) + compositions ────────────────────────────────
    INSERT INTO ruche (tenant_id, modele, site_id, ferme_id, agent_responsable_id, etat)
    VALUES (t, 'Langstroth', v_site_beja, v_ferme_n, v_ag_api, 'active') RETURNING id INTO v_r1;
    INSERT INTO ruche (tenant_id, modele, site_id, ferme_id, agent_responsable_id, etat)
    VALUES (t, 'Dadant', v_site_beja, v_ferme_n, v_ag_api, 'en_collecte') RETURNING id INTO v_r2;
    INSERT INTO ruche (tenant_id, modele, site_id, ferme_id, agent_responsable_id, etat)
    VALUES (t, 'Dadant', v_site_nabeul, v_ferme_s, v_ag_api, 'active') RETURNING id INTO v_r3;
    INSERT INTO ruche (tenant_id, modele, site_id, ferme_id, agent_responsable_id, etat)
    VALUES (t, 'Warré', v_site_nabeul, v_ferme_s, v_ag_sup, 'peuplee') RETURNING id INTO v_r4;
    INSERT INTO ruche (tenant_id, modele, site_id, ferme_id, agent_responsable_id, etat)
    VALUES (t, 'Langstroth', v_site_kairouan, v_ferme_s, v_ag_api, 'active') RETURNING id INTO v_r5;
    INSERT INTO ruche (tenant_id, modele, site_id, ferme_id, agent_responsable_id, etat)
    VALUES (t, 'Dadant', v_site_kairouan, v_ferme_s, v_ag_sup, 'creee') RETURNING id INTO v_r6;

    INSERT INTO compartiment (tenant_id, ruche_id, type, nb_cadres) VALUES
        (t, v_r1, 'corps', 10), (t, v_r1, 'hausse', 9), (t, v_r1, 'hausse', 9),
        (t, v_r2, 'corps', 10), (t, v_r2, 'hausse', 9),
        (t, v_r3, 'corps', 10), (t, v_r3, 'hausse', 9),
        (t, v_r4, 'corps', 8),
        (t, v_r5, 'corps', 10), (t, v_r5, 'hausse', 9), (t, v_r5, 'hausse', 9),
        (t, v_r6, 'corps', 10);

    -- ─── Plannings ─────────────────────────────────────────────────────────
    INSERT INTO planning (tenant_id, ruche_id, agent_id, superviseur_id, date_prevue, raison, statut)
    VALUES (t, v_r1, v_ag_api, v_ag_sup, CURRENT_DATE + 3, 'controle', 'approuve')
    RETURNING id INTO v_plan1;
    INSERT INTO planning (tenant_id, ruche_id, agent_id, date_prevue, raison, statut)
    VALUES (t, v_r3, v_ag_api, CURRENT_DATE + 7, 'recolte', 'propose')
    RETURNING id INTO v_plan2;

    -- ─── Visites & rapports ────────────────────────────────────────────────
    INSERT INTO visite (tenant_id, ruche_id, agent_id, planning_id, date_visite, raison,
                        constatations, effectif_qualitatif, etat_sante, productivite)
    VALUES (t, v_r1, v_ag_api, v_plan1, CURRENT_DATE - 5, 'controle',
            'Colonie vigoureuse, couvain compact, réserves correctes.', 'fort', 'bon', 3)
    RETURNING id INTO v_vis1;
    INSERT INTO visite (tenant_id, ruche_id, agent_id, date_visite, raison,
                        constatations, effectif_qualitatif, etat_sante, productivite)
    VALUES (t, v_r2, v_ag_api, CURRENT_DATE - 4, 'recolte',
            'Hausse operculée à 80 %, récolte imminente.', 'fort', 'bon', 3),
           (t, v_r4, v_ag_sup, CURRENT_DATE - 12, 'traitement',
            'Présence de varroa, traitement à l''acide oxalique appliqué.', 'moyen', 'moyen', 2),
           (t, v_r5, v_ag_api, CURRENT_DATE - 30, 'controle',
            'Colonie affaiblie, reine peu prolifique — à surveiller.', 'faible', 'mauvais', 1);

    INSERT INTO photo (tenant_id, visite_id, url, legende)
    VALUES (t, v_vis1, 'https://demo.zumm.tn/photos/couvain-r1.jpg', 'Cadre de couvain operculé');

    -- ─── Mesures (séries temporelles) ──────────────────────────────────────
    -- Poids r1 : série stable ~34 kg, une pointe (anomalie EWMA) puis un poids bas
    -- (déclencheur d'alerte sous le seuil de 15 kg).
    INSERT INTO mesure (tenant_id, ruche_id, type_indicateur, instant, valeur) VALUES
        (t, v_r1, 'poids', now() - INTERVAL '8 day',  34.10),
        (t, v_r1, 'poids', now() - INTERVAL '7 day',  34.30),
        (t, v_r1, 'poids', now() - INTERVAL '6 day',  34.00),
        (t, v_r1, 'poids', now() - INTERVAL '5 day',  34.40),
        (t, v_r1, 'poids', now() - INTERVAL '4 day',  33.90),
        (t, v_r1, 'poids', now() - INTERVAL '3 day',  55.00),  -- pointe (anomalie)
        (t, v_r1, 'poids', now() - INTERVAL '2 day',  34.20),
        (t, v_r1, 'poids', now() - INTERVAL '1 day',  12.00);  -- sous le seuil
    INSERT INTO mesure (tenant_id, ruche_id, type_indicateur, instant, valeur) VALUES
        (t, v_r1, 'temperature', now() - INTERVAL '2 day', 34.50),
        (t, v_r1, 'temperature', now() - INTERVAL '1 day', 35.10),
        (t, v_r1, 'humidite',    now() - INTERVAL '1 day', 62.00);
    -- Poids r3 : série saine, montée régulière (miellée).
    INSERT INTO mesure (tenant_id, ruche_id, type_indicateur, instant, valeur) VALUES
        (t, v_r3, 'poids', now() - INTERVAL '5 day', 28.00),
        (t, v_r3, 'poids', now() - INTERVAL '4 day', 29.20),
        (t, v_r3, 'poids', now() - INTERVAL '3 day', 30.10),
        (t, v_r3, 'poids', now() - INTERVAL '2 day', 31.40),
        (t, v_r3, 'poids', now() - INTERVAL '1 day', 32.80);

    -- ─── Alerte ouverte (poids sous le seuil sur r1) ───────────────────────
    INSERT INTO alerte (tenant_id, ruche_id, type_indicateur, niveau, message, valeur_declenchement, ouverte)
    VALUES (t, v_r1, 'poids', 'critique', 'Poids 12.0 kg sous le seuil de 15 kg', 12.00, true);

    -- ─── Tâches & rappels ──────────────────────────────────────────────────
    INSERT INTO tache (tenant_id, libelle, ruche_id, agent_id, echeance, faite) VALUES
        (t, 'Poser les hausses avant la miellée', v_r3, v_ag_api, CURRENT_DATE - 2, false), -- rappel échu
        (t, 'Commander des cadres gaufrés',       NULL, v_ag_resp, CURRENT_DATE + 5, false),
        (t, 'Traiter contre le varroa (r4)',      v_r4, v_ag_sup,  CURRENT_DATE - 10, true);

    -- ─── Récoltes & traçabilité ────────────────────────────────────────────
    INSERT INTO recolte (tenant_id, ruche_id, date_recolte, quantite_kg, type_miel, lot) VALUES
        (t, v_r2, CURRENT_DATE - 3, 18.500, 'Toutes fleurs',
         'ZUMM-' || v_r2 || '-' || to_char(CURRENT_DATE - 3, 'YYYYMMDD') || '-01'),
        (t, v_r5, CURRENT_DATE - 20, 12.250, 'Romarin',
         'ZUMM-' || v_r5 || '-' || to_char(CURRENT_DATE - 20, 'YYYYMMDD') || '-01');

    -- ─── Suivi de la reine ─────────────────────────────────────────────────
    INSERT INTO suivi_reine (tenant_id, ruche_id, date_evenement, statut, couleur_marquage, annee_naissance, race, note)
    VALUES
        (t, v_r1, DATE '2024-05-01', 'introduite', 'vert', 2024, 'Buckfast', 'Reine fécondée, marquage vert.'),
        (t, v_r1, CURRENT_DATE - 5, 'en_ponte', 'vert', 2024, 'Buckfast', 'Ponte régulière, bon couvain.'),
        (t, v_r5, CURRENT_DATE - 30, 'disparue', NULL, NULL, 'Locale', 'Reine non retrouvée, colonie bourdonneuse.');

    RAISE NOTICE 'Seed « % » : fermier %, 2 fermes, 4 agents, 3 sites, 6 ruches.', t, v_fermier;
END $$;
