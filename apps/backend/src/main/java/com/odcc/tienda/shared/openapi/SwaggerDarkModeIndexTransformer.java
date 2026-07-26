package com.odcc.tienda.shared.openapi;

import jakarta.servlet.http.HttpServletRequest;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springdoc.webmvc.ui.SwaggerIndexPageTransformer;
import org.springdoc.webmvc.ui.SwaggerWelcomeCommon;
import org.springframework.core.io.Resource;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.servlet.resource.ResourceTransformerChain;
import org.springframework.web.servlet.resource.TransformedResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

final class SwaggerDarkModeIndexTransformer extends SwaggerIndexPageTransformer {

    private static final String DARK_MODE_LINK =
        "<link rel=\"stylesheet\" type=\"text/css\" href=\"/swagger-dark.css\">";

    SwaggerDarkModeIndexTransformer(
        SwaggerUiConfigProperties swaggerUiConfigProperties,
        SwaggerUiOAuthProperties swaggerUiOAuthProperties,
        SwaggerWelcomeCommon swaggerWelcomeCommon,
        ObjectMapperProvider objectMapperProvider
    ) {
        super(
            swaggerUiConfigProperties,
            swaggerUiOAuthProperties,
            swaggerWelcomeCommon,
            objectMapperProvider
        );
    }

    @Override
    public Resource transform(
        HttpServletRequest request,
        Resource resource,
        ResourceTransformerChain transformerChain
    ) throws IOException {
        Resource transformed = super.transform(request, resource, transformerChain);
        byte[] transformedBytes = FileCopyUtils.copyToByteArray(
            transformed.getInputStream()
        );
        String html = new String(transformedBytes, StandardCharsets.UTF_8);

        if (html.contains(DARK_MODE_LINK)) {
            return transformed;
        }

        String darkModeHtml = html.replace("</head>", DARK_MODE_LINK + "</head>");
        return new TransformedResource(
            transformed,
            darkModeHtml.getBytes(StandardCharsets.UTF_8)
        );
    }
}

