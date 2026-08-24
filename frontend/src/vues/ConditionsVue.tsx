import type { ReactElement } from 'react';
import { useT } from '../i18n/langue';
import { PageLegale } from './PageLegale';

/**
 * Conditions générales d'utilisation (SPRINT-19).
 *
 * <p>Ce qui est écrit ici décrit le <strong>logiciel tel qu'il est dans le
 * dépôt</strong> : l'entrée par code d'exploitation, les quatre rôles humains,
 * le minimum de douze caractères imposé au mot de passe, le mode hors ligne qui
 * conserve les saisies localement. Rien qui ne se vérifie dans le code.
 *
 * <p>Ce qui relève de l'<strong>exploitant</strong> du service — qui édite, qui
 * héberge, sous quel droit, avec quelle disponibilité garantie — n'est pas dans
 * le dépôt et n'est donc pas écrit : c'est le bandeau des manques qui le porte.
 */
export function ConditionsVue(): ReactElement {
  const t = useT();

  return (
    <PageLegale
      titre={t.cgu.titre}
      chapo={t.cgu.chapo}
      maj={t.cgu.maj}
      manques={t.cgu.manques}
      sections={t.cgu.sections}
    />
  );
}
