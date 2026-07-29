import { useCallback, useEffect, useState, type ReactElement } from 'react';
import { emettreInvitation, listerInvitations, revoquerInvitation } from '../api/client';
import type { Invitation, InvitationCorps, RoleAgent } from '../api/types';
import { useT } from '../i18n/langue';
import { useDialogues } from '../ui/dialogues';
import { useToasts } from '../ui/toasts';
import {
  Bouton,
  ChampNombre,
  ChampSelect,
  EtatVide,
  Pastille,
  Squelette,
} from '../ui/composants';

/** Roles qu'une invitation peut attribuer. `admin` en est volontairement absent. */
const ROLES_INVITABLES: RoleAgent[] = ['apiculteur', 'superviseur', 'responsable'];

/**
 * Codes d'invitation de l'exploitation (US-058, ADR-009).
 *
 * <p>C'est le pendant du formulaire d'inscription : quelqu'un doit pouvoir
 * décider qui rejoint l'exploitation, et avec quel rôle. Sans cet écran, le
 * formulaire d'inscription serait une porte sans clef.
 *
 * <p>L'écran est réservé au responsable et à l'administrateur par la matrice
 * RBAC du serveur. Le masquer aux autres rôles côté client serait du confort ;
 * ce qui protège, c'est le 403.
 */
export function InvitationsVue(): ReactElement {
  const t = useT();
  const toasts = useToasts();
  const dialogues = useDialogues();
  const [codes, setCodes] = useState<Invitation[] | null>(null);
  const [role, setRole] = useState<RoleAgent>('apiculteur');
  const [places, setPlaces] = useState('1');
  const [jours, setJours] = useState('14');
  const [enCours, setEnCours] = useState(false);

  const charger = useCallback(async () => {
    setCodes(await listerInvitations());
  }, []);

  useEffect(() => {
    void charger();
  }, [charger]);

  async function emettre(): Promise<void> {
    setEnCours(true);
    const corps: InvitationCorps = {
      role,
      utilisationsMax: Math.max(1, Number(places) || 1),
      joursValidite: Math.max(1, Number(jours) || 14),
    };
    try {
      const cree = await emettreInvitation(corps);
      // Le code est ajouté en tête plutôt que rechargé : c'est celui qu'on vient
      // d'émettre qu'on cherche des yeux, et il doit apparaître là où le regard
      // se trouve déjà.
      setCodes((liste) => [cree, ...(liste ?? [])]);
      await copier(cree.code);
    } finally {
      setEnCours(false);
    }
  }

  /**
   * Copie le code dans le presse-papier.
   *
   * <p>Le presse-papier peut être refusé — contexte non sécurisé, permission
   * refusée — et l'échec doit rester silencieux : le code est affiché en clair
   * juste à côté, l'utilisateur peut toujours le lire.
   */
  async function copier(code: string): Promise<void> {
    try {
      await navigator.clipboard.writeText(code);
      toasts.succes(t.invitations.copie);
    } catch {
      // Le code reste lisible à l'écran : rien à signaler.
    }
  }

  async function revoquer(code: Invitation): Promise<void> {
    if (!(await dialogues.confirmer(t.invitations.confirmerRevocation))) {
      return;
    }
    await revoquerInvitation(code.id);
    setCodes((liste) => (liste ?? []).filter((autre) => autre.id !== code.id));
  }

  return (
    <section className="z-section">
      <header className="z-section__entete">
        <div>
          <h2 className="z-section__titre">{t.invitations.titre}</h2>
          <p className="z-section__soustitre">{t.invitations.sousTitre}</p>
        </div>
      </header>

      <form
        className="z-invitation__emission"
        onSubmit={(evenement) => {
          evenement.preventDefault();
          void emettre();
        }}
      >
        <ChampSelect
          libelle={t.invitations.role}
          valeur={role}
          onChange={(valeur) => setRole(valeur as RoleAgent)}
          options={ROLES_INVITABLES.map((cle) => ({ valeur: cle, libelle: t.roles[cle] }))}
        />
        <ChampNombre
          libelle={t.invitations.places}
          valeur={places}
          onChange={setPlaces}
          pas="1"
        />
        <ChampNombre
          libelle={t.invitations.validite}
          valeur={jours}
          onChange={setJours}
          pas="1"
        />
        <Bouton variante="primaire" type="submit" disabled={enCours}>
          {t.invitations.emettre}
        </Bouton>
      </form>

      {codes === null ? (
        <Squelette lignes={3} />
      ) : codes.length === 0 ? (
        <EtatVide pictogramme="🎟️" titre={t.invitations.vide} />
      ) : (
        <div className="z-table-conteneur">
          <table className="z-table">
            <thead>
              <tr>
                <th>{t.invitations.colonneCode}</th>
                <th>{t.invitations.colonneRole}</th>
                <th>{t.invitations.colonneUsage}</th>
                <th>{t.invitations.colonneExpiration}</th>
                <th>{t.invitations.colonneAuteur}</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {codes.map((code) => (
                <tr key={code.id}>
                  <td>
                    {/* Chasse fixe : un code se relit caractère par caractère. */}
                    <code className="z-code">{code.code}</code>
                  </td>
                  <td>{t.roles[code.role]}</td>
                  <td>
                    {code.utilisations} / {code.utilisationsMax}{' '}
                    <Pastille ton={code.epuise ? 'neutre' : 'succes'}>
                      {code.epuise ? t.invitations.epuise : t.invitations.actif}
                    </Pastille>
                  </td>
                  <td>{new Date(code.expireLe).toLocaleDateString()}</td>
                  <td>{code.creePar ?? '—'}</td>
                  <td className="z-table__actions">
                    <button
                      type="button"
                      className="z-lien"
                      onClick={() => void copier(code.code)}
                    >
                      {t.invitations.copier}
                    </button>
                    <button
                      type="button"
                      className="z-lien z-lien--danger"
                      onClick={() => void revoquer(code)}
                    >
                      {t.invitations.revoquer}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
