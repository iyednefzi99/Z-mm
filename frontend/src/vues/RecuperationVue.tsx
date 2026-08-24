import type { ReactElement } from 'react';
import { useT } from '../i18n/langue';
import { PageLegale } from './PageLegale';

/**
 * Page « Mot de passe oublié » (SPRINT-19, lot 4).
 *
 * <p><strong>Elle aiguille, elle ne traite pas.</strong> La réinitialisation de
 * mot de passe appartient au service d'authentification ; la redévelopper ici
 * reviendrait à réimplémenter, moins bien, ce qui existe déjà — et à faire
 * transiter des secrets par une couche de plus.
 *
 * <p><strong>Elle n'affiche même pas de lien « recevoir un courriel ».</strong>
 * Vérification faite dans `infra/keycloak/realm-zumm{,.dev}.json` : aucun
 * serveur d'envoi n'y est configuré. Le lien existerait, le message ne partirait
 * pas, et l'utilisateur attendrait un courriel qui n'arrive jamais — le pire des
 * trois états possibles. Le manque est donc porté par le bandeau, et le seul
 * chemin qui fonctionne aujourd'hui est nommé : le responsable d'exploitation.
 */
export function RecuperationVue(): ReactElement {
  const t = useT();

  return (
    <PageLegale
      titre={t.recuperation.titre}
      chapo={t.recuperation.chapo}
      maj={t.recuperation.maj}
      manques={t.recuperation.manques}
      sections={t.recuperation.sections}
    />
  );
}
