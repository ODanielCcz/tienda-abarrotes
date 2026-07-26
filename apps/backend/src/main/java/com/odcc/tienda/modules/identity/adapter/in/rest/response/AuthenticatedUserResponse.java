package com.odcc.tienda.modules.identity.adapter.in.rest.response;

import java.util.Set;
import java.util.UUID;

public record AuthenticatedUserResponse(
    UUID id,
    String username,
    String displayName,
    Set<String> roles,
    Set<String> permissions
) {
}
