import type { ReactElement } from 'react';
import { useT } from '../i18n/langue';
import { Bouton } from '../ui/composants';

/**
 * Écran servi sur une URL inconnue (US-051, SPRINT-11).
 *
 * <p>Une adresse fausse — lien partagé mal recopié, favori devenu caduc — doit se
 * voir. Retomber silencieusement sur le premier onglet laisserait croire que le
 * lien a fonctionné.
 */
export function IntrouvableVue({ onRetour }: { onRetour: () => void }): ReactElement {
  const t = useT();

  return (
    <section className="z-section">
      <header className="z-section__entete">
        <h1 className="z-section__titre">{t.introuvable.titre}</h1>
      </header>
      <p className="z-info">{t.introuvable.explication}</p>
      <Bouton variante="primaire" onClick={onRetour}>
        {t.introuvable.retour}
      </Bouton>
    </section>
  );
}
