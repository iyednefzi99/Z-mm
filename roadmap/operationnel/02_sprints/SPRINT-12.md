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

OWASP Dependency-Check tournait en `continue-on-error`. Ajout de **Trivy**
(configuration d'infrastructure) et d'un workflow **CodeQL** — jusqu'ici, rien ne
lisait le code de Zümm lui-même.

**Ce que la première exécution a révélé — et qui vaut plus que le sprint.**

Rendre Dependency-Check bloquant sans clé NVD était une erreur : le NIST limite
les adresses partagées des runners GitHub, et la mise à jour échouait en
85 secondes, pour une cause étrangère au code. Le remplacer par OSV-Scanner lisant
`pom.xml` a reproduit le même travers sous une autre forme — OSV reconstituait
alors l'arbre en interrogeant Maven Central des dizaines de fois, et se faisait
limiter en 429.

La méthode qui tient : **ne pas faire résoudre l'arbre par le scanner**. Maven l'a
déjà fait. Un SBOM CycloneDX est produit à partir des artefacts réellement
résolus, et OSV le lit — verdict en quelques millisecondes, sans dépendre d'un
registre tiers.

Le garde ainsi réparé a immédiatement trouvé ce qu'aucune des mesures de ce sprint
n'aurait vu : **29 vulnérabilités dans l'arbre de dépendances, dont trois de score
9.1 à 9.6.** Spring Boot était figé en 3.4.1, publié en décembre 2024, soit
dix-huit mois de correctifs manquants — `tomcat-embed-core` 9.6,
`spring-security-core` et `spring-security-web` 9.1, `postgresql` 8.2.

Autrement dit : ce sprint verrouillait la configuration pendant que les
bibliothèques sous-jacentes portaient des failles critiques. La leçon n'est pas
que les mesures de configuration étaient inutiles, mais qu'aucune ne compense une
dépendance non tenue à jour — et que l'absence de garde opérant l'avait laissé
invisible.

Correction : montée en Spring Boot **3.5.16**, plus trois versions relevées
au-dessus du BOM (`jackson-databind`, `commons-lang3`, `postgresql`) pour les
vulnérabilités qu'il ne couvre pas encore. Résultat mesuré : **29 → 0**, sans une
seule régression sur les 147 tests.

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
