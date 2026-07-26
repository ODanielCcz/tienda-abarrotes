package com.odcc.tienda.shared.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springdoc.webmvc.ui.SwaggerIndexTransformer;
import org.springdoc.webmvc.ui.SwaggerWelcomeCommon;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

    public static final String BEARER_AUTH_SCHEME = "bearerAuth";

    @Bean
    OpenAPI tiendaOpenApi() {
        return new OpenAPI()
            .components(
                new Components().addSecuritySchemes(
                    BEARER_AUTH_SCHEME,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                )
            )
            .addSecurityItem(
                new SecurityRequirement().addList(BEARER_AUTH_SCHEME)
            )
            .info(
                new Info()
                    .title("Tienda de Abarrotes API")
                    .version("v1")
                    .description(
                        "API para la administración de la tienda de abarrotes"
                    )
                    .contact(new Contact().name("Equipo del proyecto"))
            );
    }

    @Bean
    SwaggerIndexTransformer swaggerDarkModeIndexTransformer(
        SwaggerUiConfigProperties swaggerUiConfigProperties,
        SwaggerUiOAuthProperties swaggerUiOAuthProperties,
        SwaggerWelcomeCommon swaggerWelcomeCommon,
        ObjectMapperProvider objectMapperProvider
    ) {
        return new SwaggerDarkModeIndexTransformer(
            swaggerUiConfigProperties,
            swaggerUiOAuthProperties,
            swaggerWelcomeCommon,
            objectMapperProvider
        );
    }
}
