import type { ReactElement } from 'react';
import { useT } from '../i18n/langue';
import { Bouton } from '../ui/composants';

/**
 * Écran servi sur une panne du serveur — statut 5xx (SPRINT-19).
 *
 * <p><strong>Pourquoi le distinguer d'une erreur ordinaire.</strong> Le bandeau
 * générique affiche le détail renvoyé par le serveur. Sur un 500, ce détail ne
 * dit rien d'utile à un apiculteur — et sur un serveur vraiment tombé, il n'y a
 * même pas de détail à afficher. Cet écran énonce ce qui est vrai : la panne
 * est côté serveur, la saisie n'est pas en cause, réessayer a du sens.
 *
 * <p><strong>Il ne dépend d'aucune donnée</strong> — c'est sa raison d'être. Un
 * écran de panne qui a besoin de l'API pour s'afficher ne s'affiche jamais au
 * moment où on en a besoin.
 *
 * <p><strong>Il ne sert PAS au mode hors ligne</strong>, et c'est une décision.
 * Zümm est utilisable au rucher sans réseau : couper l'écran quand la
 * connexion tombe retirerait précisément ce que la PWA apporte. Hors ligne, la
 * barre supérieure signale l'état et la file d'attente prend le relais.
 */
export function PanneVue({ onReessayer }: { onReessayer: () => void }): ReactElement {
  const t = useT();

  return (
    <section className="z-section">
      <header className="z-section__entete">
        <h1 className="z-section__titre">{t.panne.titre}</h1>
      </header>
      <p className="z-info">{t.panne.explication}</p>
      <Bouton variante="primaire" onClick={onReessayer}>
        {t.actions.reessayer}
      </Bouton>
    </section>
  );
}
