package com.zumm.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.StringSchema;
import org.springdoc.core.utils.SpringDocUtils;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadonnees du contrat OpenAPI 3 (US-026). springdoc genere le contrat a partir
 * des controleurs ; ce bean en fixe le titre, la version et la description. Le
 * contrat est servi sur {@code /v3/api-docs} et explorable via Swagger UI.
 */
@Configuration
public class OpenApiConfig {

    static {
        // ─── Correction du schema des types temporels (SPRINT-17) ───
        //
        // Defaut trouve en confrontant le contrat publie aux types du client : le
        // contrat decrivait `LocalTime` comme un OBJET {hour, minute, second,
        // nano}, alors que l'API le serialise en CHAINE « 14:30:00 » — Spring Boot
        // enregistre le module JavaTime et desactive l'ecriture en horodatage.
        //
        // Ce n'est pas cosmetique : le contrat est un livrable public (US-026).
        // Un integrateur qui genere son client a partir de ce contrat produit du
        // code attendant un objet, et recoit une chaine. La panne est chez lui, la
        // faute chez nous.
        //
        // Correction au niveau du TYPE plutot que par annotation champ par champ :
        // la regle vaut pour tout `LocalTime` present ou futur, et ne demande pas
        // de se souvenir de l'annoter.
        SpringDocUtils.getConfig()
                .replaceWithSchema(java.time.LocalTime.class,
                        new StringSchema().format("time").example("14:30:00"));
    }

    @Bean
    OpenAPI contratZumm() {
        return new OpenAPI().info(new Info()
                .title("API Zümm")
                .version("v1")
                .description("SIG apicole de gestion et de suivi de ruchers — contrat REST public. "
                        + "Les identifiants d'API (ex. getZummHoneyActualQuantity) ne se traduisent pas.")
                .license(new License().name("Propriétaire")));
    }
}
