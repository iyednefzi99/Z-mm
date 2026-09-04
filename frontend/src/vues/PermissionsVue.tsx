import type { ReactElement } from 'react';
import { gabarit, type Traductions } from '../i18n/console';
import { useT } from '../i18n/langue';
import { ROLES_ONGLET, type Onglet } from '../routage/routes';

/** Clé d'une règle, telle qu'elle est libellée dans les traductions. */
type CleRegle = keyof Traductions['permissions']['regles'];

/**
 * La matrice, dans l'ordre du plus restreint au plus ouvert.
 *
 * <p>Elle recopie `SecurityConfig.matriceRbac`, règle pour règle. Les rôles sont
 * des <strong>identifiants</strong> et ne vivent donc pas dans les traductions :
 * seuls les libellés de règle y sont. Écrire « responsable, admin » en toutes
 * lettres dans trois fichiers de langue aurait été le meilleur moyen de voir la
 * matrice diverger d'une langue à l'autre.
 */
export const MATRICE: readonly { cle: CleRegle; roles: readonly string[] }[] = [
  { cle: 'audit', roles: ['responsable', 'admin'] },
  { cle: 'invitations', roles: ['responsable', 'admin'] },
  { cle: 'permissions', roles: ['responsable', 'admin'] },
  { cle: 'comptabilite', roles: ['responsable', 'admin'] },
  { cle: 'referentiel', roles: ['responsable', 'admin'] },
  { cle: 'plannings', roles: ['superviseur', 'responsable', 'admin'] },
  { cle: 'mesures', roles: ['capteur', 'apiculteur', 'superviseur', 'responsable', 'admin'] },
  { cle: 'reste', roles: ['apiculteur', 'superviseur', 'responsable', 'admin'] },
];

/**
 * Écran « Rôles et permissions » (SPRINT-19, lot 4).
 *
 * <p><strong>À quoi il sert.</strong> Depuis le lot 1, la navigation masque les
 * écrans qu'un rôle n'ouvre pas. C'est plus confortable qu'un 403, mais ça
 * déplace la question : l'utilisateur ne voit plus la porte, donc il ignore
 * qu'elle existe. Cet écran répond une fois pour toutes — voici les serrures,
 * voici qui a les clés, voilà quoi demander à son responsable.
 *
 * <p><strong>Ce qu'il n'est pas.</strong> Pas un écran d'administration : on n'y
 * modifie rien, et il n'a donc ni bouton « Nouveau » ni formulaire. Les rôles
 * s'attribuent à l'invitation ou depuis l'écran des agents ; la matrice, elle,
 * vit dans le code du serveur et ne se règle pas depuis un navigateur.
 *
 * <p><strong>La dérive possible, et ce qui la limite.</strong> Cette page
 * recopie une matrice qui vit côté serveur : aucun endpoint ne la publie, le
 * risque de divergence est donc réel. Deux garde-fous : la liste des écrans
 * retirés de la navigation est <strong>dérivée</strong> de {@link ROLES_ONGLET}
 * et non réécrite, et `PermissionsVue.test.tsx` échoue si la matrice affichée
 * cesse de couvrir cette table.
 */
export function PermissionsVue(): ReactElement {
  const t = useT();
  const p = t.permissions;

  const traduire = (role: string) => t.roles[role as keyof typeof t.roles] ?? role;
  const ecransReserves = (Object.keys(ROLES_ONGLET) as Onglet[])
    .map((onglet) => t.onglets[onglet])
    .join(', ');

  return (
    <section className="z-section">
      <header className="z-section__entete">
        <div>
          <h1 className="z-section__titre">{p.titre}</h1>
          <p className="z-section__soustitre">{p.sousTitre}</p>
        </div>
      </header>

      <p className="z-info">{p.intro}</p>

      <div className="z-table-enveloppe">
        <table className="z-table">
          <thead>
            <tr>
              <th>{p.colonneRegle}</th>
              <th>{p.colonneRoles}</th>
            </tr>
          </thead>
          <tbody>
            {MATRICE.map(({ cle, roles }) => (
              <tr key={cle}>
                <td>{p.regles[cle]}</td>
                <td>{roles.map(traduire).join(', ')}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="z-encart">
        <p className="z-encart__titre">{p.ecransTitre}</p>
        <p>{gabarit(p.ecransTexte, { ecrans: ecransReserves })}</p>
        <p>{p.note}</p>
      </div>
    </section>
  );
}
