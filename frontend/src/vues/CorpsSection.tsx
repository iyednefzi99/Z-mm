import type { ReactElement, ReactNode } from 'react';
import { useT } from '../i18n/langue';
import { Bouton, EtatVide, Pagination, Squelette } from '../ui/composants';

/** Etat minimal attendu par la section (sous-ensemble de EtatRessource). */
export interface EtatSection {
  chargement: boolean;
  erreur: string | null;
  elements: unknown[];
  recharger: () => void;
  /** Pagination (US-052) : optionnelle, la barre ne s'affiche que si elle est la. */
  page?: number;
  taille?: number;
  total?: number;
  allerPage?: (page: number) => void;
}

/**
 * Ossature commune d'une section CRUD : titre, bouton « Nouveau », et gestion
 * uniforme des etats chargement / erreur / liste vide. Le contenu (table et
 * modale) est toujours rendu — la table conditionne son propre affichage a la
 * presence d'elements, afin que la modale de creation reste ouvrable a vide.
 */
export function CorpsSection({
  titre,
  etat,
  onNouveau,
  children,
}: {
  titre: string;
  etat: EtatSection;
  onNouveau: () => void;
  children: ReactNode;
}): ReactElement {
  const t = useT();
  const vide = !etat.chargement && !etat.erreur && etat.elements.length === 0;

  return (
    // `aria-busy` : l'assistance technique sait que le contenu est en cours de
    // remplacement, et n'annonce pas une liste vide qui n'en est pas une.
    <section className="z-section" aria-busy={etat.chargement}>
      <header className="z-section__entete">
        <h1 className="z-section__titre">{titre}</h1>
        <Bouton variante="primaire" onClick={onNouveau}>
          + {t.actions.nouveau}
        </Bouton>
      </header>

      {etat.chargement && (
        <>
          {/* Le squelette est muet (`aria-hidden`) : à l'œil il dit déjà tout, mais
              il n'a rien à annoncer. L'attente est donc énoncée une seule fois,
              ici, pour les lecteurs d'écran — et une seule fois seulement, sans
              doubler visuellement le squelette. */}
          <p className="z-visuellement-cache" role="status">
            {t.etats.chargement}
          </p>
          <Squelette />
        </>
      )}
      {etat.erreur && (
        <div className="z-erreur" role="alert">
          <span>{etat.erreur}</span>
          <Bouton onClick={etat.recharger}>{t.actions.reessayer}</Bouton>
        </div>
      )}
      {vide && (
        <EtatVide
          titre={t.etats.videTitre}
          texte={t.etats.videTexte}
          action={
            <Bouton variante="primaire" onClick={onNouveau}>
              + {t.actions.nouveau}
            </Bouton>
          }
        />
      )}

      {children}

      {etat.allerPage !== undefined &&
        etat.page !== undefined &&
        etat.taille !== undefined &&
        etat.total !== undefined && (
          <Pagination
            page={etat.page}
            taille={etat.taille}
            total={etat.total}
            onPage={etat.allerPage}
          />
        )}
    </section>
  );
}
