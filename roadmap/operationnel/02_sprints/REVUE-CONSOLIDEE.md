# 🎯 Revue de sprint consolidée — SPRINT-00 → SPRINT-18

> **Ce que ce document est.** La revue de sprint (*Sprint Review*) est l'inspection
> de l'incrément par les parties prenantes. Chaque sprint a la sienne, dans sa
> fiche. Ce document les rassemble : ce qui a été démontré, ce qui a été refusé ou
> réservé, et ce que chaque revue a changé au plan suivant.
>
> **Ce qu'il n'est pas.** Ni un résumé de fonctionnalités, ni une plaquette. Une
> revue qui ne montrerait que les réussites n'aurait aucune valeur d'inspection :
> les défauts trouvés *en séance* y figurent au même titre que les démonstrations
> réussies — ce sont eux qui ont fait bouger le backlog.

**Périmètre :** 19 sprints, du 2026-07-14 au 2027-04-26.
**Dernière mise à jour :** 2026-07-26.

---

## 1. Vue d'ensemble

| Sprint | Période | Thème | Pts livrés | Capacité |
|:---|:---|:---|:---:|:---:|
| [00](SPRINT-00.md) | 2026-07-14 → 07-27 | Cadrage, ADR et *walking skeleton* | 0 *(hors vélocité)* | 40 |
| [01](SPRINT-01.md) | 2026-07-28 → 08-10 | Fondation — CRUD et configuration | 36 | 40 |
| [02](SPRINT-02.md) | 2026-08-11 → 08-24 | Ruche, composition, mesure, i18n, TLS | 39 | 40 |
| [03](SPRINT-03.md) | 2026-08-25 → 09-07 | Workflow de visite | 36 | 40 |
| [04](SPRINT-04.md) | 2026-09-08 → 09-21 | Hors-ligne, OIDC, RBAC, unités | 39 | 40 |
| [05](SPRINT-05.md) | 2026-09-22 → 10-05 | Tableaux de bord, tâches, export | 39 | 40 |
| [06](SPRINT-06.md) | 2026-10-06 → 10-19 | ROI, ingestion capteurs, API tierce, météo | 39 | 40 |
| [07](SPRINT-07.md) | 2026-10-20 → 11-02 | Anomalie EWMA, carte, reine, récolte/QR | 39 | 40 |
| [08](SPRINT-08.md) | 2026-11-03 → 11-16 | Microservice IA Python, tests | 37 | 40 |
| [09](SPRINT-09.md) | 2026-11-17 → 11-30 | Notifications, rapport PDF, audit, prévision | 26 | 26 |
| [10](SPRINT-10.md) | 2026-12-01 → 12-14 | Intelligence spatiale PostGIS, socle de test front | 34 | 34 |
| [11](SPRINT-11.md) | 2027-01-05 → 01-18 | Fiabilité de la session et du front | 34 | 40 |
| [12](SPRINT-12.md) | 2027-01-19 → 02-01 | **Durcissement de la sécurité** | 34 | 40 |
| [13](SPRINT-13.md) | 2027-02-02 → 02-15 | PWA livrable, graphiques et carte | 32 | 40 |
| [14](SPRINT-14.md) | 2027-02-16 → 03-01 | Idempotence, index, conformité miel | 34 | 40 |
| [15](SPRINT-15.md) | 2027-03-02 → 03-15 | Ressources de langue externalisées | 8 | 40 |
| [16](SPRINT-16.md) | 2027-03-16 → 03-29 | Sessions sans jeton, portée par affectation | 34 | 40 |
| [17](SPRINT-17.md) | 2027-03-30 → 04-12 | Dettes techniques | 29 | 40 |
| [18](SPRINT-18.md) | 2027-04-13 → 04-26 | Les deux dernières dettes | 13 | 40 |

**Total : 582 points sur 18 sprints de livraison** — moyenne 32,3, capacité de
référence 40.

**Lecture de la vélocité.** Elle se casse en deux, et la rupture est délibérée.

- **Sprints 01 → 08 : 36 à 39 points par sprint.** Phase de construction du
  périmètre fonctionnel, sur une capacité de 40.
- **Sprints 09 → 18 : 8 à 34 points.** La capacité n'a pas baissé ; ce sont les
  sprints qui ont cessé d'être remplis jusqu'au bord. Deux raisons, toutes deux
  assumées en revue : à partir du SPRINT-09 le travail porte de plus en plus sur
  la **qualité de l'existant** (sécurité, tenue à l'échelle, dette), dont le
  chiffrage est moins fiable ; et les SPRINT-15 (8 points) et SPRINT-18
  (13 points) sont volontairement courts — ils soldent des points ouverts plutôt
  que d'ouvrir un chantier.

