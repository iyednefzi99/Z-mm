"""Détection d'anomalie EWMA — cœur métier du microservice IA (US-035).

Fonctions pures (sans I/O) : une ligne de base (moyenne EWMA) et une variance
EWMA incrémentale (formule de Finch) sont maintenues ; le z-score d'un point est
son écart à la ligne de base rapporté à l'écart-type. Au-delà du seuil, le point
est une anomalie. Miroir de l'implémentation Java `AnomalieService`, ici prêt à
évoluer vers des modèles plus riches (scikit-learn, acoustique, vision) sans
toucher au reste du système — c'est tout l'intérêt du découplage REST/JSON.
"""

from __future__ import annotations

from dataclasses import dataclass
from math import sqrt

ALPHA = 0.3       # poids de la dernière mesure dans la moyenne EWMA
SEUIL_Z = 3.0     # z-score au-delà duquel un point est une anomalie


@dataclass
class Anomalie:
    instant: str
    valeur: float
    z_score: float


@dataclass
class Resultat:
    alpha: float
    seuil_z: float
    baseline: float | None
    ecart_type: float | None
    nombre_points: int
    anomalies: list[Anomalie]


def _arrondi(x: float) -> float:
    return round(x, 3)


def detecter(points: list[dict], alpha: float = ALPHA, seuil_z: float = SEUIL_Z) -> Resultat:
    """Analyse une série [{"instant": str, "valeur": float}, ...] et renvoie les anomalies."""
    if not points:
        return Resultat(alpha, seuil_z, None, None, 0, [])

    moyenne = float(points[0]["valeur"])
    variance = 0.0
    anomalies: list[Anomalie] = []

    for p in points[1:]:
        x = float(p["valeur"])
        ecart = x - moyenne
        increment = alpha * ecart
        ecart_type = sqrt(variance)
        if ecart_type > 0:
            z = ecart / ecart_type
            if abs(z) > seuil_z:
                anomalies.append(Anomalie(str(p.get("instant", "")), x, _arrondi(z)))
        moyenne += increment
        variance = (1 - alpha) * (variance + ecart * increment)

    return Resultat(alpha, seuil_z, _arrondi(moyenne), _arrondi(sqrt(variance)),
                    len(points), anomalies)


def resultat_en_dict(r: Resultat) -> dict:
    """Sérialise un Resultat au format JSON attendu par le client."""
    return {
        "alpha": r.alpha,
        "seuilZ": r.seuil_z,
        "baseline": r.baseline,
        "ecartType": r.ecart_type,
        "nombrePoints": r.nombre_points,
        "anomalies": [
            {"instant": a.instant, "valeur": a.valeur, "zScore": a.z_score}
            for a in r.anomalies
        ],
    }
