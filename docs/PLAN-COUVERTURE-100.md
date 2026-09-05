# Plan de couverture intégrale du backend

> État mesuré le 05/09/2026, après la migration `V31`, sur les campagnes
> **fusionnées** (unitaire + intégration) :
> **82,1 %** d'instructions (30 651 / 37 325) · **63,0 %** de branches
> (1 288 / 2 046) · **83,9 %** de lignes (6 310 / 7 518).
>
> Il manquait donc **6 674 instructions** et **758 branches**. Ce document dit
> où elles sont, dans quel ordre les fermer, et ce qui ne se fermera jamais.
>
> **Lots 1, 2 et vagues A-B du lot 3, livrés le 05/09/2026** : **89,9 %**
> d'instructions, **75,9 %** de branches, **90,2 %** de lignes, sur **418** tests
> unitaires et 242 tests d'intégration — contre 82,1 %, 63,0 % et 83,9 % au
> départ. Les planchers du `pom.xml` sont relevés d'autant à chaque fois.
>
> Source : `backend/target/site/jacoco/jacoco.csv`, produit par `./mvnw -B verify`.
> Aucun chiffre de ce document n'est recopié d'un autre.

---

## 0. Ce que « 100 % » veut dire, et ce qu'il ne veut pas

Ce plan vise 100 %. Il faut dire tout de suite ce que cette cible fait de bien et
ce qu'elle fait de mal, sinon elle se retourne contre le dépôt.

**Ce qu'elle fait de bien.** Les 6 674 instructions manquantes ne sont pas
réparties uniformément : elles se concentrent dans une poignée de classes que
personne n'a jamais exécutées en test. `IdentiteService` est couvert à **6,3 %** —
c'est la classe qui mène le flux OIDC, crée les comptes et traduit les refus de
Keycloak. `OpenMeteoFournisseur` à **14,8 %**. `RegleMeteoDefavorable` à
**14,8 %**. Ce ne sont pas des recoins : ce sont des chemins que la production
emprunte tous les jours et qu'aucun test n'a jamais parcourus. Viser 100 % force à
les regarder.

**Ce qu'elle fait de mal.** Passée une certaine barre, la seule façon d'avancer
est d'écrire des tests qui *exécutent* du code sans rien *vérifier*. Un test qui
appelle un `toString()` pour verdir une ligne ne prouve rien et coûte une
maintenance. Le dépôt a déjà écrit cette phrase à propos d'autre chose, et elle
vaut ici mot pour mot :

> Une route couverte par un test qui ne la met jamais dans l'état intéressant est
> une route non couverte.

Le corollaire est que **le pourcentage n'est pas la cible ; c'est le révélateur**.
Chacun des sept lots ci-dessous est donc défini par *ce qu'il fait vérifier*, et
le gain de couverture n'en est que la conséquence chiffrée.

**Et une règle qui ne bouge pas** : les planchers JaCoCo montent avec l'acquis,
ils ne descendent jamais. Chaque lot livré les relève. C'est ce qui distingue un
plan d'une intention.

> ⚠️ **Correction apportée à la livraison du lot 1.** Ce paragraphe disait d'abord
> « au chiffre atteint, arrondi au dixième inférieur ». C'était plus strict que
> la convention du dépôt, et à tort : un plancher calé au dixième près fait
> échouer le build sur une variation qui n'apprend rien, et la première réponse
> serait de le baisser — exactement ce qu'il sert à empêcher. Le `pom.xml` le
> pose **sous la mesure** depuis le 26/07/2026 (82,5 % mesurés, plancher à
> 0,80), et le plan s'aligne dessus.

| | Départ | Lot 1 | Lot 2 | Lot 3 A | Lot 3 B | Plancher |
|---|--:|--:|--:|--:|--:|--:|
| Instructions | 82,1 % | 84,4 % | 88,1 % | 89,0 % | **89,9 %** | 0,80 → … → **0,89** |
| Branches | 63,0 % | 66,0 % | 70,8 % | 74,7 % | **75,9 %** | 0,60 → … → **0,75** |
| Lignes | 83,9 % | 86,3 % | 88,9 % | 89,6 % | **90,2 %** | *aucun* |

