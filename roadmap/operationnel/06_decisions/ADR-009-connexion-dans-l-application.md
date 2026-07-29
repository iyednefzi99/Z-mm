# ADR-009 — Connexion et inscription depuis l'application

- **Date** : 2026-07-29
- **Statut** : 🟢 Accepté et **mis en œuvre** — SPRINT-18
- **Décideurs** : product owner, architecte, responsable sécurité
- **Dépend de** : [ADR-006](ADR-006-stockage-des-jetons.md) — le BFF et le cookie de session
- **Bloque** : parcours d'entrée, création de comptes, second facteur

---

## Contexte

Depuis l'ADR-006, l'entrée dans Zümm est une **redirection** : l'écran de session
ne porte qu'un bouton, qui envoie l'utilisateur s'authentifier sur les pages de
Keycloak, lequel le renvoie ensuite avec un cookie. La PWA ne voit jamais un mot
de passe, jamais un jeton.

C'est le montage le plus sûr, et il pose deux problèmes qui ne sont pas
techniques :

1. **la rupture de marque.** L'utilisateur quitte une interface soignée et
   trilingue pour une page grise, en anglais par défaut, portant le nom d'un
   composant d'infrastructure qui ne lui dit rien. Pour un exploitant qui découvre
   le produit, c'est le premier écran — et il n'appartient pas au produit.
2. **l'absence de création de compte.** `registrationAllowed` est à `false`, et
   il doit le rester : l'inscription libre sur un royaume qui garde des positions
   de ruchers n'est pas envisageable. Aujourd'hui, un compte naît donc dans la
   console d'administration de Keycloak, avec pose manuelle de l'attribut
   `tenant_id` et du rôle. Cela ne passe pas l'échelle d'une seule exploitation.

La demande est explicite : des écrans de connexion et d'inscription **dans**
l'application, l'appel au fournisseur se faisant en arrière-plan.

---

## Décision

Zümm sert ses propres écrans. Le BFF expose deux routes ouvertes,
`POST /bff/connexion` et `POST /bff/inscription`, et appelle Keycloak lui-même :

- **connexion** — échange direct (`grant_type=password`) sur le client
  confidentiel `zumm-bff`. Les jetons obtenus restent côté serveur ; le navigateur
  ne repart qu'avec le cookie `HttpOnly` habituel ;
- **inscription** — API d'administration du royaume, avec le compte de service de
  `zumm-bff` porteur du seul rôle `manage-users`. Le rattachement vient d'un
  **code d'invitation** (V18), qui porte le `tenant_id` et le rôle du futur
  compte.

La redirection vers Keycloak **reste offerte** sur le même écran.

---

## Ce que cela coûte

Il faut le dire sans l'atténuer : **le mot de passe traverse désormais la page**,
ce que la redirection évitait. Trois conséquences en découlent.

- **L'échange direct est déconseillé par OAuth 2.1**, et Keycloak le signale
  comme tel. Il court-circuite tout ce que le fournisseur sait faire après la
  saisie : second facteur, écran de mot de passe expiré, consentement,
  vérification d'adresse.
- **La fédération n'y passe pas.** « Continuer avec Google » reste une
  redirection, et c'est irréductible : demander à l'utilisateur son mot de passe
  Google dans notre formulaire serait exactement le comportement que le hameçonnage
  imite.
- **Le formulaire devient une cible.** Un point d'entrée ouvert qui teste des mots
  de passe appelle une limitation de débit ; elle est déléguée à la protection
  contre le bourrage de Keycloak (`bruteForceProtected`), et non réécrite.

**Ce qui n'est pas perdu** : aucun jeton n'entre dans le navigateur. L'invariant
central de l'ADR-006 — une XSS ne repart avec rien de réutilisable ailleurs —
tient toujours, puisque la session reste un cookie que le script ne peut pas lire.
Ce qui change, c'est la fenêtre pendant laquelle une XSS pourrait lire un champ de
saisie ; elle est bornée à l'écran de connexion, et la CSP reste la parade.

---

## Alternatives écartées

| Option | Pourquoi non |
|---|---|
| **A. Statu quo** — redirection seule | Ne répond ni à la rupture de marque, ni à la création de comptes. |
| **B. Thème Keycloak aux couleurs de Zümm** | Répare l'apparence, pas le parcours : toujours une navigation hors du domaine, et toujours pas d'inscription rattachée. Reste une piste **complémentaire** pour le chemin fédéré, qui lui ne peut pas être ramené dans l'application. |
| **C. Formulaire posté au backend, qui stocke ses propres mots de passe** | Réintroduit une base de mots de passe dans Zümm : hachage, rotation, réinitialisation, verrouillage — tout ce que déléguer à Keycloak avait supprimé. Non. |
| **D. Inscription libre, rattachement ensuite** | Un compte sans `tenant_id` est refusé par `TenantFilter` sur chaque écran : l'utilisateur s'inscrirait pour arriver sur un mur. Et un royaume ouvert à l'inscription est une surface à lui seul. |

---

## Conséquences

- `infra/keycloak/realm-zumm{,.dev}.json` : `directAccessGrantsEnabled` et
  `serviceAccountsEnabled` passent à `true` sur `zumm-bff`, dont le compte de
  service reçoit `realm-management:manage-users`. **Les deux royaumes**, comme
  pour le mapper `tenant_id`.
- `registrationAllowed` **reste à `false`** : l'inscription passe par Zümm et un
  code, jamais par les pages du fournisseur.
- V18 introduit `code_invitation`, sa politique RLS, et deux fonctions
  `SECURITY DEFINER` — seul chemin de lecture hors contexte de tenant, puisqu'un
  code se résout par définition avant toute authentification.
- Le secret du client BFF devient sensible **à deux titres** : il signait déjà le
  flux par redirection, il autorise désormais la création de comptes. Sa fuite
  vaut création d'un compte dans n'importe quelle exploitation dont on connaît un
  code.
- À prévoir, et non couvert ici : l'écran de gestion des codes d'invitation côté
  responsable, la vérification d'adresse électronique, et le second facteur — qui
  demandera de router ces comptes-là vers la redirection.
