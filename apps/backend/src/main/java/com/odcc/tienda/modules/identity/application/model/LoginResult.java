package com.odcc.tienda.modules.identity.application.model;

import java.time.Instant;

public record LoginResult(
    String accessToken,
    String tokenType,
    Instant expiresAt,
    AuthenticatedUser user
) {
}