**Les branches ont gagné 12,9 points en quatre passes**, après onze sprints où
elles n'avaient jamais bougé de plus d'un point. La raison est constante : les
classes visées sont celles dont les branches sont presque toutes des chemins
d'**absence** — un tiers injoignable, une donnée manquante, une observation
jamais faite. Ce sont les moins coûteuses à couvrir et les plus coûteuses à
ignorer, parce qu'elles ne font pas tomber le système : elles lui font afficher
un nombre faux.

Le relèvement des branches est **le premier depuis la pose du cliquet** : la
marge était restée courte du SPRINT-22 au SPRINT-32, entre 2,2 et 3,8 points.
Trois points d'un coup viennent des cinq classes qui parlent à un tiers — leurs
branches sont presque toutes des chemins d'échec.

---

## 1. Où se trouve exactement le manque

Mesuré, par paquet, instructions puis branches manquantes :

| Paquet | Instr. manquantes | Instr. % | Branches manquantes | Branches % |
|---|--:|--:|--:|--:|
| `com.zumm.service` | 4 702 | 77,3 % | 477 | 64,1 % |
| `com.zumm.domain` | 639 | 85,8 % | 72 | 59,3 % |
| `com.zumm.controller` | 553 | 78,5 % | 42 | 42,5 % |
| `com.zumm.service.regles` | 304 | 65,7 % | 36 | 41,9 % |
| `com.zumm.config` | 166 | 87,4 % | 32 | 42,9 % |
| `com.zumm.web.dto` | 91 | 98,3 % | 46 | 71,6 % |
| `com.zumm.securite` | 69 | 81,1 % | 16 | 65,2 % |
| `com.zumm.configmetier` | 59 | 88,6 % | 14 | 66,7 % |
| `com.zumm.web` | 48 | 92,2 % | 8 | 82,6 % |
| `com.zumm.tenant` | 33 | 91,2 % | 11 | 71,1 % |
| `com.zumm.repository` | 5 | 97,7 % | 4 | 71,4 % |
| `com.zumm` | 5 | 37,5 % | 0 | — |

**Deux chiffres méritent qu'on s'arrête.**

`com.zumm.controller` est à **42,5 % de branches** pour 78,5 % d'instructions.
L'écart dit exactement ce qui manque : les tests appellent les routes dans le cas
nominal et presque jamais dans les cas de refus. Or un contrôleur, c'est
majoritairement du branchement de refus.

`com.zumm.web.dto` est à **98,3 % d'instructions** mais **71,6 % de branches**.
Ce sont les `equals` générés des records : leurs instructions passent, leurs
branches non. C'est le premier endroit où la cible de 100 % devient discutable —
voir le §3.

### Les vingt-cinq classes qui portent le manque

Les dix premières portent à elles seules **2 481 instructions manquantes**, soit
**37 %** du total.

| Classe | Instr. manquantes | Branches manquantes | Couverture actuelle |
|---|--:|--:|--:|
| `ExportService` | 772 | 45 | 48,4 % |
| `RegistreElevagePdfService` | 379 | 27 | 48,6 % |
| `IdentiteService` | 374 | 27 | **6,3 %** |
| `OpenMeteoFournisseur` | 208 | 21 | **14,8 %** |
| `ElevageService` | 179 | 21 | 72,2 % |
| `RegleMeteoDefavorable` | 156 | 17 | **14,8 %** |
| `IndiceColonieService` | 150 | 44 | 61,7 % |
| `BilanAnnuelPdfService` | 125 | 12 | 79,3 % |
| `RechercheService` | 115 | 7 | 52,9 % |
| `ClientAnomalieIA` | 101 | 9 | 30,3 % |
| `NotificationAlerteService` | 96 | 15 | 59,8 % |
| `CaptureEssaimService` | 93 | 6 | 55,1 % |
| `FicheInspectionPdfService` | 93 | 12 | 80,7 % |
| `SiteService` | 90 | 12 | 90,2 % |
| `LotConditionnementService` | 88 | 6 | 73,7 % |
| `RucheService` | 85 | 4 | 71,4 % |
| `PhotoService` | 85 | 7 | 57,3 % |
| `PlanningService` | 81 | 6 | 82,7 % |
| `ConformiteBioService` | 79 | 15 | 74,0 % |
| `BriefingService` | 78 | 3 | 71,6 % |
| `TransportService` | 78 | 8 | 72,6 % |
| `SecurityConfig` | 78 | 13 | 91,2 % |
| `ComptabiliteService` | 76 | 6 | 81,2 % |
| `SyntheseRucherService` | 76 | 18 | 81,8 % |
| `MaterielService` | 73 | 6 | 60,5 % |

