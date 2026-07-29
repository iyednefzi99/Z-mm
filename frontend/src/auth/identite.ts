/**
 * Connexion et inscription menées depuis l'application.
 *
 * <p>Zümm présente ses propres écrans plutôt que ceux de Keycloak : l'appel au
 * fournisseur se fait EN ARRIÈRE-PLAN, par le BFF. Le navigateur poste des
 * identifiants sur sa propre origine et reçoit, en cas de succès, le même cookie
 * de session {@code HttpOnly} que le flux par redirection — l'invariant central
 * de l'ADR-006 tient donc toujours : <strong>aucun jeton n'entre dans le
 * navigateur</strong>.
 *
 * <p>Ce que ce choix coûte, et qu'il faut connaître : le mot de passe traverse
 * cette page, ce que la redirection évitait. La contrepartie est assumée
 * (ADR-009). Trois conséquences pratiques ici :
 *
 * <ul>
 *   <li>le mot de passe n'est JAMAIS mémorisé — ni variable de module, ni
 *       {@code sessionStorage}, ni journal. Il vit le temps d'un appel ;
 *   <li>les échecs de connexion sont volontairement indistincts : « identifiants
 *       invalides » ne dit pas si c'est le compte ou le mot de passe qui est en
 *       cause, sans quoi le formulaire devient un oracle d'existence de comptes ;
 *   <li>la fédération (Google) reste une redirection : un fournisseur externe ne
 *       peut pas s'authentifier en arrière-plan, et le tenter reviendrait à
 *       demander à l'utilisateur son mot de passe Google — jamais.
 * </ul>
 */

import { jetonCsrf } from '../api/client';
import { rafraichirSession, type Session } from './session';

/** Points d'entrée du BFF. Même origine : le cookie de session suit tout seul. */
const CONNEXION = '/bff/connexion';
const INSCRIPTION = '/bff/inscription';

/**
 * Causes d'échec que l'interface sait traduire.
 *
 * <p>Un code fermé plutôt qu'un message du serveur : le texte affiché est choisi
 * par la PWA, donc traduit dans les trois langues, et le serveur ne peut pas
 * injecter de contenu dans la page.
 */
export type EchecIdentite =
  | 'identifiants-invalides'
  | 'compte-suspendu'
  | 'code-inconnu'
  | 'courriel-deja-pris'
  | 'mot-de-passe-refuse'
  | 'trop-de-tentatives'
  | 'indisponible';

/** Échec d'identité, porteur d'un code que l'interface traduit. */
export class ErreurIdentite extends Error {
  constructor(readonly code: EchecIdentite) {
    super(code);
    this.name = 'ErreurIdentite';
  }
}

/** Champs demandés à la création de compte. */
export interface DemandeInscription {
  nom: string;
  courriel: string;
  motDePasse: string;
  /** Code d'exploitation remis par le responsable : il porte le rattachement. */
  code: string;
}

/**
 * Poste sur le BFF avec le jeton CSRF.
 *
 * <p>Ces deux endpoints sont ouverts — il faut bien pouvoir se connecter sans
 * être connecté — mais ils restent des mutations sur une origine à cookie : sans
 * jeton CSRF, un site tiers pourrait déclencher une tentative de connexion ou
 * consommer un code d'invitation à l'insu de l'utilisateur.
 */
async function poster(url: string, corps: unknown): Promise<Response> {
  const jeton = jetonCsrf();
  return fetch(url, {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json',
      ...(jeton ? { 'X-XSRF-TOKEN': jeton } : {}),
    },
    body: JSON.stringify(corps),
  });
}

/**
 * Traduit une réponse en échec.
 *
 * <p>Le code du serveur n'est retenu que s'il fait partie de ceux que
 * l'interface sait dire ; tout le reste devient « indisponible ». Une réponse
 * inattendue ne doit pas produire un écran muet.
 */
async function echec(reponse: Response): Promise<ErreurIdentite> {
  const connus: EchecIdentite[] = [
    'identifiants-invalides',
    'compte-suspendu',
    'code-inconnu',
    'courriel-deja-pris',
    'mot-de-passe-refuse',
    'trop-de-tentatives',
  ];
  const corps = (await reponse.json().catch(() => null)) as { code?: string } | null;
  const code = connus.find((connu) => connu === corps?.code);
  if (code) {
    return new ErreurIdentite(code);
  }
  // 401 sans code exploitable : le cas de loin le plus fréquent reste un
  // mauvais couple identifiant/mot de passe.
  return new ErreurIdentite(reponse.status === 401 ? 'identifiants-invalides' : 'indisponible');
}

/**
 * Ouvre une session à partir d'un identifiant et d'un mot de passe.
 *
 * <p>Le BFF échange ces identifiants contre des jetons auprès de Keycloak, les
 * garde, et ne renvoie qu'un cookie. La session est ensuite relue par le canal
 * normal ({@code /bff/session}) : un seul chemin de vérité sur « qui est
 * connecté », que l'on soit entré par ce formulaire ou par la redirection.
 */
export async function connexion(identifiant: string, motDePasse: string): Promise<Session> {
  let reponse: Response;
  try {
    reponse = await poster(CONNEXION, { identifiant, motDePasse });
  } catch {
    // Serveur injoignable : distinct d'un refus, et l'utilisateur doit le savoir
    // — réessayer son mot de passe n'y changerait rien.
    throw new ErreurIdentite('indisponible');
  }
  if (!reponse.ok) {
    throw await echec(reponse);
  }
  const session = await rafraichirSession();
  if (!session) {
    // Cookie posé mais session illisible : incohérent, on ne prétend pas que
    // l'utilisateur est entré.
    throw new ErreurIdentite('indisponible');
  }
  return session;
}

/**
 * Crée un compte, puis ouvre la session dans la foulée.
 *
 * <p>Le code d'exploitation n'est pas une formalité : c'est lui qui porte le
 * {@code tenant_id} du futur compte. Sans rattachement, le jeton serait refusé
 * par {@code TenantFilter} et le compte, bien que créé, n'atteindrait aucun
 * écran — un compte fantôme est pire qu'une inscription refusée.
 */
export async function inscription(demande: DemandeInscription): Promise<Session> {
  let reponse: Response;
  try {
    reponse = await poster(INSCRIPTION, demande);
  } catch {
    throw new ErreurIdentite('indisponible');
  }
  if (!reponse.ok) {
    throw await echec(reponse);
  }
  // Enchaîner la connexion évite de renvoyer l'utilisateur ressaisir ce qu'il
  // vient de taper. En cas d'échec ici, l'erreur remonte telle quelle : le
  // compte EXISTE, et le formulaire de connexion le prendra.
  return connexion(demande.courriel, demande.motDePasse);
}
