package com.chatop.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springdoc.core.customizers.OperationCustomizer;
import io.swagger.v3.oas.models.responses.ApiResponse;

/** Configure les métadonnées et les règles de sécurité de la documentation OpenAPI. */
@Configuration
public class OpenApiConfig {

    /** Déclare le contrat API et le schéma d'authentification JWT pour Swagger. */
    @Bean
    OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info().title("Chatop API").version("1.0")
                        .description("Portail de locations : authentification, annonces, images et messages."))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP)
                                .scheme("bearer").bearerFormat("JWT")));
    }

    /** Ajoute aux opérations Swagger les réponses d'erreur communes de l'API. */
    @Bean
    OperationCustomizer errorResponses() {
        return (operation, handler) -> {
            operation.getResponses().putIfAbsent("400", new ApiResponse().description("Requete invalide"));
            operation.getResponses().putIfAbsent("401", new ApiResponse().description("JWT absent, invalide ou expire"));
            operation.getResponses().putIfAbsent("403", new ApiResponse().description("Operation non autorisee"));
            operation.getResponses().putIfAbsent("404", new ApiResponse().description("Ressource introuvable"));
            operation.getResponses().putIfAbsent("500", new ApiResponse().description("Erreur interne"));
            if (handler.getMethod().getName().equals("create")) {
                operation.getResponses().putIfAbsent("413", new ApiResponse().description("Fichier trop volumineux"));
            }
            return operation;
        };
    }
}
