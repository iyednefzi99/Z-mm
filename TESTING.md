# Tester le site Zümm en local avec des données réelles

Ce guide monte la pile complète, la **peuple d'un jeu de données de démonstration**
et fournit des **comptes de test** pour parcourir la console PWA comme un
utilisateur réel.

> ⚠️ **Développement / démonstration uniquement.** Comptes à mots de passe en clair
> (`test`), données factices, validation d'émetteur OIDC assouplie. Rien de tout
> ceci ne doit atteindre la production.

## Prérequis

- Docker Engine, Node 20+, un `.env` à la racine (copié depuis `.env.example`).
- L'image PostgreSQL de test (PostGIS + TimescaleDB) :
  ```bash
  docker build -f infra/test-postgres.Dockerfile -t zumm/test-postgres:16 infra/
  ```

## 1. Lancer la pile (realm de dev + comptes de test)

La surcouche `docker-compose.dev.yml` importe le realm **de développement**
(`realm-zumm.dev.json`, quatre comptes de test) et publie l'API sur `:8080` :

```bash
docker compose --env-file .env \
  -f infra/docker-compose.yml -f infra/docker-compose.dev.yml up -d --build
```

## 2. Charger les données de démonstration

```bash
bash infra/seed-demo.sh
```

Crée, sous le tenant **`exploitation-demo`** : 1 fermier, 2 fermes, 4 agents
(un par rôle), 3 ruchers géolocalisés (Béja, Nabeul, Kairouan), 6 ruches et leur
composition, plannings, 4 visites, séries de mesures (dont une **pointe d'anomalie**
et un **poids sous le seuil** → alerte), tâches (dont un rappel échu), 2 récoltes
avec lots/QR, et un suivi de reine. Le script est **idempotent** (re-jouable).

## 3. Lancer la console (front)

```bash
cd frontend && npm ci
# OIDC vers le Keycloak local :
VITE_OIDC_ISSUER=http://localhost:8081/realms/zumm \
VITE_OIDC_CLIENT=zumm-frontend \
VITE_OIDC_REDIRECT=http://localhost:5173 \
npm run dev
```

Ouvrir **http://localhost:5173**.

## 4. Se connecter (comptes de test — mots de passe en clair)

| Identifiant        | Mot de passe | Rôle         | Peut faire                                   |
|--------------------|--------------|--------------|----------------------------------------------|
| `admin-test`       | `test`       | admin        | tout                                         |
| `responsable-test` | `test`       | responsable  | référentiel (fermiers, fermes, sites, ruches…) |
| `superviseur-test` | `test`       | superviseur  | approuver / refuser les plannings            |
| `apiculteur-test`  | `test`       | apiculteur   | visites, mesures, tâches, récoltes, reines   |

Tous portent le claim `tenant_id = exploitation-demo` : ils voient donc les données
du seed.

### Alternative rapide — coller un jeton (sans variables OIDC)

Le client `zumm-frontend` autorise le *direct grant* en dev. Récupérer un jeton et
le coller dans l'écran de connexion (« coller un jeton ») :

```bash
curl -s http://localhost:8081/realms/zumm/protocol/openid-connect/token \
  -d grant_type=password -d client_id=zumm-frontend \
  -d username=admin-test -d password=test \
  | python -c "import sys,json;print(json.load(sys.stdin)['access_token'])"
```

## 5. À explorer

- **Tableaux de bord** : calendrier des visites, production (ruche `Langstroth` de
  Béja **sous le seuil**), alertes sanitaires, synthèse & ROI.
- **Capteurs** : alerte de seuil ouverte, et **détection d'anomalie** sur le poids de
  cette même ruche (pointe à 55 kg) — bouton *Analyser*.
- **Récoltes** : deux lots, **QR de traçabilité** cliquable.
- **Carte** : les trois ruchers et leurs **rayons de butinage** 1/2/3 km.
- **Reines** : historique de la reine (introduction, ponte).

### SPRINT-09 (fonctionnalités ajoutées)

- **Tableaux de bord → Prévisions** : tendance du poids par ruche (régression
  linéaire), gain/jour et **projection à 7 jours** (US-042).
- **Visites → colonne Rapport** : bouton ⬇ **PDF** qui télécharge le rapport de
  visite mis en forme (US-044).
- **Audit** (onglet, visible pour `responsable-test` / `admin-test`) : journal
  « qui a fait quoi, quand » alimenté par chaque création / modification /
  suppression (US-043). Refusé aux autres profils (403).
- **Alertes par e-mail** (US-041) : à l'ouverture d'une alerte de seuil, l'agent
  responsable est notifié — désactivé par défaut ; activer via
  `ZUMM_NOTIF_EMAIL_ENABLED=true` et un SMTP (`spring.mail.*`). Les agents du seed
  ont une adresse (`amine@domaine-oliviers.tn`, …).

## Remarques

- **Auth = Keycloak / JWT** : le back-end ne stocke aucun mot de passe (serveur de
  ressources). Les mots de passe « en clair » sont ceux des comptes de test du realm
  de dev — pratiques pour tester, sans valeur.
- **Base Keycloak dédiée** : depuis le SPRINT-09, Keycloak utilise une base
  `keycloak` distincte de `zumm` (créée à l'initialisation du volume par
  `infra/postgres-init/`), pour que le schéma `public` reste la propriété exclusive
  de Flyway. **Conséquence** : sur un volume déjà existant (créé avant ce
  changement), relancer `docker compose … up` échouerait (base `keycloak`
  manquante) — repartir d'un volume vierge : `docker compose … down -v` puis `up`.
- En dev, l'émetteur du jeton (`localhost:8081`) diffère de l'URL interne du
  back-end (`keycloak:8080`) : la surcouche valide donc la **signature** via le JWKS
  interne sans épingler la chaîne `iss` (cf. `docker-compose.dev.yml`).
- Microservice IA optionnel (US-035) : `docker build -t zumm/ia-service ia-service`
  puis brancher `zumm.ia.url=http://…` ; sinon la détection reste locale (EWMA).
