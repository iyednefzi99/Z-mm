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
    v_vis1 BIGINT; v_vis4 BIGINT;
BEGIN
    -- ─── Purge du tenant (ordre enfant → parent) ───────────────────────────
    -- Les quatre tables du SPRINT-20 partent d'abord : elles référencent la
    -- visite et la ruche, et `observation_pathologie` cascade sur la visite.
    DELETE FROM observation_pathologie WHERE tenant_id = t;
    DELETE FROM comptage_varroa        WHERE tenant_id = t;
    DELETE FROM nourrissement          WHERE tenant_id = t;
    DELETE FROM traitement             WHERE tenant_id = t;
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
           (t, v_r5, v_ag_api, CURRENT_DATE - 30, 'controle',
            'Colonie affaiblie, reine peu prolifique — à surveiller.', 'faible', 'mauvais', 1);
    -- La visite de traitement rend son identifiant : c'est elle qui porte la
    -- pathologie constatée, et le traitement qui en découle.
    INSERT INTO visite (tenant_id, ruche_id, agent_id, date_visite, raison,
                        constatations, effectif_qualitatif, etat_sante, productivite)
    VALUES (t, v_r4, v_ag_sup, CURRENT_DATE - 12, 'traitement',
            'Chute de varroa élevée au lange, traitement posé.', 'moyen', 'moyen', 2)
    RETURNING id INTO v_vis4;

    INSERT INTO photo (tenant_id, visite_id, url, legende)
    VALUES (t, v_vis1, 'https://demo.zumm.tn/photos/couvain-r1.jpg', 'Cadre de couvain operculé');

    -- ─── Grille d'inspection & météo figée (SPRINT-20) ─────────────────────
    -- Une visite remplie à la grille, avec le relevé météo du jour figé. Les
    -- autres visites n'en portent AUCUNE : « non observé » n'est pas « non »,
    -- et un jeu de démonstration qui remplirait tout ferait croire l'inverse.
    UPDATE visite SET couvain_oeufs = true, couvain_larves = true, couvain_opercule = true,
                      motif_ponte = 'compact', reine_vue = true,
                      cadres_couvain = 6, cadres_miel = 4, cadres_pollen = 2,
                      temperament = 'doux',
                      meteo_temperature_c = 24.5, meteo_humidite_pct = 58,
                      meteo_vent_kmh = 11.0, meteo_source = 'open-meteo'
     WHERE tenant_id = t AND id = v_vis1;

    -- La visite de traitement, elle, dit ce qu'elle a vu : ponte lacunaire et
    -- varroose confirmée. C'est le couple qui justifie le traitement ci-dessous.
    UPDATE visite SET couvain_oeufs = true, couvain_opercule = true,
                      motif_ponte = 'lacunaire', reine_vue = false,
                      cadres_couvain = 3, cadres_miel = 2, cadres_pollen = 1,
                      temperament = 'normal'
     WHERE tenant_id = t AND id = v_vis4;

    INSERT INTO observation_pathologie (tenant_id, visite_id, pathologie, gravite, note)
    VALUES (t, v_vis4, 'varroose', 'moderee',
            'Chute naturelle de 7 varroas par jour au lange.');

    -- ─── Référentiel de la ruche (SPRINT-20) ───────────────────────────────
    -- `modele` reste le texte libre ; ces colonnes-ci sont le référentiel
    -- au-dessus, celui qui rend possible une statistique par type.
    UPDATE ruche SET type_ruche = 'dadant', couleur = 'jaune', origine = 'division'
     WHERE tenant_id = t AND id IN (v_r1, v_r2, v_r3);
    UPDATE ruche SET type_ruche = 'langstroth', couleur = 'bleu', origine = 'essaim_capture'
     WHERE tenant_id = t AND id IN (v_r4, v_r5);
    UPDATE ruche SET type_ruche = 'warre', couleur = 'bois', origine = 'nucleus'
     WHERE tenant_id = t AND id = v_r6;

    -- ─── Registre sanitaire (SPRINT-20) ────────────────────────────────────
    -- Le premier traitement est terminé mais ENCORE SOUS CARENCE : c'est le cas
    -- que la démonstration doit montrer — la ruche r4 apparaît au bandeau des
    -- carences, et son miel ne part pas en récolte avant `date_retrait`
    -- (colonne générée : date_fin + delai_carence_jours).
    INSERT INTO traitement (tenant_id, ruche_id, agent_id, visite_id, produit, substance_active,
                            cible, dose, dose_unite, date_debut, date_fin,
                            delai_carence_jours, ordonnance, note)
    VALUES (t, v_r4, v_ag_sup, v_vis4, 'Apivar', 'amitraze', 'varroa', 2, 'laniere',
            CURRENT_DATE - 12, CURRENT_DATE - 2, 14, 'ORD-2027-0142',
            'Deux lanières posées entre les cadres de couvain.'),
           -- Le second est soldé : sa carence est nulle, il ne bloque rien.
           (t, v_r1, v_ag_api, NULL, 'Acide oxalique 3,2 %', 'acide oxalique', 'varroa',
            50, 'ml', CURRENT_DATE - 200, CURRENT_DATE - 199, 0, NULL,
            'Dégouttement hors couvain, traitement d''hiver.');

    INSERT INTO nourrissement (tenant_id, ruche_id, agent_id, date_apport, type_aliment,
                               quantite, quantite_unite, motif, note)
    VALUES (t, v_r5, v_ag_api, CURRENT_DATE - 25, 'sirop_2_1', 8, 'kg', 'hivernage',
            'Colonie faible, constitution des réserves.'),
           (t, v_r3, v_ag_api, CURRENT_DATE - 40, 'sirop_1_1', 4, 'l', 'stimulation',
            'Stimulation de ponte avant la miellée.');

    -- Deux comptages, deux méthodes, deux unités — et c'est tout l'intérêt :
    -- 21 varroas sur 3 jours de lange donnent 7,00 varroas/jour (« traiter ») ;
    -- 2 varroas sur 300 abeilles donnent 0,67 % (« faible »). Le taux n'est pas
    -- stocké : il est calculé par `ComptageVarroaService`, avec son unité.
    INSERT INTO comptage_varroa (tenant_id, ruche_id, agent_id, visite_id, date_comptage,
                                 methode, varroas_comptes, abeilles_echantillon,
                                 jours_exposition, note)
    VALUES (t, v_r4, v_ag_sup, v_vis4, CURRENT_DATE - 12, 'lange', 21, NULL, 3,
            'Lange graissé posé trois jours avant la visite.'),
           (t, v_r1, v_ag_api, NULL, CURRENT_DATE - 6, 'sucre_glace', 2, 300, NULL,
            'Échantillon prélevé sur cadre de couvain.');

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

    RAISE NOTICE 'Seed « % » : fermier %, 2 fermes, 4 agents, 3 sites, 6 ruches, '
                 '2 traitements (dont 1 sous carence), 2 nourrissements, 2 comptages de varroa.',
                 t, v_fermier;
END $$;
