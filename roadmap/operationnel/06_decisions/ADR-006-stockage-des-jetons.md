# ADR-006 — Où vivent les jetons de la PWA

- **Date** : 2026-07-26
- **Statut** : 🟡 Accepté à titre transitoire — à réexaminer avant toute mise en production réelle
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

**Rester en option A pour le périmètre académique, en le disant explicitement**,
et compenser par les mesures qui réduisent la probabilité d'une XSS plutôt que son
impact :

- **CSP stricte** posée par le proxy inverse (SPRINT-12) : `script-src 'self'`,
  aucun script en ligne, aucun CDN, `object-src 'none'`, `base-uri 'self'`,
  `form-action 'self'`, `frame-ancestors 'none'` ;
- **validation à l'entrée** des champs qui finissent dans le DOM — les URL de
  photo sont restreintes à `http(s)`/chemin relatif (`PhotoCorps`) ;
- **révocation explicite** du jeton de rafraîchissement à la déconnexion
  (`deconnexionOidc`), pour que la fermeture de session ait un effet côté serveur ;
- **rotation courte** des jetons d'accès (cinq minutes, défaut Keycloak conservé).

**Le BFF reste la cible.** Ce n'est pas « à voir un jour » : c'est le seul point
de l'architecture de sécurité de Zümm où la mesure retenue borne la probabilité
sans borner l'impact. La bascule est à programmer avant toute exploitation avec
des données réelles d'exploitations tierces.

## Conséquences

- La CSP devient un élément **fonctionnel**, pas décoratif : toute évolution du
  front qui exigerait `unsafe-inline` sur `script-src` est un retour en arrière de
  sécurité et doit être refusée. `style-src` tolère `unsafe-inline` (MapLibre pose
  ses styles de marqueur en ligne) — une feuille de style n'exécute rien.
- Une revue de sécurité front devient obligatoire à chaque introduction de
  `dangerouslySetInnerHTML` ou d'injection DOM directe. Il n'y en a aucune
  aujourd'hui ; `CarteFond` construit ses marqueurs avec `textContent`.
- Le jour de la bascule en BFF : réactiver CSRF, retirer `localStorage` de
  `session.ts`, et l'en-tête `Authorization` du client d'API.

## Références

- OAuth 2.0 for Browser-Based Applications (IETF, BCP)
- [ADR-001](ADR-001-multi-tenant.md) — isolation multi-tenant
- `infra/nginx/nginx.conf` — CSP en vigueur
