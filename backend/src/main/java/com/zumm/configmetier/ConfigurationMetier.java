package com.zumm.configmetier;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Charge et tient a jour les seuils metier de {@code ConfigZumm.ini} (US-025).
 *
 * <p>La configuration metier est externe au code : la modifier ne demande ni
 * recompilation ni redemarrage. Le fichier est relu a chaud quand sa date de
 * modification change ({@link #relire()}, planifie). S'il est absent ou illisible,
 * le service retombe sur {@link SeuilsMetier#defauts()} et l'application demarre
 * quand meme — un poste de developpement n'a pas a fournir le fichier.
 *
 * <p>Aucun secret ici : identifiants et mots de passe passent par l'environnement,
 * jamais par ce fichier.
 */
@Service
public class ConfigurationMetier {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigurationMetier.class);

    private final Path chemin;
    private volatile SeuilsMetier seuils = SeuilsMetier.defauts();
    private volatile long dateModificationVue = -1L;

    public ConfigurationMetier(
            @Value("${zumm.config-metier.chemin:config/ConfigZumm.ini}") String chemin) {
        this.chemin = Path.of(chemin);
    }

    @PostConstruct
    void chargementInitial() {
        recharger();
    }

    /** Seuils courants ; jamais {@code null} (defauts a defaut de fichier). */
    public SeuilsMetier seuils() {
        return seuils;
    }

    /**
     * Relecture planifiee : ne recharge que si le fichier a change depuis la
     * derniere lecture, pour ne pas reparser inutilement a chaque tick.
     */
    @Scheduled(fixedDelayString = "${zumm.config-metier.intervalle-relecture-ms:10000}")
    void relire() {
        try {
            if (Files.exists(chemin) && Files.getLastModifiedTime(chemin).toMillis() != dateModificationVue) {
                recharger();
            }
        } catch (IOException e) {
            LOG.warn("Relecture de {} impossible : {}", chemin, e.getMessage());
        }
    }

    /** Force une relecture immediate du fichier. */
    public synchronized void recharger() {
        if (!Files.exists(chemin)) {
            LOG.info("ConfigZumm.ini absent ({}) : seuils par defaut appliques.", chemin.toAbsolutePath());
            seuils = SeuilsMetier.defauts();
            dateModificationVue = -1L;
            return;
        }
        try (Reader source = Files.newBufferedReader(chemin, StandardCharsets.UTF_8)) {
            seuils = extraire(LecteurIni.analyser(source));
            dateModificationVue = Files.getLastModifiedTime(chemin).toMillis();
            LOG.info("ConfigZumm.ini charge depuis {}.", chemin.toAbsolutePath());
        } catch (IOException e) {
            LOG.warn("Lecture de {} impossible : {} — seuils par defaut conserves.", chemin, e.getMessage());
        }
    }

    /** Analyse un flux INI deja fourni, sans acces disque. Utile aux tests. */
    public static SeuilsMetier extraireDe(Reader source) throws IOException {
        return extraire(LecteurIni.analyser(source));
    }

    private static SeuilsMetier extraire(Map<String, Map<String, String>> ini) {
        SeuilsMetier defauts = SeuilsMetier.defauts();
        Map<String, String> application = ini.getOrDefault("application", Map.of());
        Map<String, String> seuilsIni = ini.getOrDefault("seuils", Map.of());
        Map<String, String> visites = ini.getOrDefault("visites", Map.of());
        Map<String, String> carte = ini.getOrDefault("carte", Map.of());

        return new SeuilsMetier(
                application.getOrDefault("langue_par_defaut", defauts.langueParDefaut()),
                liste(application.get("langues_actives"), defauts.languesActives()),
                entier(seuilsIni.get("poids_ruche_alerte_kg"), defauts.poidsRucheAlerteKg()),
                entier(seuilsIni.get("temperature_min_celsius"), defauts.temperatureMinCelsius()),
                entier(seuilsIni.get("temperature_max_celsius"), defauts.temperatureMaxCelsius()),
                entier(seuilsIni.get("humidite_max_pourcent"), defauts.humiditeMaxPourcent()),
                entier(visites.get("delai_alerte_jours"), defauts.delaiAlerteJours()),
                entier(carte.get("arrondi_degres_public"), defauts.arrondiDegresPublic()));
    }

    private static int entier(String valeur, int defaut) {
        if (valeur == null || valeur.isBlank()) {
            return defaut;
        }
        try {
            return Integer.parseInt(valeur.trim());
        } catch (NumberFormatException e) {
            LOG.warn("Valeur entiere invalide « {} » : defaut {} conserve.", valeur, defaut);
            return defaut;
        }
    }

    private static List<String> liste(String valeur, List<String> defaut) {
        if (valeur == null || valeur.isBlank()) {
            return defaut;
        }
        return java.util.Arrays.stream(valeur.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }
}