---

## 2. Les sept lots

L'ordre n'est pas celui du gain. Il est celui du **risque non couvert** : ce qui
peut faire tomber la production en premier passe en premier, et il se trouve que
c'est aussi ce qui rapporte le plus.

### Lot 1 — Les frontières externes  ·  783 instructions · 72 branches  ·  ✅ LIVRÉ

`IdentiteService` · `OpenMeteoFournisseur` · `ClientAnomalieIA` ·
`MeteoService` · `NotificationAlerteService`

**Le lot le plus urgent, et de loin.** Ces cinq classes parlent à quelque chose
qui n'est pas nous — Keycloak, Open-Meteo, le microservice Python — et elles sont
les moins couvertes du dépôt. `IdentiteService` à 6,3 % signifie que **le flux de
connexion n'est vérifié nulle part** hors du cas passant : les traductions de
statut HTTP en motif d'échec (`motifDeRefus`, `motifDeCreation`) décident si
l'utilisateur voit « identifiants invalides », « compte suspendu » ou « service
indisponible », et rien ne les teste.

**Comment.** Les trois classes parlent en `RestClient`, et
`spring-boot-starter-test` apporte déjà `MockRestServiceServer` — aucune
dépendance à ajouter, aucun réseau, aucun conteneur.

**Mais elles ne se testent pas de la même façon, et c'est le premier travail du
lot.** `IdentiteService` reçoit un `RestClient.Builder` par son constructeur : il
se teste tel quel, `MockRestServiceServer.bindTo(builder)` suffit.
`OpenMeteoFournisseur` et `ClientAnomalieIA` construisent le leur **à
l'intérieur** (`RestClient.builder()…build()`) : rien ne peut s'y interposer, et
c'est très exactement pourquoi ils sont à 14,8 % et 30,3 %. Le premier geste du
lot est donc d'injecter le constructeur, comme `IdentiteService` le fait déjà —
un paramètre de plus, aucun changement de comportement. **La sous-couverture
n'est pas ici un oubli de test : c'est un défaut de conception qui rendait le
test impossible.**

**Livré le 05/09/2026** — 41 tests ajoutés, couverture globale portée à
**84,4 %** d'instructions, **66,0 %** de branches, 86,3 % de lignes, sur
200 tests unitaires et 242 tests d'intégration.

| Classe | Avant | Tests ajoutés |
|---|--:|--:|
| `IdentiteService` | 6,3 % | 18 |
| `OpenMeteoFournisseur` | 14,8 % | 8 |
| `ClientAnomalieIA` | 30,3 % | 6 |
| `NotificationAlerteService` | 59,8 % | 9 |
| `MeteoService` | déjà couvert (4 instructions manquantes) | — |

**Un défaut que ce plan n'avait pas vu, et que le premier geste a sorti.**
`IdentiteService` n'avait **aucun délai d'attente**. Les deux autres fabriquaient
le leur — c'est même la seule chose que cette fabrication apportait — mais
`IdentiteService`, qui recevait déjà son constructeur du conteneur, héritait des
défauts, c'est-à-dire d'aucun. Un Keycloak qui accepte la connexion sans jamais
répondre bloquait donc le fil de la requête de connexion sans limite. Les délais
sont passés en configuration (`spring.http.client.*`, 2 s et 3 s) : ils
deviennent visibles, égaux pour les trois clients, surchargeables par
environnement — et les deux classes n'ont plus qu'à recevoir leur constructeur.

Le diagnostic du plan était donc juste et incomplet : la fabrication interne du
`RestClient` n'empêchait pas seulement de tester, elle **cachait une
configuration qui manquait ailleurs**.

**Ce que ça fait vérifier**, et c'était le vrai motif du lot :

1. **Chaque code de statut de Keycloak tombe sur le bon motif.** Un 401 qui
   sortirait en 503 ferait croire à une panne là où le mot de passe est faux.
2. **`PolitiqueReseau` coupe réellement — c'était le trou le plus subtil du
   dépôt, et il est fermé.** Deux tests la touchaient déjà
   (`AssistanceLocaleIT`, `InfoControllerTest`), mais tous deux vérifiaient que
   le serveur **annonce** l'état de son réseau sortant sur `/api/info`. Aucun ne
   vérifiait qu'avec `zumm.reseau.sortant=false` la météo et le microservice ne
   sont **pas appelés** : le mode local était testé sur sa promesse, pas sur son
   effet. La forme qui le prouve est `serveur.verify()` sur un serveur
   d'attentes **vide** — il échoue si un appel est parti.
