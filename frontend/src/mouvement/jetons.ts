import type { Transition } from 'motion/react';

/**
 * Jetons de mouvement — miroir TypeScript de la charte.
 *
 * <p><strong>Pourquoi ce fichier existe.</strong> `theme/tokens.css` porte déjà
 * les durées et les courbes (`--z-duration-*`, `--z-ease-*`) et c'est lui qui sert
 * au CSS. Mais Motion anime en JavaScript : il écrit des styles en ligne et ne lit
 * aucune variable CSS. Sans ce miroir, chaque composant animé ré-écrirait `0.2` et
 * `cubic-bezier(0.22, 1, 0.36, 1)` en dur — exactement la dérive de valeurs que le
 * dépôt combat ailleurs (trois comptes de tests contradictoires, cf. CLAUDE.md).
 *
 * <p><strong>Rien ici n'est inventé.</strong> Chaque nombre est relevé dans
 * `design/motion/transitions.md`, `design/motion/modal.css`, `theme/tokens.css` ou
 * `App.css`, et `mouvement.test.ts` échoue si l'un d'eux s'écarte du CSS. La seule
 * unité qui change est la seconde : Motion compte en secondes, le CSS en
 * millisecondes.
 *
 * <p><strong>Ce qui manque volontairement.</strong> Aucun jeton d'inertie
 * (`dragTransition`) : la charte ne décrit pas de mouvement projeté. Le premier
 * composant qui glisse à l'inertie devra en fixer la valeur ici <em>et</em> dans
 * `design/motion/`, pas dans son propre fichier.
 */

/** Courbe de Bézier cubique, dans la forme attendue par Motion. */
type Courbe = [number, number, number, number];

export const jetonsMouvement = {
  /** Durées, en secondes. Source : `--z-duration-*` et `--z-modal-*-dur`. */
  duree: {
    /** 120 ms — micro : survol, swap d'icône, focus. */
    rapide: 0.12,
    /** 200 ms — standard : fondu, petit glissement, badge. */
    base: 0.2,
    /** 320 ms — ample : tiroir, panneau, transition de vue. */
    lente: 0.32,
    /** 250 ms — ouverture de modale. Plus longue que la fermeture, à dessein. */
    modaleOuverture: 0.25,
    /** 150 ms — fermeture de modale. Une sortie lente retient l'utilisateur. */
    modaleFermeture: 0.15,
    /** 1,4 s — cycle du chatoiement des squelettes (`z-chatoiement`, App.css). */
    chatoiement: 1.4,
  },

  /** Courbes. Source : `--z-ease-*`. `sortie` est aussi celle de la modale. */
  courbe: {
    /** Entrées — `cubic-bezier(0.22, 1, 0.36, 1)`. */
    sortie: [0.22, 1, 0.36, 1] as Courbe,
    /** Sorties — `cubic-bezier(0.4, 0, 1, 1)`. */
    entree: [0.4, 0, 1, 1] as Courbe,
    /** Morph et redimensionnement — `cubic-bezier(0.65, 0, 0.35, 1)`. */
    entreeSortie: [0.65, 0, 0.35, 1] as Courbe,
  },

  /** Amplitudes de déplacement, en pixels. */
  distance: {
    /** 12 px — `--z-shift` : fade-up, révélation d'un bloc. */
    decalage: 12,
    /** 24 px — glissement d'un changement d'onglet ou de vue. */
    vue: 24,
    /** 1 px — enfoncement d'un bouton (`.z-btn:active`). */
    pression: 1,
  },

  /** Échelles. */
  echelle: {
    /** 0,96 — apparition et modale (`--z-modal-scale`, `--z-scale-in`). */
    apparition: 0.96,
    /** 1,08 — sommet du pop d'un chiffre mis à jour (`z-pop`). */
    pop: 1.08,
    /** 0,8 — icône sortante d'un swap d'état. */
    swap: 0.8,
  },

  /** 40 ms entre deux enfants révélés en cascade (`z-fade-up:nth-child`). */
  echelonnement: 0.04,
};

/**
 * Transitions prêtes à l'emploi, dans la grammaire de la charte : entrée vive,
 * sortie posée — une sortie dure moins longtemps qu'une entrée et emprunte la
 * courbe inverse.
 */
