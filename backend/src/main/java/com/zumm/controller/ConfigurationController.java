package com.zumm.controller;

import com.zumm.configmetier.ConfigurationMetier;
import com.zumm.configmetier.SeuilsMetier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Expose les seuils metier courants issus de {@code ConfigZumm.ini} (US-025).
 *
 * <p>Support de la demonstration de fin de sprint : « seuils lus depuis
 * ConfigZumm.ini ». Lecture seule — la modification passe par le fichier, relu a
 * chaud, jamais par l'API.
 */
@RestController
@RequestMapping("/api/configuration")
public class ConfigurationController {

    private final ConfigurationMetier configuration;

    public ConfigurationController(ConfigurationMetier configuration) {
        this.configuration = configuration;
    }

    @GetMapping("/seuils")
    public SeuilsMetier seuils() {
        return configuration.seuils();
    }
}
