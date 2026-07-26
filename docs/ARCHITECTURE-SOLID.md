# Architecture Zümm — principes SOLID et patrons de conception

> Ce document cartographie les principes et patrons **tels qu'ils sont réellement
> appliqués dans le code**, avec les fichiers à l'appui. Il signale aussi, sans les
> maquiller, les endroits où un principe est encore enfreint : une cartographie
> qui ne montrerait que les réussites n'aurait aucune valeur de revue.

---

## 1. Les cinq principes SOLID

### S — Responsabilité unique (*Single Responsibility*)

La chaîne `Controller → Service → Repository` répartit trois responsabilités qui
changent pour trois raisons différentes : la forme de l'API, la règle métier, la
persistance.

| Couche | Rôle | Exemple |
|---|---|---|
| `controller/` | traduire HTTP ↔ métier, et rien d'autre | `LotController` — 60 lignes, aucune règle |
| `service/` | la règle métier et la transaction | `LotConditionnementService` — la somme des parts, la consolidation par pays |
| `repository/` | l'accès aux données | `LotConditionnementRepository` |
| `web/dto/` | le contrat exposé, découplé des entités | `SiteReponse`, `LotReponse` |

Cas exemplaire du découpage : **la confidentialité des positions**. Elle aurait pu
vivre dans `SiteService` sous forme d'un `if (role == …)`. Elle est isolée dans
`securite/PolitiquePositions` — une classe, une raison de changer (la politique de
confidentialité), et un test qui ne monte aucun contexte Spring.

**Écart connu** : `TableauDeBordService` (202 lignes) porte trois tableaux de bord
distincts. Trois raisons de changer dans un seul fichier ; à scinder quand un
quatrième arrivera.

### O — Ouvert/fermé (*Open/Closed*)

`FournisseurMeteo` est une interface ; `OpenMeteoFournisseur` en est une
implémentation. Ajouter Météo-France n'oblige à modifier **ni** `MeteoService`,
**ni** ses tests : on ajoute une classe.

