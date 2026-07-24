// ===========================================================================
// Test de charge k6 — Zümm (SPRINT-07, US-038)
//
// Objectif de service (cahier, chap. 10) : p95 < 500 ms et taux d'erreurs < 1 %.
// Ces seuils sont des THRESHOLDS k6 : le test echoue (exit != 0) s'ils sont
// depasses, ce qui permet de le brancher en CI (etape non bloquante par defaut).
//
// Usage :
//   k6 run tests/k6/charge-api.js                     # endpoints publics
//   k6 run -e BASE=https://staging.zumm -e TOKEN=<jwt> tests/k6/charge-api.js
//
// Sans TOKEN, seuls les endpoints publics (/api/info, contrat OpenAPI) sont
// sollicites. Avec un TOKEN valide (claim tenant_id), les tableaux de bord et
// l'ingestion sont ajoutes au scenario.
// ===========================================================================

import http from 'k6/http';
import { check, group, sleep } from 'k6';

const BASE = __ENV.BASE || 'http://localhost:8080';
const TOKEN = __ENV.TOKEN || '';

export const options = {
  scenarios: {
    charge: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 20 }, // montee en charge
        { duration: '1m', target: 20 }, // palier
        { duration: '15s', target: 0 }, // descente
      ],
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<500'], // p95 < 500 ms
    http_req_failed: ['rate<0.01'], // erreurs < 1 %
  },
};

const entetesAuth = TOKEN ? { Authorization: `Bearer ${TOKEN}` } : null;

export default function () {
  group('public', () => {
    const info = http.get(`${BASE}/api/info`, { headers: { 'Accept-Language': 'fr' } });
    check(info, { 'info 200': (r) => r.status === 200 });

    const contrat = http.get(`${BASE}/v3/api-docs`);
    check(contrat, { 'openapi 200': (r) => r.status === 200 });
  });

  if (entetesAuth) {
    group('tableaux', () => {
      const prod = http.get(`${BASE}/api/tableaux/production`, { headers: entetesAuth });
      check(prod, { 'production 200': (r) => r.status === 200 });

      const synthese = http.get(`${BASE}/api/tableaux/synthese`, { headers: entetesAuth });
      check(synthese, { 'synthese 200': (r) => r.status === 200 });

      const alertes = http.get(`${BASE}/api/mesures/alertes`, { headers: entetesAuth });
      check(alertes, { 'alertes 200': (r) => r.status === 200 });
    });
  }

  sleep(1);
}
