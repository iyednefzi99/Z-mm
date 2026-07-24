# Microservice IA Zümm (US-035)

Service de **détection d'anomalie** découplé du back-end Java, exposé en **REST/JSON**.
Il isole la partie « intelligence » du système : le moteur (aujourd'hui EWMA en
bibliothèque standard) peut évoluer vers scikit-learn / PyTorch (acoustique du
varroa, comptage par vision) sans impacter le reste de l'application.

## Endpoints

| Méthode | Chemin    | Description                                  |
|---------|-----------|----------------------------------------------|
| `GET`   | `/health` | Sonde de vie → `{"status": "ok"}`            |
| `POST`  | `/score`  | Détection d'anomalie EWMA sur une série      |

### `POST /score`

Requête :

```json
{ "series": [
  { "instant": "2026-06-01T10:00:00Z", "valeur": 30.0 },
  { "instant": "2026-06-01T11:00:00Z", "valeur": 50.0 }
] }
```

Réponse :

```json
{ "alpha": 0.3, "seuilZ": 3.0, "baseline": 30.6, "ecartType": 0.2,
  "nombrePoints": 2,
  "anomalies": [ { "instant": "2026-06-01T11:00:00Z", "valeur": 50.0, "zScore": 90.1 } ] }
```

## Lancer

```bash
# Directement (aucune dépendance à installer)
cd ia-service && python app.py            # écoute sur :8000 (variable PORT)

# Conteneur
docker build -t zumm/ia-service ia-service
docker run -p 8000:8000 zumm/ia-service
```

## Tests

```bash
cd ia-service && python -m unittest
```

## Intégration back-end

Le back-end Java l'appelle via `ClientAnomalieIA`, activé par la propriété
`zumm.ia.url` (ex. `http://ia-service:8000`). **Sans cette propriété**, le back-end
retombe sur sa détection EWMA locale (`AnomalieService`) : le couplage est donc
optionnel et non bloquant, comme pour le contexte météo.
