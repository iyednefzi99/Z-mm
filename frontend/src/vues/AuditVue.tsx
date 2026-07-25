import { useEffect, useState, type ReactElement } from 'react';
import { chargerAudit } from '../api/client';
import type { AuditEntree } from '../api/types';
import { useFormats, useT } from '../i18n/langue';
import { messageErreur } from '../hooks';

/**
 * Journal d'audit (US-043, SPRINT-09) : « qui a fait quoi, quand ». Lecture seule,
 * réservée aux profils responsable / admin (le back-end renvoie 403 sinon).
 */
export function AuditVue(): ReactElement {
  const t = useT();
  const indisponible = t.etats.serviceIndisponible;
  const f = useFormats();
  const [entrees, setEntrees] = useState<AuditEntree[]>([]);
  const [erreur, setErreur] = useState<string | null>(null);

  useEffect(() => {
    setErreur(null);
    void chargerAudit().then(setEntrees).catch((c) => setErreur(messageErreur(c, indisponible)));
  }, [indisponible]);

  return (
    <section className="z-section">
      <header className="z-section__entete">
        <h1 className="z-section__titre">{t.audit.titre}</h1>
      </header>

      {erreur && (
        <div className="z-erreur" role="alert">
          <span>{erreur}</span>
        </div>
      )}

      {!erreur && entrees.length === 0 ? (
        <p className="z-info">{t.etats.vide}</p>
      ) : (
        <div className="z-table-enveloppe">
          <table className="z-table">
            <thead>
              <tr>
                <th>{t.audit.instant}</th>
                <th>{t.audit.acteur}</th>
                <th>{t.audit.action}</th>
                <th>{t.audit.entite}</th>
                <th>{t.audit.resume}</th>
              </tr>
            </thead>
            <tbody>
              {entrees.map((e) => (
                <tr key={e.id}>
                  <td>{f.dateHeure(e.instant)}</td>
                  <td>{e.acteur}</td>
                  <td>{t.audit.actions[e.action]}</td>
                  <td>{e.entite}{e.entiteId != null ? ` #${e.entiteId}` : ''}</td>
                  <td>{e.resume ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
