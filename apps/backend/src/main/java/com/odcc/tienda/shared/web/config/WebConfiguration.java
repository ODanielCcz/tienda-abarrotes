package com.odcc.tienda.shared.web.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CorsProperties.class)
@RequiredArgsConstructor
public class WebConfiguration implements WebMvcConfigurer {

    private final CorsProperties corsProperties;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry
            .addMapping("/api/**")
            .allowedOrigins(corsProperties.allowedOrigins().toArray(String[]::new))
            .allowedMethods(corsProperties.allowedMethods().toArray(String[]::new))
            .allowedHeaders(corsProperties.allowedHeaders().toArray(String[]::new))
            .exposedHeaders(corsProperties.exposedHeaders().toArray(String[]::new))
            .allowCredentials(corsProperties.allowCredentials())
            .maxAge(corsProperties.maxAgeSeconds());
    }
}
