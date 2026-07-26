# ADR-006 — Où vivent les jetons de la PWA

- **Date** : 2026-07-26
- **Statut** : 🟢 Accepté et **mis en œuvre** — SPRINT-16
- **Révision** : le statu quo (option A) a tenu deux sprints ; l'option C est en place depuis le 2026-07-26
- **Décideurs** : architecte, responsable sécurité
- **Bloque** : surface d'attaque XSS, architecture d'authentification, CSRF

---

## Contexte

La PWA obtient ses jetons de Keycloak par le flux *Authorization Code + PKCE*
(`frontend/src/auth/oidc.ts`) et les conserve dans `localStorage`
(`frontend/src/auth/session.ts`) : le jeton d'**accès** et le jeton de
**rafraîchissement**.

Ce choix a une conséquence directe et connue : **toute exécution de script tiers
dans l'origine de l'application vaut vol de session**. `localStorage` est lisible
par n'importe quel JavaScript de la page. Et ce n'est pas le vol du jeton d'accès
qui coûte le plus cher — il expire en cinq minutes — mais celui du jeton de
**rafraîchissement**, qui survit à la fermeture de l'onglet et permet de
re-fabriquer des jetons d'accès pendant toute la durée de la session SSO.

Trois éléments aggravent l'exposition dans le cas de Zümm :

1. les données protégées sont des **positions de ruchers**, dont le vol a une
   valeur marchande immédiate (cf. ADR-004 et annexe G) ;
2. l'application accepte du **contenu utilisateur** rendu ensuite dans l'interface
   (constatations de visite, légendes et URL de photos) ;
3. c'est une **PWA** : le service worker et le cache prolongent la vie de tout ce
   qui a été servi une fois.

## Options

### A. Statu quo — jetons dans `localStorage`

Ce que fait le code aujourd'hui. Simple, sans serveur intermédiaire, et compatible
avec l'API strictement sans état (`SessionCreationPolicy.STATELESS`, CSRF
désactivé à juste titre puisque rien ne voyage en cookie).

Coût : une seule XSS suffit à exfiltrer une session complète, durablement.

### B. Jeton d'accès en mémoire, rafraîchissement en `localStorage`

Réduit la fenêtre : le jeton d'accès disparaît au rechargement. Mais le jeton de
rafraîchissement — celui qui compte — reste lisible. **Le gain est cosmétique** :
il complique l'exfiltration sans la rendre impossible.

### C. Pattern BFF / *Token Handler*

Un back-end de façade détient les jetons ; le navigateur ne reçoit qu'un cookie
`HttpOnly; Secure; SameSite=Strict`. Un script injecté ne peut alors **pas lire**
le cookie — il peut au mieux déclencher des requêtes depuis la page ouverte, ce
qui est un dégât borné et détectable, sans exfiltration réutilisable ailleurs.

C'est la recommandation courante de l'OAuth 2.0 Security BCP pour les
applications de navigateur, et la seule option qui traite réellement le risque.

Coût réel, à ne pas minimiser :
- un composant serveur de plus (Spring Cloud Gateway ou un filtre dédié) ;
- **CSRF à réactiver** : dès qu'un cookie porte l'authentification, le navigateur
  l'envoie tout seul. Le `csrf.disable()` de `SecurityConfig` deviendrait un
  défaut, pas un choix ;
- l'API n'est plus sans état de bout en bout ;
- affinité de session ou stockage partagé si le BFF est répliqué.

## Décision

**Option C : le pattern BFF.** L'option A a été retenue à titre transitoire le
temps de deux sprints, puis abandonnée — la raison invoquée alors (« périmètre
académique ») ne résistait pas au fait que la mesure retenue bornait la
probabilité d'une XSS sans jamais en borner l'impact.

### Ce qui a été mis en œuvre

Le back-end est désormais **à la fois** serveur de ressources et client OAuth2.
Deux chaînes de sécurité, une seule matrice d'autorisation :

