# 🏃 SPRINT-16 : Sessions sans jeton et portée par affectation

**Thème :** Fermer les deux derniers écarts du modèle d'autorisation
**Objectif :** Que le navigateur ne détienne plus de jeton, et qu'un agent ne puisse plus énumérer le parc entier de son exploitation
**Période :** 2027-03-16 → 2027-03-29 (14 jours)
**Story Points :** 34 / Capacity : 40

---

## 📋 User Stories

| ID | Story | Points | Statut |
|:---|:---|:---:|:---|
| US-073 | BFF : jetons détenus par le serveur (ADR-006) | 21 | 🟢 Livré |
| US-057 | Portée d'autorisation par affectation d'agent | 13 | 🟢 Livré |

---

## 🔎 US-057 — la RLS isolait les exploitations, pas les agents

Une exploitation ne voyait pas les données d'une autre (ADR-001). Mais **à
l'intérieur** d'une exploitation, tout le monde voyait tout : un saisonnier, un
stagiaire ou un compte compromis pouvait énumérer l'intégralité du parc — toutes
les ruches, tous les sites, donc **la carte des ruchers**.

C'est l'écart signalé depuis le SPRINT-12 dans `PolitiquePositions` : arrondir les
coordonnées limite ce qu'un profil non propriétaire lit d'un site, mais ne
l'empêche pas de tous les lister.

### Où la règle est posée

**Dans le SGBD**, comme l'isolation de tenant. La poser dans les services
reviendrait à ajouter un `WHERE` à chaque requête et à espérer que personne ne
l'oublie — précisément le mode de défaillance que la RLS a été introduite pour
rendre impossible.

Deux variables de session, posées par l'application avec le tenant et **dans la
même requête préparée** : les dissocier ouvrirait une fenêtre pendant laquelle le
tenant serait posé et la portée non, donc pendant laquelle une requête verrait
toute l'exploitation.

| Profil | Portée |
|---|---|
| responsable, admin | toute l'exploitation — c'est leur fonction |
| capteur | globale, mais le RBAC le borne au seul dépôt de mesures |
| apiculteur, superviseur | leurs ruches, et ce qui s'y rattache |

**L'absence de portée vaut « rien voir », jamais « tout voir ».** C'est le mode de
défaillance qui compte : une tâche planifiée, une connexion du pool reprise hors
contexte ou un test mal isolé ne doivent pas ouvrir le parc.

### Le lien entre le compte et l'agent

Une colonne `sujet_oidc` porte le claim `sub` — identifiant stable chez le
fournisseur. Le courriel ne convient pas comme clé : il change, et un changement
silencieux romprait l'affectation sans que rien ne le signale. La liaison se fait
**à la première connexion**, par le courriel, puis le sujet fait foi. Aucune
reprise de données n'est donc nécessaire.

### Ce qui n'est pas filtré, et pourquoi

`fermier` et `ferme` restent visibles — un agent doit savoir pour qui il travaille,
et ces tables ne portent aucune position. `recolte`, `reine`, `photo` sont
rattachées à une ruche déjà filtrée. Ces choix sont à rejuger si l'une de ces
tables se met à porter une donnée de localisation.

---

## ✅ Definition of Done

- [x] `./mvnw verify` : 55 unitaires + **109** d'intégration, **Skipped : 0**
- [x] Restriction **prouvée sous le rôle applicatif réel** — en test, l'application
      se connecte avec le propriétaire, qui contourne la RLS : un test passant par
      l'API n'aurait rien prouvé
- [x] Le fait que l'application **pose** les variables est testé séparément :
      des politiques justes mais non alimentées resteraient lettre morte

## 🔁 Rétrospective

**Ce qui a marché.** Se demander « ce test prouverait-il quelque chose si je
retirais la protection ? ». La réponse était non pour la première version, qui
passait par l'API : l'application se connecte en test avec le propriétaire de la
base, lequel ignore la RLS. Le test a été refait sous `zumm_app`.

**Ce qu'on retient.** Un test vert ne dit rien tant qu'on n'a pas vérifié qu'il
peut rougir.

**Ce qui reste ouvert.** Agrégats des tableaux de bord calculés en Java ; client
d'API front toujours écrit à la main alors que le contrat OpenAPI existe ;
`TableauDeBordService` porte trois tableaux ; une session serveur suppose une
affinité ou un stockage partagé le jour d'une réplication horizontale.