3. **Une réponse malformée ne fait pas tomber la requête de l'utilisateur.**
   `OpenMeteoFournisseur.valeur(colonne, index)` lit un tableau par index — un
   jour où la source rendra une colonne plus courte, on saura ce qui se passe.
4. **Le repli EWMA se déclenche quand le microservice est absent**, et pas
   seulement quand il est éteint proprement.

> Plancher après le lot : **84,2 %** d'instructions · **66,5 %** de branches.

### Lot 2 — Les producteurs de fichiers  ·  1 391 instructions · 106 branches  ·  ✅ LIVRÉ

`ExportService` (772 !) · `RegistreElevagePdfService` · `BilanAnnuelPdfService` ·
`FicheInspectionPdfService` · `RapportVisitePdfService` · `ClasseurXlsx` ·
`CalendrierIcs`

**Le plus gros gain du plan, et le plus facile.** `ExportService` est à 48,4 %
avec quatorze ressources et trois formats : la combinatoire est de quarante-deux
sorties, dont on en teste une poignée. Ce sont des fonctions **pures** — une
grille de chaînes en entrée, des octets en sortie — donc testables sans base,
sans conteneur, en quelques millisecondes.

Le dépôt a déjà écrit la règle qui rend ce lot obligatoire :

> Écrire un format de fichier à la main n'est défendable que si le test le tient.

`ClasseurXlsx` a été écrit à la main **contre** Apache POI, et cette décision
n'est défendable que sous cette condition. Elle vaut aussi pour l'iCalendar et
pour chaque colonne de chaque export.

**Ce que ça fait vérifier.** Les quarante-deux combinaisons ressource × format
produisent un fichier lisible ; l'échappement CSV tient sur un nom de rucher
contenant une virgule, un guillemet ou un retour à la ligne ; le XLSX reste
ouvrable au-delà de la colonne Z ; un PDF de registre d'élevage sans aucune reine
sort vide plutôt qu'en erreur.

**Livré le 05/09/2026** — 87 tests ajoutés, couverture globale portée à
**88,1 %** d'instructions, **70,8 %** de branches, 88,9 % de lignes, sur
290 tests unitaires et 242 tests d'intégration.

| Classe | Avant | Tests |
|---|--:|--:|
| `ExportService` | 48,4 % | 62 (2 → 62) |
| `RegistreElevagePdfService` | 48,6 % · **12,9 %** de branches | 9 |
| `BilanAnnuelPdfService` | 79,3 % · 40 % de branches | 8 |
| `FicheInspectionPdfService` | 80,7 % · 50 % de branches | 8 |
| `RapportVisitePdfService` | 96,7 % · 61,5 % de branches | 4 (2 → 4) |

**Les tests PDF lisent le TEXTE du document, pas sa signature.** Le repère qui
existait vérifiait que les octets commencent par `%PDF` — un test qui resterait
vert si toutes les cellules sortaient vides. `PdfTextExtractor` est fourni par
OpenPDF, sans dépendance à ajouter ; le registre d'élevage est désormais vérifié
sur ce qu'un contrôleur y lirait, et c'est bien le sujet : c'est un document
réglementaire.

**Un défaut trouvé, et corrigé.** La légende de la fiche d'inspection était un
texte constant. Une fiche dont le gabarit avait éteint « Reine vue » **expliquait
quand même comment la remplir**. Au bureau cela se devine ; au rucher, debout,
cela se lit comme une colonne qu'on a oublié d'imprimer — et la fiche vierge
existe précisément pour les gens qui n'ont pas l'écran sous les yeux. La légende
est maintenant adossée aux colonnes réellement imprimées et disparaît avec elles.

**Le diagnostic du plan tenait, à une nuance près.** Le lot était annoncé comme
« le plus facile » : il l'a été pour `ExportService`, fonction pure sur quatorze
dépôts simulés. Il l'a moins été pour les PDF, où la question n'était pas
d'exécuter le code mais de décider **ce qu'un test doit affirmer** sur un
document binaire. La réponse — extraire le texte — vaut pour les quatre.

