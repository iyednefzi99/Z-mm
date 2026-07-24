# Tests de charge k6 — Zümm (US-038)

Scénario de charge validant les objectifs de service du cahier des charges
(chap. 10) : **p95 < 500 ms** et **taux d'erreurs < 1 %**, exprimés comme
*thresholds* k6 (le test sort en échec si l'un est dépassé).

## Prérequis

- [k6](https://k6.io/docs/get-started/installation/) installé (`k6 version`).
- La pile Zümm démarrée (cf. racine : `docker compose --env-file .env -f infra/docker-compose.yml up -d`).

## Exécution

```bash
# Endpoints publics uniquement (santé, /api/info, contrat OpenAPI)
k6 run tests/k6/charge-api.js

# Avec authentification : ajoute les tableaux de bord et les alertes
k6 run -e BASE=https://staging.zumm -e TOKEN="<jwt-avec-claim-tenant_id>" tests/k6/charge-api.js
```

## Seuils (thresholds)

| Métrique             | Seuil        | Signification                     |
|----------------------|--------------|-----------------------------------|
| `http_req_duration`  | `p(95)<500`  | 95 % des requêtes sous 500 ms     |
| `http_req_failed`    | `rate<0.01`  | moins de 1 % d'erreurs            |

## Intégration continue

Le test peut être branché en CI comme étape **non bloquante** (informative) sur
l'environnement de staging : un dépassement de seuil signale une régression de
performance sans casser le pipeline applicatif.
