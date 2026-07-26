package com.odcc.tienda.modules.identity.application.model;

import java.util.Set;
import java.util.UUID;

public record AuthenticatedUser(
    UUID id,
    String username,
    String displayName,
    Set<String> roles,
    Set<String> permissions
) {

    public AuthenticatedUser {
        roles = Set.copyOf(roles);
        permissions = Set.copyOf(permissions);
    }
}
