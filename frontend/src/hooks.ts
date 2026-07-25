import { useCallback, useEffect, useState } from 'react';
import { ErreurApi, type PageResultat } from './api/client';
import { useT } from './i18n/langue';

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
  const [elements, setElements] = useState<E[]>([]);
  const [chargement, setChargement] = useState(true);
  const [erreur, setErreur] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [total, setTotal] = useState(0);
  const indisponible = t.etats.serviceIndisponible;

  const recharger = useCallback(() => {
    setChargement(true);
    setErreur(null);
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
      .catch((cause: unknown) => setErreur(messageErreur(cause, indisponible)))
      .finally(() => setChargement(false));
  }, [api, indisponible, page]);

  useEffect(recharger, [recharger]);

  const muter = async (operation: Promise<unknown>): Promise<void> => {
    await operation;
    recharger();
  };

  return {
    elements,
    chargement,
    erreur,
    recharger,
    creer: (corps: C) => muter(api.creer(corps)),
    mettreAJour: (id: number, corps: C) => muter(api.mettreAJour(id, corps)),
    supprimer: (id: number) => muter(api.supprimer(id)),
    page,
    taille: TAILLE_PAGE,
    total,
    allerPage: setPage,
  };
}
