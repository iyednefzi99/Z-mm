import type { ReactElement, ReactNode } from 'react';
import { useT } from '../i18n/langue';
import { Bouton } from '../ui/composants';

/** Etat minimal attendu par la section (sous-ensemble de EtatRessource). */
export interface EtatSection {
  chargement: boolean;
  erreur: string | null;
  elements: unknown[];
  recharger: () => void;
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
    <section className="z-section">
      <header className="z-section__entete">
        <h1 className="z-section__titre">{titre}</h1>
        <Bouton variante="primaire" onClick={onNouveau}>
          + {t.actions.nouveau}
        </Bouton>
      </header>

      {etat.chargement && (
        <p className="z-info" role="status">
          {t.etats.chargement}
        </p>
      )}
      {etat.erreur && (
        <div className="z-erreur" role="alert">
          <span>{etat.erreur}</span>
          <Bouton onClick={etat.recharger}>{t.actions.reessayer}</Bouton>
        </div>
      )}
      {vide && <p className="z-info">{t.etats.vide}</p>}

      {children}
    </section>
  );
}
