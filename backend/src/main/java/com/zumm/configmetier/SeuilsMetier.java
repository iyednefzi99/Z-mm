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
 */
public record SeuilsMetier(
        String langueParDefaut,
        java.util.List<String> languesActives,
        int poidsRucheAlerteKg,
        int temperatureMinCelsius,
        int temperatureMaxCelsius,
        int humiditeMaxPourcent,
        int delaiAlerteJours,
        int arrondiDegresPublic) {

    /** Valeurs de repli, alignees sur le gabarit versionne. */
    public static SeuilsMetier defauts() {
        return new SeuilsMetier(
                "fr",
                java.util.List.of("fr", "en", "ar"),
                15,
                32,
                36,
                70,
                21,
                2);
    }
}