export const transitions = {
  /** Apparition d'un élément. */
  entree: {
    duration: jetonsMouvement.duree.base,
    ease: jetonsMouvement.courbe.sortie,
  },
  /** Disparition d'un élément. */
  sortie: {
    duration: jetonsMouvement.duree.rapide,
    ease: jetonsMouvement.courbe.entree,
  },
  /** Déplacement ample : tiroir, panneau, changement de vue. */
  ample: {
    duration: jetonsMouvement.duree.lente,
    ease: jetonsMouvement.courbe.sortie,
  },
  /** Transformation de forme : accordéon, redimensionnement, `+` → menu. */
  morph: {
    duration: jetonsMouvement.duree.lente,
    ease: jetonsMouvement.courbe.entreeSortie,
  },
  /** Ouverture de modale — ne pas réinventer, cf. `design/motion/dialog.md`. */
  modaleOuverture: {
    duration: jetonsMouvement.duree.modaleOuverture,
    ease: jetonsMouvement.courbe.sortie,
  },
  /** Fermeture de modale. */
  modaleFermeture: {
    duration: jetonsMouvement.duree.modaleFermeture,
    ease: jetonsMouvement.courbe.sortie,
  },
} satisfies Record<string, Transition>;

/**
 * Ressorts, pour ce que la durée ne sait pas faire : un geste direct (glisser,
 * suivre le pointeur) ou une valeur interrompue en vol. Un ressort reprend la
 * vitesse courante au lieu de repartir de zéro ; une transition à durée fixe, non.
 *
 * <p>Ils sont exprimés en `visualDuration` + `bounce`, la forme perceptuelle de
 * Motion, ce qui laisse les durées de la charte comme seule source. `bounce: 0`
 * partout : <strong>aucune courbe de la charte ne dépasse sa cible</strong>. Le seul
 * dépassement admis est le pop d'un chiffre (`echelle.pop`), qui est une image clé
 * explicite, pas une élasticité de ressort.
 */
export const ressorts = {
  /** Micro-interaction : survol, enfoncement. */
  vif: {
    type: 'spring',
    visualDuration: jetonsMouvement.duree.rapide,
    bounce: 0,
  },
  /** Standard : la plupart des changements d'état. */
  doux: {
    type: 'spring',
    visualDuration: jetonsMouvement.duree.base,
    bounce: 0,
  },
  /** Ample : panneau, tiroir, élément lourd. */
  ample: {
    type: 'spring',
    visualDuration: jetonsMouvement.duree.lente,
    bounce: 0,
  },
} satisfies Record<string, Transition>;

/**
 * Pulsation du ressort de rappel, en rad/s.
 *
 * <p>Un ressort <em>critique</em> (amortissement = 2ω) revient à sa cible sans
 * jamais la dépasser, et il ne reste que 1,7 % du déplacement à ωt = 6. Fixer ce
 * point à `duree.base` donne donc un rappel visuellement terminé en 200 ms,
 * exactement comme le reste des changements d'état de la charte.
 */
const PULSATION = 6 / jetonsMouvement.duree.base;

/**
 * Relâchement — ce qu'un élément fait quand le doigt le lâche (`dragTransition`).
 *
 * <p><strong>Cette valeur est une décision, pas un relevé.</strong> La charte ne
 * décrivait aucun mouvement projeté ; celui-ci a été tranché ici puis inscrit dans
 * `design/motion/transitions.md` § Relâchement et dans `design/tokens.json`, qui en
 * sont désormais la source. Aucun équivalent CSS n'existe — l'inertie ne
 * s'exprime pas en feuille de style — donc, contrairement aux durées et aux
 * courbes, ce jeton-ci n'est pas contrôlé par le test anti-dérive.
 *
 * <p><strong>`power: 0` — Zümm ne projette pas.</strong> C'est le point de la
 * décision. Un élément lâché ne part pas glisser vers une position calculée depuis
 * la vitesse du geste : il rejoint un état que le code a décidé — rejeté ou
 * remis en place, réordonné ou revenu à son rang. Trois raisons :
 *
 * <ul>
 *   <li>la charte pose que le mouvement <em>guide</em> ; une projection libre
 *       amène l'élément là où personne ne l'a demandé ;</li>
 *   <li>au rucher, avec des gants, une ligne qui continue de filer après le
 *       relâchement est un défaut de contrôle, pas une fluidité ;</li>
 *   <li>la position finale reste prévisible, donc annonçable à une technologie
 *       d'assistance — ce qu'une position issue d'une vitesse ne serait pas.</li>
 * </ul>
 *
 * <p><strong>Le rappel est critique</strong> (`bounceDamping² = 4 × bounceStiffness`)
 * pour la même raison que `ressorts` est à `bounce: 0` : rien, dans le vocabulaire
 * de la charte, ne dépasse sa cible.
 *
 * <p><strong>`timeConstant` n'a d'effet que si un composant relève `power`.</strong>
 * Il est fixé quand même, pour que l'exception — un jour, une surface réellement
 * balayable — hérite d'une valeur au lieu d'en inventer une : trois constantes de
 * temps couvrent 95 % du glissement, donc à `duree.lente / 3`, une projection ne
 * survit jamais au plus lent des mouvements de la charte.
 */
export const relachement = {
  power: 0,
  timeConstant: (jetonsMouvement.duree.lente * 1000) / 3,
  bounceStiffness: PULSATION ** 2,
  bounceDamping: 2 * PULSATION,
};
