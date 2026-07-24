package com.zumm.config;

import io.swagger.v3.oas.models.OpenAPI;
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
