package com.odcc.tienda.modules.identity.adapter.in.rest.response;

import java.time.Instant;

public record LoginResponse(
    String accessToken,
    String tokenType,
    Instant expiresAt
) {
}