Une vélocité qui remonterait à 39 sur ces derniers sprints ne serait pas un bon
signe : elle voudrait dire qu'on a re-rempli le sprint sans garder de marge pour
ce que les revues font systématiquement apparaître.

---

## 2. Ce que les revues ont réellement trouvé

C'est la partie utile. Neuf revues sur dix-neuf ont produit une découverte qui
n'était **au programme d'aucune d'entre elles**, et qui a changé le plan.

| Sprint | Découvert en séance | Conséquence |
|:---:|:---|:---|
| **12** | La porte CI réparée trouve **29 vulnérabilités réelles** dans l'arbre de dépendances, dont trois de score 9.1 à 9.6. Spring Boot figé en 3.4.1 — dix-huit mois de correctifs manquants | Montée en 3.5.16 séance tenante, 29 → 0, sans régression sur 147 tests. **Leçon : aucune mesure de configuration ne compense une dépendance non tenue à jour** |
| **12** | Le stockage des jetons en `localStorage` est acté comme dette **bloquante pour la mise en production** | Inscrit au plus tard au SPRINT-16 sous forme de BFF ([ADR-006](../06_decisions/ADR-006-stockage-des-jetons.md)) — et effectivement livré là |
| **13** | La palette catégorielle de la charte, validée **par calcul** et non à l'œil, a deux emplacements adjacents en deçà du plancher de distinction (ΔE OKLab 12,9 pour un plancher de 15) | Permutation des emplacements 5 et 6 ; palette du mode sombre **recalculée** plutôt qu'éclaircie |
| **13** | Les tuiles publiques d'OpenStreetMap excluent la production | `VITE_TUILES_URL` ajoutée — seule option qui garantisse qu'aucune position de rucher ne sorte du système |
| **14** | **La démonstration prévue échoue en séance** : PostgreSQL refuse la compression TimescaleDB sur une table sous RLS. L'ADR-001 et l'ADR-002 étaient incompatibles depuis leur rédaction | [ADR-008](../06_decisions/ADR-008-rls-contre-compression.md) : RLS conservée, compression abandonnée, coût chiffré (~2,8 Go/an pour 500 ruches) |
| **16** | Les tests d'US-057 étaient **verts à tort** : en test, l'application se connecte avec le propriétaire de la base, qui contourne la RLS | Tests refaits sous le rôle applicatif `zumm_app`. **Leçon : un test vert ne dit rien tant qu'on n'a pas vérifié qu'il peut rougir** |
| **17** | Le contrôle de parité trouve un défaut **dans le contrat publié** dès sa première exécution : `LocalTime` décrit comme `{hour, minute, second, nano}` alors que l'API sérialise `"14:30:00"` | Corrigé au niveau du type, pas champ par champ. Tout intégrateur tiers générant son client depuis le contrat aurait produit du code cassé |
| **18** | **US-080 est irréalisable telle qu'elle a été planifiée** : PostgreSQL refuse un agrégat continu sur une hypertable sous RLS — seconde manifestation du conflit de l'ADR-008, et le contournement par vue filtrante tombe aussi, le refus arrivant à la *création* | ADR-008 **généralisé** plutôt que complété au cas par cas : sous RLS, aucune fonctionnalité *matérialisante* de TimescaleDB n'est disponible. Le bénéfice est obtenu autrement (`time_bucket` à la demande) : ~105 000 points transportés → ~1 100 |
| **18** | Une requête **native** échappe au discriminant `@TenantId` d'Hibernate, qui ne réécrit que le JPQL | En production la RLS couvre ce trou, mais le projet a toujours voulu **deux** barrières. Les trois requêtes natives portent désormais un filtre de tenant explicite |

Deux de ces neuf découvertes viennent d'une démonstration qui **a échoué devant
les parties prenantes** (sprints 14 et 16), et une troisième d'une user story
livrée **autrement** que planifiée (sprint 18). C'est le résultat le plus
difficile à obtenir d'une revue, et celui qui a le plus de valeur : une
démonstration entièrement répétée à l'avance n'inspecte rien.

---

## 3. Le fil des revues, sprint par sprint

### Phase 1 — Prouver la chaîne, puis construire (00 → 08)

