package com.odcc.tienda.modules.identity.application.model;

import java.time.Instant;

public record IssuedAccessToken(
    String value,
    Instant expiresAt
) {
}
