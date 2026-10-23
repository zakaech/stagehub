package com.stagehub.common.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Déclare le schéma "bearerAuth" : le bouton Authorize de Swagger UI envoie ensuite
 * l'en-tête {@code Authorization: Bearer <jeton>} sur chaque requête.
 * Les endpoints publics retirent cette exigence avec {@code @SecurityRequirements}.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(title = "StageHub — offre-service", version = "v1",
                description = "Offres de stage (publication, recherche), entreprises et leur validation par l'admin."),
        security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
@SecurityScheme(name = OpenApiConfig.BEARER_AUTH, type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";
}