> Plancher après le lot : **87,9 %** d'instructions · **71,7 %** de branches.

### Lot 3 — Le reste des services métier  ·  2 528 instructions · 299 branches  ·  🟡 VAGUE A

Vingt-cinq classes, de `ElevageService` (179) à `MaterielService` (73).

**Le lot le plus long, et celui qu'il faut découper.** Il ne se traite pas d'un
bloc : le découpage naturel est par domaine, dans l'ordre de la valeur métier —
élevage, indices de colonie, recherche, production, comptabilité, matériel.

**Le point de vigilance est ici, et pas ailleurs.** C'est le lot où l'on est
tenté d'écrire des tests d'intégration qui traversent tout pour verdir vite. Le
dépôt a déjà tranché : les services qui calculent — `IndiceColonieService`
(61,7 %, **44 branches manquantes**, le pire ratio du dépôt),
`ComptabiliteService`, `SyntheseRucherService`, `ConformiteBioService` — sont des
**pièces pures** et se testent sans base. C'est exactement ce qui a repris un
point et demi de branches après le lot E et sept dixièmes après le lot D.

Les 44 branches manquantes d'`IndiceColonieService` sont le meilleur exemple du
plan : un indice qui se calcule à chaque lecture, avec des seuils, des `null`
possibles partout et aucune donnée stockée pour se rattraper. Chaque branche non
couverte y est une valeur d'indice potentiellement fausse affichée à
l'apiculteur.

#### Vague A — les quatre pires ratios de branches  ·  ✅ livrée le 05/09/2026

67 tests, **84 branches** prises sur les 318 du paquet. Couverture globale portée
à **89,0 %** d'instructions et **74,7 %** de branches (89,6 % de
lignes), sur 357 tests unitaires et 242 tests d'intégration.

| Classe | Branches avant | Tests |
|---|--:|--:|
| `IndiceColonieService` | **38,9 %** — 44 manquantes, *aucun test* | 28 |
| `SyntheseRucherService` | **35,7 %** — 18 manquantes | 13 |
| `ConformiteBioService` | **42,3 %** — 15 manquantes | 15 |
| `AlerteSanitaireService` | **46,2 %** — 7 manquantes | 11 |

Le choix des quatre suit la règle du §0 : ce sont les classes où **une branche
fausse ne casse rien** — elle affiche un nombre, l'apiculteur le lit, et rien ne
le contredit. Cinq décisions de conception y étaient tenues par la seule lecture
du code :

1. **Une composante n'est comptée que si elle a été observée.** Sans
   observation, l'indice vaut 0 et non 100 : l'ignorance ne se lit pas comme de
   la santé. Même règle au rucher, où `santeMoyenne` vaut `null`.
2. **Le risque d'essaimage remonté est le maximum, jamais la moyenne.** Sur
   trois colonies à 0/0/90, la moyenne dirait 30 et personne ne se déplacerait.
3. **Une pathologie seulement *suspectée* ne fait pas chuter l'indice** — au
   rucher on constate un symptôme, on ne pose pas un diagnostic.
4. **L'origine des sucres reste « à justifier » même quand tout est tracé.** Le
   système enregistre ce qui a été donné, jamais d'où cela vient.
5. **L'ordre des `else if` d'`AlerteSanitaireService` EST la règle métier** — sa
   propre javadoc le dit. Rien ne le tenait ; un refactor bien intentionné
   l'aurait défait sans bruit.

**Ce que la vague a coûté en pièges de test**, tous connus du dépôt : `anyLong()`
ne filtre pas un `null` ; un `mock()` créé *dans* les arguments d'un `when()`
laisse le stub inachevé (le piège du lot D) ; et deux accesseurs devinés au lieu
d'être lus — `forcerCarence` exige un motif, et c'est ce qui rend le forçage
opposable.

#### Vague B — quatre services, dont deux mal classés  ·  ✅ livrée le 05/09/2026

61 tests. Couverture globale portée à **89,9 %** d'instructions et **75,9 %** de
branches (90,2 % de lignes), sur **418** tests unitaires et 242 tests
d'intégration. Planchers relevés à `0,89` et `0,75`.

