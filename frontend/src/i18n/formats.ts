/**
 * Formatage localisé des dates et des nombres (US-053, SPRINT-11).
 *
 * <p>Avant ce sprint, la console n'utilisait `Intl` nulle part : les dates
 * s'affichaient en ISO brut (`2026-12-04`) en français, en anglais *et* en arabe,
 * et les nombres passaient par `toFixed`, donc toujours avec un point décimal.
 *
 * <p>Un choix mérite d'être explicité : l'arabe est formaté avec le système de
 * numération **latin**. Les traductions arabes du produit écrivent déjà « 1 و2 و3
 * كم » ; mêler des chiffres indo-arabes dans les tableaux donnerait une console
 * incohérente avec elle-même. Le jour où le client demande les chiffres
 * indo-arabes, c'est cette constante qu'il faut changer, et elle seule.
 */
import type { Langue } from './messages';

/** Locale BCP-47 par langue de l'interface. */
const LOCALES: Record<Langue, string> = {
  fr: 'fr-FR',
  en: 'en-GB',
  ar: 'ar-TN-u-nu-latn',
};

/** Ce qui s'affiche à la place d'une valeur absente. */
export const VIDE = '—';

export interface Formats {
  /** Date seule : `4 déc. 2026`, `4 Dec 2026`, `٤ ديسمبر…` selon la langue. */
  date: (iso: string | null | undefined) => string;
  /** Date et heure, pour les horodatages (journal d'audit, mesures). */
  dateHeure: (iso: string | null | undefined) => string;
  /** Nombre décimal, séparateurs de la locale. */
  nombre: (valeur: number | null | undefined, decimales?: number) => string;
  /** Distance en mètres, rendue en m ou en km selon l'ordre de grandeur. */
  distance: (metres: number | null | undefined) => string;
}

/** Fabrique les formateurs d'une langue. */
export function formatsDe(langue: Langue): Formats {
  const locale = LOCALES[langue];

  const date = (iso: string | null | undefined): string => {
    const valeur = enDate(iso);
    return valeur === null
      ? VIDE
      : new Intl.DateTimeFormat(locale, { dateStyle: 'medium' }).format(valeur);
  };

  const dateHeure = (iso: string | null | undefined): string => {
    const valeur = enDate(iso);
    return valeur === null
      ? VIDE
      : new Intl.DateTimeFormat(locale, { dateStyle: 'medium', timeStyle: 'short' }).format(valeur);
  };

  const nombre = (valeur: number | null | undefined, decimales = 2): string =>
    valeur === null || valeur === undefined || Number.isNaN(valeur)
      ? VIDE
      : new Intl.NumberFormat(locale, {
          minimumFractionDigits: decimales,
          maximumFractionDigits: decimales,
        }).format(valeur);

  const distance = (metres: number | null | undefined): string => {
    if (metres === null || metres === undefined || Number.isNaN(metres)) {
      return VIDE;
    }
    // En deçà du kilomètre, le mètre est plus parlant qu'un « 0,35 km ».
    return metres < 1000 ? `${nombre(metres, 0)} m` : `${nombre(metres / 1000, 2)} km`;
  };

  return { date, dateHeure, nombre, distance };
}

/** Convertit une chaîne ISO en Date, ou null si elle est absente ou invalide. */
function enDate(iso: string | null | undefined): Date | null {
  if (!iso) {
    return null;
  }
  const valeur = new Date(iso);
  return Number.isNaN(valeur.getTime()) ? null : valeur;
}
