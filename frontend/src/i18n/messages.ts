/**
 * Messages du client.
 *
 * Le francais est la langue source ; `en` et `ar` en sont des traductions.
 * Aucune chaine visible ne doit etre ecrite en dur dans un composant.
 *
 * La terminologie metier (fermier, ferme, site, rucher, ruche, corps, hausse,
 * cadre, visite, agent) provient du glossaire du cahier des charges : ne pas
 * retraduire de zero.
 */
export const LANGUES = ['fr', 'en', 'ar'] as const;

export type Langue = (typeof LANGUES)[number];

/** Langues dont l'ecriture va de droite a gauche. */
export const LANGUES_RTL: readonly Langue[] = ['ar'];

export const direction = (langue: Langue): 'rtl' | 'ltr' =>
  LANGUES_RTL.includes(langue) ? 'rtl' : 'ltr';

type Cle = 'titre' | 'sousTitre' | 'etatApi' | 'apiJoignable' | 'apiInjoignable' | 'chargement' | 'langue';

export const messages: Record<Langue, Record<Cle, string>> = {
  fr: {
    titre: 'Zümm',
    sousTitre: "Système d'information de gestion et de suivi apicole",
    etatApi: "État de l'API",
    apiJoignable: 'API joignable',
    apiInjoignable: 'API injoignable',
    chargement: 'Chargement…',
    langue: 'Langue',
  },
  en: {
    titre: 'Zümm',
    sousTitre: 'Beekeeping management and monitoring information system',
    etatApi: 'API status',
    apiJoignable: 'API reachable',
    apiInjoignable: 'API unreachable',
    chargement: 'Loading…',
    langue: 'Language',
  },
  ar: {
    titre: 'زُم',
    sousTitre: 'نظام معلومات لإدارة ومتابعة تربية النحل',
    etatApi: 'حالة الواجهة البرمجية',
    apiJoignable: 'الواجهة البرمجية متاحة',
    apiInjoignable: 'الواجهة البرمجية غير متاحة',
    chargement: 'جارٍ التحميل…',
    langue: 'اللغة',
  },
};
