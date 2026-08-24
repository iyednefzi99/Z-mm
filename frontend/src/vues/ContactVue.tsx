import type { ReactElement } from 'react';
import { useT } from '../i18n/langue';
import { PageLegale } from './PageLegale';

/**
 * Page « Contact » (SPRINT-19).
 *
 * <p><strong>Pourquoi il n'y a pas de formulaire.</strong> Aucun contrôleur ne
 * reçoit de message : il n'existe pas d'endpoint de contact dans le back, et
 * aucun serveur SMTP n'est configuré dans les deux realms. Un formulaire posé
 * ici n'enverrait rien, et l'utilisateur repartirait convaincu d'avoir été
 * entendu. C'est le seul cas où ne rien construire est la fonctionnalité.
 *
 * <p>La page aiguille donc vers les interlocuteurs qui existent réellement — le
 * responsable de l'exploitation, seul à pouvoir émettre un code, attribuer un
 * rôle ou relancer un mot de passe — et inscrit au bandeau des manques ce qui
 * reste à ouvrir : une adresse d'éditeur et un canal de signalement.
 */
export function ContactVue(): ReactElement {
  const t = useT();

  return (
    <PageLegale
      titre={t.contact.titre}
      chapo={t.contact.chapo}
      maj={t.contact.maj}
      manques={t.contact.manques}
      sections={t.contact.sections}
    />
  );
}
