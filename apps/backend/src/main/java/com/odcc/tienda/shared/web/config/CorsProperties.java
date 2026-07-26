package com.odcc.tienda.shared.web.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.web.cors")
public record CorsProperties(
    List<String> allowedOrigins,
    List<String> allowedMethods,
    List<String> allowedHeaders,
    List<String> exposedHeaders,
    boolean allowCredentials,
    long maxAgeSeconds
) {

    public CorsProperties {
        allowedOrigins = copy(allowedOrigins);
        allowedMethods = copy(allowedMethods);
        allowedHeaders = copy(allowedHeaders);
        exposedHeaders = copy(exposedHeaders);
    }

    private static List<String> copy(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
