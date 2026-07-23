/**
 * Libelles de la console de gestion (FR, langue source).
 *
 * Aucune chaine visible n'est ecrite en dur dans un composant (charte Zümm §9).
 * La console reste monolingue au SPRINT-01 ; sa traduction FR/EN/AR suivra le
 * meme mecanisme que `i18n/messages.ts`. La terminologie metier (fermier, ferme,
 * site, agent, rucher) provient du glossaire du cahier des charges.
 */
export const L = {
  marque: 'Zümm',
  baseline: "Gestion et suivi apicole",
  onglets: {
    fermiers: 'Fermiers',
    fermes: 'Fermes',
    sites: 'Sites',
    agents: 'Agents',
    config: 'Configuration',
  },
  actions: {
    nouveau: 'Nouveau',
    modifier: 'Modifier',
    supprimer: 'Supprimer',
    enregistrer: 'Enregistrer',
    annuler: 'Annuler',
    fermer: 'Fermer',
    reessayer: 'Réessayer',
    seConnecter: 'Ouvrir la session',
    seDeconnecter: 'Fermer la session',
  },
  etats: {
    chargement: 'Chargement…',
    vide: 'Aucun élément pour le moment.',
    erreur: 'Une erreur est survenue.',
    confirmerSuppression: 'Confirmer la suppression de « {nom} » ?',
  },
  champs: {
    nom: 'Nom',
    contact: 'Contact',
    fermier: 'Fermier',
    ferme: 'Ferme',
    role: 'Rôle',
    latitude: 'Latitude',
    longitude: 'Longitude',
    altitude: 'Altitude (m)',
    dateMiseEnOeuvre: 'Mise en œuvre',
    dateDemenagement: 'Déménagement',
    dateCloture: 'Clôture',
    aucun: '— aucun —',
  },
  roles: {
    apiculteur: 'Apiculteur',
    superviseur: 'Superviseur',
    responsable: 'Responsable',
    admin: 'Administrateur',
  },
  session: {
    titre: 'Session requise',
    explication:
      "La console interroge une API protégée. En production, la connexion passe par Keycloak (OIDC). Pour le développement, collez un jeton d'accès valide portant le claim tenant_id.",
    jeton: "Jeton d'accès (JWT)",
    placeholder: 'eyJhbGciOi…',
  },
  config: {
    titre: 'Seuils métier',
    sousTitre: 'Lus depuis ConfigZumm.ini — modifiables sans redémarrage.',
    poids: "Poids d'alerte (kg)",
    tempMin: 'Température min (°C)',
    tempMax: 'Température max (°C)',
    humidite: 'Humidité max (%)',
    delai: 'Délai sans visite (j)',
    arrondi: 'Arrondi position publique (°)',
    langues: 'Langues actives',
  },
} as const;

/** Substitue {nom} dans un gabarit de libelle. */
export const gabarit = (modele: string, valeurs: Record<string, string>): string =>
  modele.replace(/\{(\w+)\}/g, (_, cle: string) => valeurs[cle] ?? `{${cle}}`);
