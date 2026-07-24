"""Microservice IA Zümm (US-035) — API REST/JSON découplée, bibliothèque standard.

Endpoints :
  GET  /health            → {"status": "ok"}
  POST /score             → détection d'anomalie EWMA sur une série

Corps attendu par /score :
  {"series": [{"instant": "2026-06-01T10:00:00Z", "valeur": 30.0}, ...]}

Réponse :
  {"alpha": .., "seuilZ": .., "baseline": .., "ecartType": ..,
   "nombrePoints": N, "anomalies": [{"instant": .., "valeur": .., "zScore": ..}]}

Aucune dépendance externe : se lance avec `python app.py` (port 8000 par défaut,
surchargeable via la variable d'environnement PORT). Le découplage REST/JSON permet
de remplacer le moteur (scikit-learn, PyTorch…) sans impacter le back-end Java.
"""

from __future__ import annotations

import json
import os
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from scoring import detecter, resultat_en_dict


class Gestionnaire(BaseHTTPRequestHandler):
    server_version = "ZummIA/1.0"

    def _repondre(self, code: int, corps: dict) -> None:
        charge = json.dumps(corps).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(charge)))
        self.end_headers()
        self.wfile.write(charge)

    def do_GET(self) -> None:  # noqa: N802 (nom imposé par BaseHTTPRequestHandler)
        if self.path == "/health":
            self._repondre(200, {"status": "ok"})
        else:
            self._repondre(404, {"error": "not found"})

    def do_POST(self) -> None:  # noqa: N802
        if self.path != "/score":
            self._repondre(404, {"error": "not found"})
            return
        try:
            longueur = int(self.headers.get("Content-Length", 0))
            corps = json.loads(self.rfile.read(longueur) or b"{}")
            series = corps.get("series", [])
            if not isinstance(series, list):
                raise ValueError("`series` doit être une liste")
            self._repondre(200, resultat_en_dict(detecter(series)))
        except (ValueError, json.JSONDecodeError) as e:
            self._repondre(400, {"error": str(e)})

    def log_message(self, *args) -> None:  # silencieux (pas de bruit dans les logs de test)
        return


def main() -> None:
    port = int(os.environ.get("PORT", "8000"))
    serveur = ThreadingHTTPServer(("0.0.0.0", port), Gestionnaire)
    print(f"Microservice IA Zümm à l'écoute sur :{port}")
    try:
        serveur.serve_forever()
    except KeyboardInterrupt:
        serveur.shutdown()


if __name__ == "__main__":
    main()
