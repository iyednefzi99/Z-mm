import { useCallback, useEffect, useState } from 'react';
import { ErreurApi, type PageResultat } from './api/client';
import { sessionCourante, surSession } from './auth/session';
import { useT } from './i18n/langue';
import { useToasts } from './ui/toasts';

/**
 * Message lisible a partir d'une erreur d'API ou reseau.
 *
 * <p>Le detail d'une {@link ErreurApi} vient du serveur (ProblemDetail) ; le repli
 * reseau, lui, est une chaine de l'interface et doit donc etre traduit — d'ou le
 * parametre, plutot qu'un francais en dur (US-053).
 */
export function messageErreur(cause: unknown, replReseau: string): string {
  if (cause instanceof ErreurApi) {
    return cause.detail;
  }
  return replReseau;
}

/** Contrat CRUD minimal d'une ressource, tel qu'expose par le client d'API. */
export interface ApiRessource<E, C> {
  lister: () => Promise<E[]>;
  listerPage?: (page: number, taille: number) => Promise<PageResultat<E>>;
  creer: (corps: C) => Promise<E>;
  mettreAJour: (id: number, corps: C) => Promise<E>;
  supprimer: (id: number) => Promise<void>;
}

export interface EtatRessource<E, C> {
  elements: E[];
  chargement: boolean;
  erreur: string | null;
  /**
   * Statut HTTP du dernier echec, `null` sinon (SPRINT-19).
   *
   * <p>`erreur` aplatit tout en une phrase, ce qui suffit a l'afficher mais pas
   * a decider quoi afficher : un refus de role et une panne de serveur ne se
   * traitent pas pareil, et ni l'un ni l'autre ne se resout en relisant le
   * message. Le statut est donc conserve a cote du texte.
   */
  statut: number | null;
  recharger: () => void;
  creer: (corps: C) => Promise<void>;
  mettreAJour: (id: number, corps: C) => Promise<void>;
  supprimer: (id: number) => Promise<void>;
  /** Pagination (US-052) : absente si la ressource ne la propose pas. */
  page: number;
  taille: number;
  total: number;
  allerPage: (page: number) => void;
}

/** Taille de page du client. Le serveur impose la sienne par defaut, et plafonne. */
const TAILLE_PAGE = 25;

/**
 * Gere l'etat d'une liste CRUD : chargement, erreurs, et rechargement apres
 * chaque mutation. L'objet {@code api} est un module stable (reference constante).
 */
export function useRessource<E extends { id: number }, C>(
  api: ApiRessource<E, C>,
): EtatRessource<E, C> {
  const t = useT();
  const toasts = useToasts();
  const [elements, setElements] = useState<E[]>([]);
  const [chargement, setChargement] = useState(true);
  const [erreur, setErreur] = useState<string | null>(null);
  const [statut, setStatut] = useState<number | null>(null);
  const [page, setPage] = useState(0);
  const [total, setTotal] = useState(0);
  const indisponible = t.etats.serviceIndisponible;

  const recharger = useCallback(() => {
    setChargement(true);
    setErreur(null);
    setStatut(null);
    const chargement = api.listerPage
      ? api.listerPage(page, TAILLE_PAGE).then((p: PageResultat<E>) => {
          setElements(p.elements);
          setTotal(p.total);
        })
      : api.lister().then((liste) => {
          setElements(liste);
          setTotal(liste.length);
        });
    chargement
      .catch((cause: unknown) => {
        setErreur(messageErreur(cause, indisponible));
        setStatut(cause instanceof ErreurApi ? cause.statut : null);
      })
      .finally(() => setChargement(false));
  }, [api, indisponible, page]);

  useEffect(recharger, [recharger]);

  /**
   * Joue une mutation, recharge, et signale l'issue.
   *
   * <p>L'erreur est **re-levée** après avoir été signalée : la vue appelante en a
   * besoin pour décider si elle garde sa modale ouverte. L'avaler ici fermerait
   * le formulaire sur un échec, et l'utilisateur perdrait sa saisie en croyant
   * avoir enregistré.
   */
  const muter = async (
    operation: Promise<unknown>,
    succes: string,
    echec: string,
  ): Promise<void> => {
    try {
      await operation;
    } catch (cause: unknown) {
      toasts.erreur(messageErreur(cause, echec));
      throw cause;
    }
    toasts.succes(succes);
    recharger();
  };

  /**
   * Supprime, avec une fenêtre d'annulation.
   *
   * <p>La suppression n'est **pas** envoyée tout de suite : elle part à
   * l'expiration du délai, si rien ne l'a annulée. C'est ce qui fait la différence
   * entre annuler et recréer — un objet recréé changerait d'identifiant et
   * perdrait ses rattachements.
   *
   * <p>La ligne disparaît en revanche immédiatement de la liste : laisser à
   * l'écran quelque chose que l'utilisateur vient de supprimer lui ferait croire
   * que le geste n'a pas abouti, et l'inviterait à recommencer.
   */
  const supprimerAvecAnnulation = (id: number): Promise<void> => {
    const restants = elements.filter((element) => element.id !== id);
    setElements(restants);
    setTotal((precedent) => Math.max(0, precedent - 1));

    toasts.annulable(
      t.retours.supprime,
      () => {
        api
          .supprimer(id)
          .then(recharger)
          .catch((cause: unknown) => {
            toasts.erreur(messageErreur(cause, t.retours.echecSuppression));
            recharger();
          });
      },
      // Annulation : rien n'a été envoyé, il suffit de remettre la liste à jour
      // depuis le serveur — source de vérité, plutôt qu'une pile de défaire.
      recharger,
    );
    return Promise.resolve();
  };

  return {
    elements,
    chargement,
    erreur,
    statut,
    recharger,
    creer: (corps: C) => muter(api.creer(corps), t.retours.cree, t.retours.echecCreation),
    mettreAJour: (id: number, corps: C) =>
      muter(api.mettreAJour(id, corps), t.retours.modifie, t.retours.echecModification),
    supprimer: supprimerAvecAnnulation,
    page,
    taille: TAILLE_PAGE,
    total,
    allerPage: setPage,
  };
}

/**
 * Rôles de la session en cours, réévalués si elle change.
 *
 * <p>Les écrans sont chargés à la demande et ne reçoivent pas la session en
 * paramètre ; la faire descendre depuis `App` jusqu'à cinq vues du référentiel
 * imposerait une signature à toutes les autres pour rien. Le module de session
 * est déjà un magasin observable — ce crochet ne fait que s'y abonner.
 *
 * <p>Une session non encore connue rend un tableau vide, c'est-à-dire « aucun
 * rôle » : pendant ce laps, les commandes d'écriture ne s'affichent pas. Le
 * défaut prudent est le bon sens ici — proposer puis retirer serait pire que
 * d'afficher un instant plus tard.
 */
export function useRoles(): readonly string[] {
  const [roles, setRoles] = useState<readonly string[]>(sessionCourante()?.roles ?? []);
  useEffect(() => surSession((session) => setRoles(session?.roles ?? [])), []);
  return roles;
}
