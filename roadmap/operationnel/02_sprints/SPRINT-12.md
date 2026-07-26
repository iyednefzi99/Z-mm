# 🏃 SPRINT-12 : Durcissement de la sécurité

**Thème :** Fermer les écarts entre la sécurité documentée et la sécurité réelle
**Objectif :** Qu'aucune garantie annoncée au cahier ne repose sur une configuration absente, un contrôle manquant ou une règle jamais appliquée
**Période :** 2027-01-19 → 2027-02-01 (14 jours)
**Story Points :** 34 / Capacity : 40

> **Origine du sprint.** Une revue d'architecture menée sur la branche `main` a
> relevé six écarts entre ce que la documentation garantissait et ce que le code
> faisait. Aucun n'était visible en test : tous se manifestaient à l'exécution, en
> configuration de production.

---

## 📋 User Stories

| ID | Story | Points | Statut |
|:---|:---|:---:|:---|
| US-058 | Claim `tenant_id` garanti et refus explicite | 5 | 🟢 Livré |
| US-059 | Validation d'audience du jeton | 3 | 🟢 Livré |
| US-060 | Confidentialité des positions de ruchers | 8 | 🟢 Livré |
| US-061 | RBAC en refus par défaut et identité machine | 8 | 🟢 Livré |
| US-062 | En-têtes de sécurité du proxy inverse | 5 | 🟢 Livré |
| US-063 | Portes de sécurité bloquantes dans la CI | 5 | 🟢 Livré |

---

## 🔎 Défauts corrigés

### 1. Le realm de production n'avait pas le mapper `tenant_id`

`infra/keycloak/realm-zumm.dev.json` déclarait le mapper ; `realm-zumm.json` —
celui que monte `docker-compose.yml` — ne l'avait pas. Les jetons émis n'avaient
donc **aucun** `tenant_id`, la variable de session RLS restait nulle, et **toutes
les API métier renvoyaient zéro ligne**. L'application paraissait fonctionner sur
une base vide.

Corrigé des deux côtés : le mapper est ajouté, et `TenantFilter` répond **403**
au lieu de laisser passer. Un défaut de configuration doit échouer franchement, pas
se déguiser en résultat vide.

### 2. Aucune validation d'audience

`application.yml` ne validait que l'émetteur. Keycloak émet des jetons pour tous
les clients d'un même royaume, dont `account`, livré par défaut : un jeton obtenu
par une application tierce du royaume avait le bon émetteur, une signature valide,
et était **accepté**. `ValidateurAudience` ferme cela (5 tests).

### 3. L'arrondi des positions n'était jamais appliqué

`arrondi_degres_public` existait dans `ConfigZumm.ini`, était lu par
`SeuilsMetier`, documenté dans la migration V2 comme protection anti-vol… et
**jamais utilisé**. `SiteReponse` rendait la position au mètre près à tout porteur
de jeton. Le vol de ruches est le premier sinistre du métier.

Corrigé par `securite/PolitiquePositions` : arrondi selon le rôle, altitude
masquée, distances des voisins dégradées à 100 m (une distance précise depuis un
site connu se trilatère).

### 4. Le RBAC était troué par construction

`anyRequest().authenticated()` laissait passer tout porteur de jeton valide sur
tout ce qui n'était pas explicitement listé — ingestion de mesures, récoltes,
suppression de reines, photos. Remplacé par `hasAnyRole(...)`, et un client machine
`zumm-capteur` (client_credentials, rôle `capteur`) limité au seul dépôt de mesures.

### 5. Aucune CSP

Le proxy posait HSTS, `nosniff`, `X-Frame-Options` et `Referrer-Policy`, mais pas
de `Content-Security-Policy`. Avec les jetons dans `localStorage`, c'était la
double peine. Ajoutée, avec `Permissions-Policy`, COOP/CORP, une zone de débit
dédiée à l'authentification et le blocage de la console d'administration Keycloak.

### 6. Les portes de sécurité de la CI ne bloquaient rien

OWASP Dependency-Check tournait en `continue-on-error`. Rendu bloquant (CVSS 7),
avec un fichier de suppressions tracé. Ajout de **Trivy** (configuration
d'infrastructure) et d'un workflow **CodeQL** — jusqu'ici, rien ne lisait le code
de Zümm lui-même.

---

## ✅ Definition of Done

- [x] `./mvnw verify` : 55 unitaires + 92 d'intégration, **Skipped : 0**
- [x] Chaque correctif porte un test de régression
- [x] Aucun secret introduit ; gitleaks vert
- [x] Décisions non triviales consignées ([ADR-006](../06_decisions/ADR-006-stockage-des-jetons.md))

## 🔁 Rétrospective

**Ce qui a marché.** Les écarts se sont tous révélés en lisant le code contre la
documentation. Aucun n'aurait été trouvé en ajoutant des tests aux comportements
déjà testés.

**Ce qu'on retient.** Un réglage de sécurité qui n'a pas de test échouant quand on
le retire n'est pas une garantie, c'est une intention. Les six correctifs ont
chacun leur test.

**Ce qui reste ouvert.** Le stockage des jetons dans `localStorage`
([ADR-006](../06_decisions/ADR-006-stockage-des-jetons.md)) : la CSP réduit la
probabilité d'une XSS, elle n'en réduit pas l'impact. Le pattern BFF reste la
cible avant toute exploitation réelle.
