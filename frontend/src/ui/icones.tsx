import type { ReactElement } from 'react';

/**
 * Pictogrammes de la vitrine publique (SPRINT-19).
 *
 * <p><strong>Pourquoi pas les émojis de la console.</strong> Dans l'application,
 * l'émoji est un repère parmi dix-neuf onglets déjà nommés : il aide à retrouver
 * une ligne, et son rendu approximatif ne coûte rien. Sur la vitrine, il devient
 * la première chose qu'un visiteur voit du produit — et il n'est pas rendu deux
 * fois pareil : 🧑‍🌾 tombe en carré tofu sur les Windows sans police de secours,
 * 🐝 arrive en couleurs plates sous Android et en dégradé sous macOS. Six
 * pictogrammes qui ne s'accordent ni en graisse, ni en taille, ni en palette
 * disent « page assemblée à la hâte » avant même qu'on ait lu un mot.
 *
 * <p>Ces tracés-là partagent une grille de 24, une graisse de trait unique et
 * `currentColor` : ils se teintent avec la charte et suivent le mode sombre sans
 * variante à maintenir. Ils restent <strong>décoratifs</strong> — le titre
 * traduit porte seul le sens, comme dans le rail de la console — d'où
 * `aria-hidden` posé sur le SVG lui-même.
 *
 * <p>La console garde ses émojis : les remplacer partout est un autre chantier,
 * qui touche dix-neuf onglets, la palette de commandes et leurs tests.
 */
const TRACES = {
  /** Histogramme : ce que « pilotage » montre réellement à l'ouverture. */
  graphique: ['M3 21h18', 'M7 21v-8', 'M12 21V5', 'M17 21v-11'],
  /** Ruche à hausses empilées, vue de face, avec son trou de vol. */
  ruche: ['M5 20a7 7 0 0 1 14 0', 'M4 20h16', 'M6.4 15.5h11.2', 'M8 11h8', 'M11 20v-2.2h2V20'],
  /** Carte pliée en trois volets — le pliage EST ce qui la distingue d'un cadre. */
  carte: ['M9 4 3 6.2v13.6L9 17.6l6 2.2 6-2.2V6.2L15 8.4Z', 'M9 4v13.6', 'M15 8.4V20'],
  /** Goutte de miel. */
  miel: ['M12 3.2s5.2 6.3 5.2 9.6a5.2 5.2 0 1 1-10.4 0C6.8 9.5 12 3.2 12 3.2Z'],
  /** Capteur : le mât, l'émission, et le point de mesure au centre. */
  capteur: [
    'M12 14.5V21',
    'M9 21h6',
    'M8.6 9.6a4.8 4.8 0 0 1 6.8 0',
    'M5.8 6.8a8.8 8.8 0 0 1 12.4 0',
  ],
  /** Le même signal, barré : la tournée continue quand le réseau s'arrête. */
  horsLigne: [
    'M5.2 12.4a9.6 9.6 0 0 1 13.6 0',
    'M8.6 15.8a4.8 4.8 0 0 1 6.8 0',
    'M12 19.4h.01',
    'M3.5 3.5 20.5 20.5',
  ],
  /** Apiculteur : la silhouette et le bord du chapeau de voile. */
  apiculteur: [
    'M12 4.6a3.4 3.4 0 1 1 0 6.8 3.4 3.4 0 0 1 0-6.8Z',
    'M6 8h12',
    'M5 20.5a7 7 0 0 1 14 0',
  ],
  /** Agent de terrain : le point posé sur le rucher qu'il visite. */
  agent: [
    'M12 21s6-5.9 6-10.2a6 6 0 1 0-12 0C6 15.1 12 21 12 21Z',
    'M12 8.6a2.2 2.2 0 1 1 0 4.4 2.2 2.2 0 0 1 0-4.4Z',
  ],
  /** Clé : le responsable est celui qui ouvre les droits, pas celui qui saisit. */
  cle: [
    'M8 11.6a3.4 3.4 0 1 1 0 6.8 3.4 3.4 0 0 1 0-6.8Z',
    'M10.6 12.6 20 3.2',
    'M16.8 6.4l2 2',
    'M14.6 8.6l2 2',
  ],
} as const;

export type NomIcone = keyof typeof TRACES;

export function Icone({ nom }: { nom: NomIcone }): ReactElement {
  return (
    <svg
      className="z-icone"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      {TRACES[nom].map((trace) => (
        <path key={trace} d={trace} />
      ))}
    </svg>
  );
}
