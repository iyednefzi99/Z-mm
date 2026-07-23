import { useCallback, useEffect, useState } from 'react';
import { ErreurApi } from './api/client';

/** Message lisible a partir d'une erreur d'API ou reseau. */
export function messageErreur(cause: unknown): string {
  if (cause instanceof ErreurApi) {
    return cause.detail;
  }
  return 'Service indisponible. Vérifiez votre connexion.';
}

/** Contrat CRUD minimal d'une ressource, tel qu'expose par le client d'API. */
export interface ApiRessource<E, C> {
  lister: () => Promise<E[]>;
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
}

/**
 * Gere l'etat d'une liste CRUD : chargement, erreurs, et rechargement apres
 * chaque mutation. L'objet {@code api} est un module stable (reference constante).
 */
export function useRessource<E extends { id: number }, C>(
  api: ApiRessource<E, C>,
): EtatRessource<E, C> {
  const [elements, setElements] = useState<E[]>([]);
  const [chargement, setChargement] = useState(true);
  const [erreur, setErreur] = useState<string | null>(null);

  const recharger = useCallback(() => {
    setChargement(true);
    setErreur(null);
    api
      .lister()
      .then(setElements)
      .catch((cause: unknown) => setErreur(messageErreur(cause)))
      .finally(() => setChargement(false));
  }, [api]);

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
  };
}