- **SPRINT-00.** Rien de métier n'est démontré, et c'est le sujet : connexion HTTPS
  via Keycloak **en production**, persistance d'une entité, réaction du tableau de
  bord Grafana, **restauration de la base depuis une sauvegarde**, et les quatre
  ADR acceptés. Critère de sortie posé d'avance : *si le walking skeleton n'est pas
  en production à la fin du sprint, le planning est renégocié* — pas absorbé en
  heures supplémentaires.
- **SPRINT-01 → 03.** Les entités métier, puis le workflow de visite. Les revues
  portent sur des parcours complets (créer une ferme, un site, une ruche, planifier
  et rapporter une visite), pas sur des écrans isolés.
- **SPRINT-04.** Première revue jouée **hors ligne** : saisie au rucher sans
  réseau, synchronisation au retour. C'est le scénario qui structure tout le reste
  du produit.
- **SPRINT-05 → 07.** Tableaux de bord, ROI, ingestion de capteurs, détection
  d'anomalie EWMA, carte, suivi de reine, traçabilité par QR.
- **SPRINT-08.** Le microservice IA Python, et le repli local quand il est absent —
  démontré en l'arrêtant en séance. Ce geste devient un rituel des revues
  suivantes.

### Phase 2 — Fiabiliser ce qui existe (09 → 11)

- **SPRINT-09.** Notifications, rapport PDF, journal d'audit, prévision de récolte.
  La vélocité passe de 37 à 26 : premier sprint où le chiffrage tient compte de la
  qualité attendue plutôt que du volume de fonctionnalités.
- **SPRINT-10.** Intelligence spatiale PostGIS (regroupement de sites, plus proches
  voisins, ordre de tournée) et **socle de test du front**, jusque-là inexistant.
- **SPRINT-11.** Session tenue plus de trente minutes en séance sans déconnexion ni
  perte de saisie, navigation au bouton retour, partage d'URL, liste paginée,
  bascule FR → AR des dates et des nombres, suppression confirmée **au clavier
  seul**. Deux défauts trouvés par les tests en cours de sprint, dont une `Modale`
  qui volait le focus à chaque frappe.

### Phase 3 — Fermer les trous (12 → 17)

- **SPRINT-12.** Revue jouée comme une **tentative d'intrusion** : six portes
  ouvertes au sprint précédent, chacune essayée sur l'image d'avant puis sur celle
  du jour. C'est la revue qui a le plus rapporté du projet entier (voir § 2).
- **SPRINT-13.** Première revue **entièrement jouée depuis la pile déployée** —
  aucun `npm run dev` en séance. Le front était complet et testé depuis trois
  sprints, et n'était servi par personne.
- **SPRINT-14.** Revue construite sur le scénario de terrain (« le réseau tombe
  entre la requête et la réponse ») plutôt que sur la fonctionnalité. C'est ce
  cadrage qui a fait apparaître un bug de **perte de données** que personne n'aurait
  signalé : l'utilisateur aurait conclu qu'il avait oublié de saisir.
- **SPRINT-15.** Revue courte, et la garantie est **cassée devant témoins** : une
  clé réellement supprimée d'`en.json`, `tsc` qui échoue en la nommant.
- **SPRINT-16.** `localStorage` ouvert dans la console en séance : plus aucun jeton.
  Un compte apiculteur affecté à trois ruches sur un parc de plus de deux cents ne
  rend plus que trois lignes — avant, la même requête rendait **la carte des
  ruchers**.
- **SPRINT-17.** Aucun écran nouveau : la revue remet l'application dans les
  conditions où la dette faisait mal. Huit millions de relevés, `OutOfMemoryError`
  avant / page rendue après ; `docker compose restart backend` en direct sans
  déconnecter personne.
- **SPRINT-18.** La revue la plus inconfortable, et la plus utile : **une des deux
  user stories n'a pas été livrée comme elle avait été planifiée**, et l'échec est
  montré tel quel en séance, commande à l'appui. Le bénéfice recherché est obtenu
  autrement, chiffré (~105 000 points transportés → ~1 100 pour un tracé
  indiscernable), et ce qui est perdu — la mémorisation du résultat — est dit
  explicitement.

---

## 4. Traçabilité — de la user story au code

