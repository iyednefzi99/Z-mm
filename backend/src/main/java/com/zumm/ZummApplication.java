package com.zumm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Point d'entree de l'application Zumm.
 *
 * <p>Ossature du Sprint 0 : l'application demarre et expose son etat de sante.
 * La persistance, l'authentification et l'ingestion de mesures sont ajoutees
 * par les tranches suivantes du walking skeleton.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ZummApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZummApplication.class, args);
    }
}
