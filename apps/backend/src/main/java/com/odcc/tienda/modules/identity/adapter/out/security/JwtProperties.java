package com.odcc.tienda.modules.identity.adapter.out.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(
    String issuer,
    Duration accessTokenTtl,
    String secretBase64
) {
}
