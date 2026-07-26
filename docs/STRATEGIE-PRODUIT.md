# Zümm — positionnement marché et recommandations

> État au 26/07/2026. Ce document sert deux usages : justifier les choix produit en
> soutenance, et servir de backlog priorisé pour les sprints suivants.

---

## 1. Le marché tel qu'il est

Quatre familles d'offres coexistent, avec très peu de recouvrement entre elles —
et c'est précisément dans les interstices que Zümm a une place.

### A. Carnets de ruche grand public

**HiveTracks**, **Apiary Book**, **Beetight**, **BeeKeeper Pro**, **HiveLog AI**
(analyse photo de cadres), **HiveSense** (hors-ligne d'abord, saisie vocale).

Modèle : freemium, ~10 $/mois au-delà de quelques ruches. Excellente UX mobile,
saisie rapide sur le terrain.

**Leurs limites** : mono-utilisateur, aucune notion d'équipe ni de rôles, pas de
traçabilité réglementaire, aucune intelligence spatiale, données hébergées chez
l'éditeur sans option d'auto-hébergement.

### B. Outils professionnels francophones

**Beekube** (gratuit, registre d'élevage réglementaire, très complet),
**Api'Track** (gratuit, traçabilité miellerie, piégeage du frelon asiatique),
**Melys** (traçabilité extraction → fût → conditionnement), **Bee-Partner**,
**apiDan**.

**Leurs limites** : peu ou pas de cartographie analytique, pas de multi-tenant
véritable pour une coopérative, monolingue (français), API rarement ouverte.

### C. Apiculture de précision (matériel + logiciel)

**BeeGuard** (balance, thermomètre de couvain, antivol GPS), **BroodMinder**
(capteurs BLE modulaires), **Arnia** (acoustique), **3Bee**, **Nectar** (grands
cheptels, Amérique du Nord).

Modèle : le logiciel est offert, le matériel est vendu.

**Leurs limites** : l'outil de gestion est un accessoire du capteur ; le suivi des
visites, des agents et des récoltes y est pauvre ou absent. On y voit ses courbes,
on n'y pilote pas une exploitation.

### D. Projets libres

`hivetool.org`, Open Source Beehives, divers montages Arduino/ESP32.

**Leurs limites** : ce sont des briques, pas des produits — pas d'authentification,
pas de multi-utilisateur, pas de mise en production réaliste.

---

## 2. Ce que personne ne fait, et que Zümm fait déjà

| Capacité | Zümm | Le marché |
|---|---|---|
| **Multi-tenant garanti par le SGBD** (RLS + clés composites) | ✅ | Aucun concurrent grand public. Décisif pour une **coopérative** ou un **groupement**. |
| **Intelligence spatiale PostGIS** — clustering DBSCAN, plus proches voisins, ordre de tournée | ✅ | Personne. Les autres affichent des points sur une carte. |
| **Trilingue FR/EN/AR avec RTL** | ✅ | Inexistant. Ouvre le Maghreb, le Machrek et le Golfe. |
| **Auto-hébergeable**, données chez l'exploitant | ✅ | BeeGuard et HiveTracks sont des SaaS fermés. |
| **API REST + contrat OpenAPI ouvert** | ✅ | Rarement documenté, presque jamais public. |
| **Confidentialité des positions par rôle** | ✅ | Aucun. Pourtant le vol de ruches est le premier sinistre du métier. |
| **Idempotence du rejeu hors-ligne** | ✅ | Les apps hors-ligne du marché dupliquent silencieusement. |

**Le positionnement qui en découle** : Zümm n'est pas un carnet de ruche de plus.
C'est le **système d'information d'une organisation apicole** — plusieurs
exploitations, plusieurs agents, plusieurs rôles, une traçabilité opposable.

---

## 3. Les manques du marché, et l'ordre dans lequel les prendre

### Priorité 1 — Conformité réglementaire *(fait au SPRINT-14)*

La [directive (UE) 2024/1438](https://eur-lex.europa.eu/legal-content/FR/TXT/PDF/?uri=OJ:L_202401438)
s'applique au **14 juin 2026** (décret n° 2026-312) : pays d'origine sur
l'étiquette, par ordre décroissant, en pourcentages.

C'est l'argument commercial du moment, et **presque aucun outil ne le gère
correctement** : la plupart tracent la récolte, pas le **mélange conditionné** —
or c'est le mélange qui est étiqueté.

→ Livré : `lot_conditionnement` + `lot_composition`, consolidation par pays, tri
décroissant, mention traduite (`GET /api/lots/{id}/mention`), 7 tests d'intégration.

**Reste à faire** : registre d'élevage réglementaire, numéro NAPI/SIRET, déclaration
annuelle de ruches, étiquette PDF prête à imprimer avec DLUO et numéro de lot.

### Priorité 2 — Intégration matérielle réelle

Aujourd'hui l'ingestion se fait par `POST /api/mesures`. C'est le canal, pas
l'intégration. Ce qui manque pour parler au matériel du marché :

- **pont MQTT** (le protocole des passerelles LoRaWAN) ;
- **connecteur BroodMinder** — API ouverte, parc installé important ;
- **connecteur LoRaWAN** (TTN / ChirpStack) pour les balances au champ ;
- **BLE depuis la PWA** via Web Bluetooth, pour relever un capteur sans passerelle.

Le socle est prêt : le client machine `zumm-capteur` (client_credentials, rôle
`capteur` limité à l'ingestion) a été posé au SPRINT-12.

### Priorité 3 — Ce que Zümm a et n'exploite pas

Trois atouts déjà en base, sans usage produit :

1. **Le clustering PostGIS** sert la carte. Il pourrait servir la **transhumance** :
   proposer un emplacement en croisant grappes, rayons de butinage et météo.
2. **La détection d'anomalie EWMA** signale. Elle pourrait **prévenir** : une chute
   de poids nocturne, c'est un vol ; une chute diurne brutale, un essaimage. Deux
   alertes métier distinctes, à partir des données déjà collectées.
3. **La météo** est affichée. Croisée aux mesures, elle expliquerait les anomalies
   (« poids en baisse — 4 jours de pluie ») au lieu de les signaler à sec.

Aucun de ces trois points ne demande de nouvelle donnée. C'est le meilleur rapport
valeur/effort du backlog.

### Priorité 4 — L'expérience de terrain

Ce que HiveSense fait mieux que tout le monde, et que Zümm devrait prendre :

- **saisie vocale** de la visite (Web Speech API) — les mains sont dans la ruche ;
- **mode gant** : cibles ≥ 56 px, aucune saisie clavier obligatoire ;
- **photo avec position et horodatage** rattachée à la ruche ;
- **QR sur la ruche** pour ouvrir directement sa fiche.

### Priorité 5 — La valeur que personne ne vend

- **Comparaison anonymisée entre pairs** : « votre miellée de juin est 15 % sous la
  médiane de votre département ». Zümm est multi-tenant : il est le seul à pouvoir
  agréger sans exposer. Un vrai différenciateur — et un sujet de consentement à
  traiter sérieusement.
- **Coût de revient au kilo** : le ROI existe déjà (`SyntheseService`) ; il manque
  la ventilation par rucher et par saison.
- **Portail client** : un QR sur le pot ouvre l'histoire du miel — rucher, date,
  photo du site, origines. Argument de vente directe, à partir de données déjà là.

---

## 4. Recommandations transverses

### Ce qu'il faut tenir

1. **La RLS d'abord.** L'[ADR-008](../roadmap/operationnel/06_decisions/ADR-008-rls-contre-compression.md)
   a tranché contre la compression pour la conserver. Cette ligne doit rester : la
   garantie d'isolation est le socle de l'offre coopérative.
2. **La sécurité comme argument commercial.** Positions arrondies par rôle, audit,
   CSP, validation d'audience, RBAC deny-by-default : aucun concurrent ne peut en
   dire autant. C'est une page de plaquette, pas seulement une ligne de backlog.
3. **L'auto-hébergement.** C'est la réponse à « où sont mes positions de ruchers ? »,
   la question que se pose tout apiculteur professionnel.

### Ce qu'il faut corriger avant toute mise en production

| Sujet | Pourquoi c'est bloquant |
|---|---|
| **Jetons en `localStorage`** | Une XSS = vol de session durable. Voir [ADR-006](../roadmap/operationnel/06_decisions/ADR-006-stockage-des-jetons.md) — le pattern BFF est la cible. |
| **Autorisation horizontale** | Un saisonnier voit tout le parc de l'exploitation. US-053. |
| **Client d'API écrit à la main** | La parité des types avec le contrat OpenAPI n'est garantie par rien. |
| **Tuiles OSM publiques** | Leur politique d'usage exclut la production. `VITE_TUILES_URL` est prévu ; il faut un fournisseur ou un serveur interne. |
| **Keycloak en `start-dev`** | La pile « complète » lance encore le mode développement. |

### Modèle économique observé

- **Freemium par nombre de ruches** — HiveTracks (~10 $/mois).
- **Gratuit financé par le matériel** — BeeGuard, BroodMinder, 3Bee.
- **Gratuit financé autrement** — Beekube, Api'Track.

Ces trois modèles visent l'apiculteur individuel, et se livrent une guerre des
prix vers zéro. **Le créneau libre est ailleurs** : l'abonnement par organisation —
coopérative, groupement, exploitation à plusieurs salariés — où le multi-tenant,
les rôles, l'audit et la conformité justifient un prix, et où le gratuit grand
public ne répond pas au besoin.

---

## 5. Feuille de route proposée

| Sprint | Contenu | Pourquoi maintenant |
|---|---|---|
| ~~12~~ | ~~Durcissement sécurité~~ | ✅ livré |
| ~~13~~ | ~~PWA déployable, graphiques, carte~~ | ✅ livré |
| ~~14~~ | ~~Idempotence, index, conformité miel~~ | ✅ livré |
| **15** | Registre réglementaire, étiquette PDF, portail QR public | Achève la conformité et ouvre la vente directe |
| **16** | Pont MQTT, connecteur BroodMinder, Web Bluetooth | Passe de « on peut ingérer » à « ça marche avec le matériel du marché » |
| **17** | Alertes métier (vol, essaimage), corrélation météo | Meilleur rapport valeur/effort : aucune donnée nouvelle |
| **18** | BFF cookie `HttpOnly`, autorisation horizontale, client généré | Prérequis d'une exploitation réelle |
| **19** | Mode terrain (voix, gants, QR ruche) | Adoption quotidienne |
| **20** | Comparaison anonymisée entre pairs | Le différenciateur que seul un multi-tenant peut offrir |

---

## Sources

- [Best Beekeeping Apps 2026](https://beekeeping-diary.eu/blog/en/best-beekeeping-apps-2026/) · [HiveLog AI — comparatif](https://beefamily.net/beekeeping-apps) · [MyApiary](https://www.myapiary.com/)
- [Beekube — top 5 des applications de suivi](https://www.beekube.com/apiculture/guide-apiculture/outils-logiciels-apiculteurs/top-5-applications-suivi-ruches/) · [BeeGuard](https://www.beeguard.fr/application) · [Api'Track](https://api-track.bee-apic.com/) · [Melys](https://melys.app/)
- [BroodMinder](https://broodminder.com/) · [Comparatif capteurs 2026](https://beefamily.net/smart-hive-sensors)
- [Directive (UE) 2024/1438](https://eur-lex.europa.eu/legal-content/FR/TXT/PDF/?uri=OJ:L_202401438) · [Réglementation étiquetage 2026](https://www.apiculture.net/blog/etiquetage-du-miel-ce-que-dit-la-loi-en-2026-n516)
