import { useEffect, useRef, useState, type ReactElement } from 'react';
import { jetonCsrf } from '../api/client';
import { deconnexion } from '../auth/oidc';
import type { Session } from '../auth/session';
import { useT } from '../i18n/langue';

/**
 * Menu de profil de la barre supérieure.
 *
 * <p>Il remplace un bouton « Se déconnecter » posé seul. Ce n'est pas qu'une
 * question d'apparence : la barre n'affichait <strong>nulle part</strong> QUI est
 * connecté ni pour quelle exploitation. Sur un produit multi-exploitation, c'est
 * l'information dont l'absence coûte le plus cher — saisir une récolte dans le
 * mauvais cheptel se découvre trop tard.
 *
 * <p>Les rôles sont affichés parce qu'ils expliquent l'interface : un apiculteur
 * qui ne voit pas l'écran des invitations doit pouvoir comprendre pourquoi sans
 * appeler son responsable.
 */
export function MenuProfil({ session }: { session: Session }): ReactElement {
  const t = useT();
  const [ouvert, setOuvert] = useState(false);
  const conteneur = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!ouvert) {
      return;
    }
    // Échap et clic extérieur : les deux sorties qu'un utilisateur essaie sans
    // y penser. Un menu qui ne se ferme qu'en recliquant sur son bouton est un
    // piège au clavier.
    const auClavier = (evenement: KeyboardEvent) => {
      if (evenement.key === 'Escape') {
        setOuvert(false);
      }
    };
    const auClic = (evenement: MouseEvent) => {
      if (!conteneur.current?.contains(evenement.target as Node)) {
        setOuvert(false);
      }
    };
    document.addEventListener('keydown', auClavier);
    document.addEventListener('mousedown', auClic);
    return () => {
      document.removeEventListener('keydown', auClavier);
      document.removeEventListener('mousedown', auClic);
    };
  }, [ouvert]);

  /** Deux lettres tirées du nom : ni photo à charger, ni service tiers. */
  const initiales = session.utilisateur
    .split(/[\s.@_-]+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((morceau) => morceau[0]?.toUpperCase() ?? '')
    .join('');

  return (
    <div className="z-profil" ref={conteneur}>
      <button
        type="button"
        className="z-profil__declencheur"
        aria-expanded={ouvert}
        aria-haspopup="menu"
        onClick={() => setOuvert((etat) => !etat)}
      >
        <span className="z-profil__pastille" aria-hidden="true">
          {initiales}
        </span>
        <span className="z-profil__nom">{session.utilisateur}</span>
      </button>

      {ouvert && (
        <div className="z-profil__menu" role="menu">
          <p className="z-profil__ligne">
            <span className="z-profil__cle">{t.profil.exploitation}</span>
            <span className="z-profil__valeur">{session.exploitation}</span>
          </p>
          <p className="z-profil__ligne">
            <span className="z-profil__cle">{t.profil.roles}</span>
            <span className="z-profil__valeur">
              {session.roles.length === 0
                ? t.profil.sansRole
                : session.roles.map((role) => t.roles[role as keyof typeof t.roles] ?? role).join(', ')}
            </span>
          </p>
          <button
            type="button"
            role="menuitem"
            className="z-profil__action"
            onClick={() => void deconnexion(jetonCsrf())}
          >
            {t.actions.seDeconnecter}
          </button>
        </div>
      )}
    </div>
  );
}
