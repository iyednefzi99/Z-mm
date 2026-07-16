# 🛠️ Guide de Modification du Roadmap

Ce document explique comment modifier chaque composant du roadmap de manière cohérente.

---

## 1. Modifier le Product Backlog

### Fichier: `01_product_backlog/product_backlog.json`

**Structure:**
```json
{
  "epics": [
    {
      "id": "EPIC-XXX",
      "titre": "Nom de l'epic",
      "description": "Description",
      "priorite": "Haute|Moyenne|Basse",
      "source_cdc": "§X.Y",
      "stories": [
        {
          "id": "US-XXX",
          "titre": "Nom de la story",
          "points": 5,
          "priorite": "Haute",
          "critere_acceptation": "Critères clairs"
        }
      ]
    }
  ]
}
```

**Règles:**
- Les IDs doivent être uniques (US-001, US-002, ...)
- Story points: suite de Fibonacci (1, 2, 3, 5, 8, 13, 21)
- Toujours référencer le CdC source

**Après modification:**
```bash
# Regénérer le Markdown
python scripts/generate_md.py
```

---

## 2. Modifier les Sprints

### Fichier: `02_sprints/SPRINT-XX.md`

**Sections modifiables:**
- User Stories (ajouter/supprimer des lignes)
- Burndown (mettre à jour quotidiennement)
- Rétrospective (en fin de sprint)

**Règles:**
- La somme des points ne doit pas dépasser la velocity (38)
- Les dates sont calculées automatiquement

---

## 3. Modifier la Pipeline DevOps

### Fichier: `03_devops_pipeline/github-actions.yml`

**Sections modifiables:**
- Variables d'environnement (`env:`)
- Seuils de qualité (coverage, latence)
- Stages (ajouter/supprimer des jobs)

**Règles:**
- Toujours tester sur une branche feature avant merge
- Les secrets (tokens, passwords) vont dans GitHub Secrets

---

## 4. Modifier les Releases

### Fichier: `04_releases/releases.json`

**Règles SemVer:**
- **MAJOR:** Changement incompatible (API cassée)
- **MINOR:** Nouvelle fonctionnalité (compatible)
- **PATCH:** Correction de bug

---

## 5. Bonnes Pratiques

1. **Versionner tout:** Commit après chaque modification majeure
2. **Documenter:** Mettre à jour le README si structure change
3. **Valider:** Vérifier JSON avec un linter
4. **Synchroniser:** Backlog ↔ Sprints ↔ Releases doivent être cohérents

---

## 6. Checklist de Validation

Avant de considérer une modification terminée:

- [ ] JSON valide (pas de syntaxe erronée)
- [ ] IDs uniques vérifiés
- [ ] Story points cohérents avec la velocity
- [ ] Références CdC à jour
- [ ] Markdown regénéré si nécessaire
- [ ] README mis à jour

---

*Pour toute question, consulter le Product Owner ou le Scrum Master.*
