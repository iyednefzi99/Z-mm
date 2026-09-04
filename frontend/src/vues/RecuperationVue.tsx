import { useEffect, useState, type ReactElement } from 'react';
import { chargerInfo } from '../api/client';
import { useLangue, useT } from '../i18n/langue';
import { PageLegale } from './PageLegale';

/**
 * Page « Mot de passe oublié » (SPRINT-19, lot 4 ; libre-service au SPRINT-25).
 *
 * <p><strong>Elle aiguille, elle ne traite pas.</strong> La réinitialisation de
 * mot de passe appartient au service d'authentification ; la redévelopper ici
 * reviendrait à réimplémenter, moins bien, ce qui existe déjà — et à faire
 * transiter des secrets par une couche de plus.
 *
 * <p><strong>Le lien n'apparaît que s'il mène quelque part.</strong> Le realm
 * livré ne configure aucun serveur d'envoi : un lien affiché malgré tout
 * conduirait à un formulaire qui accepte l'adresse et n'envoie rien, et
 * l'utilisateur attendrait un courriel qui n'arrive jamais — le pire des trois
 * états possibles. L'exploitant qui a configuré son SMTP renseigne
 * `ZUMM_AUTH_REINITIALISATION_URL` (voir `infra/keycloak/README.md`), et le
 * lien paraît. Sinon, le seul chemin qui fonctionne reste nommé : le
 * responsable d'exploitation.
 */
export function RecuperationVue(): ReactElement {
  const t = useT();
  const { langue } = useLangue();
  const [url, setUrl] = useState('');

  useEffect(() => {
    // `/api/info` est public : celui qui a oublié son mot de passe n'est, par
    // construction, pas connecté.
    void chargerInfo(langue)
      .then((info) => setUrl(info.reinitialisationUrl ?? ''))
      .catch(() => setUrl(''));
  }, [langue]);

  return (
    <PageLegale
      titre={t.recuperation.titre}
      chapo={t.recuperation.chapo}
      maj={t.recuperation.maj}
      manques={url === '' ? t.recuperation.manques : []}
      sections={t.recuperation.sections}
    >
      {url !== '' && (
        <p>
          <a className="z-lien" href={url} rel="noreferrer">
            {t.recuperation.libreService}
          </a>
        </p>
      )}
    </PageLegale>
  );
}