| Domaine | US | Où le vérifier |
|:---|:---|:---|
| CRUD métier | US-001 → 010 | `controller/`, `service/`, `V2__modele_metier_sprint01.sql` |
| Ruche, composition, mesure | US-011 → 020 | `V4`, `V5` (hypertable TimescaleDB) |
| Visite et rapport | US-021 → 030 | `V6__visites_rapports_sprint03.sql` |
| Hors-ligne, OIDC, RBAC | US-031 → 040 | `offline/file.ts`, `SecurityConfig`, `auth/` |
| Capteurs, météo, anomalie | US-041 → 044 | `MeteoService`, `AnomalieService`, `ia-service/` |
| Spatial PostGIS | US-045 → 049 | `SiteRepository` (requêtes natives), `CarteVue.tsx` |
| Session et front | US-050 → 054 | `routage/`, `i18n/formats.ts`, `ui/dialogues.tsx` |
| Idempotence, conformité | US-055, US-056 | `FiltreIdempotence`, `V14`, `V15` |
| Portée par agent | US-057 | `securite/FiltrePortee`, `ResolveurPortee`, `V16` |
| Sécurité | US-058 → 063 | `TenantFilter`, `ValidateurAudience`, `PolitiquePositions`, `infra/nginx/nginx.conf` |
| PWA, graphiques, carte | US-064 → 068 | `infra/frontend.Dockerfile`, `ui/graphiques.tsx`, `CarteFond.tsx` |
| Échelle et couverture | US-069 → 071 | `V13`, `@EntityGraph`, JaCoCo fusionné |
| i18n externalisée | US-072 | `i18n/locales/{fr,en,ar}.json` |
| BFF | US-073 | `config/`, `V17__sessions_serveur_sprint17.sql` |
| Dettes | US-074 → 078 | `ProductionService`, `AlerteSanitaireService`, `CalendrierService`, `MoteurAnomalie`, `api/parite.ts` |
| Dernières dettes | US-079, US-080 | `SyntheseService`, `MesureRepository` (`time_bucket`), `web/dto/PointJournalier` |

**Ordres de grandeur à la fin du SPRINT-18** — 18 entités métier (dont la sonde
`Ping` du SPRINT-00, conservée volontairement), 22 contrôleurs REST, 16 repositories,
30 services, **17 migrations Flyway**, ~11 000 lignes Java et ~11 300 lignes
TypeScript, 10 ADR, 20 classes de test d'intégration Testcontainers.

**Campagnes de test au dernier passage vérifié** — back : **59 unitaires +
111 d'intégration, `Skipped : 0`**, `BUILD SUCCESS`, plancher JaCoCo tenu.
Front : **118 tests Vitest**, 0 erreur ESLint, `typecheck` et `build` verts,
paquet initial 230 ko (73,7 ko compressés).

---

## 5. Ce qui reste ouvert après le SPRINT-18

Une revue consolidée qui se terminerait sur un bilan positif ne serait pas une
inspection. Voici ce qui n'est **pas** fait.

**Soldé depuis** : `SyntheseService` agrège désormais en base (US-079) ;
l'agrégat continu TimescaleDB est **définitivement écarté** — impossible sous RLS,
règle généralisée dans l'ADR-008, bénéfice obtenu par `time_bucket` à la demande
(US-080) ; l'analyse statique du code est couverte par CodeQL (Java, TypeScript,
Python) et le scan des images construites est entré dans la CI.

| Point ouvert | Nature | Depuis |
|:---|:---|:---|
| Pas de chiffrement au repos des positions GPS | **Arbitrage**, pas oubli : chiffrer applicativement interdirait les requêtes PostGIS de l'EPIC-012 | à trancher par ADR avant mise en production |
| DAST non exécuté en continu | Un balayage utile exige la pile entière ; procédure manuelle documentée dans [`docs/SECURITE.md`](../../../docs/SECURITE.md) | à rejuger quand une pré-production permanente existera |
| Flux OIDC toujours non joué en CI | 19 tests, mais tous avec un Keycloak simulé — précisément l'angle mort qui avait laissé passer l'absence de rafraîchissement | SPRINT-11 |
| Pagination sur 7 listes seulement | Visites et récoltes ont un chargement composite | SPRINT-11 |
| Couverture des vues front faible | 12,9 % sur `src/vues` à la dernière mesure | SPRINT-11 |
| Aucune alerte sur anomalie d'accès | Le journal d'audit enregistre, personne ne le lit en continu | SPRINT-09 |
| `style-src 'unsafe-inline'` conservé | Contrainte MapLibre ; impact borné, mais le point n'est pas refermé | SPRINT-13 |

**Reconductions.** Trois points ont figuré à l'identique dans « ce qui reste
ouvert » des SPRINT-14, 15 et 16 avant d'être soldés au SPRINT-17. La règle posée
en rétrospective du SPRINT-17 s'applique désormais : *une dette reconduite trois
fois n'est plus une dette, c'est une décision implicite de ne jamais la payer* — la
rétrospective compte les reconductions, elle ne se contente plus de les lister.
