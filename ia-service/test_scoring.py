"""Tests du cœur de scoring (US-035, US-036). Exécution : `python -m unittest`."""

import unittest

from scoring import detecter, resultat_en_dict


class TestScoring(unittest.TestCase):

    def _serie(self, *valeurs):
        return [{"instant": f"2026-06-01T{h:02d}:00:00Z", "valeur": v} for h, v in enumerate(valeurs)]

    def test_repere_une_pointe(self):
        r = detecter(self._serie(30.0, 30.2, 29.9, 30.1, 29.8, 30.3, 29.9, 50.0))
        self.assertEqual(r.nombre_points, 8)
        self.assertIsNotNone(r.baseline)
        self.assertTrue(any(a.valeur == 50.0 for a in r.anomalies))

    def test_serie_stable_sans_anomalie(self):
        r = detecter(self._serie(30.0, 30.1, 29.9, 30.0, 30.1, 29.9))
        self.assertEqual(r.anomalies, [])

    def test_serie_vide(self):
        r = detecter([])
        self.assertEqual(r.nombre_points, 0)
        self.assertIsNone(r.baseline)
        self.assertEqual(r.anomalies, [])

    def test_serialisation_json(self):
        d = resultat_en_dict(detecter(self._serie(30.0, 50.0, 30.0)))
        self.assertIn("anomalies", d)
        self.assertIn("baseline", d)
        self.assertEqual(d["seuilZ"], 3.0)


if __name__ == "__main__":
    unittest.main()
