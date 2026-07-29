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
  sousTitre,
  actions,
  etat,
  onNouveau,
  children,
}: {
  titre: string;
  /** Une phrase qui situe l'ecran. Omise, l'en-tete reste comme avant. */
  sousTitre?: string;
  /** Commandes propres a l'ecran (export, filtre), posees avant « Nouveau ». */
  actions?: ReactNode;
  etat: EtatSection;
  onNouveau: () => void;
  children: ReactNode;
}): ReactElement {
  const t = useT();
  const vide = !etat.chargement && !etat.erreur && etat.elements.length === 0;

  /**
   * Volume de la liste.
   *
   * <p>`total` quand la liste est paginee — sinon le compteur dirait « 20 » sur
   * un parc de trois cents ruches, ce qui est pire que pas de compteur. Masque
   * pendant le chargement et sur une liste vide : l'etat vide porte deja le
   * message, et un « 0 » a cote du titre le repete sans rien ajouter.
   */
  const volume = etat.total ?? etat.elements.length;
  const afficherVolume = !etat.chargement && !etat.erreur && volume > 0;

  return (
    // `aria-busy` : l'assistance technique sait que le contenu est en cours de
    // remplacement, et n'annonce pas une liste vide qui n'en est pas une.
    <section className="z-section" aria-busy={etat.chargement}>
      <header className="z-section__entete">
        <div>
          <div className="z-section__ligne-titre">
            <h1 className="z-section__titre">{titre}</h1>
            {afficherVolume && (
              // Le compteur appartient au titre, pas a la liste : il repond a
              // « combien en ai-je ? » avant que l'oeil descende dans le tableau.
              <span className="z-section__volume">{volume}</span>
            )}
          </div>
          {sousTitre && <p className="z-section__soustitre">{sousTitre}</p>}
        </div>
        <div className="z-section__actions">
          {actions}
          <Bouton variante="primaire" onClick={onNouveau}>
            + {t.actions.nouveau}
          </Bouton>
        </div>
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