| Classe | Avant | Après | Tests |
|---|--:|--:|--:|
| `ComptabiliteService` | 81,2 % · 70 % br. | 95,6 % · 95,0 % br. | 17 |
| `RechercheService` | **52,9 %** · 41,7 % br. | **100 %** · 100 % br. | 18 |
| `MaterielService` | **60,5 %** · 57,1 % br. | 97,8 % · 92,9 % br. | 14 |
| `BriefingService` | 71,6 % · 62,5 % br. | **100 %** · 100 % br. | 12 |

**Le plan s'était trompé sur deux d'entre eux.** Il annonçait pour le reste du
lot 3 « des lectures et des écritures, pas des calculs », et un gain par test
plus faible. C'est vrai de `MaterielService`. Ce l'est beaucoup moins de
`RechercheService` et de `BriefingService`, qui portent chacun une décision de
sécurité ou d'architecture :

- **La recherche transverse est bornée par sécurité, pas par confort.** Deux
  caractères minimum — « une recherche sur *a* n'est pas une recherche, c'est un
  export » —, cinq résultats par famille pour qu'une famille prolifique n'évince
  pas les six autres, et surtout **aucune position, aucune adresse, aucun
  courriel**. Chercher par rue rendrait interrogeable ce que
  `PolitiquePositions` masque à l'affichage ; chercher par adresse ferait de la
  palette un annuaire exportable, un caractère à la fois.
- **Le briefing ne passe par aucun modèle de langue** (D4, ADR-013), et les
  tests vérifient ce que cette contrainte implique concrètement : chaque ligne
  cite sa **source** — une valeur de déclenchement, une date de retrait, un nom
  de produit — et non une formulation. Le jour où un détail cesserait d'être
  vérifiable, le briefing redeviendrait ce qu'il refuse d'être.

Deux gestes de `MaterielService` portaient aussi une décision que rien ne
tenait : **un entretien ne se date pas dans l'avenir** — sans ce refus, une
faute de frappe sur l'année sortirait le matériel du plan de maintenance sans
bruit, un oubli qui ne se voit qu'à la panne — et **il remet l'état à « bon »,
jamais à « neuf »**.

**Reste au lot 3** : une quinzaine de services, ~1 800 instructions et
~190 branches, dont `ElevageService` (179 instructions) est le plus gros.

**Le piège de test de ce lot, trois fois rencontré.** Un `mock()` créé *dans*
les arguments d'un `when()` laisse le premier stub inachevé
(`UnfinishedStubbingException`) : Mockito construit le stub extérieur avant
d'évaluer ses arguments. C'est le coût de fabriquer des entités JPA dont
l'identifiant n'est pas assignable autrement — et la parade est toujours la
même, construire les mocks d'abord.

> Plancher après le lot : **94,7 %** d'instructions · **86,3 %** de branches.

### Lot 4 — Les refus des contrôleurs  ·  553 instructions · 42 branches

**42,5 % de branches** : le plus mauvais chiffre du dépôt après le moteur de
règles. Les tests d'intégration appellent les routes et vérifient qu'elles
répondent ; presque aucun ne vérifie **qu'elles refusent**.

**Ce que ça fait vérifier**, et c'est de la sécurité, pas de la couverture :

- chaque route rejette le rôle qui ne doit pas l'atteindre — la règle
  `anyRequest().hasAnyRole(...)` du dépôt n'a de valeur que si on la teste route
  par route ;
- le corps invalide sort en `400` avec un message exploitable, pas en `500` ;
- les conflits métier sortent en `409` (carence, alerte déjà ouverte, floraison
  déjà saisie) ;
- `PolitiquePositions` filtre bien **chaque** DTO portant des coordonnées. Le
  dépôt exige que tout nouveau DTO y passe : un test paramétré sur la liste des
  DTO géographiques transforme cette exigence écrite en exigence tenue.

### Lot 5 — Le domaine  ·  639 instructions · 72 branches

`com.zumm.domain` est à 85,8 % d'instructions mais **59,3 % de branches** — le
plus mauvais ratio de tous les paquets. Ce ne sont pas des accesseurs : ce sont
les **méthodes de calcul portées par les entités** et les `equals`/`hashCode`
composites, ceux-là mêmes dont dépend l'identité multi-tenant.