| | Machines | Navigateurs |
|---|---|---|
| Identité | jeton porteur | cookie de session `HttpOnly` |
| État | sans état | session serveur |
| CSRF | sans objet (aucun cookie) | **actif**, jeton en cookie lisible |
| Jetons OIDC | présentés par l'appelant | **détenus par le serveur** |

L'aiguillage se fait sur la présence d'un en-tête `Authorization: Bearer` —
critère explicite et observable, pas une devinette sur l'agent utilisateur.

**La matrice RBAC est écrite une seule fois** et appliquée aux deux chaînes.
C'était le principal risque de la manœuvre : deux chaînes, c'est deux occasions
de diverger, et le chemin oublié devient la porte d'entrée. Un test le vérifie
sur les deux chemins.

### Ce que le navigateur a perdu — et c'est le but

- `session.ts` ne stocke plus aucun jeton : il ne mémorise que ce que le serveur
  veut bien dire de la session (nom, rôles, exploitation) ;
- `oidc.ts` est passé de ~190 lignes de protocole — génération du verifier,
  échange du code, rafraîchissement sous verrou, révocation — à **deux
  navigations**. Le code le plus sûr est celui qu'on n'écrit plus ;
- le rafraîchissement côté navigateur a disparu, avec ses 19 tests. Le serveur
  renouvelle ; le client n'a plus à savoir qu'il existe des jetons ;
- l'écran de connexion ne collecte plus rien, pas même un jeton collé à la main —
  c'était le seul endroit de l'interface capable d'en introduire un.

### La contrepartie, assumée

Le cookie part **tout seul** : un site tiers peut donc déclencher une requête
authentifiée. CSRF redevient obligatoire, et le `csrf.disable()` d'avant serait
maintenant un défaut, non un choix. Le jeton est déposé dans un cookie **lisible
par le script de la page** : ce n'est pas un secret, c'est la preuve que la
requête vient bien de notre origine — seule capable de le relire.

Un test échoue si la protection est retirée. C'est le contrôle qui compte : sans
lui, le BFF échangerait un risque d'exfiltration contre un risque de requête
forgée, ce qui ne serait pas un progrès.

### Ce qui n'est PAS résolu

Une XSS reste capable d'**agir depuis la page ouverte** — elle ne repart
simplement plus avec une session réutilisable ailleurs, ni après la fermeture de
l'onglet. La CSP demeure donc nécessaire, et les mesures prises au SPRINT-12
restent toutes en vigueur.

## Conséquences

- La CSP reste un élément **fonctionnel** : toute évolution du front qui exigerait
  `unsafe-inline` sur `script-src` est un retour en arrière de sécurité.
- Le client Keycloak `zumm-bff` est **confidentiel** : il détient un secret, ce
  qui est précisément ce qu'un client public ne peut pas faire, et ce qui rend le
  BFF possible. Ce secret ne doit jamais entrer dans le dépôt.
- Le client public `zumm-frontend` n'est plus utilisé par la PWA. Il est conservé
  pour les intégrations existantes ; le supprimer est une tâche de nettoyage.
- La découverte OIDC **n'est pas** activée côté client : les endpoints sont
  déclarés explicitement, pour que l'application démarre sans Keycloak joignable —
  même choix que pour le serveur de ressources. La déconnexion RP-initiated
  construit donc son URL de fin de session elle-même (`DeconnexionOidc`).
- Une session serveur implique une affinité ou un stockage partagé le jour où le
  back-end sera répliqué. C'est le coût réel de cette décision, et il est à
  traiter avant toute mise à l'échelle horizontale.

## Références

- OAuth 2.0 for Browser-Based Applications (IETF, BCP)
- [ADR-001](ADR-001-multi-tenant.md) — isolation multi-tenant
- `infra/nginx/nginx.conf` — CSP en vigueur