Même forme pour `PolitiquePositions` : durcir la règle (portée par affectation
d'agent plutôt que par rôle) se fait par une nouvelle implémentation, sans
toucher `SiteService`.

Les convertisseurs d'énumération (`EtatSanteConverter`, `RaisonVisiteConverter`…)
suivent le même esprit : ajouter une valeur métier n'impose pas de modifier le
mapping des autres.

### L — Substitution de Liskov

`OpenMeteoFournisseur` respecte le contrat de `FournisseurMeteo` **y compris dans
la panne** : il rend `Optional.empty()` au lieu de lever. C'est le point qui
compte — une implémentation qui lèverait une exception réseau obligerait chaque
appelant à la connaître, et la substitution ne serait plus vraie en pratique.

`EntiteTenant` est une superclasse mappée dont toutes les entités métier héritent
sans en affaiblir le contrat : `tenantId` n'est jamais posé par le code, toujours
par Hibernate.

**Écart assumé** : `Photo` et `LotComposition` n'héritent pas d'`EntiteTenant`.
C'est délibéré — ni l'une ni l'autre ne se modifie, donc aucune n'a de `maj_le` ;
les faire hériter les doterait d'un champ toujours nul. Une hiérarchie qu'on
n'étend pas de force.

### I — Ségrégation des interfaces

Les repositories exposent des méthodes nommées par leur intention métier
(`findByLot`, `countByRuche_IdAndDateRecolte`, `idsProches`) plutôt qu'un accès
générique. Un appelant ne dépend que de ce qu'il utilise.

`PolitiquePositions` expose trois méthodes, chacune pour un besoin réel :
masquer un DTO, masquer un couple de coordonnées (centroïde de grappe), savoir si
l'appelant a droit à l'exactitude (pour dégrader une distance).

### D — Inversion des dépendances

- `MeteoService` dépend de l'**interface** `FournisseurMeteo`, pas du client HTTP.
- `SiteService` dépend de `PolitiquePositions`, pas de `PolitiquePositionsSelonRole`.
- Injection **par constructeur**, jamais par champ : la dépendance est visible dans
  la signature, l'objet est valide dès sa construction, et le test peut substituer
  sans conteneur.

---

## 2. Patrons de conception présents

### Patrons structurels

| Patron | Où | Ce qu'il résout |
|---|---|---|
| **Adaptateur** | `ClientAnomalieIA` | Adapte le microservice Python (REST/JSON) au type `AnomalieReponse` du domaine. Changer de moteur de scoring ne remonte pas dans le métier. |
| **Façade** | `service/*Service` | Une porte d'entrée métier par agrégat, derrière laquelle repositories et règles restent internes. |
| **DTO** | `web/dto/` | Sépare la forme exposée de la forme persistée. C'est ce qui permet à `SiteReponse` d'être masqué sans que l'entité `Site` change. |
| **Décorateur** | `PolitiquePositionsSelonRole.masquer` | Enveloppe un DTO et en rend une version restreinte, sans que l'appelant sache laquelle il tient. |

### Patrons comportementaux

| Patron | Où | Ce qu'il résout |
|---|---|---|
| **Stratégie** | `FournisseurMeteo`, `PolitiquePositions` | Une famille d'algorithmes interchangeable à l'exécution. |
| **Patron de méthode** | `EntiteTenant` | Fige le squelette commun (identité, tenant, horodatages) ; les sous-classes n'apportent que leur spécificité. |
| **Chaîne de responsabilité** | `TenantFilter` → `FiltreIdempotence` → contrôleurs | Chaque filtre traite ce qui le concerne et passe la main. L'ordre est une décision d'architecture : l'idempotence a besoin d'un tenant déjà posé, parce qu'elle écrit dans une table sous RLS. |
| **Observateur** | `surSession`, `surFile` (front) | Les vues s'abonnent à la session et à la file hors-ligne sans que ces modules les connaissent. |

### Patrons de création

| Patron | Où |
|---|---|
| **Fabrique statique** | `SiteReponse.de(site)`, `LotReponse.de(lot)`, `RessourceIntrouvable.de(type, id)` |
| **Fabrique de fonctions** | `ressource<E, C>(base)` côté front : produit les six opérations CRUD d'une ressource, une seule fois pour huit ressources |
| **Singleton (par le conteneur)** | tous les `@Service`/`@Component` — porté par Spring, jamais écrit à la main |

### Patrons d'architecture et de données

| Patron | Où |
|---|---|
| **Repository** | `repository/*Repository` (Spring Data JPA) |
| **Migration versionnée** | `db/migration/V1..V15` — Flyway seul propriétaire du schéma, `ddl-auto: none` |
| **Multi-tenant à discriminant + RLS** | `@TenantId` + politiques PostgreSQL ([ADR-001](../roadmap/operationnel/06_decisions/ADR-001-multi-tenant.md)) |
| **Idempotence par clé de requête** | `FiltreIdempotence` + `MagasinIdempotence` + table `requete_idempotente` |
| **Circuit de repli (*fallback*)** | `MeteoService` (simulation), `AnomalieService` (EWMA local si le microservice IA est absent) |

---

## 3. Ce qui n'est pas encore propre

Une revue honnête liste aussi les dettes.

1. **`TableauDeBordService` fait trop.** Trois tableaux de bord, trois raisons de
   changer. À scinder par tableau.
2. **Les agrégats sont calculés en Java.** `SyntheseService` et
   `TableauDeBordService` itèrent en mémoire sur des `findAll()` là où une
   agrégation SQL (`time_bucket`) ferait le travail en base. Fonctionne, ne passe
   pas à l'échelle.
3. **Pas d'autorisation horizontale.** La RLS isole les *tenants*, pas les agents
   entre eux : un apiculteur voit toutes les ruches de l'exploitation, y compris
   celles qui ne lui sont pas affectées. C'est l'US-053.
4. **Le client d'API front est écrit à la main** alors que le contrat OpenAPI
   existe. Le fichier le dit lui-même en en-tête ; la parité des types n'est
   garantie par rien d'autre que l'attention.
5. **`console.ts` : 944 lignes de traductions en dur.** À externaliser en
   ressources par locale, sans perdre le contrôle de parité qui casse aujourd'hui
   la compilation quand une clé manque.
6. **Pas de couche anticorruption vers le microservice IA.** `ClientAnomalieIA`
   adapte, mais le format de la série est celui du domaine Zümm : un changement de
   contrat côté Python se verrait à la compilation, pas à la conception.

---

## 4. Règles à tenir pour les prochains sprints

- Une règle métier ne vit **jamais** dans un contrôleur.
- Toute dépendance externe (HTTP, fichier, horloge) passe par une **interface** du
  paquet `service`, pour rester substituable en test.
- Injection **par constructeur** uniquement.
- Un nouvel agrégat = une entité + un repository + un service + des DTO. Pas
  d'entité exposée directement par l'API.
- Toute nouvelle table métier porte `tenant_id`, sa politique RLS et une clé
  étrangère **composite** `(id, tenant_id)` — c'est ce qui empêche un
  rattachement inter-tenant même quand la vérification de clé contourne la RLS.