**Ce que ça fait vérifier.** Qu'une clé composite `(id, tenant_id)` ne se
confonde pas entre deux exploitations, et que les invariants portés par l'entité
(dates cohérentes, unités compatibles, transitions d'état) refusent ce que la
base refuserait — avant d'atteindre la base.

### Lot 6 — Le moteur de règles  ·  304 instructions · 36 branches

`com.zumm.service.regles` : **65,7 %** d'instructions, **41,9 %** de branches, la
pire couverture de branches du dépôt. `RegleMeteoDefavorable` y est à **14,8 %**.

C'est le paquet dont la sous-couverture est la plus **paradoxale** : cinq règles
dont le rôle est précisément de décider, donc presque uniquement des branches, et
c'est le paquet le moins branché en test. Une règle qui ne se déclenche pas ne
casse rien de visible — elle produit simplement une liste de tâches trop courte,
et personne ne s'en aperçoit. C'est le pire mode de défaillance qui soit.

**Ce que ça fait vérifier.** Chaque règle propose *et* s'abstient, aux deux côtés
de son seuil ; l'idempotence tient (clé de déclenchement + index unique partiel) ;
et deux règles qui visent la même ruche le même jour ne produisent pas deux fois
la même tâche.

### Lot 7 — L'infrastructure  ·  471 instructions · 131 branches

`config` (166 · 32) · `web.dto` (91 · 46) · `securite` (69 · 16) ·
`configmetier` (59 · 14) · `web` (48 · 8) · `tenant` (33 · 11) ·
`repository` (5 · 4)

Le solde. `SecurityConfig` (78 instructions, 13 branches) et `com.zumm.tenant`
(71,1 % de branches) sont les seuls morceaux qui portent un vrai risque : ce sont
les invariants de cloisonnement. Le reste est du câblage.

C'est aussi le lot où le §3 s'applique le plus.

---

## 3. L'irréductible, et comment l'exclure honnêtement

Une partie du code **ne peut pas** être couverte par un test utile. La nommer
maintenant évite de la découvrir à 99,4 % en écrivant n'importe quoi pour finir.

| Quoi | Volume | Pourquoi |
|---|--:|---|
| `ZummApplication.main` | 5 instr. | Le point d'entrée Spring Boot. Un test qui l'appelle démarre l'application — ce que fait déjà chaque test d'intégration, sans passer par `main`. |
| `equals`/`hashCode` générés des records | l'essentiel des 46 branches de `web.dto` | Chaque `equals` d'un record à dix composants porte une vingtaine de branches que le compilateur écrit. Les couvrir demande de comparer chaque champ deux à deux : du test généré pour du code généré. |
| Branches défensives | dispersé | `default:` d'un `switch` exhaustif sur une énumération, `if (x == null) throw new IllegalStateException(...)` sur un cas que la base interdit. Les couvrir demande de fabriquer un état que le système ne peut pas atteindre. |

**Comment les traiter, dans l'ordre de préférence :**

1. **Supprimer le code mort.** Un `default:` inatteignable sur une énumération
   fermée peut souvent disparaître.
2. **Exclure explicitement, par une règle nommée**, dans la configuration JaCoCo
   du `pom.xml` : `ZummApplication`, et les méthodes annotées d'un
   `@Generated` maison — JaCoCo ignore nativement toute annotation dont le nom
   simple est `Generated` et dont la rétention est `RUNTIME` ou `CLASS`.
3. **Ne jamais exclure une classe entière** pour cause de couverture difficile.
   Exclure `ExportService` parce qu'il est gros serait la version silencieuse
   d'abaisser le plancher.

**Ce que l'exclusion change au compte.** Elle ne « donne » pas de couverture :
elle retire du dénominateur ce qui n'est pas du code écrit à la main. Après
exclusion, la cible de 100 % porte sur du code que quelqu'un a réellement décidé
d'écrire — et devient une affirmation qui a un sens.

---

## 4. Séquencement et progression cumulée

| Ordre | Lot | Instr. | Branches | Instr. cumulées | Branches cumulées |
|---|---|--:|--:|--:|--:|
| — | *état mesuré* | — | — | **82,1 %** | **63,0 %** |
| ~~1~~ | ~~Frontières externes~~ ✅ | 783 | 72 | **84,4 %** | **66,0 %** |
| ~~2~~ | ~~Producteurs de fichiers~~ ✅ | 1 391 | 106 | **88,1 %** | **70,8 %** |
| 3 | Services métier *(vagues A+B)* 🟡 | 2 528 | 299 | **89,9 %** | **75,9 %** |
| 4 | Refus des contrôleurs | 553 | 42 | **96,2 %** | **88,3 %** |
| 5 | Domaine | 639 | 72 | **97,9 %** | **91,8 %** |
| 6 | Moteur de règles | 304 | 36 | **98,7 %** | **93,6 %** |
| 7 | Infrastructure | 471 | 131 | **100 %** | **100 %** |

6 674 instructions et 758 branches : la somme des lots tombe juste, aucune ligne
n'est orpheline.

**Le lot 3 est le point de bascule des branches** — il en porte 299 sur 758, soit
39 % à lui seul. Tant qu'il n'est pas fait, la marge de branches reste ce qu'elle
est depuis le SPRINT-22 : courte.

**Où s'arrêter si l'on doit s'arrêter.** Les lots 1 à 3 valent à eux seuls
**94,7 % / 86,3 %**, pour un peu moins des deux tiers de l'effort et la totalité
du risque réel. Les lots 4 à 7 valent surtout pour ce qu'ils font vérifier — les
refus, le cloisonnement, les règles —, pas pour le pourcentage. Si le budget
manque, c'est là qu'il faut couper, et le dire.

---

## 5. Le piège à éviter, nommé avant d'écrire

À partir du lot 5, la tentation change de nature. Elle n'est plus d'écrire trop
peu de tests, mais d'en écrire qui n'affirment rien :

```java
// Ce test verdit une ligne et ne prouve rien.
@Test
void toStringNeCassePas() {
    assertThat(new Ruche().toString()).isNotNull();
}
```

**La règle de recevabilité d'un test de ce plan** : il doit pouvoir échouer pour
une raison qu'on sait nommer. Si l'on ne peut pas dire quel bug il attraperait,
il ne compte pas — même s'il fait monter le pourcentage.

Le repère est déjà dans le dépôt : `AnomalieEmbarqueeTest` et `ewma.test.ts`
fixent **les mêmes trois nombres sur la même série**, en Java et en TypeScript.
Toucher l'un sans l'autre fait échouer une campagne. C'est ce qu'est un test qui
tient quelque chose.

---

## 6. Comment vérifier

Le chiffre se relève, il ne se recopie pas.

```bash
# La mesure de référence — Docker requis (Testcontainers)
cd backend && ./mvnw -B verify

# Rapport lisible
#   backend/target/site/jacoco/index.html
```

```bash
# Le manque, par paquet puis par classe, depuis la sortie machine
cd backend/target/site/jacoco && python - <<'PY'
import csv, io
from collections import defaultdict
rows = list(csv.DictReader(io.open('jacoco.csv', encoding='utf-8')))
def rate(m, c): return 100.0 * c / (m + c) if m + c else 100.0

tot = defaultdict(int)
paq = defaultdict(lambda: [0, 0, 0, 0])
for r in rows:
    for k in ('INSTRUCTION', 'BRANCH', 'LINE'):
        tot[k + 'm'] += int(r[k + '_MISSED'])
        tot[k + 'c'] += int(r[k + '_COVERED'])
    p = paq[r['PACKAGE']]
    p[0] += int(r['INSTRUCTION_MISSED']); p[1] += int(r['INSTRUCTION_COVERED'])
    p[2] += int(r['BRANCH_MISSED']);      p[3] += int(r['BRANCH_COVERED'])

for k in ('INSTRUCTION', 'BRANCH', 'LINE'):
    print('%-12s %5.1f %%  (manque %d)' % (k, rate(tot[k+'m'], tot[k+'c']), tot[k+'m']))
print()
for k, v in sorted(paq.items(), key=lambda x: -x[1][0]):
    print('%-28s %5d instr manquantes  %5.1f %%  |  %4d branches  %5.1f %%'
          % (k, v[0], rate(v[0], v[1]), v[2], rate(v[2], v[3])))
PY
```

> ⚠️ **Un `BUILD SUCCESS` ne prouve rien si les tests d'intégration ont été
> ignorés faute de Docker.** La campagne d'intégration sautée fait *monter* le
> pourcentage affiché sur certaines classes et *chuter* le total sans que rien
> n'échoue. Vérifier `Tests run: N, … Skipped: 0` sur les rapports `…IT`.

**Le plancher monte à chaque lot livré**, dans
`backend/pom.xml` (`${couverture.minimale}` et `${couverture.branches.minimale}`),
au chiffre mesuré arrondi au dixième inférieur. Il ne redescend jamais — c'est la
seule règle de ce document qui n'admet aucune exception.
