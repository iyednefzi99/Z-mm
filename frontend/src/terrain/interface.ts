import { useEffect, useState } from 'react';
import type { Onglet } from '../routage/routes';

/**
 * Étendue de l'interface (SPRINT-25, lot J).
 *
 * <p>Deux éditeurs — APIGO et HiveTracks — conseillent à leurs utilisateurs de
 * « n'activer les modules avancés qu'au moment où ils en ont besoin » (§13 de
 * `docs/ECART-CONCURRENTS.md`). Le conseil dit la même chose que le reproche :
 * une console de vingt et un écrans est écrasante pour l'apiculteur de trois
 * ruches, qui ouvre le produit pour noter une visite et se retrouve devant un
 * journal d'audit et des lots de conditionnement.
 *
 * <p><strong>Masquer n'est pas interdire, et la distinction est tout.</strong>
 * Les rôles décident de ce qui est PERMIS ({@code ROLES_ONGLET}, et derrière lui
 * `SecurityConfig`) ; ce réglage-ci décide de ce qui est MONTRÉ. Un écran masqué
 * reste atteignable par son lien direct, la recherche continue d'y mener, et le
 * réglage se rétablit en un clic. Confondre les deux produirait une interface
 * qui a l'air de retirer des droits — et un utilisateur qui appelle son
 * responsable pour un réglage qu'il pouvait changer lui-même.
 *
 * <p><strong>Pourquoi ces cinq écrans-là.</strong> Ce sont ceux qui supposent un
 * équipement ou une échelle : les <em>capteurs</em> demandent du matériel, les
 * <em>lots</em> de conditionnement supposent qu'on vende, les <em>essaims</em>
 * et les <em>reines</em> supposent qu'on élève, le <em>journal d'audit</em>
 * suppose une équipe. Aucun ne concerne la tenue d'un rucher.
 */

const CLE = 'zumm.interface';

export const ETENDUES = ['complete', 'essentielle'] as const;
export type Etendue = (typeof ETENDUES)[number];

/**
 * Écrans retirés en mode essentiel.
 *
 * <p>La liste est écrite en dur et c'est volontaire : un réglage écran par
 * écran donnerait vingt et une cases à cocher, c'est-à-dire un second problème
 * de complexité pour en résoudre un premier.
 */
export const ECRANS_AVANCES: readonly Onglet[] = [
  'capteurs',
  'lots',
  'essaims',
  'reines',
  'audit',
];

type Abonne = (etendue: Etendue) => void;
const abonnes = new Set<Abonne>();

function lire(): Etendue {
  try {
    const valeur = localStorage.getItem(CLE);
    return ETENDUES.includes(valeur as Etendue) ? (valeur as Etendue) : 'complete';
  } catch {
    // Stockage indisponible (navigation privée) : l'interface complète est le
    // repli juste — elle ne cache rien, donc ne surprend personne.
    return 'complete';
  }
}

export function etendueCourante(): Etendue {
  return lire();
}

export function definirEtendue(etendue: Etendue): void {
  try {
    localStorage.setItem(CLE, etendue);
  } catch {
    // Sans stockage, le réglage vaut pour la session en cours et pas au-delà.
  }
  abonnes.forEach((abonne) => abonne(etendue));
}

/** Cet écran doit-il apparaître dans la navigation ? */
export function ongletMontre(onglet: Onglet, etendue: Etendue): boolean {
  return etendue === 'complete' || !ECRANS_AVANCES.includes(onglet);
}

export function useEtendue(): [Etendue, (etendue: Etendue) => void] {
  const [etendue, setEtendue] = useState<Etendue>(lire);
  useEffect(() => {
    abonnes.add(setEtendue);
    return () => {
      abonnes.delete(setEtendue);
    };
  }, []);
  return [etendue, definirEtendue];
}
