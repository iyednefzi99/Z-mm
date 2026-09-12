package com.zumm.configmetier;

/**
 * Vue immuable des seuils metier lus dans {@code ConfigZumm.ini} (US-025).
 *
 * <p>Ces valeurs sont modifiables sans recompilation ni redemarrage (relecture a
 * chaud, cf. {@link ConfigurationMetier}). Les defauts refletent le gabarit
 * {@code config/ConfigZumm.example.ini} et servent de repli si le fichier est
 * absent ou une cle manquante.
 *
 * @param langueParDefaut     langue source du produit
 * @param languesActives      locales servies, la premiere etant la langue source
 * @param poidsRucheAlerteKg  poids en deca duquel une ruche est signalee
 * @param temperatureMinCelsius seuil bas de temperature
 * @param temperatureMaxCelsius seuil haut de temperature
 * @param humiditeMaxPourcent  seuil haut d'humidite
 * @param delaiAlerteJours     jours sans visite avant signalement
 * @param arrondiDegresPublic  arrondi des positions pour les profils non proprietaires
 * @param taillePageParDefaut  nombre d'elements par page des listes (US-052)
 * @param prixMielKgEur        valorisation du kg de miel produit, pour le ROI (US-015)
 * @param coutVisiteEur        cout d'une intervention, pour le ROI (US-015)
 * @param seuilRefusAnomalie   nombre de refus (403) d'un meme acteur qui declenche
 *                             une alerte d'anomalie d'acces (SPRINT-34)
 * @param fenetreRefusAnomalieMinutes fenetre glissante, en minutes, sur laquelle
 *                             ce nombre de refus est compte
 */
public record SeuilsMetier(
        String langueParDefaut,
        java.util.List<String> languesActives,
        int poidsRucheAlerteKg,
        int temperatureMinCelsius,
        int temperatureMaxCelsius,
        int humiditeMaxPourcent,
        /**
         * Niveau de batterie sous lequel un capteur est signale (SPRINT-26).
         *
         * <p>20 % par defaut : c'est ce qui laisse le temps d'une tournee, pas
         * ce qui reste quand la balance s'est deja tue.
         */
        int batterieMinPourcent,
        /**
         * Inclinaison au-dela de laquelle une ruche est signalee, en degres.
         *
         * <p>20° par defaut : une ruche posee de travers sur un terrain en pente
         * n'en est pas la. Au-dela, elle a bouge.
         */
        int inclinaisonMaxDegres,
        /**
         * Chute de poids qui declenche l'alarme anti-vol, en kilogrammes.
         *
         * <p>10 kg par defaut, entre deux mesures consecutives. C'est plus
         * qu'une hausse pleine retiree a la main sans que rien ne soit
         * enregistre, et moins qu'une ruche emportee.
         */
        int chuteVolKg,
        /**
         * Rayon de butinage par defaut, en kilometres (SPRINT-32).
         *
         * <p>3 km : la distance ou une colonie fait l'essentiel de sa recolte.
         * Elle va plus loin — jusqu'a une dizaine de kilometres en terrain
         * pauvre —, mais le rendement d'un vol decroit vite, et dessiner d'emblee
         * un cercle de dix kilometres ferait compter comme environnement des
         * parcelles ou aucune abeille ne va deux fois.
         */
        int rayonButinageKm,
        int delaiAlerteJours,
        int arrondiDegresPublic,
        int taillePageParDefaut,
        java.math.BigDecimal prixMielKgEur,
        java.math.BigDecimal coutVisiteEur,
        int seuilRefusAnomalie,
        int fenetreRefusAnomalieMinutes) {

    /** Valeurs de repli, alignees sur le gabarit versionne. */
    public static SeuilsMetier defauts() {
        return new SeuilsMetier(
                "fr",
                java.util.List.of("fr", "en", "ar"),
                15,
                32,
                36,
                70,
                20,
                20,
                10,
                3,
                21,
                2,
                25,
                java.math.BigDecimal.valueOf(12),
                java.math.BigDecimal.valueOf(25),
                5,
                15);
    }
}
